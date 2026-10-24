package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.difficulty.RunGamePlayer
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.Move.Draw
import io.github.thomasvanriel.solitaire.engine.model.Move.TableauToFoundation
import io.github.thomasvanriel.solitaire.engine.model.Move.TableauToTableau
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.model.StateFormat
import io.github.thomasvanriel.solitaire.engine.pack.SolutionCodec
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256
import io.github.thomasvanriel.solitaire.engine.solver.DealSolver
import io.github.thomasvanriel.solitaire.engine.solver.ProofVerifier
import io.github.thomasvanriel.solitaire.engine.solver.SolveResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Spider and Scorpion II: deals, rules, apply/undo, the solver and the fair player. */
class RunGamesTest {
    private val sp1 = RuleSet.SPIDER_1_SUIT
    private val sp2 = RuleSet.SPIDER_2_SUITS
    private val sp4 = RuleSet.SPIDER_4_SUITS
    private val sc = RuleSet.SCORPION_II
    private val runGames = listOf(sp1, sp2, sp4, sc)

    private fun pos(text: String, rs: RuleSet) = StateFormat.parse(text.trimIndent(), rs)

    /** Applies [move] in place, checks the result, then checks that undo restores the start exactly. */
    private fun applyAndUndo(start: GameState, rs: RuleSet, move: Move, check: (GameState) -> Unit = {}) {
        assertTrue(Rules.isLegal(start, rs, move), "$move should be legal in\n$start")
        val after = Rules.apply(start, rs, move)
        check(after)
        val inPlace = start.copy()
        val token = Rules.applyCode(inPlace, rs, move.code)
        assertEquals(after, inPlace)
        Rules.undoCode(inPlace, rs, token)
        assertEquals(start, inPlace)
    }

    private fun col(s: GameState, c: Int) = s.column(c).joinToString(" ") { Card.code(it) }

    // ------------------------------------------------------------------ deals

    @Test
    fun oneDeckShuffleIsUnchanged() {
        for (seed in listOf(0L, 1L, 48213L, -7L)) {
            assertArrayEquals(Deals.deck(seed), Deals.deck(seed, RuleSet.DRAW_1_UNLIMITED))
            assertEquals(Deals.deal(seed), Deals.deal(seed, RuleSet.DRAW_3_UNLIMITED))
        }
    }

    @Test
    fun spiderDealLayout() {
        for (rs in listOf(sp1, sp2, sp4)) {
            val s = Deals.deal(5, rs)
            assertEquals(10, s.columns)
            assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), s.tabSize.toList())
            for (c in 0 until 10) assertEquals(s.tabSize[c] - 1, s.tabDown[c])
            assertEquals(50, s.stockSize)
            assertNull(StateValidator.problem(s, rs))
            val counts = HashMap<Int, Int>()
            for (card in Deals.unshuffledDeck(rs)) counts.merge(card, 1, Int::plus)
            assertEquals(13 * rs.suits, counts.size, "$rs uses ${rs.suits} suits")
            assertTrue(counts.values.all { it == 8 / rs.suits })
        }
        assertTrue(Deals.unshuffledDeck(sp1).all { Card.suit(it) == Card.SPADES })
        assertEquals(setOf(Card.SPADES, Card.HEARTS), Deals.unshuffledDeck(sp2).map { Card.suit(it) }.toSet())
    }

    @Test
    fun scorpionDealLayout() {
        val s = Deals.deal(5, sc)
        assertEquals(7, s.columns)
        assertTrue(s.tabSize.all { it == 7 })
        assertEquals(listOf(3, 3, 3, 0, 0, 0, 0), s.tabDown.toList())
        assertEquals(3, s.stockSize)
        assertNull(StateValidator.problem(s, sc))
    }

    // ------------------------------------------------------------------ Spider rules

    @Test
    fun spiderMovesSameSuitRunsOnly() {
        val s = pos(
            """
            t1: 9H 8S 7S
            t2: 9D
            t3: 8C
            t4: KS
            t5: 2C
            t6: 3C
            t7: 4C
            t8: 5C
            t9: 6C
            t10: TC
            stock: AC
            found: 0 0 0 0
            """,
            sp4,
        )
        val moves = Rules.legalMoves(s, sp4)
        // 8S 7S is a same-suit run and may move onto any 9; 9H 8S is not, so the column can't move as one.
        assertTrue(TableauToTableau(0, 2, 1) in moves)
        assertFalse(moves.any { it is TableauToTableau && it.from == 0 && it.count == 3 })
        // Down regardless of suit: 7S onto 8C, and 9D onto TC.
        assertTrue(TableauToTableau(0, 1, 2) in moves)
        assertTrue(TableauToTableau(1, 1, 9) in moves)
        applyAndUndo(s, sp4, TableauToTableau(0, 2, 1)) { assertEquals("9D 8S 7S", col(it, 1)) }
    }

    @Test
    fun spiderEmptyColumnsTakeAnythingAndBlockTheDeal() {
        val s = pos(
            """
            t1: 4S | 9D
            t2:
            t3: 5C
            stock: 2C 3C 4C 5C 6C 7C 8C 9C TC JC
            """,
            sp4,
        )
        val moves = Rules.legalMoves(s, sp4)
        assertTrue(TableauToTableau(0, 1, 1) in moves, "any card may go to an empty column")
        assertFalse(Draw in moves, "no deal while a column is empty")
        applyAndUndo(s, sp4, TableauToTableau(0, 1, 1)) {
            assertEquals("4S", col(it, 0))
            assertEquals(0, it.tabDown[0], "the card underneath turns over")
        }
    }

    @Test
    fun spiderDealPutsOneCardOnEveryColumn() {
        val s = Deals.deal(11, sp2)
        val top = s.stock[s.stockSize - 1]
        applyAndUndo(s, sp2, Draw) {
            assertEquals(40, it.stockSize)
            for (c in 0 until 10) assertEquals(s.tabSize[c] + 1, it.tabSize[c])
            assertEquals(top, it.columnTop(0), "the top of the stock goes to the first column")
            assertEquals(s.tabDown.toList(), it.tabDown.toList(), "dealt cards are face up")
        }
    }

    @Test
    fun completeRunMustLeaveAndUndoesExactly() {
        val s = pos(
            """
            t1: 3H | KS QS JS TS 9S 8S 7S 6S 5S 4S 3S 2S
            t2: AS
            t3: 4D
            t4: 5D
            t5: 6D
            t6: 7D
            t7: 8D
            t8: 9D
            t9: TD
            t10: JD
            stock:
            found: 0 0 0 0
            """,
            sp4,
        )
        // Not complete yet; put the Ace on.
        val afterAce = Rules.apply(s, sp4, TableauToTableau(1, 1, 0))
        assertTrue(Rules.hasCompleteRun(afterAce, 0))
        assertEquals(listOf<Move>(TableauToFoundation(0)), Rules.legalMoves(afterAce, sp4), "taking the run off is forced")
        applyAndUndo(afterAce, sp4, TableauToFoundation(0)) {
            assertEquals(13, it.found[Card.SPADES])
            assertEquals("3H", col(it, 0))
            assertEquals(0, it.tabDown[0], "the card under the run turns over")
        }
    }

    @Test
    fun mixedSuitKingToAceIsNotComplete() {
        val s = pos("t1: KS QS JS TS 9S 8S 7S 6S 5S 4S 3S 2S AH\nstock: KC", sp4)
        assertFalse(Rules.hasCompleteRun(s, 0))
    }

    @Test
    fun spiderWinsWhenEveryRunIsOff() {
        val s = pos("t1: KS QS JS TS 9S 8S 7S 6S 5S 4S 3S 2S AS\nfound: 0 0 0 91", sp1)
        assertNull(StateValidator.problem(s, sp1))
        val won = Rules.apply(s, sp1, TableauToFoundation(0))
        assertTrue(Rules.isWon(won))
        assertEquals(104, won.foundationCount)
    }

    // ------------------------------------------------------------------ Scorpion rules

    @Test
    fun scorpionMovesAnyFaceUpCardWithEverythingOnIt() {
        val s = pos(
            """
            t1: 2C | 7H 3D KC 9S
            t2: 8H
            t3: QD
            t4:
            t5: 4D
            stock: AC
            """,
            sc,
        )
        val moves = Rules.legalMoves(s, sc)
        assertTrue(TableauToTableau(0, 4, 1) in moves, "7H and everything on it onto 8H")
        assertTrue(TableauToTableau(0, 2, 3) in moves, "a King (with its cards) into an empty column")
        assertFalse(moves.any { it is TableauToTableau && it.to == 2 }, "nothing goes on QD: only JD could")
        assertTrue(TableauToTableau(0, 3, 4) in moves, "3D with KC 9S onto 4D, in suit")
        assertFalse(TableauToTableau(0, 1, 1) in moves, "9S needs TS, not 8H")
        applyAndUndo(s, sc, TableauToTableau(0, 4, 1)) {
            assertEquals("8H 7H 3D KC 9S", col(it, 1))
            assertEquals(0, it.tabDown[0])
        }
    }

    @Test
    fun scorpionOnlyKingsFillEmptyColumns() {
        val s = pos("t1: 5S 9H\nt2:\nstock: AC", sc)
        assertFalse(Rules.legalMoves(s, sc).any { it is TableauToTableau })
    }

    @Test
    fun scorpionDealsOnceOntoTheFirstThreeColumns() {
        val s = Deals.deal(3, sc)
        val stock = s.stockList()
        applyAndUndo(s, sc, Draw) {
            assertEquals(0, it.stockSize)
            assertEquals(stock[2], it.columnTop(0))
            assertEquals(stock[1], it.columnTop(1))
            assertEquals(stock[0], it.columnTop(2))
            for (c in 3 until 7) assertEquals(7, it.tabSize[c])
            assertFalse(Draw in Rules.legalMoves(it, sc), "only one deal")
        }
        // The deal may go onto empty columns.
        val e = pos("t1:\nt2: KH\nstock: AC 2C 3C", sc)
        assertTrue(Draw in Rules.legalMoves(e, sc))
    }

    // ------------------------------------------------------------------ fuzz

    @Test
    fun randomGamesKeepInvariantsAndUndoExactly() {
        val rng = Xoshiro256.seeded(20261025)
        val buf = IntArray(Rules.MAX_MOVES)
        val seenTypes = HashSet<Int>()
        var totalMoves = 0L
        for (game in 0 until 2_000) {
            val rs = runGames[game % runGames.size]
            val start = Deals.deal(game.toLong(), rs)
            val s = start.copy()
            val tokens = IntArray(400)
            var n = 0
            while (n < tokens.size) {
                val count = Rules.legalMoveCodes(s, rs, buf)
                if (count == 0) break
                // Deal less often, so games last.
                var code = buf[rng.nextInt(count)]
                if (Move.type(code) == Move.TYPE_DRAW && count > 1 && rng.nextInt(4) != 0) code = buf[rng.nextInt(count - 1)]
                val check = if (n % 37 == 0) Rules.apply(s, rs, Move.decode(code)) else null
                tokens[n++] = Rules.applyCode(s, rs, code)
                seenTypes += Move.type(code)
                if (check != null) assertEquals(check, s)
                val problem = StateValidator.problem(s, rs)
                assertNull(problem, "$rs game $game move $n (${Move.decode(code)}): $problem\n$s")
                if (Rules.isWon(s)) break
            }
            totalMoves += n
            for (i in n - 1 downTo 0) Rules.undoCode(s, rs, tokens[i])
            assertEquals(start, s, "$rs game $game did not undo back to the start")
        }
        assertTrue(totalMoves > 300_000, "fuzz should play plenty of moves, played $totalMoves")
        assertEquals(setOf(Move.TYPE_DRAW, Move.TYPE_T2T), seenTypes - Move.TYPE_T2F, "deals and group moves come up")
    }

    // ------------------------------------------------------------------ codes and codec

    @Test
    fun wideMoveCodesRoundTrip() {
        val all = buildList {
            for (f in 0..9) for (t in 0..9) for (n in listOf(1, 13, 15, 16, 31, 52, 63)) if (f != t) add(TableauToTableau(f, n, t))
            for (c in 0..9) add(TableauToFoundation(c))
        }
        assertEquals(all.size, all.map { it.code }.toSet().size)
        for (m in all) assertEquals(m, Move.decode(m.code))
        assertEquals(all, Move.decodeAll(Move.encodeAll(all)))
        // Klondike codes keep their old 13-bit values, so old saved games still load.
        assertEquals(5 or (2 shl 3) or (4 shl 6) or (3 shl 9), TableauToTableau(2, 3, 4).code)
        val old = byteArrayOf(0, 0, (TableauToTableau(2, 3, 4).code shr 8).toByte(), TableauToTableau(2, 3, 4).code.toByte())
        assertEquals(listOf(Draw, TableauToTableau(2, 3, 4)), Move.decodeAll(old, bytesPerMove = 2))
    }

    @Test
    fun codecHandlesMoreThan255Choices() {
        // Five K-to-2 spade runs and five empty columns: 5 runs x 12 lengths x 5 empty columns = 300 moves.
        val run = "KS QS JS TS 9S 8S 7S 6S 5S 4S 3S 2S"
        val text = (1..5).joinToString("\n") { "t$it: $run" } +
            "\nstock: " + (List(5) { "AS" } + List(3) { run.split(" ") + "AS" }.flatten()).joinToString(" ")
        val s = pos(text, sp1)
        assertNull(StateValidator.problem(s, sp1))
        val n = Rules.legalMoves(s, sp1).size
        assertEquals(300, n)
        val last = Rules.legalMoves(s, sp1).last()
        val moves = listOf(last, Rules.legalMoves(Rules.apply(s, sp1, last), sp1).first())
        val bytes = SolutionCodec.encode(s, sp1, moves)
        assertEquals(4, bytes.size, "an escaped index takes three bytes")
        assertEquals(moves, SolutionCodec.decode(s, sp1, bytes))
    }

    // ------------------------------------------------------------------ solver and player

    @Test
    fun solverFindsVerifiedWins() {
        for (rs in runGames) {
            var solved = 0
            val solver = DealSolver.create(rs, maxNodes = 200_000)
            for (seed in 0L until 12L) {
                val r = solver.solve(Deals.deal(seed, rs))
                if (r is SolveResult.Solved) {
                    solved++
                    assertTrue(ProofVerifier.verify(seed, rs, r.moves), "$rs seed $seed: proof must verify")
                }
            }
            assertTrue(solved > 0, "$rs: the solver should win some of the first 12 deals")
        }
    }

    @Test
    fun corruptedProofsFail() {
        val rs = sp2
        val seed = (0L until 20L).first { DealSolver.create(rs, 200_000).solve(Deals.deal(it, rs)) is SolveResult.Solved }
        val moves = (DealSolver.create(rs, 200_000).solve(Deals.deal(seed, rs)) as SolveResult.Solved).moves
        assertTrue(ProofVerifier.verify(seed, rs, moves))
        assertFalse(ProofVerifier.verify(seed, rs, moves.dropLast(1)))
        assertFalse(ProofVerifier.verify(seed, rs, moves.drop(1)))
        assertFalse(ProofVerifier.verify(seed + 1, rs, moves))
        assertFalse(ProofVerifier.verify(seed, sp4, moves), "a 2-suit proof doesn't win the 4-suit deal")
    }

    /** Swaps the hidden cards (face-down cards and the stock) around, keeping everything visible fixed. */
    private fun shuffleHidden(s: GameState, rng: Xoshiro256): GameState {
        val t = s.copy()
        val slots = ArrayList<Pair<Int, Int>>()
        for (c in 0 until t.columns) for (i in 0 until t.tabDown[c]) slots += c to i
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
    fun playerDecisionsDependOnlyOnVisibleCards() {
        val walk = Xoshiro256.seeded(9)
        for (rs in runGames) {
            for (seed in 0L until 60L) {
                var s = Deals.deal(seed, rs)
                repeat(walk.nextInt(30)) {
                    val moves = Rules.legalMoves(s, rs)
                    if (moves.isNotEmpty()) s = Rules.apply(s, rs, moves[walk.nextInt(moves.size)])
                }
                val other = shuffleHidden(s, Xoshiro256.seeded(seed))
                val a = RunGamePlayer(rs, Xoshiro256.seeded(seed)).chooseMove(s)
                val b = RunGamePlayer(rs, Xoshiro256.seeded(seed)).chooseMove(other)
                assertEquals(a, b, "$rs seed $seed: the choice changed when only hidden cards changed")
            }
        }
    }
}
