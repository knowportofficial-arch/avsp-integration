package com.avsp.pro.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VideoScreen(
    projectId: String,
    viewModel: VideoViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(projectId) { viewModel.prepare(projectId) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("M4 Video Engine", style = MaterialTheme.typography.headlineMedium)
        Text("Android backend: Media3 Transformer", style = MaterialTheme.typography.bodyMedium)
        when (val s = state) {
            VideoUiState.Idle -> Text("Preparing…")
            is VideoUiState.Ready -> {
                Text("Scenes: ${s.plan.scenes.size}")
                Text("Audio duration: ${s.plan.totalDurationMs} ms")
                Text("Output: ${s.plan.width} × ${s.plan.height} @ ${s.plan.fps}fps")
                Button(onClick = { viewModel.render(projectId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Render Video")
                }
            }
            is VideoUiState.Rendering -> {
                Text("Rendering… ${s.progress}%")
            }
            is VideoUiState.Success -> {
                Text("M4 render SUCCESS", color = MaterialTheme.colorScheme.primary)
                Text("Duration: ${s.durationMs} ms")
                Text("Output: ${s.width} × ${s.height}")
                Text(s.outputPath)
            }
            is VideoUiState.Error -> {
                Text("M4 ERROR", color = MaterialTheme.colorScheme.error)
                Text(s.message)
                Button(onClick = { viewModel.prepare(projectId) }) { Text("Retry") }
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}
