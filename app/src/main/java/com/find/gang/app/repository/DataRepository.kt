package com.find.gang.app.repository

import com.find.gang.app.network.ApiService

/**
 * 数据仓库：统一管理数据源（网络、本地数据库、内存缓存）
 */
class DataRepository(private val apiService: ApiService) {

    companion object {
        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(apiService: ApiService): DataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataRepository(apiService).also { INSTANCE = it }
            }
        }
    }
}