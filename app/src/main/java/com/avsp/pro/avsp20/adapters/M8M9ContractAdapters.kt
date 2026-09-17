package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.m8.M8Timeline
import com.avsp.pro.m9.M9Job

object M8TimelineContractAdapter {

    fun toAvsp20(timeline: M8Timeline): TimelineContract {
        val items = buildList {
            timeline.visuals.forEachIndexed { index, event ->
                add(
                    TimelineItem(
                        itemId = AvspId("visual-$index-${timeline.projectId}"),
                        trackType = when (event.type.uppercase()) {
                            "IMAGE" -> TimelineTrackType.IMAGE
                            else -> TimelineTrackType.VIDEO
                        },
                        assetId = AvspId(event.path),
                        startMs = (event.start * 1000.0).toLong(),
                        durationMs = ((event.end - event.start) * 1000.0).toLong()
                    )
                )
            }
            timeline.captions.forEachIndexed { index, event ->
                add(
                    TimelineItem(
                        itemId = AvspId("caption-$index-${timeline.projectId}"),
                        trackType = TimelineTrackType.CAPTION,
                        startMs = (event.start * 1000.0).toLong(),
                        durationMs = ((event.end - event.start) * 1000.0).toLong(),
                        zIndex = if (event.emphasis) 1 else 0
                    )
                )
            }
            timeline.musicPath?.let {
                add(
                    TimelineItem(
                        itemId = AvspId("music-${timeline.projectId}"),
                        trackType = TimelineTrackType.MUSIC,
                        assetId = AvspId(it),
                        startMs = 0L,
                        durationMs = (timeline.duration * 1000.0).toLong()
                    )
                )
            }
        }

        return TimelineContract(
            header = ContractHeader(
                contractType = ContractType.TIMELINE,
                id = AvspId("timeline-${timeline.projectId}"),
                projectId = AvspId(timeline.projectId),
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            ),
            items = items,
            durationMs = (timeline.duration * 1000.0).toLong(),
            frameRate = timeline.fps,
            widthPx = timeline.width,
            heightPx = timeline.height
        )
    }
}

object M9PublishContractAdapter {

    fun toAvsp20(job: M9Job, renderId: String): PublishContract {
        val target = when {
            job.platforms.contains(com.avsp.pro.m9.M9Platform.YOUTUBE) -> PublishPlatform.YOUTUBE
            job.platforms.contains(com.avsp.pro.m9.M9Platform.FACEBOOK) -> PublishPlatform.FACEBOOK
            else -> PublishPlatform.OTHER
        }

        val status = when (job.status) {
            com.avsp.pro.m9.M9Status.DRAFT -> PublishStatus.DRAFT
            com.avsp.pro.m9.M9Status.QUEUED,
            com.avsp.pro.m9.M9Status.VALIDATING,
            com.avsp.pro.m9.M9Status.READY -> PublishStatus.QUEUED
            com.avsp.pro.m9.M9Status.UPLOADING,
            com.avsp.pro.m9.M9Status.PROCESSING,
            com.avsp.pro.m9.M9Status.RETRYING -> PublishStatus.UPLOADING
            com.avsp.pro.m9.M9Status.PUBLISHED -> PublishStatus.PUBLISHED
            com.avsp.pro.m9.M9Status.CANCELLED -> PublishStatus.CANCELLED
            else -> PublishStatus.FAILED
        }

        return PublishContract(
            header = ContractHeader(
                contractType = ContractType.PUBLISH,
                id = AvspId(job.jobId),
                projectId = AvspId(job.projectId),
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            ),
            renderId = AvspId(renderId),
            target = PublishTarget(platform = target),
            title = job.title,
            status = status
        )
    }
}


