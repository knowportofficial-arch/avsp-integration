package com.avsp.pro.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.settings.ConfigState
import com.avsp.pro.settings.ThemePreference
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    val saved by viewModel.saved.collectAsState()
    var aiKeyDraft by remember { mutableStateOf("") }

    LaunchedEffect(saved) {
        if (saved) {
            delay(1500)
            viewModel.clearSavedFlag()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Core preferences for AVSP. Stored secrets are never displayed after save — only CONFIGURED / NOT CONFIGURED.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        when (val s = state) {
            is UiState.Idle, is UiState.Loading -> LoadingState("Loading settings…")
            is UiState.Error -> ErrorState(s.message, onRetry = viewModel::refresh)
            is UiState.Success -> {
                val settings = s.data
                EnumDropdown(
                    label = "Default language",
                    options = ProjectLanguage.entries,
                    selected = settings.defaultLanguage,
                    labelFor = { it.displayName },
                    onSelected = viewModel::updateLanguage
                )
                EnumDropdown(
                    label = "Default aspect ratio",
                    options = AspectRatio.entries,
                    selected = settings.defaultAspectRatio,
                    labelFor = { it.label },
                    onSelected = viewModel::updateAspect
                )
                EnumDropdown(
                    label = "Theme",
                    options = ThemePreference.entries,
                    selected = settings.themePreference,
                    labelFor = { it.name },
                    onSelected = viewModel::updateTheme
                )
                EnumDropdown(
                    label = "Logging level",
                    options = LogLevel.entries,
                    selected = settings.loggingLevel,
                    labelFor = { it.name },
                    onSelected = viewModel::updateLogLevel
                )
                OutlinedTextField(
                    value = settings.defaultOutputDirectoryRef,
                    onValueChange = viewModel::updateOutputRef,
                    label = { Text("Default output directory reference") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Relative reference only — not an absolute OS path.")
                    }
                )

                Text("Connections", style = MaterialTheme.typography.titleLarge)
                ConfigStateRow("AI configuration", settings.aiConfigState)
                ConfigStateRow("Publishing configuration", settings.publishingConfigState)

                OutlinedTextField(
                    value = aiKeyDraft,
                    onValueChange = { aiKeyDraft = it },
                    label = { Text("Gemini API key (dev)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text("Stored in encrypted SecureConfigStore. Never committed to Git.")
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveAiApiCredential(aiKeyDraft)
                            aiKeyDraft = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = aiKeyDraft.isNotBlank()
                    ) { Text("Save AI key") }
                    OutlinedButton(
                        onClick = {
                            viewModel.clearAiApiCredential()
                            aiKeyDraft = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear AI key") }
                }

                Button(
                    onClick = { viewModel.save(settings) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saved) "Saved" else "Save settings")
                }
            }
        }
    }
}

@Composable
private fun ConfigStateRow(label: String, state: ConfigState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            state.name.replace('_', ' '),
            style = MaterialTheme.typography.titleMedium,
            color = if (state == ConfigState.CONFIGURED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelFor(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelFor(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
