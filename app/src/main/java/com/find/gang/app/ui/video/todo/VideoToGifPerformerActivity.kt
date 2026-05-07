package com.find.gang.app.ui.video.todo

import android.content.Context
import android.content.Intent
import com.arthenica.ffmpegkit.FFmpegKit
import com.find.gang.app.R
import com.find.gang.app.toolbox.FileTools
import com.find.gang.app.toolbox.FileTools.copyFile
import com.find.gang.app.toolbox.FileTools.createNewFile
import com.find.gang.app.toolbox.FileTools.formattedFileSize
import com.find.gang.app.toolbox.MediaTools
import com.find.gang.app.toolbox.Toolbox.constraintBy
import com.find.gang.app.toolbox.Toolbox.logRed
import com.find.gang.app.ui.video.edit.MyVideoConstants.EXTRA_TASK_BUILDER_VIDEO_TO_GIF
import com.find.gang.app.ui.video.edit.MyVideoConstants.OUTPUT_GIF_TEMP_PATH
import com.find.gang.app.ui.video.edit.MyVideoConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH
import com.find.gang.app.ui.video.edit.task.TaskBuilderVideoToGif
import kotlin.math.max

class VideoToGifPerformerActivity: BaseVideoPerformActivity() {
    private var previousUpdatedFileSize = 0L

    override fun doPerformOnThread() {
        putProgress(0, getString(R.string.exporting_gif_))
        FileTools.resetDirectory(VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH)
        val command = taskBuilder.getCommandExtractFrame()
        logRed("CommandExtractFrame", command)
        FFmpegKit.executeAsync(command, { completeCallback ->
            when {
                completeCallback.returnCode.isValueSuccess -> performPart2()
                completeCallback.returnCode.isValueError -> quitOrFailed(getString(R.string.an_error_occurred))
            }
        }, { logCallback ->
            logRed("logcallback", logCallback.message.toString())
        }, { statistics ->
            putProgress(
                (statistics.videoFrameNumber * 40 / taskBuilder.getOutputFramesEstimated()).constraintBy(0..40), getString(R.string.exporting_gif_)
            )
        })
    }

    private fun performPart2() {
        val command = taskBuilder.getCommandCreatePalette()
        logRed("commandCreatePalette", command)
        FFmpegKit.executeAsync(command, { completeCallback ->
            when {
                completeCallback.returnCode.isValueSuccess -> performPart3()
                completeCallback.returnCode.isValueError -> quitOrFailed(getString(R.string.an_error_occurred))
            }
        }, { log -> logRed("logcallback", log.message.toString()) }, {})
    }

    private fun performPart3() {
        putProgress(60, getString(R.string.exporting_gif_))
        val command = taskBuilder.getCommandVideoToGif()
        logRed("commandVideoToGif", command)
        FFmpegKit.executeAsync(command, { completeCallback ->
            when {
                completeCallback.returnCode.isValueSuccess -> performPart4()
                completeCallback.returnCode.isValueError -> quitOrFailed(getString(R.string.an_error_occurred))
            }
        }, { log -> logRed("logcallback", log.message.toString()) }, { statistics ->
            previousUpdatedFileSize = max(previousUpdatedFileSize, statistics.size)
            putProgress(
                (60 + statistics.videoFrameNumber * 40 / taskBuilder.getOutputFramesEstimated()).constraintBy(60..100),
                getString(R.string.exporting_gif_) + getString(R.string.____brackets____, statistics.size.formattedFileSize())
            )
        })
    }

    private fun performPart4() {
        with(taskBuilder) {
            lossy?.let {
                putProgress(null, getString(R.string.compressing_gif_raw_size, previousUpdatedFileSize.formattedFileSize()))
                logRed("gifsicleLossy", "start rtime")
                MediaTools.gifsicleLossy(it, OUTPUT_GIF_TEMP_PATH, null, true)
                logRed("gifsicleLossy", "end rtime")
            }
            if (!taskQuitOrFailed) {
                val outputUri = createNewFile(FileTools.FileName(inputVideoPath).nameWithoutExtension, "gif")
                copyFile(OUTPUT_GIF_TEMP_PATH, outputUri, true)
                finish()
                FileSavedActivity.start(this@VideoToGifPerformerActivity, outputUri)
            }
        }
    }

    companion object {
        fun start(context: Context, taskBuilderVideoToGif: TaskBuilderVideoToGif) =
            context.startActivity(Intent(context, VideoToGifPerformerActivity::class.java).apply {
                putExtra(EXTRA_TASK_BUILDER_VIDEO_TO_GIF, taskBuilderVideoToGif)
            })
    }
}
