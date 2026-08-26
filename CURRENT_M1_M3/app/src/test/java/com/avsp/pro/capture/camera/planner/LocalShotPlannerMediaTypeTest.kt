package com.avsp.pro.capture.camera.planner

import com.avsp.pro.capture.camera.guided.GuidedCapturePlanAdapter
import com.avsp.pro.capture.camera.guided.GuidedClipMediaType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * LocalShotPlanner media-type classification for AVSP Guided Capture.
 * Adapter must preserve planner decisions unchanged.
 */
class LocalShotPlannerMediaTypeTest {

    private val planner = LocalShotPlanner()

    @Test
    fun standardIntroWideMediumCloseAreVideo() = runBlocking {
        val plan = planner.createPlan("general coverage for a short film")
        assertThat(plan.intent).isEqualTo(CaptureIntent.GENERAL_COVERAGE)
        assertThat(plan.shots.map { it.title })
            .containsExactly("Intro", "Wide", "Medium", "Close")
            .inOrder()
        assertThat(plan.shots.map { it.mediaType })
            .containsExactly(
                PlannedMediaType.VIDEO,
                PlannedMediaType.VIDEO,
                PlannedMediaType.VIDEO,
                PlannedMediaType.VIDEO
            )
            .inOrder()

        val session = GuidedCapturePlanAdapter.fromMasterShotPlanOrSampleFallback(plan)
        assertThat(session.usedSampleFallback).isFalse()
        assertThat(session.template.clips.map { it.clipName })
            .containsExactly("Intro", "Wide", "Medium", "Close")
            .inOrder()
        assertThat(session.template.clips.map { it.mediaType })
            .containsExactly(
                GuidedClipMediaType.VIDEO,
                GuidedClipMediaType.VIDEO,
                GuidedClipMediaType.VIDEO,
                GuidedClipMediaType.VIDEO
            )
            .inOrder()
    }

    @Test
    fun genuineStillPhotoShotsRemainPhoto() = runBlocking {
        val portrait = planner.createPlan("take my photo selfie portrait of me")
        assertThat(portrait.shots.first { it.title == "Take My Photo" }.mediaType)
            .isEqualTo(PlannedMediaType.PHOTO)
        assertThat(portrait.shots.first { it.title == "Final Portrait Moment" }.mediaType)
            .isEqualTo(PlannedMediaType.PHOTO)

        val document = planner.createPlan("scan this document receipt form")
        assertThat(document.shots).hasSize(1)
        assertThat(document.shots.single().mediaType).isEqualTo(PlannedMediaType.PHOTO)
        assertThat(document.shots.single().title).isEqualTo("Document")

        val food = planner.createPlan("cooking chicken recipe")
        assertThat(food.shots.first { it.title == "Food detail" }.mediaType)
            .isEqualTo(PlannedMediaType.PHOTO)
        assertThat(food.shots.first { it.title == "Final presentation" }.mediaType)
            .isEqualTo(PlannedMediaType.PHOTO)
    }

    @Test
    fun mixedEventPlanIsPhotoVideoPhotoVideo() = runBlocking {
        val plan = planner.createPlan("wedding ceremony festival speech")
        assertThat(plan.shots.map { it.mediaType })
            .containsExactly(
                PlannedMediaType.PHOTO,
                PlannedMediaType.VIDEO,
                PlannedMediaType.PHOTO,
                PlannedMediaType.VIDEO
            )
            .inOrder()
        assertThat(plan.shots.map { it.title })
            .containsExactly("Guest still", "Event establishing", "Event detail", "Main action")
            .inOrder()
    }

    @Test
    fun adapterPreservesEachMediaTypeUnchanged() = runBlocking {
        val plan = planner.createPlan("product review unboxing")
        assertThat(plan.shots.map { it.mediaType })
            .containsExactly(
                PlannedMediaType.PHOTO,
                PlannedMediaType.VIDEO,
                PlannedMediaType.PHOTO,
                PlannedMediaType.VIDEO
            )
            .inOrder()

        val session = GuidedCapturePlanAdapter.fromMasterShotPlanOrSampleFallback(plan)
        assertThat(session.usedSampleFallback).isFalse()
        assertThat(session.template.clips).hasSize(plan.shots.size)
        plan.shots.zip(session.template.clips).forEach { (planned, clip) ->
            val expected = when (planned.mediaType) {
                PlannedMediaType.PHOTO -> GuidedClipMediaType.PHOTO
                PlannedMediaType.VIDEO -> GuidedClipMediaType.VIDEO
            }
            assertThat(clip.mediaType).isEqualTo(expected)
            assertThat(clip.clipName).isEqualTo(planned.title)
        }
    }

    @Test
    fun cinematicEstablishingShotsAreVideoNotPhoto() = runBlocking {
        val food = planner.createPlan("cooking chicken recipe")
        assertThat(food.shots.first { it.title == "Cooking setup" }.mediaType)
            .isEqualTo(PlannedMediaType.VIDEO)

        val temple = planner.createPlan("temple heritage visit")
        assertThat(temple.shots.first { it.title == "Entrance" }.mediaType)
            .isEqualTo(PlannedMediaType.VIDEO)
        assertThat(temple.shots.first { it.title == "Architecture" }.mediaType)
            .isEqualTo(PlannedMediaType.VIDEO)
        assertThat(temple.shots.first { it.title == "Important detail" }.mediaType)
            .isEqualTo(PlannedMediaType.PHOTO)

        val landscape = planner.createPlan("landscape scenery sunset")
        assertThat(landscape.shots.first { it.title == "Establishing view" }.mediaType)
            .isEqualTo(PlannedMediaType.VIDEO)
        assertThat(landscape.shots.first { it.title == "Visual detail" }.mediaType)
            .isEqualTo(PlannedMediaType.PHOTO)
    }
}
