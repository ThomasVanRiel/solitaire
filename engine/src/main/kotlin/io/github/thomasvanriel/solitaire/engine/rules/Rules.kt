package io.github.thomasvanriel.solitaire.engine.rules

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/**
 * The one and only rules implementation, for every game in [GameType]. The game, the solvers and the proof
 * verifier all use it.
 *
 * Fast path (solver): [legalMoveCodes], [applyCode] and [undoCode] change a [GameState] in place
 * and work on int move codes (see [Move]).
 * Convenience path (UI, tests): [legalMoves], [isLegal] and [apply], which returns a new state.
 */
object Rules {
    /** More than the number of legal moves in any position of any game. */
    const val MAX_MOVES = 2048

    /** Cards in a complete run, which leaves the board in Spider and Scorpion. */
    const val RUN = 13

    // Undo token layout: bits 0-16 move code, bit 17 "a card was flipped", bits 18-21 cards drawn or dealt,
    // bits 22-23 suit of the card(s) that went to a foundation.
    private const val FLIP_BIT = 1 shl Move.CODE_BITS
    private const val DRAWN_SHIFT = Move.CODE_BITS + 1
    private const val SUIT_SHIFT = DRAWN_SHIFT + 4

    /** Won when every card has left the board (in Klondike: all 52 are on the foundations). */
    fun isWon(s: GameState): Boolean {
        if (s.stockSize != 0 || s.wasteSize != 0) return false
        for (c in 0 until s.columns) if (s.tabSize[c] != 0) return false
        for (c in s.cells) if (c >= 0) return false
        return true
    }

    fun canRecycle(s: GameState, rs: RuleSet): Boolean =
        rs.game == GameType.KLONDIKE &&
            s.stockSize == 0 && s.wasteSize > 0 && (rs.maxRecycles < 0 || s.recycles < rs.maxRecycles)

    /** Spider and Scorpion: true if column [col] ends in a complete face-up King-to-Ace run of one suit. */
    fun hasCompleteRun(s: GameState, col: Int): Boolean {
        val size = s.tabSize[col]
        if (size < RUN || size - RUN < s.tabDown[col]) return false
        val c = s.tab[col]
        if (Card.rank(c[size - 1]) != Card.ACE) return false
        for (i in size - RUN + 1 until size) if (!Card.followsInSuit(c[i], c[i - 1])) return false
        return true
    }

    /** Spider: can a deal be made? It needs cards in the stock and no empty column. */
    fun canDeal(s: GameState, rs: RuleSet): Boolean {
        if (s.stockSize == 0) return false
        if (rs.game == GameType.SPIDER) for (c in 0 until s.columns) if (s.tabSize[c] == 0) return false
        return true
    }

    /** Columns that one deal from the stock covers (Spider: all of them; Scorpion: the first three). */
    private fun dealColumns(s: GameState, rs: RuleSet): Int = if (rs.game == GameType.SCORPION) 3 else s.columns

    /** Klondike: true if [card] may be placed on column [col] (on a matching card, or a King on an empty column). */
    fun fitsOnColumn(s: GameState, card: Int, col: Int): Boolean {
        val n = s.tabSize[col]
        return if (n == 0) Card.rank(card) == Card.KING else Card.stacksOn(card, s.tab[col][n - 1])
    }

    /** True if [card] (heading a movable group) may be placed on column [col] under [rs]'s rules. */
    fun fitsOnColumn(s: GameState, rs: RuleSet, card: Int, col: Int): Boolean {
        val n = s.tabSize[col]
        return when (rs.game) {
            GameType.KLONDIKE -> fitsOnColumn(s, card, col)
            GameType.SPIDER -> n == 0 || Card.rank(card) + 1 == Card.rank(s.tab[col][n - 1])
            GameType.SCORPION -> if (n == 0) Card.rank(card) == Card.KING else Card.followsInSuit(card, s.tab[col][n - 1])
            GameType.YUKON -> if (n == 0) Card.rank(card) == Card.KING else stacks(rs, card, s.tab[col][n - 1])
            GameType.FREECELL -> n == 0 || Card.stacksOn(card, s.tab[col][n - 1])
        }
    }

    /** Yukon and FreeCell: [card] may rest on [onto] (alternating colours, or in suit for Russian). */
    fun stacks(rs: RuleSet, card: Int, onto: Int): Boolean =
        if (rs.buildInSuit) Card.followsInSuit(card, onto) else Card.stacksOn(card, onto)

    /**
     * FreeCell: the longest run that may move as one move onto column [to]: as many as could be moved card
     * by card using the free cells and the other empty columns, (free cells + 1) × 2^(empty columns).
     */
    fun maxRunMove(s: GameState, to: Int): Int {
        var empty = 0
        for (c in 0 until s.columns) if (c != to && s.tabSize[c] == 0) empty++
        return (s.freeCells() + 1) shl empty
    }

    fun fitsOnFoundation(s: GameState, card: Int): Boolean = s.found[Card.suit(card)] == Card.rank(card)

    /**
     * Writes the codes of every legal move into [out] and returns how many there are.
     * The order is fixed, so results are deterministic.
     */
    fun legalMoveCodes(s: GameState, rs: RuleSet, out: IntArray): Int = when (rs.game) {
        GameType.KLONDIKE -> klondikeMoves(s, rs, out)
        GameType.SPIDER, GameType.SCORPION -> runGameMoves(s, rs, out)
        GameType.YUKON -> yukonMoves(s, rs, out)
        GameType.FREECELL -> freeCellMoves(s, rs, out)
    }

    /**
     * Yukon: to the foundations, then any face-up card with everything on top of it onto another column,
     * then foundation cards back down.
     */
    private fun yukonMoves(s: GameState, rs: RuleSet, out: IntArray): Int {
        var n = 0
        val cols = s.columns
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size > 0 && fitsOnFoundation(s, s.tab[from][size - 1])) out[n++] = Move.encode(Move.TYPE_T2F, from, 0, 0)
        }
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            val col = s.tab[from]
            for (i in s.tabDown[from] until size) {
                val card = col[i]
                for (to in 0 until cols) {
                    if (to != from && fitsOnColumn(s, rs, card, to)) out[n++] = Move.encode(Move.TYPE_T2T, from, to, size - i)
                }
            }
        }
        for (suit in 0 until 4) {
            val f = s.found[suit]
            if (f == 0) continue
            val card = Card.of(suit, f - 1)
            for (to in 0 until cols) if (fitsOnColumn(s, rs, card, to)) out[n++] = Move.encode(Move.TYPE_F2T, suit, to, 0)
        }
        return n
    }

    /**
     * FreeCell: free cells to the foundations, columns to the foundations, free cells onto columns, runs
     * onto columns (see [maxRunMove]), then column tops into a free cell.
     */
    private fun freeCellMoves(s: GameState, rs: RuleSet, out: IntArray): Int {
        var n = 0
        val cols = s.columns
        val cells = s.cells
        for (k in cells.indices) {
            if (cells[k] >= 0 && fitsOnFoundation(s, cells[k])) out[n++] = Move.encode(Move.TYPE_CELL, k, 0, Move.CELL_UP)
        }
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size > 0 && fitsOnFoundation(s, s.tab[from][size - 1])) out[n++] = Move.encode(Move.TYPE_T2F, from, 0, 0)
        }
        for (k in cells.indices) {
            if (cells[k] < 0) continue
            for (to in 0 until cols) if (fitsOnColumn(s, rs, cells[k], to)) out[n++] = Move.encode(Move.TYPE_CELL, k, to, Move.CELL_OUT)
        }
        val free = s.freeCells()
        var empty = 0
        for (c in 0 until cols) if (s.tabSize[c] == 0) empty++
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size == 0) continue
            val col = s.tab[from]
            val up = size - s.tabDown[from]
            var count = 1
            while (count <= up) {
                val card = col[size - count]
                if (count > 1 && !Card.stacksOn(col[size - count + 1], card)) break
                for (to in 0 until cols) {
                    if (to == from || !fitsOnColumn(s, rs, card, to)) continue
                    val otherEmpty = if (s.tabSize[to] == 0) empty - 1 else empty
                    if (count <= (free + 1) shl otherEmpty) out[n++] = Move.encode(Move.TYPE_T2T, from, to, count)
                }
                count++
            }
        }
        if (free > 0) {
            for (from in 0 until cols) if (s.tabSize[from] > 0) out[n++] = Move.encode(Move.TYPE_CELL, from, 0, Move.CELL_IN)
        }
        return n
    }

    /**
     * Spider and Scorpion. A complete King-to-Ace run must leave the board straight away: while one is
     * showing, taking it off is the only legal move. Then group moves, then the deal.
     */
    private fun runGameMoves(s: GameState, rs: RuleSet, out: IntArray): Int {
        var n = 0
        val cols = s.columns
        for (c in 0 until cols) if (hasCompleteRun(s, c)) out[n++] = Move.encode(Move.TYPE_T2F, c, 0, 0)
        if (n > 0) return n

        val spider = rs.game == GameType.SPIDER
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size == 0) continue
            val col = s.tab[from]
            val up = size - s.tabDown[from]
            var count = 1
            while (count <= up) {
                val card = col[size - count]
                // Spider moves only same-suit runs together. Scorpion moves any face-up card with all above it.
                if (spider && count > 1 && !Card.followsInSuit(col[size - count + 1], card)) break
                for (to in 0 until cols) {
                    if (to != from && fitsOnColumn(s, rs, card, to)) out[n++] = Move.encode(Move.TYPE_T2T, from, to, count)
                }
                count++
            }
        }
        if (canDeal(s, rs)) out[n++] = Move.encode(Move.TYPE_DRAW, 0, 0, 0)
        return n
    }

    private fun klondikeMoves(s: GameState, rs: RuleSet, out: IntArray): Int {
        var n = 0
        val cols = s.columns

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
                if (rs.game != GameType.KLONDIKE) {
                    // Deal one card face up onto each of the first k columns, left to right.
                    val k = minOf(dealColumns(s, rs), s.stockSize)
                    for (c in 0 until k) s.tab[c][s.tabSize[c]++] = s.stock[--s.stockSize]
                    return code or (k shl DRAWN_SHIFT)
                }
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
                if (!rs.buildsFoundations) {
                    // A complete run leaves the board.
                    val suit = Card.suit(s.tab[from][s.tabSize[from] - 1])
                    s.tabSize[from] -= RUN
                    s.found[suit] += RUN
                    return code or (suit shl SUIT_SHIFT) or flipIfNeeded(s, from)
                }
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
            Move.TYPE_CELL -> when (Move.c(code)) {
                Move.CELL_IN -> {
                    val from = Move.a(code)
                    val k = s.firstFreeCell()
                    s.cells[k] = s.tab[from][--s.tabSize[from]]
                    return code or (k shl DRAWN_SHIFT) or flipIfNeeded(s, from)
                }
                Move.CELL_OUT -> {
                    val k = Move.a(code)
                    val to = Move.b(code)
                    s.tab[to][s.tabSize[to]++] = s.cells[k]
                    s.cells[k] = -1
                    return code
                }
                else -> {
                    val k = Move.a(code)
                    val suit = Card.suit(s.cells[k])
                    s.found[suit]++
                    s.cells[k] = -1
                    return code or (suit shl SUIT_SHIFT)
                }
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
                if (rs.game != GameType.KLONDIKE) {
                    for (c in k - 1 downTo 0) s.stock[s.stockSize++] = s.tab[c][--s.tabSize[c]]
                } else {
                    repeat(k) { s.stock[s.stockSize++] = s.waste[--s.wasteSize] }
                }
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
                if (!rs.buildsFoundations) {
                    for (r in Card.KING downTo Card.ACE) s.tab[from][s.tabSize[from]++] = Card.of(suit, r)
                    s.found[suit] -= RUN
                } else {
                    s.tab[from][s.tabSize[from]++] = Card.of(suit, --s.found[suit])
                }
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
            Move.TYPE_CELL -> when (Move.c(code)) {
                Move.CELL_IN -> {
                    val from = Move.a(code)
                    val k = (token ushr DRAWN_SHIFT) and 15
                    if (flipped) s.tabDown[from]++
                    s.tab[from][s.tabSize[from]++] = s.cells[k]
                    s.cells[k] = -1
                }
                Move.CELL_OUT -> {
                    val to = Move.b(code)
                    s.cells[Move.a(code)] = s.tab[to][--s.tabSize[to]]
                }
                else -> {
                    val suit = (token ushr SUIT_SHIFT) and 3
                    s.cells[Move.a(code)] = Card.of(suit, --s.found[suit])
                }
            }
        }
    }
}
