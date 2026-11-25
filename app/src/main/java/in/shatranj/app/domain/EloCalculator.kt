package `in`.shatranj.app.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class RankTier(
    val name: String,
    val minElo: Int,
    val maxElo: Int,
    val icon: String,
    val divisions: Int
)

data class RankInfo(
    val title: String,
    val icon: String,
    val tierName: String,
    val divMin: Int,
    val divMax: Int,
    val nextTitle: String,
    val progress: Float
)

object EloCalculator {
    val RANK_TIERS = listOf(
        RankTier("Iron", 0, 999, "🔩", 3),
        RankTier("Bronze", 1000, 1149, "🥉", 3),
        RankTier("Silver", 1150, 1299, "🥈", 3),
        RankTier("Gold", 1300, 1449, "🥇", 3),
        RankTier("Platinum", 1450, 1599, "💠", 3),
        RankTier("Emerald", 1600, 1799, "💚", 3),
        RankTier("Diamond", 1800, 1999, "💎", 3),
        RankTier("Master", 2000, 2199, "⚔️", 1),
        RankTier("Grandmaster", 2200, 2399, "👑", 1),
        RankTier("Champion", 2400, 9999, "🏆", 1)
    )

    fun getFideKFactor(elo: Int, peakElo: Int, totalRankedGames: Int): Int = when {
        totalRankedGames < 30 -> 40 // Provisional
        peakElo >= 2400 || elo >= 2400 -> 10 // Master/Champion
        else -> 20 // Established
    }

    fun calculateEloDelta(
        playerElo: Int,
        opponentElo: Int,
        score: Double, // 1.0 = Win, 0.5 = Draw, 0.0 = Loss
        peakElo: Int,
        totalRankedGames: Int
    ): Int {
        val k = getFideKFactor(playerElo, peakElo, totalRankedGames)
        val cappedDiff = max(-400, min(400, opponentElo - playerElo))
        val expected = 1.0 / (1.0 + 10.0.pow(cappedDiff / 400.0))
        return (k * (score - expected)).roundToInt()
    }

    fun getRankInfo(elo: Int): RankInfo {
        val idx = RANK_TIERS.indexOfFirst { elo in it.minElo..it.maxElo }.let { if (it >= 0) it else 0 }
        val tier = RANK_TIERS[idx]
        if (tier.divisions == 1) {
            val nextTier = RANK_TIERS.getOrNull(idx + 1)
            val span = if (nextTier != null) (nextTier.minElo - tier.minElo) else 200
            val progress = ((elo - tier.minElo).toFloat() / span).coerceIn(0f, 1f)
            return RankInfo(
                title = tier.name,
                icon = tier.icon,
                tierName = tier.name,
                divMin = tier.minElo,
                divMax = nextTier?.minElo ?: (tier.minElo + 200),
                nextTitle = nextTier?.name ?: "Top Tier",
                progress = progress
            )
        }
        val span = (tier.maxElo - tier.minElo + 1) / 3
        val divIdx = ((elo - tier.minElo) / span).coerceIn(0, 2)
        val numerals = listOf("III", "II", "I")
        val divMin = tier.minElo + divIdx * span
        val divMax = if (divIdx == 2) tier.maxElo + 1 else divMin + span
        val nextTitle = if (divIdx < 2) "${tier.name} ${numerals[divIdx + 1]}"
        else RANK_TIERS.getOrNull(idx + 1)?.let { if (it.divisions > 1) "${it.name} III" else it.name } ?: "Top Tier"
        val progress = ((elo - divMin).toFloat() / max(1, divMax - divMin)).coerceIn(0f, 1f)
        return RankInfo(
            title = "${tier.name} ${numerals[divIdx]}",
            icon = tier.icon,
            tierName = tier.name,
            divMin = divMin,
            divMax = divMax,
            nextTitle = nextTitle,
            progress = progress
        )
    }
}
