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

            // Use Hindi model as default for mixed text support
            ocrEngine.setLanguage("hi")

            for (index in pageIndices.sorted()) {
                if (index !in 0 until renderer.pageCount) {
                    Log.w("PdfExtractor", "Page index $index out of bounds (0..${renderer.pageCount - 1})")
                    continue
                }
                
                Log.d("PdfExtractor", "Processing page $index with PaddleOCR...")
                renderer.openPage(index).use { page ->
                    // 3x resolution for high-fidelity OCR on complex scripts
                    val zoom = 3
                    val bitmap = Bitmap.createBitmap(page.width * zoom, page.height * zoom, Bitmap.Config.ARGB_8888)
                    
                    try {
                        Log.d("PdfExtractor", "Rendering page $index to bitmap ${bitmap.width}x${bitmap.height}...")
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        Log.d("PdfExtractor", "Running OCR on page $index...")
                        val result = ocrEngine.run(bitmap)
                        Log.d("PdfExtractor", "OCR completed for page $index. Text length: ${result.text.length}, Confidence: ${result.meanConfidence}")
                        
                        if (result.text.isNotBlank()) {
                            combinedText.append(result.text).append("\n\n")
                        } else {
                            Log.w("PdfExtractor", "Page $index: No text detected (Empty result)")
                        }
                    } catch (e: Exception) {
                        Log.e("PdfExtractor", "OCR process failed for page $index: ${e.message}", e)
                        throw Exception("OCR failed on page ${index + 1}: ${e.message}")
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            
            renderer.close()
            pfd.close()
            
            val finalResult = combinedText.toString().trim()
            if (finalResult.isEmpty()) {
                Log.w("PdfExtractor", "No text extracted from any of the selected pages. Quads might be empty.")
                return@withContext Result.failure(Exception("No text could be detected on the selected pages."))
            }
            Result.success(finalResult)
        } catch (e: Exception) {
            Log.e("PdfExtractor", "Extraction failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
