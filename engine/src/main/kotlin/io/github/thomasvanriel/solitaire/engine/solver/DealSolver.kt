package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/** A full-information solver for one rule set. */
interface DealSolver {
    fun solve(start: GameState): SolveResult

    companion object {
        /** The solver for [ruleSet]'s game. */
        fun create(
            ruleSet: RuleSet,
            maxNodes: Long = 2_000_000,
            timeLimitMs: Long = 10_000,
            isCancelled: () -> Boolean = { false },
        ): DealSolver = when (ruleSet.game) {
            GameType.KLONDIKE -> Solver(ruleSet, maxNodes, timeLimitMs, isCancelled)
            GameType.SPIDER, GameType.SCORPION -> RunGameSolver(ruleSet, maxNodes, timeLimitMs, isCancelled)
        }
    }
}
