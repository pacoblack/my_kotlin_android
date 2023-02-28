package com.test.gang.proxy

import android.text.TextUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import kotlin.math.max

/**
 * Model for Http GET request.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class GetRequest(request: String) {
    val uri: String
    val rangeOffset: Long
    val partial: Boolean
    private fun findRangeOffset(request: String): Long {
        val matcher = RANGE_HEADER_PATTERN.matcher(request)
        if (matcher.find()) {
            val rangeValue = matcher.group(1)
            return rangeValue.toLong()
        }
        return -1
    }

    private fun findUri(request: String): String {
        val matcher = URL_PATTERN.matcher(request)
        if (matcher.find()) {
            return matcher.group(1)
        }
        throw IllegalArgumentException("Invalid request `$request`: url not found!")
    }

    override fun toString(): String {
        return "GetRequest{" +
                "rangeOffset=" + rangeOffset +
                ", partial=" + partial +
                ", uri='" + uri + '\'' +
                '}'
    }

    companion object {
        private val RANGE_HEADER_PATTERN = Pattern.compile("[R,r]ange:[ ]?bytes=(\\d*)-")
        private val URL_PATTERN = Pattern.compile("GET /(.*) HTTP")
        @Throws(IOException::class)
        fun read(inputStream: InputStream?): GetRequest {
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val stringRequest = StringBuilder()
            var line: String?
            while (!TextUtils.isEmpty(
                    reader.readLine().also { line = it })
            ) { // until new line (headers ending)
                stringRequest.append(line).append('\n')
            }
            return GetRequest(stringRequest.toString())
        }
    }

    init {
        Preconditions.checkNotNull(request)
        val offset = findRangeOffset(request)
        rangeOffset = max(0, offset)
        partial = offset >= 0
        uri = findUri(request)
    }
}
