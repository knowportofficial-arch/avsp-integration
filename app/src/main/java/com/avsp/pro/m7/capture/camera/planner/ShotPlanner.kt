package com.avsp.pro.m7.capture.camera.planner

/**
 * Model-agnostic planning boundary. Gemini is one implementation; a local
 * fallback keeps the camera workflow usable when no API key/network exists.
 */
interface ShotPlanner {
    suspend fun createPlan(request: String): MasterShotPlan
}
