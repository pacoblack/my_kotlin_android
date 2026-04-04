package com.find.gang.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.find.gang.app.databinding.ActivitySplashBinding
import com.find.gang.app.ui.main.MainActivity
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.request.PermissionBuilder

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 DataBinding
        binding = ActivitySplashBinding.inflate(layoutInflater)

        var builder: PermissionBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionX.init(this)
                .permissions(listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))

        } else {
            PermissionX.init(this)
                .permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        builder.onExplainRequestReason { scope, deniedList ->
            scope.showRequestReasonDialog(deniedList, "请求存储权限", "OK", "Cancel")
        }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    Toast.makeText(this, "权限申请已通过！", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "权限申请未通过！", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
    }

}