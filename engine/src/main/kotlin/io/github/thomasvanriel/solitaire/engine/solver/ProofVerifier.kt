package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals

/**
 * The winnability guarantee rests on this object. It deliberately does nothing clever:
 * deal the cards, check every move is legal with [Rules], play it with [Rules], and require a won game.
 */
object ProofVerifier {
    fun verify(seed: Long, ruleSet: RuleSet, moves: List<Move>, shuffleVersion: Int = Deals.SHUFFLE_V1): Boolean =
        verifyFrom(Deals.deal(seed, ruleSet, shuffleVersion), ruleSet, moves)

    fun verifyFrom(start: GameState, ruleSet: RuleSet, moves: List<Move>): Boolean {
        var s = start
        for (m in moves) {
            if (!Rules.isLegal(s, ruleSet, m)) return false
            s = Rules.apply(s, ruleSet, m)
        }
        return Rules.isWon(s)
    }
}
