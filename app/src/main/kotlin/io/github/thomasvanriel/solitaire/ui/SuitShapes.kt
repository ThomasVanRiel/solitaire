package io.github.thomasvanriel.solitaire.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.thomasvanriel.solitaire.engine.model.Card

/**
 * Suit symbols drawn as shapes in a square box, so they look the same on every device and stay easy to
 * tell apart at small sizes: the spade has a pointed top, the club three round leaves.
 */
fun DrawScope.drawSuit(suit: Int, topLeft: Offset, side: Float, color: Color) {
    fun p(x: Float, y: Float) = Offset(topLeft.x + x * side, topLeft.y + y * side)
    fun Path.move(x: Float, y: Float) = p(x, y).let { moveTo(it.x, it.y) }
    fun Path.line(x: Float, y: Float) = p(x, y).let { lineTo(it.x, it.y) }
    fun Path.curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = p(x1, y1)
        val b = p(x2, y2)
        val c = p(x3, y3)
        cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
    }

    // A flared stem shared by spades and clubs.
    fun stem(): Path = Path().apply {
        move(0.5f, 0.55f)
        curve(0.5f, 0.78f, 0.42f, 0.92f, 0.27f, 0.98f)
        line(0.73f, 0.98f)
        curve(0.58f, 0.92f, 0.5f, 0.78f, 0.5f, 0.55f)
        close()
    }

    when (suit) {
        Card.HEARTS -> drawPath(
            Path().apply {
                move(0.5f, 0.92f)
                curve(0.15f, 0.68f, 0.0f, 0.46f, 0.05f, 0.28f)
                curve(0.1f, 0.08f, 0.4f, 0.04f, 0.5f, 0.27f)
                curve(0.6f, 0.04f, 0.9f, 0.08f, 0.95f, 0.28f)
                curve(1.0f, 0.46f, 0.85f, 0.68f, 0.5f, 0.92f)
                close()
            },
            color,
        )
        Card.DIAMONDS -> drawPath(
            Path().apply {
                move(0.5f, 0.02f)
                curve(0.62f, 0.2f, 0.74f, 0.36f, 0.88f, 0.5f)
                curve(0.74f, 0.64f, 0.62f, 0.8f, 0.5f, 0.98f)
                curve(0.38f, 0.8f, 0.26f, 0.64f, 0.12f, 0.5f)
                curve(0.26f, 0.36f, 0.38f, 0.2f, 0.5f, 0.02f)
                close()
            },
            color,
        )
        Card.SPADES -> {
            drawPath(
                Path().apply {
                    move(0.5f, 0.02f)
                    curve(0.82f, 0.26f, 1.0f, 0.44f, 0.96f, 0.6f)
                    curve(0.92f, 0.78f, 0.64f, 0.82f, 0.5f, 0.64f)
                    curve(0.36f, 0.82f, 0.08f, 0.78f, 0.04f, 0.6f)
                    curve(0.0f, 0.44f, 0.18f, 0.26f, 0.5f, 0.02f)
                    close()
                },
                color,
            )
            drawPath(stem(), color)
        }
        else -> { // clubs: three round leaves on a stem
            val r = 0.215f * side
            for ((cx, cy) in listOf(0.5f to 0.26f, 0.26f to 0.56f, 0.74f to 0.56f)) {
                val c = p(cx, cy)
                drawPath(Path().apply { addOval(Rect(c, r)) }, color)
            }
            val mid = p(0.5f, 0.5f)
            drawPath(Path().apply { addOval(Rect(mid, 0.12f * side)) }, color)
            drawPath(stem(), color)
        }
    }
}
