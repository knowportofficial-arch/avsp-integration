package com.avsp.pro.avsp20.adapters

import com.avsp.pro.m5.M5InputResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M5InputContractAdapterTest {

    @Test
    fun mapsTextInputResult() {
        val source = M5InputResult(
            source = "youtube",
            reference = "https://youtu.be/abc1234",
            title = "Test",
            text = "Gold 2500.50",
            numbers = listOf(2500.50),
            confidence = 0.9,
            processedAt = 100L
        )

        val result = M5InputContractAdapter.toAvsp20(source)

        assertEquals("youtube", result.source)
        assertEquals(source.reference, result.reference)
        assertEquals("Test", result.title)
        assertEquals("Gold 2500.50", result.text)
        assertEquals(listOf(2500.50), result.numbers)
        assertEquals(0L, result.durationMs)
        assertEquals(0.9, result.confidence, 0.0)
        assertEquals(100L, result.processedAt)
    }

    @Test
    fun convertsSecondsToMilliseconds() {
        val source = M5InputResult(
            source = "screen",
            reference = "content://media/1",
            durationSeconds = 12L,
            confidence = 0.8
        )

        val result = M5InputContractAdapter.toAvsp20(source)

        assertEquals(12000L, result.durationMs)
        assertTrue(result.title.isEmpty())
    }
}
