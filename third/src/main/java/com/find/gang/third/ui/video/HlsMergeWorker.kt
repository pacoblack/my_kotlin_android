package com.find.gang.third.ui.video

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.find.gang.third.ui.video.VideoUriExtensions.decryptTsFile
import com.find.gang.third.ui.video.VideoUriExtensions.mergeToMp4
import com.find.gang.third.ui.video.VideoUriExtensions.mergeTsFiles
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

class HlsMergeWorker(context: Context,workerParams: WorkerParameters) : Worker(context, workerParams) {
    companion object {
        const val TAG: String = "HlsMergeWorker"

        const val KEY_M3U8_URL: String = "m3u8_url"

        const val KEY_OUTPUT_FILE: String = "output_file"

        const val KEY_OUTPUT_FORMAT: String = "output_format" // "ts" or "mp4"
    }

    @UnstableApi
    override fun doWork(): Result {
        val m3u8Url: String? = inputData.getString(KEY_M3U8_URL)
        val outputPath: String? = inputData.getString(KEY_OUTPUT_FILE)
        val outputFormat: String? = inputData.getString(KEY_OUTPUT_FORMAT)

        if (m3u8Url.isNullOrEmpty() || outputPath.isNullOrEmpty() || outputFormat.isNullOrEmpty()) {
            return Result.failure();
        }

        try {
            val outputFile = File(outputPath)


            // 1. 解析主M3U8文件
            val masterPlaylist: HlsParser.HlsMasterPlaylist = HlsParser.parseMasterPlaylist(m3u8Url)

            // 2. 选择最高码率的变体
            val selectedVariant = selectBestVariant(masterPlaylist.variants) ?: throw IOException("No valid variant found")

            // 3. 解析媒体播放列表
            val mediaPlaylist: HlsParser.HlsMediaPlaylist = HlsParser.parseMediaPlaylist(selectedVariant.url)


            // 4. 下载所有分段
            val downloader = HlsDownloader(applicationContext)
            downloader.downloadHlsSegments(mediaPlaylist.segments, object : HlsDownloader.DownloadListener {
                override fun onProgressUpdate(progress: Int) {
                    setProgressAsync(Data.Builder().putInt("progress", progress).build())
                }

                override fun onDownloadComplete() {
                    // 继续处理
                }

                override fun onDownloadFailed(e: Exception?) {
                    // 在doWork中处理
                }
            })

            // 5. 获取所有分段文件（解密如果需要）
            val segmentFiles: MutableList<File> = ArrayList()
            var key: ByteArray? = null
            var iv: ByteArray? = null

            for (segment in mediaPlaylist.segments) {
                val segmentFile = downloader.getCachedSegment(segment.uri) ?: throw IOException("Segment not found in cache: " + segment.uri)

                if (segment.isEncrypted) {
                    // 获取密钥（只获取一次）
                    if (key == null) {
                        key = downloadKey(segment.encryptionUri)
                        iv = parseIv(segment.encryptionIv)
                    }

                    val decryptedFile = decryptTsFile(segmentFile, key, iv!!)
                    segmentFiles.add(decryptedFile)
                } else {
                    segmentFiles.add(segmentFile)
                }
            }


            // 6. 合并文件
            val result = if ("mp4".equals(outputFormat, ignoreCase = true)) {
                mergeToMp4(segmentFiles, outputFile)
            } else {
                mergeTsFiles(segmentFiles, outputFile)
            }


            // 7. 清理临时文件
            for (file in segmentFiles) {
                if (file.name.startsWith("decrypted")) {
                    file.delete()
                }
            }

            downloader.clearCache()

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "HLS merge failed", e)
            return Result.failure()
        }
    }

    private fun selectBestVariant(variants: List<HlsParser.HlsUrl>): HlsParser.HlsUrl? {
        var bestVariant: HlsParser.HlsUrl? = null
        var maxBandwidth = 0

        for (variant in variants) {
            if (variant.bandwidth > maxBandwidth) {
                maxBandwidth = variant.bandwidth
                bestVariant = variant
            }
        }
        return bestVariant
    }

    @Throws(IOException::class)
    private fun downloadKey(keyUri: String): ByteArray {
        val client = OkHttpClient()
        val request: Request = Request.Builder().url(keyUri).build()

        client.newCall(request).execute().use { response ->
            if (response.body == null) {
                throw IOException("Empty key response")
            }
            return response.body!!.bytes()
        }
    }

    private fun parseIv(ivStr: String?): ByteArray {
        if (ivStr == null || ivStr.length < 32) {
            return ByteArray(16) // 默认IV
        }

        val ivStr = ivStr.substring(2) // 去掉0x前缀
        val iv = ByteArray(16)

        for (i in iv.indices) {
            val index = i * 2
            iv[i] = ivStr.substring(index, index + 2).toInt(16).toByte()
        }

        return iv
    }
}