package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.difficulty.OpenGamePlayer
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.Move.CellToFoundation
import io.github.thomasvanriel.solitaire.engine.model.Move.CellToTableau
import io.github.thomasvanriel.solitaire.engine.model.Move.FoundationToTableau
import io.github.thomasvanriel.solitaire.engine.model.Move.TableauToCell
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Yukon, Russian Solitaire and FreeCell: deals, rules, apply/undo, the finish, the solver and the fair player. */
class OpenGamesTest {
    private val yu = RuleSet.YUKON
    private val ru = RuleSet.RUSSIAN
    private val fc = RuleSet.FREECELL
    private val openGames = listOf(yu, ru, fc)

    private fun pos(text: String, rs: RuleSet) =
        StateFormat.parse(text.trimIndent(), rs).also { assertNull(StateValidator.problem(it, rs), it.toString()) }

    /**
     * A small hand-built position: each foundation holds its suit up to the lowest card of that suit on the
     * board. (Higher cards of the suit that aren't listed are simply left out, as in the other rules tests.)
     */
    private fun withFoundations(text: String, rs: RuleSet): GameState {
        val s = StateFormat.parse(text.trimIndent(), rs)
        val onBoard = HashSet<Int>()
        for (c in 0 until s.columns) onBoard += s.column(c)
        for (card in s.cells) if (card >= 0) onBoard += card
        // Each foundation takes its suit up to (not including) the lowest card of that suit on the board.
        for (suit in 0 until 4) s.found[suit] = (0 until 13).firstOrNull { Card.of(suit, it) in onBoard } ?: 13
        return s
    }

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
    fun yukonDealLayout() {
        for (rs in listOf(yu, ru)) {
            val s = Deals.deal(5, rs)
            assertEquals(7, s.columns)
            assertEquals(listOf(1, 6, 7, 8, 9, 10, 11), s.tabSize.toList())
            assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), s.tabDown.toList())
            assertEquals(0, s.stockSize)
            assertEquals(0, s.cells.size)
            assertNull(StateValidator.problem(s, rs))
        }
        // The first 28 cards go out as in Klondike, so the face-down cards are Klondike's.
        val k = Deals.deal(5, RuleSet.DRAW_1_UNLIMITED)
        val y = Deals.deal(5, yu)
        for (c in 0 until 7) for (i in 0 until c) assertEquals(k.tab[c][i], y.tab[c][i])
    }

    @Test
    fun freeCellDealLayout() {
        val s = Deals.deal(5, fc)
        assertEquals(8, s.columns)
        assertEquals(listOf(7, 7, 7, 7, 6, 6, 6, 6), s.tabSize.toList())
        assertTrue(s.tabDown.all { it == 0 })
        assertEquals(listOf(-1, -1, -1, -1), s.cells.toList())
        assertEquals(0, s.stockSize)
        assertNull(StateValidator.problem(s, fc))
        // Row by row: the deck's first card heads column 1, its ninth is the second card there.
        val d = Deals.deck(5)
        assertEquals(d[0], s.tab[0][0])
        assertEquals(d[8], s.tab[0][1])
        assertEquals(d[51], s.tab[3][6])
    }

    /** Pins the layouts: deal numbers in shipped packs depend on them. */
    @Test
    fun goldenDeals() {
        val f = Deals.deal(1, fc)
        assertEquals("4H KH 5H 9H 3D 3C 5D", col(f, 0))
        assertEquals("6D 9D 9C 5C 2S TS", col(f, 7))
        val y = Deals.deal(1, yu)
        assertEquals("4H", col(y, 0))
        assertEquals("5S 6D JC 8C 3C QC", col(y, 1))
        assertEquals("TD 3H AD 7H 9H KD JH JD 2S TC 8S", col(y, 6))
        assertEquals(6, y.tabDown[6])
        assertEquals(y.column(6), Deals.deal(1, ru).column(6))
    }

    // ------------------------------------------------------------------ Yukon

    @Test
    fun yukonMovesAnyFaceUpCardWithEverythingOnIt() {
        val s = withFoundations(
            """
            t1: 5S | 9H 2C KD 4S
            t2: TC
            t3: 5C
            """,
            yu,
        )
        // 9H (with 2C KD 4S on it, not in order) goes onto TC.
        applyAndUndo(s, yu, TableauToTableau(0, 4, 1)) { after ->
            assertEquals("TC 9H 2C KD 4S", col(after, 1))
            assertEquals("5S", col(after, 0))
            assertEquals(0, after.tabDown[0], "the card under it turns over")
        }
        // 4S onto 5C: same colour, so no.
        assertFalse(Rules.isLegal(s, yu, TableauToTableau(0, 1, 2)))
        // Only Kings go into an empty column: KD with 4S moves, 9H doesn't.
        val e = withFoundations(
            """
            t1: 5S | 9H 2C KD 4S
            t2: TC
            """,
            yu,
        )
        assertTrue(Rules.isLegal(e, yu, TableauToTableau(0, 2, 2)))
        assertFalse(Rules.isLegal(e, yu, TableauToTableau(0, 4, 2)))
    }

    @Test
    fun russianBuildsInSuit() {
        val s = withFoundations(
            """
            t1: 9H 4C
            t2: TH
            t3: TS
            """,
            ru,
        )
        assertTrue(Rules.isLegal(s, ru, TableauToTableau(0, 2, 1)), "9H onto TH")
        assertFalse(Rules.isLegal(s, ru, TableauToTableau(0, 2, 2)), "9H not onto TS in Russian")
        assertTrue(Rules.isLegal(s, yu, TableauToTableau(0, 2, 2)), "but in Yukon it may")
        assertFalse(Rules.isLegal(s, yu, TableauToTableau(0, 2, 1)), "and in Yukon not onto TH")
    }

    @Test
    fun yukonFoundationsGoBothWays() {
        val s = withFoundations(
            """
            t1: 2H
            t2: 4C
            t3: 3S
            t4: 3D
            """,
            yu,
        )
        assertEquals(1, s.found[Card.HEARTS])
        assertEquals(2, s.found[Card.DIAMONDS])
        applyAndUndo(s, yu, TableauToFoundation(0)) { assertEquals(2, it.found[Card.HEARTS]) }
        // The 2 of diamonds is on its foundation and may come back down onto the 3 of spades.
        assertTrue(Rules.isLegal(s, yu, FoundationToTableau(Card.DIAMONDS, 2)))
        applyAndUndo(s, yu, FoundationToTableau(Card.DIAMONDS, 2)) { assertEquals("3S 2D", col(it, 2)) }
        assertFalse(Rules.legalMoves(s, yu).any { it is TableauToCell })
    }

    // ------------------------------------------------------------------ FreeCell

    @Test
    fun freeCellCellsHoldOneCardEach() {
        val s = withFoundations(
            """
            t1: 9H 4C
            t2: TS
            cells: - AH - -
            """,
            fc,
        )
        // Tableau to cell goes into the first empty cell.
        applyAndUndo(s, fc, TableauToCell(0)) { after ->
            assertEquals(listOf(Card.parse("4C"), Card.parse("AH"), -1, -1), after.cells.toList())
        }
        applyAndUndo(s, fc, CellToFoundation(1)) { after ->
            assertEquals(-1, after.cells[1])
            assertEquals(1, after.found[Card.HEARTS])
        }
        applyAndUndo(s, fc, CellToTableau(1, 2)) { after -> assertEquals("AH", col(after, 2)) }
        // Any card goes into an empty column; no foundation card ever comes back.
        assertTrue(Rules.isLegal(s, fc, TableauToTableau(0, 1, 3)))
        assertFalse(Rules.legalMoves(s, fc).any { it is FoundationToTableau })
        // With every cell full, nothing more goes in.
        val full = withFoundations(
            """
            t1: 9H 4C
            cells: AH 2S 3D 5C
            """,
            fc,
        )
        assertFalse(Rules.legalMoves(full, fc).any { it is TableauToCell })
        assertEquals(0, full.freeCells())
    }

    @Test
    fun freeCellRunsNeedRoom() {
        // A run of 4 (8S 7H 6C 5D) onto 9D: room for (free cells + 1) × 2^(empty columns) cards.
        fun position(cells: String, emptyColumns: Int): GameState {
            val filler = listOf("KC", "KS", "KH", "QC", "QS", "QH").drop(emptyColumns).take(6 - emptyColumns)
            val lines = filler.mapIndexed { i, card -> "t${i + 3}: $card" }.joinToString("\n")
            return withFoundations("t1: 8S 7H 6C 5D\nt2: 9D\n$lines\ncells: $cells", fc)
        }
        val run = TableauToTableau(0, 4, 1)
        assertTrue(Rules.isLegal(position("- - - -", 0), fc, run), "4 free cells: room for 5")
        assertTrue(Rules.isLegal(position("JC JS - -", 1), fc, run), "2 free cells, 1 empty column: room for 6")
        assertFalse(Rules.isLegal(position("JC JS JH -", 0), fc, run), "1 free cell: room for 2")
        assertTrue(Rules.isLegal(position("JC JS JH -", 1), fc, run), "1 free cell, 1 empty column: room for 4")
        // Into an empty column, that column doesn't count as room.
        val s = position("JC JS JH -", 1)
        assertEquals(0, s.tabSize[7])
        assertEquals(4, Rules.maxRunMove(s, 1))
        assertEquals(2, Rules.maxRunMove(s, 7))
        assertTrue(Rules.isLegal(s, fc, TableauToTableau(0, 2, 7)))
        assertFalse(Rules.isLegal(s, fc, TableauToTableau(0, 3, 7)))
    }

    @Test
    fun wonWhenCellsAreEmptyToo() {
        val s = withFoundations("cells: KH - - -", fc)
        assertFalse(Rules.isWon(s))
        assertTrue(Rules.isWon(Rules.apply(s, fc, CellToFoundation(0))))
    }

    // ------------------------------------------------------------------ the finish

    @Test
    fun finishesOrderedColumns() {
        val s = withFoundations(
            """
            t1: KH QS JH
            t2: KC QD
            t3: KS
            t4: KD QC JD
            cells: QH JC JS -
            """,
            fc,
        )
        assertTrue(Endgame.canFinish(s, fc))
        var g = s
        while (!Rules.isWon(g)) {
            val m = Endgame.nextMove(g) ?: error("finish got stuck at\n$g")
            g = Rules.apply(g, fc, m)
        }
        val messy = withFoundations("t1: KH QS JH\nt2: QD KC", fc)
        assertFalse(Endgame.canFinish(messy, fc))
        val hidden = withFoundations("t1: KH | QS", yu)
        assertFalse(Endgame.canFinish(hidden, yu))
        assertFalse(Endgame.canFinish(Deals.deal(3, RuleSet.SPIDER_1_SUIT), RuleSet.SPIDER_1_SUIT))
    }

    // ------------------------------------------------------------------ fuzz

    @Test
    fun randomGamesKeepInvariantsAndUndoExactly() {
        val rng = Xoshiro256.seeded(20261026)
        val buf = IntArray(Rules.MAX_MOVES)
        val seen = HashSet<Pair<Int, Int>>()
        var totalMoves = 0L
        for (game in 0 until 1_500) {
            val rs = openGames[game % openGames.size]
            val start = Deals.deal(game.toLong(), rs)
            val s = start.copy()
            val tokens = IntArray(300)
            var n = 0
            while (n < tokens.size) {
                val count = Rules.legalMoveCodes(s, rs, buf)
                if (count == 0) break
                val code = buf[rng.nextInt(count)]
                val check = if (n % 29 == 0) Rules.apply(s, rs, Move.decode(code)) else null
                tokens[n++] = Rules.applyCode(s, rs, code)
                seen += Move.type(code) to (if (Move.type(code) == Move.TYPE_CELL) Move.c(code) else 0)
                if (check != null) assertEquals(check, s)
                val problem = StateValidator.problem(s, rs)
                assertNull(problem, "$rs game $game move $n (${Move.decode(code)}): $problem\n$s")
                if (Rules.isWon(s)) break
            }
            totalMoves += n
            for (i in n - 1 downTo 0) Rules.undoCode(s, rs, tokens[i])
            assertEquals(start, s, "$rs game $game did not undo back to the start")
        }
        assertTrue(totalMoves > 200_000, "fuzz should play plenty of moves, played $totalMoves")
        for (kind in listOf(Move.CELL_IN, Move.CELL_OUT, Move.CELL_UP)) assertTrue(Move.TYPE_CELL to kind in seen, "cell move $kind")
        assertTrue(Move.TYPE_F2T to 0 in seen && Move.TYPE_T2T to 0 in seen && Move.TYPE_T2F to 0 in seen)
    }

    @Test
    fun cellMoveCodesRoundTrip() {
        for (m in listOf(TableauToCell(7), CellToTableau(3, 7), CellToFoundation(2), CellToTableau(0, 0))) {
            assertEquals(m, Move.decode(m.code))
            assertEquals(listOf(m), Move.decodeAll(Move.encodeAll(listOf(m))))
        }
    }

    // ------------------------------------------------------------------ solver and player

    @Test
    fun solverFindsVerifiedWins() {
        for (rs in openGames) {
            var solved = 0
            var seed = 0L
            val solver = DealSolver.create(rs, maxNodes = 300_000)
            while (solved < 5 && seed < 400) {
                val start = Deals.deal(seed, rs)
                val r = solver.solve(start)
                if (r is SolveResult.Solved) {
                    assertTrue(ProofVerifier.verify(seed, rs, r.moves), "$rs seed $seed: proof failed")
                    val bytes = SolutionCodec.encode(start, rs, r.moves)
                    assertEquals(r.moves, SolutionCodec.decode(start, rs, bytes))
                    solved++
                }
                seed++
            }
            assertEquals(5, solved, "$rs: solver should find wins")
        }
    }

    @Test
    fun solverProvesTinyPositionsUnsolvable() {
        val s = withFoundations("t1: KS | 2H AH", ru)
        val solved = DealSolver.create(ru).solve(s)
        assertTrue(solved is SolveResult.Solved && ProofVerifier.verifyFrom(s, ru, solved.moves), "AH, 2H, then KS go up")
        // Russian: the 2 of hearts covers the Ace, and only the 3 of hearts (already up? no: not dealt) could take it.
        val stuck = withFoundations("t1: AH 2H", ru)
        assertTrue(DealSolver.create(ru).solve(stuck) is SolveResult.Unsolvable)
    }

    /** The player never peeks: hiding different cards under the same face-up cards gives the same choices. */
    @Test
    fun playerDecisionsDependOnlyOnVisibleCards() {
        for (seed in 0L until 40L) {
            val s = Deals.deal(seed, yu)
            val other = s.copy()
            val hidden = ArrayList<Int>()
            for (c in 0 until 7) for (i in 0 until other.tabDown[c]) hidden += other.tab[c][i]
            hidden.shuffle(java.util.Random(seed))
            var k = 0
            for (c in 0 until 7) for (i in 0 until other.tabDown[c]) other.tab[c][i] = hidden[k++]
            val a = OpenGamePlayer(yu, Xoshiro256.seeded(seed)).chooseMove(s)
            val b = OpenGamePlayer(yu, Xoshiro256.seeded(seed)).chooseMove(other)
            assertEquals(a, b, "seed $seed")
        }
    }
}
