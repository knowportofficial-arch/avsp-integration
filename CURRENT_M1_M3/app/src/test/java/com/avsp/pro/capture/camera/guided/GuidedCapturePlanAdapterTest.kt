package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.planner.LocalShotPlanner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Guided Capture must map real shot plans into the full clip sequence with
 * preserved semantic names — never reduce to framing-only Wide · WIDE labels.
 */
class GuidedCapturePlanAdapterTest {

    @Test
    fun foodPlanPreservesSemanticTitlesAndFullSequence() = runBlocking {
        val plan = LocalShotPlanner().createPlan("cooking chicken recipe")
        val session = GuidedCapturePlanAdapter.fromMasterShotPlan(plan)

        assertThat(session.template.clips).hasSize(plan.shots.size)
        assertThat(session.template.clips.size).isAtLeast(4)

        val names = session.template.clips.map { it.clipName }
        assertThat(names).contains("Cooking setup")
        assertThat(names).contains("Main cooking action")
        assertThat(names).contains("Food detail")
        assertThat(names).contains("Final presentation")

        // Must NOT collapse to framing-only semantic names
        assertThat(names).doesNotContain("WIDE")
        session.template.clips.forEach { clip ->
            val instruction = clip.captureInstruction()
            assertThat(instruction).startsWith("${clip.clipName} · ")
            assertThat(instruction).contains(clip.framingType.displayName)
            assertThat(instruction).contains(clip.framingType.description)
            // Framing codes are technical; semantic title stays primary
            assertThat(clip.clipName).isNotEqualTo(clip.framingType.displayName)
            when (clip.mediaType) {
                GuidedClipMediaType.PHOTO -> {
                    assertThat(instruction).contains(" · Photo · ")
                    assertThat(Regex("""· \d+s ·""").containsMatchIn(instruction)).isFalse()
                }
                GuidedClipMediaType.VIDEO -> {
                    assertThat(Regex("""· \d+s ·""").containsMatchIn(instruction)).isTrue()
                    assertThat(instruction).doesNotContain(" · Photo · ")
                }
            }
        }
    }

    @Test
    fun templePlanExposesLaterShotsBeyondWideMediumClose() = runBlocking {
        val plan = LocalShotPlanner().createPlan("temple heritage visit")
        val session = GuidedCapturePlanAdapter.fromMasterShotPlan(plan)

        assertThat(session.template.clips.size).isAtLeast(5)
        val titles = session.template.clips.map { it.clipName }
        assertThat(titles).contains("Entrance")
        assertThat(titles).contains("Architecture")
        assertThat(titles).contains("Interior")
        assertThat(titles).contains("Important detail")
        assertThat(titles).contains("Ambient activity")

        // Every clip carries mission/shot identity (sceneId not invented)
        session.template.clips.forEach { clip ->
            assertThat(clip.missionId).isEqualTo(session.mission.id)
            assertThat(clip.missionShotId).isNotEmpty()
            assertThat(clip.sceneId).isNull()
            assertThat(clip.takeIndex).isEqualTo(1)
            assertThat(clip.subjectHint).isNotEmpty()
            assertThat(clip.guidanceHint).isNotEmpty()
        }
    }

    @Test
    fun sampleFallbackPreservesIntroNamingContract() {
        val session = GuidedCapturePlanAdapter.fromSampleFallback()
        val intro = session.template.clips.first()
        assertThat(intro.clipName).isEqualTo("Intro")
        assertThat(intro.category).isEqualTo("INTRO")
        assertThat(intro.framingType).isEqualTo(CameraShotType.WIDE)
        assertThat(intro.captureInstruction()).isEqualTo(
            "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
        )
    }

    @Test
    fun zoomComesFromFramingNotHardCodedSequenceOnly() = runBlocking {
        val plan = LocalShotPlanner().createPlan("product review unboxing")
        val session = GuidedCapturePlanAdapter.fromMasterShotPlan(plan)
        session.template.clips.forEach { clip ->
            assertThat(clip.requestedZoomRatio()).isEqualTo(clip.framingType.defaultZoomRatio)
        }
        val medium = session.template.clips.first { it.framingType == CameraShotType.MEDIUM }
        val close = session.template.clips.first { it.framingType == CameraShotType.CLOSE }
        assertThat(medium.requestedZoomRatio()).isEqualTo(1.8f)
        assertThat(close.requestedZoomRatio()).isEqualTo(3.0f)
    }
}
