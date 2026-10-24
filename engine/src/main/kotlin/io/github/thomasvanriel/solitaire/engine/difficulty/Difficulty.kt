package io.github.thomasvanriel.solitaire.engine.difficulty

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

enum class Tier(val id: Int, val label: String) {
    EASY(0, "Easy"),
    MEDIUM(1, "Medium"),
    HARD(2, "Hard"),
    EXPERT(3, "Expert");

    companion object {
        fun byId(id: Int): Tier = entries.first { it.id == id }
    }
}

/**
 * How hard a deal is for someone who can't see the hidden cards: the share of [playouts] games that the
 * [HeuristicPlayer] wins. Ratings are deterministic for a given seed.
 */
object DifficultyRater {
    const val DEFAULT_PLAYOUTS = 30

    fun wins(start: GameState, ruleSet: RuleSet, rngSeed: Long, playouts: Int = DEFAULT_PLAYOUTS): Int {
        val player = HeuristicPlayer(ruleSet, Xoshiro256.seeded(rngSeed xor 0x0D1FF1C0L))
        var wins = 0
        repeat(playouts) { if (player.play(start)) wins++ }
        return wins
    }

    /**
     * Tier cut-offs on the heuristic win rate, calibrated from the real distribution (see PROGRESS.md, M3).
     * Expert means the heuristic player never won, although the solver proved the deal winnable.
     */
    fun tierFor(wins: Int, playouts: Int): Tier {
        val rate = wins.toDouble() / playouts
        return when {
            wins == 0 -> Tier.EXPERT
            rate < HARD_BELOW -> Tier.HARD
            rate < MEDIUM_BELOW -> Tier.MEDIUM
            else -> Tier.EASY
        }
    }

    // Placeholder values until calibration.
    const val HARD_BELOW = 0.15
    const val MEDIUM_BELOW = 0.5
}
