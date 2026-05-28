package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.brahmadeo.supertonic.tts.utils.ocr.paddle.PaddleOcrEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified PDF Extractor using PaddleOCR (via ONNX).
 * Works offline and independent of Google Play Services.
 */
class PdfExtractorImpl(private val context: Context) : PdfExtractor {
    private val ocrEngine = PaddleOcrEngine(context)

    override suspend fun extractText(file: File, pageIndices: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("PdfExtractor", "Starting PaddleOCR for file: ${file.name}, pages: $pageIndices")
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val combinedText = StringBuilder()

            // Default to Hindi/English mixed if not specified
            ocrEngine.setLanguage("hi+en")

            for (index in pageIndices.sorted()) {
                if (index !in 0 until renderer.pageCount) {
                    Log.w("PdfExtractor", "Page index $index out of bounds (0..${renderer.pageCount - 1})")
                    continue
                }
                
                Log.d("PdfExtractor", "Processing page $index with PaddleOCR...")
                renderer.openPage(index).use { page ->
                    // 2x resolution is usually enough for PaddleOCR as it's more robust than ML Kit
                    val zoom = 2
                    val bitmap = Bitmap.createBitmap(page.width * zoom, page.height * zoom, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    try {
                        val result = ocrEngine.run(bitmap)
                        if (result.text.isNotBlank()) {
                            combinedText.append(result.text).append("\n\n")
                        } else {
                            Log.w("PdfExtractor", "Page $index: No text detected")
                        }
                    } catch (e: Exception) {
                        Log.e("PdfExtractor", "OCR failed for page $index", e)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            
            renderer.close()
            pfd.close()
            
            val finalResult = combinedText.toString().trim()
            Result.success(finalResult)
        } catch (e: Exception) {
            Log.e("PdfExtractor", "Extraction failed", e)
            Result.failure(e)
        }
    }
}
