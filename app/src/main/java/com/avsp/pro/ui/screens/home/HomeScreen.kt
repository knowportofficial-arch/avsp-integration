package com.avsp.pro.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenProjects: () -> Unit,
    onOpenModules: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    when (val s = state) {
        is UiState.Idle, is UiState.Loading -> LoadingState("Preparing AVSP…")
        is UiState.Error -> ErrorState(s.message, onRetry = viewModel::refresh)
        is UiState.Success -> {
            val data = s.data
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "AVSP",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "AI Video Studio Pro",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        Text(
                            "Modular production shell — Core & UI foundation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Studio overview", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${data.projectCount} projects · M1 status ${data.m1Status}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenProjects) { Text("Projects") }
                        OutlinedButton(onClick = onOpenModules) { Text("Modules") }
                        TextButton(onClick = onOpenSettings) { Text("Settings") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent projects", style = MaterialTheme.typography.titleLarge)
                    if (data.recentProjects.isEmpty()) {
                        Text(
                            "No projects yet. Create one from Projects to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        data.recentProjects.forEach { project ->
                            Text(
                                "${project.name} · ${project.status.name}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pipeline modules", style = MaterialTheme.typography.titleLarge)
                    data.modules.take(5).forEach { mod ->
                        Text(
                            "${mod.displayName}: ${mod.status.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                }
            }
        }
    }
}
