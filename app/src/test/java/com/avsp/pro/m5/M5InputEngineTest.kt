package com.avsp.pro.m5

import org.junit.Assert.*
import org.junit.Test

class M5InputEngineTest {
    @Test fun youtubeIdAndNumbersAreParsed() {
        assertEquals("abcDEF12", M5InputEngine.parseYouTubeUrl("https://www.youtube.com/watch?v=abcDEF12"))
        val r = M5InputEngine.analyzeText("screen", "x", "Speed 100 Mbps, price 599")
        assertEquals(listOf(100.0, 599.0), r.numbers)
        assertTrue(r.confidence > 0.0)
    }
}
