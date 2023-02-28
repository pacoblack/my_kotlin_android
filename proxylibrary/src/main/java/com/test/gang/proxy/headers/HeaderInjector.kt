package com.test.gang.proxy.headers

/**
 * Allows to add custom headers to server's requests.
 *
 * @author Lucas Nelaupe (https://github.com/lucas34).
 */
interface HeaderInjector {
    /**
     * Adds headers to server's requests for corresponding url.
     *
     * @param url an url headers will be added for
     * @return a map with headers, where keys are header's names, and values are header's values. `null` is not acceptable!
     */
    fun addHeaders(url: String?): Map<String, String>
}
