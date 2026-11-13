package ru.srr.vpscockpit

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var web: WebView
    private var loaded = false
    private var opening = false
    private var cyberJsCache: String? = null

    private fun cyberJsSource(): String {
        cyberJsCache?.let { return it }
        val src = assets.open("vps-cyber.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        cyberJsCache = src
        return src
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startTunnelService() }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TunnelEvents.ACTION_STATUS) return
            applyTunnelState(
                intent.getStringExtra(TunnelEvents.EXTRA_STATE).orEmpty(),
                intent.getStringExtra(TunnelEvents.EXTRA_MESSAGE).orEmpty()
            )
        }
    }

    private fun applyTunnelState(state: String, message: String) {
        if (message.isNotBlank()) status.text = message
        when (state) {
            TunnelEvents.STATE_CONNECTING -> {
                status.visibility = View.VISIBLE
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
            }
            TunnelEvents.STATE_READY -> {
                openCockpitWhenPortReady(message)
            }
            TunnelEvents.STATE_FAILED -> {
                status.visibility = View.VISIBLE
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
                web.visibility = View.GONE
                loaded = false
            }
            TunnelEvents.STATE_STOPPED -> {
                progress.visibility = View.GONE
                status.visibility = View.VISIBLE
                web.visibility = View.GONE
                loaded = false
            }
            TunnelEvents.STATE_HOSTKEY_CHANGED -> {
                progress.visibility = View.GONE
                status.visibility = View.VISIBLE
                web.visibility = View.GONE
                loaded = false
                showHostKeyDialog(message)
            }
        }
    }

    private var hostKeyDialog: AlertDialog? = null

    private fun showHostKeyDialog(message: String) {
        if (hostKeyDialog?.isShowing == true || isFinishing) return
        hostKeyDialog = AlertDialog.Builder(this)
            .setTitle(R.string.hostkey_dialog_title)
            .setMessage(message + "\n\n" + getString(R.string.hostkey_dialog_hint))
            .setCancelable(false)
            .setNegativeButton(R.string.hostkey_dialog_cancel, null)
            .setPositiveButton(R.string.hostkey_dialog_reset) { _, _ ->
                val cfg = TunnelConfigStore.load(this)
                HostKeyPins.forget(this, cfg.host, cfg.sshPort)
                startTunnelService()
            }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.PUBLIC_BUILD && !TunnelConfigStore.hasSaved(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        web = findViewById(R.id.web)

        if (BuildConfig.PUBLIC_BUILD) {
            status.setOnLongClickListener {
                openServerSettings()
                true
            }
        }

        // Keep WebView above system nav — otherwise Cockpit bottom tabs
        // (Система/Узел/Помощь/Сеанс) draw under the Android buttons.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(top = bars.top, bottom = nav.bottom)
            insets
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // Keep UI denser on Ultra-tall phones
            textZoom = 90
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString =
                "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                false

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                if (url.contains("vps-mobile.css")) {
                    return try {
                        val bytes = assets.open("vps-mobile.css").use { it.readBytes() }
                        WebResourceResponse(
                            "text/css",
                            "utf-8",
                            ByteArrayInputStream(bytes)
                        )
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (url.contains("vps-cyber.js")) {
                    return try {
                        val bytes = assets.open("vps-cyber.js").use { it.readBytes() }
                        WebResourceResponse(
                            "application/javascript",
                            "utf-8",
                            ByteArrayInputStream(bytes)
                        )
                    } catch (_: Throwable) {
                        null
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectMobileAdapt(view)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                injectMobileAdapt(view)
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (TunnelTls.mayProceed(error?.url)) handler?.proceed() else handler?.cancel()
            }
        }

        // Tunnel start moved to onStart — register receiver first, then sync state.
    }

    private fun injectMobileAdapt(view: WebView?) {
        val wv = view ?: return
        // CSS via intercepted same-origin URL (CSP-safe vs inline <style>)
        wv.evaluateJavascript(
            """
            (function(){
              try {
                document.documentElement.scrollLeft = 0;
                document.body && (document.body.scrollLeft = 0);
                if (window.scrollTo) window.scrollTo(0, window.scrollY || 0);
              } catch (e) {}
              var m=document.querySelector('meta[name=viewport]');
              if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
              m.setAttribute('content','width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes');
              document.documentElement.setAttribute('data-mc-theme','matrix-core');
              var href='/vps-mobile.css?v=19';
              var l=document.getElementById('vps-cockpit-mobile-link');
              if(!l){
                l=document.createElement('link');
                l.id='vps-cockpit-mobile-link';
                l.rel='stylesheet';
                l.href=href;
                document.head.appendChild(l);
              } else if (l.href.indexOf('v=19') < 0) {
                l.href=href;
              }
              try {
                document.querySelectorAll('iframe.container-frame').forEach(function(f){
                  var d=f.contentDocument; if(!d||!d.head) return;
                  var ll=d.getElementById('vps-cockpit-mobile-link');
                  if(!ll){ ll=d.createElement('link'); ll.id='vps-cockpit-mobile-link'; ll.rel='stylesheet'; d.head.appendChild(ll); }
                  ll.href='/vps-mobile.css?v=19';
                  d.documentElement.setAttribute('data-mc-theme','matrix-core');
                });
              } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
        // HUD JS via evaluateJavascript — bypasses Cockpit CSP script-src
        try {
            wv.evaluateJavascript(cyberJsSource(), null)
        } catch (_: Throwable) {
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(TunnelEvents.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
        ensureNotificationPermissionAndStart()
        // Replay only after receiver is up; never trust stale READY without a live local port.
        when (SshTunnelService.lastState) {
            TunnelEvents.STATE_READY -> openCockpitWhenPortReady(SshTunnelService.lastMessage)
            TunnelEvents.STATE_CONNECTING,
            TunnelEvents.STATE_FAILED,
            TunnelEvents.STATE_STOPPED ->
                applyTunnelState(SshTunnelService.lastState, SshTunnelService.lastMessage)
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Throwable) {
        }
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.visibility == View.VISIBLE && web.canGoBack()) {
            web.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun ensureNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTunnelService()
        }
    }

    private fun startTunnelService() {
        val cfg = TunnelConfigStore.load(this)
        val intent = TunnelConfigStore.putExtras(Intent(this, SshTunnelService::class.java), cfg)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun openCockpitWhenPortReady(readyMessage: String) {
        if (opening) return
        opening = true
        status.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        if (readyMessage.isNotBlank()) {
            status.text = readyMessage
        } else {
            status.text = getString(R.string.tunnel_ready)
        }
        val port = TunnelConfigStore.load(this).localPort
        thread(name = "cockpit-port-wait", isDaemon = true) {
            var ok = false
            repeat(40) { // ~10s
                if (isLocalPortOpen(port)) {
                    ok = true
                    return@repeat
                }
                try {
                    Thread.sleep(250L)
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
            runOnUiThread {
                opening = false
                if (ok) {
                    progress.visibility = View.GONE
                    status.visibility = View.GONE
                    openCockpit(forceReload = loaded)
                } else {
                    status.visibility = View.VISIBLE
                    progress.visibility = View.VISIBLE
                    status.text = "Туннель есть, но :$port ещё не отвечает. Жду…"
                    // Ask service to re-assert status / reconnect path
                    startTunnelService()
                }
            }
        }
    }

    private fun isLocalPortOpen(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 400)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun openCockpit(forceReload: Boolean = false) {
        web.visibility = View.VISIBLE
        val url = "https://127.0.0.1:${TunnelConfigStore.load(this).localPort}/"
        if (!loaded) {
            loaded = true
            web.loadUrl(url)
        } else if (forceReload) {
            web.reload()
        }
    }

    fun openServerSettings() {
        if (!BuildConfig.PUBLIC_BUILD) return
        startActivity(Intent(this, LoginActivity::class.java))
    }
}
