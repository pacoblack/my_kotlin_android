package com.find.gang.app.ui.home

import android.net.Uri
import android.util.Log.d
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.launcher.ARouter
import com.find.gang.app.R
import com.find.gang.app.base.BaseFragment
import com.find.gang.app.databinding.FragmentHomeBinding
import com.find.gang.app.router.RouterPath
import com.find.gang.app.toolbox.Toolbox.onClick
import com.find.gang.app.widget.MediaGridSelector
import com.find.gang.app.widget.MediaGridSelector.OnSelectionChangedListener
import com.find.gang.common.toolbox.PickerManager
import com.find.gang.third.ui.video.VideoWatchActivity

class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    private lateinit var adapter: HomeAdapter

    override fun getViewModelClass(): Class<HomeViewModel> = HomeViewModel::class.java
    private val manager = PickerManager()
    override fun initView() {
        binding.selectMedia.onClick {
            manager.pickMultipleVideos()
        }
        manager.register(this, object : PickerManager.Callback {
            override fun onVideosPicked(uris: List<Uri>) {
                uris.forEach { uri ->
                    println("视频: $uri")
                }
                binding.multiSelector.setVideoUris(uris)
            }
        })
        binding.multiSelector.setOnSelectionChangedListener(object:OnSelectionChangedListener{
            override fun onSelectionChanged(selected: List<Pair<Uri, Int>>) {
                selected.forEach { (uri, order) ->
                    println("选中序号: $order, 视频: $uri")
                }
            }
        })
        binding.multiSelector.setOnItemLongClickListener(object:MediaGridSelector.OnItemLongClickListener{
            @OptIn(UnstableApi::class)
            override fun onItemLongClick(position: Int, uri: Uri) {
                VideoWatchActivity.start(context!!, uri.toString())
            }

        })

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