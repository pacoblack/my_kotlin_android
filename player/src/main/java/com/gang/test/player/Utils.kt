package com.gang.test.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.util.Rational
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.gang.test.player.BuildConfig
import com.obsez.android.lib.filechooser.ChooserDialog
import java.io.File
import java.lang.reflect.Method
import java.util.*
import kotlin.math.abs

@UnstableApi
object Utils {
    const val FEATURE_FIRE_TV = "amazon.hardware.fire_tv"
    val supportedExtensionsVideo = arrayOf("3gp", "avi", "m4v", "mkv", "mov", "mp4", "ts", "webm")
    @JvmField
    val supportedExtensionsSubtitle = arrayOf("srt", "ssa", "ass", "vtt", "ttml", "dfxp", "xml")
    @JvmField
    val supportedMimeTypesVideo = arrayOf( // Local mime types on Android:
        MimeTypes.VIDEO_MATROSKA,  // .mkv
        MimeTypes.VIDEO_MP4,  // .mp4, .m4v
        MimeTypes.VIDEO_WEBM,  // .webm
        "video/quicktime",  // .mov
        "video/mp2ts",  // .ts, but also incompatible .m2ts
        MimeTypes.VIDEO_H263,  // .3gp
        "video/avi",  // For remote storages:
        "video/x-m4v"
    )
    @JvmField
    val supportedMimeTypesSubtitle = arrayOf(
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.TEXT_SSA,
        MimeTypes.TEXT_VTT,
        MimeTypes.APPLICATION_TTML,
        "text/*",
        "application/octet-stream"
    )

    @JvmStatic
    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    @JvmStatic
    fun pxToDp(px: Float): Float {
        return px / Resources.getSystem().displayMetrics.density
    }

    @JvmStatic
    fun fileExists(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme
        return if (ContentResolver.SCHEME_CONTENT == scheme) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                inputStream!!.close()
                true
            } catch (e: Exception) {
                false
            }
        } else {
            val path: String = if (ContentResolver.SCHEME_FILE == scheme) {
                uri.path.toString()
            } else {
                uri.toString()
            }
            val file = File(path)
            file.exists()
        }
    }

    @JvmStatic
    fun toggleSystemUi(activity: Activity, playerView: CustomPlayerView, show: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            val window = activity.window
            if (window != null) {
                val windowInsetsController = window.insetsController
                if (windowInsetsController != null) {
                    if (show) {
                        windowInsetsController.show(WindowInsets.Type.systemBars())
                    } else {
                        windowInsetsController.hide(WindowInsets.Type.systemBars())
                    }
                }
            }
        } else {
            if (show) {
                playerView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            } else {
                playerView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            }
        }
    }

    @JvmStatic
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        try {
            if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (columnIndex > -1) result = cursor.getString(columnIndex)
                    }
                }
            }
            if (result == null) {
                result = uri.path
                val cut = result!!.lastIndexOf('/')
                if (cut != -1) {
                    result = result!!.substring(cut + 1)
                }
            }
            if (result!!.indexOf(".") > 0) result = result!!.substring(0, result!!.lastIndexOf("."))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    @JvmStatic
    fun isVolumeMax(audioManager: AudioManager): Boolean {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == audioManager.getStreamMaxVolume(
            AudioManager.STREAM_MUSIC
        )
    }

    fun isVolumeMin(audioManager: AudioManager): Boolean {
        val min =
            if (Build.VERSION.SDK_INT >= 28) audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == min
    }

    @JvmStatic
    fun adjustVolume(
        context: Context,
        audioManager: AudioManager,
        playerView: CustomPlayerView,
        raise: Boolean,
        canBoost: Boolean,
        clear: Boolean
    ) {
        var canBoost = canBoost
        playerView.removeCallbacks(playerView.textClearRunnable)
        val volume = getVolume(context, false, audioManager)
        val volumeMax = getVolume(context, true, audioManager)
        var volumeActive = volume != 0

        // Handle volume changes outside the app (lose boost if volume is not maxed out)
        if (volume != volumeMax) {
            PlayerActivity.boostLevel = 0
        }
        if (PlayerActivity.loudnessEnhancer == null) canBoost = false
        if (volume != volumeMax || PlayerActivity.boostLevel == 0 && !raise) {
            if (PlayerActivity.loudnessEnhancer != null) PlayerActivity.loudnessEnhancer!!.enabled =
                false
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
            )
            val volumeNew = getVolume(context, false, audioManager)
            // Custom volume step on Samsung devices (Sound Assistant)
            if (raise && volume == volumeNew) {
                playerView.volumeUpsInRow++
            } else {
                playerView.volumeUpsInRow = 0
            }
            if (playerView.volumeUpsInRow > 4 && !isVolumeMin(audioManager)) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE or AudioManager.FLAG_SHOW_UI
                )
            } else {
                volumeActive = volumeNew != 0
                playerView.setCustomErrorMessage(if (volumeActive) " $volumeNew" else "")
            }
        } else {
            if (canBoost && raise && PlayerActivity.boostLevel < 10) PlayerActivity.boostLevel++ else if (!raise && PlayerActivity.boostLevel > 0) PlayerActivity.boostLevel--
            if (PlayerActivity.loudnessEnhancer != null) {
                try {
                    PlayerActivity.loudnessEnhancer!!.setTargetGain(PlayerActivity.boostLevel * 200)
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                }
            }
            playerView.setCustomErrorMessage(" " + (volumeMax + PlayerActivity.boostLevel))
        }
        playerView.setIconVolume(volumeActive)
        if (PlayerActivity.loudnessEnhancer != null) PlayerActivity.loudnessEnhancer!!.enabled =
            PlayerActivity.boostLevel > 0
        playerView.setHighlight(PlayerActivity.boostLevel > 0)
        if (clear) {
            playerView.postDelayed(
                playerView.textClearRunnable,
                CustomPlayerView.MESSAGE_TIMEOUT_KEY.toLong()
            )
        }
    }

    private fun getVolume(context: Context, max: Boolean, audioManager: AudioManager): Int {
        if (Build.VERSION.SDK_INT >= 30 && Build.MANUFACTURER.equals(
                "samsung",
                ignoreCase = true
            )
        ) {
            try {
                val method: Method
                var result: Any?
                val clazz = Class.forName("com.samsung.android.media.SemSoundAssistantManager")
                val constructor = clazz.getConstructor(Context::class.java)
                val getMediaVolumeInterval = clazz.getDeclaredMethod("getMediaVolumeInterval")
                result = getMediaVolumeInterval.invoke(constructor.newInstance(context))
                if (result is Int) {
                    val mediaVolumeInterval = result
                    if (mediaVolumeInterval < 10) {
                        method = AudioManager::class.java.getDeclaredMethod(
                            "semGetFineVolume",
                            Int::class.javaPrimitiveType
                        )
                        result = method.invoke(audioManager, AudioManager.STREAM_MUSIC)
                        if (result is Int) {
                            return if (max) {
                                150 / mediaVolumeInterval
                            } else {
                                result / mediaVolumeInterval
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
        return if (max) {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    @JvmStatic
    fun setButtonEnabled(context: Context, button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) context.resources
            .getInteger(R.integer.exo_media_button_opacity_percentage_enabled)
            .toFloat() / 100 else context.resources
            .getInteger(R.integer.exo_media_button_opacity_percentage_disabled).toFloat() / 100
    }

    @JvmStatic
    @JvmOverloads
    fun showText(playerView: CustomPlayerView, text: String?, timeout: Long = 1200) {
        playerView.removeCallbacks(playerView.textClearRunnable)
        playerView.clearIcon()
        playerView.setCustomErrorMessage(text)
        playerView.postDelayed(playerView.textClearRunnable, timeout)
    }

    @JvmStatic
    @SuppressLint("SourceLockedOrientationActivity")
    fun setOrientation(activity: Activity, orientation: Orientation?) {
        when (orientation) {
            Orientation.VIDEO -> if (PlayerActivity.player != null) {
                val format = PlayerActivity.player?.videoFormat
                if (format != null && isPortrait(format)) activity.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else activity.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            Orientation.SYSTEM -> activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @JvmStatic
    fun getNextOrientation(orientation: Orientation?): Orientation {
        return when (orientation) {
            Orientation.VIDEO -> Orientation.SYSTEM
            Orientation.SYSTEM -> Orientation.VIDEO
            else -> Orientation.VIDEO
        }
    }

    fun isRotated(format: Format): Boolean {
        return format.rotationDegrees == 90 || format.rotationDegrees == 270
    }

    @JvmStatic
    fun isPortrait(format: Format): Boolean {
        return if (isRotated(format)) {
            format.width > format.height
        } else {
            format.height > format.width
        }
    }

    @JvmStatic
    fun getRational(format: Format): Rational {
        return if (isRotated(format)) Rational(
            format.height,
            format.width
        ) else Rational(format.width, format.height)
    }

    @JvmStatic
    fun formatMilis(time: Long): String {
        val totalSeconds = abs(time.toInt() / 1000)
        val seconds = totalSeconds % 60
        val minutes = totalSeconds % 3600 / 60
        val hours = totalSeconds / 3600
        return if (hours > 0) String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        ) else String.format("%02d:%02d", minutes, seconds)
    }

    @JvmStatic
    fun formatMilisSign(time: Long): String {
        return if (time > -1000 && time < 1000) formatMilis(time) else (if (time < 0) "−" else "+") + formatMilis(
            time
        )
    }

    @JvmStatic
    fun log(text: String?) {
        if (BuildConfig.DEBUG) {
            Log.d("JustPlayer", text!!)
        }
    }

    @JvmStatic
    fun setViewMargins(
        view: View,
        marginLeft: Int,
        marginTop: Int,
        marginRight: Int,
        marginBottom: Int
    ) {
        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
        layoutParams.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        view.layoutParams = layoutParams
    }

    @JvmStatic
    fun setViewParams(
        view: View,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
        marginLeft: Int,
        marginTop: Int,
        marginRight: Int,
        marginBottom: Int
    ) {
        view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        setViewMargins(view, marginLeft, marginTop, marginRight, marginBottom)
    }

    @JvmStatic
    fun isDeletable(context: Context, uri: Uri): Boolean {
        try {
            if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val columnIndex =
                            cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                        if (columnIndex > -1) {
                            val flags = cursor.getInt(columnIndex)
                            return flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE == DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                        }
                    }
                }
            } else if (ContentResolver.SCHEME_FILE == uri.scheme) {
                if (Build.VERSION.SDK_INT >= 23) {
                    val hasPermission =
                        (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                == PackageManager.PERMISSION_GRANTED)
                    if (!hasPermission) {
                        return false
                    }
                }
                val file = File(uri.schemeSpecificPart)
                return file.canWrite()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun isSupportedNetworkUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme ?: return false
        return scheme.startsWith("http") || scheme == "rtsp"
    }

    @JvmStatic
    fun isTvBox(context: Context): Boolean {
        val pm = context.packageManager

        // TV for sure
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        if (pm.hasSystemFeature(FEATURE_FIRE_TV)) {
            return true
        }

        // Missing Files app (DocumentsUI) means box (some boxes still have non functional app or stub)
        if (!hasSAFChooser(pm)) {
            return true
        }

        // Legacy storage no longer works on Android 11 (level 30)
        if (Build.VERSION.SDK_INT < 30) {
            // (Some boxes still report touchscreen feature)
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                return true
            }
            if (pm.hasSystemFeature("android.hardware.hdmi.cec")) {
                return true
            }
            if (Build.MANUFACTURER.equals("zidoo", ignoreCase = true)) {
                return true
            }
        }

        // Default: No TV - use SAF
        return false
    }

    @JvmStatic
    fun hasSAFChooser(pm: PackageManager?): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/*"
        return intent.resolveActivity(pm!!) != null
    }

    fun normRate(rate: Float): Int {
        return (rate * 100f).toInt()
    }

    @JvmStatic
    fun switchFrameRate(activity: PlayerActivity, uri: Uri, play: Boolean): Boolean {
        // preferredDisplayModeId only available on SDK 23+
        // ExoPlayer already uses Surface.setFrameRate() on Android 11+
        return if (Build.VERSION.SDK_INT >= 23) {
            if (activity.frameRateSwitchThread != null) {
                activity.frameRateSwitchThread!!.interrupt()
            }
            activity.frameRateSwitchThread = Thread( Runnable {

                // Use ffprobe as ExoPlayer doesn't detect video frame rate for lots of videos
                // and has different precision than ffprobe (so do not mix that)
                var frameRate = Format.NO_VALUE.toFloat()
                val mediaInformation = getMediaInformation(activity, uri)
                if (mediaInformation == null) {
                    activity.runOnUiThread { playIfCan(activity, play) }
                    return@Runnable
                }
                val streamInformations = mediaInformation.streams
                for (streamInformation in streamInformations) {
                    if (streamInformation.type == "video") {
                        val averageFrameRate = streamInformation.averageFrameRate
                        if (averageFrameRate.contains("/")) {
                            val vals = averageFrameRate.split("/".toRegex()).toTypedArray()
                            frameRate = vals[0].toFloat() / vals[1].toFloat()
                            break
                        }
                    }
                }
                handleFrameRate(activity, frameRate, play)
            })
            activity.frameRateSwitchThread!!.start()
            true
        } else {
            false
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun handleFrameRate(activity: PlayerActivity, frameRate: Float, play: Boolean) {
        activity.runOnUiThread {
            var switchingModes = false
            if (BuildConfig.DEBUG) Toast.makeText(
                activity,
                "Video frameRate: $frameRate",
                Toast.LENGTH_LONG
            ).show()
            if (frameRate > 0) {
                val display = activity.window.decorView.display ?: return@runOnUiThread
                val supportedModes = display.supportedModes
                val activeMode = display.mode
                if (supportedModes.size > 1) {
                    // Refresh rate >= video FPS
                    val modesHigh: MutableList<Display.Mode> = ArrayList()
                    // Max refresh rate
                    var modeTop = activeMode
                    var modesResolutionCount = 0

                    // Filter only resolutions same as current
                    for (mode in supportedModes) {
                        if (mode.physicalWidth == activeMode.physicalWidth &&
                            mode.physicalHeight == activeMode.physicalHeight
                        ) {
                            modesResolutionCount++
                            if (normRate(mode.refreshRate) >= normRate(frameRate)) modesHigh.add(
                                mode
                            )
                            if (normRate(mode.refreshRate) > normRate(modeTop.refreshRate)) modeTop =
                                mode
                        }
                    }
                    if (modesResolutionCount > 1) {
                        var modeBest: Display.Mode? = null
                        var modes = "Available refreshRates:"
                        for (mode in modesHigh) {
                            modes += " " + mode.refreshRate
                            if (normRate(mode.refreshRate) % normRate(frameRate) <= 0.0001f) {
                                if (modeBest == null || normRate(mode.refreshRate) > normRate(
                                        modeBest.refreshRate
                                    )
                                ) {
                                    modeBest = mode
                                }
                            }
                        }
                        val window = activity.window
                        val layoutParams = window.attributes
                        if (modeBest == null) modeBest = modeTop
                        switchingModes = modeBest!!.modeId != activeMode.modeId
                        if (switchingModes) {
                            layoutParams.preferredDisplayModeId = modeBest.modeId
                            window.attributes = layoutParams
                        }
                        if (BuildConfig.DEBUG) Toast.makeText(
                            activity, """
     $modes
     Video frameRate: $frameRate
     Current display refreshRate: ${modeBest.refreshRate}
     """.trimIndent(), Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            if (!switchingModes) {
                playIfCan(activity, play)
            }
        }
    }

    private fun playIfCan(activity: PlayerActivity, play: Boolean) {
        if (play) {
            if (PlayerActivity.player != null) PlayerActivity.player!!.play()
            if (activity.playerView != null) activity.playerView!!.hideController()
        }
    }

    @JvmStatic
    fun alternativeChooser(activity: PlayerActivity, initialUri: Uri?, video: Boolean): Boolean {
        val startPath: String = if (initialUri != null && File(initialUri.schemeSpecificPart).exists()) {
            initialUri.schemeSpecificPart
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath
        }
        val suffixes = if (video) supportedExtensionsVideo else supportedExtensionsSubtitle
        val chooserDialog = ChooserDialog(activity, R.style.FileChooserStyle_Dark)
            .withStartFile(startPath)
            .withFilter(false, false, *suffixes)
            .withChosenListener { _, pathFile ->
                activity.releasePlayer()
                var uri: Uri? = DocumentFile.fromFile(pathFile).uri
                if (video) {
                    activity.mPrefs.setPersistent(true)
                    activity.mPrefs.updateMedia(activity, uri, null)
                    activity.searchSubtitles()
                } else {
                    // Convert subtitles to UTF-8 if necessary
                    SubtitleUtils.clearCache(activity)
                    uri = SubtitleUtils.convertToUTF(activity, uri)
                    activity.mPrefs.updateSubtitle(uri)
                }
                PlayerActivity.focusPlay = true
                activity.initializePlayer()
            } // to handle the back key pressed or clicked outside the dialog:
            .withOnCancelListener { dialog ->
                dialog.cancel() // MUST have
            }
        chooserDialog
            .withOnBackPressedListener { chooserDialog.goBack() }
            .withOnLastBackPressedListener { dialog: AlertDialog -> dialog.cancel() }
        chooserDialog.build().show()
        return true
    }

    @JvmStatic
    fun isPiPSupported(context: Context): Boolean {
        val packageManager = context.packageManager
        //        if (BuildConfig.FLAVOR_distribution.equals("amazon") && packageManager.hasSystemFeature(FEATURE_FIRE_TV)) {
//            return false;
//        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(
            PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
    }

    @JvmStatic
    val moviesFolderUri: Uri?
        get() {
            var uri: Uri? = null
            if (Build.VERSION.SDK_INT >= 26) {
                val authority = "com.android.externalstorage.documents"
                val documentId = "primary:" + Environment.DIRECTORY_MOVIES
                uri = DocumentsContract.buildDocumentUri(authority, documentId)
            }
            return uri
        }

    @JvmStatic
    fun isProgressiveContainerUri(uri: Uri): Boolean {
        var path = uri.path
        if (path == null) {
            return false
        }
        path = path.toLowerCase()
        for (extension in supportedExtensionsVideo) {
            if (path.endsWith(extension)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    val deviceLanguages: Array<String>
        get() {
            val locales: MutableList<String> = ArrayList()
            if (Build.VERSION.SDK_INT >= 24) {
                val localeList = Resources.getSystem().configuration.locales
                for (i in 0 until localeList.size()) {
                    locales.add(localeList[i].isO3Language)
                }
            } else {
                val locale = Resources.getSystem().configuration.locale
                locales.add(locale.isO3Language)
            }
            return locales.toTypedArray()
        }

    @JvmStatic
    fun getSystemComponent(context: Context, intent: Intent?): ComponentName? {
        val resolveInfos = context.packageManager.queryIntentActivities(
            intent!!, 0
        )
        if (resolveInfos.size < 2) {
            return null
        }
        var systemCount = 0
        var componentName: ComponentName? = null
        for (resolveInfo in resolveInfos) {
            val flags = resolveInfo.activityInfo.applicationInfo.flags
            val system = flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (system) {
                systemCount++
                componentName = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name
                )
            }
        }
        return if (systemCount == 1) {
            componentName
        } else null
    }

    @JvmStatic
    fun normalizeScaleFactor(scaleFactor: Float, min: Float): Float {
        return Math.max(min, Math.min(scaleFactor, 2.0f))
    }

    private fun getMediaInformation(activity: Activity, uri: Uri): MediaInformation? {
        val path: String = when {
            ContentResolver.SCHEME_CONTENT == uri.scheme -> {
                try {
                    FFmpegKitConfig.getSafParameterForRead(activity, uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            ContentResolver.SCHEME_FILE == uri.scheme -> {
                // TODO: FFprobeKit doesn't accept encoded uri (like %20) (?!)
                uri.schemeSpecificPart
            }
            else -> {
                uri.toString()
            }
        }
        val mediaInformationSession = FFprobeKit.getMediaInformation(path)
        return mediaInformationSession.mediaInformation
    }

    @JvmStatic
    fun markChapters(activity: PlayerActivity, uri: Uri, controlView: PlayerControlView) {
        if (activity.chaptersThread != null) {
            activity.chaptersThread!!.interrupt()
        }
        activity.chaptersThread = Thread(Runnable {
            val mediaInformation = getMediaInformation(activity, uri)
                ?: return@Runnable
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
            PlayerActivity.chapterStarts = starts
            activity.runOnUiThread { controlView.setExtraAdGroupMarkers(starts, played) }
        })
        activity.chaptersThread!!.start()
    }

    @JvmStatic
    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 720
    }

    @JvmStatic
    fun <K, V> orderByValue(m: LinkedHashMap<K, V>, c: Comparator<in V>) {
        val entries: List<Map.Entry<K, V>> = ArrayList<Map.Entry<K, V>>(m.entries)
        Collections.sort(entries) { lhs: Map.Entry<K, V>, rhs: Map.Entry<K, V> ->
            c.compare(
                lhs.value,
                rhs.value
            )
        }
        m.clear()
        for ((key, value) in entries) {
            m[key] = value
        }
    }

    enum class Orientation(val value: Int, val description: Int) {
        VIDEO(0, R.string.video_orientation_video), SYSTEM(
            1,
            R.string.video_orientation_system
        ),
        UNSPECIFIED(2, R.string.video_orientation_system);
    }
}