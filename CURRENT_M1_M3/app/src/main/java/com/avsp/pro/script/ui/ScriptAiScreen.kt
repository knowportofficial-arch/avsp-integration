package com.avsp.pro.script.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.language.ScriptLanguageRegistry
import com.avsp.pro.settings.ConfigState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptAiScreen(
    projectId: String,
    viewModel: ScriptAiViewModel,
    onBack: () -> Unit,
    onOpenAudio: () -> Unit = {}
) {
    val form by viewModel.form.collectAsState()
    val scriptState by viewModel.scriptState.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()
    val savedMessage by viewModel.savedMessage.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.start(projectId)
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
            Text("Script AI", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier)
        }

        Text(
            "M2 generates an editable script package. Audio/TTS (M3) is not invoked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Text(
            "AI configuration: ${aiConfig.name.replace('_', ' ')}",
            style = MaterialTheme.typography.titleMedium,
            color = if (aiConfig == ConfigState.CONFIGURED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            }
        )
        if (aiConfig == ConfigState.NOT_CONFIGURED) {
            Text(
                "Using Mock Script Generator (deterministic). Remote providers require CONFIGURED credentials.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = form.topic,
            onValueChange = viewModel::updateTopic,
            label = { Text("Topic") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        LanguageDropdown(
            selected = form.languageCode,
            onSelected = viewModel::updateLanguage
        )

        DurationDropdown(
            mode = form.durationMode,
            onMode = viewModel::updateDurationMode
        )
        if (form.durationMode == "explicit") {
            OutlinedTextField(
                value = form.explicitDurationSec,
                onValueChange = viewModel::updateExplicitDurationSec,
                label = { Text("Duration (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = form.instructions,
            onValueChange = viewModel::updateInstructions,
            label = { Text("Optional instructions") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = viewModel::generate,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate script") }

        savedMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = viewModel::clearSavedMessage) { Text("Dismiss") }
        }

        HorizontalDivider()

        when (val state = scriptState) {
            is UiState.Idle -> Text(
                "Enter a topic and generate, or reopen a saved script for this project.",
                style = MaterialTheme.typography.bodyMedium
            )
            is UiState.Loading -> LoadingState("Working on script…")
            is UiState.Error -> ErrorState(state.message, onRetry = viewModel::generate)
            is UiState.Success -> ScriptEditor(
                script = state.data,
                onTitle = viewModel::updateTitle,
                onHook = viewModel::updateHook,
                onCta = viewModel::updateCta,
                onSceneNarration = viewModel::updateSceneNarration,
                onSave = viewModel::saveEdits,
                onOpenAudio = onOpenAudio
            )
        }
    }
}

@Composable
private fun ScriptEditor(
    script: ScriptPackage,
    onTitle: (String) -> Unit,
    onHook: (String) -> Unit,
    onCta: (String) -> Unit,
    onSceneNarration: (String, String) -> Unit,
    onSave: () -> Unit,
    onOpenAudio: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Review & edit", style = MaterialTheme.typography.titleLarge)
        Text(
            "Validation: ${script.validation.status.name}" +
                if (script.validation.warnings.isNotEmpty()) {
                    " · ${script.validation.warnings.size} warning(s)"
                } else "",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Target ${script.targetDurationMs}ms · Planned ${script.estimatedDurationMs}ms · " +
                "Narration ~${script.estimatedNarrationDurationMs}ms",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Generator: ${script.metadata.generatorId} (${script.metadata.generatorMode})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )

        OutlinedTextField(
            value = script.title,
            onValueChange = onTitle,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = script.hook,
            onValueChange = onHook,
            label = { Text("Hook") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = script.cta,
            onValueChange = onCta,
            label = { Text("CTA") },
            modifier = Modifier.fillMaxWidth()
        )

        script.scenes.sortedBy { it.order }.forEach { scene ->
            HorizontalDivider()
            Text(
                "Scene ${scene.order + 1} · ${scene.durationMs}ms · ${scene.shotType.name}",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = scene.narration,
                onValueChange = { onSceneNarration(scene.sceneId, it) },
                label = { Text("Narration") },
                modifier = Modifier.fillMaxWidth()
            )
            if (scene.onScreenText.isNotBlank()) {
                Text("On-screen: ${scene.onScreenText}", style = MaterialTheme.typography.bodyMedium)
            }
            if (scene.visualDescription.isNotBlank()) {
                Text("Visual: ${scene.visualDescription}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save edits")
        }
        Button(onClick = onOpenAudio, modifier = Modifier.fillMaxWidth()) {
            Text("Continue to Audio / TTS")
        }
        Text(
            "Audio generation is handled by M3. Video (M4) is not started automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selected: String, onSelected: (String) -> Unit) {
    val options = ScriptLanguageRegistry.supportedCodes().sorted()
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = ScriptLanguageRegistry.resolve(selected).displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(ScriptLanguageRegistry.resolve(code).displayName) },
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
private fun DurationDropdown(mode: String, onMode: (String) -> Unit) {
    val options = listOf("short", "medium", "long", "explicit")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = mode,
            onValueChange = {},
            readOnly = true,
            label = { Text("Duration") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onMode(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
