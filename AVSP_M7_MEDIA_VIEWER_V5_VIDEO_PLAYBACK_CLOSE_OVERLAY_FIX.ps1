$ErrorActionPreference = "Stop"

$root = "C:\AVSP_ALL_ANDROID_M1_M9_INTEGRATED\avsp_android_master"
$file = Join-Path $root "app\src\main\java\com\avsp\pro\ui\screens\media\MediaScreen.kt"

if (-not (Test-Path $file)) { throw "MediaScreen.kt not found: $file" }

$backup = "$file.avsp_m7_viewer_v5_video.bak"
Copy-Item $file $backup -Force

$content = Get-Content $file -Raw
$content = $content.Replace('import android.view.SurfaceView', 'import android.view.TextureView')

$old = @'
@Composable
private fun VideoPreview(asset: MediaAsset) {
    val context = LocalContext.current
    val player = remember(asset.assetId, asset.relativePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(asset.relativePath)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { SurfaceView(it) },
        modifier = Modifier.fillMaxSize().padding(16.dp),
        update = { surfaceView ->
            player.setVideoSurfaceView(surfaceView)
        }
    )
}
'@

$new = @'
@Composable
private fun VideoPreview(asset: MediaAsset) {
    val context = LocalContext.current
    var isPlaying by remember(asset.assetId, asset.relativePath) {
        mutableStateOf(false)
    }

    val player = remember(asset.assetId, asset.relativePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(asset.relativePath)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { TextureView(it) },
            modifier = Modifier.fillMaxSize(),
            update = { textureView ->
                player.setVideoTextureView(textureView)
            }
        )

        IconButton(
            onClick = {
                if (player.isPlaying) player.pause() else player.play()
            },
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Icon(
                imageVector = if (isPlaying) {
                    androidx.compose.material.icons.filled.Pause
                } else {
                    androidx.compose.material.icons.filled.PlayArrow
                },
                contentDescription = if (isPlaying) "Pause video" else "Play video",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
'@

if (-not $content.Contains($old)) {
    throw "Expected V4 VideoPreview block was not found. No source change made."
}

$content = $content.Replace($old, $new)
Set-Content -Path $file -Value $content -Encoding UTF8

Write-Host "AVSP M7 MEDIA VIEWER V5 VIDEO PLAYBACK + CLOSE OVERLAY FIX APPLIED"
Write-Host "Video renderer: TextureView"
Write-Host "Video: autoplay + Play/Pause"
Write-Host "Existing V4 Close control retained"
Write-Host "Photo viewer unchanged"
Write-Host "M7 Media Bridge unchanged"
Write-Host "M1-M4 untouched; M6 untouched"
Write-Host "Backup: $backup"
