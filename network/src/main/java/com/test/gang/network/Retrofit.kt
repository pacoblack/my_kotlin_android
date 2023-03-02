package com.test.gang.network

import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitHelper {
    //retrofitHashMap
    private val retrofitHashMap: HashMap<String, Retrofit> = HashMap()

    //API访问地址
    private lateinit var mBaseUrl: String
    private lateinit var networkApi: NetworkApi
    /**
     * 配置Retrofit
     *
     * @param serviceClass 服务类
     * @return Retrofit
     */
    private fun getRetrofit(serviceClass: Class<*>): Retrofit? {
        if (retrofitHashMap[mBaseUrl + serviceClass.name] != null) {
            //刚才上面定义的Map中键是String，值是Retrofit，当键不为空时，必然有值，有值则直接返回。
            return retrofitHashMap[mBaseUrl + serviceClass.name]
        }
        //初始化Retrofit  Retrofit是对OKHttp的封装，通常是对网络请求做处理，也可以处理返回数据。
        //Retrofit构建器
        val builder = Retrofit.Builder()
        //设置访问地址
        builder.baseUrl(mBaseUrl)
        //设置OkHttp客户端，传入上面写好的方法即可获得配置后的OkHttp客户端。
        networkApi.getOkHttpClient()?.apply {
            builder.client(this)
            //设置数据解析器 会自动把请求返回的结果（json字符串）通过Gson转化工厂自动转化成与其结构相符的实体Bean
            builder.addConverterFactory(GsonConverterFactory.create())
            //设置请求回调，使用RxJava 对网络返回进行处理
            builder.addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            //retrofit配置完成
            val retrofit = builder.build()
            //放入Map中
            retrofitHashMap[mBaseUrl + serviceClass.name] = retrofit
            //最后返回即可
            return retrofit
        }
        return null
    }
}