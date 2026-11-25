package `in`.shatranj.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.shatranj.app.data.PlayerProfile
import `in`.shatranj.app.domain.EloCalculator
import `in`.shatranj.app.ui.theme.*

@Composable
fun LeaderboardScreen(profile: PlayerProfile) {
    val rankInfo = EloCalculator.getRankInfo(profile.elo)
    val kFactor = EloCalculator.getFideKFactor(profile.elo, profile.peakElo, profile.aiStats.total)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Rank Tiers & Elo", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Slate900), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${rankInfo.icon} ${rankInfo.title}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Amber400)
                        Text("K-Factor: $kFactor", fontSize = 12.sp, color = Slate400)
                    }
                    LinearProgressIndicator(
                        progress = { rankInfo.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Amber400,
                        trackColor = Slate800
                    )
                    Text("Next: ${rankInfo.nextTitle} (${rankInfo.divMax} Elo)", fontSize = 12.sp, color = Slate400)
                }
            }
        }
                item {
            var simOppElo by remember { mutableStateOf(1200) }
            val wDelta = EloCalculator.calculateEloDelta(profile.elo, simOppElo, 1.0, profile.peakElo, profile.aiStats.total)
            val dDelta = EloCalculator.calculateEloDelta(profile.elo, simOppElo, 0.5, profile.peakElo, profile.aiStats.total)
            val lDelta = EloCalculator.calculateEloDelta(profile.elo, simOppElo, 0.0, profile.peakElo, profile.aiStats.total)
            Card(colors = CardDefaults.cardColors(containerColor = Slate900), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Simulate Opponent Elo", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate400)
                        Text("$simOppElo Elo", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Amber400)
                    }
                    Slider(
                        value = simOppElo.toFloat(),
                        onValueChange = { simOppElo = ((it / 25f).roundToInt() * 25).coerceIn(800, 2600) },
                        valueRange = 800f..2600f,
                        steps = 71,
                        colors = SliderDefaults.colors(
                            thumbColor = ArenaColors.ChampagneGold,
                            activeTrackColor = ArenaColors.ChampagneGold,
                            inactiveTrackColor = ArenaColors.ObsidianInteractive
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Win", "${if (wDelta >= 0) "+" else ""}$wDelta", Emerald400),
                            Triple("Draw", "${if (dDelta >= 0) "+" else ""}$dDelta", Slate400),
                            Triple("Loss", "${if (lDelta >= 0) "+" else ""}$lDelta", Rose400)
                        ).forEach { (lbl, valTxt, col) ->
                            Surface(color = Slate800, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(lbl, fontSize = 11.sp, color = Slate400)
                                    Text(valTxt, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = col)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("Bot Gauntlet Milestones", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        items(EloCalculator.RANK_TIERS) { tier ->
            val unlocked = profile.peakElo >= tier.minElo
            Card(
                colors = CardDefaults.cardColors(containerColor = if (unlocked) Slate800 else Slate900),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (unlocked) Modifier.border(1.dp, Emerald500.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else Modifier)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${tier.icon} ${tier.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${tier.minElo} – ${if (tier.maxElo >= 9999) "2400+" else tier.maxElo}",
                        fontSize = 13.sp,
                        color = if (unlocked) Emerald400 else Slate400,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
