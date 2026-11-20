package com.brahmadeo.supertonic.tts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
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
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    var showSpeedSheet by remember { mutableStateOf(false) }

    val bottomPadding by animateDpAsState(
        targetValue = if (isServiceActive || isPlaying) 210.dp else 140.dp,
        label = "playback_bottom_padding"
    )

    // Auto-scroll to active sentence
    LaunchedEffect(currentIndex, isServiceActive, isPlaying, bottomPadding) {
        if (currentIndex in sentences.indices) {
            val layoutInfo = listState.layoutInfo
            val visibleInfo = layoutInfo.visibleItemsInfo
            val activeItem = visibleInfo.find { it.index == currentIndex }

            val bottomPaddingPx = with(density) { bottomPadding.toPx() }
            val unobscuredEnd = layoutInfo.viewportEndOffset - bottomPaddingPx

            val isObscured = activeItem == null ||
                    activeItem.offset < layoutInfo.viewportStartOffset ||
                    (activeItem.offset + activeItem.size) > unobscuredEnd

            if (isObscured) {
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
                    val isSpeedHigh = currentSpeed > 1.5f
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSpeedHigh) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isSpeedHigh) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clickable { showSpeedSheet = true }
                            .padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Playback Speed",
                                modifier = Modifier.size(16.dp),
                                tint = if (isSpeedHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.US, "%.2fx", currentSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpeedHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

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
                    bottom = bottomPadding
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val isSpeedHigh = currentSpeed > 1.5f
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSpeedHigh) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { showSpeedSheet = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "Playback Speed",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isSpeedHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format(Locale.US, "%.2fx", currentSpeed),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSpeedHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
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

            // Speed Adjustment Bottom Sheet
            if (showSpeedSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSpeedSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { onSpeedChange(1.10f) }) {
                                Text("Reset (1.10x)")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Preset Chips
                        val presets = com.brahmadeo.supertonic.tts.utils.PlaybackSpeedUtils.SPEED_PRESETS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { preset ->
                                val isSelected = Math.abs(currentSpeed - preset) < 0.02f
                                val isPresetHigh = com.brahmadeo.supertonic.tts.utils.PlaybackSpeedUtils.isHighSpeed(preset)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSpeedChange(preset) },
                                    label = {
                                        Text(
                                            text = String.format(Locale.US, "%.2fx", preset),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isPresetHigh) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9),
                                        selectedLabelColor = if (isPresetHigh) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val isHighSpeed = currentSpeed > 1.5f
                        val isDark = isSystemInDarkTheme()

                        val targetContainerBg = if (isHighSpeed) {
                            if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            if (isDark) Color(0xFF1B3B22).copy(alpha = 0.45f)
                            else Color(0xFFE8F5E9).copy(alpha = 0.85f)
                        }

                        val targetBorderColor = if (isHighSpeed) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        } else {
                            if (isDark) Color(0xFF81C784).copy(alpha = 0.4f)
                            else Color(0xFF4CAF50).copy(alpha = 0.5f)
                        }

                        val targetAccentColor = if (isHighSpeed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            if (isDark) Color(0xFF81C784)
                            else Color(0xFF2E7D32)
                        }

                        val targetInactiveTrack = if (isHighSpeed) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                        } else {
                            if (isDark) Color(0xFF2E7D32).copy(alpha = 0.3f)
                            else Color(0xFFA5D6A7).copy(alpha = 0.6f)
                        }

                        val containerBg by animateColorAsState(targetValue = targetContainerBg, label = "sheet_speed_bg")
                        val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "sheet_speed_border")
                        val accentColor by animateColorAsState(targetValue = targetAccentColor, label = "sheet_speed_accent")
                        val inactiveTrackColor by animateColorAsState(targetValue = targetInactiveTrack, label = "sheet_speed_inactive_track")

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = containerBg,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp), tint = accentColor)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Speed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = accentColor.copy(alpha = 0.15f),
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isHighSpeed) "Fast (DSP)" else "Natural",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = String.format(Locale.US, "%.2fx", currentSpeed),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                    }
                                }
                                Slider(
                                    value = currentSpeed,
                                    onValueChange = onSpeedChange,
                                    valueRange = com.brahmadeo.supertonic.tts.utils.PlaybackSpeedUtils.SLIDER_MIN_SPEED..com.brahmadeo.supertonic.tts.utils.PlaybackSpeedUtils.SLIDER_MAX_SPEED,
                                    steps = com.brahmadeo.supertonic.tts.utils.PlaybackSpeedUtils.SLIDER_STEPS,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = inactiveTrackColor,
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { showSpeedSheet = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
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
    val isFootnote = text.startsWith("Footnote:", ignoreCase = true)
    val containerColor by animateColorAsState(
        when {
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            isFootnote -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surface
        }
    )
    val contentColor by animateColorAsState(
        when {
            isActive -> MaterialTheme.colorScheme.onPrimaryContainer
            isFootnote -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = when {
            isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            isFootnote -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (isFootnote) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "FOOTNOTE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(
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
                    style = when {
                        isActive -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        isFootnote -> MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        else -> MaterialTheme.typography.bodyLarge
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
