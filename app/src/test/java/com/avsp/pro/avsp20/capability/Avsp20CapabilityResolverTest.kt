package com.avsp.pro.avsp20.capability

import com.avsp.pro.avsp20.contracts.ContractType
import com.avsp.pro.avsp20.contracts.Avsp20ContractRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20CapabilityResolverTest {

    @Test
    fun allReturnsEveryCanonicalContractType() {
        val capabilities = Avsp20CapabilityResolver.all()

        assertEquals(ContractType.entries.size, capabilities.size)
        assertEquals(
            ContractType.entries.toSet(),
            capabilities.map { it.type }.toSet()
        )
        assertTrue(capabilities.all { it.supported })
    }

    @Test
    fun resolveUsesCanonicalRegistryVersionAndSchema() {
        val capability = Avsp20CapabilityResolver.resolve(ContractType.TIMELINE)

        assertTrue(capability.supported)
        assertEquals(Avsp20ContractRegistry.VERSION, capability.contractVersion)
        assertEquals(Avsp20ContractRegistry.SCHEMA, capability.schemaVersion)
    }
}
