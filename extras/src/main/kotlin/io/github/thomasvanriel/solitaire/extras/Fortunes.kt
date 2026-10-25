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
            100 -> add("A hundred in a row. Please teach us.")
            50 -> add("Fifty in a row. We're checking the deck for marked cards.")
            25 -> add("Twenty-five in a row. The cards fear you now.")
            10 -> add("Ten in a row. Statistically suspicious.")
            5 -> add("Five in a row. You are on fire. Not literally, we hope.")
            3 -> add("Three in a row. Something is happening.")
        }
        if (f.newBestTime) add("New record: ${formatTime(f.seconds)}. The cards barely had time to move.")
        if (f.newFewestMoves) add("Fewest moves ever: ${f.moves}. Efficient.")
        if (f.tier == Tier.EXPERT && !f.usedUndo) add("Expert, no undo. Show-off.")
        if (f.lostStreak >= 10 && f.streak == 1) add("Your streak of ${f.lostStreak} has been avenged. Sort of.")
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
        if (f.restarts >= 5) add("${f.restarts} restarts. Persistence pays.")
        if (!f.usedUndo && f.hints == 0) add("No undo, no hints. The purist's way.")
        if (f.tier == Tier.HARD) add("A Hard deal, handled.")
        if (f.tier == Tier.MEDIUM) add("Medium difficulty, maximum style.")
        if (f.seconds in 120 until 300) add("Under five minutes. Brisk.")
        if (f.streak == 2) add("Two in a row. Don't look at it, you'll jinx it.")
        if (f.totalPlayed >= 100) add("Game number ${f.totalPlayed}. You're a regular now.")
        if (prettyNumber(f.moves.toLong())) add("${f.moves} moves. A tidy number for a tidy win.")
        if (f.totalPlayed >= 20 && f.totalWins * 10 > f.totalPlayed * 9) {
            add("${f.totalWins * 100 / f.totalPlayed}% win rate. Are you okay?")
        }
        addAll(Calendar.winTimeLines(f.now))
        if (f.ruleSet == RuleSet.DRAW_3_UNLIMITED) add("Draw 3? On purpose? Respect.")
        if (f.ruleSet == RuleSet.SPIDER_4_SUITS) add("Four suits? You absolute legend.")
        if (f.ruleSet == RuleSet.RUSSIAN) add("Russian, in suit, all the way down. Respect.")
    }

    internal fun random(f: WinFacts): List<String> = GENERAL + luckyNumbers(f.seed) + when (f.ruleSet.game) {
        GameType.KLONDIKE -> KLONDIKE
        GameType.SPIDER -> SPIDER
        GameType.SCORPION -> SCORPION
        GameType.YUKON -> if (f.ruleSet.buildInSuit) RUSSIAN else YUKON
        GameType.FREECELL -> FREECELL
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
        "The cards have been returned to their rightful owners.",
        "You will soon be asked to play one more. Say yes.",
        "A tidy deck is the sign of a tidy mind.",
        "Your next deal will be kind. Probably.",
        "The Jacks are still talking about you.",
        "You turned chaos into four neat piles. Put that on your CV.",
        "Luck had nothing to do with it. Well, a little.",
        "Today you are the house, and the house always wins.",
        "The cards fell into place, with a little shove.",
        "Expect good news, in suits of four.",
        "Every Ace you freed will remember your name.",
        "Some people collect stamps. You collect wins.",
        "You have earned a biscuit. We can't give you one, but you've earned it.",
        "Sit back. Stretch. Admire the foundations.",
        "Someone shuffled these. You fixed it.",
        "The Queen of Spades nods in your direction. High praise.",
        "What the shuffle took apart, you put back together.",
        "Your patience has been tested and found excellent.",
        "A clean sweep. The broom is jealous.",
        "The deck is calm now. You did that.",
        "Cards in order, mind at ease.",
        "You will be remembered as the one who moved the Eight.",
        "A small win, but it's all yours.",
        "There's no such thing as a lucky shuffle. There is such a thing as a good player.",
        "The foundations are full, and so is your heart. Probably.",
        "A win a day keeps the boredom away.",
        "Rumour has it the Joker wanted in. Not today.",
        "You played that like you'd seen it before. Have you?",
        "The cards are going to tell their friends about you.",
        "Solved twice: once by a computer, once by someone with taste.",
        "Even the Twos got home in the end.",
        "Look at you, putting things in order.",
        "A fortune awaits you. This is it. Congratulations.",
        "The answer was in the cards all along.",
        "Your next move: another deal or a cup of tea. Both are correct.",
        "You have the patience of a cat at a window.",
        "Today's forecast: clear columns, with a chance of another win.",
        "Kings at the back, Aces at the front. As it should be.",
        "Those cards were in a terrible mess. Not any more.",
        "You will find what you are looking for. Unless it's a red Six. Those hide.",
        "Good news: you won. Better news: there are more deals.",
        "The pips are aligned.",
        "Your win has been noted in the great ledger of patience.",
        "Well played. The cards would applaud, but they have no hands.",
        "The odds were good, and so were you.",
        "You have unlocked: the feeling of a job well done.",
        "Neat. Tidy. Done.",
        "This win pairs well with a cup of tea.",
        "Hearts, Spades, Diamonds, Clubs. All present and correct.",
        "You will travel far. At least as far as the next deal.",
        "It's the little wins that keep us going. This was one.",
        "A deal well dealt with.",
        "The cards believe in you. They're cards, but still.",
        "One card at a time. Look where it got you.",
        "Every card in its place. If only socks were this easy.",
        "Your inner librarian is very proud.",
        "The universe is a little more sorted than it was a minute ago.",
        "Victory smells faintly of cardboard.",
        "Not a single card left under the sofa. Well done.",
        "The deck salutes you.",
        "You make it look easy. It wasn't.",
        "Order from chaos. Your speciality.",
        "Somewhere, a shuffling machine is weeping.",
        "The cards would like to know your secret. So would we.",
        "A quiet triumph. The best kind.",
        "Patience: one. Chaos: nil.",
        "Your future is bright, and neatly sorted by suit.",
        "Every pile a story. Every story a happy ending.",
    )

    private val KLONDIKE = listOf(
        "Every red Queen has found her black King.",
        "The stock is empty, and so are your worries.",
        "Seven columns, zero problems.",
        "Red on black, black on red, all the way home.",
        "Every face-down card turned over. No secrets left.",
        "A King in every corner of the kingdom.",
        "The stock ran dry, but you didn't.",
        "The oldest game in the box, and you made it look new.",
        "Klondike: the classic. You: also a classic.",
    )

    private val SPIDER = listOf(
        "Eight runs, King to Ace. The spider is pleased.",
        "You untangled the web.",
        "No spiders were harmed. One was mildly impressed.",
        "Two decks, one champion.",
        "A hundred and four cards, all where they belong.",
        "The web is tidy. Charlotte would be proud.",
        "Ten columns of chaos, now zero.",
        "You went down the suits like a staircase.",
        "Spider solved. Check under the bed anyway.",
        "The spider tips all eight of its hats.",
    )

    private val SCORPION = listOf(
        "The scorpion has been calmed.",
        "You pulled the sting out of that one.",
        "The three reserve cards never stood a chance.",
        "A sting in the tail, and you dodged it.",
        "Scorpion II: not for the faint-hearted. Clearly not you.",
        "You moved whole columns like furniture. Very tidy.",
        "The scorpion has gone back under its rock.",
        "Kings at the top, Aces at the bottom. The scorpion approves.",
    )

    private val YUKON = listOf(
        "You struck gold in the Yukon.",
        "Out of order, into the foundations. Nicely done.",
        "No stock, no mercy, no problem.",
        "You panned the whole river and kept the gold.",
        "Big moves, deep columns, no stock. Classic Yukon.",
        "Even in the cold, you kept your head.",
        "Every buried card dug out, no shovel needed.",
    )

    private val RUSSIAN = listOf(
        "Russian Solitaire. In suit. You absolute machine.",
        "Nine in ten of these can't be won. This one could, and you did.",
        "The suits lined up for you. They rarely do.",
        "In suit, on purpose, all the way. Remarkable.",
        "Russian Solitaire bows to you. Slightly.",
        "One of the hardest games in the box, done.",
    )

    private val FREECELL = listOf(
        "Four cells, zero panic.",
        "Every card was there all along. You just had to look.",
        "Not a single card was hidden, and you still made it look hard. Kidding. Well played.",
        "The free cells are free again.",
        "No luck involved. Just you.",
        "Four cells, four foundations, one winner.",
        "FreeCell, freed.",
        "Every card face up, every move yours.",
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
            m == 2 && d == 29 -> "A leap-day win. See you in four years."
            m == 2 && d == 14 -> "You stacked the hearts. How romantic."
            m == 3 && d == 14 -> "Happy Pi day. 3.14 cheers for you."
            m == 4 && d == 1 -> "This message is not an April Fools' joke. You actually won."
            m == 5 && d == 4 -> "May the fours be with you. They were."
            m == 6 && d == 21 -> "A midsummer win. The longest day, well spent."
            m == 9 && d == 19 -> "Arr! The treasure be in the foundations, matey."
            m == 10 && d == 31 -> "A spooky win. The face-down cards were the scariest part."
            m == 12 && d == 21 -> "The shortest day, and a win to brighten it."
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
            in 8..9 -> add("A win before ten. The day is already a success.")
            in 12..13 -> add("A lunch-break win. Don't tell your boss.")
            in 15..16 -> add("An afternoon win. Better than a nap. Almost.")
            in 18..19 -> add("A win before dinner. Lovely.")
            in 22..23 -> add("One more before bed? You know it's never one more.")
        }
        when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> add("You beat Monday. That's half the battle.")
            DayOfWeek.TUESDAY -> add("Tuesday, now with a win in it.")
            DayOfWeek.WEDNESDAY -> add("Halfway through the week, and winning.")
            DayOfWeek.THURSDAY -> add("Thursday. Nearly there, and winning.")
            DayOfWeek.FRIDAY -> add("Winning into the weekend.")
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> add("A weekend win. The best kind.")
        }
    }

    fun lateNight(now: LocalDateTime): Boolean = now.hour in 0..4
}
