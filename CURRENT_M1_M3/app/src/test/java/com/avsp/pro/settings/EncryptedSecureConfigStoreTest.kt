package com.avsp.pro.settings

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Secure credential storage tests — secrets encrypted at rest; never plaintext in prefs XML.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EncryptedSecureConfigStoreTest {

    @Test
    fun createSecurePrefs_neverStoresPlaintextSecrets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteSharedPreferences(EncryptedSecureConfigStore.PREFS_NAME)
        context.deleteSharedPreferences(EncryptedSecureConfigStore.FALLBACK_PREFS_NAME)
        context.deleteSharedPreferences(EncryptedSecureConfigStore.LEGACY_PLAINTEXT_PREFS_NAME)

        val store = EncryptedSecureConfigStore(context)
        val secretValue = "test-credential-value-xyz"

        assertThat(store.hasSecret(SecureConfigKeys.AI_API)).isFalse()
        assertThat(store.configState(SecureConfigKeys.AI_API).name).isEqualTo("NOT_CONFIGURED")

        store.putSecret(SecureConfigKeys.AI_API, secretValue)
        assertThat(store.hasSecret(SecureConfigKeys.AI_API)).isTrue()
        assertThat(store.getSecret(SecureConfigKeys.AI_API)).isEqualTo(secretValue)
        assertThat(store.configState(SecureConfigKeys.AI_API).name).isEqualTo("CONFIGURED")

        // Legacy plaintext prefs must be gone.
        val legacyPlain = File(
            context.applicationInfo.dataDir,
            "shared_prefs/${EncryptedSecureConfigStore.LEGACY_PLAINTEXT_PREFS_NAME}.xml"
        )
        assertThat(legacyPlain.exists()).isFalse()

        // Neither encrypted nor fallback prefs XML may contain the raw secret.
        listOf(
            EncryptedSecureConfigStore.PREFS_NAME,
            EncryptedSecureConfigStore.FALLBACK_PREFS_NAME
        ).forEach { name ->
            val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
            if (file.exists()) {
                assertThat(file.readText()).doesNotContain(secretValue)
            }
        }

        store.clearSecret(SecureConfigKeys.AI_API)
        assertThat(store.hasSecret(SecureConfigKeys.AI_API)).isFalse()
        assertThat(store.getSecret(SecureConfigKeys.AI_API)).isNull()
    }

    @Test
    fun productionFactory_usesEncryptedSharedPreferencesWhenKeystoreAvailable() {
        if (!EncryptedSecureConfigStore.isAndroidKeyStoreAvailable()) {
            // Robolectric JVM: Keystore unavailable — verify fallback still encrypts.
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val prefs = EncryptedSecureConfigStore.createSecurePrefs(context)
            assertThat(prefs).isNotInstanceOf(EncryptedSharedPreferences::class.java)
            assertThat(prefs.javaClass.simpleName).contains("AesGcm")
            return
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = EncryptedSecureConfigStore.createEncryptedPrefs(context)
        assertThat(prefs).isInstanceOf(EncryptedSharedPreferences::class.java)
    }

    @Test
    fun blankKeyOrValueRejected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = EncryptedSecureConfigStore(context)
        try {
            store.putSecret(" ", "value")
            throw AssertionError("Expected IllegalArgumentException for blank key")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            store.putSecret(SecureConfigKeys.YOUTUBE, "  ")
            throw AssertionError("Expected IllegalArgumentException for blank value")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun sourceUsesEncryptedSharedPreferencesAndMasterKey() {
        val file = java.io.File("src/main/java/com/avsp/pro/settings/PreferenceSecureConfigStore.kt")
        val text = file.readText()
        assertThat(text).contains("EncryptedSharedPreferences")
        assertThat(text).contains("MasterKey")
        assertThat(text).contains("AES256_GCM")
        assertThat(text).doesNotContain(
            "getSharedPreferences(\"avsp_secure_config\", Context.MODE_PRIVATE)"
        )
    }
}
