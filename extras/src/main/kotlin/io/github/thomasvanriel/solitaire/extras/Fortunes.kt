package io.github.thomasvanriel.solitaire.extras

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Everything about a win that a fortune may mention. Counts include this win. */
data class WinFacts(
    val ruleSet: RuleSet,
    val tier: Tier,
    val seed: Long,
    val seconds: Long,
    val moves: Int,
    /** Moves in the deal's stored winning sequence. */
    val solutionMoves: Int,
    val usedUndo: Boolean,
    val hints: Int,
    /** Times the player restarted this deal. */
    val restarts: Int,
    val streak: Int,
    /** The streak that ended just before this one began (0 if none was broken). */
    val lostStreak: Int = 0,
    val newBestTime: Boolean = false,
    val newFewestMoves: Boolean = false,
    val firstWinEver: Boolean = false,
    val firstWinInRuleSet: Boolean = false,
    val firstWinInTier: Boolean = false,
    val totalWins: Int = 0,
    val totalPlayed: Int = 0,
    val now: LocalDateTime,
)

/** The one-line fortune on the win screen. */
object Fortunes {
    /**
     * Notable moments (first wins, records, streaks, holidays) always get their line. Otherwise a line about
     * the game or the time of day, sometimes, or a random one picked from the deal number, so friends who
     * share a deal code see the same fortune. Never [previous] when another line is possible.
     */
    fun pick(f: WinFacts, previous: String? = null): String {
        notable(f).firstOrNull { it != previous }?.let { return it }
        Calendar.winLine(f.now)?.takeIf { it != previous }?.let { return it }
        val p = Picker(mix(f.seed, f.ruleSet.id.toLong()))
        val common = common(f)
        if (common.isNotEmpty() && p.chance(0.35)) p.fresh(common, previous)?.let { return it }
        return p.one(random(f), previous)
    }

    private val MILESTONES = listOf(10, 50, 100, 250, 500, 1000, 2000, 5000)

    internal fun notable(f: WinFacts): List<String> = buildList {
        if (f.firstWinEver) add("Your first win. The first of many.")
        if (f.totalWins in MILESTONES) add("Win number ${f.totalWins}. Someone get this person a cake.")
        if (f.solutionMoves > 0 && f.moves < f.solutionMoves) {
            val n = f.solutionMoves - f.moves
            add("You beat the solver by $n ${if (n == 1) "move" else "moves"}. It's sulking.")
        }
        if (f.firstWinInRuleSet) add("First ${f.ruleSet.label} win. Welcome to the club.")
        when (f.streak) {
            25 -> add("Twenty-five in a row. The cards fear you now.")
            10 -> add("Ten in a row. Statistically suspicious.")
            5 -> add("Five in a row. You are on fire. Not literally, we hope.")
            3 -> add("Three in a row. Something is happening.")
        }
        if (f.newBestTime) add("New record: ${formatTime(f.seconds)}. The cards barely had time to move.")
        if (f.newFewestMoves) add("Fewest moves ever: ${f.moves}. Efficient.")
        if (f.tier == Tier.EXPERT && !f.usedUndo) add("Expert, no undo. Show-off.")
        if (f.lostStreak > 0 && f.streak == 1) add("The streak is dead. Long live the streak.")
        if (f.firstWinInTier && f.tier != Tier.EASY) add("First ${f.tier.label} win. Moving up in the world.")
        if (f.solutionMoves > 0 && f.moves == f.solutionMoves) add("Exactly as many moves as the solver. Are you the solver?")
        if (prettyNumber(f.seed)) add("Deal #${f.seed}. What a lovely number.")
    }

    internal fun common(f: WinFacts): List<String> = buildList {
        if (f.seconds in 1 until 120) add("Did you even look at the cards?")
        if (f.seconds > 2 * 3600) add("You've been playing this one longer than some films.")
        else if (f.seconds > 30 * 60) add("That took ${formatTime(f.seconds)}. Slow and steady wins the deal.")
        if (f.solutionMoves > 0 && f.moves > 2 * f.solutionMoves) add("${f.moves} moves. The scenic route.")
        if (f.usedUndo) add("Undo is just time travel for card people.") else add("No take-backs. Bold.")
        if (f.hints >= 5) add("The hint button would like a thank-you card.")
        else if (f.hints > 0) add("A little help from a friend. That's what friends are for.")
        if (f.tier == Tier.EXPERT) add("The test player lost this one 30 times out of 30. You didn't.")
        if (f.tier == Tier.EASY) add("A gentle one. Nothing wrong with that.")
        if (f.restarts > 0) add("Same cards, new you. It worked.")
        if (f.totalPlayed >= 20 && f.totalWins * 10 > f.totalPlayed * 9) {
            add("${f.totalWins * 100 / f.totalPlayed}% win rate. Are you okay?")
        }
        addAll(Calendar.winTimeLines(f.now))
        if (f.ruleSet == RuleSet.DRAW_3_UNLIMITED) add("Draw 3? On purpose? Respect.")
        if (f.ruleSet == RuleSet.SPIDER_4_SUITS) add("Four suits? You absolute legend.")
    }

    internal fun random(f: WinFacts): List<String> = GENERAL + luckyNumbers(f.seed) + when (f.ruleSet.game) {
        GameType.KLONDIKE -> KLONDIKE
        GameType.SPIDER -> SPIDER
        GameType.SCORPION -> SCORPION
    }

    private fun luckyNumbers(seed: Long): String {
        val p = Picker(mix(seed, 0x1C4BL))
        val numbers = generateSequence { p.int(1, 50) }.distinct().take(3).sorted().joinToString(", ")
        return "Your lucky numbers: $numbers. Deal #$seed thanks you."
    }

    /** 777, 1111, 1234, 4321, 1000 and the like. */
    internal fun prettyNumber(n: Long): Boolean {
        val s = n.toString()
        if (s.length < 3) return false
        if (s.all { it == s[0] }) return true
        if (s.drop(1).all { it == '0' }) return true
        val steps = s.zipWithNext { a, b -> b - a }.distinct()
        return steps == listOf(1) || steps == listOf(-1)
    }

    private val GENERAL = listOf(
        "You will soon find a red seven where you need one.",
        "A face-down card is just a friend you haven't met.",
        "Patience is a virtue. You have demonstrated it.",
        "Good things come to those who stack.",
        "The cards were on your side. Eventually.",
        "A King in an empty column is worth two in the stock.",
        "You will meet a tall, dark Jack of Spades.",
        "Your future holds many more Aces.",
        "Fortune favours the one who turns over cards.",
        "Every pile you build is a small act of order in a messy world.",
        "Today's lucky suit: all of them.",
        "A great sorting has occurred. The cards thank you.",
        "The deck has been put back in order. It feels lighter.",
        "Somewhere, a Queen of Hearts is smiling.",
        "You can't always get what you want. But today you got every card home.",
        "It's not luck if the deal was winnable all along.",
        "The best time to plant a tree was 20 years ago. The best time to move a King was just now.",
        "The journey of a thousand cards begins with a single Ace.",
        "He who hesitates is still allowed to undo.",
        "Order restored. Universe slightly tidier.",
        "You are the reason the Ace left the tableau.",
        "Expect a pleasant surprise. Possibly a Seven.",
        "The stars say: play another one.",
        "Someone, somewhere, is still stuck on their deal. Not you.",
        "You have the patience of a saint. Or at least of a solitaire player.",
        "This deal was proven winnable. You have now proven it twice.",
        "The solver knew you could do it. It's a computer, but still.",
        "Even the face-down cards are impressed.",
        "You did not shuffle these. You un-shuffled them.",
        "A messy table, made neat. Your kitchen is next.",
        "The Kings have their kingdoms back.",
        "This victory is 100% organic and locally sourced.",
        "No cards were harmed in the making of this win.",
        "Your ancestors also played patience. Probably.",
        "Big things are coming. Like another deal.",
        "You and this deck are now on speaking terms.",
        "The Aces would like to thank their agent: you.",
        "One small step for cards, one giant leap for you.",
        "Remember this feeling the next time you're stuck.",
        "A clear table is a clear mind.",
        "The cards are going home now. Wave goodbye.",
        "Somewhere a Two of Clubs finally feels seen.",
        "Fortune cookie says: you will win again. Fortune cookie is usually right.",
        "Help, I'm trapped in a solitaire app. (Just kidding. Well done.)",
    )

    private val KLONDIKE = listOf(
        "Every red Queen has found her black King.",
        "The stock is empty, and so are your worries.",
        "Seven columns, zero problems.",
    )

    private val SPIDER = listOf(
        "Eight runs, King to Ace. The spider is pleased.",
        "You untangled the web.",
        "No spiders were harmed. One was mildly impressed.",
        "Two decks, one champion.",
    )

    private val SCORPION = listOf(
        "The scorpion has been calmed.",
        "You pulled the sting out of that one.",
        "The three reserve cards never stood a chance.",
    )
}

/** Lines that depend on the time of day and the date. */
internal object Calendar {
    /** A holiday line for a win, or null on an ordinary day. */
    fun winLine(now: LocalDateTime): String? {
        val m = now.monthValue
        val d = now.dayOfMonth
        return when {
            d == 13 && now.dayOfWeek == DayOfWeek.FRIDAY -> "A win on Friday the 13th. Who's unlucky now?"
            m == 1 && d == 1 -> "First wins of the year are the best wins."
            m == 2 && d == 14 -> "You stacked the hearts. How romantic."
            m == 3 && d == 14 -> "Happy Pi day. 3.14 cheers for you."
            m == 4 && d == 1 -> "This message is not an April Fools' joke. You actually won."
            m == 10 && d == 31 -> "A spooky win. The face-down cards were the scariest part."
            m == 12 && d in 24..26 -> "A festive win. The Kings are wearing tiny hats."
            m == 12 && d == 31 -> "Last wins of the year. Go out on a high."
            else -> null
        }
    }

    fun winTimeLines(now: LocalDateTime): List<String> = buildList {
        when (now.hour) {
            in 0..2 -> add("A midnight win. The cards don't sleep either.")
            in 3..4 -> add("Shouldn't you be asleep? (Won anyway.)")
            in 5..7 -> add("An early win. The coffee hasn't even kicked in.")
            in 12..13 -> add("A lunch-break win. Don't tell your boss.")
            in 22..23 -> add("One more before bed? You know it's never one more.")
        }
        when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> add("You beat Monday. That's half the battle.")
            DayOfWeek.FRIDAY -> add("Winning into the weekend.")
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> add("A weekend win. The best kind.")
            else -> {}
        }
    }

    fun lateNight(now: LocalDateTime): Boolean = now.hour in 0..4
}
