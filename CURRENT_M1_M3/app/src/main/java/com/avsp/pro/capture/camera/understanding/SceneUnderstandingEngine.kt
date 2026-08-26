package com.avsp.pro.capture.camera.understanding

import com.avsp.pro.capture.camera.vision.VisualAnalysisResult

data class VisualEvidenceItem(
    val label: String,
    val confidence: Float,
    val source: String = "ModelInference"
)

/**
 * Layer B: Scene Understanding.
 * Strictly separates:
 * 1. USER CONTEXT (reported by human)
 * 2. MODEL EVIDENCE (measured by ML Kit Object Detector & Image Labeler)
 * 3. INFERENCE (honest logical derivation preserving uncertainty without faking scene labels)
 */
data class SceneUnderstandingResult(
    val userProvidedContext: String?,
    val detectedVisualEvidence: List<VisualEvidenceItem>,
    val inferredSceneSummary: String,
    val contextEvidenceAlignmentScore: Float,
    val isContextVisuallyConfirmed: Boolean,
    val dominantVisualCategory: String
)

interface SceneUnderstandingEngine {
    fun evaluateScene(
        userContext: String?,
        visualAnalysis: VisualAnalysisResult
    ): SceneUnderstandingResult
}

class DefaultSceneUnderstandingEngine : SceneUnderstandingEngine {

    override fun evaluateScene(
        userContext: String?,
        visualAnalysis: VisualAnalysisResult
    ): SceneUnderstandingResult {
        val evidenceList = mutableListOf<VisualEvidenceItem>()

        // 1. Gather genuine model-detected labels and object classifications
        for (obj in visualAnalysis.detectedObjects) {
            for (label in obj.labels) {
                if (label.confidence >= 0.35f) {
                    evidenceList.add(
                        VisualEvidenceItem(
                            label = label.text,
                            confidence = label.confidence,
                            source = "ObjectDetector"
                        )
                    )
                }
            }
            if (obj.primaryLabel.isNotBlank() && obj.primaryLabel != "Object" && obj.primaryLabel != "Detected Object") {
                evidenceList.add(
                    VisualEvidenceItem(
                        label = obj.primaryLabel,
                        confidence = obj.confidence,
                        source = "ObjectDetector"
                    )
                )
            }
        }

        for (lbl in visualAnalysis.visibleLabels) {
            if (lbl.confidence >= 0.35f) {
                evidenceList.add(
                    VisualEvidenceItem(
                        label = lbl.text,
                        confidence = lbl.confidence,
                        source = "ImageLabeler"
                    )
                )
            }
        }

        // Deduplicate evidence items by label, retaining highest confidence
        val distinctEvidence = evidenceList
            .groupBy { it.label.lowercase() }
            .values
            .map { group -> group.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }

        val userContextClean = userContext?.trim()?.takeIf { it.isNotBlank() }

        // 2. Generic Semantic Alignment Evaluation (Token overlap & substring containment)
        var matchScoreSum = 0.0f
        var matchCount = 0

        if (userContextClean != null && distinctEvidence.isNotEmpty()) {
            val userTokens = userContextClean.lowercase()
                .split(" ", ",", "-", "/", "_", ".", "'", "\"")
                .filter { it.length >= 3 }

            for (evidence in distinctEvidence) {
                val evLower = evidence.label.lowercase()
                val isMatched = userTokens.any { token ->
                    evLower.contains(token) || token.contains(evLower) ||
                            (token.length >= 4 && evLower.startsWith(token.take(4))) ||
                            (evLower.length >= 4 && token.startsWith(evLower.take(4)))
                }

                if (isMatched) {
                    matchScoreSum += evidence.confidence
                    matchCount++
                }
            }
        }

        val alignmentScore = if (matchCount > 0) (matchScoreSum / matchCount).coerceIn(0.0f, 1.0f) else 0.0f
        val isConfirmed = alignmentScore >= 0.40f

        // 3. Dominant Category from highest confidence real visual evidence
        val dominantCategory = distinctEvidence.firstOrNull()?.label ?: "Unclassified Scene"

        // 4. Honest Inferred Summary
        val evidenceSummary = distinctEvidence.take(2).joinToString { "${it.label} (${(it.confidence * 100).toInt()}%)" }

        val sceneSummary = when {
            userContextClean != null && isConfirmed ->
                "Visually Supported: \"$userContextClean\" [Evidence: $evidenceSummary]"
            userContextClean != null && distinctEvidence.isNotEmpty() ->
                "User Context: \"$userContextClean\" [Unmatched Evidence: $evidenceSummary]"
            userContextClean != null ->
                "User Context: \"$userContextClean\" [Awaiting Visual Evidence]"
            distinctEvidence.isNotEmpty() ->
                "Observed Evidence: $evidenceSummary"
            else ->
                "Live Viewfinder (Awaiting Visual Evidence)"
        }

        return SceneUnderstandingResult(
            userProvidedContext = userContextClean,
            detectedVisualEvidence = distinctEvidence,
            inferredSceneSummary = sceneSummary,
            contextEvidenceAlignmentScore = alignmentScore,
            isContextVisuallyConfirmed = isConfirmed,
            dominantVisualCategory = dominantCategory
        )
    }
}
