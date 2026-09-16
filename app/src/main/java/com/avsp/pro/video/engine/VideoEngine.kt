package com.avsp.pro.video.engine

import com.avsp.pro.video.contract.VideoRenderPlan
import com.avsp.pro.video.contract.VideoRenderResult
import kotlinx.coroutines.flow.Flow

interface VideoEngine {
    suspend fun render(plan: VideoRenderPlan): VideoRenderResult
    fun observeProgress(): Flow<Int>
    fun cancel()
}
