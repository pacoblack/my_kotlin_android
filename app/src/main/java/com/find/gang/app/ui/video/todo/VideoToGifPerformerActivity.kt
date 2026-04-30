package com.find.gang.app.ui.video.todo

import com.find.gang.app.ui.video.edit.VideoToGifActivity
import com.find.gang.app.ui.video.edit.task.TaskBuilderVideoToGif
import com.google.android.material.button.MaterialButton

class VideoToGifPerformerActivity(lambda: MaterialButton.() -> Unit) {
    companion object {
        fun start(vtgActivity: VideoToGifActivity, createTaskBuilder: TaskBuilderVideoToGif) {}
    }

}
