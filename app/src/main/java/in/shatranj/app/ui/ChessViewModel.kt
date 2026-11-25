package `in`.shatranj.app.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.shatranj.app.data.*
import `in`.shatranj.app.domain.ChessEngine
import `in`.shatranj.app.domain.UciEngineBridge
import `in`.shatranj.app.domain.EloCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ReviewFrame(
    val board: List<List<ChessPiece?>>,
    val lastMove: Pair<Square, Square>?,
    val evalWhiteCp: Int,
    val tag: String,
    val notation: String,
    val color: PieceColor?,
    val cpLoss: Int
)

data class GameSnapshot(
    val board: List<List<ChessPiece?>>,
    val turn: PieceColor,
    val castling: CastlingRights,
    val enPassant: Square?,
    val capturedByW: List<ChessPiece>,
    val capturedByB: List<ChessPiece>,
    val history: List<String>,
    val lastMove: Pair<Square, Square>?,
    val halfMoveClock: Int,
    val positionCounts: Map<String, Int>,
    val checksGivenW: Int,
    val checksGivenB: Int
)

data class ActiveGameState(
    val mode: GameMode,
    val modeId: String = "blitz5",
    val modeLabel: String,
    val variant: ChessVariant,
    val incSec: Int,
    val opponentName: String,
    val board: List<List<ChessPiece?>>,
    val turn: PieceColor = PieceColor.WHITE,
    val selected: Square? = null,
    val legalMoves: List<ChessMove> = emptyList(),
    val lastMove: Pair<Square, Square>? = null,
    val castling: CastlingRights,
    val castlingConfig: CastlingConfig,
    val enPassant: Square? = null,
    val halfMoveClock: Int = 0,
    val positionCounts: Map<String, Int> = emptyMap(),
    val capturedByW: List<ChessPiece> = emptyList(),
    val capturedByB: List<ChessPiece> = emptyList(),
    val checksGivenW: Int = 0,
    val checksGivenB: Int = 0,
    val history: List<String> = emptyList(),
    val snapshots: List<GameSnapshot> = emptyList(),
    val flipped: Boolean = false,
    val wTime: Int,
    val bTime: Int,
    val timeless: Boolean = false,
    val over: Boolean = false,
    val resultTitle: String = "",
    val resultReason: String = "",
    val eloDelta: Int = 0,
    val pendingPromotion: ChessMove? = null,
    val isAiThinking: Boolean = false,
    val reviewFrames: List<ReviewFrame> = emptyList(),
    val reviewMode: Boolean = false,
    val reviewIndex: Int = 0,
    val resultWinner: String = "",
    val roomCode: String? = null,
    val roomRole: PieceColor? = null
)

class ChessViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PlayerRepository(application)
    private val uciBridge = UciEngineBridge(application)

    private val _profile = MutableStateFlow(repo.loadProfile())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    private val _activeGame = MutableStateFlow<ActiveGameState?>(null)
    val activeGame: StateFlow<ActiveGameState?> = _activeGame.asStateFlow()

    private val _selectedModeId = MutableStateFlow("blitz5")
    val selectedModeId: StateFlow<String> = _selectedModeId.asStateFlow()

    private val _selectedAiElo = MutableStateFlow(1200)
    val selectedAiElo: StateFlow<Int> = _selectedAiElo.asStateFlow()


    private val _showResignDialog = MutableStateFlow(false)
    val showResignDialog: StateFlow<Boolean> = _showResignDialog.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var lastResignedSnapshot: Triple<ActiveGameState, String, Boolean>? = null
    val canRejoinLastResigned: Boolean get() = lastResignedSnapshot != null

    private var clockJob: Job? = null
    private var aiJob: Job? = null

    fun selectMode(id: String) { _selectedModeId.value = id }
    fun selectAiElo(elo: Int) { _selectedAiElo.value = elo.coerceIn(400, 2800) }

    private fun playTone(kind: String) {
        if (!_profile.value.soundEnabled) return
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            val tone = when (kind) {
                "capture" -> ToneGenerator.TONE_PROP_ACK
                "check" -> ToneGenerator.TONE_PROP_BEEP2
                "gameover" -> ToneGenerator.TONE_PROP_PROMPT
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            tg.startTone(tone, 65)
            tg.release()
        }
    }

    fun toggleSound() {
        val next = !_profile.value.soundEnabled
        val updated = _profile.value.copy(soundEnabled = next)
        _profile.value = updated
        repo.saveProfile(updated)
        _toastMessage.value = if (next) "Sound enabled" else "Sound muted"
    }

    fun updateDisplayName(name: String) {
        val clean = name.trim().take(20)
        if (clean.isEmpty()) return
        val updated = _profile.value.copy(displayName = clean)
        _profile.value = updated
        repo.saveProfile(updated)
        _toastMessage.value = "Name updated to $clean"
    }

    fun selectBoardTheme(theme: BoardTheme) {
        val updated = _profile.value.copy(boardTheme = theme)
        _profile.value = updated
        repo.saveProfile(updated)
    }

    fun selectAiEngine(engine: AiEngineType) {
        val updated = _profile.value.copy(selectedEngine = engine)
        _profile.value = updated
        repo.saveProfile(updated)
    }
    fun isNativeEngineAvailable(engine: AiEngineType): Boolean = uciBridge.isNativeEngineAvailable(engine)
    fun requestResign() { if (_activeGame.value?.over == false) _showResignDialog.value = true }
    fun dismissResignDialog() { _showResignDialog.value = false }
    fun clearToast() { _toastMessage.value = null }

    fun getLeaderboard(): List<LeaderboardEntry> = repo.getLeaderboard(_profile.value)

    fun launchQuickPlayRecent(onNavigateToPlay: () -> Unit = {}) {
        val p = _profile.value
        val modeId = if (GAME_MODES.any { it.id == p.recentModeId }) p.recentModeId else "blitz5"
        _selectedModeId.value = modeId ?: _selectedModeId.value
        val oppMode = if (p.recentOppMode == GameMode.LOCAL) GameMode.LOCAL else GameMode.AI
        startNewMatch(oppMode, onNavigateToPlay)
    }

    fun startNewMatch(mode: GameMode, onNavigateToPlay: () -> Unit = {}) {
        val current = _activeGame.value
        if (current != null && !current.over && current.history.isNotEmpty()) {
            _toastMessage.value = "Finish or resign your current match first."
            onNavigateToPlay()
            return
        }

        val cfg = GAME_MODES.find { it.id == _selectedModeId.value } ?: GAME_MODES[2]
        val updatedProf = _profile.value.copy(recentModeId = cfg.id, recentOppMode = mode)
        _profile.value = updatedProf
        repo.saveProfile(updatedProf)
        val initBoard = ChessEngine.createInitialBoard(cfg.variant)
        val castlingCfg = ChessEngine.getCastlingConfigFromBoard(initBoard)
        val initCastling = CastlingRights(
            wK = castlingCfg.white.kRookCol != null,
            wQ = castlingCfg.white.qRookCol != null,
            bK = castlingCfg.black.kRookCol != null,
            bQ = castlingCfg.black.qRookCol != null
        )
        val initKey = ChessEngine.getPositionKey(initBoard, PieceColor.WHITE, initCastling, null)
        val oppName = if (mode == GameMode.AI) "${_profile.value.selectedEngine.label} (${_selectedAiElo.value} • ${ChessEngine.getAiEloTier(_selectedAiElo.value)})" else "Local Friend (2P)"

        lastResignedSnapshot = null
        _activeGame.value = ActiveGameState(
            mode = mode,
            modeId = cfg.id,
            modeLabel = cfg.label,
            variant = cfg.variant,
            incSec = cfg.incSec,
            opponentName = oppName,
            board = initBoard,
            castling = initCastling,
            castlingConfig = castlingCfg,
            positionCounts = mapOf(initKey to 1),
            wTime = cfg.baseSec,
            bTime = cfg.baseSec,
            timeless = cfg.timeless,
            reviewFrames = listOf(ReviewFrame(initBoard, null, 0, "Start", "Start Position", null, 0))
        )
        startClock()
        onNavigateToPlay()
    }

    fun toggleFlip() {
        val g = _activeGame.value ?: return
        if (!g.over && g.history.isNotEmpty()) {
            _toastMessage.value = "Board flip is locked once the match begins."
            return
        }
        _activeGame.value = g.copy(flipped = !g.flipped)
    }

    fun onSquareTapped(row: Int, col: Int) {
        val g = _activeGame.value ?: return
        if (g.over || g.pendingPromotion != null) return
        if (g.mode != GameMode.LOCAL && g.turn == PieceColor.BLACK) return

        // Support Chess960 UX: tapping own Rook while King is selected triggers castling
        val homeRow = if (g.turn == PieceColor.WHITE) 7 else 0
        val castleByRookTap = if (g.selected != null && row == homeRow) {
            g.legalMoves.find { it.castleSide != null && it.rookFromCol == col }
        } else null

        val tappedMove = castleByRookTap ?: g.legalMoves.find { it.to.row == row && it.to.col == col }
        if (tappedMove != null) {
            val movingPiece = g.board[tappedMove.from.row][tappedMove.from.col]
            if (movingPiece?.type == PieceType.PAWN && (row == 0 || row == 7)) {
                _activeGame.value = g.copy(pendingPromotion = tappedMove)
            } else {
                executeMove(tappedMove, PieceType.QUEEN)
            }
            return
        }

        val piece = g.board[row][col]
        if (piece != null && piece.color == g.turn) {
            val legal = ChessEngine.getLegalMoves(g.board, row, col, g.castling, g.enPassant, g.castlingConfig)
            _activeGame.value = g.copy(selected = Square(row, col), legalMoves = legal)
        } else {
            _activeGame.value = g.copy(selected = null, legalMoves = emptyList())
        }
    }

    fun completePromotion(type: PieceType) {
        val g = _activeGame.value ?: return
        val move = g.pendingPromotion ?: return
        _activeGame.value = g.copy(pendingPromotion = null)
        executeMove(move, type)
    }
    private fun executeMove(move: ChessMove, promoType: PieceType) {
        val g = _activeGame.value ?: return
        val piece = g.board[move.from.row][move.from.col] ?: return
        val color = piece.color

        val snapshot = GameSnapshot(
            board = g.board,
            turn = g.turn,
            castling = g.castling,
            enPassant = g.enPassant,
            capturedByW = g.capturedByW,
            capturedByB = g.capturedByB,
            history = g.history,
            lastMove = g.lastMove,
            halfMoveClock = g.halfMoveClock,
            positionCounts = g.positionCounts,
            checksGivenW = g.checksGivenW,
            checksGivenB = g.checksGivenB
        )

        var captured = g.board[move.to.row][move.to.col]
        if (move.isEnPassant) captured = g.board[move.from.row][move.to.col]
        val newCapW = if (captured != null && color == PieceColor.WHITE) g.capturedByW + captured else g.capturedByW
        val newCapB = if (captured != null && color == PieceColor.BLACK) g.capturedByB + captured else g.capturedByB

        val nextBoard = ChessEngine.applyMoveToBoard(g.board, move, promoType)

        var c = g.castling
        val cCfg = g.castlingConfig
        if (piece == ChessPiece(PieceColor.WHITE, PieceType.KING)) c = c.copy(wK = false, wQ = false)
        if (piece == ChessPiece(PieceColor.BLACK, PieceType.KING)) c = c.copy(bK = false, bQ = false)
        if (move.from.row == 7 && move.from.col == cCfg.white.qRookCol) c = c.copy(wQ = false)
        if (move.from.row == 7 && move.from.col == cCfg.white.kRookCol) c = c.copy(wK = false)
        if (move.from.row == 0 && move.from.col == cCfg.black.qRookCol) c = c.copy(bQ = false)
        if (move.from.row == 0 && move.from.col == cCfg.black.kRookCol) c = c.copy(bK = false)
        if (move.to.row == 0 && move.to.col == cCfg.black.qRookCol) c = c.copy(bQ = false)
        if (move.to.row == 0 && move.to.col == cCfg.black.kRookCol) c = c.copy(bK = false)
        if (move.to.row == 7 && move.to.col == cCfg.white.qRookCol) c = c.copy(wQ = false)
        if (move.to.row == 7 && move.to.col == cCfg.white.kRookCol) c = c.copy(wK = false)

        val nextEp = if (move.isDoublePush) Square((move.from.row + move.to.row) / 2, move.from.col) else null
        val nextHalfMove = if (piece.type == PieceType.PAWN || captured != null) 0 else g.halfMoveClock + 1
        val nextTurn = color.opposite()

        val notation = if (move.castleSide == 'K') "O-O"
        else if (move.castleSide == 'Q') "O-O-O"
        else (if (piece.type == PieceType.PAWN) "" else piece.type.symbol.toString()) +
            ChessEngine.squareName(move.from) + (if (captured != null) "x" else "-") + ChessEngine.squareName(move.to)

        val nextWTime = if (color == PieceColor.WHITE) g.wTime + g.incSec else g.wTime
        val nextBTime = if (color == PieceColor.BLACK) g.bTime + g.incSec else g.bTime

        val inCheckNow = ChessEngine.isInCheck(nextBoard, nextTurn)
        val nextChecksW = if (inCheckNow && color == PieceColor.WHITE) g.checksGivenW + 1 else g.checksGivenW
        val nextChecksB = if (inCheckNow && color == PieceColor.BLACK) g.checksGivenB + 1 else g.checksGivenB

        val posKey = ChessEngine.getPositionKey(nextBoard, nextTurn, c, nextEp)
        val nextPosCounts = g.positionCounts.toMutableMap()
        nextPosCounts[posKey] = (nextPosCounts[posKey] ?: 0) + 1

        _activeGame.value = g.copy(
            board = nextBoard,
            turn = nextTurn,
            selected = null,
            legalMoves = emptyList(),
            lastMove = move.from to move.to,
            castling = c,
            enPassant = nextEp,
            halfMoveClock = nextHalfMove,
            positionCounts = nextPosCounts,
            capturedByW = newCapW,
            capturedByB = newCapB,
            checksGivenW = nextChecksW,
            checksGivenB = nextChecksB,
            history = g.history + notation,
            snapshots = g.snapshots + snapshot,
            wTime = nextWTime,
            bTime = nextBTime,
            reviewFrames = g.reviewFrames + run {
                val prevEvalW = g.reviewFrames.lastOrNull()?.evalWhiteCp ?: 0
                val evalW = -ChessEngine.evaluateBoard(nextBoard, g.variant, nextChecksB, nextChecksW)
                val cpLoss = if (color == PieceColor.WHITE) (prevEvalW - evalW).coerceAtLeast(0) else (evalW - prevEvalW).coerceAtLeast(0)
                val tag = when {
                    cpLoss <= 15 -> "Best"
                    cpLoss <= 50 -> "Good"
                    cpLoss <= 120 -> "Inaccuracy"
                    cpLoss <= 250 -> "Mistake"
                    else -> "Blunder"
                }
                val moveNum = (g.history.size / 2) + 1
                val prefix = if (color == PieceColor.WHITE) "$moveNum." else "$moveNum..."
                ReviewFrame(nextBoard, move.from to move.to, evalW, tag, "$prefix $notation", color, cpLoss)
            }
        )
        val nextMoves = ChessEngine.getAllLegalMoves(nextBoard, nextTurn, c, nextEp, cCfg)
        val kothWin = g.variant == ChessVariant.KOTH && ChessEngine.findKing(nextBoard, color)?.let { it.row in 3..4 && it.col in 3..4 } == true
        val threeCheckWin = g.variant == ChessVariant.THREE_CHECK && (if (color == PieceColor.WHITE) nextChecksW else nextChecksB) >= 3

        when {
            kothWin -> finishGame(color.name, "King of the Hill")
            threeCheckWin -> finishGame(color.name, "Three-Check (3/3)")
            nextMoves.isEmpty() -> if (inCheckNow) finishGame(color.name, "Checkmate") else finishGame("DRAW", "Stalemate")
            ChessEngine.hasInsufficientMaterial(nextBoard) -> finishGame("DRAW", "Insufficient Material")
            (nextPosCounts[posKey] ?: 0) >= 3 -> finishGame("DRAW", "Threefold Repetition")
            nextHalfMove >= 100 -> finishGame("DRAW", "50-Move Rule")
            else -> if (nextTurn == PieceColor.BLACK && g.mode == GameMode.AI) scheduleAiMove()
        }
    }

    fun undoMove() {
        val g = _activeGame.value ?: return
        if (g.over || g.snapshots.size < 2) return
        aiJob?.cancel()
        val prev = g.snapshots[g.snapshots.size - 2]
        _activeGame.value = g.copy(
            board = prev.board,
            turn = prev.turn,
            castling = prev.castling,
            enPassant = prev.enPassant,
            capturedByW = prev.capturedByW,
            capturedByB = prev.capturedByB,
            history = prev.history,
            lastMove = prev.lastMove,
            halfMoveClock = prev.halfMoveClock,
            positionCounts = prev.positionCounts,
            checksGivenW = prev.checksGivenW,
            checksGivenB = prev.checksGivenB,
            snapshots = g.snapshots.dropLast(2),
            selected = null,
            legalMoves = emptyList()
        )
    }

    fun confirmResign() {
        _showResignDialog.value = false
        val g = _activeGame.value ?: return
        if (g.over) return
        val winner = if (g.mode == GameMode.LOCAL) g.turn.opposite().name else PieceColor.BLACK.name
        val matchId = "M-" + UUID.randomUUID().toString().take(6).uppercase()
        if (g.mode == GameMode.AI || g.mode == GameMode.LOCAL) {
            lastResignedSnapshot = Triple(g.copy(), matchId, g.mode == GameMode.AI && g.modeId in setOf("timeless", "blitz3", "blitz5", "rapid"))
        }
        finishGame(winner, "Resignation", fixedMatchId = matchId)
    }

    fun rejoinLastResignedMatch() {
        val snap = lastResignedSnapshot ?: return
        lastResignedSnapshot = null
        val (restoredGame, matchId, hadAiStat) = snap
        val p = _profile.value
        val updatedAi = if (hadAiStat) p.aiStats.copy(losses = (p.aiStats.losses - 1).coerceAtLeast(0)) else p.aiStats
        val updatedMatches = if (p.matches.firstOrNull()?.matchId == matchId) p.matches.drop(1) else p.matches
        val newProfile = p.copy(aiStats = updatedAi, matches = updatedMatches)
        _profile.value = newProfile
        repo.saveProfile(newProfile)

        _activeGame.value = restoredGame.copy(over = false)
        startClock()
        _toastMessage.value = "Rejoined your last match!"
    }
    fun dismissGameOver() {
        _activeGame.value = null
    }


    fun loadPgnReplay(pgnText: String) {
        if (pgnText.isBlank()) {
            _toastMessage.value = "Paste a PGN first!"
            return
        }
        val fenMatch = Regex("""\[FEN\s+"([^"]+)"\]""", RegexOption.IGNORE_CASE).find(pgnText)
        val blackMatch = Regex("""\[Black\s+"([^"]+)"\]""", RegexOption.IGNORE_CASE).find(pgnText)

        val body = pgnText
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\{[^}]*\\}"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\d+\\.\\.\\."), " ")
            .replace(Regex("\\d+\\."), " ")

        val tokens = body.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() && !it.matches(Regex("^(1-0|0-1|1/2-1/2|\\*)$")) }

        if (tokens.isEmpty()) {
            _toastMessage.value = "No valid moves found in PGN."
            return
        }

        val (initBoard, initTurn) = if (fenMatch != null) ChessEngine.fenToBoard(fenMatch.groupValues[1])
        else ChessEngine.createInitialBoard(ChessVariant.STANDARD) to PieceColor.WHITE

        var board = initBoard
        var turn = initTurn
        var castling = CastlingRights()
        val cCfg = ChessEngine.getCastlingConfigFromBoard(board)
        var enPassant: Square? = null
        val history = mutableListOf<String>()
        val frames = mutableListOf(ReviewFrame(initBoard, null, 0, "Start", "Start Position", null, 0))

        for ((i, tok) in tokens.withIndex()) {
            val parsed = ChessEngine.findMoveForPgnToken(tok, board, turn, castling, enPassant, cCfg)
            if (parsed == null) {
                _toastMessage.value = "Could not parse move #${i + 1}: $tok"
                return
            }
            val (move, promo) = parsed
            val piece = board[move.from.row][move.from.col]!!
            board = ChessEngine.applyMoveToBoard(board, move, promo)

            if (piece == ChessPiece(PieceColor.WHITE, PieceType.KING)) castling = castling.copy(wK = false, wQ = false)
            if (piece == ChessPiece(PieceColor.BLACK, PieceType.KING)) castling = castling.copy(bK = false, bQ = false)
            if (move.from.row == 7 && move.from.col == cCfg.white.qRookCol) castling = castling.copy(wQ = false)
            if (move.from.row == 7 && move.from.col == cCfg.white.kRookCol) castling = castling.copy(wK = false)
            if (move.from.row == 0 && move.from.col == cCfg.black.qRookCol) castling = castling.copy(bQ = false)
            if (move.from.row == 0 && move.from.col == cCfg.black.kRookCol) castling = castling.copy(bK = false)
            enPassant = if (move.isDoublePush) Square((move.from.row + move.to.row) / 2, move.from.col) else null

            history.add(tok)
            val prevEvalW = frames.last().evalWhiteCp
            val evalW = -ChessEngine.evaluateBoard(board, ChessVariant.STANDARD, 0, 0)
            val cpLoss = if (turn == PieceColor.WHITE) (prevEvalW - evalW).coerceAtLeast(0) else (evalW - prevEvalW).coerceAtLeast(0)
            val tag = when {
                cpLoss <= 15 -> "Best"
                cpLoss <= 50 -> "Good"
                cpLoss <= 120 -> "Inaccuracy"
                cpLoss <= 250 -> "Mistake"
                else -> "Blunder"
            }
            val mvNum = (history.size + 1) / 2
            val prefix = if (turn == PieceColor.WHITE) "$mvNum." else "$mvNum..."
            frames.add(ReviewFrame(board, move.from to move.to, evalW, tag, "$prefix $tok", turn, cpLoss))
            turn = turn.opposite()
        }

        clockJob?.cancel()
        aiJob?.cancel()
        _activeGame.value = ActiveGameState(
            mode = GameMode.LOCAL,
            modeLabel = "PGN Replay",
            variant = ChessVariant.STANDARD,
            incSec = 0,
            opponentName = blackMatch?.groupValues?.get(1) ?: "Black (PGN)",
            board = frames[0].board,
            turn = initTurn,
            castling = castling,
            castlingConfig = cCfg,
            history = history,
            wTime = 0,
            bTime = 0,
            timeless = true,
            over = true,
            reviewFrames = frames,
            reviewMode = true,
            reviewIndex = 0
        )
        _toastMessage.value = "Loaded ${history.size} moves for PGN Replay!"
    }

    fun enterReviewMode() {
        val g = _activeGame.value ?: return
        val lastIdx = (g.reviewFrames.size - 1).coerceAtLeast(0)
        _activeGame.value = g.copy(reviewMode = true, reviewIndex = lastIdx)
    }

    fun stepReview(index: Int) {
        val g = _activeGame.value ?: return
        if (g.reviewFrames.isEmpty()) return
        val clamped = index.coerceIn(0, g.reviewFrames.size - 1)
        val frame = g.reviewFrames[clamped]
        _activeGame.value = g.copy(reviewIndex = clamped, board = frame.board, lastMove = frame.lastMove)
    }

    fun computeSideAccuracy(color: PieceColor): Int {
        val frames = _activeGame.value?.reviewFrames?.filter { it.color == color } ?: return 100
        if (frames.isEmpty()) return 100
        val avgLoss = frames.map { it.cpLoss }.average()
        val acc = 103.1668 * kotlin.math.exp(-0.04354 * avgLoss.div(3.2).coerceAtMost(200.0)) - 3.1669
        return acc.toInt().coerceIn(0, 100)
    }

    fun buildPgn(): String {
        val g = _activeGame.value ?: return ""
        val res = when (g.resultWinner) {
            "WHITE" -> "1-0"
            "BLACK" -> "0-1"
            "DRAW" -> "1/2-1/2"
            else -> "*"
        }
        val headers = listOf(
            "[Event \"Shatranj.in - ${g.modeLabel}\"]",
            "[White \"${_profile.value.displayName}\"]",
            "[Black \"${g.opponentName}\"]",
            "[Result \"$res\"]"
        ).joinToString("\n")
        val moves = g.history.chunked(2).mapIndexed { idx, pair ->
            "${idx + 1}. ${pair[0]}${if (pair.size > 1) " ${pair[1]}" else ""}"
        }.joinToString(" ")
        return "$headers\n\n$moves $res"
    }

    private fun finishGame(winner: String, reason: String, fixedMatchId: String? = null) {
        val g = _activeGame.value ?: return
        playTone("gameover")
        clockJob?.cancel()
        aiJob?.cancel()

        val resultStr = when (winner) {
            "DRAW" -> "DRAW"
            "WHITE" -> "WIN"
            else -> "LOSS"
        }

        val p = _profile.value
        var newElo = p.elo
        var eloDelta = 0
        var newRanked = p.rankedStats
        var newAi = p.aiStats
        var newHistory = p.eloHistory

        val isRatedMode = g.mode == GameMode.AI && g.modeId in setOf("timeless", "blitz3", "blitz5", "rapid")
        if (isRatedMode) {
            val score = when (resultStr) { "WIN" -> 1.0; "DRAW" -> 0.5; else -> 0.0 }
            eloDelta = EloCalculator.calculateEloDelta(p.elo, _selectedAiElo.value, score, p.peakElo, p.aiStats.total)
            newElo = (p.elo + eloDelta).coerceAtLeast(100)
            newHistory = (p.eloHistory + newElo).takeLast(30)
            newAi = when (resultStr) {
                "WIN" -> p.aiStats.copy(wins = p.aiStats.wins + 1)
                "DRAW" -> p.aiStats.copy(draws = p.aiStats.draws + 1)
                else -> p.aiStats.copy(losses = p.aiStats.losses + 1)
            }
        } else {
            newRanked = when (resultStr) {
                "WIN" -> p.rankedStats.copy(wins = p.rankedStats.wins + 1)
                "DRAW" -> p.rankedStats.copy(draws = p.rankedStats.draws + 1)
                else -> p.rankedStats.copy(losses = p.rankedStats.losses + 1)
            }
        }

        val matchId = fixedMatchId ?: ("M-" + UUID.randomUUID().toString().take(6).uppercase())
        val record = MatchRecord(
            matchId = matchId,
            mode = g.mode,
            modeLabel = g.modeLabel,
            opponentName = g.opponentName,
            result = resultStr,
            reason = reason,
            eloDelta = eloDelta,
            timestamp = System.currentTimeMillis()
        )
        val updatedProfile = p.copy(
            elo = newElo,
            peakElo = maxOf(p.peakElo, newElo),
            eloHistory = newHistory,
            rankedStats = newRanked,
            aiStats = newAi,
            matches = listOf(record) + p.matches.take(39)
        )
        _profile.value = updatedProfile
        repo.saveProfile(updatedProfile)

        val title = when (resultStr) {
            "WIN" -> if (g.mode == GameMode.LOCAL) "White Wins!" else "Victory!"
            "DRAW" -> "Draw"
            else -> if (g.mode == GameMode.LOCAL) "Black Wins!" else "Defeat"
        }
        _activeGame.value = g.copy(over = true, resultTitle = title, resultReason = reason, eloDelta = eloDelta, resultWinner = winner)
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val g = _activeGame.value ?: break
                if (g.over || g.pendingPromotion != null || g.timeless) continue
                if (g.turn == PieceColor.WHITE) {
                    val t = g.wTime - 1
                    if (t <= 0) {
                        _activeGame.value = g.copy(wTime = 0)
                        if (ChessEngine.canSideCheckmate(g.board, PieceColor.BLACK)) {
                            finishGame(PieceColor.BLACK.name, "Time Out")
                        } else {
                            finishGame("DRAW", "Timeout vs Insufficient Material")
                        }
                        break
                    } else _activeGame.value = g.copy(wTime = t)
                } else {
                    val t = g.bTime - 1
                    if (t <= 0) {
                        _activeGame.value = g.copy(bTime = 0)
                        if (ChessEngine.canSideCheckmate(g.board, PieceColor.WHITE)) {
                            finishGame(PieceColor.WHITE.name, "Time Out")
                        } else {
                            finishGame("DRAW", "Timeout vs Insufficient Material")
                        }
                        break
                    } else _activeGame.value = g.copy(bTime = t)
                }
            }
        }
    }

    private fun computeHumanThinkDelayMs(g: ActiveGameState, aiElo: Int): Long {
        val legalCount = ChessEngine.getAllLegalMoves(g.board, PieceColor.BLACK, g.castling, g.enPassant, g.castlingConfig).size
        if (legalCount <= 1) return (450L..700L).random()

        val bTime = g.bTime.coerceAtLeast(1)
        val ply = g.history.size
        val eloNorm = ((aiElo - 400) / 2400.0).coerceIn(0.0, 1.0)

        val baseScale = if (g.timeless) 1.0 else when {
            bTime <= 15 -> 0.25
            bTime <= 45 -> 0.55
            (g.wTime + g.bTime) < 240 -> 0.65
            else -> 1.0
        }
        var phaseFactor = when {
            ply < 10 -> 0.65
            legalCount > 32 -> 1.25
            else -> 1.0
        }
        if (ChessEngine.isInCheck(g.board, PieceColor.BLACK)) phaseFactor *= 0.85

        val u1 = kotlin.random.Random.nextDouble(1e-5, 1.0)
        val u2 = kotlin.random.Random.nextDouble()
        val z = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        val medianMs = 950.0 + eloNorm * 1450.0
        var target = medianMs * kotlin.math.exp(0.38 * z) * phaseFactor * baseScale

        if (ply >= 10 && bTime > 45 && kotlin.random.Random.nextDouble() < 0.07) {
            target += 1400.0 + kotlin.random.Random.nextDouble() * 1800.0
        }
        val maxByClock = if (g.timeless) 6200L else (bTime * 220L).coerceAtMost(6200L)
        return target.toLong().coerceIn(450L, maxByClock.coerceAtLeast(450L))
    }

    private fun scheduleAiMove() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val g = _activeGame.value ?: return@launch
            if (g.over || g.turn != PieceColor.BLACK) return@launch
            _activeGame.value = g.copy(isAiThinking = true)

            val aiElo = _selectedAiElo.value
            val targetDelay = computeHumanThinkDelayMs(g, aiElo)
            val tStart = System.currentTimeMillis()

            val engine = _profile.value.selectedEngine
            val fullMoves = (g.history.size / 2) + 1
            val result = uciBridge.selectBestMove(
                engine = engine,
                board = g.board,
                castling = g.castling,
                enPassant = g.enPassant,
                castlingConfig = g.castlingConfig,
                variant = g.variant,
                halfMoveClock = g.halfMoveClock,
                fullMoveNumber = fullMoves,
                checksB = g.checksGivenB,
                checksW = g.checksGivenW,
                aiElo = aiElo
            )

            val elapsed = System.currentTimeMillis() - tStart
            val remaining = (targetDelay - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)

            val latest = _activeGame.value
            if (latest != null && !latest.over && latest.turn == PieceColor.BLACK) {
                _activeGame.value = latest.copy(isAiThinking = false)
                if (result != null) executeMove(result.move, result.promotion)
            }
        }
    }
}
