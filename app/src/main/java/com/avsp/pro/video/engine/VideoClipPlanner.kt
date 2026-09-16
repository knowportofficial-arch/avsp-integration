package com.avsp.pro.video.engine

/**
 * Pure timeline math for fitting a source video clip to an exact scene duration.
 * A short source is repeated; a long source is trimmed; the sum is exact.
 */
object VideoClipPlanner {
    data class Piece(val startMs: Long, val endMs: Long, val durationMs: Long)

    fun pieces(sourceDurationMs: Long, targetDurationMs: Long): List<Piece> {
        require(sourceDurationMs > 0) { "Source duration must be > 0" }
        require(targetDurationMs > 0) { "Target duration must be > 0" }
        val result = mutableListOf<Piece>()
        var remaining = targetDurationMs
        while (remaining > 0) {
            val piece = minOf(sourceDurationMs, remaining)
            result += Piece(0L, piece, piece)
            remaining -= piece
        }
        check(result.sumOf { it.durationMs } == targetDurationMs)
        return result
    }
}
