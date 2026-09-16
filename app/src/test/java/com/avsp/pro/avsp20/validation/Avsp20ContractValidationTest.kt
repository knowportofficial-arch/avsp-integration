package com.avsp.pro.avsp20.validation

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.avsp20.integrity.Avsp20ContractIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20ContractValidationTest {

    private fun header() = ContractHeader(
        contractType = ContractType.PROJECT,
        id = AvspId("project-1"),
        projectId = AvspId("project-1"),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 200L
    )

    @Test
    fun validContractPassesDispatchAndIntegrity() {
        val h = header()
        val fingerprint = Avsp20ContractIntegrity.fingerprint(h)

        val result = Avsp20ContractValidation.validate(h, fingerprint)

        assertTrue(result.valid)
        assertEquals(fingerprint, result.fingerprint)
        assertEquals(null, result.error)
    }

    @Test
    fun modifiedContractFailsIntegrity() {
        val original = header()
        val fingerprint = Avsp20ContractIntegrity.fingerprint(original)

        val modified = original.copy(updatedAtEpochMs = 999L)
        val result = Avsp20ContractValidation.validate(modified, fingerprint)

        assertFalse(result.valid)
        assertEquals("FINGERPRINT_MISMATCH", result.error)
    }
}
