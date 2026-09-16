package com.avsp.pro.video.validation

import com.avsp.pro.video.contract.VideoRenderPlan

object VideoValidator {
    const val MAX_TIMELINE_DRIFT_MS = 80L

    data class Validation(val isValid: Boolean, val errors: List<String> = emptyList())

    fun validatePlan(plan: VideoRenderPlan): Validation {
        val errors = mutableListOf<String>()
        if (plan.projectId.isBlank()) errors += "projectId is blank"
        if (plan.scenes.isEmpty()) errors += "No scenes"
        if (plan.totalDurationMs <= 0) errors += "Invalid total duration"
        if (plan.width <= 0 || plan.height <= 0) errors += "Invalid output dimensions"
        if (plan.fps <= 0) errors += "Invalid FPS"

        val ordered = plan.scenes.sortedBy { it.order }
        var cursor = 0L
        ordered.forEachIndexed { index, scene ->
            if (scene.order != index) errors += "Scene order gap at index $index"
            if (scene.durationMs <= 0) errors += "Invalid duration for ${scene.sceneId}"
            if (scene.startMs != cursor) errors += "Timeline gap/overlap before ${scene.sceneId}"
            if (scene.endMs - scene.startMs != scene.durationMs) errors += "Duration mismatch for ${scene.sceneId}"
            if (scene.audioRelativePath.isBlank()) errors += "Missing audio for ${scene.sceneId}"
            cursor = scene.endMs
        }
        if (kotlin.math.abs(cursor - plan.totalDurationMs) > MAX_TIMELINE_DRIFT_MS) {
            errors += "Scene timeline ${cursor}ms != audio total ${plan.totalDurationMs}ms"
        }
        return Validation(errors.isEmpty(), errors)
    }

    fun validateOutput(
        expectedWidth: Int,
        expectedHeight: Int,
        expectedDurationMs: Long,
        actualWidth: Int,
        actualHeight: Int,
        actualDurationMs: Long,
        hasVideo: Boolean,
        hasAudio: Boolean,
        rotationDegrees: Int = 0
    ): Validation {
        val errors = mutableListOf<String>()
        if (!hasVideo) errors += "Output has no video track"
        if (!hasAudio) errors += "Output has no audio track"
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val displayWidth = if (normalizedRotation == 90 || normalizedRotation == 270) actualHeight else actualWidth
        val displayHeight = if (normalizedRotation == 90 || normalizedRotation == 270) actualWidth else actualHeight
        if (displayWidth != expectedWidth || displayHeight != expectedHeight) {
            errors += "Output dimensions ${displayWidth}x${displayHeight} != ${expectedWidth}x${expectedHeight} (encoded ${actualWidth}x${actualHeight}, rotation ${normalizedRotation}°)"
        }
        if (kotlin.math.abs(actualDurationMs - expectedDurationMs) > MAX_TIMELINE_DRIFT_MS) {
            errors += "Output duration ${actualDurationMs}ms != expected ${expectedDurationMs}ms"
        }
        return Validation(errors.isEmpty(), errors)
    }
}
