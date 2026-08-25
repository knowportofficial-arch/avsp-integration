package com.avsp.creator.ui.projects

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
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCreateProject: () -> Unit,
    onNavigateToWorkspace: (String) -> Unit
) {
    val uiState by viewModel.projectListState.collectAsState()
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects Pipeline", color = Slate100, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate100)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreateProject) {
                        Icon(Icons.Default.Add, contentDescription = "Add Project", tint = Indigo500)
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
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Indigo500
                )
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "Failed to load projects",
                    color = Rose500,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val filteredProjects = uiState.projects.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.topic.contains(searchQuery, ignoreCase = true)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        placeholder = { Text("Search projects...", color = Slate400) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Slate400) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Indigo500,
                            unfocusedBorderColor = Slate800,
                            focusedContainerColor = Slate900,
                            unfocusedContainerColor = Slate900,
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate100
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (filteredProjects.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "No projects found in Room DB" else "No matching projects",
                                color = Slate400
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredProjects, key = { it.id }) { project ->
                                ProjectCardItem(
                                    project = project,
                                    onOpen = { onNavigateToWorkspace(project.id) },
                                    onDeleteRequest = { projectToDelete = project }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Delete Dialog
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(id = R.string.confirm_delete_title)) },
            text = { Text(stringResource(id = R.string.confirm_delete_message, project.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(project) {
                            projectToDelete = null
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.delete), color = Rose500)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(stringResource(id = R.string.cancel), color = Slate400)
                }
            },
            containerColor = Slate900,
            titleContentColor = Slate100,
            textContentColor = Slate400
        )
    }
}

@Composable
fun ProjectCardItem(
    project: Project,
    onOpen: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modified: ${dateFormat.format(Date(project.updatedAt))}",
                    fontSize = 11.sp,
                    color = Slate400
                )

                Row {
                    TextButton(onClick = onOpen) {
                        Text("Open", color = Indigo500, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDeleteRequest) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Rose500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
