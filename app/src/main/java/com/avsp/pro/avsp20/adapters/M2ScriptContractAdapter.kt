package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.AvspId
import com.avsp.pro.avsp20.contracts.ContractHeader
import com.avsp.pro.avsp20.contracts.ContractType
import com.avsp.pro.avsp20.contracts.ScriptContract
import com.avsp.pro.avsp20.contracts.ScriptLanguage
import com.avsp.pro.avsp20.contracts.ScriptSegment
import com.avsp.pro.script.contract.ScriptPackage

object M2ScriptContractAdapter {

    fun toAvsp20(script: ScriptPackage): ScriptContract {
        val language = when (script.language.lowercase()) {
            "bn", "ben", "bengali" -> ScriptLanguage.BN
            "hi", "hin", "hindi" -> ScriptLanguage.HI
            "en", "eng", "english" -> ScriptLanguage.EN
            else -> ScriptLanguage.OTHER
        }

        val segments = script.scenes
            .sortedBy { it.order }
            .map { scene ->
                ScriptSegment(
                    segmentId = AvspId(scene.sceneId),
                    order = scene.order,
                    text = scene.narration,
                    language = language,
                    durationMs = scene.durationMs,
                    sceneId = AvspId(scene.sceneId)
                )
            }

        return ScriptContract(
            header = ContractHeader(
                contractType = ContractType.SCRIPT,
                id = AvspId(script.scriptId),
                projectId = AvspId(script.projectId),
                createdAtEpochMs = script.metadata.createdAt,
                updatedAtEpochMs = script.metadata.updatedAt
            ),
            title = script.title,
            language = language,
            segments = segments,
            totalDurationMs = script.estimatedDurationMs
        )
    }
}
