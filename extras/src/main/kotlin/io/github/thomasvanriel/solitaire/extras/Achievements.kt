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
    /** How the board looked during [win]. */
    val leftHanded: Boolean = false,
    val darkMode: Boolean = false,
    val landscape: Boolean = false,
    /** The judges' scorecards came up for [win]. */
    val scorecards: Boolean = false,
    /** The player [win] is for had won this same deal before. */
    val dealWonBefore: Boolean = false,
    /** Games left unwon in a row just before [win]. */
    val abandonedBefore: Int = 0,
    /** Days without a game before today's first one (0 if played yesterday, or never before). */
    val daysAway: Int = 0,
    /** Wins today, and how many game types they cover. */
    val winsToday: Int = 0,
    val gameTypesWonToday: Int = 0,
    /** Undos in the game on the table. */
    val undosThisGame: Int = 0,
    /** Just now: the player shared a deal's code, or started a deal from a code. */
    val sharedDeal: Boolean = false,
    val playedCode: Boolean = false,
    /** Achievements already unlocked (set by [Achievements.newlyEarned]). */
    val unlockedCount: Int = 0,
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
        a("wins_10000", "Seriously?", "Win 10,000 games. At five minutes each, that's a month without sleep.") {
            it.totalWins >= 10_000
        },
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
        a("wins_50", "Regular", "Win 50 games.") { it.totalWins >= 50 },
        a("wins_500", "Half a Grand", "Win 500 games.") { it.totalWins >= 500 },
        a("klondike_100", "Prospector", "Win 100 Klondike games.") { it.winsIn(GameType.KLONDIKE) >= 100 },
        a("expert_all", "Expert in Everything", "Win an Expert game of every game type.") { f ->
            RuleSet.ALL.all { (f.wins[it]?.get(Tier.EXPERT) ?: 0) > 0 }
        },
        a("no_hints_100", "Self-Made", "Win 100 games without hints.") { it.winsWithoutHints >= 100 },
        w("purist", "Purist", "Win a Hard or Expert game without undo or hints.") {
            !it.usedUndo && it.hints == 0 && (it.tier == Tier.HARD || it.tier == Tier.EXPERT)
        },
        w("great_minds", "Great Minds", "Win in exactly as many moves as the stored solution.") {
            it.solutionMoves > 0 && it.moves == it.solutionMoves
        },
        w("fast_freecell", "Quick Release", "Win a FreeCell game in under 3 minutes.") {
            it.ruleSet.game == GameType.FREECELL && it.seconds < 180
        },
        w("marathon", "Marathon", "Win a game that took over 3 hours.") { it.seconds > 3 * 3600 },
        w("streak_50", "Are You a Robot?", "Win 50 in a row.") { it.streak >= 50 },
        w("comeback", "Comeback", "Win straight after losing a streak of 5 or more.") { it.lostStreak >= 5 && it.streak == 1 },
        w("restarts_10", "Stubborn", "Win a deal you restarted at least 10 times.") { it.restarts >= 10 },
        a("days_100", "Part of the Furniture", "Play on 100 days in a row.") { it.dayStreak >= 100 },
        w("friday13", "Unlucky for Some", "Win on Friday the 13th.") {
            it.now.dayOfMonth == 13 && it.now.dayOfWeek == DayOfWeek.FRIDAY
        },
        w("valentine", "Queen of Hearts", "Win on 14 February.") { it.now.monthValue == 2 && it.now.dayOfMonth == 14 },
        w("pi_day", "Easy as Pi", "Win on 14 March.") { it.now.monthValue == 3 && it.now.dayOfMonth == 14 },
        w("leap_day", "Leap of Faith", "Win on 29 February.") { it.now.monthValue == 2 && it.now.dayOfMonth == 29 },
        w("christmas", "Deck the Halls", "Win between 24 and 26 December.") { it.now.monthValue == 12 && it.now.dayOfMonth in 24..26 },
        w("hat_trick", "Hat Trick", "Win 3 in a row.") { it.streak >= 3 },
        w("backseat", "Backseat Driver", "Win a game with 10 or more hints. Who's playing, exactly?") { it.hints >= 10 },
        w("scenic", "The Scenic Route", "Win in more than twice the moves of the stored solution.") {
            it.solutionMoves > 0 && it.moves > 2 * it.solutionMoves
        },
        w("on_the_dot", "Right on Time", "Win in exactly 5:00.") { it.seconds == 300L },
        w("dont_panic", "Don't Panic", "Win deal #42 of any game.") { it.seed == 42L },
        w("last_orders", "Last Orders", "Win between 23:00 and midnight.") { it.now.hour == 23 },
        w("tea_time", "Tea Time", "Win between 16:00 and 17:00.") { it.now.hour == 16 },
        w("monday", "Case of the Mondays", "Win before 9 a.m. on a Monday.") {
            it.now.dayOfWeek == DayOfWeek.MONDAY && it.now.hour < 9
        },
        w("april_fools", "No, Really", "Win on 1 April.") { it.now.monthValue == 4 && it.now.dayOfMonth == 1 },
        w("friday13_deal13", "Unlucky for Some, Again", "Win deal #13 on Friday the 13th.") {
            it.seed == 13L && it.now.dayOfMonth == 13 && it.now.dayOfWeek == DayOfWeek.FRIDAY
        },
        a("southpaw", "Southpaw", "Win a game with the left-handed layout.") { it.win != null && it.leftHanded },
        a("lights_out", "Lights Out", "Win a game in dark mode.") { it.win != null && it.darkMode },
        a("widescreen", "Widescreen", "Win a game in landscape.") { it.win != null && it.landscape },
        a("perfect_ten", "Perfect Ten", "Get a score from the judges.") { it.win != null && it.scorecards },
        a("share", "Spread the Word", "Share a deal.") { it.sharedDeal },
        a("code", "Code Breaker", "Play a deal from its code.") { it.playedCode },
        a("deja_vu", "Déjà Vu", "Win the same deal twice.") { it.win != null && it.dealWonBefore },
        a("binge", "Binge", "Win 10 games in one day.") { it.winsToday >= 10 },
        a("variety", "Variety Show", "Win 3 different games in one day.") { it.gameTypesWonToday >= 3 },
        a("brink", "Back From the Brink", "Win after leaving 10 games unfinished in a row.") {
            it.win != null && it.abandonedBefore >= 10
        },
        a("prodigal", "Prodigal Return", "Win on your first day back after a month away.") { it.win != null && it.daysAway >= 30 },
        a("ctrl_z", "Ctrl+Z", "Undo 100 times in one game.") { it.undosThisGame >= 100 },
        // These two count the others, so they stay last.
        a("achievements_25", "Achievement Unlocked", "Unlock 25 achievements, this one included.") { it.unlockedCount >= 24 },
        a("completionist", "Completionist", "Unlock every other achievement. Then go outside. Really.") {
            it.unlockedCount >= ALL.size - 1
        },
    )

    fun byId(id: String): Achievement? = ALL.firstOrNull { it.id == id }

    /**
     * Achievements earned by [facts] that aren't in [unlocked] yet, in list order. Ones earned earlier in the
     * list count towards the ones that count achievements.
     */
    fun newlyEarned(facts: AchievementFacts, unlocked: Set<String>): List<Achievement> {
        val got = ALL.filter { it.id in unlocked }.mapTo(HashSet()) { it.id }
        return ALL.filter { a ->
            (a.id !in got && a.earned(facts.copy(unlockedCount = got.size))).also { if (it) got += a.id }
        }
    }

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
