package com.avsp.creator.capture.camera.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M6.2 (FIX 1) — JVM unit tests for [FpsRangeSelector]. Pure math, no Android dependency
 * (deliberately uses a plain `FpsRange` data class instead of `android.util.Range`, which
 * would not be usable in a local JVM test). Runs with `./gradlew testDebugUnitTest`.
 */
class FpsRangeSelectorTest {

    @Test
    fun `exact 60fps range is chosen when available`() {
        val available = listOf(
            FpsRangeSelector.FpsRange(15, 30),
            FpsRangeSelector.FpsRange(30, 30),
            FpsRangeSelector.FpsRange(60, 60)
        )
        val result = FpsRangeSelector.select(available, requestedFps = 60)
        assertEquals(FpsRangeSelector.FpsRange(60, 60), result)
    }

    @Test
    fun `tightest containing range chosen when no exact 60fps range exists`() {
        val available = listOf(
            FpsRangeSelector.FpsRange(15, 30),
            FpsRangeSelector.FpsRange(30, 60), // contains 60 but isn't exact
            FpsRangeSelector.FpsRange(4, 60)   // also contains 60, wider
        )
        val result = FpsRangeSelector.select(available, requestedFps = 60)
        assertEquals(FpsRangeSelector.FpsRange(30, 60), result) // the tighter of the two
    }

    @Test
    fun `unsupported 60fps falls back to exact 30fps range`() {
        val available = listOf(
            FpsRangeSelector.FpsRange(15, 30),
            FpsRangeSelector.FpsRange(30, 30)
            // no range reaches 60
        )
        val result = FpsRangeSelector.select(available, requestedFps = 60)
        assertEquals(FpsRangeSelector.FpsRange(30, 30), result)
    }

    @Test
    fun `unsupported 60fps falls back to a containing 30fps range when no exact 30 exists`() {
        val available = listOf(
            FpsRangeSelector.FpsRange(15, 30) // contains 30 but not exact
        )
        val result = FpsRangeSelector.select(available, requestedFps = 60)
        assertEquals(FpsRangeSelector.FpsRange(15, 30), result)
    }

    @Test
    fun `neither request nor fallback achievable returns null, not a fake value`() {
        val available = listOf(
            FpsRangeSelector.FpsRange(5, 15) // doesn't cover 60 or 30
        )
        val result = FpsRangeSelector.select(available, requestedFps = 60)
        assertNull(result)
    }

    @Test
    fun `empty available list returns null`() {
        assertNull(FpsRangeSelector.select(emptyList(), requestedFps = 60))
    }

    @Test
    fun `exact 30fps request with only 30 available does not need fallback`() {
        val available = listOf(FpsRangeSelector.FpsRange(30, 30))
        val result = FpsRangeSelector.select(available, requestedFps = 30)
        assertEquals(FpsRangeSelector.FpsRange(30, 30), result)
    }

    @Test
    fun `24fps request works via generic containment, not hardcoded to 30 or 60`() {
        val available = listOf(FpsRangeSelector.FpsRange(15, 30))
        val result = FpsRangeSelector.select(available, requestedFps = 24, fallbackFps = 30)
        assertEquals(FpsRangeSelector.FpsRange(15, 30), result)
    }

    @Test
    fun `does not silently claim 60fps by returning a range that does not actually cover it`() {
        val available = listOf(FpsRangeSelector.FpsRange(24, 30))
        val result = FpsRangeSelector.select(available, requestedFps = 60)
        // Must fall back to the 30fps-covering range, never fabricate a 60fps-capable result.
        assertEquals(FpsRangeSelector.FpsRange(24, 30), result)
        assert(result!!.upper < 60)
    }
}
