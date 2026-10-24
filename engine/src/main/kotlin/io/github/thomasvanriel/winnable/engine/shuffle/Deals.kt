package io.github.thomasvanriel.winnable.engine.shuffle

import io.github.thomasvanriel.winnable.engine.model.GameState

/**
 * Seed → deal. The algorithm is versioned: once released, [SHUFFLE_V1] must never change,
 * because deal packs and shared deal numbers depend on it. Golden tests pin it.
 */
object Deals {
    const val SHUFFLE_V1 = 1
    const val CURRENT_SHUFFLE = SHUFFLE_V1

    /** The shuffled deck for [seed]: a Fisher–Yates shuffle of 0..51. */
    fun deck(seed: Long, shuffleVersion: Int = CURRENT_SHUFFLE): IntArray {
        require(shuffleVersion == SHUFFLE_V1) { "Unknown shuffle version $shuffleVersion" }
        val rng = Xoshiro256.seeded(seed)
        val d = IntArray(52) { it }
        for (i in 51 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = d[i]; d[i] = d[j]; d[j] = t
        }
        return d
    }

    /**
     * The starting position for [seed]. Cards are dealt from the front of the deck, row by row:
     * row r puts one card on each column r..6, so column c gets c+1 cards with the top one face up.
     * The other 24 cards form the stock, with deck[51] on top.
     */
    fun deal(seed: Long, shuffleVersion: Int = CURRENT_SHUFFLE): GameState {
        val d = deck(seed, shuffleVersion)
        val s = GameState()
        var k = 0
        for (row in 0 until 7) {
            for (c in row until 7) s.tab[c][s.tabSize[c]++] = d[k++]
        }
        for (c in 0 until 7) s.tabDown[c] = c
        while (k < 52) s.stock[s.stockSize++] = d[k++]
        return s
    }
}
