package com.avsp.pro.capture.camera.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.avsp.pro.capture.theme.*
import com.avsp.pro.capture.camera.CaptureMode
import com.avsp.pro.capture.camera.mission.ShotMediaType

@Composable
fun ShotMissionDialog(
    onDismiss: () -> Unit,
    onStartContextMission: (String) -> Unit,
    onSelectPresetMission: (ShotMission) -> Unit,
    presets: List<ShotMission>,
    activeMission: ShotMission? = null,
    onPromoteShot: (String) -> Unit = {},
    onSkipCurrentShot: () -> Unit = {},
    onCaptureModeSelected: (CaptureMode) -> Unit = {}
) {
    var customContextText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(if (activeMission != null) 0 else 1) }
    var selectedCaptureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    val naturalPersonalPhoto = remember(activeMission) {
        val text = listOfNotNull(
            activeMission?.title,
            activeMission?.contextDescription,
            activeMission?.currentShot?.title,
            activeMission?.currentShot?.subjectHint
        ).joinToString(" ").lowercase()
        listOf(
            "take my photo", "personal photo", "personal portrait", "selfie",
            "portrait of me", "my picture", "my photo"
        ).any(text::contains)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Plan",
                            tint = Indigo500
                        )
                        Text(
                            text = "AI Cinematographer Plan",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                // Collection format: the same mission can be collected as photo or video.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCaptureMode == CaptureMode.PHOTO,
                        onClick = { selectedCaptureMode = CaptureMode.PHOTO; onCaptureModeSelected(CaptureMode.PHOTO) },
                        label = { Text("PHOTO", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = selectedCaptureMode == CaptureMode.VIDEO,
                        onClick = { selectedCaptureMode = CaptureMode.VIDEO; onCaptureModeSelected(CaptureMode.VIDEO) },
                        label = { Text("VIDEO", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Videocam, null, modifier = Modifier.size(16.dp)) }
                    )
                    if (!naturalPersonalPhoto) {
                        Text(
                            text = "W / M / C is chosen by the shot",
                            color = Slate500,
                            fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                // Tab Switcher (Active Plan vs New Sequence)
                if (activeMission != null) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Slate950,
                        contentColor = Indigo500
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Active Plan", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("New Sequence", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                if (selectedTab == 0 && activeMission != null) {
                    // Active Shot Plan Management
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = activeMission.title,
                            color = Slate100,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (naturalPersonalPhoto) {
                                activeMission.currentShot?.description?.ifBlank { "Point the camera toward yourself and follow the guidance." }
                                    ?: "Point the camera toward yourself and follow the guidance."
                            } else activeMission.contextDescription,
                            color = Slate400,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(activeMission.shots) { shot ->
                                val isCurrent = shot.status == ShotStatus.CURRENT || shot.status == ShotStatus.OPPORTUNITY
                                val isCaptured = shot.status == ShotStatus.CAPTURED || shot.isCompleted
                                val isOpportunity = shot.status == ShotStatus.OPPORTUNITY

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isCurrent) {
                                            onPromoteShot(shot.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isOpportunity -> Rose500.copy(alpha = 0.25f)
                                            isCurrent -> Indigo500.copy(alpha = 0.25f)
                                            isCaptured -> Slate950.copy(alpha = 0.5f)
                                            else -> Slate800
                                        }
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = when {
                                                        isOpportunity -> Rose500
                                                        isCurrent -> Indigo500
                                                        isCaptured -> Emerald500
                                                        else -> Slate700
                                                    }
                                                ) {
                                                    Text(
                                                        text = shot.status.displayName,
                                                        color = Color.White,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = shot.title,
                                                    color = Slate100,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = if (shot.mediaType == ShotMediaType.VIDEO) Rose500.copy(alpha = 0.22f) else Emerald500.copy(alpha = 0.22f)
                                                ) {
                                                    Text(
                                                        text = shot.mediaType.name,
                                                        color = if (shot.mediaType == ShotMediaType.VIDEO) Rose500 else Emerald500,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = shot.description.ifBlank { shot.subjectHint },
                                                color = Slate400,
                                                fontSize = 10.sp,
                                                maxLines = 2
                                            )
                                        }

                                        if (!isCurrent) {
                                            TextButton(onClick = { onPromoteShot(shot.id) }) {
                                                Text(
                                                    if (isCaptured) "Select Again" else "Make Current",
                                                    fontSize = 10.sp,
                                                    color = Indigo500
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onSkipCurrentShot) {
                                Text("Skip Current Shot", fontSize = 11.sp, color = Slate400)
                            }
                        }
                    }
                } else {
                    // Custom Context Prompt Generator
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "What or where are you filming?",
                            color = Slate400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = customContextText,
                            onValueChange = { customContextText = it },
                            placeholder = { Text("e.g. Take photos of my computer, or Railway station", color = Slate600, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Indigo500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate100
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (customContextText.isNotBlank()) {
                                    onStartContextMission(customContextText)
                                }
                            },
                            enabled = customContextText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Generate Dynamic Plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Divider(color = Slate800)

                    // Presets List
                    Text(
                        text = "Or choose standard coverage sequence:",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets) { preset ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPresetMission(preset) },
                                colors = CardDefaults.cardColors(containerColor = Slate800),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = preset.title,
                                            color = Slate100,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Indigo500.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "${preset.shots.size} SHOTS",
                                                color = Indigo500,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = preset.contextDescription,
                                        color = Slate400,
                                        fontSize = 10.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveEventDialog(
    onDismiss: () -> Unit,
    onReportEvent: (String) -> Unit
) {
    var eventText by remember { mutableStateOf("") }

    val quickEvents = listOf(
        "Special train arrival",
        "Action movement started",
        "Subject interaction / gesture",
        "Key guest arrived",
        "Process / pouring action"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Opportunity",
                            tint = Rose500
                        )
                        Text(
                            text = "Report Live Occurrence",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Text(
                    text = "Instantly reprioritizes the cinematography plan for spontaneous action.",
                    color = Slate400,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = eventText,
                    onValueChange = { eventText = it },
                    placeholder = { Text("e.g. A special train has arrived", color = Slate600, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Rose500,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (eventText.isNotBlank()) {
                            onReportEvent(eventText)
                        }
                    },
                    enabled = eventText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⚡ Insert High Priority Opportunity", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Divider(color = Slate800)

                Text(
                    text = "Quick Occurrences:",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickEvents.forEach { ev ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReportEvent(ev) }
                        ) {
                            Text(
                                text = "⚡ $ev",
                                color = Slate200,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
