package com.find.gang.third.ui.video

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Matcher
import java.util.regex.Pattern

class HlsParser {
    companion object {
        // M3U8 标签常量
        const val EXT_M3U: String = "#EXTM3U"

        const val EXT_X_STREAM_INF: String = "#EXT-X-STREAM-INF"

        const val EXT_X_VERSION: String = "#EXT-X-VERSION"

        const val EXT_X_MEDIA: String = "#EXT-X-MEDIA"

        const val EXT_X_I_FRAME_STREAM_INF: String = "#EXT-X-I-FRAME-STREAM-INF"

        const val EXT_X_KEY: String = "#EXT-X-KEY"

        const val EXTINF: String = "#EXTINF"

        const val EXT_X_ENDLIST: String = "#EXT-X-ENDLIST"


        @Throws(IOException::class)
        public fun parseMasterPlaylist(masterUrl: String): HlsMasterPlaylist {
            val playlistContent = downloadPlaylist(masterUrl)
            return parseMasterPlaylistContent(masterUrl, playlistContent)
        }

        @Throws(IOException::class)
        fun parseMediaPlaylist(mediaUrl: String): HlsMediaPlaylist {
            val playlistContent = downloadPlaylist(mediaUrl)
            return parseMediaPlaylistContent(mediaUrl, playlistContent)
        }

        @Throws(IOException::class)
        private fun downloadPlaylist(url: String): String {
            val client = OkHttpClient()
            val request: Request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                return response.body?.string().toString()
            }
        }

        private fun parseMasterPlaylistContent(baseUrl: String, content: String): HlsMasterPlaylist {
            val lines = content.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var version = 1

            val variants: MutableList<HlsUrl> = ArrayList()
            val iFramePlaylists: MutableList<HlsUrl> = ArrayList()
            val renditions: MutableList<Rendition> = ArrayList()

            var currentAttributes: Map<String, String>? = mapOf()

            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) {
                    i++
                    continue
                }

                if (line.startsWith(EXT_X_VERSION)) {
                    version = line.substring(EXT_X_VERSION.length + 1).trim().toInt()
                } else if (line.startsWith(EXT_X_STREAM_INF)) {
                    currentAttributes = parseAttributes(line.substring(EXT_X_STREAM_INF.length))
                } else if (line.startsWith(EXT_X_I_FRAME_STREAM_INF)) {
                    val attrs = parseAttributes(line.substring(EXT_X_I_FRAME_STREAM_INF.length))
                    if (i + 1 < lines.size && !lines[i + 1].startsWith("#")) {
                        val uri = resolveUrl(baseUrl, lines[++i].trim())
                        val hlsUrl = HlsUrl()
                        hlsUrl.url = uri!!
                        hlsUrl.bandwidth = parseIntAttr(attrs, "BANDWIDTH", 0)
                        iFramePlaylists.add(hlsUrl)
                    }
                } else if (line.startsWith(EXT_X_MEDIA)) {
                    val attrs = parseAttributes(line.substring(EXT_X_MEDIA.length))
                    val rendition = Rendition()
                    rendition.type = attrs["TYPE"]!!
                    rendition.groupId = attrs["GROUP-ID"]!!
                    rendition.name = attrs["NAME"]!!
                    rendition.language = attrs["LANGUAGE"]!!
                    rendition.uri = resolveUrl(baseUrl, attrs["URI"])!!
                    renditions.add(rendition)
                } else if (!line.startsWith("#")) {
                    val uri = resolveUrl(baseUrl, line)
                    val variant = HlsUrl()
                    variant.url = uri!!
                    variant.bandwidth = parseIntAttr(currentAttributes, "BANDWIDTH", 0)
                    variant.resolution = parseResolution(currentAttributes)
                    variant.codecs = parseCodecs(currentAttributes)
                    variants.add(variant)
                    currentAttributes = null
                }
                i++
            }

            return HlsMasterPlaylist(
                baseUrl,
                variants,
                iFramePlaylists,
                renditions,
                emptyMap(),
                version
            )
        }

        private fun parseCodecs(attributes: Map<String, String>?): Array<String> {
            val codecs = attributes?.get("CODECS")
            if (codecs != null) {
                return codecs.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            }
            return arrayOf()
        }

        private fun parseResolution(attributes: Map<String, String>?): String {
            return attributes?.get("RESOLUTION").toString()
        }

        private fun parseMediaPlaylistContent(baseUrl: String, content: String): HlsMediaPlaylist {
            val lines = content.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val segments: MutableList<Segment> = ArrayList()
            var currentKeyInfo: Map<String, String>? = null
            var duration = 0.0
            var isEncrypted = false
            var encryptionMethod: String? = null
            var encryptionUri: String? = null
            var encryptionIv: String? = null
            var encryptionKeyFormat: String? = null

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue

                if (line.startsWith(EXT_X_KEY)) {
                    currentKeyInfo = parseAttributes(line.substring(EXT_X_KEY.length))
                    isEncrypted = true
                    encryptionMethod = currentKeyInfo["METHOD"]
                    encryptionUri = resolveUrl(baseUrl, currentKeyInfo["URI"])
                    encryptionIv = currentKeyInfo["IV"]
                    encryptionKeyFormat = currentKeyInfo["KEYFORMAT"]
                } else if (line.startsWith(EXTINF)) {
                    // 解析时长: #EXTINF:6.006,
                    val pattern: Pattern = Pattern.compile("#EXTINF:([\\d.]+),")
                    val matcher: Matcher = pattern.matcher(line)
                    if (matcher.find()) {
                        duration = matcher.group(1)?.toDouble() ?: 0.0
                    }
                } else if (!line.startsWith("#") && line != EXT_X_ENDLIST) {
                    val uri = resolveUrl(baseUrl, line)
                    val segment = Segment()
                    segment.uri = uri!!
                    segment.duration = duration
                    segment.isEncrypted = isEncrypted
                    if (isEncrypted) {
                        segment.encryptionMethod = encryptionMethod!!
                        segment.encryptionUri = encryptionUri!!
                        segment.encryptionIv = encryptionIv!!
                        segment.encryptionKeyFormat = encryptionKeyFormat!!
                    }
                    segments.add(segment)
                }
            }

            return HlsMediaPlaylist(baseUrl, segments)
        }

        private fun parseAttributes(attributeLine: String): Map<String, String> {
            val attributes: MutableMap<String, String> = HashMap()
            val pairs = attributeLine.split(",".toRegex()).toTypedArray()

            for (pair in pairs) {
                val keyValue = pair.split("=".toRegex(), limit = 2).toTypedArray()
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    val value = keyValue[1].trim().replace("\"", "")
                    attributes[key] = value
                }
            }

            return attributes
        }

        private fun parseIntAttr(attrs: Map<String, String>?, key: String, defaultValue: Int): Int {
            val value = attrs?.get(key)
            return value?.toInt() ?: defaultValue
        }

        private fun resolveUrl(baseUrl: String, relativeUrl: String?): String? {
            if (relativeUrl == null || relativeUrl.startsWith("http")) {
                return relativeUrl
            }

            try {
                val baseUri = URI(baseUrl)
                return baseUri.resolve(relativeUrl).toString()
            } catch (e: URISyntaxException) {
                Log.e("HlsParser", "URL解析错误: $relativeUrl", e)
                return relativeUrl
            }
        }
    }

    public class HlsMasterPlaylist(
        val baseUrl: String, val variants: List<HlsUrl>,
        val iFramePlaylists: List<HlsUrl>,
        val renditions: List<Rendition>,
        val sessionKeyDrmData: Map<String, String>,
        val version: Int
    )

    class HlsMediaPlaylist(val baseUrl: String, val segments: List<Segment>)

    class HlsUrl {
        var url: String = ""
        var bandwidth: Int = 0
        var resolution: String = ""
        var codecs: Array<String> = arrayOf()
    }


    class Rendition {
        var type: String = ""
        var groupId: String = ""
        var name: String = ""
        var language: String = ""
        var uri: String = ""
    }

    class Segment {
        var uri: String = ""
        var duration: Double = 0.0
        var isEncrypted: Boolean = false
        var encryptionMethod: String = ""
        var encryptionUri: String = ""
        var encryptionIv: String = ""
        var encryptionKeyFormat: String = ""
    }
}