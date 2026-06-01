package com.brahmadeo.supertonic.tts.ui

import androidx.compose.animation.AnimatedVisibility
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brahmadeo.supertonic.tts.R as AppR
import androidx.compose.ui.tooling.preview.Preview
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    placeholderText: String,
    isInitializing: Boolean,
    isSynthesizing: Boolean,
    onSynthesizeClick: () -> Unit,

    languages: Map<String, String>,
    currentLangCode: String,
    onLangChange: (String) -> Unit,

    voices: Map<String, String>,
    selectedVoiceFile: String,
    onVoiceChange: (String) -> Unit,

    isMixingEnabled: Boolean,
    onMixingEnabledChange: (Boolean) -> Unit,
    selectedVoiceFile2: String,
    onVoice2Change: (String) -> Unit,
    mixAlpha: Float,
    onMixAlphaChange: (Float) -> Unit,

    speed: Float,
    onSpeedChange: (Float) -> Unit,
    steps: Int,
    onStepsChange: (Int) -> Unit,

    isAdvancedNormalizationEnabled: Boolean,
    onAdvancedNormalizationEnabledChange: (Boolean) -> Unit,

    onResetClick: () -> Unit,
    onSavedAudioClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLexiconClick: () -> Unit,
    onDeleteV2Click: () -> Unit,
    onDeleteV3Click: () -> Unit,
    onOpenEbookClick: () -> Unit,
    isV2Ready: Boolean,
    isV3Ready: Boolean,

    canResume: Boolean,
    onResumeClick: () -> Unit,

    showMiniPlayer: Boolean,
    miniPlayerTitle: String,
    miniPlayerIsPlaying: Boolean,
    onMiniPlayerClick: () -> Unit,
    onMiniPlayerPlayPauseClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(AppR.string.app_name),
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(AppR.string.action_reset)) }, 
                            onClick = { showMenu = false; onResetClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(AppR.string.action_open_ebook)) }, 
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                            onClick = { showMenu = false; onOpenEbookClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(AppR.string.action_saved)) }, 
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = { showMenu = false; onSavedAudioClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(AppR.string.action_queue)) }, 
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                            onClick = { showMenu = false; onQueueClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(AppR.string.action_lexicon)) },
                            onClick = { showMenu = false; onLexiconClick() },
                            enabled = currentLangCode != "ko"
                        )
                        if (isV2Ready && currentLangCode == "en") {
                            DropdownMenuItem(
                                text = { Text(stringResource(AppR.string.action_delete_v2), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDeleteV2Click() }
                            )
                        }
                        if (isV3Ready && currentLangCode == "en") {
                            DropdownMenuItem(
                                text = { Text(stringResource(AppR.string.action_delete_v3), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDeleteV3Click() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        floatingActionButton = {
            val buttonText = when {
                isInitializing -> stringResource(AppR.string.initializing)
                isSynthesizing -> stringResource(AppR.string.notif_synthesizing)
                else -> stringResource(AppR.string.synthesize_button)
            }
            val isLoading = isInitializing || isSynthesizing
            val synthesizeContentDesc = stringResource(AppR.string.synthesize_content_description)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (canResume && !isLoading) {
                    val resumeContentDesc = stringResource(AppR.string.resume_content_description)
                    SmallFloatingActionButton(
                        onClick = onResumeClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.semantics {
                            contentDescription = resumeContentDesc
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = onSynthesizeClick,
                    text = { Text(buttonText) },
                    icon = { Icon(painterResource(android.R.drawable.ic_btn_speak_now), contentDescription = null) },
                    expanded = true,
                    containerColor = if (isLoading) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics {
                        contentDescription = synthesizeContentDesc
                        stateDescription = buttonText
                        if (isLoading) {
                            disabled()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Text Input Section
                var isFocused by remember { mutableStateOf(false) }
                var isTextExpanded by remember { mutableStateOf(true) }
                var lineCount by remember { mutableIntStateOf(0) }

                LaunchedEffect(inputText) {
                    if (inputText.length > 500 && !isFocused) {
                        isTextExpanded = false
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Text(
                            text = inputText,
                            style = LocalTextStyle.current,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.dp)
                                .alpha(0f),
                            onTextLayout = { lineCount = it.lineCount }
                        )

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputTextChange,
                            placeholder = {
                                if (!isFocused) {
                                    Text(placeholderText)
                                }
                            },
                            label = { Text(stringResource(AppR.string.input_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isTextExpanded) Modifier.heightIn(min = 200.dp, max = 320.dp)
                                    else Modifier.heightIn(max = 130.dp)
                                )
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (it.isFocused) isTextExpanded = true
                                },
                            maxLines = if (isTextExpanded) 30 else 5,
                            shape = MaterialTheme.shapes.large
                        )
                    }

                    if (lineCount > 5) {
                        Text(
                            text = if (isTextExpanded) "Show less" else "Show more (${lineCount - 5} more lines)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 8.dp)
                                .clickable { isTextExpanded = !isTextExpanded }
                        )
                    }
                }

                // Configuration Groups
                SettingsGroup(
                    title = "Voice Configuration",
                    icon = Icons.Default.Settings
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DropdownSelector(
                            label = stringResource(AppR.string.language_label),
                            options = languages.keys.toList(),
                            selectedOption = languages.entries.find { it.value == currentLangCode }?.key ?: "English",
                            onOptionSelected = { name -> onLangChange(languages[name] ?: "en") }
                        )

                        DropdownSelector(
                            label = stringResource(AppR.string.voice_style_label),
                            options = voices.keys.toList().sorted(),
                            selectedOption = voices.entries.find { it.value == selectedVoiceFile }?.key ?: "",
                            onOptionSelected = { name -> onVoiceChange(voices[name] ?: "M1.json") }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(AppR.string.mix_voices_label), 
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(checked = isMixingEnabled, onCheckedChange = onMixingEnabledChange)
                        }

                        AnimatedVisibility(visible = isMixingEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                DropdownSelector(
                                    label = stringResource(AppR.string.voice_style_2_label),
                                    options = voices.keys.toList().sorted(),
                                    selectedOption = voices.entries.find { it.value == selectedVoiceFile2 }?.key ?: "",
                                    onOptionSelected = { name -> onVoice2Change(voices[name] ?: "M2.json") }
                                )

                                SliderWithLabel(
                                    label = stringResource(AppR.string.mix_ratio_label),
                                    value = mixAlpha,
                                    onValueChange = onMixAlphaChange,
                                    valueRange = 0f..1f,
                                    steps = 9,
                                    displayValue = "${(mixAlpha * 100).toInt()}%"
                                )
                            }
                        }
                    }
                }

                SettingsGroup(
                    title = "Playback Settings",
                    icon = Icons.Default.Tune
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SliderWithLabel(
                            label = stringResource(AppR.string.speed_label),
                            value = speed,
                            onValueChange = onSpeedChange,
                            valueRange = 0.9f..1.5f,
                            steps = 11,
                            displayValue = String.format(Locale.US, "%.2fx", speed),
                            leadingIcon = Icons.Default.Speed
                        )

                        SliderWithLabel(
                            label = stringResource(AppR.string.quality_label),
                            value = steps.toFloat(),
                            onValueChange = { onStepsChange(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            displayValue = "$steps steps"
                        )

                        if (currentLangCode != "en" && currentLangCode != "ko") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(AppR.string.advanced_normalization_label),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        stringResource(AppR.string.advanced_normalization_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isAdvancedNormalizationEnabled,
                                    onCheckedChange = onAdvancedNormalizationEnabledChange
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }

            // Mini Player Overlay
            if (showMiniPlayer) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    onClick = onMiniPlayerClick
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(AppR.string.now_playing_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = miniPlayerTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onMiniPlayerPlayPauseClick) {
                            Icon(
                                imageVector = if (miniPlayerIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (miniPlayerIsPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    leadingIcon: ImageVector? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(displayValue, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SupertonicTheme {
        MainScreen(
            inputText = "This is a sample text for previewing the MainScreen layout.",
            onInputTextChange = {},
            placeholderText = "Enter text here...",
            isInitializing = false,
            isSynthesizing = false,
            onSynthesizeClick = {},
            languages = mapOf("English" to "en", "Spanish" to "es"),
            currentLangCode = "en",
            onLangChange = {},
            voices = mapOf("Voice 1" to "v1", "Voice 2" to "v2"),
            selectedVoiceFile = "v1",
            onVoiceChange = {},
            isMixingEnabled = true,
            onMixingEnabledChange = {},
            selectedVoiceFile2 = "v2",
            onVoice2Change = {},
            mixAlpha = 0.5f,
            onMixAlphaChange = {},
            speed = 1.0f,
            onSpeedChange = {},
            steps = 5,
            onStepsChange = {},
            isAdvancedNormalizationEnabled = false,
            onAdvancedNormalizationEnabledChange = {},
            onResetClick = {},
            onSavedAudioClick = {},
            onHistoryClick = {},
            onQueueClick = {},
            onLexiconClick = {},
            onDeleteV2Click = {},
            onDeleteV3Click = {},
            onOpenEbookClick = {},
            isV2Ready = true,
            isV3Ready = true,
            canResume = true,
            onResumeClick = {},
            showMiniPlayer = true,
            miniPlayerTitle = "Now playing sample text",
            miniPlayerIsPlaying = true,
            onMiniPlayerClick = {},
            onMiniPlayerPlayPauseClick = {}
        )
    }
}
