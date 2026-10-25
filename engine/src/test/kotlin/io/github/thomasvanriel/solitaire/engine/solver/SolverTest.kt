package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.model.StateFormat
import io.github.thomasvanriel.solitaire.engine.rules.StateValidator
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolverTest {
    private val d1 = RuleSet.DRAW_1_UNLIMITED
    private val d3 = RuleSet.DRAW_3_UNLIMITED

    private fun pos(text: String) = StateFormat.parse(text.trimIndent()).also { assertNull(StateValidator.problem(it)) }

    private fun solveAndVerify(text: String, rs: RuleSet): List<Move> {
        val start = pos(text)
        val r = Solver(rs).solve(start)
        assertTrue(r is SolveResult.Solved, "expected Solved, got $r")
        val moves = (r as SolveResult.Solved).moves
        assertTrue(ProofVerifier.verifyFrom(start, rs, moves), "proof must verify")
        return moves
    }

    @Test
    fun alreadyWon() {
        assertEquals(emptyList<Move>(), solveAndVerify("found: 13 13 13 13", d1))
    }

    @Test
    fun oneCardLeft() {
        assertEquals(listOf<Move>(Move.TableauToFoundation(0)), solveAndVerify("found: 13 13 13 12\nt1: KS", d1))
    }

    @Test
    fun needsTheStock() {
        for (rs in listOf(d1, d3)) {
            solveAndVerify(
                """
                found: 13 13 11 11
                stock: KS QH KH QS
                """,
                rs,
            )
        }
    }

    @Test
    fun draw3NeedsARecycleToReachTheRightCard() {
        // Draw 3 shows TS, then 8S: 8S and 9S go up, but JS is stuck on top of TS. Only after a recycle do
        // the groups shift so that TS comes to the top. (Nothing can go into the empty columns but KS, which
        // is never on top in the first pass.)
        val moves = solveAndVerify(
            """
            found: 13 13 13 7
            stock: 8S 9S JS TS QS KS
            """,
            d3,
        )
        assertTrue(moves.any { it is Move.Recycle }, "solution must recycle: $moves")
    }

    @Test
    fun foundationCardsNeverComeBack() {
        // 4H covers 3H and needs a black 5; the only one is 5C on its foundation, which stays there.
        val s = pos(
            """
            found: 5 13 2 13
            t1: 3H | 4H
            t2: 6H
            t3: 5H
            stock: 6C 7C 8C 9C TC JC QC KC 7H 8H 9H TH JH QH KH
            """,
        )
        assertTrue(Solver(d1).solve(s) is SolveResult.Unsolvable)
    }

    @Test
    fun provesSmallPositionsUnsolvable() {
        // Every column holds a high heart over the low heart it needs. Hearts can only stack on black cards,
        // and the only black cards that could come down from the foundations are Kings, with no empty column.
        val deadlock = """
            found: 13 13 0 13
            t1: AH | 8H
            t2: 2H | 9H
            t3: 3H | TH
            t4: 4H | JH
            t5: 5H | QH
            t6: 6H | KH
            t7: 7H
        """
        for (rs in listOf(d1, d3)) {
            val r = Solver(rs).solve(pos(deadlock))
            assertTrue(r is SolveResult.Unsolvable, "got $r")
        }
    }

    @Test
    fun drawOneCanReachAnyStockCard() {
        // JS is on top of the stock, but QS has to wait for it: the solver must draw, play, then keep drawing.
        val text = """
            found: 13 13 13 10
            stock: QS KS JS
        """
        assertTrue(Solver(d1).solve(pos(text)) is SolveResult.Solved)
    }

    @Test
    fun budgetGivesUnknown() {
        val r = Solver(d3, maxNodes = 5).solve(Deals.deal(1))
        assertTrue(r is SolveResult.Unknown || r is SolveResult.Solved, "got $r")
        assertTrue(Solver(d3, isCancelled = { true }).solve(Deals.deal(2)) is SolveResult.Unknown)
    }

    @Test
    fun dealsSolveAndProofsVerify() {
        for (rs in listOf(d1, d3)) {
            var solved = 0
            for (seed in 0L until 60L) {
                val r = Solver(rs, maxNodes = 100_000).solve(Deals.deal(seed))
                if (r is SolveResult.Solved) {
                    solved++
                    assertTrue(ProofVerifier.verify(seed, rs, r.moves), "seed $seed $rs")
                }
            }
            assertTrue(solved >= 40, "$rs solved only $solved of 60")
        }
    }

    @Test
    fun corruptedProofsFail() {
        for (rs in listOf(d1, d3)) {
            var checked = 0
            var seed = 100L
            while (checked < 3) {
                val r = Solver(rs, maxNodes = 100_000).solve(Deals.deal(seed))
                if (r is SolveResult.Solved) {
                    val moves = r.moves
                    assertTrue(ProofVerifier.verify(seed, rs, moves))
                    // Removing any single move must break the proof.
                    for (i in moves.indices) {
                        val broken = moves.toMutableList().also { it.removeAt(i) }
                        assertFalse(ProofVerifier.verify(seed, rs, broken), "seed $seed: proof still verified without move $i")
                    }
                    // Changing a move to a different one must break it too.
                    for (i in moves.indices step 7) {
                        val other = if (moves[i] == Move.Draw) Move.Recycle else Move.Draw
                        val changed = moves.toMutableList().also { it[i] = other }
                        assertFalse(ProofVerifier.verify(seed, rs, changed), "seed $seed: changed move $i still verified")
                    }
                    assertFalse(ProofVerifier.verify(seed, rs, moves + Move.Draw), "a move after the win is illegal")
                    assertFalse(ProofVerifier.verify(seed + 1, rs, moves), "wrong seed")
                    checked++
                }
                seed++
            }
        }
    }
}
