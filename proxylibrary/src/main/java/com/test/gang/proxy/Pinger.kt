package com.test.gang.proxy

import com.test.gang.proxy.e.ProxyCacheException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.util.*
import java.util.concurrent.*

/**
 * Pings [HttpProxyCacheServer] to make sure it works.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class Pinger(host: String?, port: Int) {
    private val pingExecutor = Executors.newSingleThreadExecutor()
    private val host: String
    private val port: Int
    fun ping(maxAttempts: Int, startTimeout: Int): Boolean {
        Preconditions.checkArgument(maxAttempts >= 1)
        Preconditions.checkArgument(startTimeout > 0)
        var timeout = startTimeout
        var attempts = 0
        while (attempts < maxAttempts) {
            try {
                val pingFuture = pingExecutor.submit<Boolean>(PingCallable())
                val pinged = pingFuture[timeout.toLong(), TimeUnit.MILLISECONDS]
                if (pinged) {
                    return true
                }
            } catch (e: TimeoutException) {
                LOG.warn("Error pinging server (attempt: $attempts, timeout: $timeout). ")
            } catch (e: InterruptedException) {
                LOG.error("Error pinging server due to unexpected error", e)
            } catch (e: ExecutionException) {
                LOG.error("Error pinging server due to unexpected error", e)
            }
            attempts++
            timeout *= 2
        }
        val error = String.format(
            Locale.US, "Error pinging server (attempts: %d, max timeout: %d). " +
                    "If you see this message, please, report at https://github.com/danikula/AndroidVideoCache/issues/134. " +
                    "Default proxies are: %s", attempts, timeout / 2, defaultProxies
        )
        LOG.error(error, ProxyCacheException(error))
        return false
    }

    private val defaultProxies: List<Proxy>
        private get() = try {
            val defaultProxySelector = ProxySelector.getDefault()
            defaultProxySelector.select(URI(pingUrl))
        } catch (e: URISyntaxException) {
            throw IllegalStateException(e)
        }

    fun isPingRequest(request: String): Boolean {
        return PING_REQUEST == request
    }

    @Throws(IOException::class)
    fun responseToPing(socket: Socket) {
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 200 OK\n\n".toByteArray())
        out.write(PING_RESPONSE.toByteArray())
    }

    @Throws(ProxyCacheException::class)
    private fun pingServer(): Boolean {
        val pingUrl = pingUrl
        val source = HttpUrlSource(pingUrl)
        return try {
            val expectedResponse = PING_RESPONSE.toByteArray()
            source.open(0)
            val response = ByteArray(expectedResponse.size)
            source.read(response)
            val pingOk = Arrays.equals(expectedResponse, response)
            LOG.info("Ping response: `" + String(response) + "`, pinged? " + pingOk)
            pingOk
        } catch (e: ProxyCacheException) {
            LOG.error("Error reading ping response", e)
            false
        } finally {
            source.close()
        }
    }

    private val pingUrl: String
        private get() = String.format(Locale.US, "http://%s:%d/%s", host, port, PING_REQUEST)

    private inner class PingCallable : Callable<Boolean?> {
        @Throws(Exception::class)
        override fun call(): Boolean {
            return pingServer()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger("Pinger")
        private const val PING_REQUEST = "ping"
        private const val PING_RESPONSE = "ping ok"
    }

    init {
        this.host = Preconditions.checkNotNull<String>(host)
        this.port = port
    }
}