package io.github.thomasvanriel.solitaire.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.game.Source
import io.github.thomasvanriel.solitaire.game.Target

/**
 * Where everything goes on the board, in pixels. The stock is always at the far right, where the right
 * thumb is, and the foundations at the left. The columns are below.
 * - Klondike: four foundations, the waste (fanned to the left for draw-3), the stock. Seven columns.
 * - Spider: completed runs fanned from the left, the remaining deals fanned left of the stock. Ten columns.
 * - Scorpion II: one slot per suit for its completed run, the stock. Seven columns.
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

    /**
     * Card width: as wide as the columns allow, but small enough that the top row plus about three
     * card heights of tableau fit (so landscape and tablets don't overflow). The board is centred.
     */
    val cardWidth: Float = minOf(width / (columns + (columns + 1) * GAP_RATIO), height / (ASPECT * 4.2f))
    val gap: Float = cardWidth * GAP_RATIO
    val cardHeight: Float = cardWidth * ASPECT
    private val left: Float = (width - (columns * cardWidth + (columns + 1) * gap)) / 2f
    val cardSize = Size(cardWidth, cardHeight)
    val corner: Float = cardWidth * 0.09f

    private val topY = gap
    val tableauTop: Float = topY + cardHeight + gap * 3

    fun slotX(slot: Int): Float = left + gap + slot * (cardWidth + gap)

    val stockRect: Rect get() = Rect(Offset(slotX(columns - 1), topY), cardSize)

    /** Foundation slot for suit [suit] (C, D, H, S from left to right). Klondike and Scorpion. */
    fun foundationRect(suit: Int): Rect = Rect(Offset(slotX(suit), topY), cardSize)

    private val wasteFan: Float = cardWidth * 0.32f

    /**
     * Positions of the visible waste cards, bottom to top (up to three for draw-3, one for draw-1).
     * The top card is always next to the stock; older cards fan out to the left.
     */
    fun wastePositions(hideTop: Boolean = false): List<Pair<Int, Offset>> {
        val visible = if (ruleSet.drawCount >= 3) 3 else 1
        val wasteSize = if (hideTop) state.wasteSize - 1 else state.wasteSize
        val n = minOf(visible, wasteSize).coerceAtLeast(0)
        val first = wasteSize - n
        return (0 until n).map { k -> state.waste[first + k] to Offset(slotX(columns - 2) - (n - 1 - k) * wasteFan, topY) }
    }

    fun wasteTopRect(): Rect? = wastePositions().lastOrNull()?.let { Rect(it.second, cardSize) }

    // ---- stock and completed runs (Spider and Scorpion)

    private val stockFan: Float = cardWidth * 0.2f

    /** Spider: one face-down card per deal left, fanned to the left of the stock slot. */
    fun stockPositions(): List<Offset> {
        if (state.stockSize == 0) return emptyList()
        val deals = if (game == GameType.SPIDER) (state.stockSize + columns - 1) / columns else 1
        return (0 until deals).map { k -> Offset(stockRect.left - (deals - 1 - k) * stockFan, topY) }
    }

    /** What counts as tapping the stock. */
    private val stockHitRect: Rect
        get() = if (game == GameType.SPIDER) Rect(stockRect.left - 4 * stockFan, topY, stockRect.right, stockRect.bottom) else stockRect

    private val runFan: Float = cardWidth * 0.45f

    /** Spider: where the k-th completed run (shown as its King) goes. */
    fun completedRunOffset(k: Int): Offset = Offset(slotX(0) + k * runFan, topY)

    // ---- columns

    private val downStepFull = cardHeight * 0.13f
    private val upStepFull = cardHeight * 0.28f

    /** Vertical steps (face-down, face-up) for column [c], shrunk to fit the space below the top row. */
    private fun steps(c: Int): Pair<Float, Float> {
        val size = state.tabSize[c]
        if (size <= 1) return downStepFull to upStepFull
        val down = state.tabDown[c]
        val up = size - down
        val available = height - tableauTop - cardHeight - gap
        val needed = down * downStepFull + (up - 1) * upStepFull
        if (needed <= available || needed <= 0f) return downStepFull to upStepFull
        val scale = (available / needed).coerceAtLeast(0.1f)
        return downStepFull * scale to upStepFull * scale
    }

    fun cardOffset(c: Int, index: Int): Offset {
        val (downStep, upStep) = steps(c)
        val down = state.tabDown[c]
        val y = if (index <= down) index * downStep else down * downStep + (index - down) * upStep
        return Offset(slotX(c), tableauTop + y)
    }

    fun columnRect(c: Int): Rect {
        val size = state.tabSize[c]
        val bottom = if (size == 0) tableauTop + cardHeight else cardOffset(c, size - 1).y + cardHeight
        return Rect(slotX(c), tableauTop, slotX(c) + cardWidth, bottom)
    }

    /** Area that counts as "column c" when dropping: the whole height below the top row. */
    private fun columnDropRect(c: Int): Rect =
        Rect(slotX(c) - gap / 2, tableauTop - gap, slotX(c) + cardWidth + gap / 2, height)

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
            if (size == 0) Rect(Offset(slotX(t.col), tableauTop), cardSize)
            else Rect(cardOffset(t.col, size - 1), cardSize)
        }
    }

    /** Step between dragged cards of a run. */
    val dragStep: Float get() = upStepFull

    private companion object {
        const val GAP_RATIO = 0.09f
        const val ASPECT = 1.42f
    }
}
