package com.avsp.pro.avsp20.capability

import com.avsp.pro.avsp20.contracts.*

data class ContractCapability(
    val type: ContractType,
    val supported: Boolean,
    val contractVersion: String,
    val schemaVersion: Int
)

object Avsp20CapabilityResolver {

    fun all(): List<ContractCapability> =
        ContractType.entries.map { type ->
            ContractCapability(
                type = type,
                supported = type in Avsp20ContractRegistry.supportedTypes,
                contractVersion = Avsp20ContractRegistry.VERSION,
                schemaVersion = Avsp20ContractRegistry.SCHEMA
            )
        }

    fun resolve(type: ContractType): ContractCapability =
        ContractCapability(
            type = type,
            supported = type in Avsp20ContractRegistry.supportedTypes,
            contractVersion = Avsp20ContractRegistry.VERSION,
            schemaVersion = Avsp20ContractRegistry.SCHEMA
        )
}
