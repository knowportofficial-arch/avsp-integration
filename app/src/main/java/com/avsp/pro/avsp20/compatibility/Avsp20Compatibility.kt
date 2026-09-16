package com.avsp.pro.avsp20.compatibility

import com.avsp.pro.avsp20.contracts.*

data class CompatibilityResult(
    val compatible: Boolean,
    val errors: List<String> = emptyList()
)

object Avsp20Compatibility {

    fun check(
        source: ContractHeader,
        target: ContractHeader
    ): CompatibilityResult {
        val errors = mutableListOf<String>()

        if (!Avsp20ContractRegistry.isSupported(source)) {
            errors += "SOURCE_UNSUPPORTED"
        }

        if (!Avsp20ContractRegistry.isSupported(target)) {
            errors += "TARGET_UNSUPPORTED"
        }

        if (source.projectId != target.projectId) {
            errors += "PROJECT_MISMATCH"
        }

        if (source.contractVersion != target.contractVersion) {
            errors += "CONTRACT_VERSION_MISMATCH"
        }

        if (source.schemaVersion != target.schemaVersion) {
            errors += "SCHEMA_VERSION_MISMATCH"
        }

        return CompatibilityResult(
            compatible = errors.isEmpty(),
            errors = errors
        )
    }
}
