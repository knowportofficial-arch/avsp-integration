package com.avsp.creator.dataset

import com.avsp.creator.database.entity.MediaEntity
import com.avsp.creator.dataset.analyzer.BestShotSelector
import com.avsp.creator.dataset.model.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BestShotSelectorTest {

    private val selector = BestShotSelector()

    private fun entity(
        id: String,
        quality: Int,
        recommendation: String = Recommendation.KEEP.name,
        isDup: Boolean = false
    ) = MediaEntity(
        id = id,
        projectId = "p1",
        uriString = "content://$id",
        mediaType = "PHOTO",
        displayName = id,
        qualityScore = quality,
        qualityScoreNormalized = quality / 100.0,
        recommendation = recommendation,
        isDuplicate = isDup
    )

    @Test
    fun ranksHighestQualityAsBest() {
        val list = listOf(
            entity("a", 60),
            entity("b", 92),
            entity("c", 75)
        )
        val ranked = selector.rank(list)
        assertEquals(3, ranked.size)
        assertEquals("b", ranked[0].media.id)
        assertTrue(ranked[0].isBest)
        assertFalse(ranked[1].isBest)
        assertFalse(ranked[2].isBest)
    }

    @Test
    fun emptyListReturnsEmpty() {
        assertTrue(selector.rank(emptyList()).isEmpty())
        assertEquals(null, selector.selectBestId(emptyList()))
    }

    @Test
    fun duplicatesPenalized() {
        val list = listOf(
            entity("good", 80, isDup = false),
            entity("dup", 90, isDup = true)
        )
        val best = selector.selectBestId(list)
        assertEquals("good", best)
    }

    @Test
    fun retakePenalized() {
        val list = listOf(
            entity("keep", 70, recommendation = Recommendation.KEEP.name),
            entity("retake", 85, recommendation = Recommendation.RETAKE.name)
        )
        val best = selector.selectBestId(list)
        assertEquals("keep", best)
    }
}
