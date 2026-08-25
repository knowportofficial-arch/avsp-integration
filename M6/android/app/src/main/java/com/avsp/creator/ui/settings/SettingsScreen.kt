package com.avsp.creator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.avsp.creator.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = AppPreferences.theme
    val accent = AppPreferences.accent
    val language = AppPreferences.language
    val voiceEnabled = AppPreferences.voiceGuidanceEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance & Guidance", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsCard(title = "Appearance", icon = Icons.Default.Palette) {
                Text("Theme", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppThemeOption.SYSTEM to "System",
                        AppThemeOption.LIGHT to "Light",
                        AppThemeOption.DARK to "Dark"
                    ).forEach { (option, label) ->
                        FilterChip(
                            selected = theme == option,
                            onClick = { AppPreferences.setTheme(context, option) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Accent colour", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppAccentOption.entries.forEach { option ->
                        val color = when (option) {
                            AppAccentOption.BLUE -> Indigo500
                            AppAccentOption.CYAN -> Color(0xFF06B6D4)
                            AppAccentOption.EMERALD -> Emerald500
                            AppAccentOption.AMBER -> Amber500
                            AppAccentOption.ROSE -> Rose500
                        }
                        Box(
                            modifier = Modifier.size(40.dp).background(color, CircleShape)
                                .clickable { AppPreferences.setAccent(context, option) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (accent == option) Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
                Text(accent.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }

            SettingsCard(title = "AI Voice Guidance", icon = Icons.Default.RecordVoiceOver) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Cinematographer voice", fontWeight = FontWeight.Bold)
                        Text("Speak only important framing and capture instructions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { AppPreferences.setVoiceGuidance(context, it) }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Voice language", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                AppLanguageOption.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { AppPreferences.setLanguage(context, option) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == option, onClick = { AppPreferences.setLanguage(context, option) })
                        Text(option.displayName)
                    }
                }
            }

            SettingsCard(title = "AVSP Status", icon = Icons.Default.Info) {
                Text("Package: com.avsp.creator", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("AI Cinematographer: ML Kit + evidence-driven ranking", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("Database: Room SQLite (Offline-First)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        })
    }
}
