package com.find.gang.app.ui
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.find.gang.app.databinding.ActivityVideoEditBinding
import com.find.gang.app.engines.VideoTrimmerEngine.trimVideo
import com.find.gang.app.widget.VideoRangeSeekBar.OnRangeChangeListener
import com.find.gang.app.widget.VideoTimelineView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File


class EditVideoActivity : AppCompatActivity(), OnRangeChangeListener {
    private lateinit var binding: ActivityVideoEditBinding

    private lateinit var srcVideoUri:String // 请确保此文件存在
    private var outputDir: String? = null
    private var videoDurationUs: Long = 0
    private var trimStartUs: Long = 0
    private var trimEndUs: Long = 0
    private var isPrepared = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 创建输出目录（应用私有目录）
        parseParams()
        outputDir = getExternalFilesDir(null).toString() + "/trimmed/"
        File(outputDir).mkdirs()

        loadVideo()

        initTimelineView()

        binding.btnTrim.setOnClickListener { _: View? ->
            if (isPrepared) {
                // 预览跳转到起始点
                binding.videoView.seekTo((trimStartUs / 1000).toInt())
            }
        }

        binding.btnSaveAsNew.setOnClickListener { v: View? -> startTrim(false) }
        binding.btnOverwrite.setOnClickListener { v: View? -> startTrim(true) }
    }

    fun initTimelineView(){
        // 初始化组件缓存（建议在 Application 中只调用一次）
        binding.rangeSeekBar.setOnRangeChangeListener(this)
//        VideoTimelineView.initCache(applicationContext)
//
//        // 设置视频路径（间隔10秒）
//        binding.rangeSeekBar.setVideoPath(srcVideoUri) {
//            // 可选：时间轴准备就绪后的操作
//        }
//
//        // 设置点击跳转回调
//        binding.rangeSeekBar.setOnThumbnailClickListener { timeMs, position ->
//            binding.videoView.seekTo(timeMs.toInt())
//        }
//
//        // 定时同步进度（例如每秒调用一次）
//        lifecycleScope.launch(Dispatchers.Main) {
//            while (true) {
//                delay(500)
//                binding.rangeSeekBar.setCurrentPosition(binding.videoView.currentPosition)
//                // 可选：自动滚动到当前位置
//                binding.rangeSeekBar.scrollToCurrentPosition(smooth = true)
//            }
//        }
    }

    private fun parseParams(){
        // 从 Intent 获取视频路径
        srcVideoUri = intent.getStringExtra(PARAMETER_VIDEO_PATH) ?: run {
            Toast.makeText(this, "未传入视频路径", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun loadVideo() {
        Toast.makeText(this, "视频uri: $srcVideoUri", Toast.LENGTH_SHORT).show()
        binding.videoView.setVideoURI(srcVideoUri.toUri())
        binding.videoView.setOnPreparedListener { mp: MediaPlayer ->
            isPrepared = true
            val durationMs = binding.videoView.getDuration()
            videoDurationUs = durationMs * 1000L
            binding.rangeSeekBar.setDuration(videoDurationUs)
            // 默认选取整个视频
            trimStartUs = 0
            trimEndUs = videoDurationUs
            // 开始播放并循环
//            binding.videoView.start()
//            mp.isLooping = true
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
            val success = trimVideo(this, srcVideoUri, dstPath, trimStartUs, trimEndUs)
            mainHandler.post {
                binding.progressBar.visibility = ProgressBar.GONE
                binding.btnSaveAsNew.setEnabled(true)
                binding.btnOverwrite.setEnabled(true)
                if (success) {
                    if (overwrite) {
                        // 替换原文件：删除原文件，重命名临时文件
                        // 暂时不替换，只另存为
                    } else {
                        Toast.makeText(
                            this@EditVideoActivity,
                            "新视频已保存: $dstPath",
                            Toast.LENGTH_LONG
                        ).show()
                        // 可选择打开新视频预览
                        binding.videoView.stopPlayback()
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

    override fun onDestroy() {
        super.onDestroy()
//        binding.rangeSeekBar.release()
    }

    companion object {
        private const val REQUEST_PERMISSION = 100

        private const val PARAMETER_VIDEO_PATH = "video_path"

        fun startActivity(activity: Activity, realPath: String){
            val intent = Intent(activity, EditVideoActivity::class.java)
            intent.putExtra(PARAMETER_VIDEO_PATH, realPath)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(intent)
        }
    }
}