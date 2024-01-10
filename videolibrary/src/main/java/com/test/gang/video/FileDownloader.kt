package com.test.gang.video

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.google.android.exoplayer2.offline.Downloader
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSourceInputStream
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.test.gang.video.ext.HttpEventListener
import com.test.gang.video.ext.OkHttpDataSourceFactory
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit


class FileDownloader(context: Context, private val uri: Uri, destinationDirectory: File?) :
    Downloader {
    private val context: Context
    private val targetFile: File

    init {
        this.context = context.applicationContext
        targetFile = File(destinationDirectory, Util.escapeFileName(uri.toString()))
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun download(progressListener: Downloader.ProgressListener?) {
        val dataSource: DataSource = buildDataSource()
        FileOutputStream(targetFile).use { outputStream ->
            DataSourceInputStream(
                dataSource, DataSpec(
                    uri
                )
            ).use { inputStream ->
                val buffer = ByteArray(8192)
                var readBytes: Int
                var totalRead: Long = 0
                while (inputStream.read(buffer).also { readBytes = it } != -1) {
                    outputStream.write(buffer, 0, readBytes)
                    totalRead += readBytes.toLong()
                    progressListener!!.onProgress(-1L, totalRead, /* contentLength= */-1f)
                }
            }
        }
    }

    private fun buildDataSource(): DataSource {
        // 这里应根据你的实际需求构建DataSource实例，例如：
        // DefaultHttpDataSource.Factory httpDataSourceFactory = ...; // 创建HTTP数据源工厂
        val userAgent = Util.getUserAgent(context, "ExamplePlayer")
        val builder = OkHttpClient.Builder()
        builder.eventListenerFactory(HttpEventListener.FACTORY)
        builder.connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
        val client = builder.build()
        var dataSourceFactory =
            DefaultDataSourceFactory(context, OkHttpDataSourceFactory(client as Call.Factory, userAgent))
        var datasource = dataSourceFactory.createDataSource()
        return datasource
    }

    val isDownloaded: Boolean
        get() = targetFile.exists() && targetFile.length() > 0

    override fun remove() {
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    val keys: List<StreamKey>
        get() = emptyList()

    override fun cancel() {
        // 可以在此处添加取消下载的逻辑，但此处未给出具体实现。
    }
}