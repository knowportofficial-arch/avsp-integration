package com.avsp.pro.avsp20.dispatch

import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20ContractDispatcherTest {

    private fun header(type: ContractType, id: String = "id-1") =
        ContractHeader(
            contractType = type,
            id = AvspId(id),
            projectId = AvspId("project-1"),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

    @Test
    fun exposesAllCanonicalContractTypes() {
        assertEquals(10, Avsp20ContractDispatcher.supportedTypes().size)
        assertTrue(
            Avsp20ContractDispatcher.supportedTypes()
                .containsAll(ContractType.entries)
        )
    }

    @Test
    fun acceptsValidHeader() {
        val result = Avsp20ContractDispatcher.inspect(
            header(ContractType.RENDER, "render-1")
        )
        assertTrue(result.accepted)
        assertEquals(ContractType.RENDER, result.contractType)
        assertEquals("render-1", result.contractId.value)
    }

    @Test
    fun routesValidHeaderToCanonicalType() {
        assertEquals(
            ContractType.PUBLISH,
            Avsp20ContractDispatcher.route(
                header(ContractType.PUBLISH, "publish-1")
            )
        )
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val result = Avsp20ContractDispatcher.inspect(
            header(ContractType.PROJECT).copy(schemaVersion = 99)
        )
        assertTrue(!result.accepted)
        assertTrue(result.errors.contains("HEADER_VERSION_UNSUPPORTED"))
    }
}
