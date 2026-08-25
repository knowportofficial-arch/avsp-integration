package com.avsp.pro.core.integration

/**
 * Integration contracts connecting future modules without tight coupling.
 *
 * Master communication principle:
 * - Camera → clip.mp4, thumbnail.jpg, metadata.json
 * - Script → script.json
 * - TTS → voice.mp3, voice.json
 * - Video accepts video/audio assets + script/scene + template
 * - Publishing accepts final.mp4 + metadata + publishing configuration
 */

object ArtifactNames {
    const val CLIP_MP4 = "clip.mp4"
    const val THUMBNAIL_JPG = "thumbnail.jpg"
    const val METADATA_JSON = "metadata.json"
    const val SCRIPT_JSON = "script.json"
    const val VOICE_MP3 = "voice.mp3"
    const val VOICE_JSON = "voice.json"
    const val FINAL_MP4 = "final.mp4"
    const val TEMPLATE_JSON = "template.json"
}

/**
 * Relative project layout used by all modules. Paths are always relative to a project root.
 */
object ProjectPaths {
    const val ORIGINALS = "originals"
    const val WORKING = "working"
    const val GENERATED = "generated"
    const val PUBLISHED = "published"
    const val LOGS = "logs"
    const val SCRIPT = "generated/script"
    const val AUDIO = "generated/audio"
    const val VIDEO = "generated/video"
    const val CAMERA = "originals/camera"
    const val MEDIA = "originals/media"
}

data class CameraArtifactBundle(
    val projectId: String,
    val clipRelativePath: String,
    val thumbnailRelativePath: String,
    val metadataRelativePath: String
)

data class ScriptArtifactBundle(
    val projectId: String,
    val scriptRelativePath: String,
    val language: String
)

data class TtsArtifactBundle(
    val projectId: String,
    val voiceRelativePath: String,
    val voiceMetaRelativePath: String,
    val language: String,
    val durationSeconds: Double? = null
)

data class VideoAssemblerInput(
    val projectId: String,
    val videoAssets: List<String>,
    val audioAssets: List<String>,
    val imageAssets: List<String> = emptyList(),
    val scriptRelativePath: String? = null,
    val templateRelativePath: String? = null,
    val subtitleRelativePath: String? = null
)

data class FinalVideoOutput(
    val projectId: String,
    val fileRelativePath: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoCodec: String,
    val audioCodec: String,
    val durationSeconds: Double,
    val status: String
)

data class PublishingInput(
    val projectId: String,
    val videoRelativePath: String,
    val title: String,
    val description: String,
    val language: String,
    val platforms: List<String>,
    val thumbnailRelativePath: String? = null
)

data class PublishingOutput(
    val platform: String,
    val status: String,
    val remoteId: String? = null,
    val publishedAt: String? = null,
    val error: String? = null
)

/**
 * Extension points for future providers (interfaces only — not implemented in M1).
 */
interface TtsProvider {
    val providerId: String
    val displayName: String
}

interface PublishingPlatformAdapter {
    val platformId: String
    val displayName: String
}

interface OcrEngine {
    val engineId: String
    val displayName: String
}

interface AiModelProvider {
    val modelId: String
    val displayName: String
}

interface AutomationTrigger {
    val triggerId: String
    val displayName: String
}
