package com.avsp.pro.capture.camera.understanding

import android.graphics.RectF
import com.avsp.pro.capture.camera.detector.DetectedSubject
import com.avsp.pro.capture.camera.vision.DetectedObjectEntity
import com.avsp.pro.capture.camera.vision.VisualAnalysisResult
import kotlin.math.abs
import kotlin.math.hypot

data class SubjectUnderstandingResult(
    val targetSubjectName: String,
    val primaryTargetSubject: DetectedSubject?,
    val isTargetSubjectDetected: Boolean,
    val matchConfidence: Float,
    val explanation: String,
    val candidateCount: Int
)

/**
 * Layer B: Generic Subject Understanding Engine.
 * Evaluates semantic relevance of visible ML Kit detected subjects against cinematographic objectives,
 * user intent, and spontaneous events WITHOUT hardcoded category lookup tables.
 */
interface SubjectUnderstandingEngine {
    fun evaluateSubject(
        targetSubjectHint: String?,
        activeEventText: String?,
        userContext: String?,
        visualAnalysis: VisualAnalysisResult
    ): SubjectUnderstandingResult
}

class DefaultSubjectUnderstandingEngine : SubjectUnderstandingEngine {

    override fun evaluateSubject(
        targetSubjectHint: String?,
        activeEventText: String?,
        userContext: String?,
        visualAnalysis: VisualAnalysisResult
    ): SubjectUnderstandingResult {
        // M1.5.5: once the cinematographer has selected a live candidate, its concrete
        // detected subject is the tracking target. The user's event description remains
        // contextual evidence; it must not override the actual visible target with a
        // sentence such as "a special train has arrived".
        val targetName = targetSubjectHint?.trim()?.takeIf { it.isNotBlank() }
            ?: activeEventText?.trim()?.takeIf { it.isNotBlank() }
            ?: userContext?.trim()?.takeIf { it.isNotBlank() }
            ?: "Subject"

        val targetTokens = targetName.lowercase()
            .split(" ", ",", "-", "/", "_", ".", "'", "\"")
            .filter { it.length >= 3 }

        val detectedEntities = visualAnalysis.detectedObjects
        val visibleLabels = visualAnalysis.visibleLabels

        // If no real ML Kit entities or labels exist in frame
        if (detectedEntities.isEmpty() && visibleLabels.isEmpty()) {
            return SubjectUnderstandingResult(
                targetSubjectName = targetName,
                primaryTargetSubject = null,
                isTargetSubjectDetected = false,
                matchConfidence = 0.0f,
                explanation = "No objects or visual subjects detected in frame",
                candidateCount = 0
            )
        }

        // Generic Scored Candidate
        data class ScoredCandidate(
            val entity: DetectedObjectEntity,
            val semanticScore: Float,
            val totalScore: Float,
            val matchedLabel: String
        )

        val candidates = mutableListOf<ScoredCandidate>()

        for (entity in detectedEntities) {
            // Exclude pure fallback contours from qualifying as semantic targets unless generic mode
            val isFallbackContour = entity.primaryLabel.contains("Contour", ignoreCase = true)

            val allEntityLabels = (entity.labels.map { it.text } + entity.primaryLabel).filter { it.isNotBlank() }

            var bestSemanticMatch = 0.0f
            var bestLabel = entity.primaryLabel

            for (lbl in allEntityLabels) {
                val lblLower = lbl.lowercase()
                var tokenMatchScore = 0.0f

                for (token in targetTokens) {
                    val isSubstring = lblLower.contains(token) || token.contains(lblLower)
                    val isPrefix = (token.length >= 4 && lblLower.startsWith(token.take(4))) ||
                            (lblLower.length >= 4 && token.startsWith(lblLower.take(4)))

                    if (isSubstring || isPrefix) {
                        tokenMatchScore += 1.0f
                    }
                }

                if (tokenMatchScore > 0f) {
                    val score = entity.confidence * (1.0f + (tokenMatchScore * 0.5f))
                    if (score > bestSemanticMatch) {
                        bestSemanticMatch = score
                        bestLabel = lbl
                    }
                }
            }

            // Also check global scene labels if entity label didn't directly match
            if (bestSemanticMatch == 0.0f && !isFallbackContour) {
                for (vLabel in visibleLabels) {
                    val vLower = vLabel.text.lowercase()
                    if (targetTokens.any { token -> vLower.contains(token) || token.contains(vLower) }) {
                        bestSemanticMatch = vLabel.confidence * 0.75f
                        bestLabel = vLabel.text
                        break
                    }
                }
            }

            // Optical composition scores (Centering and Frame Size Appropriateness)
            val distFromCenter = hypot(entity.normalizedCenterX - 0.5f, entity.normalizedCenterY - 0.5f)
            val centerBonus = (1.0f - distFromCenter).coerceIn(0.0f, 1.0f) * 0.25f
            val sizeBonus = if (entity.normalizedWidth in 0.08f..0.92f) 0.15f else 0.0f

            val totalScore = bestSemanticMatch + centerBonus + sizeBonus

            if (!isFallbackContour || targetTokens.isEmpty()) {
                candidates.add(
                    ScoredCandidate(
                        entity = entity,
                        semanticScore = bestSemanticMatch,
                        totalScore = totalScore,
                        matchedLabel = bestLabel
                    )
                )
            }
        }

        val topCandidate = candidates.maxByOrNull { it.totalScore }

        // If no candidate exists or the top candidate's match score is insufficient
        val isGenericSubjectTarget = targetTokens.isEmpty() || targetName.equals("Subject", ignoreCase = true)
        val isTargetDetected = when {
            topCandidate == null -> false
            isGenericSubjectTarget -> topCandidate.entity.confidence >= 0.50f
            else -> topCandidate.semanticScore >= 0.35f
        }

        if (topCandidate == null || !isTargetDetected) {
            val visibleEvidenceNames = (detectedEntities.map { it.primaryLabel } + visibleLabels.map { it.text })
                .filter { it.isNotBlank() && !it.contains("Contour", ignoreCase = true) }
                .distinct()
                .take(2)

            val explanation = if (visibleEvidenceNames.isNotEmpty()) {
                "Visible objects (${visibleEvidenceNames.joinToString()}) do not match target \"$targetName\""
            } else {
                "Target \"$targetName\" is not detected in camera frame"
            }

            return SubjectUnderstandingResult(
                targetSubjectName = targetName,
                primaryTargetSubject = null,
                isTargetSubjectDetected = false,
                matchConfidence = 0.0f,
                explanation = explanation,
                candidateCount = candidates.size
            )
        }

        val detectedSubject = DetectedSubject(
            type = topCandidate.matchedLabel,
            confidence = topCandidate.entity.confidence.coerceIn(0.0f, 1.0f),
            normalizedCenterX = topCandidate.entity.normalizedCenterX,
            normalizedCenterY = topCandidate.entity.normalizedCenterY,
            normalizedWidth = topCandidate.entity.normalizedWidth,
            normalizedHeight = topCandidate.entity.normalizedHeight,
            boundingBox = topCandidate.entity.boundingBox,
            isRealDetection = true,
            label = topCandidate.matchedLabel,
            edgeEnergy = visualAnalysis.luminanceVariance.toFloat(),
            luminanceContrast = abs(visualAnalysis.luminanceMean.toFloat() - 128f)
        )

        val explanation = "Identified visible \"${topCandidate.matchedLabel}\" (Confidence: ${(topCandidate.entity.confidence * 100).toInt()}%)"

        return SubjectUnderstandingResult(
            targetSubjectName = targetName,
            primaryTargetSubject = detectedSubject,
            isTargetSubjectDetected = true,
            matchConfidence = topCandidate.entity.confidence,
            explanation = explanation,
            candidateCount = candidates.size
        )
    }
}
