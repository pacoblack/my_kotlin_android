package com.find.gang.video.lib.interfaces

interface VideoTrimListener {
    fun onStartTrim()
    fun onFinishTrim(url: String?)
    fun onCancel()
}
