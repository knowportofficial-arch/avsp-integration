package com.avsp.pro.capture.camera.planner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps backend vision state independent from the capture mode.
 *
 * AI AUTO, AI SHOT and MANUAL are UI/capture decisions. Vision analysis may
 * continue in all three modes.
 */
class BackendVisionCoordinator(
    private val policy: ShotApplicabilityPolicy = ShotApplicabilityPolicy()
) {

    private val _latest = MutableStateFlow<VisionShotApplicability?>(null)
    val latest: StateFlow<VisionShotApplicability?> = _latest.asStateFlow()

    private val _decision = MutableStateFlow<ApplicabilityDecision?>(null)
    val decision: StateFlow<ApplicabilityDecision?> = _decision.asStateFlow()

    fun onVisionResult(
        shot: PlannedShot,
        result: VisionShotApplicability
    ) {
        _latest.value = result
        _decision.value = policy.evaluate(shot, result)
    }

    fun clear() {
        _latest.value = null
        _decision.value = null
    }
}
