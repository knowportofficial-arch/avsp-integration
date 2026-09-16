package com.avsp.pro.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BundledMediaContractTest {
    @Test
    fun bundledClipNamesAndOrderAreStable() {
        assertThat(listOf("01_INTRO.mp4", "02_HOOK.mp4", "03_SCENE.mp4", "04_OUTRO.mp4"))
            .containsExactly("01_INTRO.mp4", "02_HOOK.mp4", "03_SCENE.mp4", "04_OUTRO.mp4")
            .inOrder()
    }
}
