package `in`.shatranj.app.domain

import `in`.shatranj.app.data.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object ChessEngine {

    // Piece-Square Tables (from White's perspective, row 0 = rank 8, row 7 = rank 1)
    private val PAWN_PST = arrayOf(
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf( 5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf( 0,  0,  0, 22, 22,  0,  0,  0),
        intArrayOf( 5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf( 5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0)
    )

    private val KNIGHT_PST = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
        intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
        intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    private val BISHOP_PST = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
        intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
        intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
        intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )

    private val ROOK_PST = arrayOf(
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf( 5, 10, 10, 10, 10, 10, 10,  5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( 0,  0,  0,  5,  5,  0,  0,  0)
    )

    private val KING_MID_PST = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf( 20, 20,  0,  0,  0,  0, 20, 20),
        intArrayOf( 20, 30, 10,  0,  0, 10, 30, 20)
    )

    fun createInitialBoard(variant: ChessVariant): List<List<ChessPiece?>> {
        val board = Array(8) { arrayOfNulls<ChessPiece>(8) }
        val backRank = if (variant == ChessVariant.CHESS960) createChess960BackRank()
        else listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )
        for (c in 0..7) {
            board[0][c] = ChessPiece(PieceColor.BLACK, backRank[c])
            board[1][c] = ChessPiece(PieceColor.BLACK, PieceType.PAWN)
            board[6][c] = ChessPiece(PieceColor.WHITE, PieceType.PAWN)
            board[7][c] = ChessPiece(PieceColor.WHITE, backRank[c])
        }
        return board.map { it.toList() }
    }

    private fun createChess960BackRank(): List<PieceType> {
        val rank = arrayOfNulls<PieceType>(8)
        rank[listOf(0, 2, 4, 6).random()] = PieceType.BISHOP
        rank[listOf(1, 3, 5, 7).random()] = PieceType.BISHOP
        rank[rank.indices.filter { rank[it] == null }.random()] = PieceType.QUEEN
        val empty2 = rank.indices.filter { rank[it] == null }.toMutableList()
        rank[empty2.removeAt(Random.nextInt(empty2.size))] = PieceType.KNIGHT
        rank[empty2.removeAt(Random.nextInt(empty2.size))] = PieceType.KNIGHT
        val rem = rank.indices.filter { rank[it] == null }
        rank[rem[0]] = PieceType.ROOK
        rank[rem[1]] = PieceType.KING
        rank[rem[2]] = PieceType.ROOK
        return rank.map { it!! }
    }

    fun getCastlingConfigFromBoard(board: List<List<ChessPiece?>>): CastlingConfig {
        fun findSide(row: Int, color: PieceColor): SideCastlingConfig {
            var kingCol = 4
            var qRook: Int? = null
            var kRook: Int? = null
            for (c in 0..7) {
                if (board[row][c] == ChessPiece(color, PieceType.KING)) kingCol = c
            }
            for (c in 0..7) {
                if (board[row][c] == ChessPiece(color, PieceType.ROOK)) {
                    if (c < kingCol && qRook == null) qRook = c
                    else if (c > kingCol) kRook = c
                }
            }
            return SideCastlingConfig(kingCol, qRook, kRook)
        }
        return CastlingConfig(findSide(7, PieceColor.WHITE), findSide(0, PieceColor.BLACK))
    }

    private fun inBounds(r: Int, c: Int) = r in 0..7 && c in 0..7

    private fun hasLegalEnPassantCapture(board: List<List<ChessPiece?>>, turn: PieceColor, ep: Square?): Boolean {
        if (ep == null) return false
        val pawnRow = if (turn == PieceColor.WHITE) ep.row + 1 else ep.row - 1
        if (!inBounds(pawnRow, 0)) return false
        for (dc in listOf(-1, 1)) {
            val c = ep.col + dc
            if (inBounds(pawnRow, c) && board[pawnRow][c] == ChessPiece(turn, PieceType.PAWN)) {
                return true
            }
        }
        return false
    }

    fun getPositionKey(
        board: List<List<ChessPiece?>>,
        turn: PieceColor,
        castling: CastlingRights,
        enPassant: Square?
    ): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p == null) sb.append('.')
                else sb.append(if (p.color == PieceColor.WHITE) p.type.symbol else p.type.symbol.lowercaseChar())
            }
            if (r < 7) sb.append('/')
        }
        sb.append('|').append(if (turn == PieceColor.WHITE) 'w' else 'b').append('|')
        val cStr = buildString {
            if (castling.wK) append('K')
            if (castling.wQ) append('Q')
            if (castling.bK) append('k')
            if (castling.bQ) append('q')
        }.ifEmpty { "-" }
        sb.append(cStr).append('|')
        val epValid = hasLegalEnPassantCapture(board, turn, enPassant)
        sb.append(if (epValid && enPassant != null) "${enPassant.row},${enPassant.col}" else "-")
        return sb.toString()
    }

    fun hasInsufficientMaterial(board: List<List<ChessPiece?>>): Boolean {
        val wMinors = mutableListOf<Pair<PieceType, Int>>() // type, squareColorParity
        val bMinors = mutableListOf<Pair<PieceType, Int>>()
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c] ?: continue
            when (p.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.BISHOP, PieceType.KNIGHT -> {
                    val entry = p.type to ((r + c) % 2)
                    if (p.color == PieceColor.WHITE) wMinors.add(entry) else bMinors.add(entry)
                }
                PieceType.KING -> Unit
            }
        }
        // K vs K, K+minor vs K
        if (wMinors.isEmpty() && bMinors.size <= 1) return true
        if (bMinors.isEmpty() && wMinors.size <= 1) return true
        // K+B vs K+B with same-color bishops
        if (wMinors.size == 1 && bMinors.size == 1 &&
            wMinors[0].first == PieceType.BISHOP && bMinors[0].first == PieceType.BISHOP &&
            wMinors[0].second == bMinors[0].second) return true
        return false
    }

    fun canSideCheckmate(board: List<List<ChessPiece?>>, color: PieceColor): Boolean {
        var minors = 0
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c] ?: continue
            if (p.color != color) continue
            when (p.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return true
                PieceType.BISHOP, PieceType.KNIGHT -> minors++
                PieceType.KING -> Unit
            }
        }
        return minors >= 2
    }

    fun findKing(board: List<List<ChessPiece?>>, color: PieceColor): Square? {
        for (r in 0..7) for (c in 0..7) {
            if (board[r][c] == ChessPiece(color, PieceType.KING)) return Square(r, c)
        }
        return null
    }

    fun isSquareAttacked(board: List<List<ChessPiece?>>, targetR: Int, targetC: Int, byColor: PieceColor): Boolean {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                if (p.color != byColor) continue
                if (p.type == PieceType.PAWN) {
                    val dir = if (byColor == PieceColor.WHITE) -1 else 1
                    if (r + dir == targetR && abs(c - targetC) == 1) return true
                } else {
                    val moves = getPseudoMoves(board, r, c, null, null, null)
                    if (moves.any { it.to.row == targetR && it.to.col == targetC }) return true
                }
            }
        }
        return false
    }

    fun isInCheck(board: List<List<ChessPiece?>>, color: PieceColor): Boolean {
        val king = findKing(board, color) ?: return false
        return isSquareAttacked(board, king.row, king.col, color.opposite())
    }

    fun getPseudoMoves(
        board: List<List<ChessPiece?>>,
        r: Int,
        c: Int,
        castling: CastlingRights?,
        enPassant: Square?,
        castlingConfig: CastlingConfig?
    ): List<ChessMove> {
        val piece = board[r][c] ?: return emptyList()
        val color = piece.color
        val opp = color.opposite()
        val from = Square(r, c)
        val moves = mutableListOf<ChessMove>()

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (color == PieceColor.WHITE) -1 else 1
                val startRow = if (color == PieceColor.WHITE) 6 else 1
                if (inBounds(r + dir, c) && board[r + dir][c] == null) {
                    moves.add(ChessMove(from, Square(r + dir, c)))
                    if (r == startRow && board[r + 2 * dir][c] == null) {
                        moves.add(ChessMove(from, Square(r + 2 * dir, c), isDoublePush = true))
                    }
                }
                for (dc in listOf(-1, 1)) {
                    val nr = r + dir; val nc = c + dc
                    if (!inBounds(nr, nc)) continue
                    val target = board[nr][nc]
                    if (target != null && target.color == opp) {
                        moves.add(ChessMove(from, Square(nr, nc)))
                    } else if (enPassant != null && enPassant.row == nr && enPassant.col == nc) {
                        moves.add(ChessMove(from, Square(nr, nc), isEnPassant = true))
                    }
                }
            }
            PieceType.KNIGHT -> {
                val jumps = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                for ((dr, dc) in jumps) {
                    val nr = r + dr; val nc = c + dc
                    if (inBounds(nr, nc) && board[nr][nc]?.color != color) moves.add(ChessMove(from, Square(nr, nc)))
                }
            }
            PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN -> {
                val dirs = mutableListOf<Pair<Int, Int>>()
                if (piece.type != PieceType.ROOK) dirs.addAll(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
                if (piece.type != PieceType.BISHOP) dirs.addAll(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
                for ((dr, dc) in dirs) {
                    var nr = r + dr; var nc = c + dc
                    while (inBounds(nr, nc)) {
                        val target = board[nr][nc]
                        if (target == null) {
                            moves.add(ChessMove(from, Square(nr, nc)))
                        } else {
                            if (target.color == opp) moves.add(ChessMove(from, Square(nr, nc)))
                            break
                        }
                        nr += dr; nc += dc
                    }
                }
            }
            PieceType.KING -> {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr; val nc = c + dc
                    if (inBounds(nr, nc) && board[nr][nc]?.color != color) moves.add(ChessMove(from, Square(nr, nc)))
                }
                if (castling != null) {
                    val homeRow = if (color == PieceColor.WHITE) 7 else 0
                    val sCfg = if (color == PieceColor.WHITE) (castlingConfig?.white ?: SideCastlingConfig())
                    else (castlingConfig?.black ?: SideCastlingConfig())

                    if (r == homeRow && c == sCfg.kingCol && !isSquareAttacked(board, homeRow, c, opp)) {
                        fun tryCastle(side: Char, allowed: Boolean, rookCol: Int?, kingTo: Int, rookTo: Int) {
                            if (!allowed || rookCol == null) return
                            if (board[homeRow][rookCol] != ChessPiece(color, PieceType.ROOK)) return
                            val minC = minOf(c, kingTo, rookCol, rookTo)
                            val maxC = maxOf(c, kingTo, rookCol, rookTo)
                            for (col in minC..maxC) {
                                if (col != c && col != rookCol && board[homeRow][col] != null) return
                            }
                            val step = if (kingTo >= c) 1 else -1
                            var col = c
                            while (true) {
                                if (col != c && isSquareAttacked(board, homeRow, col, opp)) return
                                if (col == kingTo) break
                                col += step
                            }
                            moves.add(ChessMove(from, Square(homeRow, kingTo), castleSide = side, rookFromCol = rookCol, rookToCol = rookTo))
                        }
                        tryCastle('K', if (color == PieceColor.WHITE) castling.wK else castling.bK, sCfg.kRookCol, 6, 5)
                        tryCastle('Q', if (color == PieceColor.WHITE) castling.wQ else castling.bQ, sCfg.qRookCol, 2, 3)
                    }
                }
            }
        }
        return moves
    }

    fun applyMoveToBoard(board: List<List<ChessPiece?>>, move: ChessMove, promoType: PieceType = PieceType.QUEEN): List<List<ChessPiece?>> {
        val next = Array(8) { r -> board[r].toTypedArray() }
        val piece = next[move.from.row][move.from.col] ?: return board

        if (move.castleSide != null) {
            val rFrom = move.rookFromCol ?: if (move.castleSide == 'K') 7 else 0
            val rTo = move.rookToCol ?: if (move.castleSide == 'K') 5 else 3
            val rookPiece = board[move.from.row][rFrom]
            next[move.from.row][move.from.col] = null
            next[move.from.row][rFrom] = null
            next[move.to.row][move.to.col] = piece
            next[move.to.row][rTo] = rookPiece
            return next.map { it.toList() }
        }

        next[move.to.row][move.to.col] = piece
        next[move.from.row][move.from.col] = null
        if (move.isEnPassant) next[move.from.row][move.to.col] = null
        if (piece.type == PieceType.PAWN && (move.to.row == 0 || move.to.row == 7)) {
            next[move.to.row][move.to.col] = ChessPiece(piece.color, promoType)
        }
        return next.map { it.toList() }
    }

    fun getLegalMoves(
        board: List<List<ChessPiece?>>,
        r: Int,
        c: Int,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig
    ): List<ChessMove> {
        val piece = board[r][c] ?: return emptyList()
        return getPseudoMoves(board, r, c, castling, enPassant, castlingConfig).filter { move ->
            val testBoard = applyMoveToBoard(board, move)
            !isInCheck(testBoard, piece.color)
        }
    }

    fun getAllLegalMoves(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig
    ): List<ChessMove> {
        val all = mutableListOf<ChessMove>()
        for (r in 0..7) for (c in 0..7) {
            if (board[r][c]?.color == color) {
                all.addAll(getLegalMoves(board, r, c, castling, enPassant, castlingConfig))
            }
        }
        return all
    }

    fun perft(
        board: List<List<ChessPiece?>>,
        turn: PieceColor,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig,
        depth: Int
    ): Long {
        if (depth == 0) return 1L
        val moves = getAllLegalMoves(board, turn, castling, enPassant, castlingConfig)
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (m in moves) {
            val nextBoard = applyMoveToBoard(board, m)
            val piece = board[m.from.row][m.from.col]!!
            var c = castling
            if (piece.type == PieceType.KING) {
                c = if (turn == PieceColor.WHITE) c.copy(wK = false, wQ = false) else c.copy(bK = false, bQ = false)
            }
            val nextEp = if (m.isDoublePush) Square((m.from.row + m.to.row) / 2, m.from.col) else null
            nodes += perft(nextBoard, turn.opposite(), c, nextEp, castlingConfig, depth - 1)
        }
        return nodes
    }

    fun squareName(sq: Square): String = "${'a' + sq.col}${8 - sq.row}"

    fun evaluateBoard(
        board: List<List<ChessPiece?>>,
        variant: ChessVariant,
        checksB: Int,
        checksW: Int,
        engineType: AiEngineType = AiEngineType.LEELA
    ): Int {
        val isLeela = engineType == AiEngineType.LEELA
        var score = 0
        var bKing: Square? = null
        var wKing: Square? = null

        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c] ?: continue
            if (p.type == PieceType.KING) {
                if (p.color == PieceColor.BLACK) bKing = Square(r, c) else wKing = Square(r, c)
            }
            val pstRow = if (p.color == PieceColor.WHITE) r else 7 - r
            val posBonus = when (p.type) {
                PieceType.PAWN -> PAWN_PST[pstRow][c]
                PieceType.KNIGHT -> KNIGHT_PST[pstRow][c]
                PieceType.BISHOP -> BISHOP_PST[pstRow][c]
                PieceType.ROOK -> ROOK_PST[pstRow][c]
                PieceType.QUEEN -> if (r in 2..5 && c in 2..5) 10 else 0
                PieceType.KING -> if (variant == ChessVariant.KOTH) 0 else KING_MID_PST[pstRow][c]
            }
            val sign = if (p.color == PieceColor.BLACK) 1 else -1
            score += sign * (p.type.value + posBonus)
        }

        if (variant == ChessVariant.KOTH) {
            val hillWeight = if (isLeela) 58.0 else 48.0
            bKing?.let {
                if (it.row in 3..4 && it.col in 3..4) return 90000
                val manh = abs(it.row - 3.5) + abs(it.col - 3.5)
                score += ((7.0 - manh) * hillWeight).toInt()
            }
            wKing?.let {
                if (it.row in 3..4 && it.col in 3..4) return -90000
                val manh = abs(it.row - 3.5) + abs(it.col - 3.5)
                score -= ((7.0 - manh) * if (isLeela) 52.0 else 62.0).toInt()
            }
        } else if (variant == ChessVariant.THREE_CHECK) {
            if (checksB >= 3) return 90000
            if (checksW >= 3) return -90000
            val checkWeight = if (isLeela) 340 else 380
            score += (checksB - checksW) * checkWeight
        }
        return score
    }

    private fun quiescence(
        board: List<List<ChessPiece?>>,
        maximizingBlack: Boolean,
        alphaInit: Int,
        betaInit: Int,
        castlingConfig: CastlingConfig,
        variant: ChessVariant,
        checksB: Int,
        checksW: Int,
        qDepth: Int
    ): Int {
        val standPat = evaluateBoard(board, variant, checksB, checksW)
        if (qDepth <= 0) return standPat
        var alpha = alphaInit
        var beta = betaInit

        if (maximizingBlack) {
            if (standPat >= beta) return beta
            if (alpha < standPat) alpha = standPat
            val captures = getAllLegalMoves(board, PieceColor.BLACK, CastlingRights(false, false, false, false), null, castlingConfig)
                .filter { board[it.to.row][it.to.col] != null }
            for (m in captures) {
                val nb = applyMoveToBoard(board, m)
                val s = quiescence(nb, false, alpha, beta, castlingConfig, variant, checksB, checksW, qDepth - 1)
                if (s > alpha) alpha = s
                if (alpha >= beta) break
            }
            return alpha
        } else {
            if (standPat <= alpha) return alpha
            if (beta > standPat) beta = standPat
            val captures = getAllLegalMoves(board, PieceColor.WHITE, CastlingRights(false, false, false, false), null, castlingConfig)
                .filter { board[it.to.row][it.to.col] != null }
            for (m in captures) {
                val nb = applyMoveToBoard(board, m)
                val s = quiescence(nb, true, alpha, beta, castlingConfig, variant, checksB, checksW, qDepth - 1)
                if (s < beta) beta = s
                if (alpha >= beta) break
            }
            return beta
        }
    }

    fun getAiEloTier(elo: Int): String = when {
        elo < 800 -> "Beginner"
        elo < 1200 -> "Casual"
        elo < 1600 -> "Intermediate"
        elo < 2000 -> "Advanced"
        elo < 2400 -> "Master"
        else -> "Grandmaster"
    }

    fun pickAiMove(
        board: List<List<ChessPiece?>>,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig,
        variant: ChessVariant,
        checksB: Int,
        checksW: Int,
        aiElo: Int = 1200,
        engineType: AiEngineType = AiEngineType.LEELA
    ): ChessMove? {
        val moves = getAllLegalMoves(board, PieceColor.BLACK, castling, enPassant, castlingConfig)
        if (moves.isEmpty()) return null

        val clampedElo = aiElo.coerceIn(400, 2800)
        val depth = if (clampedElo < 1000) 1 else 2
        val qDepth = when {
            clampedElo < 1000 -> 0
            clampedElo < 1500 -> 1
            clampedElo < 2000 -> 2
            else -> 3
        }
        val randomness = when {
            clampedElo >= 2000 -> 0.0
            clampedElo <= 400 -> 0.75
            else -> ((2000 - clampedElo) / 1600.0) * 0.75
        }
        if (Random.nextDouble() < randomness) return moves.random()

        // Order captures first for better alpha-beta pruning
        val orderedMoves = moves.sortedByDescending { m -> board[m.to.row][m.to.col]?.type?.value ?: 0 }

        var bestScore = Int.MIN_VALUE
        var bestMove = orderedMoves[0]
        var alpha = Int.MIN_VALUE

        for (move in orderedMoves) {
            val b1 = applyMoveToBoard(board, move)
            val bGaveCheck = if (isInCheck(b1, PieceColor.WHITE)) 1 else 0
            val cB1 = checksB + bGaveCheck

            if (variant == ChessVariant.KOTH) {
                val bk = findKing(b1, PieceColor.BLACK)
                if (bk != null && bk.row in 3..4 && bk.col in 3..4) return move
            } else if (variant == ChessVariant.THREE_CHECK && cB1 >= 3) {
                return move
            }

            val score = if (depth == 1) {
                evaluateBoard(b1, variant, cB1, checksW, engineType) + if (bGaveCheck == 1 && variant == ChessVariant.THREE_CHECK) 180 else 0
            } else {
                val replies = getAllLegalMoves(b1, PieceColor.WHITE, castling, null, castlingConfig)
                    .sortedByDescending { r -> board[r.to.row][r.to.col]?.type?.value ?: 0 }
                if (replies.isEmpty()) {
                    if (bGaveCheck == 1) 99999 else 0
                } else {
                    var minReply = Int.MAX_VALUE
                    for (rep in replies) {
                        val b2 = applyMoveToBoard(b1, rep)
                        val wGaveCheck = if (isInCheck(b2, PieceColor.BLACK)) 1 else 0
                        val s = when {
                            variant == ChessVariant.KOTH && findKing(b2, PieceColor.WHITE)?.let { it.row in 3..4 && it.col in 3..4 } == true -> -95000
                            variant == ChessVariant.THREE_CHECK && (checksW + wGaveCheck) >= 3 -> -95000
                            else -> quiescence(b2, true, alpha, minReply, castlingConfig, variant, cB1, checksW + wGaveCheck, qDepth) +
                                if (variant == ChessVariant.THREE_CHECK && bGaveCheck == 1 && engineType == AiEngineType.STOCKFISH) 90 else 0
                        }
                        if (s < minReply) minReply = s
                        if (minReply <= alpha) break
                    }
                    minReply
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            alpha = max(alpha, bestScore)
        }
        return bestMove
    }

    fun toFen(
        board: List<List<ChessPiece?>>,
        turn: PieceColor,
        castling: CastlingRights,
        enPassant: Square?,
        halfMoveClock: Int,
        fullMoveNumber: Int
    ): String {
        val boardStr = (0..7).joinToString("/") { r ->
            buildString {
                var empty = 0
                for (c in 0..7) {
                    val p = board[r][c]
                    if (p == null) empty++
                    else {
                        if (empty > 0) { append(empty); empty = 0 }
                        append(if (p.color == PieceColor.WHITE) p.type.symbol else p.type.symbol.lowercaseChar())
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val turnStr = if (turn == PieceColor.WHITE) "w" else "b"
        val cStr = buildString {
            if (castling.wK) append('K')
            if (castling.wQ) append('Q')
            if (castling.bK) append('k')
            if (castling.bQ) append('q')
        }.ifEmpty { "-" }
        val epStr = if (enPassant != null && hasLegalEnPassantCapture(board, turn, enPassant)) squareName(enPassant) else "-"
        return "$boardStr $turnStr $cStr $epStr $halfMoveClock $fullMoveNumber"
    }

    fun parseUciMove(
        board: List<List<ChessPiece?>>,
        uci: String,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig
    ): Pair<ChessMove, PieceType>? {
        if (uci.length < 4) return null
        val fc = uci[0] - 'a'
        val fr = 8 - (uci[1] - '0')
        val tc = uci[2] - 'a'
        val tr = 8 - (uci[3] - '0')
        if (!inBounds(fr, fc) || !inBounds(tr, tc)) return null

        val promo = when (uci.getOrNull(4)?.lowercaseChar()) {
            'n' -> PieceType.KNIGHT
            'b' -> PieceType.BISHOP
            'r' -> PieceType.ROOK
            else -> PieceType.QUEEN
        }
        val legal = getLegalMoves(board, fr, fc, castling, enPassant, castlingConfig)
        // Standard match or Chess960 king-captures-own-rook notation
        val move = legal.find { it.to.row == tr && it.to.col == tc }
            ?: legal.find { it.castleSide != null && tr == fr && it.rookFromCol == tc }
            ?: return null
        return move to promo
    }


    fun fenToBoard(fen: String): Pair<List<List<ChessPiece?>>, PieceColor> {
        val parts = fen.split(" ")
        val rows = parts[0].split("/")
        val board = Array(8) { arrayOfNulls<ChessPiece>(8) }
        for (r in 0..7) {
            var c = 0
            for (ch in rows[r]) {
                if (ch in '1'..'8') c += (ch - '0')
                else {
                    val color = if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = PieceType.entries.first { it.symbol == ch.uppercaseChar() }
                    board[r][c] = ChessPiece(color, type)
                    c++
                }
            }
        }
        val turn = if (parts.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE
        return board.map { it.toList() } to turn
    }


    fun findMoveForPgnToken(
        token: String,
        board: List<List<ChessPiece?>>,
        turn: PieceColor,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig
    ): Pair<ChessMove, PieceType>? {
        val clean = token.replace(Regex("[+#!?]+$"), "").trim()
        if (clean.isEmpty()) return null
        val legal = getAllLegalMoves(board, turn, castling, enPassant, castlingConfig)

        if (clean == "O-O" || clean == "0-0") {
            return legal.find { it.castleSide == 'K' }?.let { it to PieceType.QUEEN }
        }
        if (clean == "O-O-O" || clean == "0-0-0") {
            return legal.find { it.castleSide == 'Q' }?.let { it to PieceType.QUEEN }
        }

        val eqIdx = clean.indexOf('=')
        val promo = if (eqIdx != -1 && eqIdx + 1 < clean.length) {
            PieceType.entries.find { it.symbol == clean[eqIdx + 1].uppercaseChar() } ?: PieceType.QUEEN
        } else PieceType.QUEEN
        val body = if (eqIdx != -1) clean.substring(0, eqIdx) else clean

        val coordRegex = Regex("^([KQRNB])?([a-h][1-8])[-x]([a-h][1-8])$")
        coordRegex.matchEntire(body)?.let { m ->
            val fromStr = m.groupValues[2]
            val toStr = m.groupValues[3]
            val fc = fromStr[0] - 'a'; val fr = 8 - (fromStr[1] - '0')
            val tc = toStr[0] - 'a'; val tr = 8 - (toStr[1] - '0')
            return legal.find { it.from.row == fr && it.from.col == fc && it.to.row == tr && it.to.col == tc }
                ?.let { it to promo }
        }

        val sanRegex = Regex("^([KQRNB])?([a-h])?([1-8])?x?([a-h][1-8])$")
        val sm = sanRegex.matchEntire(body) ?: return null
        val pieceChar = sm.groupValues[1].firstOrNull() ?: 'P'
        val disFile = sm.groupValues[2].firstOrNull()?.let { it - 'a' }
        val disRank = sm.groupValues[3].firstOrNull()?.let { 8 - (it - '0') }
        val toStr = sm.groupValues[4]
        val tc = toStr[0] - 'a'; val tr = 8 - (toStr[1] - '0')

        return legal.find { mv ->
            val p = board[mv.from.row][mv.from.col] ?: return@find false
            p.type.symbol == pieceChar &&
                mv.to.row == tr && mv.to.col == tc &&
                (disFile == null || mv.from.col == disFile) &&
                (disRank == null || mv.from.row == disRank)
        }?.let { it to promo }
    }

}
