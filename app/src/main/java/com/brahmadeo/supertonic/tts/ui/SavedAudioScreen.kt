package com.brahmadeo.supertonic.tts.ui

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.ui.res.stringResource
import com.brahmadeo.supertonic.tts.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAudioScreen(
    onBackClick: () -> Unit,
    onPlayAudio: (File) -> Unit,
    onShareAudio: (List<File>) -> Unit
) {
    var files by remember { mutableStateOf(emptyList<File>()) }
    var filesToDelete by remember { mutableStateOf<List<File>>(emptyList()) }

    val context = LocalContext.current

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }
    val coroutineScope = rememberCoroutineScope()

    suspend fun loadFiles() {
        withContext(Dispatchers.IO) {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
            val loadedFiles = if (appDir.exists()) {
                appDir.listFiles { _, name -> name.endsWith(".wav") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                selectedFiles.clear()
                isSelectionMode = false
                files = loadedFiles
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFiles()
    }

    if (filesToDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { filesToDelete = emptyList() },
            title = { Text(stringResource(R.string.delete_audio_title)) },
            text = {
                Text(
                    if (filesToDelete.size == 1) {
                        stringResource(R.string.delete_audio_confirm_single, filesToDelete[0].name)
                    } else {
                        stringResource(R.string.delete_audio_confirm_multiple, filesToDelete.size)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = filesToDelete
                        coroutineScope.launch(Dispatchers.IO) {
                            targets.forEach { it.delete() }
                            loadFiles()
                            withContext(Dispatchers.Main) {
                                filesToDelete = emptyList()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { filesToDelete = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                selectedFiles.clear()
                                isSelectionMode = false
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedFiles.size == files.size) {
                                    selectedFiles.clear()
                                } else {
                                    selectedFiles.clear()
                                    selectedFiles.addAll(files)
                                }
                            }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            enabled = selectedFiles.isNotEmpty(),
                            onClick = {
                                onShareAudio(selectedFiles.toList())
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected")
                        }
                        IconButton(
                            enabled = selectedFiles.isNotEmpty(),
                            onClick = {
                                filesToDelete = selectedFiles.toList()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.saved_audio_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (files.isEmpty()) {
                EmptySavedAudioState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(files, key = { it.absolutePath }) { file ->
                        val isSelected = selectedFiles.contains(file)
                        SwipeToDeleteAudioContainer(
                            enabled = !isSelectionMode,
                            onDelete = { filesToDelete = listOf(file) }
                        ) {
                            SavedAudioItem(
                                file = file,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onPlay = { onPlayAudio(file) },
                                onShare = { onShareAudio(listOf(file)) },
                                onItemClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) {
                                            selectedFiles.remove(file)
                                            if (selectedFiles.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            selectedFiles.add(file)
                                        }
                                    } else {
                                        onPlayAudio(file)
                                    }
                                },
                                onItemLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedFiles.add(file)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteAudioContainer(
    enabled: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedAudioItem(
    file: File,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    val dateString = remember(file.lastModified()) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onItemClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Icon(
                    Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySavedAudioState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AudioFile,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No saved audio",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Synthesized audio files you save will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
