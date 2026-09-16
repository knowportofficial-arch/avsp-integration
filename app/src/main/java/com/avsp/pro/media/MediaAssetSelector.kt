package com.avsp.pro.media

import com.avsp.pro.core.contracts.MediaAsset

/** Selects real project media when present; otherwise exposes deterministic M4 QA clips. */
object MediaAssetSelector {
    fun videoAssetsForRender(assets: List<MediaAsset>): List<MediaAsset> {
        val video = assets.filter { it.mimeType.startsWith("video/") || it.mimeType.startsWith("image/") }
        val real = video.filterNot { it.tags.contains("m4-test") }
        return (if (real.isNotEmpty()) real else video).sortedBy { it.createdAt }
    }
}
