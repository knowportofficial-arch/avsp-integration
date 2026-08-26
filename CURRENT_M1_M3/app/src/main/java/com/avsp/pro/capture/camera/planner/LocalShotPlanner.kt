package com.avsp.pro.capture.camera.planner

import java.util.UUID

/** Deterministic fallback planner. No network and no API key required. */
class LocalShotPlanner : ShotPlanner {
    override suspend fun createPlan(request: String): MasterShotPlan {
        val text = request.trim().lowercase()
        val intent = when {
            listOf("my photo", "take my photo", "selfie", "portrait of me", "personal photo plan", "establish personal photo plan", "personal portrait").any(text::contains) -> CaptureIntent.PERSON_PORTRAIT
            listOf("chicken", "food", "cook", "cooking", "recipe", "dish").any(text::contains) -> CaptureIntent.FOOD
            listOf("temple", "church", "mosque", "monument", "heritage").any(text::contains) -> CaptureIntent.TEMPLE
            listOf("product", "item", "review", "unboxing").any(text::contains) -> CaptureIntent.PRODUCT
            listOf("landscape", "scenery", "nature", "sunset", "view").any(text::contains) -> CaptureIntent.LANDSCAPE
            listOf("document", "paper", "receipt", "form").any(text::contains) -> CaptureIntent.DOCUMENT
            listOf("event", "function", "ceremony", "festival", "speech").any(text::contains) -> CaptureIntent.EVENT
            else -> CaptureIntent.GENERAL_COVERAGE
        }

        val shots = when (intent) {
            CaptureIntent.PERSON_PORTRAIT -> listOf(
                shot("Take My Photo", "Your main personal photo", PlannedMediaType.PHOTO, PlannedFraming.PORTRAIT, "person", "Keep yourself comfortably framed. Look naturally at the camera.", 100),
                shot("Natural Portrait Video", "A short natural moving portrait", PlannedMediaType.VIDEO, PlannedFraming.PORTRAIT, "person", "Keep yourself in frame and make a small natural movement.", 90),
                shot("Photo With Surroundings", "Show you together with the place", PlannedMediaType.PHOTO, PlannedFraming.ENVIRONMENTAL, "person and surroundings", "Keep yourself visible while including enough of the surroundings to tell where you are.", 80),
                shot("Final Portrait Moment", "A relaxed final portrait", PlannedMediaType.PHOTO, PlannedFraming.PORTRAIT, "person", "Relax, look naturally at the camera, and hold for the capture.", 85)
            )
            CaptureIntent.FOOD -> listOf(
                shot("Cooking setup", "Establish the cooking environment", PlannedMediaType.PHOTO, PlannedFraming.WIDE, "food preparation area", "Show the workspace and ingredients", 80),
                shot("Main cooking action", "Capture the key preparation action", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "cooking action", "Keep hands and main action visible", 90),
                shot("Food detail", "Show texture and finished detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "food", "Tight clean composition; sharp texture", 85),
                shot("Final presentation", "Show the finished dish attractively", PlannedMediaType.PHOTO, PlannedFraming.DETAIL, "finished dish", "Clean background and attractive angle", 95)
            )
            CaptureIntent.TEMPLE -> listOf(
                shot("Entrance", "Establish the location", PlannedMediaType.PHOTO, PlannedFraming.WIDE, "temple entrance", "Show entrance and surrounding context", 90),
                shot("Architecture", "Show the main architectural character", PlannedMediaType.PHOTO, PlannedFraming.WIDE, "temple architecture", "Use strong lines and stable framing", 80),
                shot("Interior", "Show the interior environment", PlannedMediaType.PHOTO, PlannedFraming.MEDIUM, "temple interior", "Only applicable when an interior is visible", 85),
                shot("Important detail", "Capture a distinctive visual detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "temple detail", "Use a tight composition", 75),
                shot("Ambient activity", "Capture useful atmosphere", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "people/activity", "Record a stable natural moment", 60)
            )
            CaptureIntent.PRODUCT -> listOf(
                shot("Product overview", "Establish the complete product", PlannedMediaType.PHOTO, PlannedFraming.MEDIUM, "product", "Keep the entire product visible", 90),
                shot("Product detail", "Show important features or texture", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "product detail", "Focus on a distinctive feature", 85),
                shot("Use demonstration", "Show the product being used", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "product in use", "Keep the action and product visible", 80)
            )
            CaptureIntent.LANDSCAPE -> listOf(
                shot("Establishing view", "Show the complete scene", PlannedMediaType.PHOTO, PlannedFraming.WIDE, "landscape", "Level horizon and strong foreground", 90),
                shot("Visual detail", "Capture a distinctive detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "landscape detail", "Use foreground interest", 65)
            )
            CaptureIntent.DOCUMENT -> listOf(
                shot("Document", "Capture the complete document", PlannedMediaType.PHOTO, PlannedFraming.DOCUMENT, "document", "Keep all edges visible and text legible", 100)
            )
            CaptureIntent.EVENT -> listOf(
                shot("Event establishing", "Show the venue and scale", PlannedMediaType.PHOTO, PlannedFraming.WIDE, "event venue", "Show the environment and activity", 85),
                shot("Main action", "Capture the principal activity", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "event action", "Keep the main action centred and stable", 95),
                shot("Event detail", "Capture a characteristic detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "event detail", "Find a distinctive visual", 70)
            )
            else -> listOf(
                shot("Establishing view", "Show the overall context", PlannedMediaType.PHOTO, PlannedFraming.WIDE, "scene", "Give the viewer location context", 80),
                shot("Main subject", "Show the primary subject clearly", PlannedMediaType.PHOTO, PlannedFraming.MEDIUM, "main subject", "Keep the main subject prominent", 90),
                shot("Detail", "Capture a useful close detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "distinctive detail", "Look for a visually informative detail", 70)
            )
        }

        return MasterShotPlan(
            id = "plan_${UUID.randomUUID().toString().take(8)}",
            userRequest = request,
            intent = intent,
            title = titleFor(intent),
            summary = "Local fallback plan. Shots remain applicable only when the requested subject/scene is actually present.",
            shots = shots.mapIndexed { index, s -> s.copy(sequence = index + 1) },
            planner = "AVSP_LOCAL_FALLBACK"
        )
    }

    private fun shot(title: String, purpose: String, media: PlannedMediaType, framing: PlannedFraming, subject: String, guidance: String, priority: Int) =
        PlannedShot(
            id = "shot_${UUID.randomUUID().toString().take(8)}",
            sequence = 0,
            title = title,
            purpose = purpose,
            mediaType = media,
            framing = framing,
            subject = subject,
            guidance = guidance,
            priority = priority,
            required = true,
            applicabilityHint = "Skip if the subject is not present or the scene makes this shot impossible."
        )

    private fun titleFor(intent: CaptureIntent) = when (intent) {
        CaptureIntent.PERSON_PORTRAIT -> "TAKE MY PHOTO"
        CaptureIntent.FOOD -> "Food / Cooking Coverage Plan"
        CaptureIntent.TEMPLE -> "Temple Coverage Plan"
        CaptureIntent.PRODUCT -> "Product Coverage Plan"
        CaptureIntent.LANDSCAPE -> "Landscape Coverage Plan"
        CaptureIntent.DOCUMENT -> "Document Capture Plan"
        CaptureIntent.EVENT -> "Event Coverage Plan"
        else -> "AI Coverage Plan"
    }
}
