package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.Move.Draw
import io.github.thomasvanriel.solitaire.engine.model.Move.FoundationToTableau
import io.github.thomasvanriel.solitaire.engine.model.Move.Recycle
import io.github.thomasvanriel.solitaire.engine.model.Move.TableauToFoundation
import io.github.thomasvanriel.solitaire.engine.model.Move.TableauToTableau
import io.github.thomasvanriel.solitaire.engine.model.Move.WasteToFoundation
import io.github.thomasvanriel.solitaire.engine.model.Move.WasteToTableau
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.model.StateFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RulesTest {
    private val d1 = RuleSet.DRAW_1_UNLIMITED
    private val d3 = RuleSet.DRAW_3_UNLIMITED

    private fun pos(text: String) = StateFormat.parse(text.trimIndent())
    private fun cards(s: List<Int>) = s.joinToString(" ") { Card.code(it) }

    /** Applies [move] in place, checks the result, then checks that undo restores the start exactly. */
    private fun applyAndUndo(start: GameState, rs: RuleSet, move: Move, check: (GameState) -> Unit) {
        assertTrue(Rules.isLegal(start, rs, move), "$move should be legal in\n$start")
        val after = Rules.apply(start, rs, move)
        check(after)
        val inPlace = start.copy()
        val token = Rules.applyCode(inPlace, rs, move.code)
        assertEquals(after, inPlace)
        Rules.undoCode(inPlace, rs, token)
        assertEquals(start, inPlace)
    }

    @Test
    fun drawOne() {
        val s = pos("stock: 2H 3C")
        assertEquals(listOf<Move>(Draw), Rules.legalMoves(s, d1))
        applyAndUndo(s, d1, Draw) {
            assertEquals("2H", cards(it.stockList()))
            assertEquals("3C", cards(it.wasteList()))
        }
    }

    @Test
    fun drawThreeTurnsThreeCardsOver() {
        val s = pos("stock: AC 2C 3C 4C 5C")
        applyAndUndo(s, d3, Draw) {
            assertEquals("AC 2C", cards(it.stockList()))
            assertEquals("5C 4C 3C", cards(it.wasteList()))
            assertEquals(Card.parse("3C"), it.wasteTop())
        }
    }

    @Test
    fun drawThreeAtTheEndOfTheStockDrawsWhatIsLeft() {
        val s = pos(
            """
            stock: AC 2C
            waste: 5C 4C 3C
            """,
        )
        applyAndUndo(s, d3, Draw) {
            assertEquals(0, it.stockSize)
            assertEquals("5C 4C 3C 2C AC", cards(it.wasteList()))
        }
        val one = pos("stock: 9D\nwaste: 5C")
        applyAndUndo(one, d3, Draw) { assertEquals("5C 9D", cards(it.wasteList())) }
    }

    @Test
    fun recycleTurnsTheWasteBackOver() {
        val s = pos("waste: AC 2C 3C")
        assertTrue(Rules.isLegal(s, d1, Recycle))
        assertFalse(Rules.isLegal(s, d1, Draw))
        applyAndUndo(s, d1, Recycle) {
            assertEquals("3C 2C AC", cards(it.stockList()))
            assertEquals(0, it.wasteSize)
            assertEquals(1, it.recycles)
            // The first card drawn after a recycle is the one that was drawn first before it.
            assertEquals(Card.parse("AC"), Rules.apply(it, d1, Draw).wasteTop())
        }
    }

    @Test
    fun recycleIsIllegalWithStockLeftOrNoWaste() {
        assertFalse(Rules.isLegal(pos("stock: 4D\nwaste: AC"), d1, Recycle))
        assertFalse(Rules.isLegal(pos(""), d1, Recycle))
        assertTrue(Rules.legalMoves(pos(""), d1).isEmpty())
    }

    @Test
    fun passLimitIsEnforced() {
        val noRecycles = RuleSet.custom(3, 0)
        val oneRecycle = RuleSet.custom(3, 1)
        val s = pos("waste: AC 2C")
        assertFalse(Rules.isLegal(s, noRecycles, Recycle))
        assertTrue(Rules.isLegal(s, oneRecycle, Recycle))
        val used = pos("waste: AC 2C\nrecycles: 1")
        assertFalse(Rules.isLegal(used, oneRecycle, Recycle))
        assertTrue(Rules.isLegal(used, d3, Recycle))
    }

    @Test
    fun onlyKingsGoToEmptyColumns() {
        val king = pos("waste: KH\nt1: 5S")
        assertEquals(
            setOf<Move>(WasteToTableau(1), WasteToTableau(2), WasteToTableau(3), WasteToTableau(4), WasteToTableau(5), WasteToTableau(6)),
            Rules.legalMoves(king, d1).filterIsInstance<WasteToTableau>().toSet(),
        )
        applyAndUndo(king, d1, WasteToTableau(3)) { assertEquals(listOf(Card.parse("KH")), it.column(3)) }

        val queen = pos("waste: QH\nt1: 5S")
        assertTrue(Rules.legalMoves(queen, d1).filterIsInstance<WasteToTableau>().isEmpty())

        val kingRun = pos(
            """
            t1: 3D | KS QH JC
            t2: 9H
            t3: 8S
            t4: 7D
            t5: 6C
            t6: 5H
            t7: 4S
            """,
        )
        // No empty column, so the King run can't move anywhere.
        assertFalse(Rules.legalMoves(kingRun, d1).any { it is TableauToTableau && it.from == 0 })
    }

    @Test
    fun kingRunMovesToEmptyColumnAndFlips() {
        val s = pos("t1: 3D | KS QH JC")
        applyAndUndo(s, d1, TableauToTableau(0, 3, 4)) {
            assertEquals("3D", cards(it.column(0)))
            assertEquals(0, it.tabDown[0])
            assertEquals("KS QH JC", cards(it.column(4)))
        }
    }

    @Test
    fun wasteToTableauNeedsOppositeColourOneRankLower() {
        val s = pos(
            """
            waste: 9H
            t1: TS
            t2: TD
            t3: 8C
            t4: JS
            """,
        )
        assertEquals(listOf<Move>(WasteToTableau(0)), Rules.legalMoves(s, d1).filterIsInstance<WasteToTableau>())
        applyAndUndo(s, d1, WasteToTableau(0)) { assertEquals("TS 9H", cards(it.column(0))) }
    }

    @Test
    fun wasteToFoundation() {
        val s = pos("waste: 2H 3H\nfound: 0 0 2 0")
        applyAndUndo(s, d1, WasteToFoundation) {
            assertEquals(3, it.found[Card.HEARTS])
            assertEquals("2H", cards(it.wasteList()))
        }
        assertFalse(Rules.isLegal(pos("waste: 3H\nfound: 0 0 1 0"), d1, WasteToFoundation))
        assertTrue(Rules.isLegal(pos("waste: AS"), d1, WasteToFoundation))
    }

    @Test
    fun tableauToFoundationFlipsTheCardUnderneath() {
        val s = pos("t3: 4C 9D | AS\nfound: 0 0 0 0")
        applyAndUndo(s, d1, TableauToFoundation(2)) {
            assertEquals(1, it.found[Card.SPADES])
            assertEquals("4C 9D", cards(it.column(2)))
            assertEquals(1, it.tabDown[2])
            assertTrue(it.isFaceUp(2, 1))
        }
    }

    @Test
    fun anyPartOfAFaceUpRunCanMove() {
        val s = pos(
            """
            t1: 2C | 9H 8S 7D
            t2: 9D
            t3: TC
            """,
        )
        val moves = Rules.legalMoves(s, d1).filterIsInstance<TableauToTableau>().filter { it.from == 0 }.toSet()
        assertEquals(setOf(TableauToTableau(0, 2, 1), TableauToTableau(0, 3, 2)), moves)
        applyAndUndo(s, d1, TableauToTableau(0, 2, 1)) {
            assertEquals("2C 9H", cards(it.column(0)))
            assertEquals(1, it.tabDown[0], "9H was already face up, nothing flips")
            assertEquals("9D 8S 7D", cards(it.column(1)))
        }
        applyAndUndo(s, d1, TableauToTableau(0, 3, 2)) {
            assertEquals("2C", cards(it.column(0)))
            assertEquals(0, it.tabDown[0])
            assertTrue(it.isFaceUp(0, 0))
        }
    }

    @Test
    fun brokenRunsDoNotMoveTogether() {
        // Not reachable in play, but the generator must still refuse to move a broken run.
        val s = pos("t1: 9H 8H\nt2: TS")
        assertEquals(emptyList<Move>(), Rules.legalMoves(s, d1).filterIsInstance<TableauToTableau>())
    }

    @Test
    fun cardsCanComeBackFromTheFoundation() {
        val s = pos(
            """
            found: 0 0 3 0
            t1: 4S
            t2: 4H
            """,
        )
        val back = Rules.legalMoves(s, d1).filterIsInstance<FoundationToTableau>()
        assertEquals(listOf(FoundationToTableau(Card.HEARTS, 0)), back)
        applyAndUndo(s, d1, FoundationToTableau(Card.HEARTS, 0)) {
            assertEquals(2, it.found[Card.HEARTS])
            assertEquals("4S 3H", cards(it.column(0)))
        }
        // A King can come down from the foundation into an empty column.
        val k = pos("found: 13 0 0 0")
        applyAndUndo(k, d1, FoundationToTableau(Card.CLUBS, 6)) { assertEquals("KC", cards(it.column(6))) }
    }

    @Test
    fun winDetection() {
        assertTrue(Rules.isWon(pos("found: 13 13 13 13")))
        assertFalse(Rules.isWon(pos("found: 13 13 13 12\nt1: KS")))
        val almost = pos("found: 13 13 13 12\nt1: KS")
        assertTrue(Rules.isWon(Rules.apply(almost, d1, TableauToFoundation(0))))
    }

    @Test
    fun illegalMovesAreRejected() {
        val s = pos("stock: 2H\nt1: 5S")
        assertThrows<IllegalArgumentException> { Rules.apply(s, d1, Recycle) }
        assertThrows<IllegalArgumentException> { Rules.apply(s, d1, TableauToTableau(0, 1, 1)) }
        assertThrows<IllegalArgumentException> { Rules.apply(s, d1, WasteToFoundation) }
        assertThrows<IllegalArgumentException> { Rules.apply(s, d1, TableauToTableau(0, 2, 3)) }
    }

    @Test
    fun applyDoesNotChangeTheOriginalState() {
        val s = pos("stock: 2H 3C\nt1: 4D")
        val before = s.copy()
        Rules.apply(s, d1, Draw)
        assertEquals(before, s)
    }

    @Test
    fun moveCodesRoundTrip() {
        val all = buildList {
            add(Draw); add(Recycle); add(WasteToFoundation)
            for (c in 0..6) { add(WasteToTableau(c)); add(TableauToFoundation(c)) }
            for (f in 0..6) for (t in 0..6) for (n in 1..13) if (f != t) add(TableauToTableau(f, n, t))
            for (s in 0..3) for (c in 0..6) add(FoundationToTableau(s, c))
        }
        assertEquals(all.size, all.map { it.code }.toSet().size, "codes are unique")
        for (m in all) {
            assertTrue(m.code in 0..Move.CODE_MASK)
            assertEquals(m, Move.decode(m.code))
        }
        assertEquals(all, Move.decodeAll(Move.encodeAll(all)))
        assertThrows<IllegalArgumentException> { Move.decode(7) }
    }

    @Test
    fun stateFormatRoundTrips() {
        val s = pos(
            """
            stock: 2H 3C
            waste: 5S
            found: 3 0 0 1
            t1: 5D 2D | KH QS
            t4: 9C
            recycles: 2
            """,
        )
        assertEquals(s, StateFormat.parse(s.toString()))
    }

    @Test
    fun validatorCatchesBrokenStates() {
        assertTrue(StateValidator.problem(pos("found: 13 13 13 13")) == null)
        assertTrue(StateValidator.problem(pos("found: 13 13 13 12"))!!.startsWith("Missing"))
        assertTrue(StateValidator.problem(pos("found: 13 13 13 12\nt1: KS\nt2: KS"))!!.startsWith("Duplicate"))
        assertTrue(StateValidator.problem(pos("found: 13 13 13 11\nt1: QS KS"))!!.contains("broken run"))
        assertTrue(StateValidator.problem(pos("found: 13 13 13 12\nt1: KS |"))!!.contains("face-down top"))
    }
}
