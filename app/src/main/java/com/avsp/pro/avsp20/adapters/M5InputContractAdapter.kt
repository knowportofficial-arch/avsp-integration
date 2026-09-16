package com.avsp.pro.avsp20.adapters

import com.avsp.pro.m5.M5InputResult

data class M5InputContract(
    val source: String,
    val reference: String,
    val title: String,
    val durationMs: Long,
    val text: String,
    val numbers: List<Double>,
    val confidence: Double,
    val processedAt: Long
)

object M5InputContractAdapter {

    fun toAvsp20(result: M5InputResult): M5InputContract =
        M5InputContract(
            source = result.source,
            reference = result.reference,
            title = result.title,
            durationMs = result.durationSeconds * 1000L,
            text = result.text,
            numbers = result.numbers,
            confidence = result.confidence,
            processedAt = result.processedAt
        )
}
