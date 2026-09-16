package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.m7.dataset.model.DatasetMedia

object M7MediaAssetContractAdapter {

    fun toAvsp20(media: DatasetMedia): AssetContract {
        val kind = when (media.mediaType.uppercase()) {
            "PHOTO" -> MediaKind.IMAGE
            "VIDEO" -> MediaKind.VIDEO
            else -> MediaKind.UNKNOWN
        }

        return AssetContract(
            header = ContractHeader(
                contractType = ContractType.ASSET,
                id = AvspId(media.clipId),
                projectId = AvspId(media.projectId),
                createdAtEpochMs = media.createdAt,
                updatedAtEpochMs = media.createdAt
            ),
            uri = media.fileUri,
            fileName = media.displayName,
            mediaKind = kind,
            durationMs = media.durationMs,
            widthPx = media.width,
            heightPx = media.height,
            source = media.category,
            tags = media.tags
        )
    }
}
