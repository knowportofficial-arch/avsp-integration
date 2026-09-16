package com.avsp.pro.ui.screens.pipeline

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avsp.pro.m5.M5InputEngine
import com.avsp.pro.m8.M8Timeline
import com.avsp.pro.m9.M9Job
import com.avsp.pro.m9.M9Platform
import com.avsp.pro.m9.M9StateMachine
import com.avsp.pro.m9.M9Status

@Composable
fun M5InputScreen(onBack: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("M5 Input & Content Analysis", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(url, { url = it }, label = { Text("YouTube URL") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { result = M5InputEngine.parseYouTubeUrl(url)?.let { "YouTube ID: $it" } ?: "Invalid YouTube URL" }, modifier = Modifier.fillMaxWidth()) { Text("Validate YouTube") }
        Button(onClick = { val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)); runCatching { context.startActivity(i) } }, modifier = Modifier.fillMaxWidth()) { Text("Open source") }
        Text(result)
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun M8CreativeScreen(projectId: String, onBack: () -> Unit) {
    val timeline = remember { M8Timeline(projectId = projectId, ctaStart = 10.0, ctaEnd = 15.0) }
    val errors = remember { timeline.validate() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("M8 Creative Director / Timeline", style = MaterialTheme.typography.headlineSmall)
        Text("Canvas: ${timeline.width}×${timeline.height}  •  ${timeline.format}")
        Text("CTA: ${if (timeline.validateCta()) "VALID (5s)" else "INVALID"}")
        Text(if (errors.isEmpty()) "Timeline contract: READY" else errors.joinToString("\n"))
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun M9PublishingScreen(projectId: String, onBack: () -> Unit) {
    val job = remember { M9Job(projectId = projectId, videoPath = "", title = "AVSP Project", platforms = setOf(M9Platform.YOUTUBE)) }
    var status by remember { mutableStateOf(job.status) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("M9 Publishing", style = MaterialTheme.typography.headlineSmall)
        Text("Status: $status")
        Button(onClick = { M9StateMachine.transition(job, M9Status.QUEUED); status = job.status }, enabled = status == M9Status.DRAFT, modifier = Modifier.fillMaxWidth()) { Text("Queue") }
        Button(onClick = { M9StateMachine.transition(job, M9Status.VALIDATING); status = job.status }, enabled = status == M9Status.QUEUED, modifier = Modifier.fillMaxWidth()) { Text("Validate") }
        Button(onClick = { M9StateMachine.transition(job, M9Status.READY); status = job.status }, enabled = status == M9Status.VALIDATING, modifier = Modifier.fillMaxWidth()) { Text("Mark Ready") }
        Button(onClick = onBack) { Text("Back") }
    }
}
