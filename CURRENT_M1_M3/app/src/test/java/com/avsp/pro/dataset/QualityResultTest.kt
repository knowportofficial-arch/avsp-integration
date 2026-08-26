package com.avsp.pro.dataset

import com.avsp.pro.dataset.model.QualityResult
import com.avsp.pro.dataset.model.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityResultTest {

    @Test
    fun toMapContainsAllKeys() {
        val qr = QualityResult.fromMetrics(
            score = 0.86,
            blur = false,
            exposure = true,
            composition = true,
            faceQuality = 0.91,
            closedEye = false,
            duplicate = false
        )
        val map = qr.toMap()
        assertEquals(0.86, map["score"])
        assertEquals(false, map["blur"])
        assertEquals(true, map["exposure"])
        assertEquals(true, map["composition"])
        assertEquals(0.91, map["face_quality"])
        assertEquals(false, map["closed_eye"])
        assertEquals(false, map["duplicate"])
        assertEquals("KEEP", map["recommendation"])
    }

    @Test
    fun analysisFailedIsReview() {
        val qr = QualityResult.analysisFailed()
        assertEquals(Recommendation.REVIEW, qr.recommendation)
        assertEquals(0.0, qr.score, 0.0)
        assertFalse(qr.blur)
        assertTrue(qr.exposure)
    }

    @Test
    fun fromMetricsLowIsRetake() {
        val qr = QualityResult.fromMetrics(0.2)
        assertEquals(Recommendation.RETAKE, qr.recommendation)
    }
}
