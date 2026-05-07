package com.find.gang.app.ui.video.todo

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.find.gang.app.R
import com.find.gang.app.databinding.ActivityVideoToGifPerformerBinding
import com.find.gang.app.toolbox.FFmpegKitExtensions.finishFFmpegKitTask
import com.find.gang.app.toolbox.Toolbox
import com.find.gang.app.toolbox.Toolbox.constraintBy
import com.find.gang.app.toolbox.Toolbox.getExtra
import com.find.gang.app.toolbox.Toolbox.keepScreenOn
import com.find.gang.app.toolbox.Toolbox.onClick
import com.find.gang.app.ui.video.edit.MyVideoConstants.EXTRA_TASK_BUILDER_VIDEO_TO_GIF
import com.find.gang.app.ui.video.edit.task.TaskBuilderVideoToGif
import com.find.gang.common.BaseActivity
import kotlin.concurrent.thread

open class BaseVideoPerformActivity : BaseActivity<ActivityVideoToGifPerformerBinding>(R.layout.activity_video_to_gif_performer) {
    protected var taskThread: Thread? = null
    protected var taskQuitOrFailed = false
    protected val taskBuilder by lazy { intent.getExtra<TaskBuilderVideoToGif>(EXTRA_TASK_BUILDER_VIDEO_TO_GIF) }

    override fun initView(savedInstanceState: Bundle?) {
        setFinishOnTouchOutside(false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                quitOrFailed(getString(R.string.cancelled))
            }
        })
        binding.mbClose.onClick {
            quitOrFailed(getString(R.string.cancelled))
        }
        taskThread = thread { doPerformOnThread() }
    }

    protected fun quitOrFailed(toastText: String?) {
        runOnUiThread {
            taskQuitOrFailed = true
            toastText?.let { Toolbox.toast(it) }
            finishFFmpegKitTask()
            taskThread?.interrupt()
            finish()
        }
    }

    protected fun putProgress(progress: Int?, text: String) {
        runOnUiThread {
            binding.linearProgressIndicator.apply {
                if (progress == null) {
                    isIndeterminate = true
                } else {
                    isIndeterminate = false
                    setProgress(progress.constraintBy(0..100), true)
                }
            }
            binding.mtvTitle.text = text
        }
    }

    override fun onDestroy() {
        keepScreenOn(false)
        super.onDestroy()
    }

    open fun doPerformOnThread(){}

}