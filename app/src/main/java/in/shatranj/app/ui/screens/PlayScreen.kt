package `in`.shatranj.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.shatranj.app.data.*
import `in`.shatranj.app.domain.ChessEngine
import kotlin.math.roundToInt
import `in`.shatranj.app.ui.ActiveGameState
import `in`.shatranj.app.ui.theme.*

@Composable
fun PlayScreen(
    activeGame: ActiveGameState?,
    selectedModeId: String,
    selectedAiElo: Int,
    selectedEngine: AiEngineType,
    onSelectEngine: (AiEngineType) -> Unit,
    canRejoinLastResigned: Boolean,
    onSelectMode: (String) -> Unit,
    onSelectAiElo: (Int) -> Unit,
    onStartMatch: (GameMode) -> Unit,
    onSquareTap: (Int, Int) -> Unit,
    onFlipBoard: () -> Unit,
    onUndoMove: () -> Unit,
    onRequestResign: () -> Unit,
    onRejoinLast: () -> Unit,
    onStepReview: (Int) -> Unit,
    onExitReview: () -> Unit,
    onCopyPgn: () -> Unit,
    whiteAccuracy: Int,
    blackAccuracy: Int,
    boardTheme: BoardTheme,
    onSelectBoardTheme: (BoardTheme) -> Unit,
onImportPgn: (String) -> Unit
) {
    if (activeGame == null) {
        ModeLobby(
            selectedModeId = selectedModeId,
            selectedAiElo = selectedAiElo,
            selectedEngine = selectedEngine,
            onSelectEngine = onSelectEngine,
            canRejoinLastResigned = canRejoinLastResigned,
            onSelectMode = onSelectMode,
            onSelectAiElo = onSelectAiElo,
            onStartMatch = onStartMatch,
            onRejoinLast = onRejoinLast,
            boardTheme = boardTheme,
            onSelectBoardTheme = onSelectBoardTheme,
onImportPgn = onImportPgn
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val isWide = containerWidth >= 660.dp && containerWidth > containerHeight
        if (isWide) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val boardSize = minOf(containerWidth * 0.56f, containerHeight - 24.dp, 540.dp).coerceAtLeast(220.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EvalBar(
                        evalWhiteCp = activeGame.reviewFrames.getOrNull(if (activeGame.reviewMode) activeGame.reviewIndex else activeGame.reviewFrames.lastIndex)?.evalWhiteCp ?: 0,
                        height = boardSize
                    )
                    ChessBoardView(game = activeGame, boardSize = boardSize, boardTheme = boardTheme, onSquareTap = onSquareTap)
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                    activeGame.reviewMode -> ReviewPanel(activeGame, whiteAccuracy, blackAccuracy, onStepReview, onCopyPgn, onExitReview)
                    else -> PlayHudPanel(activeGame, onFlipBoard, onUndoMove, onRequestResign)
                }
                }
            }
        } else {
            val boardSize = minOf(containerWidth - 26.dp, containerHeight - 210.dp, 460.dp).coerceAtLeast(220.dp)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayTopBars(activeGame)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    EvalBar(
                        evalWhiteCp = activeGame.reviewFrames.getOrNull(if (activeGame.reviewMode) activeGame.reviewIndex else activeGame.reviewFrames.lastIndex)?.evalWhiteCp ?: 0,
                        height = boardSize
                    )
                    ChessBoardView(game = activeGame, boardSize = boardSize, boardTheme = boardTheme, onSquareTap = onSquareTap)
                }
                when {
                    activeGame.reviewMode -> ReviewPanel(activeGame, whiteAccuracy, blackAccuracy, onStepReview, onCopyPgn, onExitReview)
                    else -> PlayControlsRow(activeGame, onFlipBoard, onUndoMove, onRequestResign)
                }
            }
        }
    }
}

@Composable
private fun ModeLobby(
    selectedModeId: String,
    selectedAiElo: Int,
    selectedEngine: AiEngineType,
    onSelectEngine: (AiEngineType) -> Unit,
    canRejoinLastResigned: Boolean,
    onSelectMode: (String) -> Unit,
    onSelectAiElo: (Int) -> Unit,
    onStartMatch: (GameMode) -> Unit,
    onRejoinLast: () -> Unit,
    boardTheme: BoardTheme,
    onSelectBoardTheme: (BoardTheme) -> Unit,
onImportPgn: (String) -> Unit
) {
    var lobbyOpp by remember { mutableStateOf(GameMode.AI) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (canRejoinLastResigned) {
            item {
                Button(
                    onClick = onRejoinLast,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Accidental Resign? Rejoin Last Match", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Choose Game Mode", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = lobbyOpp == GameMode.AI, onClick = { lobbyOpp = GameMode.AI }, label = { Text("VS AI") }, modifier = Modifier.weight(1f))
                FilterChip(selected = lobbyOpp == GameMode.LOCAL, onClick = { lobbyOpp = GameMode.LOCAL }, label = { Text("Pass & Play") }, modifier = Modifier.weight(1f))
            }

            if (lobbyOpp == GameMode.AI) {
                Spacer(Modifier.height(6.dp))
                val aiTier = ChessEngine.getAiEloTier(selectedAiElo)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AI Rating", fontSize = 12.sp, color = Slate400, fontWeight = FontWeight.SemiBold)
                    Text("$selectedAiElo Elo • $aiTier", fontSize = 13.sp, color = Amber400, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = selectedAiElo.toFloat(),
                    onValueChange = { onSelectAiElo(((it / 50f).roundToInt() * 50).coerceIn(400, 2800)) },
                    valueRange = 400f..2800f,
                    steps = 47,
                    colors = SliderDefaults.colors(
                        thumbColor = ArenaColors.ChampagneGold,
                        activeTrackColor = ArenaColors.ChampagneGold,
                        inactiveTrackColor = ArenaColors.ObsidianInteractive
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("400 Beginner", fontSize = 10.sp, color = Slate400)
                    Text("1600 Adv", fontSize = 10.sp, color = Slate400)
                    Text("2800 Grandmaster", fontSize = 10.sp, color = Slate400)
                }
            }



        }
        items(GAME_MODES) { mode ->
            val selected = mode.id == selectedModeId
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) Slate800 else Slate900),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (selected) Amber400 else Slate800, RoundedCornerShape(14.dp))
                    .clickable {
                        onSelectMode(mode.id)
                        onStartMatch(if (lobbyOpp == GameMode.LOCAL) GameMode.LOCAL else GameMode.AI)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val isRated = lobbyOpp == GameMode.AI && mode.id in setOf("timeless", "blitz3", "blitz5", "rapid")
                        Text("${mode.label} • ${if (isRated) "Rated" else "Unrated"}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(mode.description, fontSize = 12.sp, color = Slate400)
                    }
                    Text(mode.timeText, fontWeight = FontWeight.SemiBold, color = Amber400, fontSize = 13.sp)
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onStartMatch(GameMode.AI) },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Play vs AI", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { onStartMatch(GameMode.LOCAL) },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Pass & Play") }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ClockPill(label: String, timeText: String, active: Boolean) {
    Surface(
        color = if (active) ArenaColors.ChampagneGold.copy(alpha = 0.16f) else ArenaColors.ObsidianSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, if (active) ArenaColors.ChampagneGold else ArenaColors.SubtleBorder, RoundedCornerShape(8.dp))
    ) {
        Text(
            "$label $timeText",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) ArenaColors.ChampagneGold else ArenaColors.TextMuted
        )
    }
}

@Composable
private fun PlayTopBars(game: ActiveGameState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("${game.modeLabel} • ${game.opponentName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber400, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClockPill("W", formatClock(game.wTime, game.timeless), !game.over && game.turn == PieceColor.WHITE)
            ClockPill("B", formatClock(game.bTime, game.timeless), !game.over && game.turn == PieceColor.BLACK)
        }
    }
}
@Composable
private fun ColumnScope.PlayHudPanel(
    game: ActiveGameState,
    onFlipBoard: () -> Unit,
    onUndoMove: () -> Unit,
    onRequestResign: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Slate900), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(game.modeLabel, fontWeight = FontWeight.Bold, color = Amber400)
            Text("White: ${formatClock(game.wTime, game.timeless)}   •   Black: ${formatClock(game.bTime, game.timeless)}", fontWeight = FontWeight.SemiBold)
            Text(if (game.isAiThinking) "${game.opponentName} is thinking..." else "Turn: ${if (game.turn == PieceColor.WHITE) "White" else "Black"}", fontSize = 12.sp, color = if (game.isAiThinking) Emerald400 else Slate400)
        }
    }
    PlayControlsRow(game, onFlipBoard, onUndoMove, onRequestResign)
    Card(colors = CardDefaults.cardColors(containerColor = Slate900), modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Moves (${game.history.size})", fontSize = 12.sp, color = Slate400, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(game.history.joinToString("  "), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayControlsRow(
    game: ActiveGameState,
    onFlipBoard: () -> Unit,
    onUndoMove: () -> Unit,
    onRequestResign: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onFlipBoard,
            enabled = game.over || game.history.isEmpty(),
            modifier = Modifier.weight(1f)
        ) { Text("Flip") }
        OutlinedButton(
            onClick = onUndoMove,
            enabled = !game.over && game.snapshots.size >= 2,
            modifier = Modifier.weight(1f)
        ) { Text("Undo") }
        Button(
            onClick = onRequestResign,
            enabled = !game.over,
            colors = ButtonDefaults.buttonColors(containerColor = Rose400, contentColor = Slate950),
            modifier = Modifier.weight(1f)
        ) { Text("Resign", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ChessBoardView(
    game: ActiveGameState,
    boardSize: androidx.compose.ui.unit.Dp,
    boardTheme: BoardTheme,
    onSquareTap: (Int, Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val sqSize = boardSize / 8
    Column(modifier = Modifier.size(boardSize).border(2.dp, Slate700)) {
        for (vr in 0..7) {
            Row {
                for (vc in 0..7) {
                    val r = if (game.flipped) 7 - vr else vr
                    val c = if (game.flipped) 7 - vc else vc
                    val isLight = (r + c) % 2 == 0
                    val isSelected = game.selected == Square(r, c)
                    val isLast = game.lastMove?.first == Square(r, c) || game.lastMove?.second == Square(r, c)
                    val isLegal = game.legalMoves.any { it.to.row == r && it.to.col == c }

                    val bg = when {
                        isSelected -> SelectedSquare
                        isLast -> Color(boardTheme.lastHex)
                        isLight -> Color(boardTheme.lightHex)
                        else -> Color(boardTheme.darkHex)
                    }
                    val piece = game.board[r][c]
                    Box(
                        modifier = Modifier
                            .size(sqSize)
                            .background(bg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSquareTap(r, c)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (piece != null) {
                            Text(
                                text = piece.glyph(),
                                fontSize = (sqSize.value * 0.65f).sp,
                                color = if (piece.color == PieceColor.WHITE) ArenaColors.PieceWhite else ArenaColors.PieceBlack
                            )
                        }
                        if (isLegal) {
                            Box(
                                modifier = Modifier
                                    .size(if (piece != null) sqSize * 0.85f else sqSize * 0.28f)
                                    .clip(CircleShape)
                                    .background(if (piece != null) Color.Transparent else Color(0x55000000))
                                    .then(if (piece != null) Modifier.border(3.dp, Color(0x77000000), CircleShape) else Modifier)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatClock(sec: Int, timeless: Boolean = false): String {
    if (timeless) return "∞"
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}


@Composable
private fun EvalBar(evalWhiteCp: Int, height: androidx.compose.ui.unit.Dp) {
    val clamped = evalWhiteCp.coerceIn(-800, 800)
    val whiteFrac = (0.5f + (clamped / 800f) * 0.45f).coerceIn(0.05f, 0.95f)
    val pawns = evalWhiteCp / 100f
    Column(
        modifier = Modifier.width(18.dp).height(height).clip(RoundedCornerShape(6.dp)).border(1.dp, Slate700, RoundedCornerShape(6.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f - whiteFrac).background(Slate800), contentAlignment = Alignment.TopCenter) {
            Text(
                text = if (pawns >= 0) "+%.1f".format(pawns) else "%.1f".format(pawns),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Amber400
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(whiteFrac).background(ArenaColors.EvalWhite))
    }
}

@Composable
private fun ReviewPanel(
    game: ActiveGameState,
    whiteAcc: Int,
    blackAcc: Int,
    onStepReview: (Int) -> Unit,
    onCopyPgn: () -> Unit,
    onExitReview: () -> Unit
) {
    val frame = game.reviewFrames.getOrNull(game.reviewIndex)
    Card(colors = CardDefaults.cardColors(containerColor = Slate900), modifier = Modifier.fillMaxWidth().border(1.dp, Amber400, RoundedCornerShape(14.dp))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Post-Game Review", fontWeight = FontWeight.Bold, color = Amber400)
                Text("Acc: W $whiteAcc% • B $blackAcc%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (frame != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${frame.notation} (${game.reviewIndex}/${(game.reviewFrames.size - 1).coerceAtLeast(0)})", fontSize = 12.sp)
                    Text(frame.tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber400)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onStepReview(0) }, modifier = Modifier.weight(1f)) { Text("|<") }
                OutlinedButton(onClick = { onStepReview(game.reviewIndex - 1) }, modifier = Modifier.weight(1f)) { Text("<") }
                OutlinedButton(onClick = { onStepReview(game.reviewIndex + 1) }, modifier = Modifier.weight(1f)) { Text(">") }
                OutlinedButton(onClick = { onStepReview(game.reviewFrames.lastIndex) }, modifier = Modifier.weight(1f)) { Text(">|") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopyPgn, modifier = Modifier.weight(1f)) { Text("Copy PGN") }
                Button(onClick = onExitReview, modifier = Modifier.weight(1f)) { Text("Back to Lobby") }
            }
        }
    }
}
