package com.avsp.pro.script

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class M2NoSecretsScanTest {

    @Test
    fun m2SourcesHaveNoHardCodedSecrets() {
        val root = File("src/main/java/com/avsp/pro/script")
        assertThat(root.exists()).isTrue()
        val forbidden = listOf(
            Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
            Regex("""sk-[A-Za-z0-9]{20,}"""),
            Regex("""(?i)api[_-]?key\s*=\s*["'][^"']+["']"""),
            Regex("""(?i)bearer\s+[a-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE)
        )
        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            forbidden.forEach { regex ->
                if (regex.containsMatchIn(text)) {
                    offenders += "${file.path} :: ${regex.pattern}"
                }
            }
        }
        assertThat(offenders).isEmpty()
    }
}
