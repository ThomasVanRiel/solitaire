package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 10,000 random games of random legal moves. After every move the invariants must hold, and undoing
 * every move must give back the exact starting position.
 */
class RulesFuzzTest {
    @Test
    fun randomGamesKeepInvariantsAndUndoExactly() {
        val rng = Xoshiro256.seeded(20261024)
        val buf = IntArray(Rules.MAX_MOVES)
        val ruleSets = listOf(RuleSet.DRAW_1_UNLIMITED, RuleSet.DRAW_3_UNLIMITED, RuleSet.custom(3, 2))
        var totalMoves = 0L
        val seenTypes = HashSet<Int>()
        for (game in 0 until 10_000) {
            val rs = ruleSets[game % ruleSets.size]
            val start = Deals.deal(game.toLong())
            val s = start.copy()
            val tokens = IntArray(250)
            var n = 0
            while (n < tokens.size) {
                val count = Rules.legalMoveCodes(s, rs, buf)
                if (count == 0) break
                val code = buf[rng.nextInt(count)]
                val check = if (n % 37 == 0) Rules.apply(s, rs, Move.decode(code)) else null
                tokens[n++] = Rules.applyCode(s, rs, code)
                seenTypes += Move.type(code)
                if (check != null) assertEquals(check, s)
                val problem = StateValidator.problem(s)
                assertNull(problem, "Game $game move $n (${Move.decode(code)}): $problem\n$s")
                if (Rules.isWon(s)) break
            }
            totalMoves += n
            for (i in n - 1 downTo 0) Rules.undoCode(s, rs, tokens[i])
            assertEquals(start, s, "Game $game did not undo back to the start")
        }
        assertTrue(totalMoves > 1_000_000, "fuzz should play plenty of moves, played $totalMoves")
        // Every Klondike move type except foundation to tableau, which no game allows any more.
        assertEquals((0..5).toSet(), seenTypes, "every move type should come up")
    }
}
