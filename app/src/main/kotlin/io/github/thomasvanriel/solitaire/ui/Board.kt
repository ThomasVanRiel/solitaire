package io.github.thomasvanriel.solitaire.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Game
import io.github.thomasvanriel.solitaire.game.Source
import io.github.thomasvanriel.solitaire.game.Target

/** Cards being dragged: where they came from, which cards, and how far the finger has moved. */
private class DragState(val source: Source, val cards: List<Int>, val origin: Offset, val targets: List<Target>) {
    var delta by mutableStateOf(Offset.Zero)
}

/**
 * The board. Moves played forward are animated: the moved cards slide from their old place to their new
 * one, one move after another (a Spider move and the run it completes are two steps). Undo, Restart and
 * a new deal change the board straight away. [animationMillis] is the length of one ordinary move.
 */
@Composable
fun Board(
    game: Game,
    enabled: Boolean,
    onStock: () -> Unit,
    onTap: (Source) -> Unit,
    onDrop: (Source, Target) -> Boolean,
    legalTargets: (Source) -> List<Target>,
    seed: Long,
    animationMillis: Int,
    modifier: Modifier = Modifier,
    hint: Move? = null,
) {
    val ruleSet = game.ruleSet
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val painter = remember(measurer) { CardPainter(measurer) }
    var drag by remember { mutableStateOf<DragState?>(null) }

    // Animation: the game last shown, the step being animated, its progress, and where a drop let go.
    var shownGame by remember { mutableStateOf<Game?>(null) }
    var animation by remember { mutableStateOf<MoveAnimation?>(null) }
    val progress = remember { Animatable(1f) }
    var dropAt by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(game) {
            val prev = shownGame
            shownGame = game
            val drop = dropAt
            dropAt = null
            val from = prev?.moves?.size ?: 0
            val to = game.moves.size
            // Only animate a few moves played forward from what was on screen.
            val forward = prev != null && prev.initial === game.initial && to > from && to - from <= 4 &&
                game.moves.subList(0, from) == prev.moves
            if (!forward) {
                animation = null
                return@LaunchedEffect
            }
            for (i in from until to) {
                val step = MoveAnimation.of(
                    game.stateAt(i), game.stateAt(i + 1), game.moves[i], ruleSet, widthPx, heightPx,
                    dropAt = if (i == from) drop else null, baseMs = animationMillis,
                ) ?: continue
                animation = step
                progress.snapTo(0f)
                progress.animateTo(1f, tween(step.durationMs, easing = LinearEasing))
            }
            animation = null
        }

        val anim = animation
        val state = anim?.after ?: game.state
        val layout = BoardLayout(widthPx, heightPx, state, ruleSet)
        // Taps and drags always work on the real (final) position.
        val inputLayout = if (anim == null) layout else BoardLayout(widthPx, heightPx, game.state, ruleSet)

        val currentLayout by rememberUpdatedState(inputLayout)
        val currentState by rememberUpdatedState(game.state)
        val currentEnabled by rememberUpdatedState(enabled)
        val tapHandler by rememberUpdatedState(onTap)
        val stockHandler by rememberUpdatedState(onStock)
        val dropHandler by rememberUpdatedState(onDrop)
        val targetsFor by rememberUpdatedState(legalTargets)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = describe(game.state, ruleSet) }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { p ->
                        if (!currentEnabled) return@detectTapGestures
                        when (val hit = currentLayout.hitTest(p)) {
                            BoardLayout.Hit.Stock -> stockHandler()
                            is BoardLayout.Hit.Card -> tapHandler(hit.source)
                            null -> {}
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val hit = currentLayout.hitTest(p) as? BoardLayout.Hit.Card
                            if (currentEnabled && hit != null) {
                                val cards = cardsOf(currentState, hit.source)
                                drag = DragState(hit.source, cards, currentLayout.sourceOffset(hit.source), targetsFor(hit.source))
                            }
                        },
                        onDragEnd = {
                            val d = drag
                            drag = null
                            if (d != null) {
                                // Drop where the middle of the dragged card's top part is.
                                val l = currentLayout
                                val point = d.origin + d.delta + Offset(l.cardWidth / 2f, l.cardHeight * 0.25f)
                                val target = l.dropTarget(point)
                                if (target != null) {
                                    dropAt = d.origin + d.delta
                                    if (!dropHandler(d.source, target)) dropAt = null
                                }
                            }
                        },
                        onDragCancel = { drag = null },
                        onDrag = { change, amount ->
                            change.consume()
                            drag?.let { it.delta += amount }
                        },
                    )
                },
        ) {
            val l = layout
            val d = drag
            val hidden: Source? = d?.source
            val gone = anim?.hidden
            val t = progress.value
            drawFeltSurprise(seed, l)
            with(painter) {
                // Stock (Spider shows one card per deal left)
                val klondike = ruleSet.game == GameType.KLONDIKE
                val stock = l.stockPositions()
                if (stock.isEmpty()) {
                    drawSlot(l.stockRect.topLeft, l.cardSize, l.corner, if (klondike && state.wasteSize > 0) "↻" else null)
                } else {
                    for (pos in stock) drawFaceDown(pos, l.cardSize, l.corner)
                }

                // Waste (leaving out the top card while it is being dragged, and cards still flying in)
                val wasteHidden = (if (hidden == Source.Waste) 1 else 0) + (gone?.wasteTop ?: 0)
                for ((card, pos) in l.wastePositions(hideCount = wasteHidden)) {
                    drawFaceUp(card, pos, l.cardSize, l.corner)
                }

                // Foundations
                when (ruleSet.game) {
                    GameType.KLONDIKE -> for (suit in 0 until 4) {
                        val r = l.foundationRect(suit)
                        val f = state.found[suit]
                        val draggingThis = hidden is Source.Foundation && hidden.suit == suit
                        val flying = gone?.foundationSuit == suit
                        val shown = f - (if (draggingThis) 1 else 0) - (if (flying) 1 else 0)
                        if (shown > 0) drawFaceUp(Card.of(suit, shown - 1), r.topLeft, l.cardSize, l.corner)
                        else drawFoundationSlot(suit, r.topLeft, l.cardSize, l.corner)
                    }
                    // A completed run shows as its King.
                    GameType.SCORPION -> for (suit in 0 until 4) {
                        val r = l.foundationRect(suit)
                        if (state.found[suit] > 0 && gone?.foundationSuit != suit) {
                            drawFaceUp(Card.of(suit, Card.KING), r.topLeft, l.cardSize, l.corner)
                        }
                        else drawFoundationSlot(suit, r.topLeft, l.cardSize, l.corner)
                    }
                    GameType.SPIDER -> {
                        var k = 0
                        var drawn = 0
                        for (suit in 0 until 4) repeat(state.found[suit] / 13) {
                            if (k != gone?.runIndex) {
                                drawFaceUp(Card.of(suit, Card.KING), l.completedRunOffset(k), l.cardSize, l.corner)
                                drawn++
                            }
                            k++
                        }
                        if (drawn == 0) drawSlot(l.completedRunOffset(0), l.cardSize, l.corner, null)
                    }
                }

                // Columns
                val spider = ruleSet.game == GameType.SPIDER
                val emptyLabel = if (spider) null else "K"
                for (c in 0 until state.columns) {
                    val size = state.tabSize[c]
                    val stopAt = minOf(
                        if (hidden is Source.Column && hidden.col == c) hidden.index else size,
                        gone?.columnFrom?.get(c) ?: size,
                    )
                    if (size == 0 || stopAt == 0) drawSlot(l.cardOffset(c, 0), l.cardSize, l.corner, emptyLabel)
                    for (i in 0 until stopAt) {
                        val pos = l.cardOffset(c, i)
                        if (i < state.tabDown[c]) drawFaceDown(pos, l.cardSize, l.corner)
                        else drawFaceUp(state.tab[c][i], pos, l.cardSize, l.corner)
                    }
                }

                // Hint: the cards to move and where they go (once the board has settled).
                if (hint != null && anim == null && d == null) {
                    for (r in l.hintRects(hint)) drawHint(r, l.cardWidth, l.corner)
                }

                // Drag: highlight legal targets, then draw the dragged cards on top.
                if (d != null) {
                    val suit = Card.suit(d.cards.first())
                    for (t in d.targets) {
                        val r = l.targetRect(t, suit)
                        drawHighlight(r.topLeft, l.cardSize, l.corner)
                    }
                    d.cards.forEachIndexed { k, card ->
                        drawFaceUp(card, d.origin + d.delta + Offset(0f, k * l.dragStep), l.cardSize, l.corner)
                    }
                }

                // Cards in flight, on top of everything.
                if (anim != null) {
                    for (f in anim.flights) drawFaceUp(f.card, f.at(t, anim.maxDelay), l.cardSize, l.corner)
                }
            }
        }
    }
}

private fun cardsOf(s: GameState, source: Source): List<Int> = when (source) {
    Source.Waste -> listOf(s.waste[s.wasteSize - 1])
    is Source.Column -> (source.index until s.tabSize[source.col]).map { s.tab[source.col][it] }
    is Source.Foundation -> listOf(Card.of(source.suit, s.found[source.suit] - 1))
}

/** A plain-language summary of the board for screen readers. */
private fun describe(s: GameState, rs: RuleSet): String = buildString {
    append("Stock ${s.stockSize} cards. ")
    if (rs.game == GameType.KLONDIKE) {
        val w = s.wasteTop()
        append(if (w >= 0) "Waste: ${Card.spoken(w)}. " else "Waste empty. ")
    }
    for (c in 0 until s.columns) {
        val top = s.columnTop(c)
        append("Column ${c + 1}: ")
        append(if (top < 0) "empty" else "${Card.spoken(top)}, ${s.tabDown[c]} hidden")
        append(". ")
    }
    if (rs.game == GameType.KLONDIKE) append("Foundations: ${s.foundationCount} cards.")
    else append("Completed suits: ${s.foundationCount / 13} of ${rs.deckSize / 13}.")
}
