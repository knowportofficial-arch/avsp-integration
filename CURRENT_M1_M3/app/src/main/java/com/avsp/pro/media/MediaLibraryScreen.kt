package com.avsp.pro.media

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avsp.pro.capture.theme.*
import com.avsp.pro.capture.data.MediaRepository
import com.avsp.pro.capture.database.entity.MediaEntity
import com.avsp.pro.dataset.analyzer.RecommendationPolicy
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(
    projectId: String,
    mediaRepository: MediaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaList by mediaRepository.getMediaForProjectFlow(projectId).collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedShotFilter by remember { mutableStateOf("ALL") }

    val filteredMedia = remember(mediaList, selectedFilter, selectedShotFilter) {
        mediaList.filter { item ->
            val typeMatch = when (selectedFilter) {
                "PHOTOS" -> item.mediaType == "PHOTO"
                "VIDEOS" -> item.mediaType == "VIDEO"
                else -> true
            }
            val shotMatch = selectedShotFilter == "ALL" || item.shotType == selectedShotFilter
            typeMatch && shotMatch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Project Media Library",
                            color = Slate100,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${mediaList.size} captured assets",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close Media Library", tint = Slate100)
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
                .padding(horizontal = 16.dp)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${mediaList.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo500,
                        selectedLabelColor = Slate100
                    )
                )
                FilterChip(
                    selected = selectedFilter == "PHOTOS",
                    onClick = { selectedFilter = "PHOTOS" },
                    label = { Text("Photos (${mediaList.count { it.mediaType == "PHOTO" }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo500,
                        selectedLabelColor = Slate100
                    )
                )
                FilterChip(
                    selected = selectedFilter == "VIDEOS",
                    onClick = { selectedFilter = "VIDEOS" },
                    label = { Text("Videos (${mediaList.count { it.mediaType == "VIDEO" }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo500,
                        selectedLabelColor = Slate100
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL", "WIDE", "MEDIUM", "CLOSE").forEach { shot ->
                    FilterChip(
                        selected = selectedShotFilter == shot,
                        onClick = { selectedShotFilter = shot },
                        label = { Text(shot, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Indigo500.copy(alpha = 0.85f),
                            selectedLabelColor = Slate100
                        )
                    )
                }
            }

            if (filteredMedia.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PermMedia,
                            contentDescription = "No Media",
                            tint = Slate600,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No captured media found",
                            color = Slate400,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Use Field Capture to record shots and photos.",
                            color = Slate600,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMedia) { item ->
                        MediaItemCard(
                            item = item,
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            Uri.parse(item.uriString),
                                            if (item.mediaType == "PHOTO") "image/*" else "video/*"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            onDelete = {
                                scope.launch {
                                    mediaRepository.deleteMedia(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    item: MediaEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.mediaType == "PHOTO") Indigo500.copy(alpha = 0.2f) else Emerald500.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = item.mediaType,
                        color = if (item.mediaType == "PHOTO") Indigo500 else Emerald500,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Rose500,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == "PHOTO") Icons.Default.Image else Icons.Default.PlayCircle,
                    contentDescription = item.displayName,
                    tint = Slate400,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.displayName,
                color = Slate100,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Shot: ${item.shotType}", color = Slate400, fontSize = 10.sp)
                Text(text = "${item.qualityScore}%", color = Emerald500, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            // Derive the display recommendation from the best available persisted metrics.
            // Older M7/M6 rows may have qualityScore populated while the newly-added
            // qualityScoreNormalized column is still 0.0. Do not let that legacy value
            // turn every analyzed row into REVIEW.
            //
            // Rules:
            // 1) Prefer normalized M7 score when it is present.
            // 2) Otherwise, derive from the legacy 0-100 qualityScore when it is present.
            // 3) If neither score is available, preserve the stored REVIEW/RETAKE state
            //    (unanalyzed media must never become KEEP).
            val displayRec = when {
                item.qualityScoreNormalized > 0.0 -> {
                    RecommendationPolicy.decide(
                        score = item.qualityScoreNormalized,
                        blur = item.blurDetected,
                        exposureOk = item.exposureOk,
                        isDuplicate = item.isDuplicate
                    ).name
                }
                item.qualityScore > 0 -> {
                    RecommendationPolicy.decideFromPercent(
                        percent = item.qualityScore,
                        blur = item.blurDetected,
                        exposureOk = item.exposureOk,
                        isDuplicate = item.isDuplicate
                    ).name
                }
                else -> {
                    when (item.recommendation.uppercase(Locale.US)) {
                        "RETAKE" -> "RETAKE"
                        "KEEP" -> "REVIEW"
                        else -> "REVIEW"
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.category.ifBlank { "Uncategorized" },
                    color = Slate400,
                    fontSize = 9.sp
                )
                Text(
                    text = displayRec,
                    color = when (displayRec) {
                        "KEEP" -> Emerald500
                        "RETAKE" -> Rose500
                        else -> Slate400
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (item.isBestShot) {
                Text(
                    text = "★ BEST SHOT",
                    color = Indigo500,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (item.isDuplicate) {
                Text(text = "Duplicate candidate", color = Rose500, fontSize = 9.sp)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(item.createdAt)),
                color = Slate500,
                fontSize = 9.sp
            )
            if (item.latitude != null && item.longitude != null) {
                Text(
                    text = "GPS ${"%.5f".format(Locale.US, item.latitude)}, ${"%.5f".format(Locale.US, item.longitude)}",
                    color = Slate500,
                    fontSize = 9.sp
                )
            }
        }
    }
}
