package com.avsp.pro.capture.camera.mission

import com.avsp.pro.capture.camera.model.CameraFrameRate
import com.avsp.pro.capture.camera.model.CameraResolution
import com.avsp.pro.capture.camera.model.CameraShotType
import java.util.UUID

/**
 * Generic user-reported live occurrence or spontaneous event.
 * Replaces hardcoded event modes with a generic high-priority event structure.
 */
data class UserReportedEvent(
    val id: String = "event_${UUID.randomUUID().toString().take(6)}",
    val type: String = "USER_REPORTED_EVENT",
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int = 100,
    val isHandled: Boolean = false
)

enum class ShotMediaType { PHOTO, VIDEO }

enum class ShotStatus(val displayName: String) {
    PENDING("PENDING"),
    CURRENT("CURRENT"),
    READY("READY"),
    CAPTURED("CAPTURED"),
    SKIPPED("SKIPPED"),
    EXPIRED("EXPIRED"),
    OPPORTUNITY("OPPORTUNITY"),
    LATER("LATER")
}

data class ShotMissionItem(
    val id: String,
    val sequenceNumber: Int,
    val title: String,
    val description: String,
    val shotType: CameraShotType,
    val mediaType: ShotMediaType = ShotMediaType.PHOTO,
    val preferredFrameRate: CameraFrameRate,
    val preferredResolution: CameraResolution = CameraResolution.FULL_HD_1080,
    val subjectHint: String,
    val compositionHint: String,
    val status: ShotStatus = ShotStatus.PENDING,
    val priority: Int = 10,
    val isCompleted: Boolean = false,
    val capturedMediaUri: String? = null,
    val eventId: String? = null
)

/**
 * Dynamic Coverage Plan for AI Cinematographer.
 * Tracks completed, current, and remaining shots, dynamically updating priority
 * based on live visible evidence and incoming spontaneous events.
 */
data class ShotMission(
    val id: String,
    val title: String,
    val contextDescription: String,
    val shots: List<ShotMissionItem>,
    val currentShotIndex: Int = 0,
    val activeEvent: UserReportedEvent? = null
) {
    /**
     * The shot currently being executed, or the next pending shot when no shot
     * has explicitly been promoted yet. A completed/skipped shot is NEVER
     * returned here. When the entire plan is complete, this becomes null so
     * the camera exits the old shot instead of remaining stuck on the last one.
     */
    val currentShot: ShotMissionItem?
        get() = shots.firstOrNull {
            it.status == ShotStatus.CURRENT ||
                it.status == ShotStatus.READY ||
                it.status == ShotStatus.OPPORTUNITY
        } ?: shots.firstOrNull {
            it.status == ShotStatus.PENDING || it.status == ShotStatus.LATER
        }

    val progressPercent: Int
        get() = if (shots.isEmpty()) 0 else ((shots.count { it.status == ShotStatus.CAPTURED || it.isCompleted }.toFloat() / shots.size) * 100).toInt()

    val pendingShots: List<ShotMissionItem>
        get() = shots.filter { it.status == ShotStatus.PENDING || it.status == ShotStatus.LATER }

    val capturedShots: List<ShotMissionItem>
        get() = shots.filter { it.status == ShotStatus.CAPTURED || it.isCompleted }

    val opportunityShots: List<ShotMissionItem>
        get() = shots.filter { it.status == ShotStatus.OPPORTUNITY }

    val remainingCoverageCount: Int
        get() = shots.count { it.status == ShotStatus.PENDING || it.status == ShotStatus.CURRENT || it.status == ShotStatus.OPPORTUNITY || it.status == ShotStatus.LATER }

    /**
     * Injects a generic user-reported event (e.g. "special train arrived", "cultural speech started")
     * as a high-priority dynamic opportunity shot without hardcoding rigid 3-shot templates.
     */
    fun injectGenericEvent(
        event: UserReportedEvent,
        preferredFps: CameraFrameRate = CameraFrameRate.FPS_60
    ): ShotMission {
        val eventTitle = event.description.trim()
        val eventClean = if (eventTitle.length > 32) eventTitle.take(30) + "..." else eventTitle

        val opportunityShot = ShotMissionItem(
            id = "opp_${event.id}",
            sequenceNumber = 1,
            title = "⚡ $eventClean (Live Opportunity)",
            description = "High-priority occurrence: $eventTitle",
            shotType = CameraShotType.MEDIUM,
            preferredFrameRate = preferredFps,
            preferredResolution = CameraResolution.FULL_HD_1080,
            subjectHint = eventTitle,
            compositionHint = "Follow action smoothly in dynamic framing",
            status = ShotStatus.OPPORTUNITY,
            priority = 100,
            eventId = event.id
        )

        // Demote previous CURRENT to PENDING if active
        val updatedShots = shots.map { shot ->
            if (shot.status == ShotStatus.CURRENT) shot.copy(status = ShotStatus.PENDING) else shot
        }.toMutableList()

        // Insert new opportunity shot at the top
        updatedShots.add(0, opportunityShot)

        return this.copy(
            shots = updatedShots,
            currentShotIndex = 0,
            activeEvent = event
        )
    }

    /**
     * Dynamically selects the best shot as CURRENT based on what is actually visible right now,
     * without forcing an unavailable shot (e.g. exterior when inside).
     */
    fun selectBestShotForVisibleEvidence(
        visibleLabelsAndObjects: List<String>
    ): ShotMission {
        if (visibleLabelsAndObjects.isEmpty() || shots.isEmpty()) return this

        val candidates = shots.filter {
            it.status == ShotStatus.PENDING || it.status == ShotStatus.CURRENT ||
                    it.status == ShotStatus.OPPORTUNITY || it.status == ShotStatus.LATER
        }
        if (candidates.isEmpty()) return this

        var bestShotId: String? = null
        var highestMatchScore = 0

        for (shot in candidates) {
            val hintTokens = (shot.subjectHint + " " + shot.title).lowercase().split(" ", ",", "-", "/").filter { it.length > 2 }
            var score = 0
            for (visible in visibleLabelsAndObjects) {
                val visLower = visible.lowercase()
                if (hintTokens.any { token -> visLower.contains(token) || token.contains(visLower) }) {
                    score += 10
                }
            }
            if (shot.status == ShotStatus.OPPORTUNITY) {
                score += 20
            }
            if (score > highestMatchScore) {
                highestMatchScore = score
                bestShotId = shot.id
            }
        }

        if (bestShotId != null && highestMatchScore > 0) {
            return promoteShot(bestShotId)
        }

        return this
    }

    /**
     * Promotes a specific shot to CURRENT and sets previous CURRENT to PENDING.
     */
    fun promoteShot(shotId: String): ShotMission {
        val targetIdx = shots.indexOfFirst { it.id == shotId }
        if (targetIdx == -1) return this

        val updatedShots = shots.map { shot ->
            when {
                shot.id == shotId -> shot.copy(
                    // A completed shot can be selected again for a retake. Keep
                    // isCompleted/capturedMediaUri so plan progress does not roll back.
                    status = if (shot.status == ShotStatus.OPPORTUNITY) ShotStatus.OPPORTUNITY else ShotStatus.CURRENT
                )
                shot.status == ShotStatus.CURRENT -> shot.copy(
                    // If the previous current shot was already completed, restore it
                    // to CAPTURED rather than turning it back into a pending shot.
                    status = if (shot.isCompleted || shot.capturedMediaUri != null) ShotStatus.CAPTURED else ShotStatus.PENDING
                )
                else -> shot
            }
        }
        return this.copy(shots = updatedShots, currentShotIndex = targetIdx)
    }

    /**
     * Marks a shot completed and advances to the next remaining pending or opportunity shot.
     */
    fun markShotCaptured(shotId: String, uri: String): ShotMission {
        val targetIdx = shots.indexOfFirst { it.id == shotId }
        val updatedShots = shots.mapIndexed { idx, shot ->
            if (shot.id == shotId || (targetIdx == -1 && idx == currentShotIndex)) {
                shot.copy(status = ShotStatus.CAPTURED, isCompleted = true, capturedMediaUri = uri)
            } else {
                shot
            }
        }.toMutableList()

        // Find next opportunity, pending, or later shot
        val nextIdx = updatedShots.indexOfFirst { it.status == ShotStatus.OPPORTUNITY || it.status == ShotStatus.PENDING || it.status == ShotStatus.LATER }
        if (nextIdx != -1) {
            val nextShot = updatedShots[nextIdx]
            updatedShots[nextIdx] = nextShot.copy(status = ShotStatus.CURRENT)
        }

        return this.copy(
            shots = updatedShots,
            currentShotIndex = if (nextIdx != -1) nextIdx else currentShotIndex
        )
    }

    fun skipCurrentShot(): ShotMission {
        val current = currentShot ?: return this
        val updatedShots = shots.map { shot ->
            if (shot.id == current.id) shot.copy(status = ShotStatus.SKIPPED) else shot
        }.toMutableList()

        val nextIdx = updatedShots.indexOfFirst { it.status == ShotStatus.PENDING || it.status == ShotStatus.OPPORTUNITY || it.status == ShotStatus.LATER }
        if (nextIdx != -1) {
            updatedShots[nextIdx] = updatedShots[nextIdx].copy(status = ShotStatus.CURRENT)
        }

        return this.copy(
            shots = updatedShots,
            currentShotIndex = if (nextIdx != -1) nextIdx else currentShotIndex
        )
    }
}
