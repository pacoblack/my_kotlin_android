package com.find.gang.app.ui.video.todo

import android.content.Context
import android.content.Intent
import com.find.gang.app.R
import com.find.gang.app.toolbox.FFmpegKitExtensions.executeAsync
import com.find.gang.app.toolbox.FileTools
import com.find.gang.app.toolbox.FileTools.copyFile
import com.find.gang.app.toolbox.FileTools.createNewFile
import com.find.gang.app.toolbox.Toolbox.constraintBy
import com.find.gang.app.toolbox.Toolbox.logRed
import com.find.gang.app.ui.video.edit.MyVideoConstants.EXTRA_TASK_BUILDER_VIDEO_TO_GIF
import com.find.gang.app.ui.video.edit.MyVideoConstants.VIDEO_TO_VIDEO_EXTRACTED_FRAMES_FILE
import com.find.gang.app.ui.video.edit.MyVideoConstants.VIDEO_TO_VIDEO_EXTRACTED_FRAMES_PATH
import com.find.gang.app.ui.video.edit.task.TaskBuilderVideoToGif

class EditVideoPerformActivity : BaseVideoPerformActivity() {

    override fun doPerformOnThread() {
        putProgress(0, getString(R.string.exporting_gif_))
        FileTools.resetDirectory(VIDEO_TO_VIDEO_EXTRACTED_FRAMES_PATH)
        val command = taskBuilder.getCommandExportVideo()
        logRed("CommandEditVideo", command)
        command.executeAsync( { completeCallback ->
            when {
                completeCallback.returnCode.isValueSuccess -> onTaskSuccess()
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

    fun onTaskSuccess(){
        with(taskBuilder) {
            val outputUri = createNewFile(FileTools.FileName(inputVideoPath).nameWithoutExtension, "mp4")
            copyFile(VIDEO_TO_VIDEO_EXTRACTED_FRAMES_FILE, outputUri, true)
            finish()
            FileSavedActivity.start(this@EditVideoPerformActivity, outputUri)
        }
    }

    companion object {
        fun start(context: Context, taskBuilderVideoToGif: TaskBuilderVideoToGif) =
            context.startActivity(Intent(context, EditVideoPerformActivity::class.java).apply {
                putExtra(EXTRA_TASK_BUILDER_VIDEO_TO_GIF, taskBuilderVideoToGif)
            })
    }
}
