package com.djangofiles.djangofiles

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.djangofiles.djangofiles.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

class MainActivity : AppCompatActivity() {
    companion object {
        const val PREFS_NAME = "AppPreferences"
        const val URL_KEY = "saved_url"
        const val TOKEN_KEY = "auth_token"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private val client = OkHttpClient()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webview
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true

        val packageInfo = packageManager.getPackageInfo(this.packageName, 0)
        Log.d("MY_APP_TAG", "versionName: ${packageInfo.versionName}")
        val userAgent =
            "${webView.settings.userAgentString} DjangoFiles Android/${packageInfo.versionName}"
        Log.d("onCreate", "UA: $userAgent")

        webView.settings.userAgentString = userAgent
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.setWebViewClient(MyWebViewClient())

        ViewCompat.setOnApplyWindowInsetsListener(
            binding.main
        ) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Handle Intent
        Log.d("onCreate", "getAction: ${intent.action}")
        Log.d("onCreate", "getData: ${intent.data}")
        Log.d("onCreate", "getExtras: ${intent.extras}")
        handleIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("onNewIntent", "intent: $intent")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        Log.d("handleIntent", "uri: $uri")

        //String mimeType = getContentResolver().getType(uri);
        val mimeType = intent.type
        Log.d("handleIntent", "mimeType: $mimeType")

        val action = intent.action
        Log.d("handleIntent", "action: $action")

        if (Intent.ACTION_MAIN == action) {
            Log.d("handleIntent", "ACTION_MAIN")
            val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val savedUrl = preferences.getString(URL_KEY, null)
            Log.d("handleIntent", "savedUrl: $savedUrl")
            val authToken = preferences.getString(TOKEN_KEY, null)
            Log.d("handleIntent", "authToken: $authToken")

            val currentUrl = webView.url
            Log.d("handleIntent", "currentUrl: $currentUrl")

            if (savedUrl.isNullOrEmpty()) {
                showSettingsDialog()
            } else {
                if (currentUrl == null) {
                    Log.d("handleIntent", "webView.loadUrl")
                    webView.loadUrl(savedUrl)
                } else {
                    Log.d("handleIntent", "SKIPPING  webView.loadUrl")
                }
            }
        } else if (Intent.ACTION_VIEW == action) {
            Log.d("handleIntent", "ACTION_VIEW")
            if (uri != null) {
                val scheme = uri.scheme
                Log.d("handleIntent", "scheme: $scheme")
                val host = uri.host
                Log.d("handleIntent", "host: $host")
                if ("djangofiles" == scheme) {
                    if ("serverlist" == host) {
                        Log.d("handleIntent", "djangofiles://serverlist")
                        showSettingsDialog()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.tst_error) + ": Unknown DeepLink",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d("handleIntent", "Unknown DeepLink!")
                        finish()
                    }
                } else {
                    Log.d("handleIntent", "processSharedFile: $uri")
                    processSharedFile(uri)
                }
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.tst_error) + ": Unknown Intent",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("IntentDebug", "Unknown Intent!")
                finish()
            }
        } else if (Intent.ACTION_SEND == action && mimeType != null) {
            Log.d("handleIntent", "ACTION_SEND")
            if ("text/plain" == mimeType) {
                val sharedText: String? = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    Log.d("handleIntent", "Received text/plain: $sharedText")
                    if (sharedText.startsWith("content://")) {
                        val fileUri = sharedText.toUri()
                        Log.d("handleIntent", "Received URI: $fileUri")
                    } else {
                        Log.d("handleIntent", "Received text/plain: $sharedText")
                    }
                }
                val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val savedUrl = preferences.getString(URL_KEY, null)
                Log.d("handleIntent", "savedUrl: ${savedUrl}/paste/")
                webView.loadUrl("${savedUrl}/paste/")
                Toast.makeText(
                    this,
                    this.getString(R.string.tst_not_implemented),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                //val fileUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (fileUri != null) {
                    processSharedFile(fileUri)
                } else {
                    Log.w("handleIntent", "URI is NULL")
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            Log.d("handleIntent", "ACTION_SEND_MULTIPLE")
            //val fileUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            val fileUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (fileUris != null) {
                for (fileUri in fileUris) {
                    processSharedFile(fileUri)
                }
            } else {
                Log.w("handleIntent", "URI is NULL")
            }
        } else {
            Toast.makeText(this, "Unknown Intent!", Toast.LENGTH_SHORT).show()
            Log.w("handleIntent", "All Intent Types Processed. No Match!")
        }
    }

    private fun showSettingsDialog() {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUrl = preferences.getString(URL_KEY, null)
        Log.d("showSettingsDialog", "savedUrl: $savedUrl")


        // Inflate custom layout with padding
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(5, 0, 5, 120)

        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        input.maxLines = 1
        layout.addView(input)
        input.hint = getString(R.string.settings_input_place)
        if (savedUrl != null) {
            input.setText(savedUrl)
        }
        input.requestFocus()

        runOnUiThread {
            AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(getString(R.string.settings_title))
                .setMessage(getString(R.string.settings_message))
                .setView(layout)
                .setNegativeButton("Exit") { dialog: DialogInterface?, which: Int -> finish() }
                .setPositiveButton("OK", null)
                .show().apply {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        var url = input.text.toString().trim { it <= ' ' }
                        Log.d("showSettingsDialog", "setPositiveButton: url: $url")

                        if (url.isEmpty()) {
                            Log.d("showSettingsDialog", "URL is Empty")
                            input.error = "This field is required."
                        } else {
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                url = "https://$url"
                            }
                            if (url.endsWith("/")) {
                                url = url.substring(0, url.length - 1)
                            }

                            Log.d("showSettingsDialog", "Processed URL: $url")
                            if (savedUrl != url) {
                                Log.d("showSettingsDialog", "Saving New URL...")
                                CoroutineScope(Dispatchers.IO).launch {
                                    val authUrl = "${url}/api/auth/methods/"
                                    Log.d("showSettingsDialog", "Auth URL: $authUrl")
                                    val response = checkUrl(authUrl)
                                    Log.d("showSettingsDialog", "response: $response")
                                    withContext(Dispatchers.Main) {
                                        if (response) {
                                            Log.d("showSettingsDialog", "SUCCESS")
                                            preferences.edit { putString(URL_KEY, url) }
                                            webView.loadUrl(url)
                                            dismiss()
                                        } else {
                                            Log.d("showSettingsDialog", "FAILURE")
                                            input.error = "Invalid URL"
                                        }
                                    }
                                }
                                //preferences.edit { putString(URL_KEY, url) }
                                //webView.loadUrl(url)
                                //dismiss()
                            } else {
                                Log.d("showSettingsDialog", "URL NOT Changed!")
                                finish()
                            }
                        }
                    }
                }
        }
    }

    private fun checkUrl(url: String): Boolean {
        Log.d("checkUrl", "url: $url")
        // TODO: Change this to HEAD or use response data...
        val request = Request.Builder().url(url).get().build()
        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private fun processSharedFile(fileUri: Uri) {
        Log.d("processSharedFile", "fileUri: $fileUri")
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUrl = preferences.getString(URL_KEY, null)
        Log.d("processSharedFile", "savedUrl: $savedUrl")
        val authToken = preferences.getString(TOKEN_KEY, null)
        Log.d("processSharedFile", "authToken: $authToken")
        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Toast.makeText(this, getString(R.string.tst_no_url), Toast.LENGTH_SHORT).show()
            return
        }

        val file = getInputStreamFromUri(fileUri)
        if (file == null) {
            Toast.makeText(this, "Unable To Process Content!", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = getFileNameFromUri(fileUri)
        Log.d("processSharedFile", "fileName: $fileName")

        val uploadUrl = "$savedUrl/api/upload"
        Log.d("processSharedFile", "uploadUrl: $uploadUrl")
        val contentType = URLConnection.guessContentTypeFromName(fileName)
        Log.d("processSharedFile", "contentType: $contentType")

        Toast.makeText(this, getString(R.string.tst_uploading_file), Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val url = URL(uploadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", authToken)
                val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
                connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )
                connection.connect()

                val outputStream = DataOutputStream(connection.outputStream)

                // Write the boundary and the necessary headers
                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                outputStream.writeBytes("Content-Type: $contentType\r\n")
                outputStream.writeBytes("Content-Transfer-Encoding: binary\r\n\r\n")

                // Write the file content
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while ((file.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                file.close()

                // End the multipart request
                outputStream.writeBytes("\r\n--$boundary--\r\n")
                outputStream.flush()
                outputStream.close()

                // Get the response code
                val responseCode = connection.responseCode
                Log.d("processSharedFile", "responseCode: $responseCode")
                val responseMessage = connection.responseMessage
                Log.d("processSharedFile", "responseMessage: $responseMessage")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonURL = parseJsonResponse(connection)
                    runOnUiThread { copyToClipboard(jsonURL!!) }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.tst_error) + ": " + responseMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.tst_error_uploading),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun getInputStreamFromUri(uri: Uri): InputStream? {
        return try {
            contentResolver.openInputStream(uri)
        } catch (e: IOException) {
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun parseJsonResponse(connection: HttpURLConnection): String? {
        try {
            Log.d("parseJsonResponse", "Begin.")
            val `in` = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var inputLine: String?
            while ((`in`.readLine().also { inputLine = it }) != null) {
                response.append(inputLine)
            }
            `in`.close()

            Log.d("parseJsonResponse", "response: $response")
            val jsonResponse = JSONObject(response.toString())
            Log.d("parseJsonResponse", "JSONObject: $jsonResponse")

            val name = jsonResponse.getString("name")
            val raw = jsonResponse.getString("raw")
            val url = jsonResponse.getString("url")

            Log.d("parseJsonResponse", "Name: $name")
            Log.d("parseJsonResponse", "RAW: $raw")
            Log.d("parseJsonResponse", "URL: $url")

            return url
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun copyToClipboard(url: String) {
        webView.loadUrl(url)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.tst_url_copied), Toast.LENGTH_SHORT).show()
    }

    inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.d("shouldOverrideUrlLoading", "url: $url")

            val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val savedUrl = preferences.getString(URL_KEY, null)
            Log.d("shouldOverrideUrlLoading", "savedUrl: $savedUrl")

            if ((savedUrl != null &&
                        url.startsWith(savedUrl) && !url.startsWith("$savedUrl/r/") && !url.startsWith(
                    "$savedUrl/raw/"
                )) ||
                url.startsWith("https://discord.com/oauth2") ||
                url.startsWith("https://github.com/sessions/two-factor/app") ||
                url.startsWith("https://github.com/login") ||
                url.startsWith("https://accounts.google.com/v3/signin") ||
                url.startsWith("https://accounts.google.com/o/oauth2/v2/auth")
            ) {
                Log.d("shouldOverrideUrlLoading", "FALSE - in app")
                return false
            }
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            view.context.startActivity(intent)
            Log.d("shouldOverrideUrlLoading", "TRUE - in browser")
            return true
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceError
        ) {
            Log.d("onReceivedError", "ERROR: " + errorResponse.errorCode)
            // TODO: This does not seem to be helpful...
            //Toast.makeText(
            //    view.context,
            //    "HTTP error " + errorResponse.description,
            //    Toast.LENGTH_LONG
            //).show()
            // TODO: Now that we verify the URL this should not be needed...
            //showSettingsDialog()
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            Log.d("onReceivedHttpError", "ERROR: " + errorResponse.statusCode)
            // TODO: This does not seem to be helpful...
            //Toast.makeText(
            //    view.context,
            //    "HTTP error " + errorResponse.reasonPhrase,
            //    Toast.LENGTH_LONG
            //).show()
            // TODO: Now that we verify the URL this should not be needed...
            //showSettingsDialog()
        }
    }
}
