package com.find.gang.app.network

import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("v2/article/list")
    fun getArticleList(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Observable<BaseResponse<Any>>
}