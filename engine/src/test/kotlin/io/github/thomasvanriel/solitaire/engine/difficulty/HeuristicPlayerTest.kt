package io.github.thomasvanriel.solitaire.engine.difficulty

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeuristicPlayerTest {
    /** Swaps the hidden cards (face-down cards and the whole stock) around, keeping everything visible fixed. */
    private fun shuffleHidden(s: GameState, rng: Xoshiro256): GameState {
        val t = s.copy()
        val slots = ArrayList<Pair<Int, Int>>() // (column or -1 for stock, index)
        for (c in 0 until GameState.COLUMNS) for (i in 0 until t.tabDown[c]) slots += c to i
        for (i in 0 until t.stockSize) slots += -1 to i
        val cards = slots.map { (c, i) -> if (c < 0) t.stock[i] else t.tab[c][i] }.toMutableList()
        for (i in cards.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val x = cards[i]; cards[i] = cards[j]; cards[j] = x
        }
        slots.forEachIndexed { k, (c, i) -> if (c < 0) t.stock[i] = cards[k] else t.tab[c][i] = cards[k] }
        return t
    }

    @Test
    fun decisionsDependOnlyOnVisibleCards() {
        val rs = RuleSet.DRAW_3_UNLIMITED
        val walk = Xoshiro256.seeded(7)
        for (seed in 0L until 200L) {
            // Walk a few random moves in, so positions vary.
            var s = Deals.deal(seed)
            repeat(walk.nextInt(40)) {
                val moves = Rules.legalMoves(s, rs)
                if (moves.isNotEmpty()) s = Rules.apply(s, rs, moves[walk.nextInt(moves.size)])
            }
            val other = shuffleHidden(s, Xoshiro256.seeded(seed))
            val a = HeuristicPlayer(rs, Xoshiro256.seeded(seed)).chooseMove(s)
            val b = HeuristicPlayer(rs, Xoshiro256.seeded(seed)).chooseMove(other)
            assertEquals(a, b, "seed $seed: the choice changed when only hidden cards changed")
        }
    }

    @Test
    fun ratingsAreDeterministicAndInRange() {
        for (rs in RuleSet.ALL) {
            var total = 0
            for (seed in 0L until 20L) {
                val w = DifficultyRater.wins(Deals.deal(seed, rs), rs, seed, 10)
                assertEquals(w, DifficultyRater.wins(Deals.deal(seed, rs), rs, seed, 10))
                assertTrue(w in 0..DifficultyRater.maxRating(rs, 10))
                total += w
            }
            assertTrue(total > 0, "$rs: the heuristic player should win some games")
        }
    }

    @Test
    fun tiers() {
        for (rs in RuleSet.ALL) {
            val max = DifficultyRater.maxRating(rs, 30)
            assertEquals(Tier.EXPERT, DifficultyRater.tierFor(0, 30, rs))
            assertEquals(Tier.EASY, DifficultyRater.tierFor(max, 30, rs))
            val tiers = (0..max).map { DifficultyRater.tierFor(it, 30, rs) }
            assertEquals(tiers.sortedByDescending { it.id }, tiers, "more wins never means a harder tier")
            assertEquals(Tier.entries.toSet(), tiers.toSet(), "every tier is reachable")
        }
        assertEquals(Tier.HARD, DifficultyRater.tierFor(6, 30, RuleSet.DRAW_1_UNLIMITED))
        assertEquals(Tier.MEDIUM, DifficultyRater.tierFor(7, 30, RuleSet.DRAW_1_UNLIMITED))
        assertEquals(Tier.MEDIUM, DifficultyRater.tierFor(11, 30, RuleSet.DRAW_3_UNLIMITED))
        assertEquals(Tier.EASY, DifficultyRater.tierFor(12, 30, RuleSet.DRAW_3_UNLIMITED))
        assertEquals(Tier.HARD, DifficultyRater.tierFor(1, 10, RuleSet.DRAW_3_UNLIMITED))
    }
}
