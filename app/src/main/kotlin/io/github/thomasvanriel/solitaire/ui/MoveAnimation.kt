package io.github.thomasvanriel.solitaire.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules

/** A gentle start and a long, soft landing, so cards glide into place rather than snap. */
private val Glide = CubicBezierEasing(0.35f, 0f, 0.15f, 1f)

/** One card sliding from [from] to [to]. It starts moving at [delay] (a share of the whole animation). */
class Flight(val card: Int, val from: Offset, val to: Offset, val delay: Float = 0f) {
    /** Where the card is at animation progress [t] (0 to 1). */
    fun at(t: Float, maxDelay: Float): Offset {
        val span = 1f - maxDelay
        val local = ((t - delay) / span).coerceIn(0f, 1f)
        return lerp(from, to, Glide.transform(local))
    }
}

/**
 * What to leave out while the flying cards are still on their way: they are already in their new place in
 * the position being drawn, so they must not be drawn there twice.
 */
class Hidden(
    /** Per column, the index from which cards are left out (the column size if none are). */
    val columnFrom: IntArray,
    /** Top waste cards left out. */
    val wasteTop: Int = 0,
    /** Suit whose top foundation card is left out (Klondike, Scorpion), or -1. */
    val foundationSuit: Int = -1,
    /** Spider: which completed run (in drawing order) is left out, or -1. */
    val runIndex: Int = -1,
    /** FreeCell: the free cell whose card is left out, or -1. */
    val cell: Int = -1,
)

/** The animation of one move: the position after it, the cards in flight, and what to leave out meanwhile. */
class MoveAnimation(val after: GameState, val flights: List<Flight>, val hidden: Hidden, val durationMs: Int) {
    val maxDelay: Float = flights.maxOfOrNull { it.delay } ?: 0f

    companion object {
        /**
         * The animation for [move] from [before] to [after], or null if nothing should fly (a recycle).
         * [dropAt] is where the player let go of dragged cards, if this move was a drop (or where the cards
         * are now, if this move replaces the one before it).
         */
        fun of(
            before: GameState,
            after: GameState,
            move: Move,
            ruleSet: RuleSet,
            width: Float,
            height: Float,
            dropAt: Offset?,
            baseMs: Int,
            leftHanded: Boolean,
        ): MoveAnimation? {
            val lb = BoardLayout(width, height, before, ruleSet, leftHanded)
            val la = BoardLayout(width, height, after, ruleSet, leftHanded)
            val columnFrom = after.tabSize.copyOf()
            val flights = ArrayList<Flight>()
            var wasteTop = 0
            var foundationSuit = -1
            var runIndex = -1
            var cell = -1
            var duration = baseMs
            val klondike = ruleSet.game == GameType.KLONDIKE

            fun toColumnTop(card: Int, from: Offset, col: Int) {
                val i = after.tabSize[col] - 1
                flights += Flight(card, dropAt ?: from, la.cardOffset(col, i))
                columnFrom[col] = i
            }

            when (move) {
                Move.Recycle -> return null
                Move.Draw -> if (klondike) {
                    val k = after.wasteSize - before.wasteSize
                    for (j in 0 until k) {
                        val index = after.wasteSize - k + j
                        val to = la.wastePosition(index) ?: continue
                        flights += Flight(after.waste[index], lb.stockRect.topLeft, to, delay = j * 0.15f)
                    }
                    wasteTop = k
                } else {
                    // A deal: one card to each column, left to right.
                    val from = lb.stockPositions().lastOrNull() ?: lb.stockRect.topLeft
                    val k = before.stockSize - after.stockSize
                    for (c in 0 until k) {
                        val i = after.tabSize[c] - 1
                        flights += Flight(after.tab[c][i], from, la.cardOffset(c, i), delay = 0.5f * c / maxOf(1, k - 1))
                        columnFrom[c] = i
                    }
                    duration = baseMs * 2
                }
                is Move.WasteToTableau -> toColumnTop(before.wasteTop(), lb.wastePositions().last().second, move.col)
                Move.WasteToFoundation -> {
                    val card = before.wasteTop()
                    foundationSuit = Card.suit(card)
                    flights += Flight(card, dropAt ?: lb.wastePositions().last().second, la.foundationRect(foundationSuit).topLeft)
                }
                is Move.TableauToFoundation -> {
                    val col = move.col
                    val size = before.tabSize[col]
                    if (ruleSet.buildsFoundations) {
                        val card = before.columnTop(col)
                        foundationSuit = Card.suit(card)
                        flights += Flight(card, dropAt ?: lb.cardOffset(col, size - 1), la.foundationRect(foundationSuit).topLeft)
                    } else {
                        // A complete run leaves: the Ace goes first, the King last (and ends on top).
                        val suit = Card.suit(before.columnTop(col))
                        val to = if (ruleSet.game == GameType.SPIDER) {
                            runIndex = (0 until suit).sumOf { after.found[it] / 13 } + after.found[suit] / 13 - 1
                            la.completedRunOffset(runIndex)
                        } else {
                            foundationSuit = suit
                            la.foundationRect(suit).topLeft
                        }
                        for (j in Rules.RUN - 1 downTo 0) {
                            val i = size - Rules.RUN + j
                            flights += Flight(before.tab[col][i], lb.cardOffset(col, i), to, delay = 0.4f * (Rules.RUN - 1 - j) / (Rules.RUN - 1))
                        }
                        duration = baseMs * 2
                    }
                }
                is Move.TableauToTableau -> {
                    val start = after.tabSize[move.to] - move.count
                    for (j in 0 until move.count) {
                        val from = dropAt?.let { it + Offset(0f, j * la.dragStep) }
                            ?: lb.cardOffset(move.from, before.tabSize[move.from] - move.count + j)
                        flights += Flight(after.tab[move.to][start + j], from, la.cardOffset(move.to, start + j))
                    }
                    columnFrom[move.to] = start
                }
                is Move.FoundationToTableau ->
                    toColumnTop(Card.of(move.suit, before.found[move.suit] - 1), lb.foundationRect(move.suit).topLeft, move.col)
                is Move.TableauToCell -> {
                    val size = before.tabSize[move.col]
                    cell = before.firstFreeCell()
                    flights += Flight(before.columnTop(move.col), dropAt ?: lb.cardOffset(move.col, size - 1), la.cellRect(cell).topLeft)
                }
                is Move.CellToTableau -> toColumnTop(before.cells[move.cell], lb.cellRect(move.cell).topLeft, move.col)
                is Move.CellToFoundation -> {
                    val card = before.cells[move.cell]
                    foundationSuit = Card.suit(card)
                    flights += Flight(card, dropAt ?: lb.cellRect(move.cell).topLeft, la.foundationRect(foundationSuit).topLeft)
                }
            }
            if (flights.isEmpty()) return null
            return MoveAnimation(after, flights, Hidden(columnFrom, wasteTop, foundationSuit, runIndex, cell), duration)
        }
    }
}
