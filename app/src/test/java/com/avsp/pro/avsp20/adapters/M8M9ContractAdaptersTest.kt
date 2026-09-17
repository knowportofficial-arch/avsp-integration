package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.m8.M8CaptionEvent
import com.avsp.pro.m8.M8Timeline
import com.avsp.pro.m8.M8VisualEvent
import com.avsp.pro.m9.M9Job
import com.avsp.pro.m9.M9Platform
import com.avsp.pro.m9.M9Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M8M9ContractAdaptersTest {

    @Test
    fun mapsM8Timeline() {
        val timeline = M8Timeline(
            projectId = "project-1",
            format = "9:16",
            width = 1080,
            height = 1920,
            fps = 30.0,
            duration = 10.0,
            visuals = listOf(
                M8VisualEvent(0.0, 5.0, "video-a.mp4", "video")
            ),
            captions = listOf(
                M8CaptionEvent(1.0, 3.0, "Hello", true)
            ),
            musicPath = "music.mp3"
        )

        val result = M8TimelineContractAdapter.toAvsp20(timeline)

        assertEquals(ContractType.TIMELINE, result.header.contractType)
        assertEquals(AvspId("project-1"), result.header.projectId)
        assertEquals(1080, result.widthPx)
        assertEquals(1920, result.heightPx)
        assertEquals(30.0, result.frameRate, 0.0)
        assertEquals(10000L, result.durationMs)
        assertEquals(3, result.items.size)
        assertTrue(result.items.any { it.trackType == TimelineTrackType.VIDEO })
        assertTrue(result.items.any { it.trackType == TimelineTrackType.CAPTION })
        assertTrue(result.items.any { it.trackType == TimelineTrackType.MUSIC })
    }

    @Test
    fun mapsM9YoutubePublishJob() {
        val job = M9Job(
            jobId = "job-1",
            projectId = "project-1",
            videoPath = "final.mp4",
            title = "Test Video",
            platforms = setOf(M9Platform.YOUTUBE),
            status = M9Status.READY
        )

        val result = M9PublishContractAdapter.toAvsp20(job, "render-1")

        assertEquals(ContractType.PUBLISH, result.header.contractType)
        assertEquals(AvspId("job-1"), result.header.id)
        assertEquals(AvspId("project-1"), result.header.projectId)
        assertEquals(AvspId("render-1"), result.renderId)
        assertEquals(PublishPlatform.YOUTUBE, result.target.platform)
        assertEquals("PRIVATE", result.target.visibility)
        assertEquals("Test Video", result.title)
        assertEquals(PublishStatus.QUEUED, result.status)
    }
}
