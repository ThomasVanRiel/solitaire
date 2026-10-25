package io.github.thomasvanriel.solitaire.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.game.Source
import io.github.thomasvanriel.solitaire.game.Target

/**
 * Where everything goes on the board, in pixels.
 *
 * **Portrait**: a top row with the foundations at the left and the stock at the far right (where the right
 * thumb is), and the columns below.
 * - Klondike: four foundations, the waste (fanned to the left for draw-3), the stock. Seven columns.
 * - Spider: completed runs fanned from the left, the remaining deals fanned left of the stock. Ten columns.
 * - Scorpion II: one slot per suit for its completed run, the stock. Seven columns.
 *
 * **Landscape** (wider than tall): the foundations go down the left edge (a 2×2 grid; Spider's completed
 * runs fan downwards) and the stock down the right edge (Klondike's waste under it, fanned downwards like a
 * column; Spider's deals fan downwards), with the columns in between using the full height.
 *
 * Column fans shrink so that a long column always fits.
 */
class BoardLayout(
    val width: Float,
    val height: Float,
    private val state: GameState,
    private val ruleSet: RuleSet,
) {
    val columns: Int = state.columns
    private val game = ruleSet.game

    /** Landscape: foundations and stock beside the columns instead of above them. */
    val sideways: Boolean = width > height

    /** Card-wide slots on the left and right of the columns (landscape only). */
    private val leftSlots = if (!sideways) 0 else if (game == GameType.SPIDER) 1 else 2
    private val rightSlots = if (!sideways) 0 else 1
    private val slotsAcross = leftSlots + columns + rightSlots

    /** Extra space between the side areas and the columns, in gaps. */
    private val sideGaps = if (sideways) 2 else 0

    /**
     * Card width: as wide as the slots allow, but small enough that about three card heights of tableau
     * (plus the top row in portrait) fit, so landscape and tablets don't overflow. The board is centred.
     */
    val cardWidth: Float = minOf(
        width / (slotsAcross + (slotsAcross + 1 + 2 * sideGaps) * GAP_RATIO),
        height / (ASPECT * if (sideways) 3.3f else 4.2f),
    )
    val gap: Float = cardWidth * GAP_RATIO
    val cardHeight: Float = cardWidth * ASPECT
    private val left: Float = (width - (slotsAcross * cardWidth + (slotsAcross + 1 + 2 * sideGaps) * gap)) / 2f
    val cardSize = Size(cardWidth, cardHeight)
    val corner: Float = cardWidth * 0.09f

    private val topY = gap
    val tableauTop: Float = if (sideways) topY else topY + cardHeight + gap * 3

    /** Left edge of slot [slot], counting from the left of the board (side areas included). */
    private fun slotX(slot: Int): Float {
        var x = left + gap + slot * (cardWidth + gap)
        if (slot >= leftSlots) x += sideGaps * gap
        if (slot >= leftSlots + columns) x += sideGaps * gap
        return x
    }

    /** Left edge of tableau column [c]. */
    fun columnX(c: Int): Float = slotX(leftSlots + c)

    /** Left edge of the k-th slot of the left side area (landscape), or of the top row (portrait). */
    private fun leftX(k: Int): Float = if (sideways) slotX(k) else columnX(k)

    val stockRect: Rect
        get() = Rect(Offset(if (sideways) slotX(slotsAcross - 1) else columnX(columns - 1), topY), cardSize)

    /** Foundation slot for suit [suit] (C, D, H, S). Klondike and Scorpion. */
    fun foundationRect(suit: Int): Rect =
        if (sideways) Rect(Offset(leftX(suit % 2), topY + (suit / 2) * (cardHeight + gap)), cardSize)
        else Rect(Offset(columnX(suit), topY), cardSize)

    private val wasteFan: Float = cardWidth * 0.32f
    private val wasteFanDown: Float = cardHeight * 0.28f

    /**
     * Positions of the visible waste cards, bottom to top (up to three for draw-3, one for draw-1), leaving
     * out the top [hideCount] cards. The top card is always next to the stock; in portrait older cards fan
     * out to the left, in landscape they sit above it in a short downward fan under the stock.
     */
    fun wastePositions(hideCount: Int = 0): List<Pair<Int, Offset>> {
        val visible = if (ruleSet.drawCount >= 3) 3 else 1
        val wasteSize = state.wasteSize - hideCount
        val n = minOf(visible, wasteSize).coerceAtLeast(0)
        val first = wasteSize - n
        return (0 until n).map { k ->
            val pos = if (sideways) {
                Offset(stockRect.left, stockRect.bottom + gap + k * wasteFanDown)
            } else {
                Offset(columnX(columns - 2) - (n - 1 - k) * wasteFan, topY)
            }
            state.waste[first + k] to pos
        }
    }

    /** Where the waste card at [index] (0 = bottom) is shown, if it is one of the visible ones. */
    fun wastePosition(index: Int): Offset? {
        val positions = wastePositions()
        val first = state.wasteSize - positions.size
        return positions.getOrNull(index - first)?.second
    }

    fun wasteTopRect(): Rect? = wastePositions().lastOrNull()?.let { Rect(it.second, cardSize) }

    // ---- stock and completed runs (Spider and Scorpion)

    private val stockFan: Float = cardWidth * 0.2f
    private val stockFanDown: Float = cardHeight * 0.16f

    /** Spider: one face-down card per deal left, fanned away from the stock slot (the last one is on top). */
    fun stockPositions(): List<Offset> {
        if (state.stockSize == 0) return emptyList()
        val deals = if (game == GameType.SPIDER) (state.stockSize + columns - 1) / columns else 1
        return (0 until deals).map { k ->
            val step = deals - 1 - k
            if (sideways) Offset(stockRect.left, topY + step * stockFanDown)
            else Offset(stockRect.left - step * stockFan, topY)
        }
    }

    /** What counts as tapping the stock. */
    private val stockHitRect: Rect
        get() = when {
            game != GameType.SPIDER -> stockRect
            sideways -> Rect(stockRect.left, topY, stockRect.right, stockRect.bottom + 4 * stockFanDown)
            else -> Rect(stockRect.left - 4 * stockFan, topY, stockRect.right, stockRect.bottom)
        }

    private val runFan: Float = cardWidth * 0.45f
    private val runFanDown: Float = minOf(cardHeight * 0.25f, (height - topY - cardHeight - gap) / 7f)

    /** Spider: where the k-th completed run (shown as its King) goes. */
    fun completedRunOffset(k: Int): Offset =
        if (sideways) Offset(leftX(0), topY + k * runFanDown) else Offset(columnX(0) + k * runFan, topY)

    // ---- columns

    private val downStepFull = cardHeight * 0.13f
    private val upStepFull = cardHeight * 0.28f

    /**
     * Extra space where a column stops being a same-suit descending run, so valid sequences sit together
     * and breaks stand out: a bit more in Spider, subtler in Scorpion.
     */
    private val breakStepFull = when (game) {
        GameType.SPIDER -> cardHeight * 0.12f
        GameType.SCORPION -> cardHeight * 0.07f
        GameType.KLONDIKE -> 0f
    }

    /** True if face-up card [i] of column [c] doesn't continue a same-suit run from the card under it. */
    private fun breaksRun(c: Int, i: Int): Boolean {
        if (breakStepFull == 0f || i <= state.tabDown[c]) return false
        return !io.github.thomasvanriel.solitaire.engine.model.Card.followsInSuit(state.tab[c][i], state.tab[c][i - 1])
    }

    /**
     * Distance from the top of column [c] to each card in it. When a column is too long for the space, the
     * face-down cards and the gaps at Spider's breaks are squeezed first, and the face-up cards (whose ranks
     * must stay readable) only after that.
     */
    private val columnY: Array<FloatArray> = Array(columns) { c ->
        val size = state.tabSize[c]
        val down = state.tabDown[c]
        val y = FloatArray(maxOf(size, 1))
        val nDown = minOf(down, size - 1).coerceAtLeast(0)
        val nUp = (size - down - 1).coerceAtLeast(0)
        var nBreaks = 0
        for (i in down + 1 until size) if (breaksRun(c, i)) nBreaks++
        var downStep = downStepFull
        var upStep = upStepFull
        var breakStep = breakStepFull
        val available = height - tableauTop - cardHeight - gap
        val needed = nDown * downStep + nUp * upStep + nBreaks * breakStep
        if (needed > available) {
            val downMin = cardHeight * 0.05f
            val breakMin = minOf(breakStepFull, cardHeight * 0.06f)
            val squeezable = nDown * (downStep - downMin) + nBreaks * (breakStep - breakMin)
            val excess = needed - available
            if (excess <= squeezable) {
                val f = 1f - excess / squeezable
                downStep = downMin + (downStep - downMin) * f
                breakStep = breakMin + (breakStep - breakMin) * f
            } else {
                downStep = downMin
                breakStep = breakMin
                val rest = available - nDown * downMin - nBreaks * breakMin
                upStep = if (nUp == 0) upStep else (rest / nUp).coerceIn(upStepFull * 0.1f, upStepFull)
            }
        }
        for (i in 1 until size) {
            val step = if (i <= down) downStep else upStep + if (breaksRun(c, i)) breakStep else 0f
            y[i] = y[i - 1] + step
        }
        y
    }

    fun cardOffset(c: Int, index: Int): Offset {
        val ys = columnY[c]
        val y = if (index < ys.size) ys[index] else ys.last() + upStepFull
        return Offset(columnX(c), tableauTop + y)
    }

    fun columnRect(c: Int): Rect {
        val size = state.tabSize[c]
        val bottom = if (size == 0) tableauTop + cardHeight else cardOffset(c, size - 1).y + cardHeight
        return Rect(columnX(c), tableauTop, columnX(c) + cardWidth, bottom)
    }

    /** Area that counts as "column c" when dropping: the whole height below the top row. */
    private fun columnDropRect(c: Int): Rect =
        Rect(columnX(c) - gap / 2, tableauTop - gap, columnX(c) + cardWidth + gap / 2, height)

    // ---- hit testing

    sealed class Hit {
        data object Stock : Hit()
        data class Card(val source: Source) : Hit()
    }

    fun hitTest(p: Offset): Hit? {
        if (stockHitRect.contains(p)) return Hit.Stock
        if (game == GameType.KLONDIKE) {
            wasteTopRect()?.let { if (it.contains(p)) return Hit.Card(Source.Waste) }
            for (suit in 0 until 4) {
                if (foundationRect(suit).contains(p) && state.found[suit] > 0) return Hit.Card(Source.Foundation(suit))
            }
        }
        for (c in 0 until columns) {
            if (!columnRect(c).contains(p)) continue
            // The topmost card whose rectangle contains the point.
            for (i in state.tabSize[c] - 1 downTo 0) {
                if (Rect(cardOffset(c, i), cardSize).contains(p)) {
                    return if (i >= state.tabDown[c]) Hit.Card(Source.Column(c, i)) else null
                }
            }
        }
        return null
    }

    /** Drop target under point [p], if any. */
    fun dropTarget(p: Offset): Target? {
        if (game == GameType.KLONDIKE) {
            for (suit in 0 until 4) if (foundationRect(suit).inflate(gap).contains(p)) return Target.Foundation
        }
        for (c in 0 until columns) if (columnDropRect(c).contains(p)) return Target.Column(c)
        return null
    }

    /** Top-left of the card at [source] (where a drag starts from). */
    fun sourceOffset(source: Source): Offset = when (source) {
        Source.Waste -> wastePositions().last().second
        is Source.Column -> cardOffset(source.col, source.index)
        is Source.Foundation -> foundationRect(source.suit).topLeft
    }

    /** Rectangle to highlight for a legal drop target. */
    fun targetRect(t: Target, draggedSuit: Int): Rect = when (t) {
        Target.Foundation -> foundationRect(draggedSuit)
        is Target.Column -> {
            val size = state.tabSize[t.col]
            if (size == 0) Rect(Offset(columnX(t.col), tableauTop), cardSize)
            else Rect(cardOffset(t.col, size - 1), cardSize)
        }
    }

    /** Rectangles to outline for a hint: the cards that move, then where they go (or the stock, to draw or deal). */
    fun hintRects(move: Move): List<Rect> {
        fun column(c: Int) = targetRect(Target.Column(c), 0)
        fun card(c: Int, i: Int) = Rect(cardOffset(c, i), cardSize)
        return when (move) {
            Move.Draw, Move.Recycle -> {
                val stock = stockPositions()
                listOf(if (stock.isEmpty()) stockRect else stock.map { Rect(it, cardSize) }.reduce(::span))
            }
            is Move.WasteToTableau -> listOfNotNull(wasteTopRect(), column(move.col))
            Move.WasteToFoundation -> listOfNotNull(wasteTopRect(), foundationRect(Card.suit(state.wasteTop())))
            is Move.TableauToFoundation -> {
                val top = state.tabSize[move.col] - 1
                listOf(card(move.col, top), foundationRect(Card.suit(state.tab[move.col][top])))
            }
            is Move.TableauToTableau -> {
                val size = state.tabSize[move.from]
                listOf(span(card(move.from, size - move.count), card(move.from, size - 1)), column(move.to))
            }
            is Move.FoundationToTableau -> listOf(foundationRect(move.suit), column(move.col))
        }
    }

    private fun span(a: Rect, b: Rect) =
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

    /** Step between dragged cards of a run. */
    val dragStep: Float get() = upStepFull

    private companion object {
        const val GAP_RATIO = 0.09f
        const val ASPECT = 1.42f
    }
}
