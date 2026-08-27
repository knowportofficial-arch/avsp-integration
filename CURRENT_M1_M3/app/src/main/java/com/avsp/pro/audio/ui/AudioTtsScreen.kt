package com.avsp.pro.audio.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avsp.pro.audio.contract.AssignmentScope
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.VoiceCloneStatus
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTtsScreen(
    projectId: String,
    viewModel: AudioTtsViewModel,
    onBack: () -> Unit
) {
    val form by viewModel.form.collectAsState()
    val state by viewModel.state.collectAsState()
    val localVoices by viewModel.localVoices.collectAsState()
    val cloneProfile by viewModel.cloneProfile.collectAsState()
    val scriptReady by viewModel.scriptReady.collectAsState()
    val projectTitle by viewModel.projectTitle.collectAsState()
    val message by viewModel.message.collectAsState()
    val playPath by viewModel.playPath.collectAsState()

    LaunchedEffect(projectId) { viewModel.start(projectId) }

    DisposableEffect(playPath) {
        val path = playPath
        var player: MediaPlayer? = null
        if (path != null) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        viewModel.onPreviewCompleted()
                    }
                    setOnErrorListener { _, _, _ ->
                        viewModel.clearPlayPath()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                viewModel.clearPlayPath()
            }
        }
        onDispose {
            runCatching {
                player?.stop()
                player?.release()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Audio / TTS", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier)
        }

        Text("Project: $projectTitle", style = MaterialTheme.typography.titleLarge)
        Text(
            if (scriptReady) "M2 script: available" else "M2 script: SCRIPT_REQUIRED",
            color = if (scriptReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Text("Voice mode", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = form.voiceMode == VoiceMode.SIMPLE_LOCAL,
                onClick = { viewModel.updateVoiceMode(VoiceMode.SIMPLE_LOCAL) },
                label = { Text("Simple Local Voice") }
            )
            FilterChip(
                selected = form.voiceMode == VoiceMode.MY_VOICE_CLONE,
                onClick = { viewModel.updateVoiceMode(VoiceMode.MY_VOICE_CLONE) },
                label = { Text("My Voice Clone") }
            )
        }

        when (form.voiceMode) {
            VoiceMode.SIMPLE_LOCAL -> {
                LanguageDropdown(
                    selected = form.language,
                    onSelected = viewModel::updateLanguage
                )
                VoiceDropdown(
                    voices = localVoices,
                    selected = form.voiceId,
                    onSelected = viewModel::updateVoice
                )
            }
            VoiceMode.MY_VOICE_CLONE -> {
                if (cloneProfile.status == VoiceCloneStatus.CONFIGURED) {
                    Text("Profile: ${cloneProfile.displayName}", color = MaterialTheme.colorScheme.primary)
                    Text("Status: Configured", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "My Voice Clone is not configured.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Text("Voice assignment", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = form.assignmentScope == AssignmentScope.ENTIRE_PROJECT,
                onClick = { viewModel.updateAssignmentScope(AssignmentScope.ENTIRE_PROJECT) },
                label = { Text("Entire project") }
            )
            FilterChip(
                selected = form.assignmentScope == AssignmentScope.INTRO_BODY_OUTRO,
                onClick = { viewModel.updateAssignmentScope(AssignmentScope.INTRO_BODY_OUTRO) },
                label = { Text("Intro / Body / Outro") }
            )
        }

        Text("Speech rate: ${"%.1f".format(form.speechRate)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = form.speechRate,
            onValueChange = viewModel::updateSpeechRate,
            valueRange = 0.5f..2.0f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::generate,
                enabled = scriptReady,
                modifier = Modifier.weight(1f)
            ) { Text("Generate audio") }
            OutlinedButton(
                onClick = viewModel::previewEntireAudio,
                enabled = state is UiState.Success,
                modifier = Modifier.weight(1f)
            ) { Text("Preview entire audio") }
        }

        if (playPath != null) {
            TextButton(onClick = viewModel::clearPlayPath) { Text("Stop preview") }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
        }

        HorizontalDivider()

        when (val s = state) {
            is UiState.Idle -> Text("Generate audio after an M2 script is saved for this project.")
            is UiState.Loading -> LoadingState("Generating audio…")
            is UiState.Error -> ErrorState(s.message, onRetry = viewModel::generate)
            is UiState.Success -> AudioPackagePanel(
                audio = s.data,
                statusLabel = viewModel::segmentStatusLabel,
                onPreview = viewModel::previewSegment,
                onRegenerate = viewModel::regenerateSegment
            )
        }
    }
}

@Composable
private fun AudioPackagePanel(
    audio: AudioPackage,
    statusLabel: (AudioSegment) -> String,
    onPreview: (String) -> Unit,
    onRegenerate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio package", style = MaterialTheme.typography.titleLarge)
        Text("Mode: ${audio.voice.voiceMode.name.replace('_', ' ')}")
        Text("Total duration: ${audio.totalDurationMs}ms")
        Text("Status: ${audio.status}")
        Text(
            "Format: ${audio.metadata.format.format} ${audio.metadata.format.sampleRateHz}Hz",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Scene audio list", style = MaterialTheme.typography.titleMedium)
        audio.segments.sortedBy { it.order }.forEach { seg ->
            SegmentRow(
                segment = seg,
                status = statusLabel(seg),
                onPreview = { onPreview(seg.relativeAudioPath) },
                onRegenerate = { onRegenerate(seg.segmentId) }
            )
        }
    }
}

@Composable
private fun SegmentRow(
    segment: AudioSegment,
    status: String,
    onPreview: () -> Unit,
    onRegenerate: () -> Unit
) {
    HorizontalDivider()
    val title = segment.title.ifBlank { "Scene ${segment.order + 1}" }
    Text("$title · ${segment.role.name}", style = MaterialTheme.typography.titleMedium)
    Text(segment.sourceText, style = MaterialTheme.typography.bodyMedium)
    Text(
        "Voice: ${segment.voiceId} (${segment.voiceMode.name}) · ${segment.durationMs}ms · $status",
        style = MaterialTheme.typography.bodySmall,
        color = when (segment.status) {
            AudioSegmentStatus.STALE -> MaterialTheme.colorScheme.error
            AudioSegmentStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onPreview) { Text("Play") }
        TextButton(onClick = onRegenerate) { Text("Regenerate") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("en" to "English", "hi" to "Hindi", "bn" to "Bengali")
    var expanded by remember { mutableStateOf(false) }
    val label = options.find { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(
    voices: List<com.avsp.pro.audio.contract.LocalVoiceInfo>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = voices.find { it.voiceId == selected }?.displayName ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Available voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        val suffix = buildString {
                            voice.gender?.let { append(" · $it") }
                            if (voice.requiresDownload) append(" · download required")
                            if (!voice.installed) append(" · unavailable")
                        }
                        Text("${voice.displayName} (${voice.languageTag})$suffix")
                    },
                    onClick = {
                        onSelected(voice.voiceId)
                        expanded = false
                    }
                )
            }
        }
    }
}
