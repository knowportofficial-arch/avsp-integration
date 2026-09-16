package com.avsp.pro.avsp20.serialization

import com.avsp.pro.avsp20.contracts.*

/**
 * Deterministic, dependency-free text serialization boundary for AVSP 2.0 contracts.
 *
 * Format:
 * key=value lines, sorted by key, with escaping for \, = and line breaks.
 *
 * This session intentionally does not add persistence, Room, JSON libraries,
 * network APIs, or changes to M1-M10.
 */
object Avsp20TextCodec {

    private const val FORMAT_VERSION = "1"

    fun encodeHeader(header: ContractHeader): String {
        require(Avsp20ContractRegistry.isSupported(header)) { "Unsupported contract header" }
        return encode(
            mapOf(
                "contractType" to header.contractType.name,
                "contractVersion" to header.contractVersion,
                "createdAtEpochMs" to header.createdAtEpochMs.toString(),
                "id" to header.id.value,
                "projectId" to header.projectId.value,
                "schemaVersion" to header.schemaVersion.toString(),
                "updatedAtEpochMs" to header.updatedAtEpochMs.toString(),
                "formatVersion" to FORMAT_VERSION
            )
        )
    }

    fun decodeHeader(text: String): ContractHeader {
        val values = decode(text)
        require(values["formatVersion"] == FORMAT_VERSION) { "Unsupported serialization format" }

        val header = ContractHeader(
            contractType = ContractType.valueOf(requireValue(values, "contractType")),
            contractVersion = requireValue(values, "contractVersion"),
            schemaVersion = requireValue(values, "schemaVersion").toInt(),
            id = AvspId(requireValue(values, "id")),
            projectId = AvspId(requireValue(values, "projectId")),
            createdAtEpochMs = requireValue(values, "createdAtEpochMs").toLong(),
            updatedAtEpochMs = requireValue(values, "updatedAtEpochMs").toLong()
        )

        require(Avsp20ContractRegistry.isSupported(header)) { "Unsupported contract header" }
        return header
    }

    fun encode(values: Map<String, String>): String =
        values.toSortedMap().entries.joinToString("\n") { (k, v) ->
            "${escape(k)}=${escape(v)}"
        }

    fun decode(text: String): Map<String, String> {
        if (text.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        text.split('\n').forEach { line ->
            if (line.isEmpty()) return@forEach
            val separator = findSeparator(line)
            require(separator > 0) { "Invalid serialized line" }

            val key = unescape(line.substring(0, separator))
            val value = unescape(line.substring(separator + 1))
            require(key.isNotEmpty()) { "Empty serialization key" }
            result[key] = value
        }
        return result
    }

    private fun findSeparator(line: String): Int {
        var escaped = false
        line.forEachIndexed { index, ch ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            if (ch == '\\') {
                escaped = true
                return@forEachIndexed
            }
            if (ch == '=') return index
        }
        return -1
    }

    private fun escape(value: String): String =
        buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '=' -> append("\\=")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(ch)
                }
            }
        }

    private fun unescape(value: String): String =
        buildString {
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch != '\\') {
                    append(ch)
                    i++
                    continue
                }

                require(i + 1 < value.length) { "Invalid escape sequence" }
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    '=' -> append('=')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> throw IllegalArgumentException("Invalid escape sequence")
                }
                i += 2
            }
        }

    private fun requireValue(values: Map<String, String>, key: String): String =
        requireNotNull(values[key]) { "Missing serialized field: $key" }
}
