package com.avsp.pro.avsp20.validation

import com.avsp.pro.avsp20.contracts.ContractHeader
import com.avsp.pro.avsp20.dispatch.Avsp20ContractDispatcher
import com.avsp.pro.avsp20.integrity.Avsp20ContractIntegrity

data class ContractValidationResult(
    val valid: Boolean,
    val fingerprint: String? = null,
    val error: String? = null
)

object Avsp20ContractValidation {

    fun validate(
        header: ContractHeader,
        expectedFingerprint: String
    ): ContractValidationResult {
        val dispatch = Avsp20ContractDispatcher.inspect(header)
        if (!dispatch.accepted) {
            return ContractValidationResult(
                valid = false,
                error = "DISPATCH_REJECTED"
            )
        }

        val integrity = Avsp20ContractIntegrity.verify(
            header = header,
            expectedFingerprint = expectedFingerprint
        )

        return ContractValidationResult(
            valid = integrity.valid,
            fingerprint = integrity.fingerprint,
            error = integrity.error
        )
    }
}
