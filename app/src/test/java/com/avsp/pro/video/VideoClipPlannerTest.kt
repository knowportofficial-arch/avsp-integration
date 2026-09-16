package com.avsp.pro.video

import com.avsp.pro.video.engine.VideoClipPlanner
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoClipPlannerTest {
    @Test
    fun shortSourceLoopsToExactSceneDuration() {
        val pieces = VideoClipPlanner.pieces(4_000L, 14_068L)
        assertThat(pieces.sumOf { it.durationMs }).isEqualTo(14_068L)
        assertThat(pieces.size).isEqualTo(4)
        assertThat(pieces.map { it.durationMs }).containsExactly(4_000L, 4_000L, 4_000L, 2_068L).inOrder()
    }

    @Test
    fun longSourceIsTrimmedToExactSceneDuration() {
        val pieces = VideoClipPlanner.pieces(10_000L, 3_728L)
        assertThat(pieces).containsExactly(VideoClipPlanner.Piece(0L, 3_728L, 3_728L))
    }

    @Test
    fun exactSourceNeedsOnePiece() {
        val pieces = VideoClipPlanner.pieces(6_000L, 6_000L)
        assertThat(pieces).containsExactly(VideoClipPlanner.Piece(0L, 6_000L, 6_000L))
    }
}
