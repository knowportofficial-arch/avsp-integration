package com.avsp.pro.m7.capture.camera.candidate

import com.avsp.pro.m7.capture.camera.model.CameraShotType

data class CapturedShotRecord(
    val shotId: String,
    val objective: String,
    val targetSubject: String,
    val shotType: CameraShotType,
    val mediaUri: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Shot Memory.
 * Tracks previously captured cinematographic shots during the active session.
 * Prevents redundant shot suggestions and guides the engine toward coverage gaps.
 */
data class ShotMemory(
    val capturedShots: List<CapturedShotRecord> = emptyList()
) {
    fun isAlreadyCaptured(targetSubject: String, shotType: CameraShotType): Boolean {
        val targetTokens = targetSubject.lowercase().split(" ", ",", "-", "/").filter { it.length >= 3 }
        return capturedShots.any { record ->
            val recordTokens = record.targetSubject.lowercase().split(" ", ",", "-", "/").filter { it.length >= 3 }
            val isSubjectOverlap = targetTokens.any { t -> recordTokens.contains(t) } || record.targetSubject.contains(targetSubject, ignoreCase = true)
            isSubjectOverlap && record.shotType == shotType
        }
    }

    fun isObjectiveCaptured(objective: String): Boolean {
        val objTokens = objective.lowercase().split(" ", ",", "-", "/").filter { it.length >= 3 }
        return capturedShots.any { record ->
            val recTokens = record.objective.lowercase().split(" ", ",", "-", "/").filter { it.length >= 3 }
            objTokens.count { recTokens.contains(it) } >= 2 || record.objective.equals(objective, ignoreCase = true)
        }
    }

    fun recordCapture(candidate: ShotCandidate, mediaUri: String): ShotMemory {
        val record = CapturedShotRecord(
            shotId = candidate.id,
            objective = candidate.objective,
            targetSubject = candidate.targetSubject,
            shotType = candidate.shotType,
            mediaUri = mediaUri
        )
        return this.copy(capturedShots = capturedShots + record)
    }

    val capturedCount: Int
        get() = capturedShots.size

    val capturedSubjectNames: Set<String>
        get() = capturedShots.map { it.targetSubject }.toSet()

    fun getSummary(): String {
        return if (capturedShots.isEmpty()) {
            "No captures yet"
        } else {
            "${capturedShots.size} captured: " + capturedShots.joinToString { "${it.targetSubject} (${it.shotType.displayName})" }
        }
    }
}
