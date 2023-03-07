package com.gang.test.player

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.preference.PreferenceManager
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.gang.test.player.SubtitleUtils.getTrailPathFromUri
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*

class Prefs(val mContext: Context) {
    val mSharedPreferences: SharedPreferences
    @JvmField
    var mediaUri: Uri? = null
    @JvmField
    var subtitleUri: Uri? = null
    @JvmField
    var scopeUri: Uri? = null
    @JvmField
    var mediaType: String? = null
    @JvmField
    var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    @JvmField
    var orientation = Utils.Orientation.UNSPECIFIED
    @JvmField
    var scale = 1f
    @JvmField
    var speed = 1f
    @JvmField
    var subtitleTrackId: String? = null
    @JvmField
    var audioTrackId: String? = null
    @JvmField
    var brightness = -1
    @JvmField
    var firstRun = true
    @JvmField
    var askScope = true
    @JvmField
    var autoPiP = false
    @JvmField
    var tunneling = false
    @JvmField
    var skipSilence = false
    @JvmField
    var frameRateMatching = false
    @JvmField
    var repeatToggle = false
    @JvmField
    var fileAccess: String? = "auto"
    @JvmField
    var decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    @JvmField
    var mapDV7ToHevc = false
    @JvmField
    var languageSubtitle: String? = TRACK_DEFAULT
    @JvmField
    var languageAudio: String? = TRACK_DEVICE
    private var positions: LinkedHashMap<String, Long>? = null
    @JvmField
    var persistentMode = true
    @JvmField
    var nonPersitentPosition = -1L
    private fun loadSavedPreferences() {
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_URI)) mediaUri = Uri.parse(
            mSharedPreferences.getString(
                PREF_KEY_MEDIA_URI, null
            )
        )
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_TYPE)) mediaType =
            mSharedPreferences.getString(
                PREF_KEY_MEDIA_TYPE, null
            )
        brightness = mSharedPreferences.getInt(PREF_KEY_BRIGHTNESS, brightness)
        firstRun = mSharedPreferences.getBoolean(PREF_KEY_FIRST_RUN, firstRun)
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_URI)) subtitleUri = Uri.parse(
            mSharedPreferences.getString(
                PREF_KEY_SUBTITLE_URI, null
            )
        )
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_ID)) audioTrackId =
            mSharedPreferences.getString(
                PREF_KEY_AUDIO_TRACK_ID, audioTrackId
            )
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK_ID)) subtitleTrackId =
            mSharedPreferences.getString(
                PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId
            )
        if (mSharedPreferences.contains(PREF_KEY_RESIZE_MODE)) resizeMode =
            mSharedPreferences.getInt(
                PREF_KEY_RESIZE_MODE, resizeMode
            )
        orientation = Utils.Orientation.values()[mSharedPreferences.getInt(
            PREF_KEY_ORIENTATION,
            orientation.value
        )]
        scale = mSharedPreferences.getFloat(PREF_KEY_SCALE, scale)
        if (mSharedPreferences.contains(PREF_KEY_SCOPE_URI)) scopeUri = Uri.parse(
            mSharedPreferences.getString(
                PREF_KEY_SCOPE_URI, null
            )
        )
        askScope = mSharedPreferences.getBoolean(PREF_KEY_ASK_SCOPE, askScope)
        speed = mSharedPreferences.getFloat(PREF_KEY_SPEED, speed)
        loadUserPreferences()
    }

    fun loadUserPreferences() {
        autoPiP = mSharedPreferences.getBoolean(PREF_KEY_AUTO_PIP, autoPiP)
        tunneling = mSharedPreferences.getBoolean(PREF_KEY_TUNNELING, tunneling)
        skipSilence = mSharedPreferences.getBoolean(PREF_KEY_SKIP_SILENCE, skipSilence)
        frameRateMatching =
            mSharedPreferences.getBoolean(PREF_KEY_FRAMERATE_MATCHING, frameRateMatching)
        repeatToggle = mSharedPreferences.getBoolean(PREF_KEY_REPEAT_TOGGLE, repeatToggle)
        fileAccess = mSharedPreferences.getString(PREF_KEY_FILE_ACCESS, fileAccess)
        decoderPriority = mSharedPreferences.getString(
            PREF_KEY_DECODER_PRIORITY,
            decoderPriority.toString()
        )!!.toInt()
        mapDV7ToHevc = mSharedPreferences.getBoolean(PREF_KEY_MAP_DV7, mapDV7ToHevc)
        languageSubtitle =
            mSharedPreferences.getString(PREF_KEY_LANGUAGE_SUBTITLE, languageSubtitle)
        languageAudio = mSharedPreferences.getString(PREF_KEY_LANGUAGE_AUDIO, languageAudio)
    }

    fun updateMedia(context: Context, uri: Uri?, type: String?) {
        mediaUri = uri
        mediaType = type
        updateSubtitle(null)
        updateMeta(null, null, AspectRatioFrameLayout.RESIZE_MODE_FIT, 1f, 1f)
        if (mediaType != null && mediaType!!.endsWith("/*")) {
            mediaType = null
        }
        if (mediaType == null) {
            if (ContentResolver.SCHEME_CONTENT == mediaUri!!.scheme) {
                mediaType = context.contentResolver.getType(mediaUri!!)
            }
        }
        if (persistentMode) {
            val sharedPreferencesEditor = mSharedPreferences.edit()
            if (mediaUri == null) sharedPreferencesEditor.remove(PREF_KEY_MEDIA_URI) else sharedPreferencesEditor.putString(
                PREF_KEY_MEDIA_URI, mediaUri.toString()
            )
            if (mediaType == null) sharedPreferencesEditor.remove(PREF_KEY_MEDIA_TYPE) else sharedPreferencesEditor.putString(
                PREF_KEY_MEDIA_TYPE, mediaType
            )
            sharedPreferencesEditor.apply()
        }
    }

    fun updateSubtitle(uri: Uri?) {
        subtitleUri = uri
        subtitleTrackId = null
        if (persistentMode) {
            val sharedPreferencesEditor = mSharedPreferences.edit()
            if (uri == null) sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_URI) else sharedPreferencesEditor.putString(
                PREF_KEY_SUBTITLE_URI, uri.toString()
            )
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID)
            sharedPreferencesEditor.apply()
        }
    }

    fun updatePosition(position: Long) {
        if (mediaUri == null) return
        while (positions!!.size > 100) positions!!.remove(positions!!.keys.toTypedArray()[0])
        if (persistentMode) {
            positions!![mediaUri.toString()] = position
            savePositions()
        } else {
            nonPersitentPosition = position
        }
    }

    fun updateBrightness(brightness: Int) {
        if (brightness >= -1) {
            this.brightness = brightness
            val sharedPreferencesEditor = mSharedPreferences.edit()
            sharedPreferencesEditor.putInt(PREF_KEY_BRIGHTNESS, brightness)
            sharedPreferencesEditor.apply()
        }
    }

    fun markFirstRun() {
        firstRun = false
        val sharedPreferencesEditor = mSharedPreferences.edit()
        sharedPreferencesEditor.putBoolean(PREF_KEY_FIRST_RUN, false)
        sharedPreferencesEditor.apply()
    }

    fun markScopeAsked() {
        askScope = false
        val sharedPreferencesEditor = mSharedPreferences.edit()
        sharedPreferencesEditor.putBoolean(PREF_KEY_ASK_SCOPE, false)
        sharedPreferencesEditor.apply()
    }

    private fun savePositions() {
        try {
            val fos = mContext.openFileOutput("positions", Context.MODE_PRIVATE)
            val os = ObjectOutputStream(fos)
            os.writeObject(positions)
            os.close()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPositions() {
        try {
            val fis = mContext.openFileInput("positions")
            val ois = ObjectInputStream(fis)
            positions = ois.readObject() as LinkedHashMap<String, Long>
            ois.close()
            fis.close()
        } catch (e: Exception) {
            e.printStackTrace()
            positions = LinkedHashMap<String, Long>(10)
        }
    }

    // Return position for uri from limited scope (loaded after using Next action)
    val position: Long
        get() {
            if (!persistentMode) {
                return nonPersitentPosition
            }
            val `val` = positions!![mediaUri.toString()]
            if (`val` != null) return `val` as Long

            // Return position for uri from limited scope (loaded after using Next action)
            if (ContentResolver.SCHEME_CONTENT == mediaUri!!.scheme) {
                val searchPath = getTrailPathFromUri(mediaUri!!)
                if (searchPath?.isEmpty() != false) return 0L
                val keySet: Set<String> = positions!!.keys
                val keys: Array<Any> = keySet.toTypedArray()
                for (i in keys.size downTo 1) {
                    val key = keys[i - 1] as String
                    val uri = Uri.parse(key)
                    if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                        val keyPath = getTrailPathFromUri(uri)
                        if (searchPath == keyPath) {
                            return positions!![key] as Long
                        }
                    }
                }
            }
            return 0L
        }

    fun updateOrientation() {
        val sharedPreferencesEditor = mSharedPreferences.edit()
        sharedPreferencesEditor.putInt(PREF_KEY_ORIENTATION, orientation.value)
        sharedPreferencesEditor.apply()
    }

    fun updateMeta(
        audioTrackId: String?,
        subtitleTrackId: String?,
        resizeMode: Int,
        scale: Float,
        speed: Float
    ) {
        this.audioTrackId = audioTrackId
        this.subtitleTrackId = subtitleTrackId
        this.resizeMode = resizeMode
        this.scale = scale
        this.speed = speed
        if (persistentMode) {
            val sharedPreferencesEditor = mSharedPreferences.edit()
            if (audioTrackId == null) sharedPreferencesEditor.remove(PREF_KEY_AUDIO_TRACK_ID) else sharedPreferencesEditor.putString(
                PREF_KEY_AUDIO_TRACK_ID, audioTrackId
            )
            if (subtitleTrackId == null) sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID) else sharedPreferencesEditor.putString(
                PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId
            )
            sharedPreferencesEditor.putInt(PREF_KEY_RESIZE_MODE, resizeMode)
            sharedPreferencesEditor.putFloat(PREF_KEY_SCALE, scale)
            sharedPreferencesEditor.putFloat(PREF_KEY_SPEED, speed)
            sharedPreferencesEditor.apply()
        }
    }

    fun updateScope(uri: Uri?) {
        scopeUri = uri
        val sharedPreferencesEditor = mSharedPreferences.edit()
        if (uri == null) sharedPreferencesEditor.remove(PREF_KEY_SCOPE_URI) else sharedPreferencesEditor.putString(
            PREF_KEY_SCOPE_URI, uri.toString()
        )
        sharedPreferencesEditor.apply()
    }

    fun setPersistent(persistentMode: Boolean) {
        this.persistentMode = persistentMode
    }

    companion object {
        // Previously used
        // private static final String PREF_KEY_AUDIO_TRACK = "audioTrack";
        // private static final String PREF_KEY_AUDIO_TRACK_FFMPEG = "audioTrackFfmpeg";
        // private static final String PREF_KEY_SUBTITLE_TRACK = "subtitleTrack";
        private const val PREF_KEY_MEDIA_URI = "mediaUri"
        private const val PREF_KEY_MEDIA_TYPE = "mediaType"
        private const val PREF_KEY_BRIGHTNESS = "brightness"
        private const val PREF_KEY_FIRST_RUN = "firstRun"
        private const val PREF_KEY_SUBTITLE_URI = "subtitleUri"
        private const val PREF_KEY_AUDIO_TRACK_ID = "audioTrackId"
        private const val PREF_KEY_SUBTITLE_TRACK_ID = "subtitleTrackId"
        private const val PREF_KEY_RESIZE_MODE = "resizeMode"
        private const val PREF_KEY_ORIENTATION = "orientation"
        private const val PREF_KEY_SCALE = "scale"
        private const val PREF_KEY_SCOPE_URI = "scopeUri"
        private const val PREF_KEY_ASK_SCOPE = "askScope"
        private const val PREF_KEY_AUTO_PIP = "autoPiP"
        private const val PREF_KEY_TUNNELING = "tunneling"
        private const val PREF_KEY_SKIP_SILENCE = "skipSilence"
        private const val PREF_KEY_FRAMERATE_MATCHING = "frameRateMatching"
        private const val PREF_KEY_REPEAT_TOGGLE = "repeatToggle"
        private const val PREF_KEY_SPEED = "speed"
        private const val PREF_KEY_FILE_ACCESS = "fileAccess"
        private const val PREF_KEY_DECODER_PRIORITY = "decoderPriority"
        private const val PREF_KEY_MAP_DV7 = "mapDV7ToHevc"
        private const val PREF_KEY_LANGUAGE_SUBTITLE = "languageSubtitle"
        private const val PREF_KEY_LANGUAGE_AUDIO = "languageAudio"
        const val TRACK_DEFAULT = "default"
        const val TRACK_DEVICE = "device"
        const val TRACK_NONE = "none"
    }

    init {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            mContext
        )
        loadSavedPreferences()
        loadPositions()
    }
}