package com.brahmadeo.supertonic.tts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    sleepTimerSecondsRemaining: Int,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onItemClick: (Int) -> Unit,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onExportClick: () -> Unit,
    onCancelExportClick: () -> Unit,
    onSleepTimerClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val navBarBottom = navBarsPadding.calculateBottomPadding()

    val bottomPadding by animateDpAsState(
        targetValue = if (isServiceActive || isPlaying) 210.dp else 140.dp,
        label = "playback_bottom_padding"
    )

    // Auto-scroll to active sentence
    LaunchedEffect(currentIndex) {
        if (currentIndex in sentences.indices) {
            val visibleInfo = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleInfo.any { it.index == currentIndex }
            if (!isVisible) {
                listState.animateScrollToItem(currentIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHomeClick) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
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
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomPadding + navBarBottom
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + navBarBottom
                    )
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
                        // 1. Close / Stop Button
                        val isStopEnabled = isServiceActive || isPlaying
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .height(56.dp)
                                .clickable(enabled = isStopEnabled, onClick = onStopClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.stop_label),
                                modifier = Modifier.size(24.dp),
                                tint = if (isStopEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.stop_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isStopEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        // 2. Sleep Timer Button
                        val isTimerEnabled = isServiceActive || isPlaying
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .height(56.dp)
                                .clickable(enabled = isTimerEnabled, onClick = onSleepTimerClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.timer_label),
                                modifier = Modifier.size(24.dp),
                                tint = if (isTimerEnabled) {
                                    if (sleepTimerSecondsRemaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val timerText = if (sleepTimerSecondsRemaining > 0 && isTimerEnabled) {
                                val mins = sleepTimerSecondsRemaining / 60
                                val secs = sleepTimerSecondsRemaining % 60
                                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
                            } else {
                                androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.timer_label)
                            }
                            Text(
                                text = timerText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = if (sleepTimerSecondsRemaining > 0 && isTimerEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTimerEnabled) {
                                    if (sleepTimerSecondsRemaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                }
                            )
                        }

                        // 3. Play / Pause Button (FAB)
                        FloatingActionButton(
                            onClick = onPlayPauseClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = MaterialTheme.shapes.large,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 4. Chapters / Library Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .height(56.dp)
                                .clickable(onClick = onBackClick)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.chapters_label),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.chapters_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }

                        // 5. Save / Export Button
                        val isSaveEnabled = !isExporting && (isServiceActive || !isPlaying)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .height(56.dp)
                                .clickable(enabled = isSaveEnabled, onClick = onExportClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.save_label),
                                modifier = Modifier.size(24.dp),
                                tint = if (isSaveEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(id = com.brahmadeo.supertonic.tts.R.string.save_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isSaveEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
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
    val containerColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
    )
    val contentColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                style = if (isActive) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
