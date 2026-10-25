package io.github.thomasvanriel.solitaire.extras

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import java.time.DayOfWeek

/** Everything the achievements look at. [win] is the win that just happened, if any. */
data class AchievementFacts(
    /** Wins so far, per rule set and difficulty. */
    val wins: Map<RuleSet, Map<Tier, Int>>,
    val winsWithoutHints: Int,
    val totalHints: Int,
    val weekendWins: Int,
    /** Days in a row with at least one game played, up to today. */
    val dayStreak: Int,
    val win: WinFacts? = null,
) {
    val totalWins: Int get() = wins.values.sumOf { it.values.sum() }
    fun winsIn(rs: RuleSet): Int = wins[rs]?.values?.sum() ?: 0
    fun winsIn(game: GameType): Int = RuleSet.ALL.filter { it.game == game }.sumOf { winsIn(it) }
}

class Achievement internal constructor(
    /** Stored on the device, so never change one. */
    val id: String,
    val title: String,
    val description: String,
    internal val earned: (AchievementFacts) -> Boolean,
)

object Achievements {
    private fun a(id: String, title: String, description: String, earned: (AchievementFacts) -> Boolean) =
        Achievement(id, title, description, earned)

    /** On a win only. */
    private fun w(id: String, title: String, description: String, earned: (WinFacts) -> Boolean) =
        Achievement(id, title, description) { f -> f.win?.let(earned) == true }

    val ALL: List<Achievement> = listOf(
        a("first_win", "Beginner's Luck", "Win your first game.") { it.totalWins >= 1 },
        a("wins_10", "Deck Hand", "Win 10 games.") { it.totalWins >= 10 },
        a("wins_100", "Card Shark", "Win 100 games.") { it.totalWins >= 100 },
        a("wins_1000", "Patience Itself", "Win 1,000 games. Go outside.") { it.totalWins >= 1000 },
        a("tourist", "Tourist", "Win every game type at least once.") { f -> RuleSet.ALL.all { f.winsIn(it) > 0 } },
        a("grand_tour", "Grand Tour", "Win every game type on every difficulty.") { f ->
            RuleSet.ALL.all { rs -> Tier.entries.all { t -> (f.wins[rs]?.get(t) ?: 0) > 0 } }
        },
        a("expert", "Expert Witness", "Win an Expert game.") { f -> RuleSet.ALL.any { (f.wins[it]?.get(Tier.EXPERT) ?: 0) > 0 } },
        a("draw3_25", "Draw-3 Masochist", "Win 25 games of Klondike draw 3.") { it.winsIn(RuleSet.DRAW_3_UNLIMITED) >= 25 },
        a("spider4", "Spider Whisperer", "Win a 4-suit Spider game.") { it.winsIn(RuleSet.SPIDER_4_SUITS) > 0 },
        a("spider_50", "Arachnophile", "Win 50 Spider games.") { it.winsIn(GameType.SPIDER) >= 50 },
        a("scorpion_25", "Scorpion Tamer", "Win 25 Scorpion II games.") { it.winsIn(GameType.SCORPION) >= 25 },
        a("yukon_25", "Gold Rush", "Win 25 Yukon games.") { it.winsIn(RuleSet.YUKON) >= 25 },
        a("russian", "In Suit, Comrade", "Win a game of Russian Solitaire.") { it.winsIn(RuleSet.RUSSIAN) > 0 },
        a("freecell_25", "Cell Mate", "Win 25 FreeCell games.") { it.winsIn(GameType.FREECELL) >= 25 },
        w("no_undo", "No Regrets", "Win without undo.") { !it.usedUndo },
        w("no_undo_expert", "No Regrets, Really", "Win an Expert game without undo.") { !it.usedUndo && it.tier == Tier.EXPERT },
        a("no_hints_10", "All By Myself", "Win 10 games without hints.") { it.winsWithoutHints >= 10 },
        a("hints_100", "Phone a Friend", "Use 100 hints. They're free, to be fair.") { it.totalHints >= 100 },
        w("undo_win", "Time Traveller", "Win a game after using undo.") { it.usedUndo },
        w("fast_klondike", "Speed Demon", "Win a Klondike game in under 2 minutes.") {
            it.ruleSet.game == GameType.KLONDIKE && it.seconds < 120
        },
        w("fast_spider", "Lightning Spider", "Win a Spider game in under 10 minutes.") {
            it.ruleSet.game == GameType.SPIDER && it.seconds < 600
        },
        w("slow", "Tortoise", "Win a game that took over an hour.") { it.seconds > 3600 },
        w("beat_solver", "Smarter Than the Solver", "Win in fewer moves than the stored solution.") {
            it.solutionMoves > 0 && it.moves < it.solutionMoves
        },
        w("streak_5", "Hot Streak", "Win 5 in a row.") { it.streak >= 5 },
        w("streak_10", "Unstoppable", "Win 10 in a row.") { it.streak >= 10 },
        w("streak_25", "Statistically Suspicious", "Win 25 in a row.") { it.streak >= 25 },
        w("restarts_3", "Never Give Up", "Win a deal you restarted at least 3 times.") { it.restarts >= 3 },
        w("night_owl", "Night Owl", "Win between midnight and 4 a.m.") { it.now.hour < 4 },
        w("early_bird", "Early Bird", "Win between 4 and 7 a.m.") { it.now.hour in 4..6 },
        w("lunch", "Lunch Break", "Win between 12:00 and 13:00.") { it.now.hour == 12 },
        a("weekend_10", "Weekend Warrior", "Win 10 games on weekends.") { it.weekendWins >= 10 },
        w("halloween", "Spooky", "Win on 31 October.") { it.now.monthValue == 10 && it.now.dayOfMonth == 31 },
        w("new_year", "New Year, New Deck", "Win on 1 January.") { it.now.monthValue == 1 && it.now.dayOfMonth == 1 },
        w("lucky", "Lucky Number", "Win deal #7, #77 or #777 of any game.") { it.seed == 7L || it.seed == 77L || it.seed == 777L },
        a("days_7", "Daily Habit", "Play on 7 days in a row.") { it.dayStreak >= 7 },
        a("days_30", "Committed", "Play on 30 days in a row.") { it.dayStreak >= 30 },
    )

    fun byId(id: String): Achievement? = ALL.firstOrNull { it.id == id }

    /** Achievements earned by [facts] that aren't in [unlocked] yet, in list order. */
    fun newlyEarned(facts: AchievementFacts, unlocked: Set<String>): List<Achievement> =
        ALL.filter { it.id !in unlocked && it.earned(facts) }

    fun isWeekend(day: DayOfWeek): Boolean = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
}

/** The rare easter egg: cards holding up judges' scores after a fast or tidy win. */
object Scorecards {
    /** About one in 20 fast or tidy wins: three scores such as "9.5", "9.7", "10". Otherwise null. */
    fun scores(f: WinFacts): List<String>? {
        val tidy = f.newBestTime || f.newFewestMoves || (f.solutionMoves > 0 && f.moves <= f.solutionMoves) ||
            (f.ruleSet.game == GameType.KLONDIKE && f.seconds < 180)
        if (!tidy) return null
        val p = Picker(mix(f.seed, f.seconds, f.moves.toLong(), 0x5CL))
        if (!p.chance(1.0 / 20)) return null
        return forced(p)
    }

    /** Scores without the dice roll (for the debug screenshot mode). */
    fun forced(seed: Long): List<String> = forced(Picker(mix(seed, 0x5CL)))

    private fun forced(p: Picker): List<String> {
        val tenths = listOf(p.int(85, 100), p.int(85, 100)).sorted()
        return tenths.map { "${it / 10}.${it % 10}" } + "10"
    }
}
