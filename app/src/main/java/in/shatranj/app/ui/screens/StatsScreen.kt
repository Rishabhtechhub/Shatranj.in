package `in`.shatranj.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.shatranj.app.data.GameMode
import `in`.shatranj.app.data.PlayerProfile
import `in`.shatranj.app.ui.theme.*

@Composable
fun StatsScreen(profile: PlayerProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Statistics", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "VS AI (Mastery)",
                    subtitle = "Offline Elo",
                    value = "${profile.aiStats.wins}W - ${profile.aiStats.losses}L - ${profile.aiStats.draws}D",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Pass & Play",
                    subtitle = "Same Device",
                    value = "${profile.rankedStats.wins}W - ${profile.rankedStats.losses}L - ${profile.rankedStats.draws}D",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Slate900), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Elo Progression (Peak: ${profile.peakElo})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    EloLineChart(profile.eloHistory, modifier = Modifier.fillMaxWidth().height(110.dp))
                }
            }
        }
        item {
            Text("Recent Match Log", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (profile.matches.isEmpty()) {
            item { Text("No matches recorded yet.", color = Slate400, fontSize = 13.sp) }
        } else {
            items(profile.matches) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = Slate900), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${m.result} vs ${m.opponentName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${m.modeLabel} • ${m.reason} • ${m.matchId}", fontSize = 11.sp, color = Slate400)
                        }
                        val badge = if (m.mode == GameMode.AI) "${if (m.eloDelta >= 0) "+" else ""}${m.eloDelta}" else "Unrated"
                        Text(badge, fontWeight = FontWeight.Bold, color = if (m.mode == GameMode.AI) Amber400 else Slate400)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, subtitle: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Slate900), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = Slate400)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, color = Amber400, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EloLineChart(history: List<Int>, modifier: Modifier = Modifier) {
    val pts = if (history.size < 2) listOf(history.firstOrNull() ?: 1200, history.firstOrNull() ?: 1200) else history
    Canvas(modifier = modifier) {
        val minVal = (pts.minOrNull() ?: 1200) - 20
        val maxVal = (pts.maxOrNull() ?: 1200) + 20
        val range = (maxVal - minVal).coerceAtLeast(1)
        val path = Path()
        pts.forEachIndexed { idx, v ->
            val x = idx.toFloat() / (pts.size - 1) * size.width
            val y = size.height - ((v - minVal).toFloat() / range * size.height)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Amber400, radius = 4.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, color = Amber400, style = Stroke(width = 2.5.dp.toPx()))
    }
}
