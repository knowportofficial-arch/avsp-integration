package com.avsp.pro.ui.screens.media
import android.provider.MediaStore
import androidx.media3.common.PlaybackException

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.ui.components.EmptyState
import com.avsp.pro.ui.components.ErrorState
import com.avsp.pro.ui.components.LoadingState
import com.avsp.pro.ui.viewmodel.MediaViewModel

@Composable
fun MediaScreen(viewModel: MediaViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedAsset by remember { mutableStateOf<MediaAsset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Media / Assets", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Project media and bundled M4 QA source clips appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            when (val s = state) {
                is UiState.Idle, is UiState.Loading ->
                    LoadingState("Loading media inventoryâ€¦")

                is UiState.Error ->
                    ErrorState(s.message, onRetry = viewModel::refresh)

                is UiState.Success -> {
                    val totalAssets = s.data.assetsByProject.values.sumOf { it.size }

                    if (s.data.projects.isEmpty()) {
                        EmptyState(
                            title = "No projects",
                            message = "Create a project first. Media folders are created per project.",
                            actionLabel = "Refresh",
                            onAction = viewModel::refresh
                        )
                    } else if (totalAssets == 0) {
                        EmptyState(
                            title = "No assets yet",
                            message = "Bundled M4 QA clips are imported automatically for render testing.",
                            actionLabel = "Refresh",
                            onAction = viewModel::refresh
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            s.data.projects.forEach { project ->
                                item(key = "project_${project.projectId}") {
                                    Column {
                                        Text(
                                            project.name,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        val assets =
                                            s.data.assetsByProject[project.projectId].orEmpty()

                                        if (assets.isEmpty()) {
                                            Text(
                                                "No assets",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        } else {
                                            assets.forEach { asset ->
                                                MediaAssetRow(
                                                    asset = asset,
                                                    onClick = { selectedAsset = asset }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = viewModel::refresh) {
                        Text("Refresh")
                    }
                }
            }
        }

        selectedAsset?.let { asset ->
            MediaViewerOverlay(
                asset = asset,
                onClose = { selectedAsset = null }
            )
        }
    }
}

@Composable
private fun MediaAssetRow(
    asset: MediaAsset,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(asset.fileName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${asset.mimeType} Â· ${asset.relativePath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun MediaViewerOverlay(
    asset: MediaAsset,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.96f))
    ) {
        

        when {
            asset.mimeType.startsWith("image/") ->
                PhotoPreview(asset)

            asset.mimeType.startsWith("video/") ->
                VideoPreview(asset)

            else ->
                Text(
                    "Preview not supported for ${asset.mimeType}",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface
                )
        }
    

IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(52.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close viewer",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
}
}

private fun resolveMediaUri(context: android.content.Context, asset: MediaAsset): Uri? {
    val raw = asset.relativePath.trim()
    if (raw.isNotBlank()) {
        val direct = runCatching {
            when {
                raw.startsWith("content://", true) -> Uri.parse(raw)
                raw.startsWith("file://", true) -> Uri.parse(raw)
                raw.startsWith("http://", true) || raw.startsWith("https://", true) -> Uri.parse(raw)
                java.io.File(raw).exists() -> Uri.fromFile(java.io.File(raw))
                else -> null
            }
        }.getOrNull()

        if (direct != null) {
            val usable = runCatching {
                context.contentResolver.openAssetFileDescriptor(direct, "r")?.use { true } ?: false
            }.getOrDefault(false)
            if (usable) return direct
        }
    }

    // Legacy MediaStore recovery by display name.
    val name = asset.fileName.trim()
    if (name.isNotBlank()) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        for (collection in collections) {
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(name),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        return collection.buildUpon().appendPath(id.toString()).build()
                    }
                }
            }
        }
    }

    return null
}
@Composable
private fun PhotoPreview(asset: MediaAsset) {
    val context = LocalContext.current
    var bitmap by remember(asset.assetId, asset.relativePath) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    var error by remember(asset.assetId, asset.relativePath) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(asset.assetId, asset.relativePath) {
        bitmap = null
        error = null
        val uri = resolveMediaUri(context, asset)
        if (uri == null) {
            error = "Image not found: ${asset.fileName}"
            return@LaunchedEffect
        }

        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        }.onSuccess { decoded ->
            if (decoded == null) {
                error = "Unable to decode image: ${asset.fileName}"
            } else {
                bitmap = decoded
            }
        }.onFailure {
            error = "Unable to open image: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = asset.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            error != null -> Text(
                text = error!!,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            else -> CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun VideoPreview(asset: MediaAsset) {
    val context = LocalContext.current
    var isPlaying by remember(asset.assetId, asset.relativePath) {
        mutableStateOf(false)
    }
    var ended by remember(asset.assetId, asset.relativePath) {
        mutableStateOf(false)
    }
    var error by remember(asset.assetId, asset.relativePath) {
        mutableStateOf<String?>(null)
    }

    val mediaUri = remember(asset.assetId, asset.relativePath, asset.fileName) {
        resolveMediaUri(context, asset)
    }

    val player = remember(asset.assetId, asset.relativePath, asset.fileName) {
        ExoPlayer.Builder(context).build().apply {
            if (mediaUri == null) {
                error = "Video not found: ${asset.fileName}"
            } else {
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            ended = true
                            isPlaying = false
                        }
                    }

                    override fun onPlayerError(playbackException: PlaybackException) {
                        error = "Unable to play video: ${playbackException.message ?: playbackException.errorCodeName}"
                        isPlaying = false
                    }
                })

                prepare()
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AndroidView(
            factory = { android.view.TextureView(it) },
            modifier = Modifier.fillMaxSize(),
            update = { textureView ->
                player.setVideoTextureView(textureView)
            }
        )

        if (error != null) {
            Text(
                text = error!!,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            IconButton(
                onClick = {
                    if (ended || player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                        ended = false
                        player.playWhenReady = true
                        player.play()
                    } else if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                androidx.compose.material3.Text(
                    text = if (isPlaying) "||" else ">",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}
