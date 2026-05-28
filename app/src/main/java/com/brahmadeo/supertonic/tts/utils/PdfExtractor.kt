package com.brahmadeo.supertonic.tts.utils

import java.io.File

interface PdfExtractor {
    suspend fun extractText(file: File, pageIndices: List<Int>): Result<String>
}
