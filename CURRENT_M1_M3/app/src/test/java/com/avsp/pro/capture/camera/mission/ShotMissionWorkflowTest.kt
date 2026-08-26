package com.avsp.pro.capture.camera.mission

import com.avsp.pro.capture.camera.model.CameraFrameRate
import com.avsp.pro.capture.camera.model.CameraResolution
import com.avsp.pro.capture.camera.model.CameraShotType
import org.junit.Assert.*
import org.junit.Test

class ShotMissionWorkflowTest {
    private fun mission(): ShotMission = ShotMission(
        id = "test-plan",
        title = "Test Plan",
        contextDescription = "Test",
        shots = listOf(
            ShotMissionItem("s1", 1, "Shot 1", "First", CameraShotType.MEDIUM, ShotMediaType.PHOTO, CameraFrameRate.FPS_30, CameraResolution.FULL_HD_1080, "person", "center"),
            ShotMissionItem("s2", 2, "Shot 2", "Second", CameraShotType.MEDIUM, ShotMediaType.VIDEO, CameraFrameRate.FPS_30, CameraResolution.FULL_HD_1080, "person", "move")
        ).mapIndexed { i, shot -> shot.copy(status = if (i == 0) ShotStatus.CURRENT else ShotStatus.PENDING) }
    )

    @Test
    fun captureAdvancesToNextShot() {
        val after = mission().markShotCaptured("s1", "content://photo/1")
        assertEquals(ShotStatus.CAPTURED, after.shots[0].status)
        assertTrue(after.shots[0].isCompleted)
        assertEquals("s2", after.currentShot?.id)
    }

    @Test
    fun completedShotCanBeSelectedAgainWithoutLosingCompletion() {
        val captured = mission().markShotCaptured("s1", "content://photo/1")
        val selectedAgain = captured.promoteShot("s1")
        assertEquals("s1", selectedAgain.currentShot?.id)
        assertTrue(selectedAgain.shots[0].isCompleted)
        assertEquals("content://photo/1", selectedAgain.shots[0].capturedMediaUri)
    }

    @Test
    fun allShotsCompleteLeavesNoCurrentShot() {
        val complete = mission()
            .markShotCaptured("s1", "content://photo/1")
            .markShotCaptured("s2", "content://video/2")
        assertNull(complete.currentShot)
        assertEquals(100, complete.progressPercent)
    }
}
