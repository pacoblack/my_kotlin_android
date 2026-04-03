package com.find.gang.myfindapplication.ui.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.find.gang.myfindapplication.R
import com.find.gang.myfindapplication.base.BaseFragment
import com.find.gang.myfindapplication.databinding.FragmentWebviewBinding
import com.find.gang.myfindapplication.router.RouterPath

@Route(path = RouterPath.WEBVIEW)
class WebViewFragment : BaseFragment<FragmentWebviewBinding, WebViewModel>(R.layout.fragment_webview) {

    @Autowired(name = "url", required = false)
    @JvmField
    var url: String? = null

    override fun getViewModelClass(): Class<WebViewModel> = WebViewModel::class.java

    override fun initView() {
        ARouter.getInstance().inject(this)

        setupWebView()

        // 如果有传入URL则自动加载
        url?.let {
            binding.etUrl.setText(it)
            loadUrl(it)
        }

        setupListeners()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webview.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // 注入JS桥接对象 - Hybrid通信核心
            addJavascriptInterface(JsBridge(requireContext()), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    showLoading(true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    showLoading(false)
                    // 页面加载完成后可以注入额外的JS代码
                    injectJavaScript()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // 拦截URL，处理自定义scheme
                    url?.let {
                        if (it.startsWith("native://")) {
                            handleNativeScheme(it)
                            return true
                        }
                    }
                    return super.shouldOverrideUrlLoading(view, url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    binding.progressBar.progress = newProgress
                    binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    // 可以设置标题栏
                }
            }
        }
    }

    private fun injectJavaScript() {
        // 注入回调函数，让H5可以接收原生主动发送的消息
        binding.webview.evaluateJavascript(
            "window.nativeCallback = function(data) { console.log('Received from native:', data); }",
            null
        )
    }

    private fun handleNativeScheme(url: String) {
        // 处理原生自定义scheme调用
        when {
            url.contains("close") -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            url.contains("share") -> {
                showToast("分享功能")
            }
            // 处理其他自定义scheme
        }
    }

    private fun setupListeners() {
        binding.btnLoad.setOnClickListener {
            val inputUrl = binding.etUrl.text.toString()
            if (inputUrl.isNotEmpty()) {
                loadUrl(inputUrl)
            } else {
                showToast("请输入URL")
            }
        }
    }

    private fun loadUrl(url: String) {
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        binding.webview.loadUrl(fullUrl)
    }

    private fun showLoading(show: Boolean) {
        // 可以在ViewModel中管理loading状态
    }

    /**
     * 原生主动调用JS方法 - 发送数据到H5
     */
    fun sendToH5(methodName: String, data: String) {
        binding.webview.evaluateJavascript("javascript:$methodName('$data')", null)
    }

    override fun onResume() {
        super.onResume()
        binding.webview.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webview.onPause()
    }

    override fun onDestroyView() {
        binding.webview.destroy()
        super.onDestroyView()
    }
}