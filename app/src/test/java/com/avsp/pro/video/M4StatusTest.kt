package com.avsp.pro.video

import com.avsp.pro.core.module.AvspModules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M4StatusTest {
    @Test
    fun m4IsRunningDuringAndroidAcceptance() {
        assertThat(AvspModules.ALL.first { it.moduleId == "M4" }.defaultStatus.name)
            .isEqualTo("RUNNING")
    }
}
