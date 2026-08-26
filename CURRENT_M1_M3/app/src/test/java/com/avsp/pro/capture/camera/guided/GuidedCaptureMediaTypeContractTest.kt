package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraAspectRatio
import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.model.CaptureOrientation
import com.avsp.pro.capture.camera.planner.LocalShotPlanner
import com.avsp.pro.capture.camera.planner.MasterShotPlan
import com.avsp.pro.capture.camera.planner.PlannedFraming
import com.avsp.pro.capture.camera.planner.PlannedMediaType
import com.avsp.pro.capture.camera.planner.PlannedShot
import com.avsp.pro.capture.camera.planner.CaptureIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Media-type contract: PHOTO stays PHOTO, VIDEO stays VIDEO.
 * PHOTO-only plans use explicit V2 sample fallback (Intro/Wide/Medium/Close all VIDEO).
 */
class GuidedCaptureMediaTypeContractTest {

    @Test
    fun plannedVideoMapsToVideoAndWouldEnterRecordingBranch() {
        val plan = MasterShotPlan(
            id = "plan_video",
            userRequest = "test",
            intent = CaptureIntent.EVENT,
            title = "Mixed",
            summary = "test",
            shots = listOf(
                PlannedShot(
                    id = "s1",
                    sequence = 1,
                    title = "Main action",
                    purpose = "Capture action",
                    mediaType = PlannedMediaType.VIDEO,
                    framing = PlannedFraming.MEDIUM,
                    subject = "action",
                    guidance = "Keep centred",
                    priority = 95,
                    required = true,
                    applicabilityHint = ""
                ),
                PlannedShot(
                    id = "s2",
                    sequence = 2,
                    title = "Still detail",
                    purpose = "Detail",
                    mediaType = PlannedMediaType.PHOTO,
                    framing = PlannedFraming.CLOSE,
                    subject = "detail",
                    guidance = "Tight",
                    priority = 70,
                    required = true,
                    applicabilityHint = ""
                )
            ),
            planner = "TEST"
        )
        val session = GuidedCapturePlanAdapter.fromMasterShotPlanOrSampleFallback(plan)
        assertThat(session.usedSampleFallback).isFalse()
        assertThat(session.template.clips).hasSize(2)

        val video = session.template.clips[0]
        assertThat(video.mediaType).isEqualTo(GuidedClipMediaType.VIDEO)
        assertThat(video.captureInstruction()).contains("s")
        assertThat(video.captureInstruction()).doesNotContain(" · Photo · ")
        // ViewModel branch selector contract
        assertThat(recordingPhaseFor(video.mediaType)).isEqualTo(GuidedCapturePhase.RECORDING)

        val photo = session.template.clips[1]
        assertThat(photo.mediaType).isEqualTo(GuidedClipMediaType.PHOTO)
        assertThat(photo.captureInstruction()).contains(" · Photo · ")
        assertThat(photo.captureInstruction()).doesNotContain("5s")
        assertThat(recordingPhaseFor(photo.mediaType)).isNotEqualTo(GuidedCapturePhase.RECORDING)
    }

    @Test
    fun plannedPhotoMapsToPhotoWithoutVideoProgressBarPhase() {
        val clip = GuidedClipSpec(
            clipName = "Entrance",
            category = "WIDE",
            targetDurationSeconds = 1,
            aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
            orientation = CaptureOrientation.PORTRAIT,
            mediaType = GuidedClipMediaType.PHOTO,
            framingType = CameraShotType.WIDE
        )
        assertThat(clip.mediaType).isEqualTo(GuidedClipMediaType.PHOTO)
        assertThat(clip.captureInstruction()).isEqualTo(
            "Entrance · WIDE · Photo · Wide (Establishing, environment & landscape shot)"
        )
        assertThat(recordingPhaseFor(clip.mediaType)).isEqualTo(GuidedCapturePhase.SAVING)
    }

    @Test
    fun photoOnlyPlanUsesExplicitSampleFallbackAllVideo() = runBlocking {
        // Document intent remains a genuine PHOTO-only plan.
        val plan = LocalShotPlanner().createPlan("scan this document receipt form")
        assertThat(plan.shots.all { it.mediaType == PlannedMediaType.PHOTO }).isTrue()

        val session = GuidedCapturePlanAdapter.fromMasterShotPlanOrSampleFallback(plan)
        assertThat(session.usedSampleFallback).isTrue()
        assertThat(session.template.clips.map { it.clipName })
            .containsExactly("Intro", "Wide", "Medium", "Close")
            .inOrder()
        assertThat(session.template.clips.all { it.mediaType == GuidedClipMediaType.VIDEO }).isTrue()
        assertThat(session.template.clips.first().captureInstruction()).isEqualTo(
            "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
        )
    }

    @Test
    fun mixedFoodPlanPreservesPhotoAndVideoWithoutForcedConversion() = runBlocking {
        val plan = LocalShotPlanner().createPlan("cooking chicken recipe")
        assertThat(plan.shots.any { it.mediaType == PlannedMediaType.VIDEO }).isTrue()
        assertThat(plan.shots.any { it.mediaType == PlannedMediaType.PHOTO }).isTrue()

        val session = GuidedCapturePlanAdapter.fromMasterShotPlanOrSampleFallback(plan)
        assertThat(session.usedSampleFallback).isFalse()

        val byName = session.template.clips.associateBy { it.clipName }
        // Establishing cooking coverage is cinematic VIDEO; stills stay PHOTO.
        assertThat(byName["Cooking setup"]!!.mediaType).isEqualTo(GuidedClipMediaType.VIDEO)
        assertThat(byName["Cooking setup"]!!.captureInstruction()).contains("s · ")
        assertThat(byName["Main cooking action"]!!.mediaType).isEqualTo(GuidedClipMediaType.VIDEO)
        assertThat(byName["Main cooking action"]!!.captureInstruction()).contains("s · ")
        assertThat(byName["Food detail"]!!.mediaType).isEqualTo(GuidedClipMediaType.PHOTO)
        assertThat(byName["Food detail"]!!.captureInstruction()).contains(" · Photo · ")
        assertThat(byName["Final presentation"]!!.mediaType).isEqualTo(GuidedClipMediaType.PHOTO)
    }

    @Test
    fun v2SampleFallbackRecordingContractIntact() {
        val sample = GuidedCaptureTemplate.sample()
        assertThat(sample.clips).hasSize(4)
        assertThat(sample.clips.map { it.clipName })
            .containsExactly("Intro", "Wide", "Medium", "Close")
            .inOrder()
        assertThat(sample.clips.map { it.targetDurationSeconds })
            .containsExactly(5, 10, 8, 5)
            .inOrder()
        sample.clips.forEach { clip ->
            assertThat(clip.mediaType).isEqualTo(GuidedClipMediaType.VIDEO)
            assertThat(recordingPhaseFor(clip.mediaType)).isEqualTo(GuidedCapturePhase.RECORDING)
        }
        val fallback = GuidedCapturePlanAdapter.fromSampleFallback()
        assertThat(fallback.usedSampleFallback).isTrue()
        assertThat(fallback.template.clips.all { it.mediaType == GuidedClipMediaType.VIDEO }).isTrue()
    }

    /**
     * Mirrors ViewModel.startCaptureForCurrentClip media-type branch without CameraX:
     * VIDEO → RECORDING path; PHOTO → SAVING/photo path (never RECORDING progress bar).
     */
    private fun recordingPhaseFor(mediaType: GuidedClipMediaType): GuidedCapturePhase =
        when (mediaType) {
            GuidedClipMediaType.VIDEO -> GuidedCapturePhase.RECORDING
            GuidedClipMediaType.PHOTO -> GuidedCapturePhase.SAVING
        }
}
