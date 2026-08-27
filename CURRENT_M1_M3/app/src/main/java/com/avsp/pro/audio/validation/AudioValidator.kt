package com.avsp.pro.audio.validation

import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.AudioValidation
import com.avsp.pro.audio.contract.AudioValidationStatus
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.script.integration.ScriptNarrationHandoff
import kotlin.math.abs

object AudioValidator {

    const val TIMING_DRIFT_WARN_RATIO = 0.25

    fun validateHandoff(handoff: ScriptNarrationHandoff?): AudioValidation {
        val errors = mutableListOf<String>()
        if (handoff == null) {
            errors += "SCRIPT_REQUIRED: no M2 script narration handoff available"
            return result(errors, emptyList())
        }
        if (handoff.projectId.isBlank()) errors += "projectId is required"
        if (handoff.scriptId.isBlank()) errors += "scriptId is required"
        if (handoff.segments.isEmpty()) errors += "SCRIPT_REQUIRED: narration segments are empty"
        if (!AudioLanguageRegistry.isSupported(handoff.language)) {
            errors += "TTS_LANGUAGE_UNAVAILABLE: unsupported language ${handoff.language}"
        }
        handoff.segments.forEach { seg ->
            if (seg.narration.isBlank()) {
                errors += "segment order=${seg.order} has empty narration"
            }
        }
        return result(errors, emptyList())
    }

    fun validatePackage(audio: AudioPackage): AudioValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (audio.version.isBlank()) errors += "audio package version required"
        if (audio.projectId.isBlank()) errors += "projectId required"
        if (audio.scriptId.isBlank()) errors += "scriptId required"
        if (audio.segments.isEmpty()) errors += "INVALID_AUDIO_TIMELINE: no segments"
        if (!AudioLanguageRegistry.isSupported(audio.language)) {
            errors += "TTS_LANGUAGE_UNAVAILABLE: ${audio.language}"
        }

        val orders = audio.segments.map { it.order }
        if (orders != orders.sorted()) {
            errors += "INVALID_AUDIO_TIMELINE: segments not ordered"
        }
        if (orders.distinct().size != orders.size) {
            errors += "INVALID_AUDIO_TIMELINE: duplicate segment order"
        }

        var cursor = 0L
        audio.segments.forEach { seg ->
            if (seg.status == AudioSegmentStatus.FAILED) {
                errors += "segment ${seg.segmentId} failed: ${seg.errorCode}"
            }
            if (seg.relativeAudioPath.isBlank() && seg.status == AudioSegmentStatus.READY) {
                errors += "segment ${seg.segmentId} missing audio path"
            }
            if (seg.durationMs <= 0L && seg.status == AudioSegmentStatus.READY) {
                errors += "segment ${seg.segmentId} invalid duration"
            }
            if (seg.startMs != cursor) {
                warnings += "segment ${seg.segmentId} startMs=${seg.startMs} expected $cursor"
            }
            if (seg.endMs != seg.startMs + seg.durationMs) {
                errors += "INVALID_AUDIO_TIMELINE: segment ${seg.segmentId} endMs mismatch"
            }
            cursor = seg.endMs
            val planned = seg.plannedDurationMs
            if (planned != null && planned > 0) {
                val deltaRatio = abs(seg.durationMs - planned).toDouble() / planned.toDouble()
                if (deltaRatio > TIMING_DRIFT_WARN_RATIO) {
                    warnings += "segment ${seg.segmentId} duration drift ${seg.durationDeltaMs}ms vs M2 plan"
                }
            }
        }

        val sum = audio.segments.sumOf { it.durationMs }
        if (audio.totalDurationMs != sum) {
            errors += "INVALID_AUDIO_TIMELINE: totalDurationMs=${audio.totalDurationMs} != sum=$sum"
        }

        return result(errors, warnings)
    }

    private fun result(errors: List<String>, warnings: List<String>): AudioValidation {
        val status = when {
            errors.isNotEmpty() -> AudioValidationStatus.INVALID
            warnings.isNotEmpty() -> AudioValidationStatus.WARNING
            else -> AudioValidationStatus.VALID
        }
        return AudioValidation(
            isValid = errors.isEmpty(),
            status = status,
            warnings = warnings,
            errors = errors
        )
    }
}
