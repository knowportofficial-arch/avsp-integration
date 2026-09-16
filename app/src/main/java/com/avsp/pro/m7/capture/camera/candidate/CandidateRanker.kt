package com.avsp.pro.m7.capture.camera.candidate

import com.avsp.pro.m7.capture.camera.analyzer.FocusState
import com.avsp.pro.m7.capture.camera.analyzer.StabilityState
import com.avsp.pro.m7.capture.camera.mission.ShotStatus
import com.avsp.pro.m7.capture.camera.mission.UserReportedEvent
import com.avsp.pro.m7.capture.camera.understanding.SceneUnderstandingResult
import com.avsp.pro.m7.capture.camera.vision.VisualAnalysisResult

data class RankedCandidatesResult(
    val bestCandidate: ShotCandidate?,
    val rankedCandidates: List<ShotCandidate>,
    val nowVsLaterSummary: String,
    val rankingExplanation: String
)

interface CandidateRanker {
    fun rankCandidates(
        candidates: List<ShotCandidate>,
        userContext: String?,
        visualAnalysis: VisualAnalysisResult,
        sceneEvidence: SceneUnderstandingResult? = null,
        currentEvent: UserReportedEvent? = null,
        shotMemory: ShotMemory = ShotMemory()
    ): RankedCandidatesResult
}

/**
 * AVSP M1.5.4 Evidence-Driven Candidate Ranker.
 * Evaluates live candidates across 11 structured factors:
 * 1. User Intent Relevance
 * 2. Visual Evidence Strength
 * 3. Target Confidence
 * 4. Target Visibility (In-Frame vs Out-of-Frame)
 * 5. Cinematographic Value
 * 6. Event Priority
 * 7. Opportunity / Time Sensitivity
 * 8. Coverage Gap
 * 9. Novelty
 * 10. Framing Feasibility
 * 11. Physical Accessibility
 */
class DefaultCandidateRanker : CandidateRanker {

    override fun rankCandidates(
        candidates: List<ShotCandidate>,
        userContext: String?,
        visualAnalysis: VisualAnalysisResult,
        sceneEvidence: SceneUnderstandingResult?,
        currentEvent: UserReportedEvent?,
        shotMemory: ShotMemory
    ): RankedCandidatesResult {
        if (candidates.isEmpty()) {
            return RankedCandidatesResult(
                bestCandidate = null,
                rankedCandidates = emptyList(),
                nowVsLaterSummary = "No candidates available",
                rankingExplanation = "Waiting for visual evidence"
            )
        }

        val contextTokens = userContext?.lowercase()
            ?.split(" ", ",", "-", "/", "_", ".")
            ?.filter { it.length >= 3 } ?: emptyList()

        val visibleLabels = (visualAnalysis.detectedObjects.map { it.primaryLabel } + visualAnalysis.visibleLabels.map { it.text }).map { it.lowercase() }

        // Score each candidate objectively based on the 11 structured factors
        val scoredCandidates = candidates.map { candidate ->
            val isAlreadyCaptured = candidate.status == ShotStatus.CAPTURED ||
                    candidate.isCompleted ||
                    shotMemory.isObjectiveCaptured(candidate.objective) ||
                    shotMemory.isAlreadyCaptured(candidate.targetSubject, candidate.shotType)

            var score = candidate.priority.toFloat()

            // 1. Target Visibility & In-Frame Presence (+50 pts)
            val isVisuallyInFrame = candidate.isCurrentlyVisible ||
                    visibleLabels.any { v -> candidate.targetSubject.lowercase().contains(v) || v.contains(candidate.targetSubject.lowercase()) }

            if (isVisuallyInFrame) {
                score += 50f
            } else {
                score -= 30f // Out-of-frame candidates are penalized for CURRENT selection
            }

            // 2. Visual Evidence Strength & ML Kit Confidence (+30 pts max)
            val evidenceConfidence = candidate.confidence.coerceIn(0.0f, 1.0f)
            score += evidenceConfidence * 30f

            // 3. User Intent Relevance (+25 pts)
            val matchesContextTokens = contextTokens.any { c ->
                candidate.targetSubject.contains(c, ignoreCase = true) || c.contains(candidate.targetSubject, ignoreCase = true)
            }
            val alignmentScore = sceneEvidence?.contextEvidenceAlignmentScore ?: 0.0f
            if (matchesContextTokens || alignmentScore >= 0.40f) {
                score += 25f * (if (alignmentScore > 0f) alignmentScore else 0.8f)
            }

            // 4. Live Spontaneous Event Priority (+70 pts)
            if (candidate.eventRelevance != null && currentEvent != null && !currentEvent.isHandled) {
                score += 70f
                if (isVisuallyInFrame) {
                    score += 35f // Highest urgency when event target is physically framed in camera
                }
            }

            // 5. Cinematographic Value (+20 pts)
            score += candidate.cinematographicValue * 20f

            // 6. Opportunity / Motion / Time Sensitivity (+15 pts)
            score += candidate.opportunityScore * 15f

            // 7. Framing Feasibility & Optical Alignment (+15 pts)
            if (visualAnalysis.stabilityState == StabilityState.STABLE && visualAnalysis.focusState == FocusState.FOCUSED && !visualAnalysis.isHorizonTilted) {
                score += 15f
            } else if (visualAnalysis.stabilityState == StabilityState.UNSTABLE) {
                score -= 10f
            }

            // 8. Physical Accessibility (+15 pts)
            score += candidate.accessibility * 15f

            // 9. Coverage Gap & Novelty (+20 pts for fresh uncaptured shot, -300 pts if already captured)
            if (isAlreadyCaptured) {
                score -= 300f
            } else {
                score += candidate.novelty * 20f
            }

            candidate.copy(
                score = score,
                isCurrentlyVisible = isVisuallyInFrame,
                status = if (isAlreadyCaptured) ShotStatus.CAPTURED else candidate.status
            )
        }

        // Sort candidates: highest score first
        val sorted = scoredCandidates.sortedByDescending { it.score }

        // Select BEST CURRENT CANDIDATE:
        // Must NOT be already captured, must be VISIBLE in the live frame, and have a positive score
        val bestCandidate = sorted.firstOrNull {
            it.status != ShotStatus.CAPTURED && it.isCurrentlyVisible && it.score > 0f
        }

        // Finalize status assignments across the coverage plan:
        // - bestCandidate -> CURRENT (or OPPORTUNITY)
        // - already captured -> CAPTURED
        // - visible but not current -> PENDING
        // - not visible -> LATER (or PENDING)
        val finalizedList = sorted.map { cand ->
            when {
                cand.status == ShotStatus.CAPTURED -> cand
                bestCandidate != null && cand.id == bestCandidate.id -> {
                    if (cand.eventRelevance != null) cand.copy(status = ShotStatus.OPPORTUNITY) else cand.copy(status = ShotStatus.CURRENT)
                }
                cand.isCurrentlyVisible -> cand.copy(status = ShotStatus.PENDING)
                else -> cand.copy(status = ShotStatus.LATER)
            }
        }

        val pendingCount = finalizedList.count { it.status == ShotStatus.PENDING }
        val laterCount = finalizedList.count { it.status == ShotStatus.LATER }
        val capturedCount = finalizedList.count { it.status == ShotStatus.CAPTURED }

        val nowVsLaterSummary = when {
            bestCandidate != null ->
                "NOW: ${bestCandidate.objective} (${bestCandidate.shotType.displayName}) | PENDING: $pendingCount visible, $laterCount later, $capturedCount captured"
            finalizedList.any { it.status != ShotStatus.CAPTURED } ->
                "WAITING FOR VISUAL EVIDENCE — $laterCount shots in plan"
            else ->
                "All planned coverage completed ($capturedCount captured)"
        }

        val explanation = bestCandidate?.reason
            ?: (if (finalizedList.any { it.isCurrentlyVisible }) "Refining visible candidates" else "No visible subjects detected in camera frame")

        return RankedCandidatesResult(
            bestCandidate = bestCandidate,
            rankedCandidates = finalizedList,
            nowVsLaterSummary = nowVsLaterSummary,
            rankingExplanation = explanation
        )
    }
}
