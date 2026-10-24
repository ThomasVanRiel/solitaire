package io.github.thomasvanriel.winnable.engine.rules

import io.github.thomasvanriel.winnable.engine.model.Card
import io.github.thomasvanriel.winnable.engine.model.GameState
import io.github.thomasvanriel.winnable.engine.model.Move
import io.github.thomasvanriel.winnable.engine.model.RuleSet

/**
 * The one and only Klondike rules implementation. The game, the solver and the proof verifier all use it.
 *
 * Fast path (solver): [legalMoveCodes], [applyCode] and [undoCode] change a [GameState] in place
 * and work on int move codes (see [Move]).
 * Convenience path (UI, tests): [legalMoves], [isLegal] and [apply], which returns a new state.
 */
object Rules {
    /** More than the number of legal moves in any position. */
    const val MAX_MOVES = 640

    // Undo token layout: bits 0-12 move code, bit 13 "a card was flipped", bits 14-17 cards drawn,
    // bits 18-19 suit of a card that went to a foundation.
    private const val FLIP_BIT = 1 shl Move.CODE_BITS
    private const val DRAWN_SHIFT = Move.CODE_BITS + 1
    private const val SUIT_SHIFT = DRAWN_SHIFT + 4

    fun isWon(s: GameState): Boolean = s.found[0] == 13 && s.found[1] == 13 && s.found[2] == 13 && s.found[3] == 13

    fun canRecycle(s: GameState, rs: RuleSet): Boolean =
        s.stockSize == 0 && s.wasteSize > 0 && (rs.maxRecycles < 0 || s.recycles < rs.maxRecycles)

    /** True if [card] may be placed on column [col] (on a matching card, or a King on an empty column). */
    fun fitsOnColumn(s: GameState, card: Int, col: Int): Boolean {
        val n = s.tabSize[col]
        return if (n == 0) Card.rank(card) == Card.KING else Card.stacksOn(card, s.tab[col][n - 1])
    }

    fun fitsOnFoundation(s: GameState, card: Int): Boolean = s.found[Card.suit(card)] == Card.rank(card)

    /**
     * Writes the codes of every legal move into [out] and returns how many there are.
     * The order is fixed, so results are deterministic.
     */
    fun legalMoveCodes(s: GameState, rs: RuleSet, out: IntArray): Int {
        var n = 0
        val cols = GameState.COLUMNS

        // Waste
        if (s.wasteSize > 0) {
            val w = s.waste[s.wasteSize - 1]
            if (fitsOnFoundation(s, w)) out[n++] = Move.encode(Move.TYPE_W2F, 0, 0, 0)
            for (c in 0 until cols) if (fitsOnColumn(s, w, c)) out[n++] = Move.encode(Move.TYPE_W2T, c, 0, 0)
        }

        // Tableau to foundation, and tableau to tableau
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size == 0) continue
            val col = s.tab[from]
            if (fitsOnFoundation(s, col[size - 1])) out[n++] = Move.encode(Move.TYPE_T2F, from, 0, 0)
            val up = size - s.tabDown[from]
            var count = 1
            while (count <= up) {
                val card = col[size - count]
                // A run must be a proper alternating sequence; stop at the first break.
                if (count > 1 && !Card.stacksOn(col[size - count + 1], card)) break
                for (to in 0 until cols) {
                    if (to != from && fitsOnColumn(s, card, to)) out[n++] = Move.encode(Move.TYPE_T2T, from, to, count)
                }
                count++
            }
        }

        // Foundation back to tableau
        for (suit in 0 until 4) {
            val f = s.found[suit]
            if (f == 0) continue
            val card = Card.of(suit, f - 1)
            for (to in 0 until cols) if (fitsOnColumn(s, card, to)) out[n++] = Move.encode(Move.TYPE_F2T, suit, to, 0)
        }

        // Stock
        if (s.stockSize > 0) out[n++] = Move.encode(Move.TYPE_DRAW, 0, 0, 0)
        else if (canRecycle(s, rs)) out[n++] = Move.encode(Move.TYPE_RECYCLE, 0, 0, 0)

        return n
    }

    fun legalMoves(s: GameState, rs: RuleSet): List<Move> {
        val buf = IntArray(MAX_MOVES)
        val n = legalMoveCodes(s, rs, buf)
        return List(n) { Move.decode(buf[it]) }
    }

    /** Legality is defined as "appears in the legal move list", so there is only one definition of it. */
    fun isLegalCode(s: GameState, rs: RuleSet, code: Int): Boolean {
        val buf = IntArray(MAX_MOVES)
        val n = legalMoveCodes(s, rs, buf)
        for (i in 0 until n) if (buf[i] == code) return true
        return false
    }

    fun isLegal(s: GameState, rs: RuleSet, move: Move): Boolean = isLegalCode(s, rs, move.code)

    /** Returns a new state with [move] applied. Throws if the move is not legal. */
    fun apply(s: GameState, rs: RuleSet, move: Move): GameState {
        require(isLegal(s, rs, move)) { "Illegal move $move in position:\n$s" }
        val next = s.copy()
        applyCode(next, rs, move.code)
        return next
    }

    /**
     * Applies a move in place, with **no legality check**. Returns an undo token for [undoCode].
     * Face-down cards that become exposed are flipped automatically.
     */
    fun applyCode(s: GameState, rs: RuleSet, code: Int): Int {
        when (Move.type(code)) {
            Move.TYPE_DRAW -> {
                val k = minOf(rs.drawCount, s.stockSize)
                repeat(k) { s.waste[s.wasteSize++] = s.stock[--s.stockSize] }
                return code or (k shl DRAWN_SHIFT)
            }
            Move.TYPE_RECYCLE -> {
                while (s.wasteSize > 0) s.stock[s.stockSize++] = s.waste[--s.wasteSize]
                s.recycles++
                return code
            }
            Move.TYPE_W2T -> {
                val to = Move.a(code)
                s.tab[to][s.tabSize[to]++] = s.waste[--s.wasteSize]
                return code
            }
            Move.TYPE_W2F -> {
                val suit = Card.suit(s.waste[--s.wasteSize])
                s.found[suit]++
                return code or (suit shl SUIT_SHIFT)
            }
            Move.TYPE_T2F -> {
                val from = Move.a(code)
                val suit = Card.suit(s.tab[from][--s.tabSize[from]])
                s.found[suit]++
                return code or (suit shl SUIT_SHIFT) or flipIfNeeded(s, from)
            }
            Move.TYPE_T2T -> {
                val from = Move.a(code)
                val to = Move.b(code)
                val count = Move.c(code)
                val src = s.tab[from]
                val dst = s.tab[to]
                val start = s.tabSize[from] - count
                System.arraycopy(src, start, dst, s.tabSize[to], count)
                s.tabSize[to] += count
                s.tabSize[from] = start
                return code or flipIfNeeded(s, from)
            }
            Move.TYPE_F2T -> {
                val suit = Move.a(code)
                val to = Move.b(code)
                s.found[suit]--
                s.tab[to][s.tabSize[to]++] = Card.of(suit, s.found[suit])
                return code
            }
            else -> throw IllegalArgumentException("Bad move code $code")
        }
    }

    private fun flipIfNeeded(s: GameState, col: Int): Int {
        val size = s.tabSize[col]
        if (size > 0 && s.tabDown[col] == size) {
            s.tabDown[col]--
            return FLIP_BIT
        }
        return 0
    }

    /** Exactly reverses the [applyCode] call that returned [token]. */
    fun undoCode(s: GameState, rs: RuleSet, token: Int) {
        val code = token and Move.CODE_MASK
        val flipped = (token and FLIP_BIT) != 0
        when (Move.type(code)) {
            Move.TYPE_DRAW -> {
                val k = (token ushr DRAWN_SHIFT) and 15
                repeat(k) { s.stock[s.stockSize++] = s.waste[--s.wasteSize] }
            }
            Move.TYPE_RECYCLE -> {
                while (s.stockSize > 0) s.waste[s.wasteSize++] = s.stock[--s.stockSize]
                s.recycles--
            }
            Move.TYPE_W2T -> {
                val to = Move.a(code)
                s.waste[s.wasteSize++] = s.tab[to][--s.tabSize[to]]
            }
            Move.TYPE_W2F -> {
                val suit = (token ushr SUIT_SHIFT) and 3
                s.waste[s.wasteSize++] = Card.of(suit, --s.found[suit])
            }
            Move.TYPE_T2F -> {
                val from = Move.a(code)
                val suit = (token ushr SUIT_SHIFT) and 3
                if (flipped) s.tabDown[from]++
                s.tab[from][s.tabSize[from]++] = Card.of(suit, --s.found[suit])
            }
            Move.TYPE_T2T -> {
                val from = Move.a(code)
                val to = Move.b(code)
                val count = Move.c(code)
                if (flipped) s.tabDown[from]++
                val start = s.tabSize[to] - count
                System.arraycopy(s.tab[to], start, s.tab[from], s.tabSize[from], count)
                s.tabSize[from] += count
                s.tabSize[to] = start
            }
            Move.TYPE_F2T -> {
                val suit = Move.a(code)
                val to = Move.b(code)
                s.tabSize[to]--
                s.found[suit]++
            }
        }
    }
}
