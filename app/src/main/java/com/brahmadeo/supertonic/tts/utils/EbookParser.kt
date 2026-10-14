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
import java.util.regex.Pattern

class EbookParser(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val pdfFactory = PdfiumDocumentFactory(context)
    private val publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory)
    private val publicationOpener = PublicationOpener(publicationParser)
    private var noteResolverInstance: NoteResolver? = null

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



    companion object {
        const val FN_MARK = "\u2060FN%d\u2060"
        private val FN_MARK_REGEX = Regex("\u2060FN(\\d+)\u2060")
        private val SENTENCE_SPLIT_PATTERN = Pattern.compile("([.!?]['\"”)}\\]]?(?:\\u2060FN\\d+\\u2060)*)\\s+")

        fun getRelativePath(urlOrPath: String): String {
            var path = if (urlOrPath.contains("://")) {
                urlOrPath.substringAfter("://").substringAfter('/', "")
            } else {
                urlOrPath
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

        fun pathsMatch(path1: String, path2: String): Boolean {
            val p1 = getRelativePath(path1)
            val p2 = getRelativePath(path2)
            return p1 == p2 || p1.endsWith("/$p2") || p2.endsWith("/$p1")
        }

        fun resolveRelativePath(basePath: String, relativeHref: String): String {
            val cleanRel = relativeHref.substringBefore('#').substringBefore('?')
            if (cleanRel.isEmpty()) return basePath.substringBefore('#')
            
            val baseDir = if (basePath.contains('/')) basePath.substringBeforeLast('/') else ""
            val combined = if (baseDir.isNotEmpty()) "$baseDir/$cleanRel" else cleanRel
            
            val parts = combined.split('/')
            val stack = mutableListOf<String>()
            for (part in parts) {
                when (part) {
                    "", "." -> {}
                    ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                    else -> stack.add(part)
                }
            }
            return stack.joinToString("/")
        }

        fun isCitationOnly(text: String): Boolean {
            var stripped = text
            // 1. Remove URLs
            stripped = stripped.replace(Regex("https?://\\S+|www\\.\\S+|doi:\\S+|doi\\.org/\\S+"), " ")
            // 2. Remove DOIs
            stripped = stripped.replace(Regex("\\b10\\.\\d{4,9}/[-._;()/:A-Za-z0-9]+\\b"), " ")
            // 3. Remove volume/issue/page citations (e.g. "pp. 12-15", "p. 45", "52: 1199-226", "vol. 2", "no. 1")
            stripped = stripped.replace(Regex("(?i)\\b(?:vol|vols|no|nos|pp|p|pages?|ch|chap|chapter)\\.?\\s*\\d+[-–—\\d]*\\b"), " ")
            stripped = stripped.replace(Regex("\\b\\d+:\\s*\\d+[-–—\\d]*\\b"), " ")
            // 4. Remove common citation terms
            stripped = stripped.replace(Regex("(?i)\\b(?:ibid|op\\.?\\s*cit|loc\\.?\\s*cit|cf|et\\s+al|eds?|see\\s+also|see|in|trans|repr|pub|press|publisher)\\.?\\b"), " ")
            // 5. Remove 4-digit years (e.g. 1998, 2017)
            stripped = stripped.replace(Regex("\\b(?:1[5-9]\\d{2}|20\\d{2})[a-z]?\\b"), " ")
            // 6. Remove numbers, punctuation, brackets
            stripped = stripped.replace(Regex("[0-9\\p{Punct}\\s\\u2018\\u2019\\u201C\\u201D—–-]+"), " ").trim()
            
            val remainingWords = stripped.split(Regex("\\s+")).filter { it.length >= 3 && it.any { c -> c.isLetter() } }
            return remainingWords.size <= 3
        }

        fun splitParagraphIntoSentences(text: String): List<String> {
            val matcher = SENTENCE_SPLIT_PATTERN.matcher(text)
            val sentences = mutableListOf<String>()
            var last = 0
            while (matcher.find()) {
                val s = text.substring(last, matcher.end()).trim()
                if (s.isNotEmpty()) {
                    sentences.add(s)
                }
                last = matcher.end()
            }
            if (last < text.length) {
                val s = text.substring(last).trim()
                if (s.isNotEmpty()) {
                    sentences.add(s)
                }
            }
            return if (sentences.isEmpty()) listOf(text) else sentences
        }

        private val sharedConsumedTargets =
            java.util.Collections.synchronizedMap(
                java.util.WeakHashMap<Publication, MutableMap<String, MutableSet<String>>>()
            )

        fun cleanFootnoteElement(el: Element): String? {
            val copy = el.clone()

            // Remove backlinks
            copy.select("a[href*=#], a[epub\\:type~=backlink], a[role=doc-backlink], [class*=backlink], [class*=return]").forEach { a ->
                val aText = a.text().trim()
                if (aText.length <= 4 || aText.matches(Regex("[↩↑^]|.*(?:back|return).*|^\\[?\\d+\\]?$|back to note.*", RegexOption.IGNORE_CASE))) {
                    a.remove()
                }
            }

            // Remove footnote labels / sups
            copy.select("sup, .fn-number, .footnote-number, .label, .fnanchor, .noteref").remove()

            var noteText = copy.text().trim()
            noteText = noteText.replaceFirst(Regex("^(?:(?:\\[?\\d+\\]?|\\(\\d+\\)|[\\p{L}\\d]+)[.:)]|Footnote:?)\\s*"), "").trim()

            return noteText.ifBlank { null }
        }

        fun weaveFootnotesIntoParagraphs(
            rawText: String,
            notesMap: Map<Int, String>,
            mode: FootnoteMode
        ): String {
            val paragraphs = rawText.split("\n")
                .map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.isNotBlank() }

            val outBlocks = mutableListOf<String>()

            for (para in paragraphs) {
                if (!para.contains("\u2060FN")) {
                    outBlocks.add(para)
                    continue
                }

                val sentences = splitParagraphIntoSentences(para)
                for (sentence in sentences) {
                    val matches = FN_MARK_REGEX.findAll(sentence).toList()
                    if (matches.isEmpty()) {
                        outBlocks.add(sentence)
                    } else {
                        val ids = matches.map { it.groupValues[1].toInt() }
                        val cleanSentence = FN_MARK_REGEX.replace(sentence, "")
                            .replace(Regex("\\s+([.,;:!?])"), "$1")
                            .trim()

                        if (cleanSentence.isNotBlank()) {
                            outBlocks.add(cleanSentence)
                        }

                        if (mode != FootnoteMode.OFF) {
                            for (id in ids) {
                                val note = notesMap[id]
                                if (!note.isNullOrBlank()) {
                                    outBlocks.add("Footnote: $note")
                                }
                            }
                        }
                    }
                }
            }

            return outBlocks.joinToString("\n\n").trim()
        }
    }

    enum class FootnoteMode {
        OFF,
        FILTER_CITATIONS,
        ALL;

        companion object {
            fun fromString(value: String?): FootnoteMode {
                return when (value?.lowercase()) {
                    "all" -> ALL
                    "off" -> OFF
                    else -> FILTER_CITATIONS
                }
            }
        }
    }

    class NoteResolver(
        val publication: Publication,
        val footnoteMode: FootnoteMode
    ) {
        private val docCache = HashMap<String, Document?>()
        val consumedTargetsByFile = HashMap<String, MutableSet<String>>()
        val totalFootnotesByFile = HashMap<String, Int>()

        suspend fun loadDocument(path: String): Document? {
            val cleanPath = getRelativePath(path)
            docCache[cleanPath]?.let { return it }

            val matchingLink = (publication.readingOrder + publication.resources).firstOrNull {
                pathsMatch(it.href.toString(), cleanPath)
            } ?: return null
            val bytes = publication.get(matchingLink)?.use { it.read().getOrElse { ByteArray(0) } } ?: return null
            if (bytes.isEmpty()) return null
            val doc = Jsoup.parse(bytes.decodeToString())
            
            val count = doc.select("li[id], aside[id], div[class*=footnote], p[class*=footnote], [epub\\:type~=footnote], [role=doc-footnote]").size
            totalFootnotesByFile[cleanPath] = count
            docCache[cleanPath] = doc
            return doc
        }

        suspend fun resolve(chapterPath: String, href: String): String? {
            if (href.isBlank() || !href.contains('#')) return null
            val filePart = href.substringBefore('#').substringBefore('?')
            val targetId = href.substringAfter('#', "")
            if (targetId.isEmpty()) return null

            val resolvedFile = if (filePart.isEmpty()) {
                chapterPath.substringBefore('#')
            } else {
                resolveRelativePath(chapterPath, filePart)
            }

            val doc = loadDocument(resolvedFile) ?: return null
            var el = doc.getElementById(targetId) ?: doc.selectFirst("[name=$targetId]") ?: return null

            if (el.text().isBlank() || el.tagName() in listOf("a", "span", "sup", "b", "i", "em", "strong")) {
                el = el.closest("p, li, div, aside, dd, dt, blockquote, tr, td") ?: el
            }

            val cleanFile = getRelativePath(resolvedFile)
            consumedTargetsByFile.getOrPut(cleanFile) { mutableSetOf() }.add(targetId)
            synchronized(sharedConsumedTargets) {
                val targetsByFile = sharedConsumedTargets.getOrPut(publication) { HashMap() }
                targetsByFile.getOrPut(cleanFile) { mutableSetOf() }.add(targetId)
            }

            val noteText = cleanFootnoteElement(el) ?: return null

            if (footnoteMode == FootnoteMode.FILTER_CITATIONS && isCitationOnly(noteText)) {
                return null
            }

            return noteText
        }
    }

    private fun getOrCreateNoteResolver(publication: Publication, mode: FootnoteMode): NoteResolver {
        val existing = noteResolverInstance
        if (existing != null && existing.publication === publication && existing.footnoteMode == mode) {
            return existing
        }
        return NoteResolver(publication, mode).also { noteResolverInstance = it }
    }

    suspend fun shouldSkipNotesChapter(publication: Publication, link: Link): Boolean {
        val prefs = context.getSharedPreferences("SupertonicPrefs", Context.MODE_PRIVATE)
        val mode = FootnoteMode.fromString(prefs.getString("pref_footnote_mode", "filter_citations"))
        if (mode == FootnoteMode.OFF) return false

        val linkHref = link.href.toString()
        val cleanPath = getRelativePath(linkHref)

        val hasNotesRel = link.rels.any { it.contains("notes", ignoreCase = true) }
        val matchingResource = (publication.readingOrder + publication.resources).firstOrNull {
            pathsMatch(it.href.toString(), cleanPath)
        } ?: link
        
        val bytes = publication.get(matchingResource)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
        if (bytes.isEmpty()) return false
        val doc = Jsoup.parse(bytes.decodeToString())
        
        val hasEpubTypeNotes = doc.select("[epub\\:type~=endnotes], [epub\\:type~=footnotes], [epub\\:type~=rearnotes], [role=doc-endnotes]").isNotEmpty()
        val title = doc.title().ifBlank { link.title ?: "" }.lowercase()
        val isLikelyNotesTitle = title.contains("endnote") || title.contains("footnote") || title.contains("chapter note") || title == "notes"

        if (!hasNotesRel && !hasEpubTypeNotes && !isLikelyNotesTitle) {
            return false
        }

        // Must not contain a bibliography or works cited section
        val hasBibliography = doc.select("h1, h2, h3, h4, h5, [class*=bib], [id*=bib]").any {
            val text = it.text().lowercase()
            text.contains("bibliography") || text.contains("works cited") || text.contains("references")
        }
        if (hasBibliography) {
            return false
        }

        val totalNotes = doc.select("li[id], aside[id], div[class*=footnote], p[class*=footnote], [epub\\:type~=footnote], [role=doc-footnote]").size
        val consumedFromInstance = noteResolverInstance?.consumedTargetsByFile?.get(cleanPath)?.size ?: 0
        val consumedFromShared = synchronized(sharedConsumedTargets) {
            sharedConsumedTargets[publication]?.get(cleanPath)?.size ?: 0
        }
        val consumed = maxOf(consumedFromInstance, consumedFromShared)

        return totalNotes >= 3 && consumed >= (totalNotes * 0.5)
    }

    private fun extractRawTextBetween(
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

    suspend fun extractHtmlWithFootnotes(
        document: Document,
        chapterPath: String,
        resolver: NoteResolver,
        mode: FootnoteMode,
        startElement: Element?,
        endElement: Element?
    ): String {
        val noterefSelector = listOf(
            "a[epub\\:type~=noteref]",
            "a[role=doc-noteref]",
            "a[class*=noteref]",
            "a[class*=footnote]",
            "a[class*=fnanchor]",
            "a[class*=fn-ref]",
            "a[class*=pginternal]",
            "sup > a[href*=#]",
            "a[href*=#] > sup"
        ).joinToString(", ")
        
        val candidateElements = document.select(noterefSelector).toMutableList()
        
        document.select("a[href*=#]").forEach { a ->
            val href = a.attr("href")
            val text = a.text().trim()
            val hrefLower = href.lowercase()
            val isFootnoteHref = hrefLower.contains("fn") || hrefLower.contains("foot") || 
                                 hrefLower.contains("note") || hrefLower.contains("endnote")
            val isFootnoteText = text.matches(Regex("^\\[?\\d+\\]?$|^\\d+$|^[ivxlcdm]+$|^[*†‡§]$"))
            if ((isFootnoteHref || isFootnoteText) && !candidateElements.contains(a)) {
                candidateElements.add(a)
            }
        }

        val noterefs = mutableListOf<Pair<Element, String>>()
        val processedMarkers = mutableSetOf<Element>()

        for (el in candidateElements) {
            val a = if (el.tagName() == "a") el else el.selectFirst("a") ?: continue
            val href = a.attr("href")
            if (href.isBlank() || !href.contains('#')) continue

            val markerEl = if (a.parent()?.tagName() == "sup") a.parent()!! else a
            if (processedMarkers.add(markerEl)) {
                noterefs.add(Pair(markerEl, href))
            }
        }

        val notesMap = mutableMapOf<Int, String>()
        val localTargetsToRemove = mutableListOf<Element>()

        for ((index, pair) in noterefs.withIndex()) {
            val (markerEl, href) = pair
            if (mode != FootnoteMode.OFF) {
                val noteText = resolver.resolve(chapterPath, href)
                if (noteText != null) {
                    notesMap[index] = noteText
                }
            }

            // Collect local note target containers to remove from DOM
            val (targetFile, targetId) = resolveHref(chapterPath, href)
            if (pathsMatch(targetFile, chapterPath) && targetId.isNotEmpty()) {
                val targetEl = document.getElementById(targetId) ?: document.selectFirst("[name=$targetId]")
                if (targetEl != null) {
                    val noteContainer = targetEl.closest("aside, li, dd, [epub\\:type~=footnote], [role=doc-footnote]") ?: targetEl
                    localTargetsToRemove.add(noteContainer)
                }
            }
            markerEl.replaceWith(TextNode(FN_MARK.format(index)))
        }

        localTargetsToRemove.forEach { it.remove() }

        val rawText = extractRawTextBetween(document, startElement, endElement)
        return weaveFootnotesIntoParagraphs(rawText, notesMap, mode)
    }

    private fun resolveHref(chapterPath: String, href: String): Pair<String, String> {
        val filePart = href.substringBefore('#').substringBefore('?')
        val idPart = href.substringAfter('#', "")
        val resolvedFile = if (filePart.isEmpty()) {
            chapterPath.substringBefore('#')
        } else {
            resolveRelativePath(chapterPath, filePart)
        }
        return Pair(resolvedFile, idPart)
    }

    suspend fun extractWebpageText(url: String): String = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .timeout(10000)
            .get()
            
        doc.select("script, style, header, footer, nav, aside, iframe, noscript, .comments, .sidebar, .menu, .nav, .footer, .header, #comments, #sidebar, #footer, #header, #nav").remove()
        
        val mainElement = doc.select("article").firstOrNull()
            ?: doc.select("main").firstOrNull()
            ?: doc.select("[id*=article], [id*=content], [id*=story], [id*=post-body], [class*=article], [class*=content], [class*=story]").firstOrNull()
            ?: doc.body()
            
        extractRawTextBetween(doc, mainElement, null)
    }

    private suspend fun extractSingleLinkText(
        publication: Publication,
        linkToExtract: Link,
        startLocator: Locator? = null,
        footnoteMode: FootnoteMode = FootnoteMode.FILTER_CITATIONS
    ): String {
        val rawUrl = startLocator?.href ?: linkToExtract.url()
        val urlStr = rawUrl.toString()
        val fragment = urlStr.substringAfter('#', "")
        
        val matchingLink = publication.readingOrder.firstOrNull {
            pathsMatch(it.href.toString(), urlStr)
        } ?: linkToExtract
        
        val bytes = publication.get(matchingLink)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
        val htmlText = bytes.decodeToString()
        
        if (htmlText.isNotBlank()) {
            try {
                val document = Jsoup.parse(htmlText)
                val resolver = getOrCreateNoteResolver(publication, footnoteMode)
                
                val startElement = if (fragment.isNotEmpty()) {
                    document.getElementById(fragment) ?: document.selectFirst("[name=$fragment]")
                } else null
                
                var endElement: Element? = null
                if (fragment.isNotEmpty()) {
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
                }
                
                val text = extractHtmlWithFootnotes(
                    document = document,
                    chapterPath = matchingLink.href.toString(),
                    resolver = resolver,
                    mode = footnoteMode,
                    startElement = startElement,
                    endElement = endElement
                )
                if (text.isNotBlank()) {
                    return text
                }
            } catch (e: Exception) {
                Log.w("EbookParser", "Failed HTML footnote extraction for $urlStr: ${e.message}")
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

    suspend fun extractText(
        publication: Publication,
        link: Link? = null,
        footnoteMode: FootnoteMode? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val effectiveMode = footnoteMode ?: run {
            val prefs = context.getSharedPreferences("SupertonicPrefs", Context.MODE_PRIVATE)
            FootnoteMode.fromString(prefs.getString("pref_footnote_mode", "filter_citations"))
        }

        try {
            if (link == null) {
                val fullText = StringBuilder()
                for (readingLink in publication.readingOrder) {
                    val text = extractSingleLinkText(publication, readingLink, footnoteMode = effectiveMode)
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
                val text = extractSingleLinkText(publication, link, footnoteMode = effectiveMode)
                return@withContext if (text.isNotBlank()) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("No text content could be extracted."))
                }
            }

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

            val combinedText = StringBuilder()
            for (i in spineStartIndex until spineEndIndex) {
                val spineLink = publication.readingOrder[i]
                val text = if (i == spineStartIndex) {
                    val startLocator = Locator(
                        href = link.url(),
                        mediaType = link.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.BINARY
                    )
                    extractSingleLinkText(publication, spineLink, startLocator, effectiveMode)
                } else {
                    extractSingleLinkText(publication, spineLink, footnoteMode = effectiveMode)
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
        cleaned = cleaned.replace(Regex("([^.!?\\-])\\h*\\r?\\n\\h*(\\p{L})"), "$1 $2")

        // 4. Remove lone page numbers on their own lines
        cleaned = cleaned.replace(Regex("(?m)^\\h*\\d+\\h*$"), "")

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
