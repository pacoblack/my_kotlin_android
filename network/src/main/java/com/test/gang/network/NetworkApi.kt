package com.test.gang.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class NetworkApi(private var iNetworkRequiredInfo: INetworkRequiredInfo) {

    //OkHttp客户端
    private var okHttpClient: OkHttpClient? = null

    /**
     * 配置OkHttp
     *
     * @return OkHttpClient
     */
    public fun getOkHttpClient(): OkHttpClient? {
        //不为空则说明已经配置过了，直接返回即可。
        if (okHttpClient == null) {
            //OkHttp构建器
            val builder: OkHttpClient.Builder = OkHttpClient.Builder()
            //设置缓存大小
            val cacheSize = 100L * 1024 * 1024
            //设置OkHttp网络缓存
            builder.cache(Cache(iNetworkRequiredInfo.applicationContext.cacheDir, cacheSize))
            //设置网络请求超时时长，这里设置为6s
            builder.connectTimeout(6, TimeUnit.SECONDS)
            //在这里添加拦截器，通过拦截器可以知道一些信息，这对于开发中是有所帮助的，后面给加上。
            // ...
            //当程序在debug过程中则打印数据日志，方便调试用。
            if (iNetworkRequiredInfo.isDebug) {
                //iNetworkRequiredInfo不为空且处于debug状态下则初始化日志拦截器
                val httpLoggingInterceptor = HttpLoggingInterceptor()
                //设置要打印日志的内容等级，BODY为主要内容，还有BASIC、HEADERS、NONE。
                httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                //将拦截器添加到OkHttp构建器中
                builder.addInterceptor(httpLoggingInterceptor)
            }
            //OkHttp配置完成
            okHttpClient = builder.build()
        }
        return okHttpClient
    }
}