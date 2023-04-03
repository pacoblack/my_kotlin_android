package com.test.gang.lib.video

import android.net.Uri
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.Extractor
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.avi.AviExtractor
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import java.util.ArrayList

class AviExtractorsFactory : ExtractorsFactory {
    /**
     * Get the underlying DefaultExtractorsFactory
     */
    val defaultExtractorsFactory = DefaultExtractorsFactory()
    override fun createExtractors(): Array<Extractor> {
        return patchExtractors(defaultExtractorsFactory.createExtractors())
    }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> {
        return patchExtractors(defaultExtractorsFactory.createExtractors())
    }

    /**
     * Hack to work-around DefaultExtractorsFactory being final
     */
    private fun patchExtractors(extractors: Array<Extractor>): Array<Extractor> {
        val list = ArrayList(listOf(*extractors))
        val aviIndex = findExtractor(list, AviExtractor::class.java)
        if (aviIndex != -1) {
            list.removeAt(aviIndex)
        }
        val mp3Index = findExtractor(list, Mp3Extractor::class.java)
        if (mp3Index != -1) {
            //Mp3Extractor falsely sniff()s AVI files, so insert the AviExtractor before it
            // trhak.avi
            list.add(mp3Index, AviExtractor())
        } else {
            list.add(AviExtractor())
        }
        return list.toTypedArray()
    }

    companion object {
        private fun findExtractor(
            list: List<Extractor>,
            extractorClass: Class<out Extractor>
        ): Int {
            for (i in list.indices) {
                if (extractorClass.isInstance(list[i])) {
                    return i
                }
            }
            return -1
        }
    }
}