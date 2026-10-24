package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.Move

sealed class SolveResult {
    /** Search nodes expanded. */
    abstract val nodes: Long

    /** A complete winning move list from the start position, made of ordinary moves only. */
    class Solved(val moves: List<Move>, override val nodes: Long) : SolveResult() {
        override fun toString() = "Solved(${moves.size} moves, $nodes nodes)"
    }

    /** The search space was exhausted without finding a win (may be a false negative, never a false positive). */
    class Unsolvable(override val nodes: Long) : SolveResult() {
        override fun toString() = "Unsolvable($nodes nodes)"
    }

    /** Ran out of budget or was cancelled. */
    class Unknown(override val nodes: Long) : SolveResult() {
        override fun toString() = "Unknown($nodes nodes)"
    }
}
