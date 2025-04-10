package com.glassous.gleslite

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.glassous.gleslite.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var currentTitle: String = "未加载网页"
    private var isFullscreen = false // 跟踪全屏状态

    private val favoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedUrl = result.data?.getStringExtra("selected_url")
            selectedUrl?.let {
                binding.webView.loadUrl(it)
                binding.urlEditText.setText(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (application as App).mainActivity = this
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)

        // 恢复状态
        savedInstanceState?.let {
            isFullscreen = it.getBoolean("isFullscreen", false)
            currentTitle = it.getString("currentTitle", "未加载网页") ?: "未加载网页"
        }

        setupWebView()
        setupButtons()
        loadDefaultUrl()
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            setSupportZoom(true)
            setBuiltInZoomControls(true)
            setDisplayZoomControls(false)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            removeAllCookies(null)
        }

        binding.webView.clearCache(true)
        binding.webView.clearHistory()

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { binding.urlEditText.setText(it) }
                currentTitle = view?.title ?: "未加载网页"
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                Log.e("WebViewError", "Error code: $errorCode, Description: $description, URL: $failingUrl")
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = newProgress
                if (newProgress == 100) binding.progressBar.visibility = View.GONE
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
    }

    private fun setupButtons() {
        binding.goButton.setOnClickListener {
            var url = binding.urlEditText.text.toString()
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            binding.webView.loadUrl(url)
        }

        binding.clearButton.setOnClickListener { binding.urlEditText.setText("") }
        binding.forwardButton.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.backButton.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.refreshButton.setOnClickListener { binding.webView.reload() }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun loadDefaultUrl() {
        var defaultUrl = sharedPreferences.getString("default_url", "https://www.bing.com") ?: "https://www.bing.com"
        if (!defaultUrl.startsWith("http://") && !defaultUrl.startsWith("https://")) defaultUrl = "https://$defaultUrl"
        binding.urlEditText.setText(defaultUrl)
        binding.webView.loadUrl(defaultUrl)
    }

    // 进入全屏模式
    private fun enterFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            exitFullscreen()
            return
        }

        customView = view
        customViewCallback = callback
        isFullscreen = true

        // 设置全屏
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        // 添加全屏视图
        binding.fullscreenContainer.addView(customView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 隐藏所有UI
        binding.fullscreenContainer.visibility = View.VISIBLE
        binding.webView.visibility = View.GONE
        binding.buttonContainer.visibility = View.GONE
        binding.urlBar.visibility = View.GONE
        binding.progressBar.visibility = View.GONE

        // 可选：添加淡入动画
        binding.fullscreenContainer.alpha = 0f
        binding.fullscreenContainer.animate().alpha(1f).setDuration(200).start()
    }

    // 退出全屏模式
    private fun exitFullscreen() {
        if (customView == null) return

        // 可选：添加淡出动画
        binding.fullscreenContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                binding.fullscreenContainer.visibility = View.GONE
                binding.fullscreenContainer.removeAllViews()
                binding.webView.visibility = View.VISIBLE
                binding.buttonContainer.visibility = View.VISIBLE
                binding.urlBar.visibility = View.VISIBLE

                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                isFullscreen = false

                Toast.makeText(this, "已退出全屏，按钮已恢复", Toast.LENGTH_SHORT).show()
            }.start()
    }

    override fun onBackPressed() {
        when {
            isFullscreen -> exitFullscreen()
            binding.webView.canGoBack() -> binding.webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isFullscreen", isFullscreen)
        outState.putString("currentTitle", currentTitle)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 处理屏幕旋转
        if (isFullscreen && customView != null) {
            binding.fullscreenContainer.removeAllViews()
            binding.fullscreenContainer.addView(customView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFullscreen) exitFullscreen()
        binding.webView.destroy()
    }

    fun openFavoritesActivity() {
        val currentUrl = binding.urlEditText.text.toString()
        val favorite = Favorite(currentTitle, currentUrl)
        val intent = Intent(this, FavoritesActivity::class.java).apply {
            putExtra("current_favorite", favorite)
        }
        favoritesLauncher.launch(intent)
    }
}