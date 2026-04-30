package com.find.gang.app.toolbox

import androidx.annotation.IntRange
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.find.gang.app.toolbox.MediaTools.getRotationFromProperties
import com.find.gang.app.toolbox.Toolbox.swapIf
import com.find.gang.app.toolbox.Toolbox.toEmptyStringIf
import com.find.gang.app.ui.video.edit.MyVideoConstants
import kotlin.math.roundToInt

object FFmpegKitExtensions {
    fun getVideoSingleFrameWithFFmpeg(
        path: String, timestamp_ms: Long, @IntRange(2, 31) quality: Int, outputPath: String
    ) =
        FFmpegKit.execute("${MyVideoConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN} -ss ${timestamp_ms}ms -i \"$path\" -frames:v 1 -q:v $quality -y \"$outputPath\"")!!

    /** Slow operation: this function may takes at least 5s! */
    fun videoKeyFramesTimestampList(path: String) =
        FFprobeKit.execute("-loglevel error -skip_frame nokey -select_streams v:0 -show_entries frame=pts_time \"$path\"")
            .allLogsAsString
            .split("\n")
            .filter { it.startsWith("pts_time=") }
            .map { ((it.split('=')[1]).toFloat() * 1000f).toInt() }

    fun mediaInformation(path: String, withFrames: Boolean = false): MediaInformation? =
        FFprobeKit.getMediaInformationFromCommand(
            "-v quiet -hide_banner -print_format json -show_format -show_streams ${("-show_frames ").toEmptyStringIf { !withFrames }}-i \"$path\""
        ).mediaInformation

    fun MediaInformation.firstVideoStream() = streams.firstOrNull { it.type == "video" }

    fun getVideoRotation(path: String) =
        getRotationFromProperties(mediaInformation(path)!!.firstVideoStream()!!.allProperties)

    fun getImageRotation(path: String) = getRotationFromProperties(
        mediaInformation(path, true)!!.allProperties.getJSONArray("frames").getJSONObject(0)
    )

    /**
     * @param rotation can be obtained via getVideoRotation() or getImageRotation()
     */
    fun getRotatedWidthAndHeight(path: String, rotation: Int) = (mediaInformation(path)!!.firstVideoStream()!!).let {
        Pair(it.width.toInt(), it.height.toInt()).swapIf { rotation % 180 != 0 }
    }

    fun getVideoDurationMsByFFmpeg(path: String) = try {
        val mediaInformation = mediaInformation(path)!!
        (((mediaInformation.firstVideoStream()!!.getStringProperty("duration"))
            ?: (mediaInformation.duration)).toFloat() * 1000f).roundToInt()
    } catch (_: Exception) {
        null
    }

    fun getVideoFps(path: String) = try {
        val fpsFraction = mediaInformation(path)!!.streams.first { it.type == "video" }.averageFrameRate
        val numerator = fpsFraction.split("/").toTypedArray()[0].toInt()
        val denominator = fpsFraction.split("/").toTypedArray()[1].toInt()
        numerator.toDouble() / denominator
    } catch (_: Exception) {
        null
    }

    fun String.executeFFmpeg(): Any {
        // 这里的 this 就是调用该方法的 String 对象本身
        return FFmpegKit.execute(this)  // 根据你的逻辑构造 A 对象
    }
}