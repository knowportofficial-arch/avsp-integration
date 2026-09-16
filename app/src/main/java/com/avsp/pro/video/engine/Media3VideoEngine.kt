package com.avsp.pro.video.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea
import com.avsp.pro.video.contract.VideoRenderPlan
import com.avsp.pro.video.contract.VideoRenderResult
import com.avsp.pro.video.validation.VideoValidator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class Media3VideoEngine(
    private val context: Context,
    private val storage: AvspStorage
) : VideoEngine {
    private val progress = MutableSharedFlow<Int>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var transformer: Transformer? = null
    @Volatile private var cancelled = false

    override fun observeProgress(): Flow<Int> = progress

    override fun cancel() {
        cancelled = true
        transformer?.cancel()
    }

    override suspend fun render(plan: VideoRenderPlan): VideoRenderResult {
        val validation = VideoValidator.validatePlan(plan)
        require(validation.isValid) { validation.errors.joinToString("; ") }
        cancelled = false
        progress.emit(0)
        storage.ensureProjectLayout(plan.projectId)

        val placeholder = createPlaceholder(plan)
        val videoItems = mutableListOf<EditedMediaItem>()
        plan.scenes.sortedBy { it.order }.forEach { scene ->
            check(!cancelled) { "M4 render cancelled" }
            val visual = scene.visualRelativePath?.let { File(storage.resolve(StorageArea.PROJECT_DATA, it, plan.projectId)) }
                ?.takeIf { it.exists() } ?: placeholder
            val uri = Uri.fromFile(visual)
            val isVideo = scene.visualMimeType?.startsWith("video/") == true

            val videoPieces = if (isVideo) {
                val sourceDurationMs = inspectSourceDuration(visual)
                VideoClipPlanner.pieces(sourceDurationMs, scene.durationMs)
            } else {
                listOf(VideoClipPlanner.Piece(0L, scene.durationMs, scene.durationMs))
            }

            videoPieces.forEach { piece ->
                val item = if (isVideo) {
                    MediaItem.Builder()
                        .setUri(uri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(piece.startMs)
                                .setEndPositionMs(piece.endMs)
                                .build()
                        )
                        .build()
                } else {
                    MediaItem.Builder()
                        .setUri(uri)
                        .setImageDurationMs(piece.durationMs)
                        .build()
                }
                val videoEffects = mutableListOf<androidx.media3.common.Effect>(
                    Presentation.createForWidthAndHeight(
                        plan.width,
                        plan.height,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )
                scene.overlayText?.takeIf { it.isNotBlank() }?.let { text ->
                    videoEffects += OverlayEffect(
                        listOf(TextOverlay.createStaticTextOverlay(SpannableString(text)))
                    )
                }
                videoItems += EditedMediaItem.Builder(item)
                    .setFrameRate(plan.fps)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }
        }
        val audioItems = plan.scenes.sortedBy { it.order }.map { scene ->
            val audio = File(storage.resolve(StorageArea.PROJECT_DATA, scene.audioRelativePath, plan.projectId))
            require(audio.exists()) { "Missing audio: ${scene.audioRelativePath}" }
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio))).build()
        }

        val videoSequence = EditedMediaItemSequence.withVideoFrom(videoItems)
        val audioSequence = EditedMediaItemSequence.withAudioFrom(audioItems)
        val composition = Composition.Builder(videoSequence, audioSequence).build()
        val outputRelative = "${ProjectPaths.VIDEO}/final.mp4"
        val output = File(storage.resolve(StorageArea.PROJECT_DATA, outputRelative, plan.projectId))
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val result = suspendCancellableCoroutine<ExportResult> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(exportResult)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            val t = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                // Never silently change the project-selected output resolution.
                // If the device cannot encode it, fail explicitly so the user gets
                // a real M4 error instead of an unexpected fallback canvas.
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setEnableFallback(false)
                        .build()
                )
                .addListener(listener)
                .build()
            transformer = t
            continuation.invokeOnCancellation { t.cancel() }
            t.start(composition, output.absolutePath)
        }
        progress.emit(100)
        val actual = inspectOutput(output)
        val outputValidation = VideoValidator.validateOutput(
            plan.width, plan.height, plan.totalDurationMs,
            actual.width, actual.height, actual.durationMs,
            actual.hasVideo, actual.hasAudio, actual.rotationDegrees
        )
        require(outputValidation.isValid) { outputValidation.errors.joinToString("; ") }
        return VideoRenderResult(
            projectId = plan.projectId,
            outputRelativePath = outputRelative,
            outputAbsolutePath = output.absolutePath,
            durationMs = actual.durationMs,
            width = actual.displayWidth,
            height = actual.displayHeight,
            videoCodec = actual.videoCodec,
            audioCodec = actual.audioCodec,
            status = "SUCCESS"
        )
    }

    private fun inspectSourceDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: error("Unable to determine source video duration: ${file.name}")
        } finally {
            retriever.release()
        }
    }

    private fun createPlaceholder(plan: VideoRenderPlan): File {
        val dir = File(context.cacheDir, "m4")
        dir.mkdirs()
        val file = File(dir, "placeholder_${plan.width}x${plan.height}.png")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = plan.width * 0.055f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("AVSP", plan.width / 2f, plan.height / 2f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private data class OutputInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val rotationDegrees: Int,
        val videoCodec: String,
        val audioCodec: String
    ) {
        val displayWidth: Int
            get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val displayHeight: Int
            get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
    }

    private fun inspectOutput(file: File): OutputInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            // Media3 may encode a portrait canvas as width×height in the
            // codec's natural orientation and carry the presentation
            // orientation separately as 90°/270° metadata. Validation must
            // use display dimensions, not raw encoded dimensions.
            val rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
            val hasVideo = width > 0 && height > 0
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val videoCodec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
            val audioCodec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
            OutputInfo(
                durationMs = duration,
                width = width,
                height = height,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                rotationDegrees = normalizedRotation,
                videoCodec = videoCodec,
                audioCodec = audioCodec
            )
        } finally {
            retriever.release()
        }
    }
}
