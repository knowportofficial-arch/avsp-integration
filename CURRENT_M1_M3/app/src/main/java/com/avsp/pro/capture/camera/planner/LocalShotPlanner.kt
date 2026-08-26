package com.avsp.pro.capture.camera.planner

import java.util.UUID

/**
 * Deterministic fallback planner. No network and no API key required.
 *
 * AVSP media-type rule (planner decides; Guided Capture adapter must not rewrite):
 *
 * VIDEO — cinematic / moving capture footage for the Guided Capture recording path:
 *   establishing, wide environment, entrance, architecture, interior coverage,
 *   action / process / demonstration / ambient, and the standard
 *   Intro → Wide → Medium → Close coverage sequence.
 *
 * PHOTO — genuine still captures only:
 *   intentional portraits / personal photos, documents, texture / detail stills,
 *   finished-product presentation stills when the shot is meant as a still.
 */
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
                // Genuine stills remain PHOTO; only the moving portrait is VIDEO.
                shot("Take My Photo", "Your main personal photo", PlannedMediaType.PHOTO, PlannedFraming.PORTRAIT, "person", "Keep yourself comfortably framed. Look naturally at the camera.", 100),
                shot("Natural Portrait Video", "A short natural moving portrait", PlannedMediaType.VIDEO, PlannedFraming.PORTRAIT, "person", "Keep yourself in frame and make a small natural movement.", 90),
                shot("Photo With Surroundings", "Show you together with the place", PlannedMediaType.PHOTO, PlannedFraming.ENVIRONMENTAL, "person and surroundings", "Keep yourself visible while including enough of the surroundings to tell where you are.", 80),
                shot("Final Portrait Moment", "A relaxed final portrait", PlannedMediaType.PHOTO, PlannedFraming.PORTRAIT, "person", "Relax, look naturally at the camera, and hold for the capture.", 85)
            )
            CaptureIntent.FOOD -> listOf(
                // Establishing / action = VIDEO; texture & presentation stills = PHOTO.
                shot("Cooking setup", "Establish the cooking environment", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "food preparation area", "Show the workspace and ingredients with a stable establishing move.", 80),
                shot("Main cooking action", "Capture the key preparation action", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "cooking action", "Keep hands and main action visible", 90),
                shot("Food detail", "Show texture and finished detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "food", "Tight clean composition; sharp texture", 85),
                shot("Final presentation", "Show the finished dish attractively", PlannedMediaType.PHOTO, PlannedFraming.DETAIL, "finished dish", "Clean background and attractive angle", 95)
            )
            CaptureIntent.TEMPLE -> listOf(
                // Cinematic coverage = VIDEO; distinctive still detail = PHOTO.
                shot("Entrance", "Establish the location", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "temple entrance", "Show entrance and surrounding context", 90),
                shot("Architecture", "Show the main architectural character", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "temple architecture", "Use strong lines and stable framing", 80),
                shot("Interior", "Show the interior environment", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "temple interior", "Only applicable when an interior is visible", 85),
                shot("Important detail", "Capture a distinctive visual detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "temple detail", "Use a tight still composition", 75),
                shot("Ambient activity", "Capture useful atmosphere", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "people/activity", "Record a stable natural moment", 60)
            )
            CaptureIntent.PRODUCT -> listOf(
                // Catalog / texture stills = PHOTO; moving use demo = VIDEO.
                shot("Product overview", "Establish the complete product as a still", PlannedMediaType.PHOTO, PlannedFraming.MEDIUM, "product", "Keep the entire product visible", 90),
                shot("Use demonstration", "Show the product being used", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "product in use", "Keep the action and product visible", 80),
                shot("Product detail", "Show important features or texture", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "product detail", "Focus on a distinctive feature", 85),
                shot("Unboxing motion", "Capture the unboxing / reveal movement", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "product unboxing", "Keep hands and product in frame during the reveal", 75)
            )
            CaptureIntent.LANDSCAPE -> listOf(
                shot("Establishing view", "Show the complete scene", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "landscape", "Level horizon and strong foreground", 90),
                shot("Visual detail", "Capture a distinctive detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "landscape detail", "Use foreground interest for a still detail", 65)
            )
            CaptureIntent.DOCUMENT -> listOf(
                shot("Document", "Capture the complete document", PlannedMediaType.PHOTO, PlannedFraming.DOCUMENT, "document", "Keep all edges visible and text legible", 100)
            )
            CaptureIntent.EVENT -> listOf(
                // PHOTO → VIDEO → PHOTO → VIDEO mixed coverage.
                shot("Guest still", "A clear still of a key person or guest", PlannedMediaType.PHOTO, PlannedFraming.MEDIUM, "guest", "Frame a natural portrait still", 70),
                shot("Event establishing", "Show the venue and scale", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "event venue", "Show the environment and activity", 85),
                shot("Event detail", "Capture a characteristic detail", PlannedMediaType.PHOTO, PlannedFraming.CLOSE, "event detail", "Find a distinctive still visual", 70),
                shot("Main action", "Capture the principal activity", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "event action", "Keep the main action centred and stable", 95)
            )
            // Standard Guided Capture coverage sequence — product footage is VIDEO.
            else -> listOf(
                shot("Intro", "Open the sequence with context", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "scene intro", "Start with a stable establishing move", 100),
                shot("Wide", "Establish the full environment", PlannedMediaType.VIDEO, PlannedFraming.WIDE, "environment", "Keep the full scene readable", 90),
                shot("Medium", "Feature the primary subject", PlannedMediaType.VIDEO, PlannedFraming.MEDIUM, "main subject", "Keep the main subject prominent", 85),
                shot("Close", "Capture a useful close detail", PlannedMediaType.VIDEO, PlannedFraming.CLOSE, "distinctive detail", "Move in for a clean close beat", 80)
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
