package com.test.gang.player.widget

import android.os.Build
import android.view.Display
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.*

class Utils {
    companion object{
        public fun playIfCan(activity: PlayerActivity, play: Boolean) {
            if (play) {
                if (PlayerActivity.player != null) PlayerActivity.player.play()
                if (activity.playerView != null) activity.playerView.hideController()
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        public fun handleFrameRate(activity: PlayerActivity, frameRate: Float, play: Boolean) {
            activity.runOnUiThread {
                var switchingModes = false
                if (BuildConfig.DEBUG) Toast.makeText(
                    activity,
                    "Video frameRate: $frameRate",
                    Toast.LENGTH_LONG
                ).show()
                if (frameRate > 0) {
                    val display: Display = activity.getWindow().getDecorView().getDisplay()
                        ?: return@runOnUiThread
                    val supportedModes =
                        display.supportedModes
                    val activeMode = display.mode
                    if (supportedModes.size > 1) {
                        // Refresh rate >= video FPS
                        val modesHigh: MutableList<Display.Mode> =
                            ArrayList()
                        // Max refresh rate
                        var modeTop = activeMode
                        var modesResolutionCount = 0

                        // Filter only resolutions same as current
                        for (mode in supportedModes) {
                            if (mode.physicalWidth == activeMode.physicalWidth &&
                                mode.physicalHeight == activeMode.physicalHeight
                            ) {
                                modesResolutionCount++
                                if (Utils.normRate(mode.refreshRate) >= Utils.normRate(
                                        frameRate
                                    )
                                ) modesHigh.add(mode)
                                if (Utils.normRate(mode.refreshRate) > Utils.normRate(
                                        modeTop.refreshRate
                                    )
                                ) modeTop = mode
                            }
                        }
                        if (modesResolutionCount > 1) {
                            var modeBest: Display.Mode? = null
                            var modes = "Available refreshRates:"
                            for (mode in modesHigh) {
                                modes += " " + mode.refreshRate
                                if (Utils.normRate(mode.refreshRate) % Utils.normRate(
                                        frameRate
                                    ) <= 0.0001f
                                ) {
                                    if (modeBest == null || Utils.normRate(mode.refreshRate) > Utils.normRate(
                                            modeBest.refreshRate
                                        )
                                    ) {
                                        modeBest = mode
                                    }
                                }
                            }
                            val window: Window = activity.getWindow()
                            val layoutParams = window.attributes
                            if (modeBest == null) modeBest = modeTop
                            switchingModes = modeBest.getModeId() != activeMode.modeId
                            if (switchingModes) {
                                layoutParams.preferredDisplayModeId = modeBest.getModeId()
                                window.attributes = layoutParams
                            }
                            if (BuildConfig.DEBUG) Toast.makeText(
                                activity,
                                """
                            $modes
                            Video frameRate: $frameRate
                            Current display refreshRate: ${modeBest.getRefreshRate()}
                            """.trimIndent(),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                if (!switchingModes) {
                    Utils.playIfCan(activity, play)
                }
            }
        }

        fun normRate(rate: Float): Int {
            return (rate * 100f).toInt()
        }
    }

}