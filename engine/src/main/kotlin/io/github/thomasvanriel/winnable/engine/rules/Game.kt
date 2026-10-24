package io.github.thomasvanriel.winnable.engine.rules

import io.github.thomasvanriel.winnable.engine.model.GameState
import io.github.thomasvanriel.winnable.engine.model.Move
import io.github.thomasvanriel.winnable.engine.model.RuleSet

/**
 * An immutable game: the starting position plus the moves played so far. Undo is unlimited.
 * Every position along the way is kept, so undo is instant.
 */
class Game private constructor(
    val ruleSet: RuleSet,
    private val states: List<GameState>,
    val moves: List<Move>,
) {
    val initial: GameState get() = states.first()
    val state: GameState get() = states.last()
    val isWon: Boolean get() = Rules.isWon(state)
    val canUndo: Boolean get() = moves.isNotEmpty()

    fun legalMoves(): List<Move> = Rules.legalMoves(state, ruleSet)
    fun isLegal(move: Move): Boolean = Rules.isLegal(state, ruleSet, move)

    /** Plays [move]. Throws if it is not legal. */
    fun play(move: Move): Game = Game(ruleSet, states + Rules.apply(state, ruleSet, move), moves + move)

    fun undo(): Game {
        check(canUndo) { "Nothing to undo" }
        return Game(ruleSet, states.dropLast(1), moves.dropLast(1))
    }

    /** Goes back to the position after the first [moveCount] moves. */
    fun undoTo(moveCount: Int): Game {
        require(moveCount in 0..moves.size)
        return Game(ruleSet, states.take(moveCount + 1), moves.take(moveCount))
    }

    fun restart(): Game = undoTo(0)

    companion object {
        fun start(initial: GameState, ruleSet: RuleSet): Game = Game(ruleSet, listOf(initial.copy()), emptyList())

        /** Rebuilds a game from a saved move list. Throws if any move is illegal. */
        fun replay(initial: GameState, ruleSet: RuleSet, moves: List<Move>): Game =
            moves.fold(start(initial, ruleSet)) { g, m -> g.play(m) }
    }
}
