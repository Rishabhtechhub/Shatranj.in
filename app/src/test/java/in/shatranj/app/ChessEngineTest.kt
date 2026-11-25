package `in`.shatranj.app

import `in`.shatranj.app.data.*
import `in`.shatranj.app.domain.ChessEngine
import `in`.shatranj.app.data.AiEngineType
import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {

    @Test
    fun perft_standardStartPosition_matchesKnownCounts() {
        val board = ChessEngine.createInitialBoard(ChessVariant.STANDARD)
        val castling = CastlingRights()
        val cfg = ChessEngine.getCastlingConfigFromBoard(board)

        assertEquals(20L, ChessEngine.perft(board, PieceColor.WHITE, castling, null, cfg, 1))
        assertEquals(400L, ChessEngine.perft(board, PieceColor.WHITE, castling, null, cfg, 2))
        assertEquals(8902L, ChessEngine.perft(board, PieceColor.WHITE, castling, null, cfg, 3))
    }

    @Test
    fun insufficientMaterial_detectsDeadPositionsCorrectly() {
        fun emptyBoard() = Array(8) { arrayOfNulls<ChessPiece>(8) }

        // K vs K
        val kVsK = emptyBoard().apply {
            this[0][4] = ChessPiece(PieceColor.BLACK, PieceType.KING)
            this[7][4] = ChessPiece(PieceColor.WHITE, PieceType.KING)
        }.map { it.toList() }
        assertTrue(ChessEngine.hasInsufficientMaterial(kVsK))

        // K+B vs K+B same color bishops (both on dark squares)
        val sameColorB = emptyBoard().apply {
            this[0][4] = ChessPiece(PieceColor.BLACK, PieceType.KING)
            this[0][1] = ChessPiece(PieceColor.BLACK, PieceType.BISHOP) // (0+1)%2 == 1
            this[7][4] = ChessPiece(PieceColor.WHITE, PieceType.KING)
            this[7][2] = ChessPiece(PieceColor.WHITE, PieceType.BISHOP) // (7+2)%2 == 1
        }.map { it.toList() }
        assertTrue(ChessEngine.hasInsufficientMaterial(sameColorB))

        // K+B vs K+B opposite color bishops -> NOT insufficient material
        val oppColorB = emptyBoard().apply {
            this[0][4] = ChessPiece(PieceColor.BLACK, PieceType.KING)
            this[0][2] = ChessPiece(PieceColor.BLACK, PieceType.BISHOP) // (0+2)%2 == 0
            this[7][4] = ChessPiece(PieceColor.WHITE, PieceType.KING)
            this[7][2] = ChessPiece(PieceColor.WHITE, PieceType.BISHOP) // (7+2)%2 == 1
        }.map { it.toList() }
        assertFalse(ChessEngine.hasInsufficientMaterial(oppColorB))
    }

    @Test
    fun chess960_castlingWorksWhenKingAndRookNotOnStandardSquares() {
        val board = Array(8) { arrayOfNulls<ChessPiece>(8) }
        board[0][4] = ChessPiece(PieceColor.BLACK, PieceType.KING)
        // White King on f1 (col 5), Kingside Rook on g1 (col 6), Queenside Rook on b1 (col 1)
        board[7][5] = ChessPiece(PieceColor.WHITE, PieceType.KING)
        board[7][6] = ChessPiece(PieceColor.WHITE, PieceType.ROOK)
        board[7][1] = ChessPiece(PieceColor.WHITE, PieceType.ROOK)
        val listBoard = board.map { it.toList() }

        val cfg = ChessEngine.getCastlingConfigFromBoard(listBoard)
        val moves = ChessEngine.getLegalMoves(listBoard, 7, 5, CastlingRights(), null, cfg)
        val castleK = moves.find { it.castleSide == 'K' }
        assertNotNull("Kingside castling should be legal in Chess960", castleK)

        val after = ChessEngine.applyMoveToBoard(listBoard, castleK!!)
        assertEquals(ChessPiece(PieceColor.WHITE, PieceType.KING), after[7][6])
        assertEquals(ChessPiece(PieceColor.WHITE, PieceType.ROOK), after[7][5])
    }

    @Test
    fun positionKey_ignoresPhantomEnPassantWhenNoPawnCanCapture() {
        val board = ChessEngine.createInitialBoard(ChessVariant.STANDARD)
        val k1 = ChessEngine.getPositionKey(board, PieceColor.BLACK, CastlingRights(), Square(5, 4))
        val k2 = ChessEngine.getPositionKey(board, PieceColor.BLACK, CastlingRights(), null)
        assertEquals(k1, k2)
    }

    @Test
    fun variantEnginesPickValidMoves() {
        for (variant in listOf(ChessVariant.CHESS960, ChessVariant.KOTH, ChessVariant.THREE_CHECK)) {
            val board = ChessEngine.createInitialBoard(variant)
            val cfg = ChessEngine.getCastlingConfigFromBoard(board)
            for (eng in listOf(AiEngineType.LEELA, AiEngineType.STOCKFISH)) {
                val mv = ChessEngine.pickAiMove(board, CastlingRights(), null, cfg, variant, 0, 0, 1600, eng)
                assertNotNull("Expected legal move for $eng in $variant", mv)
            }
        }
    }
}
