package com.avsp.pro.avsp20.integrity

import com.avsp.pro.avsp20.contracts.ContractHeader
import com.avsp.pro.avsp20.serialization.Avsp20TextCodec
import java.security.MessageDigest

data class IntegrityResult(
    val valid: Boolean,
    val fingerprint: String,
    val error: String? = null
)

object Avsp20ContractIntegrity {

    fun fingerprint(header: ContractHeader): String {
        val canonical = Avsp20TextCodec.encodeHeader(header)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(header: ContractHeader, expectedFingerprint: String): IntegrityResult {
        val actual = fingerprint(header)
        return if (actual == expectedFingerprint) {
            IntegrityResult(true, actual)
        } else {
            IntegrityResult(false, actual, "FINGERPRINT_MISMATCH")
        }
    }
}
