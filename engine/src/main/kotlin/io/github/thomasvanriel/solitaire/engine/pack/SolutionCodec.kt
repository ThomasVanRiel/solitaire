package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules

/**
 * Stores a move list as the index of each move in [Rules.legalMoveCodes] at that point: one byte for
 * indexes 0-254, or the byte 255 followed by the index in two bytes (only Spider gets that many choices).
 * Small and very compressible. Decoding can only ever produce legal moves, but the decoded list is still
 * checked by [io.github.thomasvanriel.solitaire.engine.solver.ProofVerifier] before a deal is served.
 */
object SolutionCodec {
    private const val ESCAPE = 255

    /** Throws if a move is illegal. */
    fun encode(start: GameState, ruleSet: RuleSet, moves: List<Move>): ByteArray {
        val s = start.copy()
        val buf = IntArray(Rules.MAX_MOVES)
        val out = java.io.ByteArrayOutputStream(moves.size)
        moves.forEachIndexed { i, m ->
            val n = Rules.legalMoveCodes(s, ruleSet, buf)
            var idx = -1
            for (k in 0 until n) if (buf[k] == m.code) { idx = k; break }
            require(idx >= 0) { "Move $i ($m) is illegal" }
            if (idx < ESCAPE) {
                out.write(idx)
            } else {
                out.write(ESCAPE)
                out.write(idx shr 8)
                out.write(idx and 0xFF)
            }
            Rules.applyCode(s, ruleSet, m.code)
        }
        return out.toByteArray()
    }

    /** Throws if an index is out of range or the data is cut short. */
    fun decode(start: GameState, ruleSet: RuleSet, bytes: ByteArray): List<Move> {
        val s = start.copy()
        val buf = IntArray(Rules.MAX_MOVES)
        val moves = ArrayList<Move>(bytes.size)
        var p = 0
        while (p < bytes.size) {
            val n = Rules.legalMoveCodes(s, ruleSet, buf)
            var idx = bytes[p++].toInt() and 0xFF
            if (idx == ESCAPE) {
                require(p + 2 <= bytes.size) { "Move ${moves.size}: truncated index" }
                idx = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
                p += 2
            }
            require(idx < n) { "Move ${moves.size}: index $idx but only $n legal moves" }
            val code = buf[idx]
            Rules.applyCode(s, ruleSet, code)
            moves += Move.decode(code)
        }
        return moves
    }
}
