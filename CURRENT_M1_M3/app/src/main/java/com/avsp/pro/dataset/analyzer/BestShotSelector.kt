package com.avsp.pro.dataset.analyzer

import com.avsp.pro.capture.database.entity.MediaEntity
import com.avsp.pro.dataset.model.Recommendation

/**
 * Ranks candidates that belong to the same shot/mission and marks the best.
 * All originals are retained; only the best is flagged.
 */
class BestShotSelector {

    data class RankedCandidate(
        val media: MediaEntity,
        val rank: Int,
        val qualityScore: Double,
        val isBest: Boolean
    )

    /**
     * @param candidates media items sharing the same missionShotId (or manual grouping)
     * @return ranked list (best first). Exactly one is marked isBest when candidates is non-empty.
     */
    fun rank(candidates: List<MediaEntity>): List<RankedCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val scored = candidates.map { entity ->
            val q = normalizeQuality(entity.qualityScore)
            // Prefer non-duplicates and KEEP recommendations when available
            val bonus = when {
                entity.isDuplicate -> -0.25
                entity.recommendation == Recommendation.KEEP.name -> 0.08
                entity.recommendation == Recommendation.RETAKE.name -> -0.15
                else -> 0.0
            }
            entity to (q + bonus).coerceIn(0.0, 1.0)
        }.sortedByDescending { it.second }

        return scored.mapIndexed { index, (entity, score) ->
            RankedCandidate(
                media = entity,
                rank = index + 1,
                qualityScore = score,
                isBest = index == 0
            )
        }
    }

    /**
     * Select single best entity id (or null).
     */
    fun selectBestId(candidates: List<MediaEntity>): String? {
        return rank(candidates).firstOrNull()?.media?.id
    }

    private fun normalizeQuality(raw: Int): Double {
        // Existing MediaEntity.qualityScore is 0–100 int; also accept already 0–1 stored as small int.
        return when {
            raw <= 1 -> raw.toDouble()
            raw <= 100 -> raw / 100.0
            else -> 1.0
        }
    }
}
