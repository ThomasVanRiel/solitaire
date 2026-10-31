package io.github.thomasvanriel.solitaire.ui

import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.mutableFloatStateOf
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
    leftHanded: Boolean,
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
    var progress by remember { mutableFloatStateOf(1f) }
    var dropAt by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // The steps from what is on screen to [game], worked out in the same composition that brings the new
        // game, so its very first frame already shows the cards at their starting places (never at the target).
        val steps = remember(game, widthPx, heightPx, leftHanded) {
            animationSteps(shownGame, game, widthPx, heightPx, dropAt, animationMillis, leftHanded)
        }

        LaunchedEffect(game) {
            dropAt = null
            for (step in steps) {
                // Plain state writes with no suspension in between: the step and its start land in one frame.
                progress = 0f
                animation = step
                shownGame = game
                animate(0f, 1f, animationSpec = tween(step.durationMs, easing = LinearEasing)) { v, _ -> progress = v }
            }
            animation = null
            shownGame = game
        }

        // Until the effect starts, show the first step at its start (or the new position if nothing moves).
        val pending = shownGame !== game
        val anim = if (pending) steps.firstOrNull() else animation
        val state = anim?.after ?: game.state
        val layout = BoardLayout(widthPx, heightPx, state, ruleSet, leftHanded)
        // Taps and drags always work on the real (final) position.
        val inputLayout = if (anim == null) layout else BoardLayout(widthPx, heightPx, game.state, ruleSet, leftHanded)

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
            val t = if (pending) 0f else progress // read while drawing, so a frame only redraws
            drawFeltSurprise(seed, l)
            with(painter) {
                // Stock (Spider shows one card per deal left)
                val klondike = ruleSet.game == GameType.KLONDIKE
                val stock = l.stockPositions()
                if (!ruleSet.hasStock) {
                    // Yukon and FreeCell have no stock.
                } else if (stock.isEmpty()) {
                    drawSlot(l.stockRect.topLeft, l.cardSize, l.corner, if (klondike && state.wasteSize > 0) "↻" else null)
                } else {
                    for (pos in stock) drawFaceDown(pos, l.cardSize, l.corner)
                }

                // Free cells (FreeCell), leaving out a card being dragged or flying in.
                for (k in state.cells.indices) {
                    val r = l.cellRect(k)
                    val card = state.cells[k]
                    val away = (hidden is Source.Cell && hidden.index == k) || gone?.cell == k
                    if (card >= 0 && !away) drawFaceUp(card, r.topLeft, l.cardSize, l.corner)
                    else drawSlot(r.topLeft, l.cardSize, l.corner, null)
                }

                // Waste (leaving out the top card while it is being dragged, and cards still flying in)
                val wasteHidden = (if (hidden == Source.Waste) 1 else 0) + (gone?.wasteTop ?: 0)
                for ((card, pos) in l.wastePositions(hideCount = wasteHidden)) {
                    drawFaceUp(card, pos, l.cardSize, l.corner)
                }

                // Foundations
                when (ruleSet.game) {
                    GameType.KLONDIKE, GameType.YUKON, GameType.FREECELL -> for (suit in 0 until 4) {
                        val r = l.foundationRect(suit)
                        val f = state.found[suit]
                        val flying = gone?.foundationSuit == suit
                        val shown = f - (if (flying) 1 else 0)
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
                val emptyLabel = if (ruleSet.kingsOnlyInEmpty) "K" else null
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
                    for (target in d.targets) {
                        val r = l.targetRect(target, suit)
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

/**
 * The animation steps from [prev] to [game]: only for a few moves played forward from what was on screen, or
 * for a last move swapped for another (a card tapped again), which flies from where the card is now.
 */
private fun animationSteps(
    prev: Game?,
    game: Game,
    width: Float,
    height: Float,
    dropAt: Offset?,
    baseMs: Int,
    leftHanded: Boolean,
): List<MoveAnimation> {
    if (prev == null || prev === game) return emptyList()
    val from = prev.moves.size
    val to = game.moves.size
    val swapped = prev.initial === game.initial && to == from && to > 0 &&
        game.moves.subList(0, to - 1) == prev.moves.subList(0, to - 1) && game.moves.last() != prev.moves.last()
    if (swapped) {
        val now = BoardLayout(width, height, prev.state, game.ruleSet, leftHanded)
        val s = prev.state
        val at = when (val m = prev.moves.last()) {
            is Move.TableauToTableau -> now.cardOffset(m.to, s.tabSize[m.to] - m.count)
            is Move.WasteToTableau -> now.cardOffset(m.col, s.tabSize[m.col] - 1)
            is Move.CellToTableau -> now.cardOffset(m.col, s.tabSize[m.col] - 1)
            is Move.TableauToCell -> now.cellRect(prev.stateAt(to - 1).firstFreeCell()).topLeft
            else -> return emptyList()
        }
        return listOfNotNull(
            MoveAnimation.of(
                game.stateAt(to - 1), game.state, game.moves.last(), game.ruleSet, width, height,
                dropAt = at, baseMs = baseMs, leftHanded = leftHanded,
            ),
        )
    }
    val forward = prev.initial === game.initial && to > from && to - from <= 4 && game.moves.subList(0, from) == prev.moves
    if (!forward) return emptyList()
    return (from until to).mapNotNull { i ->
        MoveAnimation.of(
            game.stateAt(i), game.stateAt(i + 1), game.moves[i], game.ruleSet, width, height,
            dropAt = if (i == from) dropAt else null, baseMs = baseMs, leftHanded = leftHanded,
        )
    }
}

private fun cardsOf(s: GameState, source: Source): List<Int> = when (source) {
    Source.Waste -> listOf(s.waste[s.wasteSize - 1])
    is Source.Column -> (source.index until s.tabSize[source.col]).map { s.tab[source.col][it] }
    is Source.Cell -> listOf(s.cells[source.index])
}

/** A plain-language summary of the board for screen readers. */
private fun describe(s: GameState, rs: RuleSet): String = buildString {
    if (rs.hasStock) append("Stock ${s.stockSize} cards. ")
    if (rs.cells > 0) {
        val held = s.cells.filter { it >= 0 }
        append(if (held.isEmpty()) "Free cells empty. " else "Free cells: ${held.joinToString { Card.spoken(it) }}. ")
    }
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
    if (rs.buildsFoundations) append("Foundations: ${s.foundationCount} cards.")
    else append("Completed suits: ${s.foundationCount / 13} of ${rs.deckSize / 13}.")
}
