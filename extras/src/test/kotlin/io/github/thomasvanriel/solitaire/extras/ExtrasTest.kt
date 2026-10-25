package io.github.thomasvanriel.solitaire.extras

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ExtrasTest {
    // A plain Wednesday afternoon: no holiday, no special time of day.
    private val wednesday = LocalDateTime.of(2026, 10, 21, 15, 30)

    private fun win(
        seed: Long = 1234 + 1,
        ruleSet: RuleSet = RuleSet.DRAW_1_UNLIMITED,
        tier: Tier = Tier.MEDIUM,
        seconds: Long = 400,
        moves: Int = 150,
        solutionMoves: Int = 120,
        usedUndo: Boolean = true,
        streak: Int = 2,
        now: LocalDateTime = wednesday,
    ) = WinFacts(
        ruleSet, tier, seed, seconds, moves, solutionMoves, usedUndo, hints = 0, restarts = 0, streak = streak,
        totalWins = 7, totalPlayed = 20, now = now,
    )

    @Test
    fun `fortunes are picked from the deal, so the same win gives the same line`() {
        assertEquals(Fortunes.pick(win(seed = 42)), Fortunes.pick(win(seed = 42)))
        val lines = (1L..200L).map { Fortunes.pick(win(seed = it)) }.toSet()
        assertTrue(lines.size > 30, "only ${lines.size} different lines")
    }

    @Test
    fun `a fortune is never the same as the previous one`() {
        for (seed in 1L..300L) {
            val f = win(seed = seed)
            val first = Fortunes.pick(f)
            assertNotEquals(first, Fortunes.pick(f, previous = first))
        }
        // Also when a notable line would be next.
        val first = win(streak = 5)
        val line = Fortunes.pick(first)
        assertEquals("Five in a row. You are on fire. Not literally, we hope.", line)
        assertNotEquals(line, Fortunes.pick(first, previous = line))
    }

    @Test
    fun `notable moments come first`() {
        assertEquals("Your first win. The first of many.", Fortunes.pick(win().copy(firstWinEver = true)))
        assertEquals("You beat the solver by 1 move. It's sulking.", Fortunes.pick(win(moves = 119)))
        assertEquals("You beat the solver by 20 moves. It's sulking.", Fortunes.pick(win(moves = 100)))
        assertEquals(
            "A spooky win. The face-down cards were the scariest part.",
            Fortunes.pick(win(now = LocalDateTime.of(2026, 10, 31, 20, 0))),
        )
    }

    @Test
    fun `pretty deal numbers`() {
        for (n in listOf(777L, 1111L, 1234L, 4321L, 1000L, 345L)) assertTrue(Fortunes.prettyNumber(n), "$n")
        for (n in listOf(7L, 77L, 1235L, 1001L, 4312L)) assertFalse(Fortunes.prettyNumber(n), "$n")
    }

    @Test
    fun `lucky numbers are three different numbers`() {
        val line = Fortunes.random(win(seed = 99)).first { it.startsWith("Your lucky numbers") }
        val numbers = line.substringAfter(": ").substringBefore(".").split(", ").map { it.toInt() }
        assertEquals(3, numbers.toSet().size)
        assertTrue(numbers.all { it in 1..49 })
    }

    @Test
    fun `stuck lines`() {
        val f = StuckFacts(RuleSet.SPIDER_2_SUITS, Tier.HARD, 5, 80, 600, usedUndo = true, hints = 1, timesStuck = 1, streak = 0, now = wednesday)
        assertTrue(Quips.stuckSpecial(f).contains("You used 1 hint and still got here. Impressive, in a way."))
        assertEquals(Quips.stuck(f), Quips.stuck(f))
        val lines = (1L..200L).map { Quips.stuck(f.copy(seed = it)) }.toSet()
        assertTrue(lines.size > 20, "only ${lines.size} different lines")
        for (seed in 1L..100L) {
            val g = f.copy(seed = seed)
            assertNotEquals(Quips.stuck(g), Quips.stuck(g, previous = Quips.stuck(g)))
        }
        val night = f.copy(now = LocalDateTime.of(2026, 10, 21, 3, 7))
        assertTrue((1L..200L).any { Quips.stuck(night.copy(seed = it)).contains("3:07") })
    }

    @Test
    fun `losing a streak is always mentioned`() {
        val f = AbandonFacts(RuleSet.DRAW_1_UNLIMITED, Tier.MEDIUM, 3, 50, 300, lostStreak = 4, abandonedInARow = 1, now = wednesday)
        assertEquals("Your streak of 4 has died. It had a good life.", Quips.abandoned(f))
        assertEquals("A moment of silence for your streak of 12.", Quips.abandoned(f.copy(lostStreak = 12)))
        val plain = f.copy(lostStreak = 0)
        assertTrue((1L..100L).map { Quips.abandoned(plain.copy(seed = it)) }.toSet().size > 8)
    }

    @Test
    fun `restart lines count the restarts`() {
        assertEquals("Restart number 3. The cards are getting bored.", Quips.restart(RestartFacts(1, 3, wednesday)))
        val first = Quips.restart(RestartFacts(1, 1, wednesday))
        assertFalse(first.contains("number"))
    }

    @Test
    fun `greetings`() {
        fun g(now: LocalDateTime = wednesday, first: Boolean = false, days: Long? = 0) =
            Greetings.pick(GreetingFacts(now, first, days, leavingUnfinished = false, streak = 0))
        assertEquals("Hello, and welcome to the table.", g(first = true))
        assertEquals("Welcome back. The cards missed you.", g(days = 9))
        assertEquals("Spooky deals tonight.", g(now = LocalDateTime.of(2026, 10, 31, 21, 0)))
        assertEquals("It's been a while. The cards have been practising.", g(days = 45))
        assertEquals("May the fours be with you.", g(now = LocalDateTime.of(2026, 5, 4, 10, 0)))
        assertEquals(g(), g(now = wednesday.plusMinutes(20)))
        assertTrue(g() in Greetings.timeOfDay(15) + Greetings.day(wednesday))
        val night = LocalDateTime.of(2026, 10, 21, 2, 0)
        assertTrue(g(now = night) in Greetings.timeOfDay(2) + Greetings.day(night))
        // Over a week of hours, every time of day gets several different lines.
        val week = (0L until 7 * 24).map { g(now = wednesday.plusHours(it)) }.toSet()
        assertTrue(week.size > 25, "only ${week.size} different greetings")
    }

    @Test
    fun `new holidays have their lines`() {
        assertEquals("A leap-day win. See you in four years.", Fortunes.pick(win(now = LocalDateTime.of(2028, 2, 29, 15, 0))))
        assertEquals("Arr! The treasure be in the foundations, matey.", Fortunes.pick(win(now = LocalDateTime.of(2026, 9, 19, 15, 0))))
    }

    @Test
    fun `lines are unique within their lists and short enough for the screen`() {
        val lists = buildList {
            for (rs in RuleSet.ALL) add(Fortunes.random(win(ruleSet = rs)))
            add(Quips.STUCK)
            addAll(Quips.STUCK_BY_GAME.values)
            add(Quips.ABANDONED)
            add(Quips.RESTART)
            for (h in 0..23) add(Greetings.timeOfDay(h))
        }
        for (lines in lists) {
            assertEquals(lines.size, lines.toSet().size, "duplicate in ${lines.groupBy { it }.filter { it.value.size > 1 }.keys}")
            for (line in lines) assertTrue(line.length <= 90, "too long (${line.length}): $line")
        }
        assertTrue(Fortunes.random(win()).size >= 120)
        assertTrue(Quips.STUCK.size >= 50 && Quips.ABANDONED.size >= 30 && Quips.RESTART.size >= 20)
    }

    @Test
    fun `achievement ids are unique and every one can be earned`() {
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.toSet().size)
        assertEquals(54, Achievements.ALL.size)
    }

    @Test
    fun `achievements are earned once`() {
        val none = AchievementFacts(emptyMap(), 0, 0, 0, 1)
        assertTrue(Achievements.newlyEarned(none, emptySet()).isEmpty())

        val w = win(seed = 777, usedUndo = false, now = LocalDateTime.of(2026, 10, 31, 2, 30), moves = 100)
        val facts = AchievementFacts(mapOf(RuleSet.DRAW_1_UNLIMITED to mapOf(Tier.MEDIUM to 1)), 1, 0, 0, 1, win = w)
        val earned = Achievements.newlyEarned(facts, emptySet()).map { it.id }
        assertEquals(listOf("first_win", "no_undo", "beat_solver", "night_owl", "halloween", "lucky"), earned)
        assertTrue(Achievements.newlyEarned(facts, earned.toSet()).isEmpty())
    }

    @Test
    fun `new achievements`() {
        fun earned(w: WinFacts) = Achievements.newlyEarned(
            AchievementFacts(mapOf(w.ruleSet to mapOf(w.tier to 1)), 0, 0, 0, 1, win = w), setOf("first_win"),
        ).map { it.id }
        val pure = earned(win(tier = Tier.HARD, usedUndo = false, moves = 120, now = LocalDateTime.of(2026, 2, 13, 15, 0)))
        assertTrue("purist" in pure && "great_minds" in pure && "friday13" in pure, "$pure")
        assertTrue("purist" !in earned(win(tier = Tier.MEDIUM, usedUndo = false)))
        assertTrue("comeback" in earned(win(streak = 1).copy(lostStreak = 6)))
        assertTrue("comeback" !in earned(win(streak = 1).copy(lostStreak = 4)))
    }

    @Test
    fun `grand tour needs every difficulty of every game`() {
        val all = RuleSet.ALL.associateWith { Tier.entries.associateWith { 1 } }
        val ids = Achievements.newlyEarned(AchievementFacts(all, 0, 0, 0, 0), emptySet()).map { it.id }
        assertTrue("grand_tour" in ids && "tourist" in ids && "spider4" in ids)
        val missing = all + (RuleSet.SCORPION_II to mapOf(Tier.EASY to 3))
        val ids2 = Achievements.newlyEarned(AchievementFacts(missing, 0, 0, 0, 0), emptySet()).map { it.id }
        assertTrue("tourist" in ids2 && "grand_tour" !in ids2)
    }

    @Test
    fun `scorecards are rare and only for tidy wins`() {
        assertNull(Scorecards.scores(win(seconds = 900, moves = 200)))
        val shown = (1L..4000L).count { Scorecards.scores(win(seed = it, seconds = 100, moves = 110)) != null }
        assertTrue(shown in 120..300, "shown $shown times in 4000")
        val scores = Scorecards.forced(5)
        assertEquals(3, scores.size)
        assertEquals("10", scores.last())
        assertTrue(scores.take(2).all { Regex("""9\.\d|8\.[5-9]""").matches(it) }, "$scores")
    }
}
