package com.find.gang.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.find.gang.app.databinding.ActivitySplashBinding
import com.find.gang.app.ui.main.MainActivity
import com.permissionx.guolindev.PermissionX

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 DataBinding
        binding = ActivitySplashBinding.inflate(layoutInflater)

        this.checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // 1. 根据 Android 版本构建需要申请的权限列表
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 2. 使用 PermissionX 请求权限
        PermissionX.init(this)
            .permissions(permissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(deniedList, "需要存储权限才能选择视频文件", "确定", "取消")
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(deniedList, "请前往设置中手动开启权限", "去设置", "取消")
            }
            .request { allGranted, denyed, _ ->
                if (allGranted) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    println(denyed)
                    // 显示一个对话框，让用户重试
                    AlertDialog.Builder(this)
                        .setTitle("权限不足")
                        .setMessage("您拒绝了必要权限，是否重新尝试申请？")
                        .setPositiveButton("重试") { _, _ -> checkAndRequestPermissions() }
                        .setNegativeButton("退出") { _, _ -> finish() }
                        .show()
                }
            }
    }

}