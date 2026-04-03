package com.find.gang.app.ui.webview

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.gson.Gson

class JsBridge(private val context: Context) {

    @JavascriptInterface
    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return "1.0.0"
    }

    @JavascriptInterface
    fun getUserInfo(): String {
        val userInfo = mapOf(
            "userId" to "12345",
            "userName" to "TestUser",
            "token" to "test_token_123"
        )
        return Gson().toJson(userInfo)
    }

    @JavascriptInterface
    fun callNativeMethod(method: String, params: String): String {
        // 处理H5调用原生方法的业务逻辑
        return when (method) {
            "getLocation" -> getLocation()
            "share" -> share(params)
            else -> "{\"code\": -1, \"message\": \"未知方法\"}"
        }
    }

    private fun getLocation(): String {
        // 获取位置信息的逻辑
        return "{\"code\": 0, \"data\": {\"lat\": 39.9042, \"lng\": 116.4074}}"
    }

    private fun share(params: String): String {
        // 分享逻辑
        return "{\"code\": 0, \"message\": \"分享成功\"}"
    }
}