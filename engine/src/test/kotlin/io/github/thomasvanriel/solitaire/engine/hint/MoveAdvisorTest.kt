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
