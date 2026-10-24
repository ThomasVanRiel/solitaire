package io.github.thomasvanriel.winnable.engine.rules

import io.github.thomasvanriel.winnable.engine.model.Card
import io.github.thomasvanriel.winnable.engine.model.GameState

/** Checks the invariants every reachable position must satisfy. Used by tests and debug builds. */
object StateValidator {
    /** Returns null if [s] is valid, otherwise a description of the first problem found. */
    fun problem(s: GameState): String? {
        val seen = BooleanArray(52)
        fun mark(card: Int, where: String): String? {
            if (card !in 0..51) return "Bad card $card in $where"
            if (seen[card]) return "Duplicate ${Card.code(card)} in $where"
            seen[card] = true
            return null
        }
        for (suit in 0 until 4) {
            if (s.found[suit] !in 0..13) return "Foundation $suit has ${s.found[suit]} cards"
            for (r in 0 until s.found[suit]) mark(Card.of(suit, r), "foundation")?.let { return it }
        }
        for (i in 0 until s.stockSize) mark(s.stock[i], "stock")?.let { return it }
        for (i in 0 until s.wasteSize) mark(s.waste[i], "waste")?.let { return it }
        for (c in 0 until GameState.COLUMNS) {
            val size = s.tabSize[c]
            val down = s.tabDown[c]
            if (down < 0 || down > size) return "Column ${c + 1} has $down face-down cards of $size"
            if (size > 0 && down == size) return "Column ${c + 1} has a face-down top card"
            for (i in 0 until size) mark(s.tab[c][i], "column ${c + 1}")?.let { return it }
            for (i in down + 1 until size) {
                if (!Card.stacksOn(s.tab[c][i], s.tab[c][i - 1])) {
                    return "Column ${c + 1} has a broken run at ${Card.code(s.tab[c][i])}"
                }
            }
        }
        val missing = (0 until 52).filter { !seen[it] }
        if (missing.isNotEmpty()) return "Missing cards: ${missing.joinToString(" ") { Card.code(it) }}"
        if (s.recycles < 0) return "Negative recycle count"
        return null
    }
}
