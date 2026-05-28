package com.brahmadeo.supertonic.tts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahmadeo.supertonic.tts.ui.components.IndeterminateWavyProgressIndicator
import com.brahmadeo.supertonic.tts.ui.components.WavyCircularProgressIndicator
import com.brahmadeo.supertonic.tts.ui.components.WavyLinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    sentences: List<String>,
    currentIndex: Int,
    isPlaying: Boolean,
    isServiceActive: Boolean,
    isExporting: Boolean,
    exportCurrent: Int,
    exportTotal: Int,
    onBackClick: () -> Unit,
    onItemClick: (Int) -> Unit,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onExportClick: () -> Unit,
    onCancelExportClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in sentences.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleLarge) },
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = 16.dp,
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sentences) { index, sentence ->
                    SentenceItem(
                        text = sentence,
                        isActive = index == currentIndex,
                        onClick = { onItemClick(index) }
                    )
                }
            }

            // Enhanced Player Card
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    if (isServiceActive || isPlaying) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${currentIndex + 1} / ${sentences.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            WavyLinearProgressIndicator(
                                progress = {
                                    if (sentences.isNotEmpty()) (currentIndex + 1).toFloat() / sentences.size else 0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isServiceActive || isPlaying) {
                            IconButton(
                                onClick = onStopClick,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Stop")
                            }
                        }

                        FloatingActionButton(
                            onClick = onPlayPauseClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = MaterialTheme.shapes.large,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        if (isServiceActive || !isPlaying) {
                            IconButton(
                                onClick = onExportClick,
                                enabled = !isExporting,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Export")
                            }
                        }
                    }
                }
            }

            // Export Overlay
            if (isExporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Card(
                            modifier = Modifier
                                .width(300.dp)
                                .padding(16.dp),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Saving Audio...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                if (exportTotal > 0) {
                                    val progress = exportCurrent.toFloat() / exportTotal
                                    Box(contentAlignment = Alignment.Center) {
                                        WavyCircularProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.size(80.dp),
                                            strokeWidth = 6.dp,
                                            waveAmplitude = 3.dp
                                        )
                                        Text(
                                            text = "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "$exportCurrent / $exportTotal chunks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    IndeterminateWavyProgressIndicator(
                                        modifier = Modifier.size(80.dp),
                                        strokeWidth = 6.dp
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                TextButton(
                                    onClick = onCancelExportClick,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SentenceItem(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
        else Color.Transparent,
        label = "backgroundColor"
    )
    val contentColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.onSurface 
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        label = "contentColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 28.sp
                ),
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
