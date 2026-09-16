package com.avsp.pro.m7.capture.camera.candidate

import android.graphics.RectF
import com.avsp.pro.m7.capture.camera.mission.ShotStatus
import com.avsp.pro.m7.capture.camera.mission.UserReportedEvent
import com.avsp.pro.m7.capture.camera.model.CameraCapabilities
import com.avsp.pro.m7.capture.camera.model.CameraFrameRate
import com.avsp.pro.m7.capture.camera.model.CameraResolution
import com.avsp.pro.m7.capture.camera.model.CameraShotType
import com.avsp.pro.m7.capture.camera.understanding.SceneUnderstandingResult
import com.avsp.pro.m7.capture.camera.vision.DetectedObjectEntity
import com.avsp.pro.m7.capture.camera.vision.VisionLabel
import com.avsp.pro.m7.capture.camera.vision.VisualAnalysisResult
import java.util.UUID
import kotlin.math.hypot

interface ShotCandidateGenerator {
    fun generateCandidates(
        userContext: String?,
        visualAnalysis: VisualAnalysisResult,
        sceneEvidence: SceneUnderstandingResult? = null,
        currentEvent: UserReportedEvent? = null,
        shotMemory: ShotMemory = ShotMemory(),
        capabilities: CameraCapabilities? = null
    ): List<ShotCandidate>
}

/**
 * AVSP M1.5.4 Evidence-Driven Candidate Generator.
 * Eliminates rigid 3-shot templates (Wide + Medium + Close) and hardcoded category presets.
 * Formulates cinematographic candidates strictly from live visual evidence, real detected entities,
 * contextual intent, and spontaneous events with full structured telemetry.
 */
class DefaultShotCandidateGenerator : ShotCandidateGenerator {

    override fun generateCandidates(
        userContext: String?,
        visualAnalysis: VisualAnalysisResult,
        sceneEvidence: SceneUnderstandingResult?,
        currentEvent: UserReportedEvent?,
        shotMemory: ShotMemory,
        capabilities: CameraCapabilities?
    ): List<ShotCandidate> {
        val candidates = mutableListOf<ShotCandidate>()
        val fps60Supported = capabilities?.is60FpsSupported ?: true

        // Extract genuine model-detected objects and high-confidence labels
        val detectedObjects = visualAnalysis.detectedObjects.filter {
            !it.primaryLabel.contains("Contour", ignoreCase = true)
        }
        val visibleLabels = visualAnalysis.visibleLabels.filter { it.confidence >= 0.35f }
        val visibleLabelTexts = (detectedObjects.map { it.primaryLabel } + visibleLabels.map { it.text }).distinct()

        val cleanContext = userContext?.trim()?.takeIf { it.isNotBlank() }
        val contextTokens = cleanContext?.lowercase()
            ?.split(" ", ",", "-", "/", "_", ".")
            ?.filter { it.length >= 3 } ?: emptyList()

        val sceneEvidenceList = sceneEvidence?.detectedVisualEvidence?.map { "${it.label} (${(it.confidence * 100).toInt()}%)" }
            ?: visibleLabelTexts

        // Personal-photo intent is a direct user action, not a generic coverage request.
        // Keep the cinematography choice internal and expose only natural language to the user.
        val isPersonalPhotoRequest = cleanContext?.let { text ->
            val normalized = text.lowercase().replace(Regex("\\s+"), " ").trim()
            listOf(
                "take my photo", "take a photo of me", "take my picture", "take my portrait",
                "my photo", "my picture", "portrait of me", "selfie",
                "amar photo nao", "amar chobi tolo", "amar chobi nao", "amar chobi tule dao",
                "personal photo plan", "establish personal photo plan", "take a personal photo", "personal portrait"
            ).any(normalized::contains)
        } == true

        if (isPersonalPhotoRequest) {
            val person = detectedObjects
                .filter { it.primaryLabel.contains("person", ignoreCase = true) || it.primaryLabel.contains("face", ignoreCase = true) }
                .maxByOrNull { it.confidence }
            val detected = person != null
            val confidence = person?.confidence ?: 0.0f
            val subjectWidth = person?.normalizedWidth ?: 0.5f
            val centerX = person?.normalizedCenterX ?: 0.5f
            candidates.add(
                ShotCandidate(
                    id = "cand_personal_photo",
                    objective = "Take My Photo",
                    targetSubject = "person",
                    detectedLabel = person?.primaryLabel.orEmpty(),
                    confidence = confidence,
                    boundingBox = person?.boundingBox,
                    subjectSize = subjectWidth,
                    subjectPositionX = centerX,
                    subjectPositionY = person?.normalizedCenterY ?: 0.5f,
                    motionMagnitude = visualAnalysis.motionMagnitude.toFloat(),
                    shotType = CameraShotType.MEDIUM,
                    priority = 100,
                    reason = if (detected) "Your face/person is visible — compose the photo naturally." else "Point the camera toward yourself.",
                    requiredComposition = "Keep the person comfortably framed with headroom and a natural background",
                    visualEvidence = person?.let { listOf(it.primaryLabel) } ?: emptyList(),
                    sceneEvidence = sceneEvidenceList,
                    userIntent = cleanContext,
                    isCurrentlyVisible = detected,
                    cinematographicValue = 1.0f,
                    opportunityScore = if (detected) 1.0f else 0.0f,
                    framingFeasibility = if (detected) 0.9f else 0.3f,
                    accessibility = 1.0f,
                    novelty = if (shotMemory.isAlreadyCaptured("person", CameraShotType.MEDIUM)) 0.05f else 1.0f,
                    status = if (detected) ShotStatus.CURRENT else ShotStatus.PENDING,
                    preferredFrameRate = CameraFrameRate.FPS_30
                )
            )
            return candidates
        }

        // =========================================================================
        // 1. DYNAMIC COVERAGE: ESTABLISHING SHOT + LIVE SUBJECT SHOTS
        // =========================================================================
        // The first useful shot for a user-described place is an establishing frame.
        // It does NOT require a particular ML object label ("railway station" may not
        // be returned by the lightweight on-device labeler). It only requires genuine
        // visual evidence. Once captured, subject-level candidates naturally take over.
        if (cleanContext != null && (detectedObjects.isNotEmpty() || visibleLabels.isNotEmpty())) {
            val establishingCaptured = shotMemory.isAlreadyCaptured("$cleanContext Environment", CameraShotType.WIDE)
            candidates.add(
                ShotCandidate(
                    id = "cand_establish_${cleanContext.lowercase().replace(" ", "_").take(24)}",
                    objective = "Establish $cleanContext",
                    targetSubject = "$cleanContext Environment",
                    detectedLabel = visibleLabelTexts.firstOrNull().orEmpty(),
                    confidence = (visibleLabels.maxOfOrNull { it.confidence } ?: detectedObjects.maxOfOrNull { it.confidence } ?: 0.55f),
                    shotType = CameraShotType.WIDE,
                    priority = if (establishingCaptured) 2 else 60,
                    reason = if (establishingCaptured)
                        "Establishing view already captured; find the next useful subject"
                    else
                        "First coverage priority: establish the ${cleanContext.lowercase()} environment from the live scene",
                    requiredComposition = "Wide establishing frame: keep the strongest visible environmental anchor and surrounding context",
                    visualEvidence = visibleLabelTexts.take(5),
                    sceneEvidence = sceneEvidenceList,
                    userIntent = cleanContext,
                    isCurrentlyVisible = true,
                    cinematographicValue = if (establishingCaptured) 0.15f else 0.95f,
                    opportunityScore = if (visualAnalysis.motionMagnitude > 8.0) 0.45f else 0.15f,
                    framingFeasibility = if (visualAnalysis.stabilityState == com.avsp.pro.m7.capture.camera.analyzer.StabilityState.STABLE) 0.95f else 0.55f,
                    accessibility = 1.0f,
                    novelty = if (establishingCaptured) 0.02f else 1.0f,
                    status = if (establishingCaptured) ShotStatus.CAPTURED else ShotStatus.PENDING,
                    preferredFrameRate = if (visualAnalysis.motionMagnitude > 9.0 && fps60Supported) CameraFrameRate.FPS_60 else CameraFrameRate.FPS_30
                )
            )
        }

        // Generate subject candidates from every real detected entity.  Shot type is
        // no longer a single static property of the object: the same visible subject
        // can produce a medium coverage shot and a close detail shot, and the ranker
        // chooses which one is useful based on what has already been captured.
        for (obj in detectedObjects) {
            val label = obj.primaryLabel
            val confidence = obj.confidence
            val width = obj.normalizedWidth
            val area = width * obj.normalizedHeight
            val centerX = obj.normalizedCenterX
            val centerY = obj.normalizedCenterY
            val isFastMotion = visualAnalysis.motionMagnitude > 8.0f
            val isCentered = hypot(centerX - 0.5f, centerY - 0.5f) < 0.25f
            val matchesContext = contextTokens.any { token ->
                label.contains(token, ignoreCase = true) || token.contains(label, ignoreCase = true)
            }
            val basePriority = when {
                matchesContext -> 50
                confidence >= 0.70f -> 38
                else -> 28
            }
            val capturedMedium = shotMemory.isAlreadyCaptured(label, CameraShotType.MEDIUM)
            val capturedClose = shotMemory.isAlreadyCaptured(label, CameraShotType.CLOSE)
            val capturedWide = shotMemory.isAlreadyCaptured(label, CameraShotType.WIDE)

            fun addSubjectCandidate(
                shotType: CameraShotType,
                objective: String,
                reason: String,
                composition: String,
                priority: Int,
                novelty: Float,
                value: Float
            ) {
                val captured = shotMemory.isAlreadyCaptured(label, shotType)
                candidates.add(
                    ShotCandidate(
                        id = "cand_${shotType.name.lowercase()}_${label.lowercase().replace(" ", "_")}_${UUID.randomUUID().toString().take(4)}",
                        objective = objective,
                        targetSubject = label,
                        detectedLabel = label,
                        confidence = confidence,
                        boundingBox = obj.boundingBox,
                        subjectSize = width,
                        subjectPositionX = centerX,
                        subjectPositionY = centerY,
                        motionMagnitude = visualAnalysis.motionMagnitude.toFloat(),
                        shotType = shotType,
                        priority = priority,
                        reason = reason,
                        requiredComposition = composition,
                        visualEvidence = listOf(label),
                        sceneEvidence = sceneEvidenceList,
                        userIntent = cleanContext,
                        eventEvidence = currentEvent?.description,
                        isCurrentlyVisible = true,
                        cinematographicValue = value,
                        opportunityScore = if (isFastMotion) 0.9f else 0.15f,
                        framingFeasibility = if (isCentered && visualAnalysis.stabilityState == com.avsp.pro.m7.capture.camera.analyzer.StabilityState.STABLE) 0.9f else 0.6f,
                        accessibility = 1.0f,
                        novelty = if (captured) 0.02f else novelty,
                        status = if (captured) ShotStatus.CAPTURED else ShotStatus.PENDING,
                        preferredFrameRate = if (isFastMotion && fps60Supported) CameraFrameRate.FPS_60 else CameraFrameRate.FPS_30
                    )
                )
            }

            // Medium = primary human-readable subject coverage.
            if (!capturedMedium || confidence >= 0.50f) {
                addSubjectCandidate(
                    shotType = CameraShotType.MEDIUM,
                    objective = "Cover $label",
                    reason = "Live subject \"$label\" detected; medium coverage preserves subject identity and context",
                    composition = "Frame $label clearly with useful surrounding context; keep the subject off the extreme edges",
                    priority = basePriority,
                    novelty = 0.95f,
                    value = (confidence * 0.55f + if (matchesContext) 0.35f else 0.15f).coerceIn(0.2f, 1f)
                )
            }

            // Close = detail opportunity.  It is deliberately generated from the
            // current object rather than from a hard-coded category.
            if (!capturedClose && confidence >= 0.55f) {
                addSubjectCandidate(
                    shotType = CameraShotType.CLOSE,
                    objective = "Detail $label",
                    reason = "Live $label provides a potential detail shot after the main coverage",
                    composition = "Move closer and isolate the most informative visible detail of $label without clipping it",
                    priority = basePriority - 4,
                    novelty = 0.92f,
                    value = (confidence * 0.50f + 0.30f).coerceIn(0.25f, 1f)
                )
            }

            // A small/distant object can also be useful as a wide contextual element.
            if (!capturedWide && (width < 0.25f || area < 0.08f)) {
                addSubjectCandidate(
                    shotType = CameraShotType.WIDE,
                    objective = "Context with $label",
                    reason = "$label is small in the live frame; preserve its relationship to the surrounding scene",
                    composition = "Keep $label visible while showing enough surrounding environment to explain its context",
                    priority = basePriority - 8,
                    novelty = 0.80f,
                    value = 0.55f
                )
            }
        }

        // =========================================================================
        // 2. GENERATE CANDIDATES FROM DISTINCT VISIBLE SCENE LABELS
        // =========================================================================
        for (vLabel in visibleLabels.take(3)) {
            val labelText = vLabel.text
            // Avoid duplicates with detected objects
            val isAlreadyCovered = candidates.any {
                it.targetSubject.contains(labelText, ignoreCase = true) || labelText.contains(it.targetSubject, ignoreCase = true)
            }

            if (!isAlreadyCovered) {
                val isBroadScene = labelText.contains("room", ignoreCase = true) ||
                        labelText.contains("architecture", ignoreCase = true) ||
                        labelText.contains("building", ignoreCase = true) ||
                        labelText.contains("landscape", ignoreCase = true) ||
                        labelText.contains("sky", ignoreCase = true) ||
                        labelText.contains("interior", ignoreCase = true) ||
                        labelText.contains("platform", ignoreCase = true)

                val shotType = if (isBroadScene) CameraShotType.WIDE else CameraShotType.MEDIUM
                val isCaptured = shotMemory.isAlreadyCaptured(labelText, shotType)
                val matchesContext = contextTokens.any { labelText.contains(it, ignoreCase = true) || it.contains(labelText, ignoreCase = true) }

                candidates.add(
                    ShotCandidate(
                        id = "cand_lbl_${labelText.lowercase().replace(" ", "_")}_${UUID.randomUUID().toString().take(4)}",
                        objective = "Feature visible $labelText",
                        targetSubject = labelText,
                        detectedLabel = labelText,
                        confidence = vLabel.confidence,
                        shotType = shotType,
                        priority = if (matchesContext) 38 else 22,
                        reason = "Scene element \"$labelText\" detected in live frame (${(vLabel.confidence * 100).toInt()}% confidence)",
                        requiredComposition = if (shotType == CameraShotType.WIDE) "Wide panoramic perspective" else "Balanced framing on $labelText",
                        visualEvidence = listOf(labelText),
                        sceneEvidence = sceneEvidenceList,
                        userIntent = cleanContext,
                        isCurrentlyVisible = true,
                        cinematographicValue = vLabel.confidence * 0.7f,
                        accessibility = 0.85f,
                        novelty = if (isCaptured) 0.05f else 0.9f,
                        status = if (isCaptured) ShotStatus.CAPTURED else ShotStatus.PENDING,
                        preferredFrameRate = CameraFrameRate.FPS_30
                    )
                )
            }
        }

        // =========================================================================
        // 3. SPONTANEOUS USER-REPORTED LIVE EVENT (`UserReportedEvent`)
        // =========================================================================
        if (currentEvent != null && !currentEvent.isHandled) {
            val eventDesc = currentEvent.description.trim()
            val eventTokens = eventDesc.lowercase().split(" ", ",", "-", "/").filter { it.length >= 3 }
            val isEventVisuallyDetected = visibleLabelTexts.any { v ->
                eventTokens.any { t -> v.contains(t, ignoreCase = true) || t.contains(v, ignoreCase = true) }
            }

            // Generic high-priority event shot
            candidates.add(
                0, // Prepend at top
                ShotCandidate(
                    id = "cand_event_${currentEvent.id}",
                    objective = "⚡ $eventDesc (Live Occurrence)",
                    targetSubject = eventDesc,
                    detectedLabel = if (isEventVisuallyDetected) visibleLabelTexts.firstOrNull { v -> eventTokens.any { t -> v.contains(t, ignoreCase = true) } } ?: "" else "",
                    confidence = if (isEventVisuallyDetected) 0.85f else 0.40f,
                    shotType = CameraShotType.MEDIUM,
                    priority = if (isEventVisuallyDetected) 100 else 5,
                    reason = if (isEventVisuallyDetected)
                        "⚡ Live event \"$eventDesc\" visually confirmed in frame"
                    else
                        "⚡ Live event \"$eventDesc\" reported — point camera toward event",
                    requiredComposition = "Follow dynamic event action smoothly",
                    visualEvidence = visibleLabelTexts.filter { v -> eventTokens.any { t -> v.contains(t, ignoreCase = true) } },
                    sceneEvidence = sceneEvidenceList,
                    userIntent = cleanContext,
                    eventEvidence = eventDesc,
                    eventRelevance = eventDesc,
                    isCurrentlyVisible = isEventVisuallyDetected,
                    cinematographicValue = 1.0f,
                    opportunityScore = if (isEventVisuallyDetected) 1.0f else 0.0f,
                    framingFeasibility = if (isEventVisuallyDetected) 0.85f else 0.2f,
                    accessibility = if (isEventVisuallyDetected) 1.0f else 0.2f,
                    novelty = 1.0f,
                    status = if (isEventVisuallyDetected) ShotStatus.OPPORTUNITY else ShotStatus.LATER,
                    preferredFrameRate = if (fps60Supported) CameraFrameRate.FPS_60 else CameraFrameRate.FPS_30
                )
            )
        }

        // =========================================================================
        // 4. USER CONTEXT AS PENDING / LATER COVERAGE (NOT FORCED AS CURRENT)
        // =========================================================================
        if (cleanContext != null) {
            val isContextVisuallyMatched = visibleLabelTexts.any { v ->
                contextTokens.any { t -> v.contains(t, ignoreCase = true) || t.contains(v, ignoreCase = true) }
            }

            // If there's an overarching coverage goal not yet captured or visible
            val isContextCaptured = shotMemory.isAlreadyCaptured(cleanContext, CameraShotType.WIDE)
            if (!isContextCaptured && candidates.none { it.targetSubject.contains(cleanContext, ignoreCase = true) }) {
                candidates.add(
                    ShotCandidate(
                        id = "cand_ctx_cov_${UUID.randomUUID().toString().take(4)}",
                        objective = "$cleanContext Overall Establishing",
                        targetSubject = "$cleanContext Environment",
                        shotType = CameraShotType.WIDE,
                        priority = 25,
                        reason = if (isContextVisuallyMatched) "Establishing view for $cleanContext" else "Coverage goal for $cleanContext (Awaiting view)",
                        requiredComposition = "Wide perspective with level horizon",
                        visualEvidence = visibleLabelTexts.filter { v -> contextTokens.any { t -> v.contains(t, ignoreCase = true) } },
                        sceneEvidence = sceneEvidenceList,
                        userIntent = cleanContext,
                        isCurrentlyVisible = isContextVisuallyMatched,
                        cinematographicValue = 0.6f,
                        accessibility = if (isContextVisuallyMatched) 0.85f else 0.35f,
                        novelty = if (isContextCaptured) 0.05f else 0.9f,
                        status = if (isContextVisuallyMatched) ShotStatus.PENDING else ShotStatus.LATER,
                        preferredFrameRate = CameraFrameRate.FPS_30
                    )
                )
            }
        }

        // =========================================================================
        // 5. NO RELEVANT SUBJECT / WAITING FOR VISUAL EVIDENCE
        // =========================================================================
        if (candidates.isEmpty()) {
            candidates.add(
                ShotCandidate(
                    id = "cand_waiting_${UUID.randomUUID().toString().take(4)}",
                    objective = "Waiting for Visual Evidence",
                    targetSubject = "Scene",
                    shotType = CameraShotType.MEDIUM,
                    priority = 5,
                    reason = "No relevant visual subjects detected — point camera toward subject",
                    requiredComposition = "Aim camera at subjects or environment",
                    isCurrentlyVisible = false,
                    cinematographicValue = 0.1f,
                    accessibility = 0.1f,
                    novelty = 0.5f,
                    status = ShotStatus.PENDING,
                    preferredFrameRate = CameraFrameRate.FPS_30
                )
            )
        }

        return candidates
    }
}
