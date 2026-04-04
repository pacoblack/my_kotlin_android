package com.find.gang.app.toolbox

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
}