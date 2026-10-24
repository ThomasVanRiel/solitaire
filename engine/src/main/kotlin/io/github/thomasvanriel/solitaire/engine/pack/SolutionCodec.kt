package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules

/**
 * Stores a move list as one byte per move: the move's index in [Rules.legalMoveCodes] at that point.
 * Small and very compressible. Decoding can only ever produce legal moves, but the decoded list is still
 * checked by [io.github.thomasvanriel.solitaire.engine.solver.ProofVerifier] before a deal is served.
 */
object SolutionCodec {
    /** Throws if a move is illegal or its index doesn't fit in a byte. */
    fun encode(start: GameState, ruleSet: RuleSet, moves: List<Move>): ByteArray {
        val s = start.copy()
        val buf = IntArray(Rules.MAX_MOVES)
        val out = ByteArray(moves.size)
        moves.forEachIndexed { i, m ->
            val n = Rules.legalMoveCodes(s, ruleSet, buf)
            var idx = -1
            for (k in 0 until n) if (buf[k] == m.code) { idx = k; break }
            require(idx >= 0) { "Move $i ($m) is illegal" }
            require(idx < 256) { "Move $i has index $idx, too big for a byte" }
            out[i] = idx.toByte()
            Rules.applyCode(s, ruleSet, m.code)
        }
        return out
    }

    /** Throws if an index is out of range. */
    fun decode(start: GameState, ruleSet: RuleSet, bytes: ByteArray): List<Move> {
        val s = start.copy()
        val buf = IntArray(Rules.MAX_MOVES)
        return List(bytes.size) { i ->
            val n = Rules.legalMoveCodes(s, ruleSet, buf)
            val idx = bytes[i].toInt() and 0xFF
            require(idx < n) { "Move $i: index $idx but only $n legal moves" }
            val code = buf[idx]
            Rules.applyCode(s, ruleSet, code)
            Move.decode(code)
        }
    }
}
