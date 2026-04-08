package com.find.gang.app.engines

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer


object VideoTrimmerEngine {
    private const val TAG = "VideoTrimmerEngine"
    private const val BUFFER_SIZE = 512 * 1024 // 512KB

    // 将 MediaExtractor 的标志转换为 MediaCodec.BufferInfo 需要的标志
    private fun convertExtractorFlagsToCodecFlags(extractorFlags: Int): Int {
        var codecFlags = 0
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        // 如果还有其他需要保留的标志，可以继续添加，但通常 MediaMuxer 只需要关键帧标志
        return codecFlags
    }

    /**
     * 裁剪视频
     * @param srcPath 源文件路径
     * @param dstPath 目标文件路径
     * @param startUs 起始时间（微秒）
     * @param endUs   结束时间（微秒）
     * @return 成功返回 true
     */
    fun trimVideo(context: Context, srcPath: String, dstPath: String, startUs: Long, endUs: Long): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            try {
                extractor.setDataSource(srcPath)
            } catch (e: IOException) {
                println("无效的视频源: $srcPath")
                return false
            }
            muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackCount = extractor.trackCount
            val trackIndexMap = IntArray(trackCount)
            var hasVideo = false
            for (i in 0..<trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("video/")) {
                    hasVideo = true
                }
                extractor.selectTrack(i)
                trackIndexMap[i] = muxer.addTrack(format)
            }
            if (!hasVideo) {
                Log.e(TAG, "No video track found")
                return false
            }
            muxer.start()

            // 定位到起始关键帧
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex == -1) {
                    break
                }
                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) {
                    // 超出结束时间，停止循环
                    break
                }
                if (sampleTime >= startUs) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        break
                    }
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = sampleTime - startUs
                    // 修正：转换标志
                    bufferInfo.flags = convertExtractorFlagsToCodecFlags(extractor.sampleFlags)
                    muxer.writeSampleData(trackIndexMap[trackIndex], buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error during trimming", e)
            return false
        } finally {
            extractor?.release()
            if (muxer != null) {
                try {
                    muxer.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}