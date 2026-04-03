package com.find.gang.app.ui.webview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.find.gang.app.base.BaseViewModel

class WebViewModel : BaseViewModel() {

    // 当前加载的 URL
    private val _url = MutableLiveData<String>()
    val url: LiveData<String> = _url

    // 页面加载进度 (0-100)
    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> = _progress

    // 页面标题
    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    // 是否可以后退
    private val _canGoBack = MutableLiveData<Boolean>()
    val canGoBack: LiveData<Boolean> = _canGoBack

    // 是否可以前进
    private val _canGoForward = MutableLiveData<Boolean>()
    val canGoForward: LiveData<Boolean> = _canGoForward

    // 页面加载错误信息
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // 是否显示加载进度条（用于控制 ProgressBar 可见性）
    private val _isLoading = MutableLiveData<Boolean>()
    override val isLoading: LiveData<Boolean> = _isLoading

    init {
        // 初始 URL 可以设置为一个默认值或空白
        _url.value = ""
        _progress.value = 0
        _canGoBack.value = false
        _canGoForward.value = false
        _isLoading.value = false
        _errorMessage.value = null
    }

    /**
     * 加载新 URL
     */
    fun loadUrl(newUrl: String) {
        val validUrl = if (newUrl.startsWith("http")) newUrl else "https://$newUrl"
        _url.value = validUrl
        _progress.value = 0
        _errorMessage.value = null
        _isLoading.value = true
    }

    /**
     * 更新页面加载进度（由 Fragment 中的 WebChromeClient 调用）
     */
    fun updateProgress(progress: Int) {
        _progress.value = progress
        if (progress >= 100) {
            _isLoading.value = false
        }
    }

    /**
     * 更新页面标题
     */
    fun updateTitle(title: String?) {
        _title.value = title ?: ""
    }

    /**
     * 更新导航状态（能否后退/前进）
     */
    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _canGoBack.value = canGoBack
        _canGoForward.value = canGoForward
    }

    /**
     * 页面加载失败时调用
     */
    fun onPageError(error: String) {
        _errorMessage.value = error
        _isLoading.value = false
    }

    /**
     * 重置错误状态（例如重新加载前）
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 页面加载完成时调用（可在此处理一些注入逻辑）
     */
    fun onPageFinished() {
        _isLoading.value = false
    }

    /**
     * 后退
     */
    fun goBack() {
        // 实际的 goBack 操作需要由 Fragment 调用 WebView 的方法
        // ViewModel 仅通知 Fragment 执行，或通过 LiveData 发送命令
        // 这里采用发送一个无数据的 Event 方式，但为了简单，Fragment 直接监听 canGoBack 并执行
    }

    /**
     * 前进
     */
    fun goForward() {
        // 同上
    }

    /**
     * 刷新当前页面
     */
    fun reload() {
        _url.value?.let { loadUrl(it) }
    }
}