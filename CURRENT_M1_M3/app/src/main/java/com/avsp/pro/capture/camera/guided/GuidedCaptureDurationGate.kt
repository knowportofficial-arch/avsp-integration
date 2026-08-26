package com.avsp.pro.capture.camera.guided

import com.avsp.pro.dataset.model.Recommendation

/**
 * Gates KEEP eligibility for Guided Capture video by the shot-plan required duration.
 *
 * KEEP must never become available merely because recording started or a media file
 * was created. For video, the recorded duration must meet [requiredDurationMs] from
 * the shot metadata before KEEP can be offered after quality analysis.
 *
 * Photos are unaffected (no minimum duration gate).
 */
object GuidedCaptureDurationGate {

    /** Required duration from clip metadata. Photos → 0 (no gate). */
    fun requiredDurationMs(clip: GuidedClipSpec): Long =
        when (clip.mediaType) {
            GuidedClipMediaType.PHOTO -> 0L
            GuidedClipMediaType.VIDEO ->
                clip.targetDurationSeconds.toLong().coerceAtLeast(1L) * 1000L
        }

    /**
     * True when recorded media has met the shot-plan duration.
     * [requiredDurationMs] ≤ 0 means no gate (photos).
     */
    fun hasMetRequiredDuration(requiredDurationMs: Long, actualDurationMs: Long): Boolean {
        if (requiredDurationMs <= 0L) return true
        return actualDurationMs >= requiredDurationMs
    }

    /**
     * KEEP is eligible only after required duration is met AND quality recommends KEEP.
     * Early / short takes never expose KEEP — even if quality would otherwise say KEEP.
     */
    fun isKeepEligible(
        mediaType: GuidedClipMediaType,
        requiredDurationMs: Long,
        actualDurationMs: Long,
        qualityRecommendation: Recommendation?
    ): Boolean {
        if (mediaType != GuidedClipMediaType.VIDEO) {
            return qualityRecommendation == Recommendation.KEEP
        }
        if (!hasMetRequiredDuration(requiredDurationMs, actualDurationMs)) return false
        return qualityRecommendation == Recommendation.KEEP
    }

    /**
     * Resolves the post-capture recommendation shown in REVIEW / insufficient-duration UX.
     * Short video takes are forced to RETAKE (never KEEP).
     */
    fun resolveRecommendation(
        mediaType: GuidedClipMediaType,
        requiredDurationMs: Long,
        actualDurationMs: Long,
        qualityRecommendation: Recommendation
    ): Recommendation {
        if (mediaType == GuidedClipMediaType.VIDEO &&
            !hasMetRequiredDuration(requiredDurationMs, actualDurationMs)
        ) {
            return Recommendation.RETAKE
        }
        return qualityRecommendation
    }

    fun insufficientDurationMessage(requiredDurationMs: Long, actualDurationMs: Long): String {
        val needSec = ((requiredDurationMs + 999L) / 1000L).coerceAtLeast(1L)
        val gotSec = (actualDurationMs / 1000L).coerceAtLeast(0L)
        val gotTenths = (actualDurationMs % 1000L) / 100L
        val gotLabel = if (actualDurationMs < 1000L) {
            "0.${gotTenths}s"
        } else {
            "${gotSec}s"
        }
        return "Insufficient duration — need ${needSec}s, recorded $gotLabel. Retake to continue."
    }

    /** Whether the accept/KEEP control may be shown after capture. */
    fun canShowKeepAction(
        mediaType: GuidedClipMediaType,
        requiredDurationMs: Long,
        actualDurationMs: Long,
        qualityRecommendation: Recommendation?
    ): Boolean = isKeepEligible(mediaType, requiredDurationMs, actualDurationMs, qualityRecommendation)
}
