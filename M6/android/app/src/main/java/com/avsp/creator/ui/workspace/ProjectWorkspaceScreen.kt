package com.avsp.creator.ui.workspace

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avsp.creator.R
import com.avsp.creator.core.theme.*
import com.avsp.creator.core.common.Resource
import com.avsp.creator.ui.projects.ProjectViewModel

data class WorkspaceModule(
    val name: String,
    val icon: ImageVector,
    val description: String,
    val isPlaceholder: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceScreen(
    projectId: String,
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit = {},
    onNavigateToMediaLibrary: (String) -> Unit = {}
) {
    val selectedState by viewModel.selectedProjectState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val modules = remember {
        listOf(
            WorkspaceModule("Source Research", Icons.Default.Search, "Source verification, fact extraction & outline"),
            WorkspaceModule("Script Studio", Icons.Default.Description, "Hook, narrative flow & multi-lingual versioning"),
            WorkspaceModule("Voice Studio", Icons.Default.Mic, "Narration profiles, speech synthesis & timeline"),
            WorkspaceModule("Field Capture", Icons.Default.CameraAlt, "CameraX, AI cinematographer HUD & shot missions", isPlaceholder = false),
            WorkspaceModule("Media Library", Icons.Default.PermMedia, "Local asset management, quality scoring & review", isPlaceholder = false),
            WorkspaceModule("Scene Planner", Icons.Default.ViewList, "Visual shot assignment & B-roll mapping"),
            WorkspaceModule("Video Timeline", Icons.Default.Movie, "Desktop engine render preview & sequence"),
            WorkspaceModule("Captions & Subtitles", Icons.Default.Subtitles, "Multi-pass caption burning & timing"),
            WorkspaceModule("Thumbnail Concept", Icons.Default.Image, "AI thumbnail specs & visual concepting"),
            WorkspaceModule("Publishing Package", Icons.Default.Publish, "SEO titles, descriptions & YouTube chapters")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.project_workspace), color = Slate100, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate100)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { onNavigateToCamera(projectId) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Indigo500,
                            contentColor = Slate100
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.open_camera),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
            )
        },
        containerColor = Slate950
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = selectedState) {
                is Resource.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Indigo500
                    )
                }
                is Resource.Error -> {
                    Text(
                        text = state.message,
                        color = Rose500,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is Resource.Success -> {
                    val project = state.data
                    if (project == null) {
                        Text(
                            text = "Project not found",
                            color = Slate400,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                // Project Header Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Slate900),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Indigo500.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = project.status.displayName,
                                                fontSize = 11.sp,
                                                color = Indigo500,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = project.title,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate100
                                        )
                                        if (project.topic.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = project.topic,
                                                fontSize = 13.sp,
                                                color = Slate400
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text(
                                                text = "Platform: ${project.targetPlatform}",
                                                fontSize = 11.sp,
                                                color = Slate400
                                            )
                                            Text(
                                                text = "Lang: ${project.targetLanguage.uppercase()}",
                                                fontSize = 11.sp,
                                                color = Slate400
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "Workspace Production Modules",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100
                                )
                            }

                            items(modules) { module ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Slate900),
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = {
                                        if (!module.isPlaceholder) {
                                            if (module.name == "Media Library") {
                                                onNavigateToMediaLibrary(projectId)
                                            } else {
                                                onNavigateToCamera(projectId)
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = module.icon,
                                                contentDescription = module.name,
                                                tint = Indigo500,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = module.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate100
                                                )
                                                Text(
                                                    text = module.description,
                                                    fontSize = 11.sp,
                                                    color = Slate400
                                                )
                                            }
                                        }
                                        if (module.isPlaceholder) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Slate800
                                            ) {
                                                Text(
                                                    text = stringResource(id = R.string.future_milestone_notice),
                                                    fontSize = 9.sp,
                                                    color = Amber500,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Indigo500.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = if (module.name == "Media Library") "VIEW" else stringResource(id = R.string.open_camera),
                                                    fontSize = 11.sp,
                                                    color = Indigo500,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
