package com.test.gang.proxy.headers

/**
 * Empty [HeaderInjector] implementation.
 *
 * @author Lucas Nelaupe (https://github.com/lucas34).
 */
class EmptyHeadersInjector : HeaderInjector {
    override fun addHeaders(url: String?): Map<String, String> {
        return HashMap()
    }
}
