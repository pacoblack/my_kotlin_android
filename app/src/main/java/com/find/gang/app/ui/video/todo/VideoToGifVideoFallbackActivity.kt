package com.find.gang.app.ui.video.todo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.find.gang.app.R
import com.find.gang.app.databinding.ActivityVideoToGifVideoFallbackBinding
import com.find.gang.app.toolbox.FFmpegKitExtensions.executeAsync
import com.find.gang.app.toolbox.FFmpegKitExtensions.finishFFmpegKitTask
import com.find.gang.app.toolbox.FFmpegKitExtensions.getVideoDurationMsByFFmpeg
import com.find.gang.app.toolbox.FileTools.resetDirectory
import com.find.gang.app.toolbox.Toolbox.getExtra
import com.find.gang.app.toolbox.Toolbox.keepScreenOn
import com.find.gang.app.toolbox.Toolbox.logRed
import com.find.gang.app.toolbox.Toolbox.onClick
import com.find.gang.app.toolbox.Toolbox.toast
import com.find.gang.app.ui.video.edit.MyVideoConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL
import com.find.gang.app.ui.video.edit.MyVideoConstants.INPUT_FILE_DIR
import com.find.gang.app.ui.video.edit.VideoToGifActivity
import com.find.gang.app.ui.video.edit.VideoToGifActivity.Companion.EXTRA_VIDEO_PATH
import com.find.gang.common.BaseActivity
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.roundToInt

class VideoToGifVideoFallbackActivity : BaseActivity<ActivityVideoToGifVideoFallbackBinding>(R.layout.activity_video_to_gif_video_fallback) {
    private val inputVideoPath by lazy { intent.getExtra<String>(EXTRA_VIDEO_PATH) }
    private var taskThread: Thread? = null
    private var taskQuitOrFailed = false

    override fun initView(savedInstanceState: Bundle?) {
        setFinishOnTouchOutside(false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                quitOrFailed(getString(R.string.cancelled))
            }
        })
        binding.mbClose.onClick { quitOrFailed(getString(R.string.cancelled)) }
        taskThread = thread { performFallback() }
    }

    private fun performFallback() {
        keepScreenOn(true)
        val duration = getVideoDurationMsByFFmpeg(inputVideoPath)
        val fallbackMp4Path = "${inputVideoPath}_fallback.mp4"
        val command =
            "$FFMPEG_COMMAND_PREFIX_FOR_ALL -i \"$inputVideoPath\" -c:v libx264 -preset:v veryfast -crf 17 -pix_fmt yuv420p -c:a aac -b:a 128k -y \"$fallbackMp4Path\""
        logRed("command", command)
        logRed("fallbackMp4Path", fallbackMp4Path)
        command.executeAsync({
            when {
                it.returnCode.isValueSuccess && !taskQuitOrFailed -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VideoToGifActivity.start(this, fallbackMp4Path)
                    }
                    finish()
                }

                it.returnCode.isValueError -> {
                    runOnUiThread { toast(R.string.unable_to_read_video) }
                    finish()
                    resetDirectory(INPUT_FILE_DIR)
                }
            }
        }, {
            logRed("logcallback", it.message.toString())
        }, {
            if (duration != null) {
                val progress = min((it.time * 100 / duration).roundToInt(), 99)
                runOnUiThread {
                    binding.mtvTitle.text = getString(R.string.transcoding_video__d_, progress)
                    binding.linearProgressIndicator.isIndeterminate = false
                    binding.linearProgressIndicator.setProgress(progress, true)
                }
            }
        })
    }

    private fun quitOrFailed(toastText: String?) {
        runOnUiThread {
            taskQuitOrFailed = true
            toastText?.let { toast(it) }
            finishFFmpegKitTask()
            taskThread?.interrupt()
            finish()
        }
    }

    override fun onDestroy() {
        keepScreenOn(false)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, inputVideoPath: String) = context.startActivity(
            Intent(context, VideoToGifVideoFallbackActivity::class.java).putExtra(
                EXTRA_VIDEO_PATH, inputVideoPath
            )
        )
    }
}
