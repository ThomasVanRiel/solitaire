package io.github.thomasvanriel.solitaire.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * A very faint little surprise on the felt, on roughly one deal in 25. It depends only on the deal
 * number, so the same deals always have it. It's drawn under the cards, so it hides as you play.
 */
fun DrawScope.drawFeltSurprise(seed: Long, layout: BoardLayout) {
    // SplitMix64 finaliser, so neighbouring deal numbers give unrelated results.
    var h = seed + 0x5EEDL
    h = (h xor (h ushr 30)) * -4658895280553007687L
    h = (h xor (h ushr 27)) * -7723592293110705685L
    h = h xor (h ushr 31)
    if (((h ushr 40) % 25L) != 0L) return
    val side = layout.cardWidth * 0.32f
    val xFraction = ((h ushr 16) and 0xFFFF).toFloat() / 0xFFFF
    val x = layout.slotX(0) + xFraction * (layout.slotX(layout.columns - 1) + layout.cardWidth - layout.slotX(0) - side)
    val y = size.height - side * 1.8f
    if (y < layout.tableauTop + layout.cardHeight) return // no empty felt to hide in

    fun oval(cx: Float, cy: Float, w: Float, hgt: Float) =
        Rect(x + (cx - w / 2) * side, y + (cy - hgt / 2) * side, x + (cx + w / 2) * side, y + (cy + hgt / 2) * side)

    val shape = Path().apply {
        addOval(oval(0.50f, 0.80f, 1.00f, 0.36f)) // bottom tier
        addOval(oval(0.50f, 0.54f, 0.74f, 0.32f)) // middle tier
        addOval(oval(0.52f, 0.30f, 0.48f, 0.26f)) // top tier
        addOval(oval(0.60f, 0.12f, 0.16f, 0.16f)) // the little tip
    }
    drawPath(shape, Color(0xFF4E342E), alpha = 0.10f)
    // A hint of shine, barely there.
    drawCircle(Color.White, radius = side * 0.05f, center = Offset(x + side * 0.36f, y + side * 0.5f), alpha = 0.05f)
}
