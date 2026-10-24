package io.github.thomasvanriel.winnable.engine.shuffle

import io.github.thomasvanriel.winnable.engine.model.Card
import io.github.thomasvanriel.winnable.engine.model.GameState
import io.github.thomasvanriel.winnable.engine.rules.StateValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins SHUFFLE_V1. If any of these fail, the shuffle changed and every deal pack and shared deal number
 * would silently point at different deals. Do not "fix" these numbers: add a new shuffle version instead.
 */
class ShuffleGoldenTest {
    @Test
    fun xoshiroMatchesReferenceImplementation() {
        val r = Xoshiro256.fromState(1, 2, 3, 4)
        assertEquals(listOf(11520uL, 0uL, 1509978240uL, 1215971899390074240uL), List(4) { r.nextLong().toULong() })
    }

    @Test
    fun splitMixMatchesReferenceImplementation() {
        assertEquals(0xe220a8397b1dcdafuL, SplitMix64(0).next().toULong())
    }

    private fun deckCodes(seed: Long) = Deals.deck(seed).joinToString(" ") { Card.code(it) }

    @Test
    fun goldenDecks() {
        assertEquals(
            "JS 9H TH 9D 8C 7D 8H 2D 7C AD 8D KD KH JD AC AS 6S 5H 6H 4D TS 3H 5D 7H KS QD 3C 2C 3D 2S 9C QC JH 4C KC 9S 5C QS 5S AH 6D 4H JC 7S 3S 6C TC QH 8S 4S TD 2H",
            deckCodes(0),
        )
        assertEquals(
            "4H 5S QD 7D 8H 6H TD 6D KH 8D JS 3S 3H AH AS 9D 5H AD 2D 7C KC 7H 2H 9C 9H 4S KD JH JC QS 6C 5C 3D JD 8C AC QH 6S KS 2S 3C 2C 7S 9S 4D TC QC TS 5D 4C TH 8S",
            deckCodes(1),
        )
        assertEquals(
            "8H 3D 9S QC TS 7H 2S 8S TD 8D 7S 2D JS KH 6H 3C QH 9H 2H 7C AH 5H AS JH 6S 5S 9D TC TH 7D AD 6D 9C 4D KS KD KC 5D 5C 8C 2C 4C QS 4H JC 3H 4S 3S QD AC JD 6C",
            deckCodes(48213),
        )
        assertEquals(
            "JC KH AS QD 6C TH 3D 7D 2S JH 9D AC 9S 4D 8D 4H 3H 6H TS 7S QC AH 4S 4C QH AD 8H JD 7C 6D 6S 8C 2D 9C 9H 5D 3C KD KC KS 2H TC 5H TD QS 5C 7H JS 5S 8S 3S 2C",
            deckCodes(-7),
        )
    }

    @Test
    fun goldenDealLayout() {
        val s = Deals.deal(1)
        // Row-by-row dealing: column 1 gets deck[0]; column 7 gets deck[6], deck[12], ..., deck[27].
        val lines = s.toString().lines()
        assertEquals("stock: " + Deals.deck(1).drop(28).joinToString(" ") { Card.code(it) }, lines[0])
        assertEquals("t1: 4H", lines[3])
        assertEquals("t2: 5S | 6D", lines[4])
        assertEquals("t7: TD 3H AD 7H 9H KD | JH", lines[9])
        assertEquals(24, s.stockSize)
        for (c in 0 until GameState.COLUMNS) {
            assertEquals(c + 1, s.tabSize[c])
            assertEquals(c, s.tabDown[c])
        }
        assertNull(StateValidator.problem(s))
    }

    @Test
    fun manySeedsGiveValidDistinctDeals() {
        val seen = HashSet<String>()
        for (seed in 0L until 2000L) {
            val s = Deals.deal(seed)
            assertNull(StateValidator.problem(s))
            seen += Deals.deck(seed).joinToString(",")
        }
        assertEquals(2000, seen.size)
    }
}
