package com.avsp.pro.avsp20.graph

import com.avsp.pro.avsp20.contracts.ContractType

object Avsp20ContractGraph {

    private val dependencies: Map<ContractType, Set<ContractType>> = mapOf(
        ContractType.PROJECT to emptySet(),
        ContractType.SCRIPT to setOf(ContractType.PROJECT),
        ContractType.AUDIO to setOf(ContractType.SCRIPT),
        ContractType.SCENE to setOf(ContractType.SCRIPT),
        ContractType.ASSET to setOf(ContractType.SCENE),
        ContractType.TIMELINE to setOf(ContractType.ASSET, ContractType.SCENE),
        ContractType.RENDER to setOf(ContractType.TIMELINE),
        ContractType.QC to setOf(ContractType.RENDER),
        ContractType.PUBLISH to setOf(ContractType.RENDER, ContractType.QC),
        ContractType.ANALYTICS to setOf(ContractType.PUBLISH)
    )

    fun dependenciesOf(type: ContractType): Set<ContractType> =
        dependencies[type].orEmpty()

    fun allEdges(): Map<ContractType, Set<ContractType>> =
        dependencies.toMap()

    fun dependsOn(source: ContractType, dependency: ContractType): Boolean =
        dependency in dependenciesOf(source)
}
