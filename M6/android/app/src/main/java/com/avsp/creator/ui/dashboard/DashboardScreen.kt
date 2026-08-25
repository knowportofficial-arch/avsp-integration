package com.avsp.creator.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avsp.creator.R
import com.avsp.creator.core.theme.*
import com.avsp.creator.domain.model.Project
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProjects: () -> Unit,
    onNavigateToCreateProject: () -> Unit,
    onNavigateToWorkspace: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AVSP",
                            fontWeight = FontWeight.Black,
                            color = Indigo500,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.dashboard_title),
                            fontSize = 16.sp,
                            color = Slate100
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Slate400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate950
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreateProject,
                icon = { Icon(Icons.Default.Add, contentDescription = "Create Project") },
                text = { Text("New Project") },
                containerColor = Indigo500,
                contentColor = Slate100
            )
        },
        containerColor = Slate950
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Indigo500
                )
            } else if (uiState.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.Center),
                    colors = CardDefaults.cardColors(containerColor = Slate900)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Rose500,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "Database Error",
                            color = Slate100,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Offline Ready Badge Banner
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Slate900
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Offline DB",
                                        tint = Emerald500,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.offline_ready_badge),
                                        fontSize = 12.sp,
                                        color = Slate400,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "v0.1 Foundation",
                                    fontSize = 10.sp,
                                    color = Indigo500,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(id = R.string.todays_production),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        // Production Metrics Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricCard(
                                    title = stringResource(id = R.string.active_projects),
                                    count = uiState.activeProjectsCount,
                                    color = Amber500,
                                    icon = Icons.Default.VideoLibrary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = stringResource(id = R.string.pending_capture),
                                    count = uiState.pendingCaptureCount,
                                    color = Indigo500,
                                    icon = Icons.Default.CameraAlt,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricCard(
                                    title = stringResource(id = R.string.pending_editing),
                                    count = uiState.pendingEditingCount,
                                    color = Amber500,
                                    icon = Icons.Default.Movie,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = stringResource(id = R.string.ready_projects),
                                    count = uiState.readyProjectsCount,
                                    color = Emerald500,
                                    icon = Icons.Default.CheckCircle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.recent_projects),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )
                            TextButton(onClick = onNavigateToProjects) {
                                Text("View All (${uiState.totalProjectsCount})", color = Indigo500)
                            }
                        }
                    }

                    if (uiState.recentProjects.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Slate900)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Empty",
                                        tint = Slate400,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.no_projects_found),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate100
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.create_first_project),
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.recentProjects, key = { it.id }) { project ->
                            ProjectSummaryCard(
                                project = project,
                                onClick = { onNavigateToWorkspace(project.id) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = Slate400,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = count.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ProjectSummaryCard(
    project: Project,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate100,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Indigo500.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = project.status.displayName,
                        fontSize = 10.sp,
                        color = Indigo500,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (project.topic.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = project.topic,
                    fontSize = 12.sp,
                    color = Slate400
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${project.targetPlatform} • ${project.targetLanguage.uppercase()}",
                    fontSize = 11.sp,
                    color = Slate400
                )
                Text(
                    text = dateFormat.format(Date(project.updatedAt)),
                    fontSize = 11.sp,
                    color = Slate400
                )
            }
        }
    }
}
