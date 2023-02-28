package com.test.gang.proxy

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * [ProxySelector] that ignore system default proxies for concrete host.
 *
 *
 * It is important to [ignore system proxy](https://github.com/danikula/AndroidVideoCache/issues/28) for localhost connection.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class IgnoreHostProxySelector(
    defaultProxySelector: ProxySelector?,
    hostToIgnore: String?,
    portToIgnore: Int
) :
    ProxySelector() {
    private val defaultProxySelector: ProxySelector
    private val hostToIgnore: String
    private val portToIgnore: Int
    override fun select(uri: URI): List<Proxy> {
        val ignored = hostToIgnore == uri.host && portToIgnore == uri.port
        return if (ignored) NO_PROXY_LIST else defaultProxySelector.select(uri)
    }

    override fun connectFailed(uri: URI, address: SocketAddress, failure: IOException) {
        defaultProxySelector.connectFailed(uri, address, failure)
    }

    companion object {
        private val NO_PROXY_LIST = mutableListOf(Proxy.NO_PROXY)
        fun install(hostToIgnore: String?, portToIgnore: Int) {
            val defaultProxySelector = getDefault()
            val ignoreHostProxySelector: ProxySelector =
                IgnoreHostProxySelector(defaultProxySelector, hostToIgnore, portToIgnore)
            setDefault(ignoreHostProxySelector)
        }
    }

    init {
        this.defaultProxySelector =
            Preconditions.checkNotNull(defaultProxySelector)
        this.hostToIgnore = Preconditions.checkNotNull(hostToIgnore)
        this.portToIgnore = portToIgnore
    }
}