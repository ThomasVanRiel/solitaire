package io.github.thomasvanriel.solitaire.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    // Soft, warm tones: linen faces, terracotta and charcoal pips, a dusty blue back.
    private val faceColor = Color(0xFFFBF6EA)
    private val edgeColor = Color(0xFFCFC5B3)
    private val shadowColor = Color(0x22000000)
    private val red = Color(0xFFB34A3C)
    private val black = Color(0xFF3B3835)
    private val backColor = Color(0xFF5D7891)
    private val backLine = Color(0xFF6C87A0)
    private val backEdge = Color(0xFFF1EADB)
    private val slotColor = Color(0x38FFFFFF)
    private val highlightColor = Color(0xDDF0D9A0)
    private val hintColor = Color(0xFFBFE0E6)

    private val ranks = arrayOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

    /** A faint shadow just below a card, so cards rest on the felt instead of being cut out of it. */
    private fun DrawScope.drawShadow(topLeft: Offset, size: Size, corner: Float) {
        drawRoundRect(shadowColor, topLeft + Offset(0f, size.width * 0.025f), size, CornerRadius(corner))
    }

    fun DrawScope.drawFaceUp(card: Int, topLeft: Offset, size: Size, corner: Float) {
        drawShadow(topLeft, size, corner)
        drawRoundRect(faceColor, topLeft, size, CornerRadius(corner))
        drawRoundRect(edgeColor, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.015f))
        val color = if (Card.isRed(card)) red else black
        val w = size.width
        val cornerStyle = TextStyle(color = color, fontSize = (w * 0.30f).toSp(), fontWeight = FontWeight.SemiBold)
        val rankLayout = measurer.measure(ranks[Card.rank(card)], cornerStyle)
        drawText(rankLayout, topLeft = topLeft + Offset(w * 0.07f, w * 0.02f))
        // Small suit in the top-right corner, level with the rank, so a fanned card shows both.
        val small = w * 0.26f
        drawSuit(Card.suit(card), topLeft + Offset(w - small - w * 0.08f, w * 0.08f), small, color)
        // Large suit in the middle.
        val big = w * 0.5f
        drawSuit(Card.suit(card), topLeft + Offset((w - big) / 2f, size.height * 0.62f - big / 2f), big, color)
    }

    fun DrawScope.drawFaceDown(topLeft: Offset, size: Size, corner: Float) {
        drawShadow(topLeft, size, corner)
        drawRoundRect(backColor, topLeft, size, CornerRadius(corner))
        val inset = size.width * 0.08f
        val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
        val innerTopLeft = topLeft + Offset(inset, inset)
        clipRect(innerTopLeft.x, innerTopLeft.y, innerTopLeft.x + inner.width, innerTopLeft.y + inner.height) {
            val step = size.width * 0.16f
            var x = -size.height
            while (x < size.width) {
                drawLine(backLine, innerTopLeft + Offset(x, 0f), innerTopLeft + Offset(x + inner.height, inner.height), strokeWidth = size.width * 0.025f)
                x += step
            }
        }
        drawRoundRect(backEdge, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.03f))
    }

    /** An empty pile outline, optionally with a faint label ("K" for an empty column, ↻ for recycling the stock). */
    fun DrawScope.drawSlot(topLeft: Offset, size: Size, corner: Float, label: String?) {
        drawRoundRect(slotColor, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.025f))
        if (label != null) {
            val layout = measurer.measure(label, TextStyle(color = slotColor, fontSize = (size.width * 0.5f).toSp()))
            drawText(layout, topLeft = topLeft + Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
        }
    }

    /** An empty foundation, showing its suit faintly. */
    fun DrawScope.drawFoundationSlot(suit: Int, topLeft: Offset, size: Size, corner: Float) {
        drawSlot(topLeft, size, corner, null)
        val side = size.width * 0.5f
        drawSuit(suit, topLeft + Offset((size.width - side) / 2f, (size.height - side) / 2f), side, slotColor)
    }

    fun DrawScope.drawHighlight(topLeft: Offset, size: Size, corner: Float) {
        drawRoundRect(highlightColor.copy(alpha = 0.18f), topLeft, size, CornerRadius(corner))
        drawRoundRect(highlightColor, topLeft, size, CornerRadius(corner), style = Stroke(width = size.width * 0.05f))
    }

    /** Outlines a hint: the cards to move, and where they go. [cardWidth] sets the line width. */
    fun DrawScope.drawHint(rect: Rect, cardWidth: Float, corner: Float) {
        drawRoundRect(hintColor.copy(alpha = 0.20f), rect.topLeft, rect.size, CornerRadius(corner))
        drawRoundRect(hintColor, rect.topLeft, rect.size, CornerRadius(corner), style = Stroke(width = cardWidth * 0.06f))
    }
}
