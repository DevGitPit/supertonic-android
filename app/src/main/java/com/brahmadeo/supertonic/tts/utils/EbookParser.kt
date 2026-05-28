package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.use
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.positions
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import android.graphics.RectF
import java.io.File

class EbookParser(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val pdfFactory = PdfiumDocumentFactory(context)
    private val publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory)
    private val publicationOpener = PublicationOpener(publicationParser)
    
    // Inject the unified OCR extractor
    private val pdfExtractor = PdfExtractorImpl(context)

    suspend fun openPublication(file: File): Result<Publication> = withContext(Dispatchers.IO) {
        try {
            Log.d("EbookParser", "Opening file: ${file.absolutePath}")
            val asset = assetRetriever.retrieve(file).getOrElse { error ->
                return@withContext Result.failure<Publication>(Exception("Failed to retrieve asset: ${error.message}"))
            }

            val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse { error ->
                return@withContext Result.failure<Publication>(Exception("Failed to open publication: ${error.message}"))
            }

            Result.success(publication)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractText(publication: Publication, link: Link? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val startUrl = link?.url()?.toString()
            val startPath = startUrl?.substringBefore('#')
            
            val locator = link?.let { Locator(href = it.url(), mediaType = it.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.BINARY) }
            val content = publication.content(locator)
            
            if (content != null) {
                val chapterText = StringBuilder()
                val elements = content.elements()

                for (element in elements) {
                    val elementUrl = element.locator.href.toString()
                    val elementPath = elementUrl.substringBefore('#')
                    
                    if (startPath != null && elementPath != startPath) {
                        break
                    }
                    
                    if (element is Content.TextualElement) {
                        element.text?.let { if (it.isNotBlank()) chapterText.append(it).append("\n\n") }
                    }
                }
                
                val result = chapterText.toString().trim()
                if (result.isNotBlank()) return@withContext Result.success(result)
            }

            val fallbackText = extractFallback(publication, link)
            if (fallbackText.isNotBlank()) return@withContext Result.success(fallbackText)

            Result.failure(Exception("No text content could be extracted."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractPages(publication: Publication, pageIndices: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        val isPdf = publication.metadata.conformsTo.contains(Publication.Profile.PDF) ||
                    publication.readingOrder.firstOrNull()?.mediaType?.matches(org.readium.r2.shared.util.mediatype.MediaType.PDF) == true

        return@withContext extractPagesReadium(publication, pageIndices, isPdf)
    }

    suspend fun extractPages(file: File, publication: Publication, pageIndices: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        val isPdf = file.extension.lowercase() == "pdf" ||
                    publication.metadata.conformsTo.contains(Publication.Profile.PDF) ||
                    publication.readingOrder.firstOrNull()?.mediaType?.matches(org.readium.r2.shared.util.mediatype.MediaType.PDF) == true

        if (isPdf) {
            val result = pdfExtractor.extractText(file, pageIndices)
            return@withContext result.map { cleanPdfText(it) }
        } else {
            return@withContext extractPagesReadium(publication, pageIndices, isPdf)
        }
    }

    private fun cleanPdfText(text: String): String {
        // 1. Fix the "Broken Word" Hyphenation across line breaks
        // Handles cases with spaces/newlines around the hyphen
        var cleaned = text.replace(Regex("(\\p{L})-\\s*\\r?\\n\\s*(\\p{L})"), "$1$2")
        
        // 2. Fix Drop Caps
        cleaned = cleaned.replace(Regex("(?m)^([A-Z])\\s*\\r?\\n\\s*([a-z]{2,})"), "$1$2")
        cleaned = cleaned.replace(Regex("(?m)^([A-Z])\\s+([a-z]{2,})"), "$1$2")
        
        // 3. Join single newlines into paragraphs (preserving spaces)
        // Join if line doesn't end in sentence punctuation and next char is a letter
        // Added Hindi punctuation: । (U+0964) and ॥ (U+0965)
        cleaned = cleaned.replace(Regex("([^.!?\\-।॥])\\s*\\r?\\n\\s*([\\p{L}])"), "$1 $2")

        // 4. Remove lone page numbers on their own lines
        cleaned = cleaned.replace(Regex("(?m)^\\s*\\d+\\s*$"), "")

        // 5. Fix multiple spaces and excessive newlines
        return cleaned.replace(Regex("[ ]{2,}"), " ")
            .replace(Regex("(\\n\\s*){3,}"), "\n\n")
            .trim()
    }

    private suspend fun extractPagesReadium(publication: Publication, pageIndices: List<Int>, isPdf: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val positions = publication.positions()
            val combinedText = StringBuilder()
            
            Log.d("EbookParser", "Extracting pages: $pageIndices, isPdf=$isPdf, totalPositions=${positions.size}")

            for (index in pageIndices.sorted()) {
                if (index !in positions.indices) continue
                
                val locator = positions[index]
                Log.d("EbookParser", "Page index $index locator: $locator")
                
                val content = publication.content(locator)
                if (content != null) {
                    val iterator = content.iterator()
                    var elementsFound = 0
                    
                    while (iterator.hasNext()) {
                        val element = iterator.next()
                        
                        // Stop if we move to the next page's locator
                        if (elementsFound > 0 && index + 1 < positions.size) {
                            val nextLocator = positions[index + 1]
                            
                            val hrefChanged = element.locator.href != nextLocator.href
                            val fragmentChanged = nextLocator.locations.fragments.isNotEmpty() && 
                                                 element.locator.locations.fragments != nextLocator.locations.fragments
                            val positionChanged = nextLocator.locations.position != null && 
                                                 element.locator.locations.position != nextLocator.locations.position
                            
                            if (hrefChanged || fragmentChanged || positionChanged) {
                                break
                            }
                        }

                        if (element is Content.TextualElement) {
                            element.text?.let { 
                                if (it.isNotBlank()) {
                                    combinedText.append(it).append(" ") 
                                    elementsFound++
                                }
                            }
                        }
                        
                        // Limit elements per page to avoid getting too much if break condition fails
                        if (elementsFound > 200) break 
                    }
                    combinedText.append("\n\n")
                    Log.d("EbookParser", "Extracted $elementsFound elements from page $index")
                } else {
                    Log.w("EbookParser", "No content service for page $index")
                }
            }
            
            val result = combinedText.toString().trim()
            if (result.isBlank()) {
                return@withContext Result.failure(Exception("No text found on selected pages."))
            }
            val finalResult = if (isPdf) cleanPdfText(result) else result
            Result.success(finalResult)
        } catch (e: Exception) {
            Log.e("EbookParser", "Error in extractPagesReadium", e)
            Result.failure(e)
        }
    }

    private suspend fun extractFallback(publication: Publication, link: Link?): String {
        val fullText = StringBuilder()
        if (link != null) {
            val bytes = publication.get(link)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
            val text = bytes.decodeToString()
            fullText.append(renderHtml(text))
        } else {
            for (readingLink in publication.readingOrder) {
                val bytes = publication.get(readingLink)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
                val text = bytes.decodeToString()
                val rendered = renderHtml(text)
                if (rendered.isNotBlank()) {
                    fullText.append(rendered).append("\n\n")
                }
            }
        }
        return fullText.toString().trim()
    }

    private fun renderHtml(html: String): String {
        if (!html.contains("<html", ignoreCase = true) && !html.contains("<body", ignoreCase = true)) return html
        
        var text = html
        text = text.replace(Regex("<head>.*?</head>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<script.*?>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<style.*?>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        
        text = text.replace(Regex("<(p|div|h[1-6]|li|br|tr|blockquote|title|header|footer).*?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<[^>]*>"), " ")
        
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
        
        return text.split("\n")
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }
}
