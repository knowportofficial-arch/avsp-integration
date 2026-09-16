package com.avsp.pro.ui.screens.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.ui.components.EmptyState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.ProjectsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Projects", style = MaterialTheme.typography.headlineLarge)
            Button(onClick = { showCreate = true }) { Text("New") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        when (val s = state) {
            is UiState.Idle, is UiState.Loading -> LoadingState("Loading projects…")
            is UiState.Error -> ErrorState(s.message, onRetry = viewModel::refresh)
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState(
                        title = "No projects",
                        message = "Create a project to establish AVSP workspace folders and status tracking.",
                        actionLabel = "Create project",
                        onAction = { showCreate = true }
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(s.data, key = { it.projectId }) { project ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenProject(project.projectId) }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(project.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "${project.status.name} · ${project.aspectRatio.label} · ${project.language.code}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    "Updated ${DateFormat.getDateTimeInstance().format(Date(project.updatedAt))}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProject(name, description)
                        name = ""
                        description = ""
                        showCreate = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    actionError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            title = { Text("Could not complete action") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = viewModel::clearActionError) { Text("OK") }
            }
        )
    }
}
