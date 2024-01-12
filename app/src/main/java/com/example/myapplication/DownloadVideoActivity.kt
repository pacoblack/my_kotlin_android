package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gang.video.service.DemoDownloadService
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadRequest
import com.test.gang.lib.video.IPlayerActivity


class DownloadVideoActivity : AppCompatActivity() {
    private lateinit var downloadUrlInput: EditText
    private lateinit var startDownloadButton: Button
    private lateinit var startPlayButton:Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        // 初始化控件
        downloadUrlInput = findViewById(R.id.download_url_input)
        startDownloadButton = findViewById(R.id.start_download_button)
        startPlayButton = findViewById(R.id.start_play_button)

        // 假设你已经有了一个自定义的downloadFileFromUrl()方法用于下载文件
        startDownloadButton.setOnClickListener {
            val url = downloadUrlInput.text.toString().trim { it <= ' ' }
            if (url.isNotEmpty()) {
                // 这里仅作为示例，你需要替换为实际的下载逻辑
                downloadFileFromUrl(this@DownloadVideoActivity, url)
            } else {
                // 输入框为空时提示用户
                Toast.makeText(this@DownloadVideoActivity, "请输入有效的下载地址", Toast.LENGTH_SHORT).show()
            }
        }

        startPlayButton.setOnClickListener {
            val url = downloadUrlInput.text.toString().trim { it <= ' ' }
            if (url.isNotEmpty()) {
                // 这里仅作为示例，你需要替换为实际的下载逻辑
                playVideoFromUrl(this@DownloadVideoActivity, url)
            } else {
                // 输入框为空时提示用户
                Toast.makeText(this@DownloadVideoActivity, "请输入有效的下载地址", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playVideoFromUrl(activity: Activity, url: String) {
        IPlayerActivity.Companion.startActivity(activity, url);
    }

    // 实际的下载文件方法，这里仅作占位，具体实现请参考ExoPlayer或其他库的下载功能
    @SuppressLint("ServiceCast")
    private fun downloadFileFromUrl(context: Context, urlString: String) {
        val downloadManager: DownloadManager =
            ((context.getSystemService(DOWNLOAD_SERVICE)) as DemoDownloadService).downloadManager

        val request: DownloadRequest = DownloadRequest
            .Builder(urlString, Uri.parse(urlString))
            .build()

        downloadManager.addDownload(request)
    }
}