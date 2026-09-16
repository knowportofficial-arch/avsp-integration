package com.avsp.pro.avsp20.dispatch

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.avsp20.integration.Avsp20IntegrationValidator

data class DispatchResult(
    val accepted: Boolean,
    val contractType: ContractType,
    val contractId: AvspId,
    val errors: List<String> = emptyList()
)

object Avsp20ContractDispatcher {

    fun inspect(header: ContractHeader): DispatchResult {
        val result = Avsp20IntegrationValidator.validateHeader(header, header.contractType)
        return DispatchResult(
            accepted = result.valid,
            contractType = header.contractType,
            contractId = header.id,
            errors = result.errors
        )
    }

    fun route(header: ContractHeader): ContractType {
        val result = inspect(header)
        require(result.accepted) { result.errors.joinToString(",") }
        return header.contractType
    }

    fun supportedTypes(): Set<ContractType> =
        Avsp20ContractRegistry.supportedTypes

    fun isSupported(type: ContractType): Boolean =
        type in Avsp20ContractRegistry.supportedTypes
}
