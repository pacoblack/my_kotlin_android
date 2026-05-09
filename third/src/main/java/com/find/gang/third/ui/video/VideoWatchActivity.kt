package com.find.gang.third.ui.video

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
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
import androidx.media3.common.C
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
import kotlin.math.max
import kotlin.math.min

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
    private val seekIntervalMs = 200L
    private val seekAmountMs = 500L // 前进/后退 每次的间隔
    private val longPressThreshold = 400L
    private var isLongPressActive = false
    private var isDown = false
    private var hasMovedInDown = false

    private val longPressRunnable = object: Runnable {
        override fun run() {
            if (isLongPressActive) {
                val currentPos = player.currentPosition
                val duration = if (player.duration > 0) player.duration else 0L
                if (duration <= 0) return

                val targetPos = if (downX < playerView.width / 2) {
                    // 左侧：快退
                    max(0, currentPos - seekAmountMs)
                } else {
                    // 右侧：快进
                    min(duration, currentPos + seekAmountMs)
                }

                player.seekTo(targetPos)
                updateSeekHint(targetPos, duration, downX < playerView.width / 2)  // 更新提示
                playerView.postDelayed(this, seekIntervalMs)
            }
        }
    }

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
            infoBtn?.setOnClickListener {
                if (player.playbackState == Player.STATE_READY) {
                    showVideoInfoPopup(it, getAllTrackInfo())
                } else {
                    Toast.makeText(this, "视频尚未准备好", Toast.LENGTH_SHORT).show()
                }
            }
        }
        setupGesture(playerView)

        if (videoUri == null) {
            Toast.makeText(this, "Uri参数问题", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    private fun updateSeekHint(targetMs: Long, durationMs: Long, isRewind: Boolean) {
        val targetTime = formatTime(targetMs)
        val totalTime = formatTime(durationMs)
        // 格式：快进 → "目标时间 / 总时长"，快退同理；也可加方向符号
        val hint = if (isRewind) "◀ $targetTime / $totalTime" else "$targetTime / $totalTime ▶"
        binding.seekHint.text = hint
        binding.seekHint.visibility = View.VISIBLE
    }

    private fun hideSeekHint() {
        binding.seekHint.visibility = View.GONE
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000).toInt()
        val s = seconds % 60
        val m = (seconds / 60) % 60
        return String.format("%02d:%02d", m, s)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGesture(view: PlayerView) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    isDown = true
                    isLongPressActive = false
                    hasMovedInDown = false
                    v.removeCallbacks(longPressRunnable)

                    v.postDelayed({
                        if (isDown) {
                            isLongPressActive = true
                            longPressRunnable.run()
                        }
                    }, longPressThreshold)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDown && (abs(event.x - downX) > 20f || abs(event.y - downY) > 20f)) {
                        hasMovedInDown = true
                        isDown = false          // 取消长按判定
                        v.removeCallbacks(longPressRunnable)
                        isLongPressActive = false
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    isDown = false
                    v.removeCallbacks(longPressRunnable)

                    if (!hasMovedInDown) {
                        val duration = System.currentTimeMillis() - downTime
                        val still = abs(event.x - downX) <= 20f && abs(event.y - downY) <= 20f

                        if (!isLongPressActive && still && duration < 200) {
                            handleShortClick(view)
                        }
                    }

                    isLongPressActive = false
                    hasMovedInDown = false
                    hideSeekHint()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDown = false
                    v.removeCallbacks(longPressRunnable)
                    isLongPressActive = false
                    hasMovedInDown = false
                    hideSeekHint()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleShortClick(view: PlayerView) {
        when {
            // 视频已播放完毕 ➔ 只切换控制栏可见性，不改变播放状态
            player.playbackState == Player.STATE_ENDED -> {
                if (view.isControllerFullyVisible) view.hideController() else view.showController()
            }
            // 正在播放 ➔ 暂停并显示控制栏
            player.isPlaying -> {
                player.pause()
                view.showController()
            }
            // 暂停（未结束）➔ 开始播放并隐藏控制栏
            else -> {
                player.play()
                view.hideController()
            }
        }
    }

    private fun getAllTrackInfo(): String {
        val tracks = player.currentTracks
        val sb = StringBuilder()

        for (group in tracks.groups) {
            // group.length 是该轨道的可选项数量（例如不同码率的同一视频）
            if (group.length == 0) continue

            // 确定轨道类型
            val typeName = when (group.type) {
                C.TRACK_TYPE_VIDEO -> "视频"
                C.TRACK_TYPE_AUDIO -> "音频"
                C.TRACK_TYPE_TEXT  -> "字幕"
                else               -> "其他(${group.type})"
            }

            // 遍历该组内所有格式（通常相同内容不同质量）
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                sb.appendLine("[$typeName] (${i + 1}/${group.length})")
                sb.appendLine("  ID: ${format.id}")
                sb.appendLine("  MIME: ${format.sampleMimeType}")
                format.codecs?.let { sb.appendLine("  编码: $it") }
                format.language?.let { sb.appendLine("  语言: $it") }
                format.bitrate.let { sb.appendLine("  码率: ${it / 1000} kbps") }

                // 视频特有信息
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    sb.appendLine("  分辨率: ${format.width}×${format.height}")
                    format.frameRate.let { sb.appendLine("  帧率: $it fps") }
                }
                // 音频特有信息
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    format.sampleRate.let { sb.appendLine("  采样率: $it Hz") }
                    format.channelCount.let { sb.appendLine("  声道数: $it") }
                }
                sb.appendLine("---")
            }
        }

        return sb.toString().ifEmpty { "暂无轨道信息" }
    }
    private fun showVideoInfoPopup(anchor: View, infoText: String) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_info, null)
        popupView.findViewById<TextView>(R.id.tv_info).text = infoText

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // 1. 先测量弹窗尺寸
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        // 2. 获取按钮在屏幕上的绝对坐标
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorRight = anchorLocation[0] + anchor.width   // 按钮右边缘 X
        val anchorTop = anchorLocation[1]                    // 按钮上边缘 Y

        // 3. 计算弹窗的左下角位置
        val popupX = anchorRight         // 弹窗左边缘 = 按钮右边缘
        val popupY = anchorTop - popupHeight   // 弹窗底边缘 = 按钮上边缘

        // 4. 显示弹窗（可添加小间距，如 -10 向上偏移 10px）
        popupWindow.showAtLocation(window.decorView, Gravity.NO_GRAVITY, popupX, popupY)
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