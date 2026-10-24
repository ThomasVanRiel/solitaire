package io.github.thomasvanriel.solitaire.game

import android.content.Context
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/** Statistics for one draw mode and difficulty. Times are in seconds; -1 means "no win yet". */
data class TierStats(
    val played: Int = 0,
    val won: Int = 0,
    val wonWithoutUndo: Int = 0,
    val bestTime: Long = -1,
    val fewestMoves: Int = -1,
) {
    val winRate: Double get() = if (played == 0) 0.0 else won.toDouble() / played
}

/** Statistics for one draw mode: per difficulty, plus win streaks across all difficulties. */
data class ModeStats(
    val tiers: Map<Tier, TierStats> = Tier.entries.associateWith { TierStats() },
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
) {
    val played: Int get() = tiers.values.sumOf { it.played }
    val won: Int get() = tiers.values.sumOf { it.won }
    val wonWithoutUndo: Int get() = tiers.values.sumOf { it.wonWithoutUndo }
    val winRate: Double get() = if (played == 0) 0.0 else won.toDouble() / played
}

/** What a win changed, for the win screen. */
data class WinRecord(val streak: Int, val newBestTime: Boolean, val newFewestMoves: Boolean)

/**
 * Statistics, kept on the device only (SharedPreferences). Nothing is ever sent anywhere.
 * The preferences file is opened lazily: call [load] from a background thread first.
 */
class StatsStore(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("stats", Context.MODE_PRIVATE) }

    private fun key(name: String, rs: RuleSet, tier: Tier) = "${name}_${rs.id}_${tier.id}"
    private fun key(name: String, rs: RuleSet) = "${name}_${rs.id}"

    fun load(rs: RuleSet): ModeStats = ModeStats(
        tiers = Tier.entries.associateWith { t ->
            TierStats(
                played = prefs.getInt(key("played", rs, t), 0),
                won = prefs.getInt(key("won", rs, t), 0),
                wonWithoutUndo = prefs.getInt(key("noUndo", rs, t), 0),
                bestTime = prefs.getLong(key("bestTime", rs, t), -1),
                fewestMoves = prefs.getInt(key("fewestMoves", rs, t), -1),
            )
        },
        currentStreak = prefs.getInt(key("streak", rs), 0),
        bestStreak = prefs.getInt(key("bestStreak", rs), 0),
    )

    fun loadAll(): Map<RuleSet, ModeStats> = RuleSet.ALL.associateWith { load(it) }

    /** A game counts as played from its first move. */
    fun recordPlayed(rs: RuleSet, tier: Tier) {
        val k = key("played", rs, tier)
        prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply()
    }

    /** A played game that was abandoned (New game before winning) breaks the streak. */
    fun recordLoss(rs: RuleSet) {
        prefs.edit().putInt(key("streak", rs), 0).apply()
    }

    fun recordWin(rs: RuleSet, tier: Tier, seconds: Long, moves: Int, usedUndo: Boolean): WinRecord {
        val t = load(rs).tiers.getValue(tier)
        val streak = prefs.getInt(key("streak", rs), 0) + 1
        val newBestTime = t.bestTime < 0 || seconds < t.bestTime
        val newFewest = t.fewestMoves < 0 || moves < t.fewestMoves
        prefs.edit().apply {
            putInt(key("won", rs, tier), t.won + 1)
            if (!usedUndo) putInt(key("noUndo", rs, tier), t.wonWithoutUndo + 1)
            if (newBestTime) putLong(key("bestTime", rs, tier), seconds)
            if (newFewest) putInt(key("fewestMoves", rs, tier), moves)
            putInt(key("streak", rs), streak)
            putInt(key("bestStreak", rs), maxOf(streak, prefs.getInt(key("bestStreak", rs), 0)))
        }.apply()
        // The first win in a tier sets both records, but that isn't news worth announcing.
        val first = t.won == 0
        return WinRecord(streak, newBestTime && !first, newFewest && !first)
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
