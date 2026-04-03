package com.find.gang.myfindapplication.ui.home

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.launcher.ARouter
import com.find.gang.myfindapplication.R
import com.find.gang.myfindapplication.base.BaseFragment
import com.find.gang.myfindapplication.databinding.FragmentHomeBinding
import com.find.gang.myfindapplication.router.RouterPath

class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    private lateinit var adapter: HomeAdapter

    override fun getViewModelClass(): Class<HomeViewModel> = HomeViewModel::class.java

    override fun initView() {
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        adapter = HomeAdapter { item ->
            when (item.type) {
                "webview" -> openWebView(item.url)
                "video" -> openVideoPlayer(item.url, item.isLocal)
            }
        }
        binding.rvHome.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHome.adapter = adapter
    }

    private fun setupClickListeners() {
        // 加载数据
        viewModel.loadHomeData()
    }

    private fun openWebView(url: String) {
        ARouter.getInstance()
            .build(RouterPath.WEBVIEW)
            .withString("url", url)
            .navigation()
    }

    private fun openVideoPlayer(url: String, isLocal: Boolean) {
        ARouter.getInstance()
            .build(RouterPath.VIDEO_PLAYER)
            .withString("videoUrl", url)
            .withBoolean("isLocal", isLocal)
            .navigation()
    }

    override fun observeData() {
        viewModel.articleList.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { showToast(it) }
        }
    }
}