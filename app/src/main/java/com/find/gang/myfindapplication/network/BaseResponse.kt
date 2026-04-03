package com.find.gang.myfindapplication.network

/**
 * 通用网络响应基类
 * @param code 响应状态码（例如 0 表示成功，其他表示失败）
 * @param msg 响应消息
 * @param data 实际数据，类型由泛型 T 决定
 */
data class BaseResponse<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null
) {
    /**
     * 判断请求是否成功
     * 根据实际后端约定修改判断条件（例如 code == 0 或 code == 200）
     */
    fun isSuccess(): Boolean = code == 0

    /**
     * 获取错误信息，如果成功则返回空字符串
     */
    fun getErrorMessage(): String = if (isSuccess()) "" else msg
}