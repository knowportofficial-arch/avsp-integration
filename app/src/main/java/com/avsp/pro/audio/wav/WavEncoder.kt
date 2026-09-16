package com.avsp.pro.audio.wav

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic PCM WAV helpers for Mock TTS and local testing.
 * Format: WAV / 16-bit PCM / mono / 16 kHz.
 */
object WavEncoder {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    /**
     * Builds a short deterministic tone-burst WAV whose length tracks [durationMs].
     * Not real speech — clearly for MOCK/test playback only.
     */
    fun synthesizeToneWav(
        seedText: String,
        durationMs: Long,
        frequencyHz: Double = frequencyFor(seedText)
    ): ByteArray {
        val clamped = durationMs.coerceIn(250L, 60_000L)
        val sampleCount = ((SAMPLE_RATE * clamped) / 1000L).toInt().coerceAtLeast(1)
        val dataSize = sampleCount * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        writeHeader(out, dataSize)
        var phase = 0.0
        val phaseInc = 2.0 * PI * frequencyHz / SAMPLE_RATE
        // Soft envelope so playback is audible but not a harsh click.
        for (i in 0 until sampleCount) {
            val env = when {
                i < SAMPLE_RATE / 50 -> i.toDouble() / (SAMPLE_RATE / 50.0)
                i > sampleCount - SAMPLE_RATE / 50 -> {
                    (sampleCount - i).toDouble() / (SAMPLE_RATE / 50.0)
                }
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val sample = (sin(phase) * 0.25 * env * Short.MAX_VALUE).toInt().toShort()
            out.write(sample.toInt() and 0xFF)
            out.write((sample.toInt() shr 8) and 0xFF)
            phase += phaseInc
        }
        return out.toByteArray()
    }

    fun durationMsForPcmBytes(pcmDataSize: Int): Long {
        val bytesPerSec = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)
        if (bytesPerSec <= 0) return 0L
        return (pcmDataSize.toLong() * 1000L) / bytesPerSec
    }

    private fun frequencyFor(seedText: String): Double {
        val hash = seedText.hashCode().and(0x7fffffff)
        return 220.0 + (hash % 400)
    }

    private fun writeHeader(out: ByteArrayOutputStream, dataSize: Int) {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        out.write("RIFF".toByteArray())
        out.write(intLE(36 + dataSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intLE(16))
        out.write(shortLE(1)) // PCM
        out.write(shortLE(CHANNELS))
        out.write(intLE(SAMPLE_RATE))
        out.write(intLE(byteRate))
        out.write(shortLE(blockAlign))
        out.write(shortLE(BITS_PER_SAMPLE))
        out.write("data".toByteArray())
        out.write(intLE(dataSize))
    }

    private fun intLE(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun shortLE(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}
