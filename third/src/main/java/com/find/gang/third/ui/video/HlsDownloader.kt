package com.find.gang.third.ui.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import com.find.gang.third.ui.video.VideoCacheManager.HLS_CACHE_DIR
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@UnstableApi
class HlsDownloader(context: Context) {
    private val downloadCache: Cache = VideoCacheManager.getCache(context, HLS_CACHE_DIR)
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val dataSourceFactory: DataSource.Factory =
        VideoDataSourceFactory.buildCacheSourceFactory(context, downloadCache)

    fun downloadHlsSegments(segments: List<HlsParser.Segment>, listener: DownloadListener) {
        val downloadedCount = AtomicInteger(0)
        val totalSegments = segments.size

        for (segment in segments) {
            executor.execute {
                try {
                    downloadSegment(segment.uri)

                    val progress = ((downloadedCount.incrementAndGet() / totalSegments.toFloat()) * 100).toInt()
                    listener.onProgressUpdate(progress)

                    if (downloadedCount.get() == totalSegments) {
                        listener.onDownloadComplete()
                    }
                } catch (e: IOException) {
                    listener.onDownloadFailed(e)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    @Throws(IOException::class)
    private fun downloadSegment(url: String) {
        val dataSource = dataSourceFactory.createDataSource()
        val dataSpec = DataSpec(url.toUri())
        dataSource.open(dataSpec)


        // 读取数据以触发下载
        val buffer = ByteArray(8192)
        while (dataSource.read(buffer, 0, buffer.size) != -1) {
            // 只需读取以填充缓存
        }

        dataSource.close()
    }

    @OptIn(UnstableApi::class)
    fun getCachedSegment(url: String): File? {
        val cacheSpan = downloadCache.getCachedSpans(url).first()
        return cacheSpan?.file
    }

    @OptIn(UnstableApi::class)
    fun clearCache() {
        downloadCache.release()
    }

    interface DownloadListener {
        fun onProgressUpdate(progress: Int)
        fun onDownloadComplete()
        fun onDownloadFailed(e: Exception?)
    }

    @UnstableApi
    private class NoOpCacheEvictor : CacheEvictor {
        override fun onCacheInitialized() {}
        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {}
        override fun onSpanAdded(cache: Cache, span: CacheSpan) {}
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {}
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {}
        override fun requiresCacheSpanTouches(): Boolean {
            return false
        }
    }
}