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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class EbookParser(context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val pdfFactory = PdfiumDocumentFactory(context)
    private val publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory)
    private val publicationOpener = PublicationOpener(publicationParser)

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

    private fun List<Link>.flatten(): List<Link> {
        val result = mutableListOf<Link>()
        fun traverse(links: List<Link>) {
            for (link in links) {
                result.add(link)
                traverse(link.children)
            }
        }
        traverse(this)
        return result
    }

    private fun getRelativePath(urlOrPath: String): String {
        var path = urlOrPath.substringAfter("://", urlOrPath)
        if (path.contains("/")) {
            path = path.substringAfter("/", path)
        }
        val lowerPath = path.lowercase()
        val epubIdx = lowerPath.indexOf(".epub/")
        if (epubIdx != -1) {
            path = path.substring(epubIdx + 6)
        } else {
            val zipIdx = lowerPath.indexOf(".zip/")
            if (zipIdx != -1) {
                path = path.substring(zipIdx + 5)
            }
        }
        path = path.substringBefore('?').substringBefore('#')
        return path.trim('/')
    }

    private fun pathsMatch(path1: String, path2: String): Boolean {
        val p1 = getRelativePath(path1)
        val p2 = getRelativePath(path2)
        return p1 == p2 || p1.endsWith("/$p2") || p2.endsWith("/$p1")
    }

    private fun extractTextBetween(
        document: Document,
        startElement: Element?,
        endElement: Element?
    ): String {
        val textBuilder = StringBuilder()
        var started = startElement == null
        var finished = false

        fun traverse(node: Node) {
            if (finished) return

            if (node == startElement) {
                started = true
            }
            if (endElement != null && node == endElement) {
                finished = true
                return
            }

            if (started) {
                if (node is TextNode) {
                    val text = node.text()
                    if (text.isNotBlank()) {
                        textBuilder.append(text)
                    }
                } else if (node is Element) {
                    val tag = node.normalName()
                    if (tag == "br" || tag == "p" || tag == "div" || tag == "li" || tag == "h1" || tag == "h2" || tag == "h3" || tag == "h4" || tag == "h5" || tag == "h6") {
                        textBuilder.append("\n")
                    }
                }
            }

            for (child in node.childNodes()) {
                traverse(child)
                if (finished) return
            }

            if (started && node is Element) {
                val tag = node.normalName()
                if (tag == "p" || tag == "div" || tag == "li" || tag == "h1" || tag == "h2" || tag == "h3" || tag == "h4" || tag == "h5" || tag == "h6") {
                    textBuilder.append("\n")
                }
            }
        }

        traverse(document.body())
        val rawText = textBuilder.toString()
        return rawText.split("\n")
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    suspend fun extractWebpageText(url: String): String = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .timeout(10000)
            .get()
            
        // Clean up noise
        doc.select("script, style, header, footer, nav, aside, iframe, noscript, .comments, .sidebar, .menu, .nav, .footer, .header, #comments, #sidebar, #footer, #header, #nav").remove()
        
        // Find main content
        val mainElement = doc.select("article").firstOrNull()
            ?: doc.select("main").firstOrNull()
            ?: doc.select("[id*=article], [id*=content], [id*=story], [id*=post-body], [class*=article], [class*=content], [class*=story]").firstOrNull()
            ?: doc.body()
            
        extractTextBetween(doc, mainElement, null)
    }

    private suspend fun extractSingleLinkText(
        publication: Publication,
        linkToExtract: Link,
        startLocator: Locator? = null
    ): String {
        val rawUrl = startLocator?.href ?: linkToExtract.url()
        val urlStr = rawUrl.toString()
        val fragment = urlStr.substringAfter('#', "")
        
        if (fragment.isNotEmpty()) {
            try {
                val matchingLink = publication.readingOrder.firstOrNull {
                    pathsMatch(it.href.toString(), urlStr)
                } ?: linkToExtract
                val bytes = publication.get(matchingLink)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
                val htmlText = bytes.decodeToString()
                if (htmlText.isNotBlank()) {
                    val document = Jsoup.parse(htmlText)
                    val startElement = document.getElementById(fragment) ?: document.selectFirst("[name=$fragment]")
                    
                    var endElement: Element? = null
                    val flatToc = publication.tableOfContents.flatten()
                    val currentIndex = flatToc.indexOfFirst {
                        pathsMatch(it.href.toString(), urlStr) && it.href.toString().substringAfter('#', "") == fragment
                    }
                    if (currentIndex != -1 && currentIndex + 1 < flatToc.size) {
                        val nextLink = flatToc[currentIndex + 1]
                        val nextUrlStr = nextLink.href.toString()
                        if (pathsMatch(nextUrlStr, urlStr)) {
                            val nextFragment = nextUrlStr.substringAfter('#', "")
                            if (nextFragment.isNotEmpty()) {
                                endElement = document.getElementById(nextFragment) ?: document.selectFirst("[name=$nextFragment]")
                            }
                        }
                    }
                    
                    val text = extractTextBetween(document, startElement, endElement)
                    if (text.isNotBlank()) {
                        return text
                    }
                }
            } catch (e: Exception) {
                Log.w("EbookParser", "Failed custom fragment extraction for $urlStr: ${e.message}")
            }
        }

        try {
            val cleanHref = urlStr.substringBefore('#')
            val cleanUrl = org.readium.r2.shared.util.Url(cleanHref) ?: rawUrl
            val locator = Locator(
                href = cleanUrl,
                mediaType = linkToExtract.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.BINARY
            )
            val content = publication.content(locator)
            if (content != null) {
                val chapterText = StringBuilder()
                val iterator = content.iterator()
                val targetPath = linkToExtract.href.toString()
                val cleanTargetPath = getRelativePath(targetPath)

                while (iterator.hasNext()) {
                    val element = iterator.next()
                    val elementUrl = element.locator.href.toString()
                    
                    if (elementUrl != targetPath) {
                        val cleanElementPath = getRelativePath(elementUrl)
                        if (cleanElementPath != cleanTargetPath && !cleanElementPath.endsWith("/$cleanTargetPath") && !cleanTargetPath.endsWith("/$cleanElementPath")) {
                            break
                        }
                    }
                    
                    if (element is Content.TextualElement) {
                        element.text?.let { if (it.isNotBlank()) chapterText.append(it).append("\n\n") }
                    }
                }
                val result = chapterText.toString().trim()
                if (result.isNotBlank()) {
                    return result
                }
            }
        } catch (e: Exception) {
            Log.w("EbookParser", "Failed to extract via Content service for ${linkToExtract.href}: ${e.message}")
        }
        
        // Fallback
        return extractFallback(publication, linkToExtract)
    }

    suspend fun extractText(publication: Publication, link: Link? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (link == null) {
                // If link is null, extract everything in readingOrder
                val fullText = StringBuilder()
                for (readingLink in publication.readingOrder) {
                    val text = extractSingleLinkText(publication, readingLink)
                    if (text.isNotBlank()) {
                        fullText.append(text).append("\n\n")
                    }
                }
                val result = fullText.toString().trim()
                return@withContext if (result.isNotBlank()) {
                    Result.success(result)
                } else {
                    Result.failure(Exception("No text content could be extracted."))
                }
            }

            val startPath = link.href.toString()
            val spineStartIndex = publication.readingOrder.indexOfFirst {
                pathsMatch(it.href.toString(), startPath)
            }

            if (spineStartIndex == -1) {
                // Not in spine, extract only this link
                val text = extractSingleLinkText(publication, link)
                return@withContext if (text.isNotBlank()) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("No text content could be extracted."))
                }
            }

            // Find spineEndIndex based on the next TOC item pointing to a different spine file
            var spineEndIndex = publication.readingOrder.size
            val flatToc = publication.tableOfContents.flatten()
            val currentTocIndex = flatToc.indexOfFirst {
                pathsMatch(it.href.toString(), startPath)
            }
            if (currentTocIndex != -1) {
                for (j in (currentTocIndex + 1) until flatToc.size) {
                    val nextPath = flatToc[j].href.toString()
                    if (!pathsMatch(nextPath, startPath)) {
                        val nextSpineIndex = publication.readingOrder.indexOfFirst {
                            pathsMatch(it.href.toString(), nextPath)
                        }
                        if (nextSpineIndex != -1 && nextSpineIndex > spineStartIndex) {
                            spineEndIndex = nextSpineIndex
                            break
                        }
                    }
                }
            }

            // Extract all spine items in the range [spineStartIndex, spineEndIndex)
            val combinedText = StringBuilder()
            for (i in spineStartIndex until spineEndIndex) {
                val spineLink = publication.readingOrder[i]
                val text = if (i == spineStartIndex) {
                    // For the first one, preserve the locator if fragment is present
                    val startLocator = Locator(
                        href = link.url(),
                        mediaType = link.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.BINARY
                    )
                    extractSingleLinkText(publication, spineLink, startLocator)
                } else {
                    extractSingleLinkText(publication, spineLink)
                }
                
                if (text.isNotBlank()) {
                    combinedText.append(text).append("\n\n")
                }
            }

            val result = combinedText.toString().trim()
            if (result.isNotBlank()) {
                Result.success(result)
            } else {
                Result.failure(Exception("No text content could be extracted."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun extractPages(file: File, publication: Publication, pageIndices: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        val isPdf = file.extension.lowercase() == "pdf" ||
                    publication.metadata.conformsTo.contains(Publication.Profile.PDF) ||
                    publication.readingOrder.firstOrNull()?.mediaType?.matches(org.readium.r2.shared.util.mediatype.MediaType.PDF) == true

        if (isPdf) {
            return@withContext extractPagesPdfBox(file, pageIndices)
        } else {
            return@withContext extractPagesReadium(publication, pageIndices, false)
        }
    }

    private suspend fun extractPagesPdfBox(file: File, pageIndices: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            
            val combinedText = StringBuilder()
            for (index in pageIndices.sorted()) {
                if (index !in 0 until document.numberOfPages) continue
                
                val page = document.getPage(index)
                val pageSize = page.cropBox
                
                // 1. Find the best gutter position
                val gutterX = findBestGutter(page)
                
                val extractor = PDFTextStripperByArea().apply { sortByPosition = true }
                
                if (gutterX > 0) {
                    // 2-column layout detected
                    // Deep header (22%) to ensure spanning titles like "SELECTIVE MEMORY" are caught
                    val headerHeight = pageSize.height * 0.22f

                    // Buffer: Start right column slightly to the left of gutter center 
                    // to avoid clipping the first letter of the right column.
                    val safetyBuffer = pageSize.width * 0.02f 
                    
                    extractor.addRegion("header", RectF(0f, 0f, pageSize.width, headerHeight))
                    extractor.addRegion("left", RectF(0f, headerHeight, gutterX, pageSize.height))
                    extractor.addRegion("right", RectF(gutterX - safetyBuffer,
                        headerHeight, pageSize.width, pageSize.height))
                    
                    extractor.extractRegions(page)
                    
                    val h = extractor.getTextForRegion("header").trim()
                    val l = extractor.getTextForRegion("left").trim()
                    val r = extractor.getTextForRegion("right").trim()
                    
                    if (h.isNotEmpty()) combinedText.append(h).append("\n\n")
                    if (l.isNotEmpty()) combinedText.append(l).append("\n\n")
                    if (r.isNotEmpty()) combinedText.append(r).append("\n\n")
                } else {
                    // Standard 1-column extraction
                    val marginY = pageSize.height * 0.08f
                    val mainRect = RectF(0f, marginY, pageSize.width, pageSize.height - marginY)
                    
                    extractor.addRegion("main", mainRect)
                    extractor.extractRegions(page)
                    
                    val pageText = extractor.getTextForRegion("main")
                    if (pageText.isNotBlank()) {
                        combinedText.append(pageText).append("\n\n")
                    } else {
                        val fullStripper = PDFTextStripper().apply {
                            sortByPosition = true
                            startPage = index + 1
                            endPage = index + 1
                        }
                        combinedText.append(fullStripper.getText(document)).append("\n\n")
                    }
                }
            }
            
            val result = combinedText.toString().trim()
            if (result.isBlank()) {
                return@withContext Result.failure(Exception("No text found on selected pages via PDFBox."))
            }
            Result.success(cleanPdfText(result))
        } catch (e: Exception) {
            Log.e("EbookParser", "PDFBox extraction failed", e)
            Result.failure(e)
        } finally {
            try { document?.close() } catch (_: Exception) {}
        }
    }

    private fun findBestGutter(page: com.tom_roush.pdfbox.pdmodel.PDPage): Float {
        val pageSize = page.cropBox
        val gutterWidth = pageSize.width * 0.015f 
        val testStripper = PDFTextStripperByArea()
        
        // Scan wider range (30% to 70%) to find gutters in asymmetrical layouts
        val scanPoints = listOf(0.30f, 0.35f, 0.40f, 0.45f, 0.50f, 0.55f, 0.60f, 0.65f, 0.70f)
        
        for (p in scanPoints) {
            val x = pageSize.width * p
            val region = RectF(x - (gutterWidth/2), pageSize.height * 0.25f, x + (gutterWidth/2), pageSize.height * 0.75f)
            testStripper.addRegion("gutter_$p", region)
        }
        
        testStripper.extractRegions(page)
        
        var bestX = -1f
        var minLength = Int.MAX_VALUE
        
        for (p in scanPoints) {
            val text = testStripper.getTextForRegion("gutter_$p").trim()
            if (text.length < minLength) {
                minLength = text.length
                bestX = pageSize.width * p
            }
        }
        
        // Increased threshold to 20 to handle spanning titles/decorations
        return if (minLength < 20) bestX else -1f
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
        cleaned = cleaned.replace(Regex("([^.!?\\-])\\s*\\r?\\n\\s*(\\p{L})"), "$1 $2")

        // 4. Remove lone page numbers on their own lines
        cleaned = cleaned.replace(Regex("(?m)^\\s*\\d+\\s*$"), "")

        // 5. Fix multiple spaces and excessive newlines
        return cleaned.replace(Regex(" {2,}"), " ")
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
            val matchingLink = publication.readingOrder.firstOrNull {
                pathsMatch(it.href.toString(), link.href.toString())
            } ?: link
            val bytes = publication.get(matchingLink)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
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
        text = text.replace(Regex("<[^>]*>"), "")
        
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
