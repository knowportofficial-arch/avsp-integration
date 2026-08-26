package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.analyzer.ShotReadinessStatus
import com.avsp.pro.dataset.model.Recommendation

enum class GuidedCapturePhase {
    PERMISSION_REQUIRED,
    READY,
    COUNTDOWN,
    RECORDING,
    REVIEW,
    SAVING,
    ERROR,
    COMPLETE
}

/**
 * Live guidance derived from existing ML Kit + cinematographer decision engines.
 * Not "ready" merely because the camera preview is open.
 */
data class GuidedLiveGuidance(
    val isReady: Boolean = false,
    val status: ShotReadinessStatus = ShotReadinessStatus.NOT_READY,
    val headline: String = "NOT READY",
    val message: String = "Point the camera toward the intended subject.",
    val isSubjectMatched: Boolean = false,
    val isFramingAcceptable: Boolean = false,
    val isStable: Boolean = false,
    val requestedZoom: Float = 1.0f,
    val readinessScore: Int = 0
)

data class GuidedCaptureState(
    val template: GuidedCaptureTemplate? = null,
    val currentClipIndex: Int = 0,
    val phase: GuidedCapturePhase = GuidedCapturePhase.PERMISSION_REQUIRED,

    val countdownSecondsRemaining: Int = 0,
    val elapsedMs: Long = 0L,
    val targetDurationMs: Long = 0L,

    val isFlashOn: Boolean = false,
    val isGridEnabled: Boolean = true,
    val deviceOrientationDegrees: Int = 0,
    val exposureIndex: Int = 0,
    val exposureRange: IntRange = 0..0,

    val lastRecordedFile: String? = null,
    val lastRecordedThumbnail: String? = null,

    /** Post-capture M7 recommendation shown in REVIEW (KEEP / REVIEW / RETAKE). */
    val lastRecommendation: Recommendation? = null,
    val lastQualityPercent: Int = 0,

    val liveGuidance: GuidedLiveGuidance = GuidedLiveGuidance(),

    val planTitle: String = "",
    val missionId: String? = null,

    val completedClips: List<ClipMetadata> = emptyList(),
    val errorMessage: String? = null
) {
    val currentClip: GuidedClipSpec?
        get() = template?.clips?.getOrNull(currentClipIndex)

    val progressFraction: Float
        get() = if (targetDurationMs <= 0L) 0f else (elapsedMs.toFloat() / targetDurationMs.toFloat()).coerceIn(0f, 1f)

    val isLastClip: Boolean
        get() = template != null && currentClipIndex == template.clips.lastIndex

    val shotProgressLabel: String
        get() {
            val total = template?.clips?.size ?: 0
            if (total <= 0) return ""
            return "Shot ${currentClipIndex + 1} of $total"
        }
}
