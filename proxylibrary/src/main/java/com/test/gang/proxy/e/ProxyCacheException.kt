package com.test.gang.proxy.e

import com.test.gang.proxy.BuildConfig
/**
 * Indicates any error in work of [ProxyCache].
 *
 * @author Alexey Danilov
 */
open class ProxyCacheException : Exception {
    constructor(message: String) : super(message + LIBRARY_VERSION) {}
    constructor(message: String, cause: Throwable?) : super(message + LIBRARY_VERSION, cause) {}
    constructor(cause: Throwable?) : super("No explanation error$LIBRARY_VERSION", cause) {}

    companion object {
        private val LIBRARY_VERSION =
            ". Version: " + BuildConfig.VERSION_NAME
    }
}
