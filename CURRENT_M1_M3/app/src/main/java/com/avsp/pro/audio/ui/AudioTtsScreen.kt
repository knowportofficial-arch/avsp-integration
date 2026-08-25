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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.settings.ConfigState
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
    val providers by viewModel.providers.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()
    val scriptReady by viewModel.scriptReady.collectAsState()
    val message by viewModel.message.collectAsState()
    val playPath by viewModel.playPath.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(projectId) { viewModel.start(projectId) }

    // Preview player — releases on dispose / path change.
    DisposableEffect(playPath) {
        val path = playPath
        var player: MediaPlayer? = null
        if (path != null) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener { viewModel.clearPlayPath() }
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

        Text(
            "M3 converts the saved M2 script into scene-aligned audio. Video (M4) is not started automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Text(
            "AI/cloud credentials: ${aiConfig.name.replace('_', ' ')}",
            style = MaterialTheme.typography.titleMedium,
            color = if (aiConfig == ConfigState.CONFIGURED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            }
        )

        Text(
            if (scriptReady) "M2 script: available" else "M2 script: SCRIPT_REQUIRED",
            style = MaterialTheme.typography.titleMedium,
            color = if (scriptReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        ProviderDropdown(
            providers = providers,
            selected = form.providerId,
            onSelected = viewModel::updateProvider
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::generate,
                enabled = scriptReady,
                modifier = Modifier.weight(1f)
            ) { Text("Generate audio") }
            OutlinedButton(
                onClick = viewModel::regenerate,
                enabled = scriptReady,
                modifier = Modifier.weight(1f)
            ) { Text("Regenerate") }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
        }

        HorizontalDivider()

        when (val s = state) {
            is UiState.Idle -> Text(
                "Generate audio after an M2 script is saved for this project.",
                style = MaterialTheme.typography.bodyMedium
            )
            is UiState.Loading -> LoadingState("Generating audio…")
            is UiState.Error -> ErrorState(s.message, onRetry = viewModel::generate)
            is UiState.Success -> AudioPackagePanel(
                audio = s.data,
                playing = playPath != null,
                onPreview = viewModel::previewSegment,
                onStop = viewModel::clearPlayPath
            )
        }
    }
}

@Composable
private fun AudioPackagePanel(
    audio: AudioPackage,
    playing: Boolean,
    onPreview: (String) -> Unit,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio package", style = MaterialTheme.typography.titleLarge)
        Text("Provider: ${audio.provider} · Voice: ${audio.voice.voiceId}")
        Text("Language: ${audio.language}")
        Text("Validation: ${audio.validation.status.name}")
        Text("Total duration: ${audio.totalDurationMs}ms")
        Text(
            "Format: ${audio.metadata.format.format} ${audio.metadata.format.sampleRateHz}Hz " +
                "${audio.metadata.format.channels}ch ${audio.metadata.format.encoding}"
        )
        if (audio.metadata.timingDriftWarnings.isNotEmpty()) {
            Text(
                "Timing notes: ${audio.metadata.timingDriftWarnings.size} drift warning(s)",
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (playing) {
            TextButton(onClick = onStop) { Text("Stop preview") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        audio.segments.sortedBy { it.order }.forEach { seg ->
            HorizontalDivider()
            Text(
                "Segment ${seg.order + 1} · scene ${seg.sceneId} · ${seg.durationMs}ms",
                style = MaterialTheme.typography.titleMedium
            )
            Text(seg.sourceText, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Timeline ${seg.startMs}–${seg.endMs} · ${seg.provider}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            TextButton(onClick = { onPreview(seg.relativeAudioPath) }) {
                Text("Play segment")
            }
        }
        Text(
            "Continue to Video Engine is reserved for M4 — not started automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    providers: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = providers.find { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("TTS provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { (id, name) ->
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
