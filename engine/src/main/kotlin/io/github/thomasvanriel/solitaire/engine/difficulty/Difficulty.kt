package io.github.thomasvanriel.solitaire.engine.difficulty

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
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

/** A fair-information player: plays one game and says whether it won. */
interface Player {
    fun play(start: GameState): Boolean
}

/**
 * How hard a deal is for someone who can't see the hidden cards: the share of [playouts] games that a
 * fair-information player ([HeuristicPlayer], [RunGamePlayer]) wins. Ratings are deterministic for a given seed.
 *
 * Spider is the exception. A simple player almost never wins 2- or 4-suit Spider, so counting wins can't
 * tell deals apart. There, each lost game scores partial credit for how far it got ([RunGamePlayer.progress],
 * at most [SPIDER_LOSS_CAP]), a win scores 1, and the rating is the average in percent (0-100).
 */
object DifficultyRater {
    const val DEFAULT_PLAYOUTS = 30

    fun wins(start: GameState, ruleSet: RuleSet, rngSeed: Long, playouts: Int = DEFAULT_PLAYOUTS): Int {
        val rng = Xoshiro256.seeded(rngSeed xor 0x0D1FF1C0L)
        if (ruleSet.game == GameType.SPIDER) {
            val player = RunGamePlayer(ruleSet, rng)
            var total = 0.0
            repeat(playouts) { total += if (player.play(start)) 1.0 else SPIDER_LOSS_CAP * player.progress }
            return Math.round(100.0 * total / playouts).toInt().coerceIn(0, 100)
        }
        val player: Player = if (ruleSet.game == GameType.KLONDIKE) HeuristicPlayer(ruleSet, rng) else RunGamePlayer(ruleSet, rng)
        var wins = 0
        repeat(playouts) { if (player.play(start)) wins++ }
        return wins
    }

    /** The highest rating [wins] can return: [playouts], or 100 for Spider (a percentage). */
    fun maxRating(ruleSet: RuleSet, playouts: Int): Int = if (ruleSet.game == GameType.SPIDER) 100 else playouts

    /** The most a lost Spider game adds to the rating (a win adds 1). */
    const val SPIDER_LOSS_CAP = 0.8

    /**
     * Tier cut-offs on the heuristic player's wins, scaled to 30 playouts, per rule set. They were calibrated
     * from 1,000 winnable deals per rule set so each tier gets plenty of deals (see PROGRESS.md, M3).
     * Expert means the heuristic player never won, although the solver proved the deal winnable.
     */
    fun tierFor(wins: Int, playouts: Int, ruleSet: RuleSet): Tier {
        require(playouts > 0 && wins in 0..maxRating(ruleSet, playouts))
        if (ruleSet.game == GameType.SPIDER) {
            // Spider ratings are percentages; the cut-offs are (Expert max, Hard max, Medium max).
            val (expertMax, hardMax, mediumMax) = spiderCutoffs(ruleSet)
            return when {
                wins <= expertMax -> Tier.EXPERT
                wins <= hardMax -> Tier.HARD
                wins <= mediumMax -> Tier.MEDIUM
                else -> Tier.EASY
            }
        }
        if (wins == 0) return Tier.EXPERT
        val (hardMax, mediumMax) = cutoffs(ruleSet)
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
    val SCORPION_CUTOFFS = 5 to 16

    /** Spider, on the percentage rating: (most for Expert, most for Hard, most for Medium). */
    val SPIDER1_CUTOFFS = Triple(33, 44, 51)
    val SPIDER2_CUTOFFS = Triple(14, 17, 20)
    val SPIDER4_CUTOFFS = Triple(11, 13, 15)

    fun cutoffs(ruleSet: RuleSet): Pair<Int, Int> = when (ruleSet.game) {
        GameType.KLONDIKE -> if (ruleSet.drawCount >= 3) DRAW3_CUTOFFS else DRAW1_CUTOFFS
        GameType.SCORPION -> SCORPION_CUTOFFS
        GameType.SPIDER -> throw IllegalArgumentException("Spider uses spiderCutoffs")
    }

    fun spiderCutoffs(ruleSet: RuleSet): Triple<Int, Int, Int> = when (ruleSet.suits) {
        1 -> SPIDER1_CUTOFFS
        2 -> SPIDER2_CUTOFFS
        else -> SPIDER4_CUTOFFS
    }
}
