package com.find.gang.app.toolbox

import android.Manifest
import android.os.Build
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.request.PermissionBuilder

object PermissionTools {
    fun requestStorage(activity: FragmentActivity, allowCallback: Callback<Void, Void>, deniCallback: Callback<List<String>, Void>){
        var builder: PermissionBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionX.init(activity)
                .permissions(Manifest.permission.MANAGE_EXTERNAL_STORAGE)

        } else {
            PermissionX.init(activity)
                .permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        builder.onExplainRequestReason { scope, deniedList ->
            scope.showRequestReasonDialog(deniedList, "请求存储权限", "OK", "Cancel")
        }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    allowCallback.call(null)
                } else {
                    deniCallback.call(deniedList)
                }
            }
    }
}