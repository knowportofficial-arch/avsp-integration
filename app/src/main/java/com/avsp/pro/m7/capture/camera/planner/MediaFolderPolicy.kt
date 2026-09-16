package com.avsp.pro.m7.capture.camera.planner

/**
 * Logical media-library placement. Physical filesystem/MediaStore naming
 * remains under the existing Media Core.
 */
object MediaFolderPolicy {

    fun folderFor(
        projectName: String,
        mediaType: String,
        metadata: CaptureMediaMetadata
    ): String {
        val root = sanitize(projectName).ifBlank { "AVSP" }
        val type = if (mediaType.equals("VIDEO", true)) "Video" else "Photo"

        return when (metadata.classification) {
            MediaClassification.PREDEFINED -> {
                val framing = metadata.framing
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::sanitize)
                    ?: "Unclassified"
                "$root/$type/$framing"
            }

            MediaClassification.MANUAL_EXTRA -> {
                "$root/$type/Manual"
            }
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "AVSP" }
}
