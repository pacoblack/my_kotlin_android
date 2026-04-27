package com.find.gang.third.ui.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.find.gang.third.ui.video.VideoUriExtensions.isHls
import java.io.File

@UnstableApi
object VideoCacheManager {
    private var videoCache: Cache? = null
    private const val MAX_CACHE_SIZE = (200 * 1024 * 1024).toLong()  // 200MB
    private const val CACHE_DIR = "video_cache"
    const val HLS_CACHE_DIR = "video_hls_cache"

    @UnstableApi
    @Synchronized
    fun getCache(context: Context): Cache {
        if (videoCache == null) {
            val cacheDir = File(context.externalCacheDir, CACHE_DIR)
            if (!cacheDir.exists()) cacheDir.mkdirs()
            videoCache = SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
                StandaloneDatabaseProvider(context)
            )
        }
        return videoCache!!
    }

    @UnstableApi
    @Synchronized
    fun getCache(context: Context, uri: Uri): Cache {
        if (videoCache == null) {
            val cacheDir = File(context.cacheDir, if(uri.isHls()) HLS_CACHE_DIR else CACHE_DIR)
            if (!cacheDir.exists()) cacheDir.mkdirs()
            videoCache = SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
                StandaloneDatabaseProvider(context)
            )
        }
        return videoCache!!
    }

    @UnstableApi
    @Synchronized
    fun getCache(context: Context, dir:String): Cache {
        if (videoCache == null) {
            val cacheDir = File(context.cacheDir, dir)
            if (!cacheDir.exists()) cacheDir.mkdirs()
            videoCache = SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
                StandaloneDatabaseProvider(context)
            )
        }
        return videoCache!!
    }

}