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
        val p = Picker(mix(f.now.toLocalDate().toEpochDay(), f.now.hour.toLong(), 0x6EL))
        val days = f.daysSinceLastPlayed
        if (days != null && days >= 30) return "It's been a while. The cards have been practising."
        if (days != null && days >= 7) return "Welcome back. The cards missed you."
        if (days != null && days >= 2 && p.chance(0.5)) return p.one(RETURNING, null)
        if (f.leavingUnfinished && p.chance(0.5)) return p.one(FRESH_START, null)
        if (f.streak >= 3 && p.chance(0.5)) return p.one(streak(f.streak), null)
        val h = f.now.hour
        if (f.now.dayOfWeek == DayOfWeek.MONDAY && h in 5..11) return "Monday again. At least the cards are kind."
        if (f.now.dayOfWeek == DayOfWeek.FRIDAY && h in 17..23) return "Weekend mode: on."
        day(f.now)?.takeIf { p.chance(0.25) }?.let { return it }
        return p.one(timeOfDay(h), null)
    }

    internal fun holiday(now: LocalDateTime): String? {
        val m = now.monthValue
        val d = now.dayOfMonth
        return when {
            m == 1 && d == 1 -> "Happy New Year!"
            d == 13 && now.dayOfWeek == DayOfWeek.FRIDAY -> "Friday the 13th. Deal carefully."
            m == 2 && d == 14 -> "Happy Valentine's Day. Deal yourself some hearts."
            m == 2 && d == 29 -> "Happy leap day. A bonus day for bonus deals."
            m == 3 && d == 14 -> "Happy Pi day. Deal yourself 3.14 games."
            m == 4 && d == 1 -> "Today's deals are entirely genuine. Honest."
            m == 5 && d == 4 -> "May the fours be with you."
            m == 6 && d == 21 -> "Happy midsummer. A long day, plenty of deals."
            m == 9 && d == 19 -> "Ahoy! Deal us in, matey."
            m == 10 && d == 31 -> "Spooky deals tonight."
            m == 12 && d == 21 -> "The shortest day. Plenty of dark for cards."
            m == 12 && d in 24..26 -> "Merry dealing!"
            m == 12 && d == 31 -> "Last deals of the year. Make them count."
            else -> null
        }
    }

    /** A line for the day of the week, or null. */
    internal fun day(now: LocalDateTime): String? = when (now.dayOfWeek) {
        DayOfWeek.TUESDAY -> "Tuesday. A perfectly good day for a deal."
        DayOfWeek.WEDNESDAY -> "Midweek deal? You've earned it."
        DayOfWeek.THURSDAY -> "Thursday. The weekend can almost see you."
        DayOfWeek.SATURDAY -> "Saturday. No alarms, just cards."
        DayOfWeek.SUNDAY -> "A Sunday deal. No rush."
        else -> null
    }

    private val RETURNING = listOf(
        "Nice to see you again.",
        "Back for more? Excellent.",
        "The cards kept your seat warm.",
        "Welcome back. New cards, same table.",
    )

    private val FRESH_START = listOf(
        "Fresh cards, fresh start.",
        "That last one never happened.",
        "New deal, clean slate.",
        "Let's forget the last one, shall we?",
    )

    private fun streak(n: Int) = listOf(
        "Streak of $n. No pressure.",
        "$n in a row. Don't jinx it.",
        "Streak: $n. The cards are nervous.",
    )

    internal fun timeOfDay(hour: Int): List<String> = when (hour) {
        in 5..8 -> listOf(
            "Good morning. The cards are already awake.",
            "Early start? The deck approves.",
            "Morning. Coffee first, or cards first?",
            "Up with the larks. And the Kings.",
            "A fresh day and a fresh deal.",
        )
        in 9..11 -> listOf(
            "Good morning. Fancy a deal?",
            "A mid-morning game. Very civilised.",
            "Time for a little break. You deserve it.",
            "Elevenses with a side of patience.",
            "Morning, card player.",
        )
        in 12..13 -> listOf(
            "Lunchtime deal?",
            "Cards and a sandwich. Perfect.",
            "A lunch break well spent.",
            "One deal, then back to work. Sure.",
        )
        in 14..17 -> listOf(
            "Good afternoon. Time for a quick one.",
            "The afternoon slump, solved.",
            "Teatime is card time.",
            "An afternoon deal. Very sensible.",
            "Good afternoon. The cards have been waiting.",
        )
        in 18..21 -> listOf(
            "Good evening. Pick your poison.",
            "Evening game? Lovely.",
            "Feet up, cards out.",
            "Good evening. The table is ready.",
            "The day is done. The deal is not.",
        )
        in 22..23 -> listOf(
            "One before bed?",
            "The night is young, and so is this deal.",
            "Just one more. We know.",
            "A late deal. The cards won't tell.",
        )
        else -> listOf(
            "Still up? The cards are too.",
            "Midnight patience. Very mysterious.",
            "The witching hour. Deal wisely.",
            "Can't sleep? Neither can the deck.",
        )
    }
}
