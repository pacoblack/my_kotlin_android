package com.example.myapplication

import android.app.Activity
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.hdl.m3u8.M3U8DownloadTask
import com.hdl.m3u8.bean.OnDownloadListener
import com.hdl.m3u8.utils.NetSpeedUtils
import nl.bravobit.ffmpeg.FFmpeg
import java.io.File

class M3U8Downloader(var context: Activity) {

    private var lastLength = 0L
    private var downloadTask = M3U8DownloadTask("1001")
    private var downloadPath = Environment.getExternalStorageDirectory().path + File.separator + "/m3u8/download/video/"

    private fun checkFFmpeg(): Boolean {
        return if (FFmpeg.getInstance(context).isSupported) {
            true
        } else {
            Toast.makeText(context, "请检查是否安装了FFmpeg", Toast.LENGTH_LONG).show()
            false
        }
    }
    private fun startDownload(mediaUrls: String) {
        downloadTask.saveFilePath = downloadPath + System.currentTimeMillis() + ".ts"
        downloadTask.download(mediaUrls, object : OnDownloadListener {
            override fun onDownloading(itemFileSize: Long, totalTs: Int, curTs: Int) {
                Log.e(Companion.TAG, "onDownloading:$itemFileSize,$totalTs,$curTs")
            }

            //下载完成
            override fun onSuccess() {
                Log.e(Companion.TAG, "onSuccess")
            }

            //下载进度回调
            override fun onProgress(curLength: Long) {
                Log.e(
                    "onProgress", """
                     $curLength
                     $lastLength
                     """.trimIndent()
                )
                if (curLength - lastLength > 0) {
                    //下载速度
                    val speed = NetSpeedUtils.getInstance().displayFileSize(curLength - lastLength) + "/s"
                    context.runOnUiThread {
                        Log.e(Companion.TAG, "onProgress:$speed")
                    }
                    lastLength = curLength
                }
            }

            //开始下载
            override fun onStart() {
                Log.e(Companion.TAG, "onStart:")
            }

            //下载出错
            override fun onError(errorMsg: Throwable) {
                Log.e(Companion.TAG, "onError:$errorMsg")
            }
        })
    }

    companion object {
        const val TAG="M3U8Downloader"
    }
}