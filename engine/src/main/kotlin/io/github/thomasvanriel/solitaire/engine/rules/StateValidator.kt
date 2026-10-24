package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals

/** Checks the invariants every reachable position must satisfy. Used by tests and debug builds. */
object StateValidator {
    /** Returns null if [s] is valid for [ruleSet], otherwise a description of the first problem found. */
    fun problem(s: GameState, ruleSet: RuleSet = RuleSet.DRAW_1_UNLIMITED): String? {
        if (s.columns != ruleSet.columns) return "Expected ${ruleSet.columns} columns, found ${s.columns}"
        // How many more of each card may still be seen.
        val left = IntArray(52)
        for (card in Deals.unshuffledDeck(ruleSet)) left[card]++
        fun mark(card: Int, where: String): String? {
            if (card !in 0..51) return "Bad card $card in $where"
            if (left[card] == 0) return "Duplicate ${Card.code(card)} in $where"
            left[card]--
            return null
        }
        val klondike = ruleSet.game == GameType.KLONDIKE
        for (suit in 0 until 4) {
            val f = s.found[suit]
            if (f < 0 || (klondike && f > 13) || (!klondike && f % 13 != 0)) return "Foundation $suit has $f cards"
            for (k in 0 until f) mark(Card.of(suit, k % 13), "foundation")?.let { return it }
        }
        for (i in 0 until s.stockSize) mark(s.stock[i], "stock")?.let { return it }
        for (i in 0 until s.wasteSize) mark(s.waste[i], "waste")?.let { return it }
        if (!klondike && (s.wasteSize != 0 || s.recycles != 0)) return "${ruleSet.game.label} has no waste"
        for (c in 0 until s.columns) {
            val size = s.tabSize[c]
            val down = s.tabDown[c]
            if (down < 0 || down > size) return "Column ${c + 1} has $down face-down cards of $size"
            if (size > 0 && down == size) return "Column ${c + 1} has a face-down top card"
            for (i in 0 until size) mark(s.tab[c][i], "column ${c + 1}")?.let { return it }
            // Only Klondike keeps every face-up run in order: in Spider and Scorpion a deal can land on anything.
            if (klondike) {
                for (i in down + 1 until size) {
                    if (!Card.stacksOn(s.tab[c][i], s.tab[c][i - 1])) {
                        return "Column ${c + 1} has a broken run at ${Card.code(s.tab[c][i])}"
                    }
                }
            }
        }
        val missing = (0 until 52).filter { left[it] > 0 }
        if (missing.isNotEmpty()) return "Missing cards: ${missing.joinToString(" ") { Card.code(it) }}"
        if (s.recycles < 0) return "Negative recycle count"
        return null
    }
}
