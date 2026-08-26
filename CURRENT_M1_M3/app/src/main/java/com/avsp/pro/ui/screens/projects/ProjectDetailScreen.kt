package com.avsp.pro.ui.screens.projects

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.ProjectDetailViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectDetailScreen(
    projectId: String,
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
    onOpenScriptAi: () -> Unit = {},
    onOpenAudioTts: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenMediaLibrary: () -> Unit = {},
    onOpenGuidedCapture: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }

    LaunchedEffect(projectId) {
        viewModel.load(projectId)
    }

    when (val s = state) {
        is UiState.Idle, is UiState.Loading -> LoadingState("Opening project…")
        is UiState.Error -> ErrorState(s.message, onRetry = { viewModel.load(projectId) })
        is UiState.Success -> {
            val dash = s.data
            val project = dash.project
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
                    Button(onClick = {
                        renameValue = project.name
                        showRename = true
                    }) { Text("Rename") }
                }
                Text(project.name, style = MaterialTheme.typography.headlineLarge)
                Text(project.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyLarge)
                Text("Status: ${project.status.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Created: ${DateFormat.getDateTimeInstance().format(Date(project.createdAt))}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Updated: ${DateFormat.getDateTimeInstance().format(Date(project.updatedAt))}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Duration: ${project.duration?.let { "${it}ms" } ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Aspect ratio: ${project.aspectRatio.label}", style = MaterialTheme.typography.bodyMedium)
                Text("Language: ${project.language.displayName}", style = MaterialTheme.typography.bodyMedium)
                Text("Output: ${project.outputPath ?: "Not set"}", style = MaterialTheme.typography.bodyMedium)

                Button(
                    onClick = onOpenScriptAi,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Script AI") }
                Button(
                    onClick = onOpenAudioTts,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Audio / TTS") }
                Button(
                    onClick = onOpenCamera,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Camera") }
                Button(
                    onClick = onOpenGuidedCapture,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guided Capture") }
                Button(
                    onClick = onOpenMediaLibrary,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Media Library / Quality") }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Available assets", style = MaterialTheme.typography.titleLarge)
                if (dash.assets.isEmpty()) {
                    Text(
                        "No media assets yet. Use Camera or Guided Capture to add clips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    dash.assets.forEach { asset ->
                        Text("${asset.fileName} · ${asset.mimeType}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Module status", style = MaterialTheme.typography.titleLarge)
                    dash.modules.forEach { mod ->
                    val highlight = when (mod.moduleId) {
                        AvspModules.M2_SCRIPT_AI, AvspModules.M3_AUDIO_TTS,
                        AvspModules.M6_CAMERA, AvspModules.M7_DATASET_VISION -> true
                        in AvspModules.FROZEN_MODULE_IDS -> true
                        else -> false
                    }
                    Text(
                        "${mod.displayName}: ${mod.status.name}",
                        style = if (highlight) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "M1/M2 = FROZEN. M3/M6/M7 = READY. M4/M5/M8/M9 = FROZEN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(projectId, renameValue)
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }
}
