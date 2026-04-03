package com.find.gang.myfindapplication.base

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider

/**
 * Activity 基类，支持 DataBinding 和 ViewModel 的 MVVM 模式
 * @param layoutId 布局文件资源 ID
 */
abstract class BaseActivity<VB : ViewDataBinding, VM : BaseViewModel>(
    @LayoutRes private val layoutId: Int
) : AppCompatActivity() {

    protected lateinit var binding: VB
    protected lateinit var viewModel: VM

    /**
     * 获取 ViewModel 的 Class 对象，由子类实现
     */
    protected abstract fun getViewModelClass(): Class<VM>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 DataBinding
        binding = DataBindingUtil.setContentView(this, layoutId)
        binding.lifecycleOwner = this
        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[getViewModelClass()]
        // 初始化视图
        initView()
        // 观察数据变化
        observeData()
    }

    /**
     * 初始化视图，子类可重写
     */
    protected open fun initView() {}

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
}