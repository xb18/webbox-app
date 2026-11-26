package com.xb18.webbox

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast

/**
 * 全屏网页容器：WebView 铺满屏幕，不注入任何 JS，靠系统返回键导航。
 * 状态栏透明，原生采样网页顶部像素亮度自动切换图标颜色（浅底深图标 / 深底白图标）。
 */
class WebViewActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var targetUrl: String = ""
    private var lastBackPress = 0L
    private var loadFailed = false
    private var lightIcons = false
    private var lastSampleAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val sampleRunnable = Runnable { sampleStatusBar() }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        private const val SAMPLE_INTERVAL_MS = 120L
        private const val BRIGHTNESS_THRESHOLD = 128
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏透明，图标颜色由采样结果动态决定
        window.statusBarColor = Color.TRANSPARENT
        targetUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        if (targetUrl.isBlank()) {
            Toast.makeText(this, "无效网址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        webView = object : WebView(this) {
            override fun onScrollChanged(l: Int, t: Int, oldL: Int, oldT: Int) {
                super.onScrollChanged(l, t, oldL, oldT)
                scheduleSample()
            }

            // 关闭悬浮复制菜单：保留文字选中，只拦截弹出的工具条
            override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
                if (!Prefs.textSelectionEnabled(this@WebViewActivity)) return null
                return super.startActionMode(callback)
            }

            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
                if (!Prefs.textSelectionEnabled(this@WebViewActivity)) return null
                return super.startActionMode(callback, type)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString WebBoxShell/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (3 * resources.displayMetrics.density).toInt())
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: Exception) { /* 无可用应用则忽略 */ }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                handler.postDelayed({ scheduleSample() }, 150)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    loadFailed = true
                    progress.visibility = View.GONE
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        val container = FrameLayout(this).apply {
            addView(webView)
            addView(progress, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP))
        }
        setContentView(container)

        if (savedInstanceState == null) webView.loadUrl(targetUrl)
        else webView.restoreState(savedInstanceState)
    }

    /** 节流采样，避免滚动时每帧抓图。 */
    private fun scheduleSample() {
        val now = System.currentTimeMillis()
        if (now - lastSampleAt < SAMPLE_INTERVAL_MS) return
        lastSampleAt = now
        handler.removeCallbacks(sampleRunnable)
        handler.postDelayed(sampleRunnable, SAMPLE_INTERVAL_MS)
    }

    /** 抓 WebView 当前帧，采样顶部状态栏区域的平均亮度，切换图标颜色。 */
    private fun sampleStatusBar() {
        if (isFinishing || !::webView.isInitialized) return
        val barH = statusBarHeightPx()
        if (barH <= 0 || webView.width <= 0) return
        try {
            val bmp = Bitmap.createBitmap(webView.width, barH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            webView.draw(canvas)
            var sum = 0L
            var count = 0
            val cols = 5
            for (c in 0 until cols) {
                val x = (webView.width - 1) * c / (cols - 1)
                for (y in listOf(barH / 4, barH / 2, barH * 3 / 4)) {
                    val color = bmp.getPixel(x, y)
                    val lum = (0.299 * android.graphics.Color.red(color) +
                        0.587 * android.graphics.Color.green(color) +
                        0.114 * android.graphics.Color.blue(color)).toLong()
                    sum += lum
                    count++
                }
            }
            bmp.recycle()
            val avg = if (count > 0) sum / count else BRIGHTNESS_THRESHOLD.toLong()
            setLightIcons(avg > BRIGHTNESS_THRESHOLD)
        } catch (_: Exception) {
            // 采样失败保持现状，不影响网页显示
        }
    }

    /** light=true 表示背景浅色，用深色图标。 */
    private fun setLightIcons(light: Boolean) {
        if (light == lightIcons) return
        lightIcons = light
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
    }

    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) { webView.goBack(); return }
        val now = System.currentTimeMillis()
        if (now - lastBackPress > 2000) {
            Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
            lastBackPress = now
        } else {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(sampleRunnable)
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        scheduleSample()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sampleRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
