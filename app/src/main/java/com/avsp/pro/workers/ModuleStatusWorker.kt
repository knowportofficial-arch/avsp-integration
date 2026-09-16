package com.avsp.pro.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Common worker/status mechanism for future modules.
 * M1 does NOT implement M8 automation — this is only a reusable status bridge.
 */
class ModuleStatusWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val moduleId = inputData.getString(KEY_MODULE_ID) ?: return Result.failure(
            workDataOf(KEY_ERROR to "moduleId required")
        )
        // Future modules (M2–M9) will enqueue real work. M1 only validates wiring.
        return Result.success(
            workDataOf(
                KEY_MODULE_ID to moduleId,
                KEY_STATUS to "ACKNOWLEDGED"
            )
        )
    }

    companion object {
        const val KEY_MODULE_ID = "moduleId"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val UNIQUE_PREFIX = "avsp_module_status_"
    }
}
