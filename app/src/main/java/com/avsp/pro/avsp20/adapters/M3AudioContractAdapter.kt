package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.*
import com.avsp.pro.audio.contract.AudioPackage

object M3AudioContractAdapter {

    fun toAvsp20(audio: AudioPackage): AudioContract {
        val language = when (audio.language.lowercase()) {
            "bn", "ben", "bengali" -> ScriptLanguage.BN
            "hi", "hin", "hindi" -> ScriptLanguage.HI
            "en", "eng", "english" -> ScriptLanguage.EN
            else -> ScriptLanguage.OTHER
        }

        return AudioContract(
            header = ContractHeader(
                contractType = ContractType.AUDIO,
                id = AvspId(audio.audioPackageId),
                projectId = AvspId(audio.projectId),
                createdAtEpochMs = audio.metadata.createdAt,
                updatedAtEpochMs = audio.metadata.updatedAt
            ),
            sourceScriptId = AvspId(audio.scriptId),
            uri = audio.segments.firstOrNull()?.relativeAudioPath.orEmpty(),
            language = language,
            durationMs = audio.totalDurationMs,
            sampleRateHz = audio.metadata.format.sampleRateHz,
            channels = audio.metadata.format.channels,
            voiceId = audio.voice.voiceId
        )
    }
}
