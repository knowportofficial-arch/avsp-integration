package com.avsp.pro.capture.camera.planner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime state manager for the Master Shot List.
 *
 * A plan is independent of capture mode. The user can execute shot 1 with
 * AUTO, shot 2 with MANUAL, shot 3 with SHOT, etc. Manual extra captures
 * never change the planned-shot count.
 */
class MasterShotListManager {

    private val _plan = MutableStateFlow<MasterShotPlan?>(null)
    val plan: StateFlow<MasterShotPlan?> = _plan.asStateFlow()

    private val _selectedShotId = MutableStateFlow<String?>(null)
    val selectedShotId: StateFlow<String?> = _selectedShotId.asStateFlow()

    private val _executionMode = MutableStateFlow(ShotExecutionMode.AUTO)
    val executionMode: StateFlow<ShotExecutionMode> = _executionMode.asStateFlow()

    private val _completions = MutableStateFlow<Map<String, ShotCompletion>>(emptyMap())
    val completions: StateFlow<Map<String, ShotCompletion>> = _completions.asStateFlow()

    fun loadPlan(masterPlan: MasterShotPlan) {
        _plan.value = masterPlan
        _selectedShotId.value = masterPlan.shots.firstOrNull()?.id
        _completions.value = emptyMap()
    }

    fun selectShot(shotId: String) {
        if (_plan.value?.shots?.any { it.id == shotId } == true) {
            _selectedShotId.value = shotId
        }
    }

    fun selectShot(index: Int) {
        val shots = _plan.value?.shots ?: return
        if (index in shots.indices) selectShot(shots[index].id)
    }

    fun setExecutionMode(mode: ShotExecutionMode) {
        _executionMode.value = mode
    }

    fun markInProgress(shotId: String = requireSelectedShot()) {
        updateCompletion(ShotCompletion(
            shotId = shotId,
            status = PlannedShotStatus.IN_PROGRESS,
            executionMode = _executionMode.value
        ))
    }

    fun markCaptured(
        shotId: String = requireSelectedShot(),
        mediaUri: String,
        mode: ShotExecutionMode = _executionMode.value
    ) {
        updateCompletion(
            ShotCompletion(
                shotId = shotId,
                status = PlannedShotStatus.CAPTURED,
                executionMode = mode,
                mediaUri = mediaUri
            )
        )
        selectNextPending()
    }

    fun markSkipped(
        shotId: String = requireSelectedShot(),
        reason: String
    ) {
        updateCompletion(
            ShotCompletion(
                shotId = shotId,
                status = PlannedShotStatus.SKIPPED,
                executionMode = _executionMode.value,
                note = reason
            )
        )
        selectNextPending()
    }

    fun requestRetake(shotId: String = requireSelectedShot(), note: String? = null) {
        updateCompletion(
            ShotCompletion(
                shotId = shotId,
                status = PlannedShotStatus.RETAKE,
                executionMode = _executionMode.value,
                note = note
            )
        )
    }

    /**
     * If a manual capture is independently useful, it must not be forced
     * into the predefined shot list. The caller should save it as an extra
     * media item; this manager deliberately does not alter mission progress.
     */
    fun recordManualExtra() {
        // Intentionally no-op: extra captures belong to Media Core.
    }

    fun capturedCount(): Int =
        _completions.value.values.count { it.status == PlannedShotStatus.CAPTURED }

    fun skippedCount(): Int =
        _completions.value.values.count { it.status == PlannedShotStatus.SKIPPED }

    fun pendingCount(): Int =
        (_plan.value?.shots?.size ?: 0) - capturedCount() - skippedCount()

    fun isComplete(): Boolean =
        pendingCount() <= 0

    private fun updateCompletion(completion: ShotCompletion) {
        _completions.update { it + (completion.shotId to completion) }
    }

    private fun selectNextPending() {
        val shots = _plan.value?.shots ?: return
        val next = shots.firstOrNull {
            val status = _completions.value[it.id]?.status
            status == null || status == PlannedShotStatus.RETAKE || status == PlannedShotStatus.IN_PROGRESS
        }
        if (next != null) _selectedShotId.value = next.id
    }

    private fun requireSelectedShot(): String =
        _selectedShotId.value ?: error("No shot is selected")
}
