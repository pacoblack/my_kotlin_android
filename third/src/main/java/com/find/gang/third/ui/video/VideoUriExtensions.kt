package com.find.gang.third.ui.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

object VideoUriExtensions {
    fun Uri.isMp4():Boolean{
        return this.path?.contains(".mp4") ?:false
    }

    fun Uri.isHls():Boolean{
        return this.path?.contains(".m3u8") ?:false
    }

    fun Uri.isMpd():Boolean{
        return this.path?.contains(".mpd") ?:false
    }

    fun Uri.isRtsp():Boolean {
        return scheme.equals("rtsp")
    }

    fun Uri.isRtmp():Boolean {
        return scheme.equals("rtmp")
    }

    @Throws(Exception::class)
    fun decryptTsFile(encryptedFile: File, key: ByteArray, iv: ByteArray): File {
        val decryptedFile = File.createTempFile("decrypted", ".ts")

        FileInputStream(encryptedFile).use { fis ->
            FileOutputStream(decryptedFile).use { fos ->
                val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
                val keySpec = SecretKeySpec(key, "AES")
                val ivSpec = IvParameterSpec(iv)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                while ((fis.read(buffer).also { bytesRead = it }) != -1) {
                    val decrypted = cipher.update(buffer, 0, bytesRead)
                    if (decrypted != null) {
                        fos.write(decrypted)
                    }
                }

                val finalBytes = cipher.doFinal()
                if (finalBytes != null) {
                    fos.write(finalBytes)
                }
            }
        }
        return decryptedFile
    }

    @Throws(IOException::class)
    fun mergeTsFiles(tsFiles: List<File>, outputFile: File): File {
        FileOutputStream(outputFile).use { fos ->
            fos.channel.use { outChannel ->
                for (tsFile in tsFiles) {
                    FileInputStream(tsFile).use { fis ->
                        fis.channel.use { inChannel ->
                            val size = inChannel.size()
                            var position: Long = 0
                            var transferred: Long
                            while (position < size) {
                                transferred = inChannel.transferTo(position, size - position, outChannel)
                                if (transferred <= 0) break
                                position += transferred
                            }
                        }
                    }
                }
                return outputFile
            }
        }
    }

    @Throws(IOException::class)
    fun mergeToMp4(mediaFiles: List<File>, outputFile: File): File {
        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap: MutableMap<Int, Int> = HashMap()
            var started = false
            var maxPts: Long = 0

            for (mediaFile in mediaFiles) {
                val extractor = MediaExtractor()
                extractor.setDataSource(mediaFile.absolutePath)

                val trackCount = extractor.trackCount

                if (!started) {
                    for (i in 0..<trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val trackIndex = muxer.addTrack(format)
                        trackIndexMap[i] = trackIndex
                    }
                    muxer.start()
                    started = true
                }

                for (i in 0..<trackCount) {
                    extractor.selectTrack(i)

                    val buffer = ByteBuffer.allocate(1024 * 1024)
                    val bufferInfo = MediaCodec.BufferInfo()

                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        var sampleTime = extractor.sampleTime
                        if (maxPts > 0) {
                            sampleTime += maxPts
                        }

                        bufferInfo[0, sampleSize, sampleTime] = MediaCodec.BUFFER_FLAG_KEY_FRAME
                        muxer.writeSampleData(trackIndexMap[i]!!, buffer, bufferInfo)
                        extractor.advance()
                    }
                }


                // 更新最大时间戳
                for (i in 0..<trackCount) {
                    extractor.selectTrack(i)
                    while (extractor.advance()) {
                        maxPts = max(maxPts, extractor.sampleTime)
                    }
                }

                extractor.release()
            }

            return outputFile
        } finally {
            if (muxer != null) {
                try {
                    muxer.stop()
                    muxer.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}