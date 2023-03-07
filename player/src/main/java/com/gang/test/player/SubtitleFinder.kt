package com.gang.test.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.gang.test.player.Utils.deviceLanguages
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*

class SubtitleFinder(private val activity: PlayerActivity, uri: Uri) {
    private val baseUri: Uri
    private var path: String? = uri.path
    private val urls: MutableList<Uri>
    @UnstableApi
    private fun addLanguage(lang: String, suffix: String) {
        urls.add(buildUri("$lang.$suffix"))
        urls.add(buildUri(Util.normalizeLanguageCode(lang) + "." + suffix))
    }

    private fun buildUri(suffix: String): Uri {
        val newPath = "$path.$suffix"
        return baseUri.buildUpon().path(newPath).build()
    }

    fun start() {
        // Prevent IllegalArgumentException in okhttp3.Request.Builder
        if (baseUri.toString().toHttpUrlOrNull() == null) {
            return
        }
        for (suffix in arrayOf("srt", "ssa", "ass")) {
            urls.add(buildUri(suffix))
            for (language in deviceLanguages) {
                addLanguage(language, suffix)
            }
        }
        urls.add(buildUri("vtt"))
        val subtitleFetcher = SubtitleFetcher(activity, urls)
        subtitleFetcher.start()
    }

    companion object {
        @JvmStatic
        fun isUriCompatible(uri: Uri): Boolean {
            val pth = uri.path
            return if (pth != null) {
                pth.lastIndexOf('.') > -1
            } else false
        }
    }

    init {
        path = path!!.substring(0, path!!.lastIndexOf('.'))
        baseUri = uri
        urls = ArrayList()
    }
}