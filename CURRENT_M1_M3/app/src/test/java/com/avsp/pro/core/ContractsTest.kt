package com.avsp.pro.core

import com.avsp.pro.core.contracts.AudioAsset
import com.avsp.pro.core.contracts.ArtifactStatus
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.contracts.PublishingReference
import com.avsp.pro.core.contracts.ScriptReference
import com.avsp.pro.core.contracts.VideoAsset
import com.avsp.pro.core.integration.ArtifactNames
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.avsp.pro.core.module.AvspModules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure JVM contract tests for M1 data/integration boundaries.
 */
class ContractsTest {

    @Test
    fun projectContractFields() {
        val now = 1_700_000_000_000L
        val project = Project(
            projectId = "prj_1",
            name = "Demo",
            description = "desc",
            createdAt = now,
            updatedAt = now,
            status = ProjectStatus.DRAFT,
            duration = null,
            aspectRatio = AspectRatio.RATIO_9_16,
            language = ProjectLanguage.ENGLISH,
            outputPath = null,
            metadata = mapOf("workflow" to "knowport")
        )
        assertThat(project.metadata["workflow"]).isEqualTo("knowport")
    }

    @Test
    fun futureArtifactContractsRemainPending() {
        val now = System.currentTimeMillis()
        val script = ScriptReference("s1", "prj_1", "generated/script/script.json", ProjectLanguage.BENGALI, createdAt = now)
        val audio = AudioAsset("a1", "prj_1", "generated/audio/voice.mp3", ProjectLanguage.BENGALI, createdAt = now)
        val video = VideoAsset("v1", "prj_1", "generated/video/final.mp4", createdAt = now)
        val pub = PublishingReference(
            publishId = "p1",
            projectId = "prj_1",
            platform = "youtube",
            videoRelativePath = ArtifactNames.FINAL_MP4,
            title = "Update",
            language = ProjectLanguage.BENGALI
        )
        val media = MediaAsset(
            assetId = "m1",
            projectId = "prj_1",
            fileName = ArtifactNames.CLIP_MP4,
            relativePath = "${ProjectPaths.CAMERA}/clip.mp4",
            mimeType = "video/mp4",
            createdAt = now
        )
        assertThat(script.status).isEqualTo(ArtifactStatus.PENDING)
        assertThat(audio.status).isEqualTo(ArtifactStatus.PENDING)
        assertThat(video.status).isEqualTo(ArtifactStatus.PENDING)
        assertThat(pub.status).isEqualTo(ArtifactStatus.PENDING)
        assertThat(media.fileName).isEqualTo("clip.mp4")
    }

    @Test
    fun moduleCatalogIncludesM1toM9() {
        assertThat(AvspModules.ALL.map { it.moduleId })
            .containsExactly("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9")
            .inOrder()
        assertThat(AvspModules.ALL.first { it.moduleId == "M2" }.defaultStatus.name)
            .isEqualTo("FROZEN")
        assertThat(AvspModules.ALL.first { it.moduleId == "M3" }.defaultStatus.name)
            .isEqualTo("READY")
        listOf("M1", "M4", "M5", "M6", "M7", "M8", "M9").forEach { id ->
            assertThat(AvspModules.ALL.first { it.moduleId == id }.defaultStatus.name)
                .isEqualTo("FROZEN")
        }
    }

    @Test
    fun artifactNamesMatchIntegrationContract() {
        assertThat(ArtifactNames.SCRIPT_JSON).isEqualTo("script.json")
        assertThat(ArtifactNames.VOICE_MP3).isEqualTo("voice.mp3")
        assertThat(ArtifactNames.FINAL_MP4).isEqualTo("final.mp4")
        assertThat(ProjectPaths.SCRIPT).isEqualTo("generated/script")
    }
}
