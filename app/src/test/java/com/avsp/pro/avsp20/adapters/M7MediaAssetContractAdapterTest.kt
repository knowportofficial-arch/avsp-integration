package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.m7.dataset.model.DatasetMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class M7MediaAssetContractAdapterTest {

    private fun media() = DatasetMedia(
        clipId = "clip-1",
        fileUri = "content://media/clip-1",
        mediaType = "VIDEO",
        category = "BROLL",
        date = "2026-09-16",
        time = "18:30:00",
        locationLat = 22.33,
        locationLng = 87.31,
        durationMs = 5000L,
        width = 1920,
        height = 1080,
        fps = 30,
        orientation = "LANDSCAPE",
        tags = listOf("station", "broll"),
        thumbnailUri = "content://thumb/clip-1",
        qualityScore = 0.92,
        qualityResult = null,
        isBestShot = true,
        missionId = "mission-1",
        missionShotId = "shot-1",
        projectId = "project-1",
        displayName = "station-broll.mp4",
        createdAt = 1000L,
        fileSizeBytes = 123456L
    )

    @Test
    fun mapsIdentityAndProject() {
        val result = M7MediaAssetContractAdapter.toAvsp20(media())

        assertEquals(ContractType.ASSET, result.header.contractType)
        assertEquals(AvspId("clip-1"), result.header.id)
        assertEquals(AvspId("project-1"), result.header.projectId)
    }

    @Test
    fun mapsVideoMetadataAndTags() {
        val result = M7MediaAssetContractAdapter.toAvsp20(media())

        assertEquals(MediaKind.VIDEO, result.mediaKind)
        assertEquals("content://media/clip-1", result.uri)
        assertEquals("station-broll.mp4", result.fileName)
        assertEquals(5000L, result.durationMs)
        assertEquals(1920, result.widthPx)
        assertEquals(1080, result.heightPx)
        assertEquals("BROLL", result.source)
        assertEquals(listOf("station", "broll"), result.tags)
    }
}
