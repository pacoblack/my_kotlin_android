package com.find.gang.third.ui.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.find.gang.third.ui.video.VideoUriExtensions.isHls
import com.find.gang.third.ui.video.VideoUriExtensions.isMpd
import com.find.gang.third.ui.video.VideoUriExtensions.isRtmp
import com.find.gang.third.ui.video.VideoUriExtensions.isRtsp
import com.tbuonomo.viewpagerdotsindicator.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlin.text.ifEmpty

object VideoDataSourceFactory {

    @OptIn(UnstableApi::class)
    fun buildMediaSource(context: Context, cache:Cache, uri: Uri): MediaSource {
        // 创建支持缓存的数据源工厂
        val cacheDataSourceFactory = buildCacheSourceFactory(context, cache)
        cacheDataSourceFactory.setCacheKeyFactory(MyCacheKeyFactory())
        // 根据文件类型创建对应的媒体源
        val mediaItem = MediaItem.fromUri(uri)
        return when {
            uri.isHls() -> {
                HlsMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            uri.isMpd() -> {
                DashMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            uri.isRtsp() -> {
                RtspMediaSource.Factory()
                    .createMediaSource(mediaItem)
            }
            uri.isRtmp() -> {
                ProgressiveMediaSource.Factory(RtmpDataSource.Factory())
                    .createMediaSource(mediaItem)
            }
            else -> {
                ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }


    @OptIn(UnstableApi::class)
    fun buildCacheSourceFactory(context: Context, cache: Cache): CacheDataSource.Factory {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor) // 示例：添加日志拦截器
            .build()

        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))

        // 创建支持缓存的数据源工厂
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return cacheDataSourceFactory
    }

    /**
     * 创建只读缓存数据源
     */
    @UnstableApi
    fun createCacheDataSource(context:Context, cache: Cache): DataSource {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE) // 只从缓存读取
            .createDataSource()
    }

    @UnstableApi
    class MyCacheKeyFactory: CacheKeyFactory {
        override fun buildCacheKey(dataSpec: DataSpec): String {
            return dataSpec.key?.ifEmpty { generateCacheKey(dataSpec.uri) } ?: dataSpec.key!!
        }

        companion object{
            /**
             * 生成缓存键（与Media3内部逻辑一致）
             */
            fun generateCacheKey(uri: Uri): String {
                return uri.toString().hashCode().toString()
            }
        }
    }
}