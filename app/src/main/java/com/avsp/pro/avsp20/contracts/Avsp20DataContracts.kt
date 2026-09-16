
package com.avsp.pro.avsp20.contracts

/**
 * AVSP 2.0 Session 02 canonical cross-module data contracts.
 *
 * IMPORTANT:
 * - This package is additive.
 * - Historical M1-M10 contracts are not modified by this session.
 * - M1-M4 remain protected.
 * - Persistence/serialization adapters are intentionally deferred to later sessions.
 */

const val AVSP20_CONTRACT_FAMILY_VERSION = "2.0"
const val AVSP20_SCHEMA_VERSION = 1

@JvmInline
value class AvspId(val value: String)

enum class ContractType {
    PROJECT, SCRIPT, AUDIO, SCENE, ASSET, TIMELINE, RENDER, QC, PUBLISH, ANALYTICS
}

enum class MediaKind { IMAGE, VIDEO, AUDIO, DOCUMENT, SCREENSHOT, UNKNOWN }

enum class ScriptLanguage { BN, HI, EN, OTHER }

enum class SceneRole { HOOK, INTRO, BODY, BROLL, TRANSITION, CTA, OUTRO, OTHER }

enum class TimelineTrackType { VIDEO, IMAGE, AUDIO, VOICE, MUSIC, SFX, CAPTION, GRAPHIC }

enum class RenderStatus { DRAFT, QUEUED, RENDERING, SUCCESS, FAILED, CANCELLED }

enum class QcStatus { NOT_RUN, PASS, WARN, FAIL }

enum class PublishPlatform { YOUTUBE, FACEBOOK, OTHER }

enum class PublishStatus { DRAFT, QUEUED, UPLOADING, PUBLISHED, FAILED, CANCELLED }

data class ContractHeader(
    val contractType: ContractType,
    val contractVersion: String = AVSP20_CONTRACT_FAMILY_VERSION,
    val schemaVersion: Int = AVSP20_SCHEMA_VERSION,
    val id: AvspId,
    val projectId: AvspId,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class ProjectContract(
    val header: ContractHeader,
    val name: String,
    val language: ScriptLanguage,
    val aspectRatio: String,
    val status: String,
    val sourceTopic: String? = null
)

data class ScriptSegment(
    val segmentId: AvspId,
    val order: Int,
    val text: String,
    val language: ScriptLanguage,
    val durationMs: Long? = null,
    val pronunciationHint: String? = null,
    val sceneId: AvspId? = null
)

data class ScriptContract(
    val header: ContractHeader,
    val title: String,
    val language: ScriptLanguage,
    val segments: List<ScriptSegment>,
    val totalDurationMs: Long? = null
)

data class AudioContract(
    val header: ContractHeader,
    val sourceScriptId: AvspId,
    val uri: String,
    val language: ScriptLanguage,
    val durationMs: Long,
    val sampleRateHz: Int,
    val channels: Int,
    val voiceId: String? = null
)

data class SceneContract(
    val header: ContractHeader,
    val sceneId: AvspId,
    val order: Int,
    val role: SceneRole,
    val scriptSegmentId: AvspId? = null,
    val narrationStartMs: Long? = null,
    val narrationEndMs: Long? = null,
    val visualIntent: String = "",
    val requiredAssetKinds: List<MediaKind> = emptyList()
)

data class AssetContract(
    val header: ContractHeader,
    val uri: String,
    val fileName: String,
    val mediaKind: MediaKind,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val source: String? = null,
    val license: String? = null,
    val sourceUrl: String? = null,
    val tags: List<String> = emptyList()
)

data class TimelineItem(
    val itemId: AvspId,
    val trackType: TimelineTrackType,
    val assetId: AvspId? = null,
    val sceneId: AvspId? = null,
    val startMs: Long,
    val durationMs: Long,
    val zIndex: Int = 0,
    val enabled: Boolean = true
)

data class TimelineContract(
    val header: ContractHeader,
    val items: List<TimelineItem>,
    val durationMs: Long,
    val frameRate: Double,
    val widthPx: Int,
    val heightPx: Int
)

data class RenderContract(
    val header: ContractHeader,
    val timelineId: AvspId,
    val outputUri: String? = null,
    val widthPx: Int,
    val heightPx: Int,
    val frameRate: Double,
    val codec: String,
    val audioCodec: String? = null,
    val status: RenderStatus = RenderStatus.DRAFT,
    val errorCode: String? = null
)

data class QcCheck(
    val checkId: String,
    val name: String,
    val passed: Boolean,
    val severity: String = "INFO",
    val details: String? = null
)

data class QcContract(
    val header: ContractHeader,
    val renderId: AvspId,
    val status: QcStatus,
    val checks: List<QcCheck>,
    val durationMs: Long? = null,
    val notes: String? = null
)

data class PublishTarget(
    val platform: PublishPlatform,
    val accountRef: String? = null,
    val visibility: String = "PRIVATE"
)

data class PublishContract(
    val header: ContractHeader,
    val renderId: AvspId,
    val target: PublishTarget,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val status: PublishStatus = PublishStatus.DRAFT,
    val externalId: String? = null,
    val errorCode: String? = null
)

data class AnalyticsMetric(
    val name: String,
    val value: Double,
    val unit: String? = null
)

data class AnalyticsContract(
    val header: ContractHeader,
    val publishId: AvspId,
    val platform: PublishPlatform,
    val capturedAtEpochMs: Long,
    val metrics: List<AnalyticsMetric> = emptyList()
)

object Avsp20ContractRegistry {
    const val VERSION = AVSP20_CONTRACT_FAMILY_VERSION
    const val SCHEMA = AVSP20_SCHEMA_VERSION

    val supportedTypes: Set<ContractType> = ContractType.entries.toSet()

    fun isSupported(header: ContractHeader): Boolean =
        header.contractVersion == VERSION &&
            header.schemaVersion == SCHEMA &&
            header.contractType in supportedTypes
}

