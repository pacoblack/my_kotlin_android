package com.find.gang.video.lib.toolbox

object DateUtil {
    fun convertSecondsToTime(seconds: Long): String {
        if (seconds <= 0) return "00:00"

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, secs) else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
