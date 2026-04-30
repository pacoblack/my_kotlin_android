package com.find.gang.common

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.viewbinding.ViewBinding
import com.find.gang.common.toolbox.PermissionTools

abstract class BaseActivity<VB : ViewBinding>(
    @LayoutRes private val layoutId: Int
) : AppCompatActivity() {

    protected lateinit var binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 DataBinding
        binding = DataBindingUtil.setContentView(this, layoutId)
        if (binding is ViewDataBinding) {
            (binding as ViewDataBinding).lifecycleOwner = this
        }
        // 初始化视图
        initView(savedInstanceState)
        // 观察数据变化
        observeData()
    }

    /**
     * 初始化视图，子类可重写
     */
    protected open fun initView(savedInstanceState: Bundle?) {}

    /**
     * 观察 LiveData，子类可重写
     */
    protected open fun observeData() {}

    /**
     * 显示 Toast 消息
     */
    protected fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示加载对话框（可选实现）
     */
    protected open fun showLoading(show: Boolean) {
        // 可以在这里实现全局加载对话框逻辑
    }

    /**
     * 通用权限请求方法，子类可直接调用
     * @param permissions 需要申请的权限列表
     * @param onAllGranted 全部授权成功回调
     * @param onDenied 有权限被拒绝时的回调（默认会弹 Toast 提示并可选关闭页面）
     */
    protected fun requestPermissions(
        permissions: List<String>,
        onAllGranted: () -> Unit,
        onDenied: (List<String>) -> Unit = { deniedList ->
            showToast("需要 ${deniedList.size} 个权限才能继续操作")
            // 可选：如果必要权限被拒绝，可以关闭页面
            // finish()
        }
    ) {
        PermissionTools.requestPermissions(
            activity = this,
            permissions = permissions,
            onAllGranted = onAllGranted,
            onDenied = onDenied
        )
    }

    // 也可以提供一个简化版本，只传权限列表，全部授权后执行某个操作
    protected fun requestPermissions(
        permissions: List<String>,
        onAllGranted: () -> Unit
    ) {
        requestPermissions(permissions, onAllGranted, {})
    }
}