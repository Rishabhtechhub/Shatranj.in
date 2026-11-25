package `in`.shatranj.app.domain

import android.content.Context
import `in`.shatranj.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class EngineMoveResult(
    val move: ChessMove,
    val promotion: PieceType = PieceType.QUEEN,
    val source: String
)

/**
 * Unified UCI Engine Bridge supporting native Leela Chess Zero (Lc0) and Stockfish binaries
 * placed in jniLibs/<abi>/ (liblc0.so, libstockfish.so) with weights in assets/weights/.
 * Automatically falls back to the built-in engine if binaries are not bundled or for variants (KoTH/3-Check).
 */
class UciEngineBridge(private val context: Context) {

    fun isNativeEngineAvailable(engine: AiEngineType): Boolean {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return false
        val bin = File(nativeDir, engine.binaryName)
        return bin.exists() && bin.canExecute()
    }

    private fun extractAssetIfExists(assetRelPath: String, outName: String): File? {
        val outFile = File(context.filesDir, outName)
        if (outFile.exists() && outFile.length() > 0L) return outFile
        return runCatching {
            context.assets.open(assetRelPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile
        }.getOrNull()
    }

    suspend fun selectBestMove(
        engine: AiEngineType,
        board: List<List<ChessPiece?>>,
        castling: CastlingRights,
        enPassant: Square?,
        castlingConfig: CastlingConfig,
        variant: ChessVariant,
        halfMoveClock: Int,
        fullMoveNumber: Int,
        checksB: Int,
        checksW: Int,
        aiElo: Int
    ): EngineMoveResult? = withContext(Dispatchers.IO) {
        // Native UCI engines (Stockfish / Lc0) support Standard and Chess960; KoTH & 3-Check use built-in variant engine
        val supportsVariant = variant in setOf(ChessVariant.STANDARD, ChessVariant.CHESS960, ChessVariant.KOTH, ChessVariant.THREE_CHECK)
        if (supportsVariant && isNativeEngineAvailable(engine)) {
            val fen = ChessEngine.toFen(board, PieceColor.BLACK, castling, enPassant, halfMoveClock, fullMoveNumber)
            val moveTimeMs = (100 + ((aiElo - 400).coerceAtLeast(0) * 1100 / 2400)).coerceIn(100, 1200)
            val uciMove = queryNativeUci(engine, fen, moveTimeMs, variant, aiElo)
            if (uciMove != null) {
                val parsed = ChessEngine.parseUciMove(board, uciMove, castling, enPassant, castlingConfig)
                if (parsed != null) {
                    return@withContext EngineMoveResult(parsed.first, parsed.second, "${engine.label} (Native UCI)")
                }
            }
        }

        // Fallback to built-in engine (with engine-style heuristics)
        val fallbackMove = withContext(Dispatchers.Default) {
            ChessEngine.pickAiMove(board, castling, enPassant, castlingConfig, variant, checksB, checksW, aiElo, engine)
        } ?: return@withContext null

        EngineMoveResult(fallbackMove, PieceType.QUEEN, "${engine.label} (Built-in Fallback)")
    }

    private fun queryNativeUci(
        engine: AiEngineType,
        fen: String,
        moveTimeMs: Int,
        variant: ChessVariant,
        aiElo: Int
    ): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        val bin = File(nativeDir, engine.binaryName)
        if (!bin.exists()) return null

        var proc: Process? = null
        return runCatching {
            proc = ProcessBuilder(bin.absolutePath).redirectErrorStream(true).start()
            val writer = BufferedWriter(OutputStreamWriter(proc!!.outputStream))
            val reader = BufferedReader(InputStreamReader(proc!!.inputStream))

            fun send(cmd: String) {
                writer.write(cmd)
                writer.newLine()
                writer.flush()
            }

            send("uci")
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line == "uciok") break
            }

            var supportsUciVariant = false
            // Check if native binary advertises UCI_Variant (e.g. Fairy-Stockfish)
            if (variant == ChessVariant.CHESS960) send("setoption name UCI_Chess960 value true")
            else if (variant == ChessVariant.KOTH) {
                send("setoption name UCI_Variant value kingofthehill")
                supportsUciVariant = true
            } else if (variant == ChessVariant.THREE_CHECK) {
                send("setoption name UCI_Variant value 3check")
                supportsUciVariant = true
            }
            if (engine == AiEngineType.LEELA) {
                extractAssetIfExists("weights/lc0-weights.pb.gz", "lc0-weights.pb.gz")?.let { wFile ->
                    send("setoption name WeightsFile value ${wFile.absolutePath}")
                }
            } else if (engine == AiEngineType.STOCKFISH) {
                if (aiElo >= 1320) {
                    send("setoption name UCI_LimitStrength value true")
                    send("setoption name UCI_Elo value ${aiElo.coerceIn(1320, 2800)}")
                } else {
                    val skill = (((aiElo - 400).coerceAtLeast(0) * 8) / 920).coerceIn(0, 8)
                    send("setoption name UCI_LimitStrength value false")
                    send("setoption name Skill Level value $skill")
                }
            }

            send("isready")
            while (reader.readLine().also { line = it } != null) {
                if (line == "readyok") break
            }

            send("position fen $fen")
            send("go movetime $moveTimeMs")

            var bestMove: String? = null
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.startsWith("bestmove ")) {
                    bestMove = l.split(" ").getOrNull(1)
                    break
                }
            }
            send("quit")
            bestMove
        }.getOrNull().also {
            proc?.destroy()
        }
    }
}
