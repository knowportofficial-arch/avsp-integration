package com.avsp.pro.avsp20.serialization

import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20TextCodecTest {

    private fun header(
        type: ContractType = ContractType.PROJECT,
        id: String = "project-1"
    ) = ContractHeader(
        contractType = type,
        id = AvspId(id),
        projectId = AvspId("project-1"),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 200L
    )

    @Test
    fun encodeIsDeterministic() {
        val a = Avsp20TextCodec.encode(
            mapOf("z" to "3", "a" to "1", "m" to "2")
        )
        val b = Avsp20TextCodec.encode(
            mapOf("m" to "2", "z" to "3", "a" to "1")
        )
        assertEquals(a, b)
        assertEquals("a=1\nm=2\nz=3", a)
    }

    @Test
    fun roundTripPreservesEscapedValues() {
        val input = mapOf(
            "title" to "A=B\\C",
            "text" to "line1\nline2",
            "plain" to "ok"
        )
        assertEquals(input, Avsp20TextCodec.decode(Avsp20TextCodec.encode(input)))
    }

    @Test
    fun headerRoundTripPreservesCanonicalValues() {
        val original = header(ContractType.TIMELINE, "timeline-1")
        val decoded = Avsp20TextCodec.decodeHeader(
            Avsp20TextCodec.encodeHeader(original)
        )

        assertEquals(original.contractType, decoded.contractType)
        assertEquals(original.contractVersion, decoded.contractVersion)
        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.id, decoded.id)
        assertEquals(original.projectId, decoded.projectId)
        assertEquals(original.createdAtEpochMs, decoded.createdAtEpochMs)
        assertEquals(original.updatedAtEpochMs, decoded.updatedAtEpochMs)
    }

    @Test
    fun unsupportedSchemaIsRejected() {
        val encoded = Avsp20TextCodec.encode(
            mapOf(
                "contractType" to "PROJECT",
                "contractVersion" to "2.0",
                "createdAtEpochMs" to "100",
                "id" to "project-1",
                "projectId" to "project-1",
                "schemaVersion" to "99",
                "updatedAtEpochMs" to "200",
                "formatVersion" to "1"
            )
        )

        try {
            Avsp20TextCodec.decodeHeader(encoded)
            assertTrue("Expected schema rejection", false)
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
