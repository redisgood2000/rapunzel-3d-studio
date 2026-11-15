package com.rapunzel.studio

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var activeDownloadId: Long = -1L
    private lateinit var downloadManager: DownloadManager

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != activeDownloadId) return
            val query = DownloadManager.Query().setFilterById(id)
            downloadManager.query(query).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(this@MainActivity, "Update downloaded. Tap INSTALL UPDATE.", Toast.LENGTH_LONG).show()
                        webView.evaluateJavascript("window.updateDownloadFinished && window.updateDownloadFinished(true)", null)
                    } else {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Toast.makeText(this@MainActivity, "Update download failed ($reason).", Toast.LENGTH_LONG).show()
                        webView.evaluateJavascript("window.updateDownloadFinished && window.updateDownloadFinished(false)", null)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(35, 19, 54)
        window.navigationBarColor = Color.rgb(35, 19, 54)
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(downloadReceiver, filter)

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(35, 19, 54))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(UpdateBridge(), "AndroidUpdater")
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {}
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/model-viewer.html")
    }

    inner class UpdateBridge {
        @JavascriptInterface
        fun getAppVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun downloadUpdate(url: String) {
            runOnUiThread {
                try {
                    val uri = Uri.parse(url)
                    val host = uri.host.orEmpty().lowercase()
                    if (uri.scheme != "https" || (host != "github.com" && host != "release-assets.githubusercontent.com")) {
                        Toast.makeText(this@MainActivity, "Blocked an untrusted update URL.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val request = DownloadManager.Request(uri)
                        .setTitle("Rapunzel 3D Studio update")
                        .setDescription("Downloading the latest app version")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "rapunzel-update.apk")
                    activeDownloadId = downloadManager.enqueue(request)
                    Toast.makeText(this@MainActivity, "Update download started.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not start update download.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun installDownloadedUpdate() {
            runOnUiThread { installUpdateApk() }
        }
    }

    private fun installUpdateApk() {
        val apk = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "rapunzel-update.apk")
        if (!apk.exists() || apk.length() == 0L) {
            Toast.makeText(this, "Download the update first.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            startActivity(settingsIntent)
            Toast.makeText(this, "Allow installs for Rapunzel 3D Studio, then return and tap INSTALL UPDATE.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open the APK installer.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface("AndroidUpdater")
            webView.destroy()
        }
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
