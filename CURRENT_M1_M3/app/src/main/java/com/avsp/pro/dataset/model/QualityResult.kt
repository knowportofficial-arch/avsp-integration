package com.avsp.pro.dataset.model

import com.avsp.pro.dataset.analyzer.RecommendationPolicy

/**
 * Structured quality assessment returned by M7 local analyzers.
 * Matches the AVSP M7 specification contract.
 */
data class QualityResult(
    val score: Double,                 // 0.0 .. 1.0 overall
    val blur: Boolean,                 // true = blurred / soft
    val exposure: Boolean,             // true = acceptable exposure
    val composition: Boolean,          // true = acceptable composition
    val faceQuality: Double?,          // 0.0 .. 1.0 or null if no face
    val closedEye: Boolean?,           // null if no reliable face/eye data
    val duplicate: Boolean,
    val recommendation: Recommendation
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "score" to score,
        "blur" to blur,
        "exposure" to exposure,
        "composition" to composition,
        "face_quality" to faceQuality,
        "closed_eye" to closedEye,
        "duplicate" to duplicate,
        "recommendation" to recommendation.name
    )

    companion object {

        /**
         * Decode / analysis failure. Must never be KEEP.
         * Score is 0.0 but recommendation is forced to REVIEW so failure
         * is not confused with a genuinely graded low-quality shot.
         */
        fun analysisFailed(): QualityResult = QualityResult(
            score = 0.0,
            blur = false,
            exposure = true,
            composition = true,
            faceQuality = null,
            closedEye = null,
            duplicate = false,
            recommendation = Recommendation.REVIEW
        )

        /**
         * Build a result with recommendation derived solely from [RecommendationPolicy].
         */
        fun fromMetrics(
            score: Double,
            blur: Boolean = false,
            exposure: Boolean = true,
            composition: Boolean = true,
            faceQuality: Double? = null,
            closedEye: Boolean? = null,
            duplicate: Boolean = false,
            analysisFailed: Boolean = false
        ): QualityResult {
            val clamped = score.coerceIn(0.0, 1.0)
            val rec = RecommendationPolicy.decide(
                score = clamped,
                blur = blur,
                exposureOk = exposure,
                isDuplicate = duplicate,
                analysisFailed = analysisFailed
            )
            return QualityResult(
                score = clamped,
                blur = blur,
                exposure = exposure,
                composition = composition,
                faceQuality = faceQuality,
                closedEye = closedEye,
                duplicate = duplicate,
                recommendation = rec
            )
        }
    }
}

enum class Recommendation {
    KEEP,
    RETAKE,
    REVIEW
}
