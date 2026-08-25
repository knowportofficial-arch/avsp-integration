package com.avsp.pro.storage

import android.content.Context
import com.avsp.pro.core.error.StorageException
import com.avsp.pro.core.integration.ProjectPaths
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * File-based storage rooted under the app's sandbox directories (platform-safe).
 */
class FileAvspStorage(
    private val context: Context
) : AvspStorage {

    private fun rootFor(area: StorageArea, projectId: String?): File {
        val base = when (area) {
            StorageArea.APP_DATA -> File(context.filesDir, "app_data")
            StorageArea.PROJECT_DATA -> File(context.filesDir, "projects")
            StorageArea.GENERATED_MEDIA -> File(context.filesDir, "generated")
            StorageArea.TEMP -> File(context.cacheDir, "temp")
            StorageArea.LOGS -> File(context.filesDir, "logs")
        }
        return if (projectId != null && area != StorageArea.APP_DATA) {
            File(base, sanitize(projectId))
        } else {
            base
        }
    }

    private fun sanitize(segment: String): String {
        require(segment.isNotBlank()) { "Path segment must not be blank" }
        require(!segment.contains("..")) { "Path traversal is not allowed" }
        return segment.replace(Regex("""[^A-Za-z0-9._\-/=]"""), "_")
    }

    private fun fileFor(area: StorageArea, relativePath: String, projectId: String?): File {
        val safe = sanitize(relativePath.trimStart('/'))
        val root = rootFor(area, projectId)
        val target = File(root, safe)
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalRoot.path)) {
            throw StorageException("Resolved path escapes storage root", details = relativePath)
        }
        return target
    }

    override fun resolve(area: StorageArea, relativePath: String, projectId: String?): String =
        fileFor(area, relativePath, projectId).absolutePath

    override fun save(area: StorageArea, relativePath: String, bytes: ByteArray, projectId: String?) {
        try {
            val file = fileFor(area, relativePath, projectId)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException("Failed to save file", details = relativePath, cause = e)
        }
    }

    override fun save(area: StorageArea, relativePath: String, content: String, projectId: String?) {
        save(area, relativePath, content.toByteArray(Charsets.UTF_8), projectId)
    }

    override fun readBytes(area: StorageArea, relativePath: String, projectId: String?): ByteArray {
        val file = fileFor(area, relativePath, projectId)
        if (!file.exists()) {
            throw StorageException("File not found", details = relativePath)
        }
        return try {
            file.readBytes()
        } catch (e: Exception) {
            throw StorageException("Failed to read file", details = relativePath, cause = e)
        }
    }

    override fun readText(area: StorageArea, relativePath: String, projectId: String?): String =
        readBytes(area, relativePath, projectId).toString(Charsets.UTF_8)

    override fun delete(area: StorageArea, relativePath: String, projectId: String?): Boolean {
        val file = fileFor(area, relativePath, projectId)
        return if (!file.exists()) false else file.delete()
    }

    override fun exists(area: StorageArea, relativePath: String, projectId: String?): Boolean =
        fileFor(area, relativePath, projectId).exists()

    override fun list(area: StorageArea, relativeDir: String, projectId: String?): List<String> {
        val dir = if (relativeDir.isBlank()) {
            rootFor(area, projectId)
        } else {
            fileFor(area, relativeDir, projectId)
        }
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    }

    override fun copy(
        fromArea: StorageArea,
        fromRelative: String,
        toArea: StorageArea,
        toRelative: String,
        fromProjectId: String?,
        toProjectId: String?
    ) {
        val bytes = readBytes(fromArea, fromRelative, fromProjectId)
        save(toArea, toRelative, bytes, toProjectId)
    }

    override fun move(
        fromArea: StorageArea,
        fromRelative: String,
        toArea: StorageArea,
        toRelative: String,
        fromProjectId: String?,
        toProjectId: String?
    ) {
        copy(fromArea, fromRelative, toArea, toRelative, fromProjectId, toProjectId)
        delete(fromArea, fromRelative, fromProjectId)
    }

    override fun ensureProjectLayout(projectId: String) {
        val dirs = listOf(
            ProjectPaths.ORIGINALS,
            ProjectPaths.WORKING,
            ProjectPaths.GENERATED,
            ProjectPaths.PUBLISHED,
            ProjectPaths.LOGS,
            ProjectPaths.SCRIPT,
            ProjectPaths.AUDIO,
            ProjectPaths.VIDEO,
            ProjectPaths.CAMERA,
            ProjectPaths.MEDIA
        )
        dirs.forEach { relative ->
            val dir = fileFor(StorageArea.PROJECT_DATA, relative, projectId)
            if (!dir.exists()) dir.mkdirs()
        }
    }

    override fun openInput(area: StorageArea, relativePath: String, projectId: String?): InputStream {
        val file = fileFor(area, relativePath, projectId)
        if (!file.exists()) throw StorageException("File not found", details = relativePath)
        return file.inputStream()
    }

    override fun openOutput(area: StorageArea, relativePath: String, projectId: String?): OutputStream {
        val file = fileFor(area, relativePath, projectId)
        file.parentFile?.mkdirs()
        return file.outputStream()
    }
}
