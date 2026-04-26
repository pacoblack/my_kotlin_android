package com.find.gang.common.toolbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX

object PermissionTools {
    /**
     * 通用权限请求方法
     * @param activity FragmentActivity
     * @param permissions 需要申请的权限列表
     * @param onAllGranted 全部授权成功回调
     * @param onDenied 有权限被拒绝时的回调，返回被拒绝的权限列表
     */
    fun requestPermissions(
        activity: FragmentActivity,
        permissions: List<String>,
        onAllGranted: () -> Unit,
        onDenied: (List<String>) -> Unit
    ) {
        if (permissions.isEmpty()) {
            onAllGranted()
            return
        }
        PermissionX.init(activity)
            .permissions(permissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(deniedList, "需要相关权限才能使用此功能", "确定", "取消")
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(deniedList, "请前往设置中手动开启权限", "去设置", "取消")
            }
            .request { allGranted, _, deniedList ->
                if (allGranted) {
                    onAllGranted()
                } else {
                    onDenied(deniedList)
                }
            }
    }

    fun checkPermissions(context: Context, callback: Callback2<String, Boolean, Array<String>>){
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            callback.onSuccess("")
        } else {
            callback.onError(permissionsToRequest)
        }

    }
}