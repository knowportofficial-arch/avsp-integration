package com.avsp.pro

import org.junit.Test
import java.io.File
import com.google.common.truth.Truth.assertThat

/**
 * Scans source for obvious hard-coded secret patterns (W) and plaintext secure-prefs misuse.
 */
class NoSecretsSourceScanTest {

    @Test
    fun sourceTreeHasNoEmbeddedApiKeys() {
        val roots = listOf(
            File("src/main/java"),
            File("src/main/res")
        )
        val forbidden = listOf(
            Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
            Regex("""sk-[A-Za-z0-9]{20,}"""),
            Regex("""(?i)api_key\s*=\s*["'][^"']+["']"""),
            Regex("""(?i)password\s*=\s*["'][^"']+["']"""),
            Regex("""(?i)bot[_-]?token\s*=\s*["'][^"']+["']""")
        )
        val offenders = mutableListOf<String>()
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.forEach { file ->
                val text = file.readText()
                forbidden.forEach { regex ->
                    if (regex.containsMatchIn(text)) {
                        offenders += "${file.path} matches ${regex.pattern}"
                    }
                }
            }
        }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun secureConfigDoesNotUsePlainSharedPreferences() {
        val secureFile = File("src/main/java/com/avsp/pro/settings/PreferenceSecureConfigStore.kt")
        assertThat(secureFile.exists()).isTrue()
        val text = secureFile.readText()
        assertThat(text).contains("EncryptedSharedPreferences")
        assertThat(text).contains("MasterKey")
        assertThat(text).doesNotContain("getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)")
        assertThat(text).doesNotContain("getSharedPreferences(\"avsp_secure_config\", Context.MODE_PRIVATE)")
    }
}
