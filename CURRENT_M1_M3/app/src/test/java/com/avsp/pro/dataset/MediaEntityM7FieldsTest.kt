package com.avsp.pro.dataset

import com.avsp.pro.capture.database.entity.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEntityM7FieldsTest {

    @Test
    fun defaultsAreUnanalyzedNotKeep() {
        val e = MediaEntity(
            projectId = "p",
            uriString = "content://x",
            mediaType = "PHOTO",
            displayName = "shot"
        )
        assertEquals("Uncategorized", e.category)
        assertEquals("", e.tagsCsv)
        assertEquals(0.0, e.qualityScoreNormalized, 0.001)
        assertEquals(0, e.qualityScore)
        assertFalse(e.blurDetected)
        assertTrue(e.exposureOk)
        assertTrue(e.compositionOk)
        assertFalse(e.isDuplicate)
        // Critical: unanalyzed must never default to KEEP
        assertEquals("REVIEW", e.recommendation)
        assertFalse(e.isBestShot)
        assertTrue(e.tagsList().isEmpty())
    }

    @Test
    fun tagsListParsesCsv() {
        val e = MediaEntity(
            projectId = "p",
            uriString = "content://x",
            mediaType = "PHOTO",
            displayName = "shot",
            tagsCsv = "golden-hour, outdoor, temple"
        )
        assertEquals(listOf("golden-hour", "outdoor", "temple"), e.tagsList())
    }
}
