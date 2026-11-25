package `in`.shatranj.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.shatranj.app.data.GAME_MODES
import `in`.shatranj.app.data.GameMode
import `in`.shatranj.app.data.PlayerProfile
import `in`.shatranj.app.domain.EloCalculator
import `in`.shatranj.app.ui.ActiveGameState
import `in`.shatranj.app.ui.theme.*

@Composable
fun HomeScreen(
    profile: PlayerProfile,
    activeGame: ActiveGameState?,
    onQuickPlay: () -> Unit,
    onOpenLobby: () -> Unit,
    onResumeMatch: () -> Unit,
    onImportPgn: (String) -> Unit
) {
    val rank = EloCalculator.getRankInfo(profile.elo)
    val aiWinRate = if (profile.aiStats.total > 0) (profile.aiStats.wins * 100 / profile.aiStats.total) else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Shatranj.in", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Surface(color = Emerald500.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                Text("100% OFFLINE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Emerald400)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LOCAL PLAYER", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.SemiBold)
                        Text("${profile.displayName} (${profile.playerId})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Amber400)
                    }
                    Surface(color = Slate800, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "${rank.icon} ${rank.title}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                HorizontalDivider(color = Slate800)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Offline Mastery Elo", fontSize = 12.sp, color = Slate400)
                        Text("${profile.elo}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("AI Win Rate", fontSize = 12.sp, color = Slate400)
                        Text("$aiWinRate% (${profile.aiStats.total}G)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                }
            }
        }

        if (activeGame != null && !activeGame.over) {
            Button(
                onClick = onResumeMatch,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Resume Current Match (${activeGame.modeLabel})", fontWeight = FontWeight.Bold)
            }
        }

        val recentCfg = GAME_MODES.find { it.id == profile.recentModeId } ?: GAME_MODES[2]
        val oppDesc = if (profile.recentOppMode == GameMode.LOCAL) "Pass & Play" else profile.selectedEngine.label
        Button(
            onClick = onQuickPlay,
            colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Quick Play: ${recentCfg.label} ($oppDesc)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = onOpenLobby,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Choose Mode, Engine & PGN Replay", fontWeight = FontWeight.SemiBold)
        }

        val lastMatch = profile.matches.firstOrNull()
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Latest Match", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate400)
                if (lastMatch == null) {
                    Text("No matches played yet. Tap Quick Play above!", fontSize = 13.sp, color = Slate400)
                } else {
                    val deltaText = if (lastMatch.mode == GameMode.AI)
                        " (${if (lastMatch.eloDelta >= 0) "+" else ""}${lastMatch.eloDelta} Elo)" else " (Pass & Play)"
                    Text("${lastMatch.result} vs ${lastMatch.opponentName}$deltaText", fontWeight = FontWeight.Bold)
                    Text("${lastMatch.modeLabel} • ${lastMatch.reason}", fontSize = 12.sp, color = Slate400)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            var pgnInput by remember { mutableStateOf("") }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Replay from Copied PGN (SAN / Coordinate)", fontSize = 13.sp, color = Amber400, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = pgnInput,
                    onValueChange = { pgnInput = it },
                    placeholder = { Text("Paste PGN (1. e4 e5 or 1. e2-e4 e7-e5...)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onImportPgn(pgnInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load & Replay PGN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
