package com.brahmadeo.supertonic.tts
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme
import com.brahmadeo.supertonic.tts.utils.EbookManager
import com.brahmadeo.supertonic.tts.utils.EbookParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.mediatype.MediaType
import java.io.File
import kotlin.math.max

class EbookOutlineActivity : ComponentActivity() {

    private lateinit var ebookParser: EbookParser

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ebookParser = EbookParser(this)

        val ebookPath = intent.getStringExtra(EXTRA_URI)
        if (ebookPath == null) {
            finish()
            return
        }

        val ebookFile = File(ebookPath)

        setContent {
            SupertonicTheme {
                OutlineScreen(
                    ebookFile = ebookFile,
                    onTextExtracted = { text ->
                        val resultIntent = Intent()
                        resultIntent.putExtra(EXTRA_TEXT, text)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun OutlineScreen(
        ebookFile: File,
        onTextExtracted: (String) -> Unit,
        onBack: () -> Unit
    ) {
        var publication by remember { mutableStateOf<Publication?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var isExtracting by remember { mutableStateOf(false) }
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        
        var isPdf by remember { mutableStateOf(ebookFile.extension.lowercase() == "pdf") }
        var lastReadChapterHref by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(ebookFile) {
            lastReadChapterHref = EbookManager.getLastReadChapter(this@EbookOutlineActivity, ebookFile.absolutePath)
            val result = ebookParser.openPublication(ebookFile)
            publication = result.getOrNull()
            isLoading = false
            if (publication == null) {
                Toast.makeText(this@EbookOutlineActivity, "Failed to open ebook", Toast.LENGTH_SHORT).show()
                onBack()
            } else {
                // Better detection using Readium metadata
                val conformsToPdf = publication?.metadata?.conformsTo?.contains(Publication.Profile.PDF) == true
                val isPdfMediaType = publication?.readingOrder?.firstOrNull()?.mediaType?.matches(MediaType.PDF) == true
                
                if (conformsToPdf || isPdfMediaType) {
                    isPdf = true
                }

                val title = publication?.metadata?.title ?: ebookFile.nameWithoutExtension
                EbookManager.addBook(this@EbookOutlineActivity, title, ebookFile.absolutePath)
                if (isPdf) selectedTabIndex = 1 // Default to Pages for PDF
            }
        }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(publication?.metadata?.title ?: "Ebook Details") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    if (!isLoading && publication != null) {
                        SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Chapters") }
                            )
                            if (isPdf) {
                                Tab(
                                    selected = selectedTabIndex == 1,
                                    onClick = { selectedTabIndex = 1 },
                                    text = { Text("Pages") }
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (publication != null) {
                    val contentAlpha = if (isExtracting) 0.4f else 1.0f
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = contentAlpha)) {
                        when (selectedTabIndex) {
                             0 -> ChapterTab(
                                ebookFile = ebookFile,
                                publication = publication!!,
                                onTextExtracted = onTextExtracted,
                                lastReadChapterHref = lastReadChapterHref,
                                onLastReadChapterUpdated = { href ->
                                    lastReadChapterHref = href
                                    EbookManager.setLastReadChapter(this@EbookOutlineActivity, ebookFile.absolutePath, href)
                                },
                                setExtracting = { isExtracting = it }
                            )
                            1 -> PagesTab(ebookFile, publication!!, isPdf, onTextExtracted) { isExtracting = it }
                        }
                    }
                }

                if (isExtracting) {
                    Surface(
                        modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Extracting text...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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

    @Composable
    fun ChapterTab(
        ebookFile: File,
        publication: Publication,
        onTextExtracted: (String) -> Unit,
        lastReadChapterHref: String?,
        onLastReadChapterUpdated: (String) -> Unit,
        setExtracting: (Boolean) -> Unit
    ) {
        val toc = publication.tableOfContents
        val links = toc.ifEmpty { publication.readingOrder }
        
        val wordCounts = remember { mutableStateMapOf<String, Int>() }
        var currentlyCalculatingHref by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        
        LaunchedEffect(publication) {
            val flatLinks = links.flatten()
            
            // Load cached word counts first
            val cachedCounts = withContext(Dispatchers.IO) {
                EbookManager.getWordCounts(this@EbookOutlineActivity, ebookFile.absolutePath)
            }
            wordCounts.putAll(cachedCounts)
            
            // Extract missing word counts gradually/sequentially
            for (link in flatLinks) {
                if (!isActive) break
                val hrefStr = link.href.toString()
                if (wordCounts.containsKey(hrefStr)) {
                    continue
                }
                
                withContext(Dispatchers.Main) {
                    currentlyCalculatingHref = hrefStr
                }
                
                withContext(Dispatchers.IO) {
                    try {
                        val result = ebookParser.extractText(publication, link)
                        val words = result.getOrNull()?.let { text ->
                            text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        } ?: 0
                        
                        withContext(Dispatchers.Main) {
                            wordCounts[hrefStr] = words
                        }
                        EbookManager.saveWordCount(this@EbookOutlineActivity, ebookFile.absolutePath, hrefStr, words)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            wordCounts[hrefStr] = 0
                        }
                        EbookManager.saveWordCount(this@EbookOutlineActivity, ebookFile.absolutePath, hrefStr, 0)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                currentlyCalculatingHref = null
            }
        }

        val flatUiList = remember(links) {
            val result = mutableListOf<Pair<Link, Int>>()
            fun traverse(list: List<Link>, level: Int) {
                for (link in list) {
                    result.add(Pair(link, level))
                    traverse(link.children, level + 1)
                }
            }
            traverse(links, 0)
            result
        }

        val lastReadIndex = remember(flatUiList, lastReadChapterHref) {
            flatUiList.indexOfFirst { it.first.href.toString() == lastReadChapterHref }
        }

        val listState = rememberLazyListState()

        LaunchedEffect(lastReadIndex) {
            if (lastReadIndex != -1) {
                listState.scrollToItem(lastReadIndex)
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(flatUiList) { index, pair ->
                val link = pair.first
                val level = pair.second
                val isDimmed = lastReadIndex != -1 && index <= lastReadIndex
                
                ChapterItem(
                    link = link,
                    publication = publication,
                    onTextExtracted = onTextExtracted,
                    setExtracting = setExtracting,
                    wordCount = wordCounts[link.href.toString()],
                    isCalculating = (currentlyCalculatingHref == link.href.toString()),
                    isDimmed = isDimmed,
                    level = level,
                    onSuccessExtraction = {
                        onLastReadChapterUpdated(link.href.toString())
                    }
                )
            }
        }
    }

    @Composable
    fun ChapterItem(
        link: Link,
        publication: Publication,
        onTextExtracted: (String) -> Unit,
        setExtracting: (Boolean) -> Unit,
        wordCount: Int?,
        isCalculating: Boolean,
        isDimmed: Boolean,
        level: Int = 0,
        onSuccessExtraction: () -> Unit
    ) {
        val focusManager = LocalFocusManager.current
        ListItem(
            headlineContent = { 
                Text(
                    text = link.title ?: link.href.toString(), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = (level * 16).dp)
                ) 
            },
            trailingContent = {
                if (wordCount != null) {
                    Text(
                        text = String.format(java.util.Locale.US, "%,d words", wordCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isCalculating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            modifier = Modifier
                .graphicsLayer(alpha = if (isDimmed) 0.5f else 1.0f)
                .clickable {
                    focusManager.clearFocus()
                    setExtracting(true)
                    lifecycleScope.launch {
                        val result = ebookParser.extractText(publication, link)
                        setExtracting(false)
                        result.onSuccess {
                            onSuccessExtraction()
                            onTextExtracted(it)
                        }
                        .onFailure { Toast.makeText(this@EbookOutlineActivity, it.message, Toast.LENGTH_SHORT).show() }
                    }
                }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun PagesTab(
        file: File,
        publication: Publication,
        isPdf: Boolean,
        onTextExtracted: (String) -> Unit,
        setExtracting: (Boolean) -> Unit
    ) {
        var pageCount by remember { mutableIntStateOf(0) }
        val selectedPages = remember { mutableStateListOf<Int>() }
        var showPagePreviewDialog by remember { mutableStateOf(false) }
        var previewPageIndex by remember { mutableIntStateOf(0) }
        var multiSelectMode by remember { mutableStateOf(false) }

        LaunchedEffect(publication) {
            pageCount = publication.positions().size
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, max(if (selectedPages.isNotEmpty()) 80 else 16, 16).dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pageCount) { index ->
                    PageThumbnailItem(
                        file = file,
                        pageIndex = index,
                        isPdf = isPdf,
                        isSelected = selectedPages.contains(index),
                        multiSelectMode = multiSelectMode,
                        onClick = { 
                            if (multiSelectMode) {
                                if (selectedPages.contains(index)) selectedPages.remove(index) else selectedPages.add(index)
                            } else {
                                previewPageIndex = index
                                showPagePreviewDialog = true
                            }
                        },
                        onLongClick = {
                            multiSelectMode = !multiSelectMode
                            if (!multiSelectMode) selectedPages.clear()
                            if (multiSelectMode && !selectedPages.contains(index)) selectedPages.add(index)
                        }
                    )
                }
            }

            if (selectedPages.isNotEmpty()) {
                Button(
                    onClick = {
                        setExtracting(true)
                        multiSelectMode = false
                        lifecycleScope.launch {
                            val result = ebookParser.extractPages(file, publication, selectedPages.toList())
                            setExtracting(false)
                            selectedPages.clear()
                            result.onSuccess { onTextExtracted(it) }
                                .onFailure { Toast.makeText(this@EbookOutlineActivity, it.message, Toast.LENGTH_SHORT).show() }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(0.8f)
                ) {
                    Text("Load ${selectedPages.size} Page(s)")
                }
            }
        }
        
        if (showPagePreviewDialog) {
            PagePreviewDialog(
                file = file,
                pageIndex = previewPageIndex,
                isPdf = isPdf,
                onDismiss = { showPagePreviewDialog = false },
                onLoadPage = { pageIndexToLoad ->
                    showPagePreviewDialog = false
                    setExtracting(true)
                    lifecycleScope.launch {
                        val result = ebookParser.extractPages(file, publication, listOf(pageIndexToLoad))
                        setExtracting(false)
                        result.onSuccess { onTextExtracted(it) }
                            .onFailure { Toast.makeText(this@EbookOutlineActivity, it.message, Toast.LENGTH_SHORT).show() }
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PagePreviewDialog(
        file: File,
        pageIndex: Int,
        isPdf: Boolean,
        onDismiss: () -> Unit,
        onLoadPage: (Int) -> Unit
    ) {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        val maxHeight = with(density) { windowInfo.containerSize.height.toDp() }

        var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
        var isLoadingPreview by remember { mutableStateOf(true) }

        LaunchedEffect(file, pageIndex) {
            if (!isPdf) {
                isLoadingPreview = false
                return@LaunchedEffect
            }
            isLoadingPreview = true
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val page = renderer.openPage(pageIndex)
                    val bitmap = createBitmap(page.width, page.height)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    thumbnail = bitmap
                    page.close()
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    thumbnail = null
                } finally {
                    isLoadingPreview = false
                }
            }
        }
        
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Page ${pageIndex + 1} Preview", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isLoadingPreview) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    } else if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail!!.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.6f),
                            contentScale = ContentScale.FillWidth
                        )
                    } else if (!isPdf) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Preview not available for this format", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("Error loading preview", color = MaterialTheme.colorScheme.error)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = { onLoadPage(pageIndex) }) { Text("Load Page") }
                    }
                }
            }
        }
    }


    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun PageThumbnailItem(
        file: File,
        pageIndex: Int,
        isPdf: Boolean,
        isSelected: Boolean,
        multiSelectMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

        if (isPdf) {
            LaunchedEffect(file, pageIndex) {
                withContext(Dispatchers.IO) {
                    try {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        val page = renderer.openPage(pageIndex)
                        val bitmap = createBitmap(300, 400)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumbnail = bitmap
                        page.close()
                        renderer.close()
                        pfd.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (isSelected || multiSelectMode) 3.dp else 1.dp,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            multiSelectMode -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                if (isPdf) {
                    thumbnail?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).align(Alignment.Center),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
                
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                } else if (multiSelectMode) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                }
            }
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    companion object {
        const val EXTRA_URI = "ebook_uri"
        const val EXTRA_TEXT = "extracted_text"
    }
}
