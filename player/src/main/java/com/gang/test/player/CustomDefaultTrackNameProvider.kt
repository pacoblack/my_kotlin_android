package com.gang.test.player

import android.content.res.Resources
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTrackNameProvider
import com.gang.test.player.BuildConfig

@UnstableApi
internal class CustomDefaultTrackNameProvider(resources: Resources?) : DefaultTrackNameProvider(
    resources!!
) {
    override fun getTrackName(format: Format): String {
        var trackName = super.getTrackName(format)
        if (format.sampleMimeType != null) {
            var sampleFormat = formatNameFromMime(format.sampleMimeType)
            if (BuildConfig.DEBUG && sampleFormat == null) {
                sampleFormat = format.sampleMimeType
            }
            trackName += " ($sampleFormat)"
        }
        if (format.label != null) {
            if (!trackName.startsWith(format.label!!)) { // HACK
                trackName += " - " + format.label
            }
        }
        return trackName
    }

    private fun formatNameFromMime(mimeType: String?): String? {
        when (mimeType) {
            MimeTypes.AUDIO_DTS -> return "DTS"
            MimeTypes.AUDIO_DTS_HD -> return "DTS-HD"
            MimeTypes.AUDIO_DTS_EXPRESS -> return "DTS Express"
            MimeTypes.AUDIO_TRUEHD -> return "TrueHD"
            MimeTypes.AUDIO_AC3 -> return "AC-3"
            MimeTypes.AUDIO_E_AC3 -> return "E-AC-3"
            MimeTypes.AUDIO_E_AC3_JOC -> return "E-AC-3-JOC"
            MimeTypes.AUDIO_AC4 -> return "AC-4"
            MimeTypes.AUDIO_AAC -> return "AAC"
            MimeTypes.AUDIO_MPEG -> return "MP3"
            MimeTypes.AUDIO_MPEG_L2 -> return "MP2"
            MimeTypes.AUDIO_VORBIS -> return "Vorbis"
            MimeTypes.AUDIO_OPUS -> return "Opus"
            MimeTypes.AUDIO_FLAC -> return "FLAC"
            MimeTypes.AUDIO_ALAC -> return "ALAC"
            MimeTypes.AUDIO_WAV -> return "WAV"
            MimeTypes.AUDIO_AMR -> return "AMR"
            MimeTypes.AUDIO_AMR_NB -> return "AMR-NB"
            MimeTypes.AUDIO_AMR_WB -> return "AMR-WB"
            MimeTypes.APPLICATION_PGS -> return "PGS"
            MimeTypes.APPLICATION_SUBRIP -> return "SRT"
            MimeTypes.TEXT_SSA -> return "SSA"
            MimeTypes.TEXT_VTT -> return "VTT"
            MimeTypes.APPLICATION_TTML -> return "TTML"
            MimeTypes.APPLICATION_TX3G -> return "TX3G"
            MimeTypes.APPLICATION_DVBSUBS -> return "DVB"
        }
        return null
    }
}