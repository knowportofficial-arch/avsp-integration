package com.avsp.pro.capture.camera.guided

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Content URIs for Guided Capture app-private files so Media Library / players can open
 * recordings without relying on raw file:// URIs (blocked / unplayable on modern Android).
 */
object GuidedCaptureUris {
    const val AUTHORITY_SUFFIX = ".guided.fileprovider"

    fun authority(context: Context): String = "${context.packageName}$AUTHORITY_SUFFIX"

    fun contentUriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)
}
