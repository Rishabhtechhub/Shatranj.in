package `in`.shatranj.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlayerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("chess_arena_prefs", Context.MODE_PRIVATE)

    fun loadProfile(): PlayerProfile {
        var playerId = prefs.getString("player_id", null)
        if (playerId == null) {
            playerId = "CHESS-" + UUID.randomUUID().toString().take(6).uppercase()
            prefs.edit().putString("player_id", playerId).apply()
        }
        val displayName = prefs.getString("display_name", "Player") ?: "Player"
        val elo = prefs.getInt("elo", 1200)
        val peakElo = prefs.getInt("peak_elo", 1200)
        val engineName = prefs.getString("selected_engine", AiEngineType.LEELA.name) ?: AiEngineType.LEELA.name
        val selectedEngine = runCatching { AiEngineType.valueOf(engineName) }.getOrDefault(AiEngineType.LEELA)
        val recentModeId = prefs.getString("recent_mode_id", "blitz5") ?: "blitz5"
        val recentOppName = prefs.getString("recent_opp_mode", GameMode.AI.name) ?: GameMode.AI.name
        val recentOppMode = runCatching { GameMode.valueOf(recentOppName) }.getOrDefault(GameMode.AI)
        val themeName = prefs.getString("board_theme", BoardTheme.WOOD.name) ?: BoardTheme.WOOD.name
        val boardTheme = runCatching { BoardTheme.valueOf(themeName) }.getOrDefault(BoardTheme.WOOD)
        val soundEnabled = prefs.getBoolean("sound_enabled", true)

        val historyJson = prefs.getString("elo_history", "[1200]") ?: "[1200]"
        val eloHistory = mutableListOf<Int>()
        val hArr = JSONArray(historyJson)
        for (i in 0 until hArr.length()) eloHistory.add(hArr.getInt(i))
        if (eloHistory.isEmpty()) eloHistory.add(elo)

        val rankedStats = WldStats(
            wins = prefs.getInt("ranked_w", 0),
            losses = prefs.getInt("ranked_l", 0),
            draws = prefs.getInt("ranked_d", 0)
        )
        val aiStats = WldStats(
            wins = prefs.getInt("ai_w", 0),
            losses = prefs.getInt("ai_l", 0),
            draws = prefs.getInt("ai_d", 0)
        )

        val matchesJson = prefs.getString("matches", "[]") ?: "[]"
        val mArr = JSONArray(matchesJson)
        val matches = mutableListOf<MatchRecord>()
        for (i in 0 until mArr.length()) {
            val obj = mArr.getJSONObject(i)
            matches.add(
                MatchRecord(
                    matchId = obj.getString("matchId"),
                    mode = GameMode.valueOf(obj.getString("mode")),
                    modeLabel = obj.getString("modeLabel"),
                    opponentName = obj.getString("opponentName"),
                    result = obj.getString("result"),
                    reason = obj.getString("reason"),
                    eloDelta = obj.getInt("eloDelta"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }

        return PlayerProfile(playerId, displayName, elo, peakElo, eloHistory, rankedStats, aiStats, matches, selectedEngine, recentModeId, recentOppMode, boardTheme)
    }

    fun saveProfile(profile: PlayerProfile) {
        val hArr = JSONArray()
        profile.eloHistory.takeLast(30).forEach { hArr.put(it) }

        val mArr = JSONArray()
        profile.matches.take(40).forEach { m ->
            mArr.put(JSONObject().apply {
                put("matchId", m.matchId)
                put("mode", m.mode.name)
                put("modeLabel", m.modeLabel)
                put("opponentName", m.opponentName)
                put("result", m.result)
                put("reason", m.reason)
                put("eloDelta", m.eloDelta)
                put("timestamp", m.timestamp)
            })
        }

        prefs.edit()
            .putString("player_id", profile.playerId)
            .putString("display_name", profile.displayName)
            .putInt("elo", profile.elo)
            .putInt("peak_elo", profile.peakElo)
            .putString("selected_engine", profile.selectedEngine.name)
            .putString("recent_mode_id", profile.recentModeId)
            .putString("recent_opp_mode", profile.recentOppMode.name)
            .putString("board_theme", profile.boardTheme.name)
            .putBoolean("sound_enabled", profile.soundEnabled)
            .putString("elo_history", hArr.toString())
            .putInt("ranked_w", profile.rankedStats.wins)
            .putInt("ranked_l", profile.rankedStats.losses)
            .putInt("ranked_d", profile.rankedStats.draws)
            .putInt("ai_w", profile.aiStats.wins)
            .putInt("ai_l", profile.aiStats.losses)
            .putInt("ai_d", profile.aiStats.draws)
            .putString("matches", mArr.toString())
            .apply()
    }

    fun getLeaderboard(profile: PlayerProfile): List<LeaderboardEntry> {
        val simulated = listOf(
            LeaderboardEntry("CHESS-9A2F11", "GM_Aarav", 2420, 148, 190),
            LeaderboardEntry("CHESS-4C8E73", "TacticalMaya", 2140, 112, 165),
            LeaderboardEntry("CHESS-7B1D05", "KnightRider99", 1865, 89, 140),
            LeaderboardEntry("CHESS-2E6A44", "EndgameWizard", 1590, 64, 115),
            LeaderboardEntry("CHESS-5F0C88", "BlitzNova", 1360, 42, 85),
            LeaderboardEntry("CHESS-1D9B32", "RookiePawn", 1110, 18, 46)
        )
        val you = LeaderboardEntry(
            playerId = profile.playerId,
            name = profile.displayName,
            elo = profile.elo,
            wins = profile.rankedStats.wins,
            total = profile.rankedStats.total,
            isYou = true
        )
        return (simulated + you).sortedByDescending { it.elo }
    }
}
