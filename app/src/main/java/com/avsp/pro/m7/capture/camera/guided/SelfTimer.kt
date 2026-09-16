package com.avsp.pro.m7.capture.camera.guided

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * M6 — self-timer / delayed-shutter countdown (e.g. 3s or 10s before capture starts).
 * Independent of the recording elapsed-time clock in the existing AI CameraViewModel.
 */
class SelfTimer {
    private var job: Job? = null

    /**
     * Runs a countdown from [seconds] down to 0, invoking [onTick] each second (including the
     * initial value and the terminal 0), then invoking [onFinished]. No-ops if [seconds] <= 0
     * (immediate [onFinished]).
     */
    fun start(scope: CoroutineScope, seconds: Int, onTick: (Int) -> Unit, onFinished: () -> Unit) {
        cancel()
        if (seconds <= 0) {
            onFinished()
            return
        }
        job = scope.launch {
            for (remaining in seconds downTo 1) {
                onTick(remaining)
                delay(1000L)
            }
            onTick(0)
            onFinished()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    val isRunning: Boolean get() = job?.isActive == true
}
