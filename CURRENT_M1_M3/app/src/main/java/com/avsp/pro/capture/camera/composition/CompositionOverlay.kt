package com.avsp.pro.capture.camera.composition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avsp.pro.capture.camera.analyzer.ShotReadinessStatus
import com.avsp.pro.capture.camera.engine.CinematographerDecision
import com.avsp.pro.capture.camera.mission.ShotMissionItem
import com.avsp.pro.capture.camera.mission.ShotStatus
import com.avsp.pro.capture.camera.model.CameraProfile
import com.avsp.pro.capture.camera.understanding.SceneUnderstandingResult
import com.avsp.pro.capture.camera.vision.VisualAnalysisResult
import com.avsp.pro.capture.theme.*
import kotlin.math.abs

@Composable
fun CompositionOverlay(
    profile: CameraProfile,
    visualAnalysis: VisualAnalysisResult,
    sceneUnderstanding: SceneUnderstandingResult,
    decision: CinematographerDecision,
    activeMissionShot: ShotMissionItem?,
    autoCaptureHoldProgress: Float = 0.0f,
    isAutoCaptureEnabled: Boolean = true,
    onShotPlanClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val naturalUserMode = activeMissionShot != null
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Grid, Reticle, and Live Subject Bounding Box Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Rule of Thirds Lines
            if (profile.isGridOverlayEnabled) {
                val lineColor = Color.White.copy(alpha = 0.22f)
                val strokeWidthPx = 1.5f

                drawLine(
                    color = lineColor,
                    start = Offset(width / 3f, 0f),
                    end = Offset(width / 3f, height),
                    strokeWidth = strokeWidthPx
                )
                drawLine(
                    color = lineColor,
                    start = Offset(2 * width / 3f, 0f),
                    end = Offset(2 * width / 3f, height),
                    strokeWidth = strokeWidthPx
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, height / 3f),
                    end = Offset(width, height / 3f),
                    strokeWidth = strokeWidthPx
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, 2 * height / 3f),
                    end = Offset(width, 2 * height / 3f),
                    strokeWidth = strokeWidthPx
                )

                // Center Reticle
                val centerX = width / 2f
                val centerY = height / 2f
                val reticleSize = 20f

                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(centerX - reticleSize, centerY),
                    end = Offset(centerX + reticleSize, centerY),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(centerX, centerY - reticleSize),
                    end = Offset(centerX, centerY + reticleSize),
                    strokeWidth = 2f
                )
            }

            // Live Visual Subject Tracking Bounding Box
            decision.targetBoundingBox?.let { box ->
                val boxLeft = box.left * width
                val boxTop = box.top * height
                val boxWidth = (box.right - box.left) * width
                val boxHeight = (box.bottom - box.top) * height

                val boxColor = when (decision.status) {
                    ShotReadinessStatus.GOOD_SHOT -> Emerald500
                    ShotReadinessStatus.ALMOST_READY -> Indigo500
                    ShotReadinessStatus.ADJUST -> Amber500
                    ShotReadinessStatus.NOT_READY -> Color.White.copy(alpha = 0.4f)
                }

                // Draw Subject Bounding Box Corner Brackets
                val bracketLen = 24f
                val strokeW = 3f

                // Top-Left Corner
                drawLine(boxColor, Offset(boxLeft, boxTop), Offset(boxLeft + bracketLen, boxTop), strokeW)
                drawLine(boxColor, Offset(boxLeft, boxTop), Offset(boxLeft, boxTop + bracketLen), strokeW)

                // Top-Right Corner
                drawLine(boxColor, Offset(boxLeft + boxWidth, boxTop), Offset(boxLeft + boxWidth - bracketLen, boxTop), strokeW)
                drawLine(boxColor, Offset(boxLeft + boxWidth, boxTop), Offset(boxLeft + boxWidth, boxTop + bracketLen), strokeW)

                // Bottom-Left Corner
                drawLine(boxColor, Offset(boxLeft, boxTop + boxHeight), Offset(boxLeft + bracketLen, boxTop + boxHeight), strokeW)
                drawLine(boxColor, Offset(boxLeft, boxTop + boxHeight), Offset(boxLeft, boxTop + boxHeight - bracketLen), strokeW)

                // Bottom-Right Corner
                drawLine(boxColor, Offset(boxLeft + boxWidth, boxTop + boxHeight), Offset(boxLeft + boxWidth - bracketLen, boxTop + boxHeight), strokeW)
                drawLine(boxColor, Offset(boxLeft + boxWidth, boxTop + boxHeight), Offset(boxLeft + boxWidth, boxTop + bracketLen), strokeW)

                // Target Centroid Point
                val focalX = decision.targetFocusNormalizedX * width
                val focalY = decision.targetFocusNormalizedY * height
                drawCircle(
                    color = boxColor.copy(alpha = 0.8f),
                    radius = 4f,
                    center = Offset(focalX, focalY)
                )
            }
        }

        // 2. Horizon / Level Indicator Line
        if (profile.isHorizonLevelEnabled) {
            val isLevel = abs(visualAnalysis.tiltRollDegrees) <= 2.5f
            val levelColor = if (isLevel) Emerald500 else Amber500

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(110.dp)
                    .height(2.dp)
                    .background(levelColor.copy(alpha = 0.85f))
            )
        }

        // 3. Auto-Capture Holding Confirmation Ring
        if (autoCaptureHoldProgress > 0.0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
            ) {
                CircularProgressIndicator(
                    progress = autoCaptureHoldProgress,
                    modifier = Modifier.fillMaxSize(),
                    color = Emerald500,
                    strokeWidth = 4.dp,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    text = "HOLD",
                    color = Emerald500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // 4. Professional AI Cinematographer HUD: guidance is the primary element.
        val accent = MaterialTheme.colorScheme.primary
        val statusColor = when (decision.status) {
            ShotReadinessStatus.GOOD_SHOT -> Emerald500
            ShotReadinessStatus.ALMOST_READY -> accent
            ShotReadinessStatus.ADJUST -> Amber500
            ShotReadinessStatus.NOT_READY -> Slate300
        }
        val instructionIcon = when {
            decision.status == ShotReadinessStatus.GOOD_SHOT -> Icons.Default.CheckCircle
            decision.instruction.contains("closer", ignoreCase = true) -> Icons.Default.ZoomIn
            decision.instruction.contains("back", ignoreCase = true) -> Icons.Default.ZoomOut
            decision.instruction.contains("right", ignoreCase = true) -> Icons.Default.ArrowForward
            decision.instruction.contains("left", ignoreCase = true) -> Icons.Default.ArrowBack
            decision.instruction.contains("up", ignoreCase = true) -> Icons.Default.ArrowUpward
            decision.instruction.contains("down", ignoreCase = true) -> Icons.Default.ArrowDownward
            decision.instruction.contains("level", ignoreCase = true) -> Icons.Default.ScreenRotation
            else -> Icons.Default.AutoAwesome
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 174.dp, start = 14.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Technical telemetry is hidden from the normal AI shot experience.
            if (!naturalUserMode) Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AI CINEMATOGRAPHER",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("•", color = Slate500)
                    Text(
                        text = "${profile.frameRate.displayName} • ${profile.resolution.displayName}",
                        color = Slate300,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAutoCaptureEnabled) {
                        Text("AUTO", color = Emerald500, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            // The recommendation itself is intentionally large and calm.
            Surface(
                modifier = Modifier.widthIn(max = 330.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.28f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f))
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(instructionIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = activeMissionShot?.title?.uppercase()
                                    ?: "${decision.shotType.displayName} SHOT",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2
                            )
                            if (!naturalUserMode) {
                                Text(
                                    text = "${decision.readinessScore}% • ${decision.status.label}",
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = if (decision.isReady) "READY" else "AI GUIDANCE",
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = decision.instruction,
                        color = Slate100,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp
                    )
                    if (decision.reason.isNotBlank() && decision.status != ShotReadinessStatus.GOOD_SHOT) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = decision.reason,
                            color = Slate400,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            // Compact evidence/context line is hidden during an active user shot.
            if (!naturalUserMode && (sceneUnderstanding.detectedVisualEvidence.isNotEmpty() || sceneUnderstanding.userProvidedContext != null)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.56f)
                ) {
                    Text(
                        text = buildString {
                            sceneUnderstanding.userProvidedContext?.let { append("Scene: $it") }
                            if (sceneUnderstanding.detectedVisualEvidence.isNotEmpty()) {
                                if (isNotEmpty()) append("  •  ")
                                append(sceneUnderstanding.detectedVisualEvidence.take(2).joinToString { it.label })
                            }
                        },
                        color = Slate300,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            activeMissionShot?.let { shot ->
                val isOpportunity = shot.status == ShotStatus.OPPORTUNITY
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOpportunity) Rose500.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.52f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOpportunity) Rose500.copy(alpha = 0.65f) else Slate700.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.clickable { onShotPlanClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isOpportunity) "⚡ EVENT" else "COVERAGE",
                            color = if (isOpportunity) Rose500 else Slate400,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (shot.title.contains("Take My Photo", ignoreCase = true)) {
                                shot.title
                            } else {
                                "${shot.shotType.displayName} • ${shot.title}"
                            },
                            color = Slate200,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
