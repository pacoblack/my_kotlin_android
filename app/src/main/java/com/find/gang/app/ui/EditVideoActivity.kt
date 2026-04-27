package com.find.gang.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.find.gang.app.databinding.ActivityVideoEditBinding
import com.find.gang.video.lib.interfaces.VideoTrimListener

class EditVideoActivity : AppCompatActivity(), VideoTrimListener {
    private lateinit var binding: ActivityVideoEditBinding

    private lateinit var srcVideoUri:String // 请确保此文件存在

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 创建输出目录（应用私有目录）
        parseParams()

        initTimelineView()

        binding.btnSaveAsNew.setOnClickListener { v: View? ->  }
        binding.btnOverwrite.setOnClickListener { v: View? ->  }
    }

    fun initTimelineView(){
        // 初始化组件缓存（建议在 Application 中只调用一次）
        binding.rangeSeekBar.setOnTrimVideoListener(this)
        binding.rangeSeekBar.initVideoByURI(srcVideoUri.toUri())
    }

    public override fun onPause() {
        super.onPause()
        binding.rangeSeekBar.onVideoPause()
        binding.rangeSeekBar.restoreState = true
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.rangeSeekBar.onDestroy()
    }

    private fun parseParams(){
        // 从 Intent 获取视频路径
        srcVideoUri = intent.getStringExtra(PARAMETER_VIDEO_PATH) ?: run {
            Toast.makeText(this, "未传入视频路径", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            if (!grantResults.isNotEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStartTrim() {
        Toast.makeText(this, "开始trim", Toast.LENGTH_SHORT).show()
    }

    override fun onFinishTrim(url: String?) {
        Toast.makeText(this, "完成trim", Toast.LENGTH_SHORT).show()
    }

    override fun onCancel() {
        binding.rangeSeekBar.onDestroy()
        finish()
    }

    companion object {
        private const val VIDEO_TRIM_REQUEST_CODE: Int = 0x001
        private const val REQUEST_PERMISSION = 100

        private const val PARAMETER_VIDEO_PATH = "video_path"

        fun startActivity(activity: Activity, realPath: String){
            val intent = Intent(activity, EditVideoActivityRangeBar::class.java)
            intent.putExtra(PARAMETER_VIDEO_PATH, realPath)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(intent)
        }
    }
}