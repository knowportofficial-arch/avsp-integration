package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraShotType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuidedCaptureShotNamingTest {

    @Test
    fun preservedIntroFormatExact() {
        val formatted = GuidedCaptureShotNaming.format(
            semanticName = "Intro",
            shotCode = "INTRO",
            durationSeconds = 5,
            framing = CameraShotType.WIDE
        )
        assertThat(formatted).isEqualTo(
            "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
        )
    }

    @Test
    fun mediumAndCloseUseExistingDescriptionsOnly() {
        assertThat(
            GuidedCaptureShotNaming.format("Medium", "MEDIUM", 8, CameraShotType.MEDIUM)
        ).isEqualTo(
            "Medium · MEDIUM · 8s · Medium (Main subject with surrounding contextual frame)"
        )
        assertThat(
            GuidedCaptureShotNaming.format("Close", "CLOSE", 5, CameraShotType.CLOSE)
        ).isEqualTo(
            "Close · CLOSE · 5s · Close (Detail, face, product & macro texture shot)"
        )
    }

    @Test
    fun semanticPlannerTitleIsNotReplacedByFraming() {
        val formatted = GuidedCaptureShotNaming.format(
            semanticName = "Cooking setup",
            shotCode = "WIDE",
            durationSeconds = 5,
            framing = CameraShotType.WIDE
        )
        assertThat(formatted).startsWith("Cooking setup · WIDE ·")
        assertThat(formatted).doesNotContain("Wide · WIDE · Wide")
    }
}
