package io.github.thomasvanriel.solitaire.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import io.github.thomasvanriel.solitaire.engine.model.Card

/** Draws cards with plain shapes and system-font text. No images. */
class CardPainter(private val measurer: TextMeasurer) {
    private val faceColor = Color(0xFFFDFDF8)
    private val edgeColor = Color(0xFF9E9E9E)
    private val red = Color(0xFFC62828)
    private val black = Color(0xFF212121)
    private val backColor = Color(0xFF1E4E8C)
    private val backLine = Color(0xFF3A6DB0)
    private val slotColor = Color(0x55FFFFFF)
    private val highlightColor = Color(0xFFFFD54F)

    private val symbols = arrayOf("♣", "♦", "♥", "♠")
    private val ranks = arrayOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

    fun DrawScope.drawFaceUp(card: Int, topLeft: Offset, size: Size, corner: Float) {
        drawRoundRect(faceColor, topLeft, size, CornerRadius(corner))
        drawRoundRect(edgeColor, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.02f))
        val color = if (Card.isRed(card)) red else black
        val w = size.width
        val cornerStyle = TextStyle(color = color, fontSize = (w * 0.30f).toSp(), fontWeight = FontWeight.Bold)
        val rankLayout = measurer.measure(ranks[Card.rank(card)], cornerStyle)
        drawText(rankLayout, topLeft = topLeft + Offset(w * 0.07f, w * 0.02f))
        val suitSmall = measurer.measure(symbols[Card.suit(card)], cornerStyle.copy(fontSize = (w * 0.28f).toSp()))
        drawText(suitSmall, topLeft = topLeft + Offset(w - suitSmall.size.width - w * 0.07f, w * 0.03f))
        // Large suit in the middle of the visible part, so even a fanned card shows its suit.
        val big = measurer.measure(symbols[Card.suit(card)], cornerStyle.copy(fontSize = (w * 0.62f).toSp(), fontWeight = FontWeight.Normal))
        val center = topLeft + Offset((w - big.size.width) / 2f, size.height * 0.62f - big.size.height / 2f)
        drawText(big, topLeft = center)
    }

    fun DrawScope.drawFaceDown(topLeft: Offset, size: Size, corner: Float) {
        drawRoundRect(backColor, topLeft, size, CornerRadius(corner))
        val inset = size.width * 0.08f
        val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
        val innerTopLeft = topLeft + Offset(inset, inset)
        clipRect(innerTopLeft.x, innerTopLeft.y, innerTopLeft.x + inner.width, innerTopLeft.y + inner.height) {
            val step = size.width * 0.16f
            var x = -size.height
            while (x < size.width) {
                drawLine(backLine, innerTopLeft + Offset(x, 0f), innerTopLeft + Offset(x + inner.height, inner.height), strokeWidth = size.width * 0.03f)
                x += step
            }
        }
        drawRoundRect(Color.White, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.03f))
    }

    /** An empty pile outline, optionally with a faint label (suit symbol, or ↻ for recycling the stock). */
    fun DrawScope.drawSlot(topLeft: Offset, size: Size, corner: Float, label: String?) {
        drawRoundRect(slotColor, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.03f))
        if (label != null) {
            val layout = measurer.measure(label, TextStyle(color = slotColor, fontSize = (size.width * 0.5f).toSp()))
            drawText(layout, topLeft = topLeft + Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
        }
    }

    fun DrawScope.drawHighlight(topLeft: Offset, size: Size, corner: Float) {
        drawRoundRect(highlightColor, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.07f))
    }

    fun suitSymbol(suit: Int): String = symbols[suit]
}
