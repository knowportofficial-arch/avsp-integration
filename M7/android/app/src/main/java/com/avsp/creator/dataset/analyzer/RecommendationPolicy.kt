package com.avsp.creator.dataset.analyzer

import com.avsp.creator.dataset.model.Recommendation

/**
 * Deterministic KEEP / REVIEW / RETAKE decision for M7.
 *
 * Thresholds (normalized score 0.0–1.0):
 *   score >= [KEEP_MIN]   → KEEP
 *   score >= [REVIEW_MIN] → REVIEW
 *   score <  [REVIEW_MIN] → RETAKE
 *
 * Analysis failure / unanalyzable media → REVIEW (never KEEP).
 * Duplicate → REVIEW.
 * Blur or bad exposure with score below KEEP_MIN → RETAKE.
 */
object RecommendationPolicy {

    /** Inclusive minimum for KEEP (high quality). */
    const val KEEP_MIN = 0.70

    /** Inclusive minimum for REVIEW (medium / uncertain). Below this → RETAKE. */
    const val REVIEW_MIN = 0.40

    fun decide(
        score: Double,
        blur: Boolean = false,
        exposureOk: Boolean = true,
        isDuplicate: Boolean = false,
        analysisFailed: Boolean = false
    ): Recommendation {
        if (analysisFailed) return Recommendation.REVIEW
        if (isDuplicate) return Recommendation.REVIEW

        val s = score.coerceIn(0.0, 1.0)

        // Technical defects force RETAKE unless overall quality is still clearly high.
        if ((blur || !exposureOk) && s < KEEP_MIN) {
            return Recommendation.RETAKE
        }

        return when {
            s >= KEEP_MIN -> Recommendation.KEEP
            s >= REVIEW_MIN -> Recommendation.REVIEW
            else -> Recommendation.RETAKE
        }
    }

    /** Map legacy 0–100 integer scores into the same bands. */
    fun decideFromPercent(
        percent: Int,
        blur: Boolean = false,
        exposureOk: Boolean = true,
        isDuplicate: Boolean = false,
        analysisFailed: Boolean = false
    ): Recommendation = decide(
        score = (percent.coerceIn(0, 100) / 100.0),
        blur = blur,
        exposureOk = exposureOk,
        isDuplicate = isDuplicate,
        analysisFailed = analysisFailed
    )
}
