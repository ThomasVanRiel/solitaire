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
        prefs.edit().putLong("lastPlayedDay", day).putInt("dayStreak", streak).apply()
    }

    fun recordWin(rs: RuleSet, now: LocalDateTime) {
        prefs.edit().apply {
            if (Achievements.isWeekend(now.dayOfWeek)) putInt("weekendWins", weekendWins + 1)
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
