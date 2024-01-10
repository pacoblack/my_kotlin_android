package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.offline.DownloadService
import com.test.gang.video.DemoDownloadService
import java.io.File


class DownloadVideoActivity : AppCompatActivity() {
    private lateinit var downloadUrlInput: EditText
    private lateinit var startDownloadButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        // 初始化控件
        downloadUrlInput = findViewById(R.id.download_url_input)
        startDownloadButton = findViewById(R.id.start_download_button)

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
    }

    // 实际的下载文件方法，这里仅作占位，具体实现请参考ExoPlayer或其他库的下载功能
    @SuppressLint("ServiceCast")
    private fun downloadFileFromUrl(context: Context, urlString: String) {

        // Starting the service in the foreground causes notification flicker if there is no scheduled
        // action. Starting it in the background throws an exception if the app is in the background too
        // (e.g. if device screen is locked).
        try {
            DownloadService.start(this, DemoDownloadService::class.java)
        } catch (e: IllegalStateException) {
            DownloadService.startForeground(this, DemoDownloadService::class.java)
        }

        val downloadManager: DownloadManager =
            ((context.getSystemService(DOWNLOAD_SERVICE)) as DemoDownloadService).downloadManager

            // 创建下载请求，指定最终存储路径（也可以根据需要自动生成）
        val destinationFile: File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "video.mp4")
        val destinationUri = Uri.fromFile(destinationFile)

        val request: DownloadRequest = DownloadRequest
            .Builder(urlString, Uri.parse(urlString))
            .build()

        downloadManager.addDownload(request)
    }
}