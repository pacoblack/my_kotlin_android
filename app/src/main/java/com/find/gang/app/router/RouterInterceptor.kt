package com.find.gang.app.router

import android.content.Context
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.annotation.Interceptor
import com.alibaba.android.arouter.facade.callback.InterceptorCallback
import com.alibaba.android.arouter.facade.template.IInterceptor

/**
 * ARouter 全局拦截器
 * priority 值越小，优先级越高
 */
@Interceptor(priority = 1, name = "登录状态拦截器")
class RouterInterceptor : IInterceptor {

    private var context: Context? = null

    override fun init(context: Context) {
        this.context = context.applicationContext
        // 初始化时加载必要的配置，例如从 SharedPreferences 读取登录状态
    }

    override fun process(postcard: Postcard, callback: InterceptorCallback) {
        // 获取目标页面是否需要登录（通过 extra 参数传递）
        val needLogin = postcard.extra == 1  // 1 表示需要登录

        if (needLogin) {
            // 检查是否已登录（示例：从 SharedPreferences 读取）
            val isLogin = checkLoginStatus()
            if (isLogin) {
                // 已登录，继续跳转
                callback.onContinue(postcard)
            } else {
                // 未登录，中断跳转，跳转到登录页
                callback.onInterrupt(RuntimeException("请先登录"))
                // 可跳转到登录页面，登录成功后携带目标页面的路由信息继续跳转
                // ARouter.getInstance().build(RouterPath.LOGIN).with(postcard.extras).navigation()
            }
        } else {
            // 不需要登录，直接放行
            callback.onContinue(postcard)
        }
    }

    /**
     * 模拟检查登录状态
     */
    private fun checkLoginStatus(): Boolean {
        // 实际开发中可以从 SharedPreferences、DataStore 等获取
        return context?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            ?.getBoolean("is_login", false) ?: false
    }
}