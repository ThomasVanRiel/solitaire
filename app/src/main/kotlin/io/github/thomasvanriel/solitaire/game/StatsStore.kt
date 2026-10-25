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
    /** Hints shown, in all games. */
    val hints: Int = 0,
    /** Wins in games where a hint was used. */
    val wonWithHints: Int = 0,
    /** Wins added to the totals below (wins from before version 0.6.2 aren't, so they don't skew the averages). */
    val totalledWins: Int = 0,
    val totalWinSeconds: Long = 0,
    val totalWinMoves: Long = 0,
    val totalWinFailedMoves: Long = 0,
    val totalWinUndos: Long = 0,
    /** Clock time of every game, added when it is won, abandoned or restarted. */
    val playSeconds: Long = 0,
    val undos: Int = 0,
    val restarts: Int = 0,
    /** Times a game reached a position that can't be won any more. */
    val stuck: Int = 0,
) {
    val wonWithoutHints: Int get() = won - wonWithHints
    val winRate: Double get() = if (played == 0) 0.0 else won.toDouble() / played

    /** Averages over the wins in [totalledWins], or null with none yet. */
    val averages: WinAverages?
        get() = averageOf(totalledWins, totalWinSeconds, totalWinMoves, totalWinFailedMoves, totalWinUndos)
}

data class WinAverages(val seconds: Double, val moves: Double, val failedMoves: Double, val undos: Double)

private fun averageOf(wins: Int, seconds: Long, moves: Long, failed: Long, undos: Long): WinAverages? {
    if (wins == 0) return null
    val n = wins.toDouble()
    return WinAverages(seconds / n, moves / n, failed / n, undos / n)
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
    val hints: Int get() = tiers.values.sumOf { it.hints }
    val wonWithoutHints: Int get() = tiers.values.sumOf { it.wonWithoutHints }
    val playSeconds: Long get() = tiers.values.sumOf { it.playSeconds }
    val undos: Int get() = tiers.values.sumOf { it.undos }
    val restarts: Int get() = tiers.values.sumOf { it.restarts }
    val stuck: Int get() = tiers.values.sumOf { it.stuck }
    val winRate: Double get() = if (played == 0) 0.0 else won.toDouble() / played

    /** Averages over the wins at every difficulty of this game. */
    val averages: WinAverages?
        get() = averageOf(
            tiers.values.sumOf { it.totalledWins },
            tiers.values.sumOf { it.totalWinSeconds },
            tiers.values.sumOf { it.totalWinMoves },
            tiers.values.sumOf { it.totalWinFailedMoves },
            tiers.values.sumOf { it.totalWinUndos },
        )
}

/**
 * Every game added up, per difficulty. Times and move counts can't be compared across games, so they are
 * left out (-1); the best streak is the best of any one game, and there is no current streak.
 */
fun combinedStats(all: Collection<ModeStats>): ModeStats = ModeStats(
    tiers = Tier.entries.associateWith { t ->
        val ts = all.map { it.tiers[t] ?: TierStats() }
        TierStats(
            played = ts.sumOf { it.played },
            won = ts.sumOf { it.won },
            wonWithoutUndo = ts.sumOf { it.wonWithoutUndo },
            hints = ts.sumOf { it.hints },
            wonWithHints = ts.sumOf { it.wonWithHints },
            playSeconds = ts.sumOf { it.playSeconds },
            undos = ts.sumOf { it.undos },
            restarts = ts.sumOf { it.restarts },
            stuck = ts.sumOf { it.stuck },
        )
    },
    bestStreak = all.maxOfOrNull { it.bestStreak } ?: 0,
)

/** Days played, over every game. */
data class DayStats(val daysPlayed: Int = 0, val currentStreak: Int = 0, val bestStreak: Int = 0)

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
                hints = prefs.getInt(key("hints", rs, t), 0),
                wonWithHints = prefs.getInt(key("hintWins", rs, t), 0),
                totalledWins = prefs.getInt(key("totalledWins", rs, t), 0),
                totalWinSeconds = prefs.getLong(key("winSeconds", rs, t), 0),
                totalWinMoves = prefs.getLong(key("winMoves", rs, t), 0),
                totalWinFailedMoves = prefs.getLong(key("winFailed", rs, t), 0),
                totalWinUndos = prefs.getLong(key("winUndos", rs, t), 0),
                playSeconds = prefs.getLong(key("playSeconds", rs, t), 0),
                undos = prefs.getInt(key("undos", rs, t), 0),
                restarts = prefs.getInt(key("restarts", rs, t), 0),
                stuck = prefs.getInt(key("stuck", rs, t), 0),
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

    fun recordHint(rs: RuleSet, tier: Tier) = increment("hints", rs, tier)
    fun recordUndo(rs: RuleSet, tier: Tier) = increment("undos", rs, tier)
    fun recordRestart(rs: RuleSet, tier: Tier) = increment("restarts", rs, tier)
    fun recordStuck(rs: RuleSet, tier: Tier) = increment("stuck", rs, tier)

    /** Adds a game's clock time to the time played (when it ends or restarts, so no second counts twice). */
    fun addPlayTime(rs: RuleSet, tier: Tier, seconds: Long) {
        if (seconds <= 0) return
        val k = key("playSeconds", rs, tier)
        prefs.edit().putLong(k, prefs.getLong(k, 0) + seconds).apply()
    }

    private fun increment(name: String, rs: RuleSet, tier: Tier) {
        val k = key(name, rs, tier)
        prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply()
    }

    /** A played game that was abandoned (New game before winning) breaks the streak. */
    fun recordLoss(rs: RuleSet) {
        prefs.edit().putInt(key("streak", rs), 0).apply()
    }

    fun recordWin(
        rs: RuleSet, tier: Tier, seconds: Long, moves: Int, failedMoves: Int, undos: Int, usedUndo: Boolean,
        usedHint: Boolean,
    ): WinRecord {
        val t = load(rs).tiers.getValue(tier)
        val streak = prefs.getInt(key("streak", rs), 0) + 1
        val newBestTime = t.bestTime < 0 || seconds < t.bestTime
        val newFewest = t.fewestMoves < 0 || moves < t.fewestMoves
        prefs.edit().apply {
            putInt(key("won", rs, tier), t.won + 1)
            if (!usedUndo) putInt(key("noUndo", rs, tier), t.wonWithoutUndo + 1)
            if (usedHint) putInt(key("hintWins", rs, tier), t.wonWithHints + 1)
            if (newBestTime) putLong(key("bestTime", rs, tier), seconds)
            if (newFewest) putInt(key("fewestMoves", rs, tier), moves)
            putInt(key("totalledWins", rs, tier), t.totalledWins + 1)
            putLong(key("winSeconds", rs, tier), t.totalWinSeconds + seconds)
            putLong(key("winMoves", rs, tier), t.totalWinMoves + moves)
            putLong(key("winFailed", rs, tier), t.totalWinFailedMoves + failedMoves)
            putLong(key("winUndos", rs, tier), t.totalWinUndos + undos)
            putLong(key("playSeconds", rs, tier), t.playSeconds + seconds)
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
