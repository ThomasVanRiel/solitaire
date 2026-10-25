package io.github.thomasvanriel.solitaire.engine.hint

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules

/** What [MoveAdvisor] found for a position. */
sealed class Advice {
    /** [move] helps, or is the first step towards a move that helps. It is a legal move, not the best one. */
    data class Hint(val move: Move) : Advice()

    /** No move that helps can ever be reached: the game can't be won from here. */
    data object Stuck : Advice()

    /** The search ran out of room before it could decide. Treat as "not stuck". */
    data object Unknown : Advice()

    data object Won : Advice()
}

/**
 * Finds helpful moves, for the Hint button and the "no moves left" check.
 *
 * A move *helps* when it can't be taken back: it turns over a face-down card, plays a card from the stock or
 * waste, deals (Spider, Scorpion II) or takes off a complete run. Every other move only rearranges face-up
 * cards (or cycles the Klondike stock), and there are only finitely many such arrangements. So the search
 * walks through them, breadth first, looking for one where a helpful move is legal. If there is none, the
 * player can only shuffle cards around forever: they are stuck.
 *
 * It only uses the moves of [Rules], and only sees what the player could see by cycling the stock: it never
 * looks under a face-down card or at the Spider stock (a deal is not played, only noted as possible).
 * It doesn't look for the best move, so a hint never solves the game for the player.
 */
object MoveAdvisor {
    /** Enough for nearly every real position; bigger searches give [Advice.Unknown]. */
    const val DEFAULT_MAX_STATES = 5_000

    fun advise(start: GameState, rs: RuleSet, maxStates: Int = DEFAULT_MAX_STATES): Advice {
        if (Rules.isWon(start)) return Advice.Won
        helpfulMove(start, rs)?.let { return Advice.Hint(it) }

        // Breadth first through the positions reachable with moves that don't help, remembering each one's
        // first move. Positions that have a helpful move are never expanded, so everything queued is reachable
        // without one.
        val seen = HashSet<GameState>()
        seen += key(start, rs)
        val queue = ArrayDeque<Pair<GameState, Move>>()
        for (m in Rules.legalMoves(start, rs)) {
            val next = Rules.apply(start, rs, m)
            if (seen.add(key(next, rs))) queue.addLast(next to m)
        }
        while (queue.isNotEmpty()) {
            val (s, first) = queue.removeFirst()
            if (Rules.isWon(s) || helpfulMove(s, rs) != null) return Advice.Hint(first)
            if (seen.size > maxStates) return Advice.Unknown
            for (m in Rules.legalMoves(s, rs)) {
                val next = Rules.apply(s, rs, m)
                if (seen.add(key(next, rs))) queue.addLast(next to first)
            }
        }
        return Advice.Stuck
    }

    /** A helpful move legal in [s], if any: turning a card over first, then playing a card, then dealing. */
    fun helpfulMove(s: GameState, rs: RuleSet): Move? {
        var best: Move? = null
        var bestRank = Int.MAX_VALUE
        for (m in Rules.legalMoves(s, rs)) {
            val rank = helpRank(s, Rules.apply(s, rs, m), rs) ?: continue
            if (rank < bestRank) {
                best = m
                bestRank = rank
            }
        }
        return best
    }

    /** How a move from [before] to [after] helps (lower is shown first), or null if it doesn't. */
    private fun helpRank(before: GameState, after: GameState, rs: RuleSet): Int? = when {
        after.foundationCount > before.foundationCount && rs.game != GameType.KLONDIKE -> 0 // a run comes off
        faceDown(after) < faceDown(before) -> 1
        rs.game == GameType.KLONDIKE && after.stockSize + after.wasteSize < before.stockSize + before.wasteSize -> 2
        rs.game != GameType.KLONDIKE && after.stockSize < before.stockSize -> 3 // a deal
        else -> null
    }

    private fun faceDown(s: GameState): Int {
        var n = 0
        for (c in 0 until s.columns) n += s.tabDown[c]
        return n
    }

    /** With unlimited recycles the recycle count doesn't matter, so positions that differ only in it are the same. */
    private fun key(s: GameState, rs: RuleSet): GameState =
        if (rs.unlimitedRecycles && s.recycles != 0) s.copy().also { it.recycles = 0 } else s
}
