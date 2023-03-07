package com.homesoft.exo.extractor.avi

import androidx.media3.extractor.ExtractorInput
import java.io.IOException

interface IReader {
    val position: Long

    /**
     * @return true if the reader is complete
     */
    @Throws(IOException::class)
    fun read(input: ExtractorInput): Boolean
}