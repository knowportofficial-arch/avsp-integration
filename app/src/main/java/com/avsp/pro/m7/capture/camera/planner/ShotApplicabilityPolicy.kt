package com.avsp.pro.m7.capture.camera.planner

/**
 * Policy layer between live vision analysis and the Master Shot List.
 *
 * It decides whether a shot is currently applicable. It does not itself
 * perform object detection; that remains the responsibility of the existing
 * VisionSubjectDetector / ML Kit pipeline.
 */
class ShotApplicabilityPolicy {

    fun evaluate(
        shot: PlannedShot,
        vision: VisionShotApplicability
    ): ApplicabilityDecision {
        if (vision.shotId != shot.id) {
            return ApplicabilityDecision(
                applicable = false,
                shouldSkip = false,
                reason = "Vision result does not belong to selected shot"
            )
        }

        if (!vision.subjectPresent) {
            return ApplicabilityDecision(
                applicable = false,
                shouldSkip = false,
                reason = "Required subject is not currently visible"
            )
        }

        if (vision.qualityScore > 0 && vision.qualityScore < 45) {
            return ApplicabilityDecision(
                applicable = true,
                shouldSkip = false,
                reason = "Subject detected; improve capture quality"
            )
        }

        return ApplicabilityDecision(
            applicable = true,
            shouldSkip = false,
            reason = vision.reason
        )
    }
}

data class ApplicabilityDecision(
    val applicable: Boolean,
    val shouldSkip: Boolean,
    val reason: String? = null
)
