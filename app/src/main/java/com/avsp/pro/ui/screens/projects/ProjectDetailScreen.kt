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
import com.avsp.pro.core.model.AspectRatio
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
    onOpenVideoEngine: () -> Unit = {},
    onOpenM5: () -> Unit = {},
    onOpenM6: () -> Unit = {},
    onOpenM8: () -> Unit = {},
    onOpenM9: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showAspectPicker by remember { mutableStateOf(false) }

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
                Button(onClick = { showAspectPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Output format: ${project.aspectRatio.label} (${project.aspectRatio.width}×${project.aspectRatio.height})")
                }
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
                    onClick = onOpenVideoEngine,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Video Engine / M4") }
                Button(onClick = onOpenM5, modifier = Modifier.fillMaxWidth()) { Text("M5 Input / Analysis") }
                Button(onClick = onOpenM6, modifier = Modifier.fillMaxWidth()) { Text("M6 AI Camera / Guided Capture") }
                Button(onClick = onOpenM8, modifier = Modifier.fillMaxWidth()) { Text("M8 Creative / Timeline") }
                Button(onClick = onOpenM9, modifier = Modifier.fillMaxWidth()) { Text("M9 Publishing") }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Available assets", style = MaterialTheme.typography.titleLarge)
                if (dash.assets.isEmpty()) {
                    Text(
                        "M4 QA source clips are available automatically. Real M6 media will take priority for rendering.",
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
                        AvspModules.M2_SCRIPT_AI, AvspModules.M3_AUDIO_TTS -> true
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
                    "M1–M4 accepted baseline. M5–M9 Android integration is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }

    if (showAspectPicker) {
        AlertDialog(
            onDismissRequest = { showAspectPicker = false },
            title = { Text("Select output format") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectRatio.entries.forEach { option ->
                        TextButton(
                            onClick = {
                                showAspectPicker = false
                                viewModel.updateAspectRatio(projectId, option)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${option.label}  •  ${option.width}×${option.height}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAspectPicker = false }) { Text("Cancel") }
            }
        )
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
