package com.avsp.creator.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avsp.creator.R
import com.avsp.creator.core.common.Resource
import com.avsp.creator.core.theme.*
import com.avsp.creator.domain.model.ProjectStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tech / Education") }
    var targetAudience by remember { mutableStateOf("General Creators") }
    var targetPlatform by remember { mutableStateOf("YouTube") }
    var targetLanguage by remember { mutableStateOf("English") }
    var selectedStatus by remember { mutableStateOf(ProjectStatus.IDEA) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.create_project_title), color = Slate100, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate100)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
            )
        },
        containerColor = Slate950
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Rose500.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = validationError!!,
                        color = Rose500,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                label = { Text(stringResource(id = R.string.title_label), color = Slate400) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text(stringResource(id = R.string.topic_label), color = Slate400) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(id = R.string.category_label), color = Slate400) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = targetAudience,
                onValueChange = { targetAudience = it },
                label = { Text(stringResource(id = R.string.target_audience_label), color = Slate400) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = targetPlatform,
                    onValueChange = { targetPlatform = it },
                    label = { Text(stringResource(id = R.string.target_platform_label), color = Slate400) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Indigo500,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = targetLanguage,
                    onValueChange = { targetLanguage = it },
                    label = { Text(stringResource(id = R.string.target_language_label), color = Slate400) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Indigo500,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Text(
                text = stringResource(id = R.string.status_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400
            )

            // Status Selector Pills
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectStatus.entries.chunked(2).forEach { rowStatuses ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowStatuses.forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text(status.displayName, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Indigo500,
                                    selectedLabelColor = Slate100,
                                    containerColor = Slate900,
                                    labelColor = Slate400
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        validationError = "Project title is required"
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        val result = viewModel.createProject(
                            title = title,
                            topic = topic,
                            category = category,
                            targetAudience = targetAudience,
                            targetPlatform = targetPlatform,
                            targetLanguage = targetLanguage,
                            status = selectedStatus
                        )
                        isSaving = false
                        when (result) {
                            is Resource.Success -> onProjectCreated(result.data)
                            is Resource.Error -> validationError = result.message
                            else -> {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Slate100)
                } else {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.save_project), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
