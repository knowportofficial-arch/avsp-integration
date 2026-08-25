package com.avsp.pro.ui.screens.logs

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
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.ui.components.EmptyState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.LogsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Logs / Status", style = MaterialTheme.typography.headlineLarge)
            Row {
                TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                TextButton(onClick = viewModel::clear) { Text("Clear") }
            }
        }
        when (val s = state) {
            is UiState.Idle, is UiState.Loading -> LoadingState("Loading logs…")
            is UiState.Error -> ErrorState(s.message, onRetry = viewModel::refresh)
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState(
                        title = "No log entries",
                        message = "Application events will appear here as you use AVSP.",
                        actionLabel = "Refresh",
                        onAction = viewModel::refresh
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(s.data, key = { it.id }) { entry ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    "${entry.level.name} · ${entry.module} · ${
                                        DateFormat.getTimeInstance().format(Date(entry.timestamp))
                                    }",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = levelColor(entry.level)
                                )
                                Text(entry.message, style = MaterialTheme.typography.bodyLarge)
                                entry.details?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: LogLevel) = when (level) {
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARNING -> MaterialTheme.colorScheme.secondary
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
}
