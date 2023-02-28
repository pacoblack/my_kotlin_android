package com.test.gang.proxy.e

/**
 * Indicates interruption error in work of [ProxyCache] fired by user.
 *
 * @author Alexey Danilov
 */
class InterruptedProxyCacheException : ProxyCacheException {
    constructor(message: String) : super(message) {}
    constructor(message: String, cause: Throwable?) : super(message, cause) {}
    constructor(cause: Throwable?) : super(cause) {}
}

