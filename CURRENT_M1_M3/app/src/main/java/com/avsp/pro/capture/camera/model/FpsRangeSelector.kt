package com.avsp.pro.capture.camera.model

/**
 * M6.2 — pure decision logic for choosing a Camera2 AE target FPS range from a device's
 * actually-available ranges. Extracted as plain Kotlin (no `android.util.Range`, no
 * `CameraCharacteristics`) specifically so it can be unit tested on the local JVM without
 * Robolectric or a device — see `FpsRangeSelectorTest`.
 *
 * [CameraController.selectAeTargetFpsRange] wraps this with the real Camera2 characteristics
 * query and converts to/from `android.util.Range<Int>`.
 */
object FpsRangeSelector {

    data class FpsRange(val lower: Int, val upper: Int)

    /**
     * Picks the best range for [requestedFps] from [available]:
     * 1. An exact fixed range (e.g. 60-60) if present.
     * 2. Otherwise the tightest range that contains [requestedFps].
     * 3. If neither exists (device doesn't actually support the request), falls back to the
     *    same two-step search for [fallbackFps] instead.
     * 4. Returns null only if neither the request nor the fallback is achievable at all —
     *    callers should treat null as "let CameraX use its own default", not as an error.
     */
    fun select(available: List<FpsRange>, requestedFps: Int, fallbackFps: Int = 30): FpsRange? {
        if (available.isEmpty()) return null
        pickFor(available, requestedFps)?.let { return it }
        if (requestedFps != fallbackFps) {
            pickFor(available, fallbackFps)?.let { return it }
        }
        return null
    }

    private fun pickFor(available: List<FpsRange>, fps: Int): FpsRange? {
        available.firstOrNull { it.lower == fps && it.upper == fps }?.let { return it }
        return available
            .filter { it.lower <= fps && it.upper >= fps }
            .minByOrNull { it.upper - it.lower }
    }
}
