package io.github.thomasvanriel.solitaire.engine.shuffle

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/**
 * Seed → deal. The algorithm is versioned: once released, [SHUFFLE_V1] must never change,
 * because deal packs and shared deal numbers depend on it. Golden tests pin it.
 */
object Deals {
    const val SHUFFLE_V1 = 1
    const val CURRENT_SHUFFLE = SHUFFLE_V1

    /** The cards of [ruleSet]'s deck before shuffling. Spider's two decks repeat cards; with fewer suits, more so. */
    fun unshuffledDeck(ruleSet: RuleSet): IntArray = when (ruleSet.game) {
        GameType.SPIDER -> {
            // 1 suit: eight sets of spades. 2 suits: four of spades, four of hearts. 4 suits: two full decks.
            val suits = when (ruleSet.suits) {
                1 -> intArrayOf(Card.SPADES)
                2 -> intArrayOf(Card.SPADES, Card.HEARTS)
                4 -> intArrayOf(Card.CLUBS, Card.DIAMONDS, Card.HEARTS, Card.SPADES)
                else -> throw IllegalArgumentException("Spider needs 1, 2 or 4 suits")
            }
            IntArray(104) { Card.of(suits[(it / 13) % suits.size], it % 13) }
        }
        else -> IntArray(52) { it }
    }

    /** The shuffled Klondike deck for [seed]: a Fisher–Yates shuffle of 0..51. */
    fun deck(seed: Long, shuffleVersion: Int = CURRENT_SHUFFLE): IntArray = shuffle(IntArray(52) { it }, seed, shuffleVersion)

    /** The shuffled deck of [ruleSet] for [seed]. For one deck this is exactly [deck]. */
    fun deck(seed: Long, ruleSet: RuleSet, shuffleVersion: Int = CURRENT_SHUFFLE): IntArray =
        shuffle(unshuffledDeck(ruleSet), seed, shuffleVersion)

    private fun shuffle(d: IntArray, seed: Long, shuffleVersion: Int): IntArray {
        require(shuffleVersion == SHUFFLE_V1) { "Unknown shuffle version $shuffleVersion" }
        val rng = Xoshiro256.seeded(seed)
        for (i in d.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = d[i]; d[i] = d[j]; d[j] = t
        }
        return d
    }

    /** The Klondike starting position for [seed]. See [deal] with a rule set. */
    fun deal(seed: Long, shuffleVersion: Int = CURRENT_SHUFFLE): GameState = deal(seed, RuleSet.DRAW_1_UNLIMITED, shuffleVersion)

    /**
     * The starting position of [ruleSet] for [seed]. Cards are dealt from the front of the deck; the rest
     * form the stock, with the last card of the deck on top.
     * - Klondike: row by row, row r puts one card on each column r..6, so column c gets c+1 cards with the
     *   top one face up. 24 cards are left in the stock.
     * - Spider: 54 cards round the 10 columns, so columns 1-4 get 6 and columns 5-10 get 5, top card face
     *   up. 50 cards (five deals) are left in the stock.
     * - Scorpion II: 7 rows of 7. Columns 1-3 have their lowest 3 cards face down. 3 cards are left in the stock.
     */
    fun deal(seed: Long, ruleSet: RuleSet, shuffleVersion: Int = CURRENT_SHUFFLE): GameState {
        val d = deck(seed, ruleSet, shuffleVersion)
        val s = GameState.forRules(ruleSet)
        var k = 0
        when (ruleSet.game) {
            GameType.KLONDIKE -> {
                for (row in 0 until 7) {
                    for (c in row until 7) s.tab[c][s.tabSize[c]++] = d[k++]
                }
                for (c in 0 until 7) s.tabDown[c] = c
            }
            GameType.SPIDER -> {
                while (k < 54) {
                    val c = k % 10
                    s.tab[c][s.tabSize[c]++] = d[k++]
                }
                for (c in 0 until 10) s.tabDown[c] = s.tabSize[c] - 1
            }
            GameType.SCORPION -> {
                for (row in 0 until 7) {
                    for (c in 0 until 7) s.tab[c][s.tabSize[c]++] = d[k++]
                }
                for (c in 0 until 3) s.tabDown[c] = 3
            }
        }
        while (k < d.size) s.stock[s.stockSize++] = d[k++]
        return s
    }
}
