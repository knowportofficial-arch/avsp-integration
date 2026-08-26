package com.avsp.pro.capture.camera.guided

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * M6.2 (FIX 3 support) — JVM unit tests for [SelfTimer]'s cancellation guarantees. This is
 * the piece of the self-timer flow that's actually pure-Kotlin/coroutine logic and testable
 * without Android; the ViewModel-level `isCleared` lifecycle guard (the other half of FIX 3)
 * requires `androidx.lifecycle.ViewModel`/`viewModelScope` integration and is covered by the
 * device test procedure instead, per the instruction not to fake device-level test coverage.
 */
class SelfTimerTest {

    @Test
    fun `cancel before completion prevents onFinished from firing`() = runTest {
        val timer = SelfTimer()
        var finishedCalled = false
        timer.start(
            scope = this,
            seconds = 5,
            onTick = {},
            onFinished = { finishedCalled = true }
        )
        advanceTimeBy(1500) // partway through the countdown
        timer.cancel()
        advanceTimeBy(10_000) // let plenty more virtual time pass
        assertFalse("onFinished must not fire after cancel()", finishedCalled)
    }

    @Test
    fun `normal countdown to zero calls onFinished exactly once`() = runTest {
        val timer = SelfTimer()
        var finishedCount = 0
        val ticks = mutableListOf<Int>()
        timer.start(
            scope = this,
            seconds = 3,
            onTick = { ticks.add(it) },
            onFinished = { finishedCount++ }
        )
        advanceTimeBy(10_000)
        assertEquals(1, finishedCount)
        assertEquals(listOf(3, 2, 1, 0), ticks)
    }

    @Test
    fun `starting again cancels the previous job so onFinished cannot fire twice`() = runTest {
        val timer = SelfTimer()
        var finishedCount = 0
        timer.start(scope = this, seconds = 5, onTick = {}, onFinished = { finishedCount++ })
        advanceTimeBy(1000) // first countdown is at 4s remaining, still running

        // Simulate a rapid double-tap: start() again before the first countdown finished.
        timer.start(scope = this, seconds = 5, onTick = {}, onFinished = { finishedCount++ })
        advanceTimeBy(10_000)

        // Only the second countdown should have completed -- the first's job was cancelled
        // by start()'s internal cancel() call, so its onFinished can never fire.
        assertEquals(1, finishedCount)
    }

    @Test
    fun `zero seconds calls onFinished immediately without a coroutine`() = runTest {
        val timer = SelfTimer()
        var finishedCalled = false
        timer.start(scope = this, seconds = 0, onTick = {}, onFinished = { finishedCalled = true })
        assertEquals(true, finishedCalled) // synchronous, no advanceTimeBy needed
        assertFalse(timer.isRunning)
    }

    @Test
    fun `isRunning reflects actual job state`() = runTest {
        val timer = SelfTimer()
        assertFalse(timer.isRunning)
        timer.start(scope = this, seconds = 3, onTick = {}, onFinished = {})
        assert(timer.isRunning)
        timer.cancel()
        assertFalse(timer.isRunning)
    }
}
