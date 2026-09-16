package com.avsp.pro.m7.capture.camera.planner

/** User-facing intent understood by the planning layer. */
enum class CaptureIntent {
    GENERAL_COVERAGE,
    PERSON_PORTRAIT,
    FOOD,
    PLACE,
    TEMPLE,
    PRODUCT,
    DOCUMENT,
    EVENT,
    LANDSCAPE,
    CUSTOM
}

enum class PlannedMediaType { PHOTO, VIDEO }

enum class PlannedFraming {
    WIDE,
    MEDIUM,
    CLOSE,
    PORTRAIT,
    FULL_BODY,
    ENVIRONMENTAL,
    DETAIL,
    MACRO,
    DOCUMENT,
    TRACKING,
    UNSPECIFIED
}

enum class PlannedShotStatus { PENDING, OPTIONAL, SKIPPED, CAPTURED, IN_PROGRESS, RETAKE }

data class PlannedShot(
    val id: String,
    val sequence: Int,
    val title: String,
    val purpose: String,
    val mediaType: PlannedMediaType,
    val framing: PlannedFraming,
    val subject: String,
    val guidance: String,
    val priority: Int = 50,
    val required: Boolean = true,
    val status: PlannedShotStatus = PlannedShotStatus.PENDING,
    val applicabilityHint: String = ""
)

data class MasterShotPlan(
    val id: String,
    val userRequest: String,
    val intent: CaptureIntent,
    val title: String,
    val summary: String,
    val shots: List<PlannedShot>,
    val planner: String,
    val createdAt: Long = System.currentTimeMillis()
)

