package com.avsp.pro.avsp20.compatibility

import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class Avsp20CompatibilityTest {

    private fun header(
        type: ContractType = ContractType.PROJECT,
        project: String = "project-1"
    ) = ContractHeader(
        contractType = type,
        id = AvspId(type.name.lowercase()),
        projectId = AvspId(project),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 200L
    )

    @Test
    fun matchingSupportedHeadersAreCompatible() {
        val result = Avsp20Compatibility.check(
            header(ContractType.SCRIPT),
            header(ContractType.AUDIO)
        )

        assertTrue(result.compatible)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun differentProjectsAreRejected() {
        val result = Avsp20Compatibility.check(
            header(project = "project-1"),
            header(project = "project-2")
        )

        assertFalse(result.compatible)
        assertEquals(listOf("PROJECT_MISMATCH"), result.errors)
    }
}
