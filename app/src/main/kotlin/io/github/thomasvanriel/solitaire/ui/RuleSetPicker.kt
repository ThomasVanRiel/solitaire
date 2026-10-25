package io.github.thomasvanriel.solitaire.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/**
 * Two scrolling rows of chips: the game (Klondike, Spider, Scorpion II, ...), then its variant (draw 1 or 3,
 * 1/2/4 suits, Yukon or Russian) when it has more than one. Each row scrolls sideways, so more games fit.
 *
 * With [onSelectAll], the game row starts with an "All games" chip (selected when [allSelected]), and no
 * variant row shows while it is selected.
 */
@Composable
fun RuleSetPicker(
    selected: RuleSet,
    onSelect: (RuleSet) -> Unit,
    showTitles: Boolean = true,
    onSelectAll: (() -> Unit)? = null,
    allSelected: Boolean = false,
) {
    Column {
        if (showTitles) Text("Game", style = MaterialTheme.typography.labelLarge)
        val games = GameType.entries
        val withAll = onSelectAll != null
        val labels = (if (withAll) listOf("All games") else emptyList()) + games.map { it.label }
        val offset = if (withAll) 1 else 0
        val selectedIndex = if (withAll && allSelected) 0 else games.indexOf(selected.game) + offset
        ChipWheel(labels, selectedIndex) { i ->
            if (withAll && i == 0) {
                onSelectAll?.invoke()
            } else {
                val game = games[i - offset]
                if (game != selected.game || allSelected) onSelect(if (game == selected.game) selected else defaultFor(game))
            }
        }
        val variants = RuleSet.ALL.filter { it.game == selected.game }
        if (variants.size > 1 && !(withAll && allSelected)) {
            Spacer(Modifier.height(4.dp))
            val title = when (selected.game) {
                GameType.KLONDIKE -> "Draw"
                GameType.SPIDER -> "Suits"
                else -> "Variant"
            }
            if (showTitles) Text(title, style = MaterialTheme.typography.labelLarge)
            ChipWheel(
                variants.map { if (it.game == GameType.KLONDIKE) (if (it.drawCount == 1) "1 card" else "3 cards") else it.variantLabel },
                variants.indexOfFirst { it.id == selected.id },
            ) { i -> onSelect(variants[i]) }
        }
    }
}

/**
 * A row of chips that scrolls sideways. The selected chip glides to the middle, and the edges fade out where
 * more chips are hidden. A plain scrolling row rather than a lazy one, because it sits inside dialogs.
 */
@Composable
private fun ChipWheel(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    // Where each chip sits in the row (left edge, width), once laid out.
    val placed = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val target = placed[selected]
    LaunchedEffect(selected, target, viewport) {
        if (target == null || viewport == 0) return@LaunchedEffect
        scroll.animateScrollTo((target.first + target.second / 2 - viewport / 2).coerceAtLeast(0))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val fade = 24.dp.toPx() / size.width
                val start = if (scroll.canScrollBackward) Color.Transparent else Color.Black
                val end = if (scroll.canScrollForward) Color.Transparent else Color.Black
                drawRect(
                    Brush.horizontalGradient(0f to start, fade to Color.Black, 1f - fade to Color.Black, 1f to end),
                    blendMode = BlendMode.DstIn,
                )
            }
            .horizontalScroll(scroll),
    ) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.onPlaced { placed[i] = it.positionInParent().x.toInt() to it.size.width },
            )
        }
    }
}

/** The variant picked when switching to [game]: draw 1 for Klondike, 1 suit for Spider (the gentlest). */
private fun defaultFor(game: GameType): RuleSet = RuleSet.ALL.first { it.game == game }
