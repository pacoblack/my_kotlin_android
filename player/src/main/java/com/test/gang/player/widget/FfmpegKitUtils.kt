package com.test.gang.player.widget

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation

class FfmpegKitUtils {
    private fun getMediaInformation(activity: Activity, uri: Uri): MediaInformation? {
        val path: String
        path = if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            try {
                FFmpegKitConfig.getSafParameterForRead(activity, uri)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        } else if (ContentResolver.SCHEME_FILE == uri.scheme) {
            // TODO: FFprobeKit doesn't accept encoded uri (like %20) (?!)
            uri.schemeSpecificPart
        } else {
            uri.toString()
        }
        val mediaInformationSession = FFprobeKit.getMediaInformation(path)
        return mediaInformationSession.mediaInformation
    }

    fun markChapters(activity: PlayerActivity, uri: Uri?, controlView: PlayerControlView) {
        if (activity.chaptersThread != null) {
            activity.chaptersThread.interrupt()
        }
        activity.chaptersThread = Thread(label@ Runnable {
            val mediaInformation: MediaInformation =
                getMediaInformation(activity, uri) ?: return@label
            val chapters = mediaInformation.chapters
            val starts = LongArray(chapters.size)
            val played = BooleanArray(chapters.size)
            var i = 0
            while (i < chapters.size) {
                val chapter = chapters[i]
                val start = chapter.start
                if (start > 0) {
                    starts[i] = start / 1000000
                    played[i] = true
                }
                i++
            }
            activity.chapterStarts = starts
            activity.runOnUiThread { controlView.setExtraAdGroupMarkers(starts, played) }
        })
        activity.chaptersThread.start()
    }

    fun switchFrameRate(activity: PlayerActivity, uri: Uri?, play: Boolean): Boolean {
        // preferredDisplayModeId only available on SDK 23+
        // ExoPlayer already uses Surface.setFrameRate() on Android 11+
        return if (Build.VERSION.SDK_INT >= 23) {
            if (activity.frameRateSwitchThread != null) {
                activity.frameRateSwitchThread.interrupt()
            }
            activity.frameRateSwitchThread = Thread(label@ Runnable {

                // Use ffprobe as ExoPlayer doesn't detect video frame rate for lots of videos
                // and has different precision than ffprobe (so do not mix that)
                var frameRate: Float = Format.NO_VALUE
                val mediaInformation: MediaInformation =
                    getMediaInformation(activity, uri)
                if (mediaInformation == null) {
                    activity.runOnUiThread { Utils.playIfCan(activity, play) }
                    return@label
                }
                val streamInformations =
                    mediaInformation.streams
                for (streamInformation in streamInformations) {
                    if (streamInformation.type == "video") {
                        val averageFrameRate = streamInformation.averageFrameRate
                        if (averageFrameRate.contains("/")) {
                            val vals =
                                averageFrameRate.split("/".toRegex()).toTypedArray()
                            frameRate = vals[0].toFloat() / vals[1].toFloat()
                            break
                        }
                    }
                }
                Utils.handleFrameRate(activity, frameRate, play)
            })
            activity.frameRateSwitchThread.start()
            true
        } else {
            false
        }
    }
}