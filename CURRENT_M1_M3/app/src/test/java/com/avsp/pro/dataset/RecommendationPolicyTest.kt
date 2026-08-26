package com.avsp.pro.dataset

import com.avsp.pro.dataset.analyzer.RecommendationPolicy
import com.avsp.pro.dataset.model.QualityResult
import com.avsp.pro.dataset.model.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the device bug: low scores must never map to KEEP.
 */
class RecommendationPolicyTest {

    @Test
    fun highScore_keep() {
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decide(0.90))
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decide(0.70))
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decide(1.0))
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decideFromPercent(90))
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decideFromPercent(70))
    }

    @Test
    fun mediumScore_review() {
        assertEquals(Recommendation.REVIEW, RecommendationPolicy.decide(0.55))
        assertEquals(Recommendation.REVIEW, RecommendationPolicy.decide(0.40))
        assertEquals(Recommendation.REVIEW, RecommendationPolicy.decide(0.69))
        assertEquals(Recommendation.REVIEW, RecommendationPolicy.decideFromPercent(55))
        assertEquals(Recommendation.REVIEW, RecommendationPolicy.decideFromPercent(40))
    }

    @Test
    fun lowScore_retake() {
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decide(0.39))
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decide(0.26))
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decide(0.0))
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decideFromPercent(26))
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decideFromPercent(0))
    }

    @Test
    fun analysisFailure_review_neverKeep() {
        assertEquals(
            Recommendation.REVIEW,
            RecommendationPolicy.decide(0.0, analysisFailed = true)
        )
        // Even a high score must not KEEP when analysis failed.
        assertEquals(
            Recommendation.REVIEW,
            RecommendationPolicy.decide(0.95, analysisFailed = true)
        )
        val failed = QualityResult.analysisFailed()
        assertEquals(Recommendation.REVIEW, failed.recommendation)
        assertEquals(0.0, failed.score, 0.0)
    }

    @Test
    fun fromMetrics_high_keep() {
        val qr = QualityResult.fromMetrics(score = 0.86)
        assertEquals(Recommendation.KEEP, qr.recommendation)
        assertEquals(0.86, qr.score, 0.001)
    }

    @Test
    fun fromMetrics_low_retake() {
        val qr = QualityResult.fromMetrics(score = 0.26)
        assertEquals(Recommendation.RETAKE, qr.recommendation)
    }

    @Test
    fun fromMetrics_zero_retake() {
        val qr = QualityResult.fromMetrics(score = 0.0)
        assertEquals(Recommendation.RETAKE, qr.recommendation)
    }

    @Test
    fun blurForcesRetakeWhenBelowKeep() {
        assertEquals(
            Recommendation.RETAKE,
            RecommendationPolicy.decide(0.65, blur = true)
        )
        // Still KEEP if overall score is clearly high despite blur flag.
        assertEquals(
            Recommendation.KEEP,
            RecommendationPolicy.decide(0.85, blur = true)
        )
    }

    @Test
    fun badExposureForcesRetakeWhenBelowKeep() {
        assertEquals(
            Recommendation.RETAKE,
            RecommendationPolicy.decide(0.50, exposureOk = false)
        )
    }

    @Test
    fun duplicate_review() {
        assertEquals(
            Recommendation.REVIEW,
            RecommendationPolicy.decide(0.95, isDuplicate = true)
        )
    }

    @Test
    fun deviceBugRegression_zeroAndLowNeverKeep() {
        // Exact regression cases observed on device.
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decideFromPercent(0))
        assertEquals(Recommendation.RETAKE, RecommendationPolicy.decideFromPercent(26))
        assertEquals(Recommendation.REVIEW, RecommendationPolicy.decideFromPercent(55))
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decideFromPercent(90))
        assertEquals(Recommendation.KEEP, RecommendationPolicy.decideFromPercent(70))
    }
}
