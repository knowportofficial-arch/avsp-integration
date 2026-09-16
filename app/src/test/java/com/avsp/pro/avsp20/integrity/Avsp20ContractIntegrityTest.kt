package com.avsp.pro.avsp20.integrity

import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20ContractIntegrityTest {

    private fun header(
        id: String = "project-1",
        updated: Long = 200L
    ) = ContractHeader(
        contractType = ContractType.PROJECT,
        id = AvspId(id),
        projectId = AvspId("project-1"),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = updated
    )

    @Test
    fun fingerprintIsDeterministic() {
        assertEquals(
            Avsp20ContractIntegrity.fingerprint(header()),
            Avsp20ContractIntegrity.fingerprint(header())
        )
    }

    @Test
    fun fingerprintChangesWhenContractChanges() {
        assertNotEquals(
            Avsp20ContractIntegrity.fingerprint(header()),
            Avsp20ContractIntegrity.fingerprint(header(updated = 201L))
        )
    }

    @Test
    fun validFingerprintPassesVerification() {
        val fingerprint = Avsp20ContractIntegrity.fingerprint(header())
        val result = Avsp20ContractIntegrity.verify(header(), fingerprint)

        assertTrue(result.valid)
        assertEquals(fingerprint, result.fingerprint)
        assertEquals(null, result.error)
    }

    @Test
    fun modifiedContractFailsVerification() {
        val original = header()
        val fingerprint = Avsp20ContractIntegrity.fingerprint(original)
        val modified = header(updated = 999L)

        val result = Avsp20ContractIntegrity.verify(modified, fingerprint)

        assertFalse(result.valid)
        assertEquals("FINGERPRINT_MISMATCH", result.error)
    }
}
