package io.github.thomasvanriel.solitaire.extras

import java.time.DayOfWeek
import java.time.LocalDateTime

data class GreetingFacts(
    val now: LocalDateTime,
    /** No game has ever been played. */
    val firstGameEver: Boolean,
    /** Days since a game was last played, or null if unknown. */
    val daysSinceLastPlayed: Long?,
    /** The current game was played but not won, so a new one ends it. */
    val leavingUnfinished: Boolean,
    /** Current win streak in the last game type played. */
    val streak: Int,
)

/** The greeting at the top of the New game dialog. */
object Greetings {
    /** The same within one hour, so it doesn't change while the dialog is open. */
    fun pick(f: GreetingFacts): String {
        if (f.firstGameEver) return "Hello, and welcome to the table."
        holiday(f.now)?.let { return it }
        val days = f.daysSinceLastPlayed
        if (days != null && days >= 7) return "Welcome back. The cards missed you."
        val p = Picker(mix(f.now.toLocalDate().toEpochDay(), f.now.hour.toLong(), 0x6EL))
        if (f.leavingUnfinished && p.chance(0.5)) return "Fresh cards, fresh start."
        if (f.streak >= 3 && p.chance(0.5)) return "Streak of ${f.streak}. No pressure."
        val h = f.now.hour
        if (f.now.dayOfWeek == DayOfWeek.MONDAY && h in 5..11) return "Monday again. At least the cards are kind."
        if (f.now.dayOfWeek == DayOfWeek.FRIDAY && h in 17..23) return "Weekend mode: on."
        return p.one(timeOfDay(h), null)
    }

    internal fun holiday(now: LocalDateTime): String? {
        val m = now.monthValue
        val d = now.dayOfMonth
        return when {
            m == 1 && d == 1 -> "Happy New Year!"
            d == 13 && now.dayOfWeek == DayOfWeek.FRIDAY -> "Friday the 13th. Deal carefully."
            m == 2 && d == 14 -> "Happy Valentine's Day. Deal yourself some hearts."
            m == 10 && d == 31 -> "Spooky deals tonight."
            m == 12 && d in 24..26 -> "Merry dealing!"
            else -> null
        }
    }

    private fun timeOfDay(hour: Int): List<String> = when (hour) {
        in 5..8 -> listOf("Good morning. The cards are already awake.", "Early start? The deck approves.")
        in 9..11 -> listOf("Good morning. Fancy a deal?", "A mid-morning game. Very civilised.")
        in 12..13 -> listOf("Lunchtime deal?", "Cards and a sandwich. Perfect.")
        in 14..17 -> listOf("Good afternoon. Time for a quick one.", "The afternoon slump, solved.")
        in 18..21 -> listOf("Good evening. Pick your poison.", "Evening game? Lovely.")
        in 22..23 -> listOf("One before bed?", "The night is young, and so is this deal.")
        else -> listOf("Still up? The cards are too.", "Midnight patience. Very mysterious.")
    }
}
