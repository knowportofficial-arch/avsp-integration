package com.avsp.pro.ui.screens.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.ModulesViewModel

@Composable
fun ModulesScreen(viewModel: ModulesViewModel) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Module status", style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        }
            Text(
                "M1/M2 = FROZEN. M3 Audio/TTS = READY. M4 Android Video = IN DEVELOPMENT.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        when (val s = state) {
            is UiState.Idle, is UiState.Loading -> LoadingState("Loading module registry…")
            is UiState.Error -> ErrorState(s.message, onRetry = viewModel::refresh)
            is UiState.Success -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(s.data, key = { it.moduleId }) { mod ->
                        Column(modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(mod.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Version ${mod.version} · ${mod.status.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor(mod.status)
                            )
                            mod.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: ModuleRunStatus) = when (status) {
    ModuleRunStatus.READY, ModuleRunStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    ModuleRunStatus.FAILED -> MaterialTheme.colorScheme.error
    ModuleRunStatus.RUNNING -> MaterialTheme.colorScheme.secondary
    ModuleRunStatus.FROZEN -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
}
