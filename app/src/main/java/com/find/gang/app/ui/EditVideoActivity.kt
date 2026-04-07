package com.find.gang.app.ui
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.find.gang.app.databinding.ActivityVideoEditBinding
import com.find.gang.app.engines.VideoTrimmerEngine.trimVideo
import com.find.gang.app.widget.VideoRangeSeekBar.OnRangeChangeListener
import java.io.File


class EditVideoActivity : AppCompatActivity(), OnRangeChangeListener {
    private lateinit var binding: ActivityVideoEditBinding

    private var srcVideoPath = "/sdcard/input.mp4" // 请确保此文件存在
    private var outputDir: String? = null
    private var videoDurationUs: Long = 0
    private var trimStartUs: Long = 0
    private var trimEndUs: Long = 0
    private var isPrepared = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditBinding.inflate(layoutInflater)

        // 创建输出目录（应用私有目录）
        outputDir = getExternalFilesDir(null).toString() + "/trimmed/"
        File(outputDir).mkdirs()

        checkPermissions()

        binding.rangeSeekBar.setOnRangeChangeListener(this)

        binding.btnTrim.setOnClickListener { v: View? ->
            if (isPrepared) {
                // 预览跳转到起始点
                binding.videoView.seekTo((trimStartUs / 1000).toInt())
            }
        }

        binding.btnSaveAsNew.setOnClickListener { v: View? -> startTrim(false) }
        binding.btnOverwrite.setOnClickListener { v: View? -> startTrim(true) }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 MediaStore 不需要传统存储权限，但为了简单起见，仍申请
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_PERMISSION
                )
            } else {
                loadVideo()
            }
        } else {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), REQUEST_PERMISSION
                )
            } else {
                loadVideo()
            }
        }
    }

    private fun loadVideo() {
        val srcFile = File(srcVideoPath)
        if (!srcFile.exists()) {
            Toast.makeText(this, "源视频不存在: $srcVideoPath", Toast.LENGTH_LONG).show()
            return
        }
        binding.videoView.setVideoPath(srcVideoPath)
        binding.videoView.setOnPreparedListener { mp: MediaPlayer ->
            isPrepared = true
            val durationMs = binding.videoView.getDuration()
            videoDurationUs = durationMs * 1000L
            binding.rangeSeekBar.setDuration(videoDurationUs)
            // 默认选取整个视频
            trimStartUs = 0
            trimEndUs = videoDurationUs
            // 开始播放并循环
            binding.videoView.start()
            mp.isLooping = true
        }
        binding.videoView.setOnErrorListener { mp: MediaPlayer?, what: Int, extra: Int ->
            Toast.makeText(this, "视频播放错误", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onRangeChanged(startUs: Long, endUs: Long) {
        trimStartUs = startUs
        trimEndUs = endUs
        // 预览跳转到起始位置
        if (isPrepared) {
            binding.videoView.seekTo((startUs / 1000).toInt())
        }
    }

    private fun startTrim(overwrite: Boolean) {
        if (!isPrepared) {
            Toast.makeText(this, "视频未准备好", Toast.LENGTH_SHORT).show()
            return
        }
        if (trimStartUs >= trimEndUs) {
            Toast.makeText(this, "无效的裁剪范围", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = ProgressBar.VISIBLE
        binding.btnSaveAsNew.setEnabled(false)
        binding.btnOverwrite.setEnabled(false)

        Thread {
            val dstPath: String
            if (overwrite) {
                // 覆盖原视频：先输出到临时文件，成功后替换
                dstPath = outputDir + "temp_trimmed.mp4"
            } else {
                // 另存为新文件，带时间戳
                val timestamp = System.currentTimeMillis().toString()
                dstPath = outputDir + "trimmed_" + timestamp + ".mp4"
            }
            val success = trimVideo(srcVideoPath, dstPath, trimStartUs, trimEndUs)
            mainHandler.post {
                binding.progressBar.visibility = ProgressBar.GONE
                binding.btnSaveAsNew.setEnabled(true)
                binding.btnOverwrite.setEnabled(true)
                if (success) {
                    if (overwrite) {
                        // 替换原文件：删除原文件，重命名临时文件
                        val srcFile = File(srcVideoPath)
                        val tempFile = File(dstPath)
                        if (srcFile.delete() && tempFile.renameTo(srcFile)) {
                            Toast.makeText(this@EditVideoActivity, "覆盖成功", Toast.LENGTH_SHORT)
                                .show()
                            // 重新加载视频
                            binding.videoView.stopPlayback()
                            loadVideo()
                        } else {
                            Toast.makeText(this@EditVideoActivity, "覆盖失败", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
                        Toast.makeText(
                            this@EditVideoActivity,
                            "新视频已保存: $dstPath",
                            Toast.LENGTH_LONG
                        ).show()
                        // 可选择打开新视频预览
                        binding.videoView.stopPlayback()
                        srcVideoPath = dstPath // 切换到新视频
                        loadVideo()
                    }
                } else {
                    Toast.makeText(this@EditVideoActivity, "裁剪失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideo()
            } else {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSION = 100
    }
}