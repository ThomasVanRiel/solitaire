package io.github.thomasvanriel.solitaire.engine.hint

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.model.StateFormat
import io.github.thomasvanriel.solitaire.engine.pack.DealPack
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MoveAdvisorTest {
    private val d1 = RuleSet.DRAW_1_UNLIMITED
    private val spider = RuleSet.SPIDER_1_SUIT

    private fun pos(text: String, rs: RuleSet = d1): GameState = StateFormat.parse(text.trimIndent(), rs)

    @Test
    fun hintsAMoveThatTurnsACardOver() {
        val s = pos(
            """
            t1: 5S | 9H
            t2: 4D | 8C
            """,
        )
        assertEquals(Advice.Hint(Move.TableauToTableau(1, 1, 0)), MoveAdvisor.advise(s, d1))
    }

    @Test
    fun hintsTheStockWhenTheHelpfulCardIsInIt() {
        // AH is the second card drawn; nothing else can move.
        val s = pos(
            """
            stock: AH 3D
            t1: 5S | 9H
            t2: 4D | 9C
            """,
        )
        assertEquals(Advice.Hint(Move.Draw), MoveAdvisor.advise(s, d1))
    }

    @Test
    fun hintsTheFirstStepWhenACardMustMakeRoomFirst() {
        // 6H needs 7C, which is covered by 6D; 6D can go up to its foundation (that alone doesn't help).
        val s = pos(
            """
            found: 0 5 0 0
            t1: 5C | 6H
            t2: 7C 6D
            """,
        )
        assertEquals(Advice.Hint(Move.TableauToFoundation(1)), MoveAdvisor.advise(s, d1))
    }

    @Test
    fun klondikeIsStuckWhenOnlyCyclingTheStockIsLeft() {
        val s = pos(
            """
            stock: 2H 3D
            t1: 5S | 9H
            t2: 4D | 9C
            """,
        )
        assertEquals(Advice.Stuck, MoveAdvisor.advise(s, d1))
        assertEquals(Advice.Stuck, MoveAdvisor.advise(s, RuleSet.DRAW_3_UNLIMITED))
    }

    @Test
    fun spiderDealsWhenNothingElseHelpsAndIsStuckWithoutAStock() {
        val columns = (1..10).joinToString("\n") { "t$it: 5S" }
        assertEquals(Advice.Hint(Move.Draw), MoveAdvisor.advise(pos("stock: " + "AS ".repeat(10) + "\n" + columns, spider), spider))
        assertEquals(Advice.Stuck, MoveAdvisor.advise(pos(columns, spider), spider))
    }

    @Test
    fun wonIsWon() {
        assertEquals(Advice.Won, MoveAdvisor.advise(pos("found: 13 13 13 13"), d1))
    }

    /** The Yukon position that once got a pointless hint (Ace down from its foundation, then back up). */
    @Test
    fun yukonFoundationCardsStayUp() {
        // The Ace of spades could rest on the 2 of diamonds, but it stays up: nothing helps any more.
        val s = pos(
            """
            found: 0 0 0 1
            t1: 3C | 2D
            t2: 4C | 9H
            """,
            RuleSet.YUKON,
        )
        assertFalse(Rules.isLegal(s, RuleSet.YUKON, Move.FoundationToTableau(3, 0)))
        assertEquals(Advice.Stuck, MoveAdvisor.advise(s, RuleSet.YUKON))
    }

    /**
     * Over many random positions of every game, a hint is always a legal move, and playing it and then going
     * on (breadth first, without the search's tricks) really does reach progress beyond the position it was
     * given for: never just a card taken down and put back up.
     */
    @Test
    fun hintsLeadToRealProgress() {
        val rng = io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256.seeded(2025)
        var hints = 0
        for (rs in RuleSet.ALL) {
            for (g in 0 until 12) {
                var s = Deals.deal(g.toLong(), rs)
                repeat(40) {
                    val advice = MoveAdvisor.advise(s, rs)
                    if (advice is Advice.Hint) {
                        hints++
                        assertTrue(Rules.isLegal(s, rs, advice.move), "$rs: illegal hint ${advice.move}")
                        val next = Rules.apply(s, rs, advice.move)
                        assertTrue(
                            makesProgress(s, next, rs) || MoveAdvisor.helpfulMove(next, rs, baseline = s) != null ||
                                MoveAdvisor.advise(next, rs) !is Advice.Stuck,
                            "$rs: hint ${advice.move} leads nowhere from\n$s",
                        )
                        assertFalse(advice.move is Move.FoundationToTableau, "$rs: foundation cards never come back")
                    }
                    val moves = Rules.legalMoves(s, rs)
                    if (moves.isEmpty()) return@repeat
                    s = Rules.apply(s, rs, moves[rng.nextInt(moves.size)])
                }
            }
        }
        assertTrue(hints > 500, "only $hints hints checked")
    }

    private fun makesProgress(before: GameState, after: GameState, rs: RuleSet): Boolean {
        fun down(s: GameState) = (0 until s.columns).sumOf { s.tabDown[it] }
        return down(after) < down(before) || after.stockSize + after.wasteSize < before.stockSize + before.wasteSize ||
            (!rs.hasStock || !rs.buildsFoundations) && after.foundationCount > before.foundationCount
    }

    /**
     * Every position on a stored winning line can still be won, so it must never be called stuck, and every
     * hint must be a legal move.
     */
    @Test
    fun neverStuckOnAWinningLine() {
        val dir = File(System.getProperty("packs.dir") ?: error("packs.dir not set"))
        for (rs in RuleSet.ALL) {
            val pack = File(dir, DealPack.fileName(rs, Tier.EXPERT)).inputStream().use { DealPack.read(it) }
            for (entry in pack.entries.take(2)) {
                var s = Deals.deal(entry.seed, rs, pack.shuffleVersion)
                for (move in pack.verifiedSolution(entry)!!) {
                    val advice = MoveAdvisor.advise(s, rs)
                    assertNotEquals(Advice.Stuck, advice, "$rs #${entry.seed}: stuck on a winning line at\n$s")
                    if (advice is Advice.Hint) assertTrue(Rules.isLegal(s, rs, advice.move), "$rs: illegal hint ${advice.move}")
                    s = Rules.apply(s, rs, move)
                }
            }
        }
    }
}
