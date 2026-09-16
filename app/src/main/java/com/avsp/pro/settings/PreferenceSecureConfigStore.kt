package com.avsp.pro.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Encrypted credential store.
 *
 * Production path: AndroidX [EncryptedSharedPreferences] + [MasterKey] (Android Keystore).
 * JVM/Robolectric path: AES-GCM value encryption into private prefs (still encrypted at rest).
 *
 * Secrets are NEVER hard-coded, NEVER shown in UI, and MUST NOT be logged.
 * [SecureConfigStore] interface is unchanged for consumers.
 */
class EncryptedSecureConfigStore(
    context: Context
) : SecureConfigStore {

    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        // Remove any legacy plaintext prefs file from earlier M1 builds.
        appContext.deleteSharedPreferences(LEGACY_PLAINTEXT_PREFS_NAME)
        prefs = createSecurePrefs(appContext)
    }

    override fun putSecret(key: String, value: String) {
        require(key.isNotBlank()) { "Secret key must not be blank" }
        require(value.isNotBlank()) { "Secret value must not be blank" }
        prefs.edit().putString(key, value).apply()
    }

    override fun getSecret(key: String): String? = prefs.getString(key, null)

    override fun clearSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun hasSecret(key: String): Boolean = !prefs.getString(key, null).isNullOrBlank()

    companion object {
        /** Legacy plaintext file name â€” deleted on init; never reused. */
        const val LEGACY_PLAINTEXT_PREFS_NAME = "avsp_secure_config"

        /** Encrypted preferences file (EncryptedSharedPreferences). */
        const val PREFS_NAME = "avsp_secure_config_enc"

        /** Fallback AES-GCM prefs file used only when AndroidKeyStore is unavailable. */
        const val FALLBACK_PREFS_NAME = "avsp_secure_config_aes"

        fun isAndroidKeyStoreAvailable(): Boolean {
            return try {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                true
            } catch (_: Exception) {
                false
            } && !isRobolectricRuntime()
        }

        private fun isRobolectricRuntime(): Boolean {
            val fingerprint = Build.FINGERPRINT
            return fingerprint.contains("robolectric", ignoreCase = true) ||
                (System.getProperty("java.class.path") ?: "").contains("robolectric", ignoreCase = true)
        }

        /**
         * Preferred production factory â€” EncryptedSharedPreferences.
         * Callers that require the AndroidX type should use this when Keystore is available.
         */
        fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                createEncryptedPrefsInternal(context)
            } catch (_: Exception) {
                resetEncryptedPrefsState(context)
                createEncryptedPrefsInternal(context)
            }
        }

        private fun createEncryptedPrefsInternal(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun resetEncryptedPrefsState(context: Context) {
            context.deleteSharedPreferences(PREFS_NAME)
            try {
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    if (containsAlias(ANDROIDX_MASTER_KEY_ALIAS)) {
                        deleteEntry(ANDROIDX_MASTER_KEY_ALIAS)
                    }
                }
            } catch (_: Exception) {
                // Continue to the fresh initialization attempt.
            }
        }

        private const val ANDROIDX_MASTER_KEY_ALIAS =
            "android-keystore://_androidx_security_master_key_"

        fun createSecurePrefs(context: Context): SharedPreferences {
            return if (isAndroidKeyStoreAvailable()) {
                createEncryptedPrefs(context)
            } else {
                AesGcmEncryptedPreferences.create(context, FALLBACK_PREFS_NAME)
            }
        }
    }
}

/**
 * AES-GCM SharedPreferences decorator for environments without AndroidKeyStore (unit tests).
 * Values are encrypted at rest; plaintext secrets never appear in the prefs XML.
 */
internal class AesGcmEncryptedPreferences private constructor(
    private val delegate: SharedPreferences,
    private val secretKey: SecretKey
) : SharedPreferences by delegate {

    override fun getString(key: String?, defValue: String?): String? {
        val stored = delegate.getString(key, null) ?: return defValue
        return decrypt(stored) ?: defValue
    }

    override fun edit(): SharedPreferences.Editor {
        val editor = delegate.edit()
        return object : SharedPreferences.Editor by editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key == null) return this
                if (value == null) {
                    editor.remove(key)
                } else {
                    editor.putString(key, encrypt(value))
                }
                return this
            }

            override fun apply() = editor.apply()
            override fun commit(): Boolean = editor.commit()
        }
    }

    private fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String? {
        return try {
            val raw = Base64.decode(payload, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_FILE = "avsp_aes_gcm_key.bin"

        fun create(context: Context, prefsName: String): SharedPreferences {
            val key = loadOrCreateKey(context)
            val delegate = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            return AesGcmEncryptedPreferences(delegate, key)
        }

        private fun loadOrCreateKey(context: Context): SecretKey {
            val dir = File(context.noBackupFilesDir, "secure_keys").apply { mkdirs() }
            val file = File(dir, KEY_FILE)
            if (file.exists()) {
                return SecretKeySpec(file.readBytes(), "AES")
            }
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            file.writeBytes(key.encoded)
            return key
        }
    }
}

