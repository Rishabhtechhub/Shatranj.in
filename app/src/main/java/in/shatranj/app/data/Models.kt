package `in`.shatranj.app.data

enum class PieceColor { WHITE, BLACK;
    fun opposite() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbol: Char, val value: Int) {
    PAWN('P', 100),
    KNIGHT('N', 320),
    BISHOP('B', 330),
    ROOK('R', 500),
    QUEEN('Q', 900),
    KING('K', 20000)
}

data class ChessPiece(val color: PieceColor, val type: PieceType) {
    fun glyph(): String = when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.KING -> "♔"
            PieceType.QUEEN -> "♕"
            PieceType.ROOK -> "♖"
            PieceType.BISHOP -> "♗"
            PieceType.KNIGHT -> "♘"
            PieceType.PAWN -> "♙"
        }
        PieceColor.BLACK -> when (type) {
            PieceType.KING -> "♚"
            PieceType.QUEEN -> "♛"
            PieceType.ROOK -> "♜"
            PieceType.BISHOP -> "♝"
            PieceType.KNIGHT -> "♞"
            PieceType.PAWN -> "♟"
        }
    }
}

enum class GameMode { AI, LOCAL }

enum class ChessVariant { STANDARD, CHESS960, KOTH, THREE_CHECK }

enum class AiDifficulty(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }

enum class AiEngineType(val label: String, val binaryName: String) {
    LEELA("Leela (Lc0)", "liblc0.so"),
    STOCKFISH("Stockfish", "libstockfish.so")
}

enum class BoardTheme(val label: String, val lightHex: Long, val darkHex: Long, val lastHex: Long) {
    WOOD("Wood", 0xFFE6D3B3, 0xFF9E7352, 0xFFE5D37A),
    EMERALD("Emerald", 0xFFE2E4C8, 0xFF66854B, 0xFFE2DF6F),
    MIDNIGHT("Midnight", 0xFFB8C4D4, 0xFF43556B, 0xFF8BB4E6)
}

data class GameModeConfig(
    val id: String,
    val label: String,
    val timeText: String,
    val baseSec: Int,
    val incSec: Int,
    val variant: ChessVariant,
    val description: String,
    val timeless: Boolean = false
)

val GAME_MODES = listOf(
    GameModeConfig("timeless", "Timeless", "No Clock", 0, 0, ChessVariant.STANDARD, "Unlimited time • Play at your pace", timeless = true),
    GameModeConfig("blitz3", "Blitz 3+2", "3m + 2s", 180, 2, ChessVariant.STANDARD, "Popular competitive blitz"),
    GameModeConfig("blitz5", "Blitz 5+0", "5 min", 300, 0, ChessVariant.STANDARD, "Classic 5-minute blitz"),
    GameModeConfig("rapid10", "Rapid 10+0", "10 min", 600, 0, ChessVariant.STANDARD, "Balanced tactical thinking"),
    GameModeConfig("chess960", "Chess960", "5 min", 300, 0, ChessVariant.CHESS960, "Randomized back-rank pieces"),
    GameModeConfig("koth", "King of the Hill", "5 min", 300, 0, ChessVariant.KOTH, "Reach center e4/d4/e5/d5 with King"),
    GameModeConfig("threecheck", "Three-Check", "5 min", 300, 0, ChessVariant.THREE_CHECK, "Deliver 3 checks to win")
)

data class Square(val row: Int, val col: Int)

data class ChessMove(
    val from: Square,
    val to: Square,
    val isEnPassant: Boolean = false,
    val isDoublePush: Boolean = false,
    val castleSide: Char? = null,
    val rookFromCol: Int? = null,
    val rookToCol: Int? = null
)

data class CastlingRights(
    val wK: Boolean = true,
    val wQ: Boolean = true,
    val bK: Boolean = true,
    val bQ: Boolean = true
)

data class SideCastlingConfig(
    val kingCol: Int = 4,
    val qRookCol: Int? = 0,
    val kRookCol: Int? = 7
)

data class CastlingConfig(
    val white: SideCastlingConfig = SideCastlingConfig(),
    val black: SideCastlingConfig = SideCastlingConfig()
)

data class WldStats(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    val total: Int get() = wins + losses + draws
}

data class MatchRecord(
    val matchId: String,
    val mode: GameMode,
    val modeLabel: String,
    val opponentName: String,
    val result: String,
    val reason: String,
    val eloDelta: Int,
    val timestamp: Long
)

data class PlayerProfile(
    val playerId: String,
    val displayName: String,
    val elo: Int,
    val peakElo: Int,
    val eloHistory: List<Int>,
    val rankedStats: WldStats,
    val aiStats: WldStats,
    val matches: List<MatchRecord>,
    val selectedEngine: AiEngineType = AiEngineType.LEELA,
    val recentModeId: String = "blitz5",
    val recentOppMode: GameMode = GameMode.AI,
    val boardTheme: BoardTheme = BoardTheme.WOOD,
    val soundEnabled: Boolean = true
)

data class LeaderboardEntry(
    val playerId: String,
    val name: String,
    val elo: Int,
    val wins: Int,
    val total: Int,
    val isYou: Boolean = false
)
