package com.find.gang.third.ui.video

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.find.gang.third.R
import com.find.gang.third.databinding.ActivityVideoPreviewBinding
import com.find.gang.third.ui.video.VideoDataSourceFactory.buildMediaSource
import com.find.gang.third.ui.video.VideoUriExtensions.isHls
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

@UnstableApi
class VideoWatchActivity : AppCompatActivity() {
    private val binding by lazy { ActivityVideoPreviewBinding.inflate(layoutInflater) }
    private val videoUri by lazy { intent.extras?.getString(EXTRA_VIDEO_URI)?.toUri() }

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var player: ExoPlayer
    private var isFullscreen = false

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    private val executor = Executors.newSingleThreadExecutor()

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
//        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        playerView = binding.playerView
        progressBar = binding.progressBar
        playerView.setFullscreenButtonClickListener {}

        playerView.setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            if (View.VISIBLE == visibility) {
                if (!isFullscreen) {
                    binding.toolbar.visibility = View.VISIBLE
                }
            } else {
                binding.toolbar.visibility = View.GONE
            }
        })
        playerView.setUseController(true)     // 仍需控制器处理进度条
        playerView.controllerAutoShow = false // 禁止自动显示/隐藏，始终可见
        playerView.showController()            // 显示控制器（透明效果）
        playerView.post{
            val infoBtn: ImageButton? = binding.playerView.findViewById(R.id.control_info)
            Toast.makeText(this, "ImageView $infoBtn", Toast.LENGTH_LONG).show()
            infoBtn?.setOnClickListener {
                showVideoInfoPopup(it)
            }
            showVideoInfoPopup(infoBtn!!)
        }
        setupGesture(playerView)

        if (videoUri == null) {
            Toast.makeText(this, "Uri参数问题", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGesture(view: PlayerView) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    true   // 必须返回 true 以继续接收后续事件
                }

                MotionEvent.ACTION_UP -> {
                    val upTime = System.currentTimeMillis()
                    val duration = upTime - downTime
                    val moved = abs(event.x - downX) > 20f || abs(event.y -downY) > 20f
                    if (!moved && duration < 200) {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun getCurrentVideoInfo(): Map<String, String> {
        // 方式1：通过 Player.Listener 中的最新数据（推荐保存到变量）
        // 这里直接展示如何从当前 tracks 中获取

        val currentTracks = player.currentTracks
        return parseTrackGroups(currentTracks)
    }

    fun parseTrackGroups(trackGroupArray: Tracks): Map<String, String> {
        val info = mutableMapOf<String, String>()
        for (trackGroup in trackGroupArray.groups) {
            // 一个TrackGroup包含一个轨道的多个版本（如不同码率的同一个视频）
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                info["Stream$i"] = format.sampleMimeType.toString()
                // 判断是否为视频轨
                if (format.sampleMimeType?.startsWith("video/") == true) {
                    format.let { it ->
                        info["分辨率"] = "${it.width} × ${it.height}"
                        it.bitrate.let { bps -> info["码率"] = "${bps / 1000} kbps" }
                        it.codecs?.let { info["编码"] = it }
                        it.frameRate.let { info["帧率"] = "$it fps" }
                        it.sampleMimeType?.let { info["MIME"] = it }
                        info["声道数/音频"] = "--"  // 可同样获取音频轨道
                    }
                }
            }
        }
        return info
    }
    private fun showVideoInfoPopup(anchor: View) {
        val infoMap = getCurrentVideoInfo()
        if (infoMap.isEmpty()) {
            Toast.makeText(this, "暂无视频信息", Toast.LENGTH_SHORT).show()
            return
        }

        val infoText = infoMap.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_info, null)
        popupView.findViewById<TextView>(R.id.tv_info).text = infoText

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.showAsDropDown(anchor, 0, -anchor.height - 20) // 显示在按钮上方
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown();
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        // 创建播放器
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer

                // 设置监听器
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> showProgress(true)
                            Player.STATE_READY -> showProgress(false)
                            Player.STATE_ENDED -> {
                                Toast.makeText(this@VideoWatchActivity, "播放完成", Toast.LENGTH_SHORT).show()
                                saveVideoAfterPlayback()
                            }
                            Player.STATE_IDLE -> {
                                showProgress(false)
                            }
                        }
                    }
                })

                // 准备媒体源
                val cache = VideoCacheManager.getCache(this, videoUri!!)
                val mediaSource = buildMediaSource(this, cache, videoUri!!)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    @OptIn(UnstableApi::class)
    @UnstableApi
    private fun saveVideoAfterPlayback() {
        if (videoUri!!.isHls()) {
            val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            val outputFile = File(outputDir, "merged_video.mp4");

            HlsMergeManager.startMerge(this, videoUri.toString(), outputFile, "mp4");
            WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData("mergeWork")
                .observe(this, Observer { workInfos ->
                    if (workInfos != null && workInfos.isNotEmpty()) {
                        val workInfo = workInfos[0];

                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                Toast.makeText(this, "视频合并成功", Toast.LENGTH_SHORT).show();
                                // 打开视频文件
                            }

                            WorkInfo.State.FAILED -> {
                                Toast.makeText(this, "视频合并失败", Toast.LENGTH_SHORT).show();
                            }

                            else -> {}
                        }
                    }

                });
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun releasePlayer() {
        player.release()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_video, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_video_info -> {}
            R.id.menu_video_save -> {}
        }
        return true
    }

    companion object {
        const val EXTRA_VIDEO_URI ="extra_video_uri"
        fun start(context: Context, uri: String) {
            context.startActivity(
                Intent(
                    context, VideoWatchActivity::class.java
                ).putExtra(EXTRA_VIDEO_URI, uri)
            )
        }
    }
}