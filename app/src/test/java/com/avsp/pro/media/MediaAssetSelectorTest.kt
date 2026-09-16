package com.avsp.pro.media

import com.avsp.pro.core.contracts.MediaAsset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaAssetSelectorTest {
    private fun asset(id: String, created: Long, qa: Boolean = false) = MediaAsset(
        assetId = id,
        projectId = "p",
        fileName = "$id.mp4",
        relativePath = "originals/media/$id.mp4",
        mimeType = "video/mp4",
        createdAt = created,
        tags = if (qa) listOf("m4-test") else emptyList()
    )

    @Test
    fun realAssetsTakePriorityOverBundledQaAssets() {
        val selected = MediaAssetSelector.videoAssetsForRender(
            listOf(asset("qa", 1, true), asset("real", 2))
        )
        assertThat(selected.map { it.assetId }).containsExactly("real").inOrder()
    }

    @Test
    fun bundledAssetsAreUsedWhenNoRealVideoExists() {
        val selected = MediaAssetSelector.videoAssetsForRender(
            listOf(asset("qa2", 2, true), asset("qa1", 1, true))
        )
        assertThat(selected.map { it.assetId }).containsExactly("qa1", "qa2").inOrder()
    }
}
