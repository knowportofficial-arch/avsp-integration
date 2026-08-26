package com.avsp.pro.capture.camera.mission

import com.avsp.pro.capture.camera.candidate.DefaultCandidateRanker
import com.avsp.pro.capture.camera.candidate.DefaultShotCandidateGenerator
import com.avsp.pro.capture.camera.candidate.ShotCandidate
import com.avsp.pro.capture.camera.candidate.ShotCandidateGenerator
import com.avsp.pro.capture.camera.candidate.ShotMemory
import com.avsp.pro.capture.camera.model.CameraCapabilities
import com.avsp.pro.capture.camera.model.CameraFrameRate
import com.avsp.pro.capture.camera.model.CameraResolution
import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.understanding.SceneUnderstandingResult
import com.avsp.pro.capture.camera.vision.DetectedObjectEntity
import com.avsp.pro.capture.camera.vision.VisionLabel
import com.avsp.pro.capture.camera.vision.VisualAnalysisResult
import java.util.UUID

/**
 * Generic Evidence-Driven AI Shot Mission & Coverage Plan Generator.
 * Synthesizes dynamic cinematographic coverage plans from user context, live visual evidence,
 * detected subjects, spontaneous events, and hardware capabilities WITHOUT hardcoded templates.
 */
interface ShotMissionGenerator {
    fun generateDynamicPlan(
        userContext: String?,
        visualAnalysis: VisualAnalysisResult = VisualAnalysisResult(),
        sceneEvidence: SceneUnderstandingResult? = null,
        currentEvent: UserReportedEvent? = null,
        shotMemory: ShotMemory = ShotMemory(),
        capabilities: CameraCapabilities? = null
    ): ShotMission

    fun createGenericEventPlan(event: UserReportedEvent, capabilities: CameraCapabilities? = null): ShotMission

    fun getPresetMissions(): List<ShotMission>
}

class DefaultShotMissionGenerator(
    private val candidateGenerator: ShotCandidateGenerator = DefaultShotCandidateGenerator(),
    private val candidateRanker: DefaultCandidateRanker = DefaultCandidateRanker()
) : ShotMissionGenerator {

    override fun generateDynamicPlan(
        userContext: String?,
        visualAnalysis: VisualAnalysisResult,
        sceneEvidence: SceneUnderstandingResult?,
        currentEvent: UserReportedEvent?,
        shotMemory: ShotMemory,
        capabilities: CameraCapabilities?
    ): ShotMission {
        val missionId = "plan_${UUID.randomUUID().toString().take(8)}"

        // 1. Generate evidence-driven candidates
        val candidates = candidateGenerator.generateCandidates(
            userContext = userContext,
            visualAnalysis = visualAnalysis,
            sceneEvidence = sceneEvidence,
            currentEvent = currentEvent,
            shotMemory = shotMemory,
            capabilities = capabilities
        )

        // 2. Rank candidates to select the best CURRENT shot based on real inputs
        val rankingResult = candidateRanker.rankCandidates(
            candidates = candidates,
            userContext = userContext,
            visualAnalysis = visualAnalysis,
            sceneEvidence = sceneEvidence,
            currentEvent = currentEvent,
            shotMemory = shotMemory
        )

        val cleanContext = userContext?.trim()?.takeIf { it.isNotBlank() }
        val normalizedContext = cleanContext?.lowercase()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val isPersonalPhotoRequest = listOf(
            "take my photo", "take a photo of me", "take my picture", "take my portrait",
            "my photo", "my picture", "portrait of me", "selfie",
            "amar photo nao", "amar chobi tolo", "amar chobi nao", "amar chobi tule dao",
            "personal photo plan", "establish personal photo plan", "take a personal photo", "personal portrait"
        ).any(normalizedContext::contains)
        val title = when {
            isPersonalPhotoRequest -> "Take My Photo"
            cleanContext != null -> "$cleanContext Coverage Plan"
            else -> "Dynamic Evidence Coverage Plan"
        }
        val description = if (isPersonalPhotoRequest) {
            rankingResult.bestCandidate?.reason ?: "Point the camera toward yourself and follow the on-screen guidance."
        } else rankingResult.nowVsLaterSummary

        // 3. Convert ranked candidates to ShotMissionItems
        val missionItems = rankingResult.rankedCandidates.mapIndexed { idx, candidate ->
            ShotMissionItem(
                id = candidate.id,
                sequenceNumber = idx + 1,
                title = candidate.objective,
                description = candidate.reason,
                shotType = candidate.shotType,
                preferredFrameRate = candidate.preferredFrameRate,
                preferredResolution = candidate.preferredResolution,
                subjectHint = candidate.targetSubject,
                compositionHint = candidate.requiredComposition,
                status = candidate.status,
                priority = candidate.priority,
                isCompleted = candidate.status == ShotStatus.CAPTURED,
                capturedMediaUri = candidate.capturedMediaUri,
                eventId = candidate.eventRelevance
            )
        }

        val currentIdx = missionItems.indexOfFirst { it.status == ShotStatus.CURRENT || it.status == ShotStatus.OPPORTUNITY }
            .takeIf { it != -1 } ?: 0

        return ShotMission(
            id = missionId,
            title = title,
            contextDescription = description,
            shots = missionItems,
            currentShotIndex = currentIdx,
            activeEvent = currentEvent
        )
    }

    override fun createGenericEventPlan(event: UserReportedEvent, capabilities: CameraCapabilities?): ShotMission {
        val fps60Supported = capabilities?.is60FpsSupported ?: true
        val fps = if (fps60Supported) CameraFrameRate.FPS_60 else CameraFrameRate.FPS_30
        val baseMission = ShotMission(
            id = "plan_event_${event.id}",
            title = "⚡ ${event.description}",
            contextDescription = "High-priority spontaneous occurrence reported: ${event.description}",
            shots = emptyList()
        )
        return baseMission.injectGenericEvent(event, fps)
    }

    override fun getPresetMissions(): List<ShotMission> {
        // Starter contextual goals that feed into dynamic evidence-driven candidate generator
        return listOf(
            ShotMission(
                id = "preset_workspace",
                title = "Workspace & Creative Station",
                contextDescription = "Dynamic coverage for studio, desk setup, hardware, and creative work",
                shots = listOf(
                    ShotMissionItem(
                        id = "shot_ws_env",
                        sequenceNumber = 1,
                        title = "Workspace Environment (Wide)",
                        description = "Establishing wide view of full workstation setup",
                        shotType = CameraShotType.WIDE,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Workspace",
                        compositionHint = "Level horizon with desk leading lines",
                        status = ShotStatus.PENDING,
                        priority = 30
                    ),
                    ShotMissionItem(
                        id = "shot_ws_core",
                        sequenceNumber = 2,
                        title = "Primary Workstation Subject (Medium)",
                        description = "Medium shot framing central device or display",
                        shotType = CameraShotType.MEDIUM,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Screen",
                        compositionHint = "Rule of thirds alignment with 45% occupancy",
                        status = ShotStatus.PENDING,
                        priority = 35
                    ),
                    ShotMissionItem(
                        id = "shot_ws_detail",
                        sequenceNumber = 3,
                        title = "Hardware / Keyboard Detail (Close)",
                        description = "Close detail showcasing input devices, textures, or materials",
                        shotType = CameraShotType.CLOSE,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Keyboard",
                        compositionHint = "Tight macro focus on distinctive element",
                        status = ShotStatus.PENDING,
                        priority = 25
                    )
                )
            ),
            ShotMission(
                id = "preset_station",
                title = "Transit & Railway Station",
                contextDescription = "Coverage for transit terminals, passenger areas, and arriving vehicles",
                shots = listOf(
                    ShotMissionItem(
                        id = "shot_st_plat",
                        sequenceNumber = 1,
                        title = "Platform & Passenger Activity (Medium)",
                        description = "Medium shot capturing passenger flow and platform environment",
                        shotType = CameraShotType.MEDIUM,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Platform",
                        compositionHint = "Frame passengers along platform perspective lines",
                        status = ShotStatus.PENDING,
                        priority = 35
                    ),
                    ShotMissionItem(
                        id = "shot_st_sign",
                        sequenceNumber = 2,
                        title = "Station Signage & Departure Detail (Close)",
                        description = "Close detail of station identity and transit signage",
                        shotType = CameraShotType.CLOSE,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Sign",
                        compositionHint = "Lock sharp focus on legible text",
                        status = ShotStatus.PENDING,
                        priority = 25
                    ),
                    ShotMissionItem(
                        id = "shot_st_ext",
                        sequenceNumber = 3,
                        title = "Station Architecture & Facade (Wide)",
                        description = "Wide exterior establishing view of station building",
                        shotType = CameraShotType.WIDE,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Building",
                        compositionHint = "Wide architectural perspective",
                        status = ShotStatus.PENDING,
                        priority = 20
                    )
                )
            ),
            ShotMission(
                id = "preset_gathering",
                title = "Cultural Event & Gathering",
                contextDescription = "Live gathering, presentation, speech, or cultural celebration",
                shots = listOf(
                    ShotMissionItem(
                        id = "shot_ev_action",
                        sequenceNumber = 1,
                        title = "Primary Event Action (Medium)",
                        description = "Medium shot capturing primary speaker, performance, or ceremony",
                        shotType = CameraShotType.MEDIUM,
                        preferredFrameRate = CameraFrameRate.FPS_60,
                        subjectHint = "Person",
                        compositionHint = "Headroom balanced with rule of thirds",
                        status = ShotStatus.PENDING,
                        priority = 40
                    ),
                    ShotMissionItem(
                        id = "shot_ev_crowd",
                        sequenceNumber = 2,
                        title = "Crowd & Venue Atmosphere (Wide)",
                        description = "Wide view capturing audience engagement and venue environment",
                        shotType = CameraShotType.WIDE,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Audience",
                        compositionHint = "Wide perspective framing stage and gathering",
                        status = ShotStatus.PENDING,
                        priority = 30
                    ),
                    ShotMissionItem(
                        id = "shot_ev_detail",
                        sequenceNumber = 3,
                        title = "Event Characteristic Detail (Close)",
                        description = "Close detail of ceremonial object, instrument, or decoration",
                        shotType = CameraShotType.CLOSE,
                        preferredFrameRate = CameraFrameRate.FPS_30,
                        subjectHint = "Detail",
                        compositionHint = "Tight focal isolation",
                        status = ShotStatus.PENDING,
                        priority = 20
                    )
                )
            )
        )
    }
}
