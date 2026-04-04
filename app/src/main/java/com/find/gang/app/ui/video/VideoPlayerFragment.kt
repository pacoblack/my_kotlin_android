package com.find.gang.app.ui.video

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.find.gang.app.R
import com.find.gang.app.base.BaseFragment
import com.find.gang.app.databinding.FragmentVideoPlayerBinding
import com.find.gang.app.router.RouterPath

@Route(path = RouterPath.VIDEO_PLAYER)
class VideoPlayerFragment : BaseFragment<FragmentVideoPlayerBinding, VideoPlayerViewModel>(R.layout.fragment_video_player) {

    @Autowired(name = "videoUrl", required = false)
    @JvmField
    var videoUrl: String? = null

    @Autowired(name = "isLocal", required = false)
    @JvmField
    var isLocal: Boolean = false

    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null

    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            playVideo(it, isLocal = true)
        }
    }

    // 权限请求
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            openFilePicker()
        } else {
            showToast("需要存储权限才能选择本地视频")
        }
    }

    override fun getViewModelClass(): Class<VideoPlayerViewModel> = VideoPlayerViewModel::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ARouter.getInstance().inject(this)
    }

    override fun initView() {
        setupPlayer()
        setupListeners()
        handleIncomingVideo()
    }

    private fun setupPlayer() {
        playerView = binding.playerView
        exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
            playerView?.player = this
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> showLoading(true)
                        Player.STATE_READY -> showLoading(false)
                        Player.STATE_ENDED -> {
                            showLoading(false)
                            showToast("播放结束")
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    showLoading(false)
                    showToast("播放出错: ${error.message}")
                }
            })
        }
    }

    private fun setupListeners() {
        binding.btnPlayNetwork.setOnClickListener {
            val url = binding.etNetworkUrl.text.toString().trim()
            if (url.isEmpty()) {
                showToast("请输入视频地址")
                return@setOnClickListener
            }
            playNetworkVideo(url)
        }

        binding.btnSelectLocal.setOnClickListener {
            checkPermissionAndOpenPicker()
        }
    }

    private fun handleIncomingVideo() {
        videoUrl?.let { url ->
            if (isLocal) {
                playVideo(Uri.parse(url), isLocal = true)
            } else {
                binding.etNetworkUrl.setText(url)
                playNetworkVideo(url)
            }
        }
    }

    private fun playNetworkVideo(url: String) {
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        val mediaItem = MediaItem.fromUri(Uri.parse(fullUrl))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun playVideo(uri: Uri, isLocal: Boolean = false) {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .build()
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun checkPermissionAndOpenPicker() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) {
            openFilePicker()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch("video/*")
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroyView() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroyView()
    }
}