package com.avsp.creator.dataset

import com.avsp.creator.dataset.model.DatasetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetCategoryTest {

    @Test
    fun knownCategoriesPresent() {
        val keys = DatasetCategory.allKnown().map { it.key }
        assertTrue(keys.contains("Intro"))
        assertTrue(keys.contains("Outro"))
        assertTrue(keys.contains("Station"))
        assertTrue(keys.contains("Road"))
        assertTrue(keys.contains("Market"))
        assertTrue(keys.contains("Temple"))
        assertTrue(keys.contains("Nature"))
        assertTrue(keys.contains("People"))
        assertTrue(keys.contains("Vehicle"))
        assertTrue(keys.contains("Food"))
        assertTrue(keys.contains("Buildings"))
    }

    @Test
    fun fromKeyCaseInsensitive() {
        assertEquals(DatasetCategory.NATURE, DatasetCategory.fromKey("nature"))
        assertEquals(DatasetCategory.TEMPLE, DatasetCategory.fromKey("Temple"))
    }

    @Test
    fun unknownMapsToUncategorized() {
        assertEquals(DatasetCategory.UNCATEGORIZED, DatasetCategory.fromKey("xyz"))
        assertEquals(DatasetCategory.UNCATEGORIZED, DatasetCategory.fromKey(null))
        assertEquals(DatasetCategory.UNCATEGORIZED, DatasetCategory.fromKey(""))
    }

    @Test
    fun allKnownExcludesUncategorized() {
        assertFalseContainsUncategorized()
    }

    private fun assertFalseContainsUncategorized() {
        assertTrue(DatasetCategory.allKnown().none { it == DatasetCategory.UNCATEGORIZED })
    }
}
