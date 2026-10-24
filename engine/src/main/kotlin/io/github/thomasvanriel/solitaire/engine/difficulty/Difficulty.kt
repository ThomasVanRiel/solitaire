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
     * Tier cut-offs on the heuristic player's wins, scaled to 30 playouts, per rule set. They were calibrated
     * from 1,000 winnable deals per rule set so each tier gets plenty of deals (see PROGRESS.md, M3).
     * Expert means the heuristic player never won, although the solver proved the deal winnable.
     */
    fun tierFor(wins: Int, playouts: Int, ruleSet: RuleSet): Tier {
        require(playouts > 0 && wins in 0..playouts)
        if (wins == 0) return Tier.EXPERT
        val (hardMax, mediumMax) = if (ruleSet.drawCount >= 3) DRAW3_CUTOFFS else DRAW1_CUTOFFS
        val of30 = wins * 30
        return when {
            of30 <= hardMax * playouts -> Tier.HARD
            of30 <= mediumMax * playouts -> Tier.MEDIUM
            else -> Tier.EASY
        }
    }

    /** (most wins out of 30 for Hard, most wins out of 30 for Medium). */
    val DRAW1_CUTOFFS = 6 to 20
    val DRAW3_CUTOFFS = 3 to 11
}
