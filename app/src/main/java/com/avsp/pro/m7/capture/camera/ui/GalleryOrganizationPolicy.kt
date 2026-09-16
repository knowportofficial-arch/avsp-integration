package com.avsp.pro.m7.capture.camera.ui

import com.avsp.pro.m7.capture.camera.planner.CaptureMediaMetadata
import com.avsp.pro.m7.capture.camera.planner.MediaFolderPolicy

/**
 * UI-facing wrapper around the Part 3 logical media organization policy.
 */
object GalleryOrganizationPolicy {

    fun displayPath(
        projectName: String,
        mediaType: String,
        metadata: CaptureMediaMetadata
    ): String = MediaFolderPolicy.folderFor(projectName, mediaType, metadata)
}
