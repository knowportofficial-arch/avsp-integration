package com.avsp.pro.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class M3NoSecretsScanTest {
    @Test
    fun audioSourcesHaveNoHardCodedSecrets() {
        val root = File("src/main/java/com/avsp/pro/audio")
        assertThat(root.exists()).isTrue()
        val forbidden = listOf(
            Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
            Regex("""sk-[A-Za-z0-9]{20,}"""),
            Regex("""(?i)api[_-]?key\s*=\s*["'][^"']+["']""")
        )
        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            forbidden.forEach { regex ->
                if (regex.containsMatchIn(text)) offenders += "${file.path} :: ${regex.pattern}"
            }
        }
        assertThat(offenders).isEmpty()
    }
}
