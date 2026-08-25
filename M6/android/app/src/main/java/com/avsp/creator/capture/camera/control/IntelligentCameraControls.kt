package com.avsp.creator.capture.camera.control

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avsp.creator.capture.camera.model.CameraCapabilities
import com.avsp.creator.capture.camera.model.CameraControlMode
import com.avsp.creator.capture.camera.model.CameraFrameRate
import com.avsp.creator.capture.camera.model.CameraProfile
import com.avsp.creator.capture.camera.model.CameraResolution
import com.avsp.creator.capture.camera.model.CameraShotType
import com.avsp.creator.core.theme.*

@Composable
fun IntelligentCameraControls(
    profile: CameraProfile,
    capabilities: CameraCapabilities,
    isTorchOn: Boolean,
    isRecording: Boolean,
    isAutoCaptureEnabled: Boolean,
    onShotTypeSelected: (CameraShotType) -> Unit,
    onResolutionSelected: (CameraResolution) -> Unit,
    onFrameRateSelected: (CameraFrameRate) -> Unit,
    onToggleControlMode: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleHorizon: () -> Unit,
    onOpenMissionDialog: () -> Unit,
    onOpenEventDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpandedSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Secondary Bar (AI Plan, Live Event, Auto-Shutter, Auto/Manual, Grid, Level, Torch, Format)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AI Shot Mission Button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Indigo500.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenMissionDialog() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Plan",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "AI PLAN",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // ⚡ Live Event / Opportunity Reporter Button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Rose500.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenEventDialog() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Live Event",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "EVENT",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Auto-Shutter Toggle Chip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isAutoCaptureEnabled) Emerald500.copy(alpha = 0.25f) else Slate800,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleAutoCapture() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isAutoCaptureEnabled) Icons.Default.CameraAlt else Icons.Default.NoPhotography,
                        contentDescription = "Auto Shutter",
                        tint = if (isAutoCaptureEnabled) Emerald500 else Slate400,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isAutoCaptureEnabled) "AUTO-SHOT ON" else "AUTO-SHOT OFF",
                        color = if (isAutoCaptureEnabled) Emerald500 else Slate400,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // AUTO / MANUAL mode toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (profile.controlMode == CameraControlMode.AUTO) Emerald500.copy(alpha = 0.2f) else Slate800,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleControlMode() }
            ) {
                Text(
                    text = profile.controlMode.displayName,
                    color = if (profile.controlMode == CameraControlMode.AUTO) Emerald500 else Slate400,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Grid Toggle
            IconButton(
                onClick = onToggleGrid,
                modifier = Modifier
                    .size(32.dp)
                    .background(if (profile.isGridOverlayEnabled) Indigo500.copy(alpha = 0.3f) else Slate900, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Grid",
                    tint = if (profile.isGridOverlayEnabled) Indigo500 else Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Horizon Level Toggle
            IconButton(
                onClick = onToggleHorizon,
                modifier = Modifier
                    .size(32.dp)
                    .background(if (profile.isHorizonLevelEnabled) Indigo500.copy(alpha = 0.3f) else Slate900, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.HorizontalRule,
                    contentDescription = "Level",
                    tint = if (profile.isHorizonLevelEnabled) Indigo500 else Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (capabilities.hasTorch) {
                Spacer(modifier = Modifier.width(6.dp))
                // Torch Toggle
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (isTorchOn) Amber500.copy(alpha = 0.3f) else Slate900, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = if (isTorchOn) Amber500 else Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Settings Pill Toggle (Resolution & FPS)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isExpandedSettings) Slate800 else Slate900,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { isExpandedSettings = !isExpandedSettings }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Format",
                        tint = Slate400,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${profile.resolution.displayName} • ${profile.frameRate.displayName}",
                        color = Slate300,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Expanded Format Controls (Resolution & FrameRate)
        AnimatedVisibility(visible = isExpandedSettings && !isRecording) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate900.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Resolution selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Resolution:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CameraResolution.values().forEach { res ->
                                val isSupported = capabilities.isResolutionSupported(res)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (profile.resolution == res) Indigo500 else if (isSupported) Slate800 else Slate900,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = isSupported) { onResolutionSelected(res) }
                                ) {
                                    Text(
                                        text = res.displayName,
                                        color = if (profile.resolution == res) Color.White else if (isSupported) Slate300 else Slate600,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // FrameRate selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frame Rate:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CameraFrameRate.values().forEach { fps ->
                                val isSupported = capabilities.isFrameRateSupported(fps)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (profile.frameRate == fps) Indigo500 else if (isSupported) Slate800 else Slate900,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = isSupported) { onFrameRateSelected(fps) }
                                ) {
                                    Text(
                                        text = fps.displayName,
                                        color = if (profile.frameRate == fps) Color.White else if (isSupported) Slate300 else Slate600,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shot Type Segmented Selector: WIDE (1x) | MEDIUM (1.8x) | CLOSE (3x)
        if (!isRecording) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Slate900.copy(alpha = 0.88f),
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraShotType.values().forEach { shotType ->
                        val isSelected = profile.shotType == shotType
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) Indigo500 else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onShotTypeSelected(shotType) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = shotType.displayName.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Slate400
                                )
                                Text(
                                    text = "${shotType.defaultZoomRatio}x",
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 9.sp,
                                    color = if (isSelected) Slate200 else Slate500
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
