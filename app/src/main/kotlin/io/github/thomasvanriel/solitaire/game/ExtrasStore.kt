package io.github.thomasvanriel.solitaire.game

import android.content.Context
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.extras.Achievements
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the fun bits need to remember: unlocked achievements, a few extra counters, and the last line of
 * each kind (so it isn't shown twice in a row). On the device only, like the statistics.
 * Opened lazily: touch it from a background thread first ([warmUp]).
 */
class ExtrasStore(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("extras", Context.MODE_PRIVATE) }

    fun warmUp() {
        prefs.contains("x")
    }

    val unlocked: Set<String> get() = prefs.getStringSet("unlocked", emptySet()).orEmpty().toSet()

    fun unlock(ids: Collection<String>) {
        prefs.edit().putStringSet("unlocked", unlocked + ids).apply()
    }

    val weekendWins: Int get() = prefs.getInt("weekendWins", 0)
    val dayStreak: Int get() = prefs.getInt("dayStreak", 0)

    /** Days on which a game was started. Before version 0.6.2 this wasn't kept, so it starts from the day streak. */
    val daysPlayed: Int get() = prefs.getInt("daysPlayed", dayStreak)
    val bestDayStreak: Int get() = prefs.getInt("bestDayStreak", dayStreak)

    /** The run of days played that is still going (played today or yesterday), else 0. */
    fun currentDayStreak(today: LocalDate): Int {
        val since = daysSinceLastPlayed(today) ?: return 0
        return if (since <= 1) dayStreak else 0
    }
    val abandonedInARow: Int get() = prefs.getInt("abandonedInARow", 0)

    /** Days since a game was last played, or null if none ever was. */
    fun daysSinceLastPlayed(today: LocalDate): Long? =
        if (prefs.contains("lastPlayedDay")) today.toEpochDay() - prefs.getLong("lastPlayedDay", 0) else null

    /** A game was started (its first move): keeps the run of days played. */
    fun recordPlayed(today: LocalDate) {
        val day = today.toEpochDay()
        val last = if (prefs.contains("lastPlayedDay")) prefs.getLong("lastPlayedDay", 0) else null
        val streak = when (last) {
            day -> maxOf(1, dayStreak)
            day - 1 -> dayStreak + 1
            else -> 1
        }
        prefs.edit()
            .apply { if (last != day) putInt("daysAway", if (last == null) 0 else (day - last - 1).toInt()) }
            .putLong("lastPlayedDay", day)
            .putInt("dayStreak", streak)
            .putInt("daysPlayed", if (last == day) daysPlayed else daysPlayed + 1)
            .putInt("bestDayStreak", maxOf(bestDayStreak, streak))
            .apply()
    }

    /** Days without a game before today's first one (0 if none today, or none before it). */
    fun daysAway(today: LocalDate): Int =
        if (prefs.getLong("lastPlayedDay", Long.MIN_VALUE) == today.toEpochDay()) prefs.getInt("daysAway", 0) else 0

    /** Whether deal [seed] of [rs] was won before (kept since version 1.2.2). */
    fun dealWon(rs: RuleSet, seed: Long): Boolean = dealKey(rs, seed) in wonDeals

    private val wonDeals: Set<String> get() = prefs.getStringSet("wonDeals", emptySet()).orEmpty()

    private fun dealKey(rs: RuleSet, seed: Long) = "${rs.id}:$seed"

    private fun isToday(today: LocalDate) = prefs.getLong("winDay", Long.MIN_VALUE) == today.toEpochDay()

    fun winsToday(today: LocalDate): Int = if (isToday(today)) prefs.getInt("winsToday", 0) else 0

    /** Game types won today, by name. */
    private fun typesWonToday(today: LocalDate): Set<String> =
        if (isToday(today)) prefs.getStringSet("typesWonToday", emptySet()).orEmpty() else emptySet()

    fun gameTypesWonToday(today: LocalDate): Int = typesWonToday(today).size

    fun recordWin(rs: RuleSet, seed: Long, now: LocalDateTime) {
        val today = now.toLocalDate()
        val winsToday = winsToday(today) + 1
        val types = typesWonToday(today) + rs.game.name
        prefs.edit().apply {
            if (Achievements.isWeekend(now.dayOfWeek)) putInt("weekendWins", weekendWins + 1)
            putStringSet("wonDeals", wonDeals + dealKey(rs, seed))
            putLong("winDay", today.toEpochDay())
            putInt("winsToday", winsToday)
            putStringSet("typesWonToday", types)
            putInt("abandonedInARow", 0)
            remove(lostStreakKey(rs))
        }.apply()
    }

    /** A played game was left unwon; [lostStreak] is the win streak that ended with it. */
    fun recordAbandoned(rs: RuleSet, lostStreak: Int) {
        prefs.edit().apply {
            putInt("abandonedInARow", abandonedInARow + 1)
            if (lostStreak > 0) putInt(lostStreakKey(rs), lostStreak)
        }.apply()
    }

    /** The streak that ended before the current one in [rs] began (0 if none). */
    fun lostStreak(rs: RuleSet): Int = prefs.getInt(lostStreakKey(rs), 0)

    private fun lostStreakKey(rs: RuleSet) = "lostStreak_${rs.id}"

    fun lastLine(kind: String): String? = prefs.getString("last_$kind", null)

    fun setLastLine(kind: String, line: String) {
        prefs.edit().putString("last_$kind", line).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
