package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/**
 * When a game can finish itself: in the games with foundations built card by card (Klondike, Yukon,
 * FreeCell), once the stock and waste are empty, nothing is face down and every column is in order (each
 * card may rest on the one under it). Then the lowest card left is always on top of a column or in a free
 * cell, and it always fits its foundation, so playing it up each time wins.
 */
object Endgame {
    fun canFinish(s: GameState, rs: RuleSet): Boolean {
        if (!rs.buildsFoundations || s.stockSize != 0 || s.wasteSize != 0) return false
        for (c in 0 until s.columns) {
            if (s.tabDown[c] != 0) return false
            val col = s.tab[c]
            for (i in 1 until s.tabSize[c]) if (!Rules.stacks(rs, col[i], col[i - 1])) return false
        }
        return true
    }

    /** The next move of the finish: the lowest card that fits its foundation, or null if there is none. */
    fun nextMove(s: GameState): Move? {
        var best: Move? = null
        var bestRank = Int.MAX_VALUE
        for (c in 0 until s.columns) {
            val top = s.columnTop(c)
            if (top >= 0 && Rules.fitsOnFoundation(s, top) && Card.rank(top) < bestRank) {
                best = Move.TableauToFoundation(c)
                bestRank = Card.rank(top)
            }
        }
        for (k in s.cells.indices) {
            val card = s.cells[k]
            if (card >= 0 && Rules.fitsOnFoundation(s, card) && Card.rank(card) < bestRank) {
                best = Move.CellToFoundation(k)
                bestRank = Card.rank(card)
            }
        }
        return best
    }
}
