package com.avsp.pro.video

import com.avsp.pro.core.model.AspectRatio
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutputFormatTest {
    @Test
    fun everyProjectAspectRatioMapsToItsOwnCanvas() {
        assertThat(AspectRatio.RATIO_9_16.width to AspectRatio.RATIO_9_16.height).isEqualTo(1080 to 1920)
        assertThat(AspectRatio.RATIO_16_9.width to AspectRatio.RATIO_16_9.height).isEqualTo(1920 to 1080)
        assertThat(AspectRatio.RATIO_1_1.width to AspectRatio.RATIO_1_1.height).isEqualTo(1080 to 1080)
        assertThat(AspectRatio.RATIO_4_5.width to AspectRatio.RATIO_4_5.height).isEqualTo(1080 to 1350)
    }

    @Test
    fun sourceAspectDoesNotChangeProjectCanvas() {
        val projectFormat = AspectRatio.RATIO_16_9
        val sourceWidth = 1920
        val sourceHeight = 1080
        assertThat(projectFormat.width).isEqualTo(sourceWidth)
        assertThat(projectFormat.height).isEqualTo(sourceHeight)

        val portraitProject = AspectRatio.RATIO_9_16
        assertThat(portraitProject.width).isEqualTo(1080)
        assertThat(portraitProject.height).isEqualTo(1920)
    }
}
