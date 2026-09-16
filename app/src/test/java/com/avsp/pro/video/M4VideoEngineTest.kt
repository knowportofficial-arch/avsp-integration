package com.avsp.pro.video

import com.avsp.pro.audio.integration.AudioToVideoHandoff
import com.avsp.pro.audio.integration.VideoAudioSegmentRef
import com.avsp.pro.video.contract.VideoRenderPlanFactory
import com.avsp.pro.video.validation.VideoValidator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M4VideoEngineTest {
    private fun handoff() = AudioToVideoHandoff(
        projectId = "p",
        scriptId = "s",
        audioPackageId = "a",
        language = "bn",
        provider = "mock",
        voiceId = "bn-mock",
        totalDurationMs = 60_000L,
        segments = listOf(
            VideoAudioSegmentRef("a0", "scn_hook", 0, "generated/audio/a/a0.wav", 0, 8_000, 8_000, "bn", "bn-mock"),
            VideoAudioSegmentRef("a1", "scn_01", 1, "generated/audio/a/a1.wav", 8_000, 45_000, 37_000, "bn", "bn-mock"),
            VideoAudioSegmentRef("a2", "scn_outro", 2, "generated/audio/a/a2.wav", 45_000, 60_000, 15_000, "bn", "bn-mock")
        ),
        audioPackageVersion = "1.0"
    )

    @Test
    fun factoryPreservesAudioTimelineAndSceneOrder() {
        val plan = VideoRenderPlanFactory.fromAudio(
            handoff(),
            mapOf("scn_hook" to "media/hook.jpg", "scn_01" to "media/scene.jpg", "scn_outro" to "media/outro.jpg")
        ).copy(scenes = VideoRenderPlanFactory.fromAudio(handoff(), emptyMap()).scenes.mapIndexed { index, scene ->
            scene.copy(overlayText = "Scene ${index + 1}")
        })
        assertThat(plan.totalDurationMs).isEqualTo(60_000L)
        assertThat(plan.scenes.map { it.sceneId }).containsExactly("scn_hook", "scn_01", "scn_outro").inOrder()
        assertThat(VideoValidator.validatePlan(plan).isValid).isTrue()
    }

    @Test
    fun invalidTimelineIsRejectedBeforeRender() {
        val plan = VideoRenderPlanFactory.fromAudio(handoff(), emptyMap()).copy(
            scenes = VideoRenderPlanFactory.fromAudio(handoff(), emptyMap()).scenes.mapIndexed { i, s ->
                if (i == 1) s.copy(startMs = s.startMs + 100) else s
            }
        )
        val result = VideoValidator.validatePlan(plan)
        assertThat(result.isValid).isFalse()
        assertThat(result.errors.joinToString(" ")).contains("Timeline gap/overlap")
    }

    @Test
    fun outputDurationAndDimensionsMustMatch() {
        val ok = VideoValidator.validateOutput(1080, 1920, 60_000, 1080, 1920, 60_000, true, true)
        assertThat(ok.isValid).isTrue()
        val bad = VideoValidator.validateOutput(1080, 1920, 60_000, 1920, 1080, 60_000, true, true)
        assertThat(bad.isValid).isFalse()
    }

    @Test
    fun rotatedPortraitOutputUsesDisplayDimensions() {
        val portrait = VideoValidator.validateOutput(
            1080, 1920, 60_000,
            1920, 1080, 60_000,
            true, true, rotationDegrees = 90
        )
        assertThat(portrait.isValid).isTrue()
    }

    @Test
    fun rotatedFourByFiveOutputUsesDisplayDimensions() {
        val portrait = VideoValidator.validateOutput(
            1080, 1350, 60_000,
            1350, 1080, 60_000,
            true, true, rotationDegrees = 270
        )
        assertThat(portrait.isValid).isTrue()
    }

    @Test
    fun unrotatedLandscapeIsNotAcceptedAsPortrait() {
        val wrong = VideoValidator.validateOutput(
            1080, 1920, 60_000,
            1920, 1080, 60_000,
            true, true, rotationDegrees = 0
        )
        assertThat(wrong.isValid).isFalse()
    }
}
