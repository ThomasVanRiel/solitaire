package io.github.thomasvanriel.winnable.engine.rules

import io.github.thomasvanriel.winnable.engine.model.Move
import io.github.thomasvanriel.winnable.engine.model.RuleSet
import io.github.thomasvanriel.winnable.engine.shuffle.Deals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GameTest {
    @Test
    fun playUndoRestartReplay() {
        val rs = RuleSet.DRAW_3_UNLIMITED
        var g = Game.start(Deals.deal(42), rs)
        assertFalse(g.canUndo)
        repeat(5) { g = g.play(Move.Draw) }
        assertEquals(5, g.moves.size)
        val afterFive = g.state
        g = g.play(Move.Draw)
        g = g.undo()
        assertEquals(afterFive, g.state)
        assertEquals(g.state, Game.replay(Deals.deal(42), rs, g.moves).state)
        assertEquals(Deals.deal(42), g.restart().state)
        assertEquals(Deals.deal(42), g.undoTo(0).state)
        assertTrue(g.undoTo(2).moves.size == 2)
        assertThrows<IllegalStateException> { g.restart().undo() }
        assertThrows<IllegalArgumentException> { g.play(Move.Recycle) }
    }
}
