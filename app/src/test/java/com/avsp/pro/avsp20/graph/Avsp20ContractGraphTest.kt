package com.avsp.pro.avsp20.graph

import com.avsp.pro.avsp20.contracts.ContractType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20ContractGraphTest {

    @Test
    fun canonicalPipelineEdgesArePresent() {
        assertTrue(
            Avsp20ContractGraph.dependsOn(
                ContractType.SCRIPT,
                ContractType.PROJECT
            )
        )
        assertTrue(
            Avsp20ContractGraph.dependsOn(
                ContractType.RENDER,
                ContractType.TIMELINE
            )
        )
        assertTrue(
            Avsp20ContractGraph.dependsOn(
                ContractType.ANALYTICS,
                ContractType.PUBLISH
            )
        )
    }

    @Test
    fun projectHasNoDependenciesAndUnknownEdgeIsFalse() {
        assertEquals(
            emptySet<ContractType>(),
            Avsp20ContractGraph.dependenciesOf(ContractType.PROJECT)
        )
        assertFalse(
            Avsp20ContractGraph.dependsOn(
                ContractType.PROJECT,
                ContractType.SCRIPT
            )
        )
    }
}
