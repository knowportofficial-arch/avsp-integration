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
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.DiscoveredVoice
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
    val voices by viewModel.voices.collectAsState()
    val scriptReady by viewModel.scriptReady.collectAsState()
    val projectName by viewModel.projectName.collectAsState()
    val cloneConfigured by viewModel.cloneConfigured.collectAsState()
    val cloneProfile by viewModel.cloneProfile.collectAsState()
    val message by viewModel.message.collectAsState()
    val playPath by viewModel.playPath.collectAsState()
    val paused by viewModel.paused.collectAsState()

    LaunchedEffect(projectId) { viewModel.start(projectId) }

    DisposableEffect(playPath) {
        val path = playPath
        var player: MediaPlayer? = null
        if (path != null) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener { viewModel.clearPlayPath() }
                    setOnErrorListener { _, _, _ ->
                        viewModel.stopPlayback()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                viewModel.stopPlayback()
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

        Text("Project: ${projectName.ifBlank { projectId }}", style = MaterialTheme.typography.titleMedium)
        Text(
            "M3 converts the saved M2 script into scene-aligned local audio. Video (M4) is not started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            if (scriptReady) "M2 script: available" else "M2 script: SCRIPT_REQUIRED",
            color = if (scriptReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Text("Voice Mode", style = MaterialTheme.typography.titleLarge)
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

        if (form.voiceMode == VoiceMode.SIMPLE_LOCAL) {
            LanguageDropdown(selected = form.language, onSelected = viewModel::updateLanguage)
            VoiceDropdown(
                label = "Available local voice",
                voices = voices,
                selected = form.voiceId,
                onSelected = viewModel::updateVoice
            )
            if (voices.isEmpty()) {
                Text(
                    "No installed local voices for ${form.language}. Install a language pack in Android TTS settings.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            if (!cloneConfigured) {
                Text("My Voice Clone is not configured.", color = MaterialTheme.colorScheme.error)
                Text("Install a local clone profile to use this optional mode. Simple Local Voice still works.")
            } else {
                Text("Saved profile: ${cloneProfile?.displayName ?: cloneProfile?.profileId}")
                Text("Status: ${cloneProfile?.status?.name}")
                OutlinedButton(onClick = { /* preview uses generate of one clip after config */ }) {
                    Text("Preview (uses clone synthesizer)")
                }
            }
        }

        Text("Voice Assignment", style = MaterialTheme.typography.titleLarge)
        VoiceDropdown(
            label = "Entire project (default)",
            voices = voices,
            selected = form.voiceId,
            onSelected = viewModel::updateVoice
        )
        VoiceDropdown(
            label = "Intro",
            voices = voices,
            selected = form.introVoiceId.ifBlank { form.voiceId },
            onSelected = viewModel::updateIntroVoice
        )
        VoiceDropdown(
            label = "Body",
            voices = voices,
            selected = form.bodyVoiceId.ifBlank { form.voiceId },
            onSelected = viewModel::updateBodyVoice
        )
        VoiceDropdown(
            label = "Outro",
            voices = voices,
            selected = form.outroVoiceId.ifBlank { form.voiceId },
            onSelected = viewModel::updateOutroVoice
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::generate,
                enabled = scriptReady,
                modifier = Modifier.weight(1f)
            ) { Text("Generate audio") }
            OutlinedButton(
                onClick = viewModel::previewEntire,
                modifier = Modifier.weight(1f)
            ) { Text("Preview entire") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::pauseOrResume, modifier = Modifier.weight(1f)) {
                Text(if (paused) "Resume" else "Pause")
            }
            OutlinedButton(onClick = viewModel::stopPlayback, modifier = Modifier.weight(1f)) {
                Text("Stop")
            }
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
                voices = voices,
                playing = playPath != null,
                onPreview = viewModel::previewSegment,
                onRegenerate = viewModel::regenerateClip,
                onSceneVoice = viewModel::updateSceneVoice
            )
        }
    }
}

@Composable
private fun AudioPackagePanel(
    audio: AudioPackage,
    voices: List<DiscoveredVoice>,
    playing: Boolean,
    onPreview: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onSceneVoice: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Scene Audio List", style = MaterialTheme.typography.titleLarge)
        Text("Mode: ${audio.assignment.voiceMode.name}")
        Text("Total duration: ${audio.totalDurationMs}ms (measured)")
        Text("Intro ${audio.introAudio()?.durationMs ?: 0}ms · Outro ${audio.outroAudio()?.durationMs ?: 0}ms")
        Text("Format: WAV PCM ${audio.metadata.format.sampleRateHz}Hz mono")
        if (playing) Text("Playing…")
        audio.segments.sortedBy { it.order }.forEach { seg ->
            HorizontalDivider()
            ClipRow(seg, voices, onPreview, onRegenerate, onSceneVoice)
        }
        Text(
            "Continue to Video Engine is reserved for M4 — not started automatically.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ClipRow(
    seg: AudioSegment,
    voices: List<DiscoveredVoice>,
    onPreview: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onSceneVoice: (String, String) -> Unit
) {
    Text("${seg.title.ifBlank { seg.role.name }} · ${seg.sceneId}", style = MaterialTheme.typography.titleMedium)
    Text(seg.sourceText, style = MaterialTheme.typography.bodyMedium)
    Text(
        "Voice: ${seg.voiceId} · ${seg.status.name} · ${seg.durationMs}ms",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    if (!seg.errorMessage.isNullOrBlank()) {
        Text(seg.errorMessage, color = MaterialTheme.colorScheme.error)
    }
    VoiceDropdown(
        label = "Scene voice",
        voices = voices,
        selected = seg.voiceId,
        onSelected = { onSceneVoice(seg.sceneId, it) }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { onPreview(seg.relativeAudioPath) },
            enabled = seg.isPlayable()
        ) { Text("Play") }
        TextButton(onClick = { onRegenerate(seg.sceneId) }) { Text("Regenerate") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("en" to "English", "hi" to "Hindi", "bn" to "Bengali")
    SimpleDropdown(
        label = "Language",
        value = options.find { it.first == selected }?.second ?: selected,
        items = options.map { it.first to it.second },
        onSelected = onSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(
    label: String,
    voices: List<DiscoveredVoice>,
    selected: String,
    onSelected: (String) -> Unit
) {
    val items = voices.map { voice ->
        val gender = voice.gender?.let { " · $it" }.orEmpty()
        val inst = if (voice.installed) "installed" else "not installed"
        voice.voiceId to "${voice.name} (${voice.locale}$gender · $inst)"
    }
    val display = items.find { it.first == selected }?.second ?: selected.ifBlank { "Default" }
    SimpleDropdown(label = label, value = display, items = items, onSelected = onSelected)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    value: String,
    items: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}
