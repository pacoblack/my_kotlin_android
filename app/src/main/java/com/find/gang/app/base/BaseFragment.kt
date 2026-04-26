package com.find.gang.app.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.find.gang.app.R

abstract class BaseFragment<VB : ViewDataBinding, VM : BaseViewModel>(
    @LayoutRes private val layoutId: Int
) : Fragment() {

    protected lateinit var binding: VB
    protected lateinit var viewModel: VM

    open val isToolbarVisible: Boolean = true

    private var toolbar: Toolbar? = null

    protected abstract fun getViewModelClass(): Class<VM>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        viewModel = ViewModelProvider(this)[getViewModelClass()]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initActivityToolbar()
        initView()
        observeData()
    }

    private fun initActivityToolbar() {
        toolbar = requireActivity().findViewById(R.id.main_toolbar)
    }

    protected open fun initView() {}

    protected open fun observeData() {}

    protected fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        applyToolbarVisibility()
    }

    override fun onPause() {
        super.onPause()
        // 离开时默认恢复显示，避免影响其他Fragment
        restoreToolbar()
    }

    private fun applyToolbarVisibility() {
        toolbar?.visibility = if (isToolbarVisible) View.VISIBLE else View.GONE
    }

    private fun restoreToolbar() {
        // 如果不是当前可见的Fragment（可能被replace但没销毁），可以选择恢复，这里简单置为可见
        toolbar?.visibility = View.VISIBLE
    }
}