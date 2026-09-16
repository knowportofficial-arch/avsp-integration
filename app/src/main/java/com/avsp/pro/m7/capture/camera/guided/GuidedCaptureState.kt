package com.avsp.pro.m7.capture.camera.guided

enum class GuidedCapturePhase {
    PERMISSION_REQUIRED,
    READY,
    COUNTDOWN,
    RECORDING,
    REVIEW,
    SAVING,
    ERROR,
    COMPLETE
}

data class GuidedCaptureState(
    val template: GuidedCaptureTemplate? = null,
    val currentClipIndex: Int = 0,
    val phase: GuidedCapturePhase = GuidedCapturePhase.PERMISSION_REQUIRED,

    // Self-timer (M6 feature #11)
    val countdownSecondsRemaining: Int = 0,

    // Recording progress (M6 feature #19): elapsed vs the clip's target duration
    val elapsedMs: Long = 0L,
    val targetDurationMs: Long = 0L,

    // Live toggles (M6 features #9, #10, #12, #13, #14)
    val isFlashOn: Boolean = false,
    val isGridEnabled: Boolean = true,
    val deviceOrientationDegrees: Int = 0,
    val exposureIndex: Int = 0,
    val exposureRange: IntRange = 0..0,

    // Retake (M6 feature #18)
    val lastRecordedFile: String? = null,
    val lastRecordedThumbnail: String? = null,

    val completedClips: List<ClipMetadata> = emptyList(),
    val errorMessage: String? = null
) {
    val currentClip: GuidedClipSpec?
        get() = template?.clips?.getOrNull(currentClipIndex)

    val progressFraction: Float
        get() = if (targetDurationMs <= 0L) 0f else (elapsedMs.toFloat() / targetDurationMs.toFloat()).coerceIn(0f, 1f)

    val isLastClip: Boolean
        get() = template != null && currentClipIndex == template.clips.lastIndex
}
