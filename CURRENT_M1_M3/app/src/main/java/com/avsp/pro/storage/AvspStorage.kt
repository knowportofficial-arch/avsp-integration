package com.avsp.pro.storage

import java.io.InputStream
import java.io.OutputStream

/**
 * Platform-safe storage abstraction. Never hard-code absolute OS paths.
 */
enum class StorageArea {
    APP_DATA,
    PROJECT_DATA,
    GENERATED_MEDIA,
    TEMP,
    LOGS
}

interface AvspStorage {
    fun resolve(area: StorageArea, relativePath: String, projectId: String? = null): String
    fun save(area: StorageArea, relativePath: String, bytes: ByteArray, projectId: String? = null)
    fun save(area: StorageArea, relativePath: String, content: String, projectId: String? = null)
    fun readBytes(area: StorageArea, relativePath: String, projectId: String? = null): ByteArray
    fun readText(area: StorageArea, relativePath: String, projectId: String? = null): String
    fun delete(area: StorageArea, relativePath: String, projectId: String? = null): Boolean
    fun exists(area: StorageArea, relativePath: String, projectId: String? = null): Boolean
    fun list(area: StorageArea, relativeDir: String = "", projectId: String? = null): List<String>
    fun copy(
        fromArea: StorageArea,
        fromRelative: String,
        toArea: StorageArea,
        toRelative: String,
        fromProjectId: String? = null,
        toProjectId: String? = null
    )
    fun move(
        fromArea: StorageArea,
        fromRelative: String,
        toArea: StorageArea,
        toRelative: String,
        fromProjectId: String? = null,
        toProjectId: String? = null
    )
    fun ensureProjectLayout(projectId: String)
    fun openInput(area: StorageArea, relativePath: String, projectId: String? = null): InputStream
    fun openOutput(area: StorageArea, relativePath: String, projectId: String? = null): OutputStream
}
