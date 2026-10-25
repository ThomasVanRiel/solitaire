package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

/**
 * Full-information solver for the games without a stock: Yukon, Russian Solitaire and FreeCell.
 * Depth-first search with a transposition table and restarts, like the other solvers.
 *
 * - Candidates are exactly the moves of [Rules.legalMoveCodes], ranked and some left out; nothing is
 *   generated on the side, so every recorded solution is a sequence of legal moves.
 * - Safe auto-moves: a card goes up without branching when no card could ever need it in the tableau
 *   (Aces and Twos; a card whose opposite-colour foundations both hold rank−1; in Russian, every card).
 * - Pruning that can only lose solutions, never invent them: only the first empty column is used (the
 *   columns are interchangeable), a whole column never moves into an empty one, a card resting on a card it
 *   may rest on only moves if that uncovers something useful, and foundation cards come down only when
 *   something could then go on them.
 * - Columns are hashed as a sum, so their order doesn't matter, and so are the free cells.
 *
 * The result is still replayed by [ProofVerifier] before anything is served.
 *
 * Not thread-safe: use one instance per thread. Instances can be reused.
 */
class OpenGameSolver(
    private val ruleSet: RuleSet,
    private val maxNodes: Long = 2_000_000,
    private val timeLimitMs: Long = 10_000,
    private val isCancelled: () -> Boolean = { false },
) : DealSolver {
    init {
        require(ruleSet.game == GameType.YUKON || ruleSet.game == GameType.FREECELL) { "Not a Yukon or FreeCell rule set" }
    }

    private val freeCell = ruleSet.game == GameType.FREECELL
    private val inSuit = ruleSet.buildInSuit
    private var s = GameState.forRules(ruleSet)
    private val path = IntArray(MAX_PATH)
    private val tokens = IntArray(MAX_PATH)
    private var pathLen = 0
    private var nodes = 0L
    private var deadline = 0L
    private var truncated = false
    private val seen = LongHashSet()

    private val legal = IntArray(Rules.MAX_MOVES)
    private val cands = Array(MAX_DEPTH) { IntArray(MAX_CANDIDATES) }
    private val keys = Array(MAX_DEPTH) { IntArray(MAX_CANDIDATES) }

    private class Abort : RuntimeException(null, null, false, false)
    private class OutOfBudget : RuntimeException(null, null, false, false)

    private var totalNodes = 0L
    private var attemptLimit = 0L
    private var shuffleRng: Xoshiro256? = null

    override fun solve(start: GameState): SolveResult {
        if (isCancelled()) return SolveResult.Unknown(0)
        deadline = System.nanoTime() + timeLimitMs * 1_000_000
        totalNodes = 0
        var budget = FIRST_ATTEMPT_NODES
        var attempt = 0
        while (true) {
            val remaining = maxNodes - totalNodes
            if (remaining <= 0) return SolveResult.Unknown(totalNodes)
            attemptLimit = if (budget * 3 > remaining) remaining else budget
            shuffleRng = if (attempt == 0) null else Xoshiro256.seeded(attempt.toLong())
            val r = attempt(start)
            totalNodes += nodes
            when (r) {
                ATTEMPT_SOLVED -> return SolveResult.Solved(List(pathLen) { Move.decode(path[it]) }, totalNodes)
                ATTEMPT_EXHAUSTED -> return if (truncated) SolveResult.Unknown(totalNodes) else SolveResult.Unsolvable(totalNodes)
                ATTEMPT_CANCELLED -> return SolveResult.Unknown(totalNodes)
            }
            budget *= 2
            attempt++
        }
    }

    private fun attempt(start: GameState): Int {
        s = start.copy()
        pathLen = 0
        nodes = 0
        truncated = false
        seen.clear()
        return try {
            if (search(0)) ATTEMPT_SOLVED else ATTEMPT_EXHAUSTED
        } catch (_: OutOfBudget) {
            ATTEMPT_OUT_OF_BUDGET
        } catch (_: Abort) {
            ATTEMPT_CANCELLED
        }
    }

    // ---------------------------------------------------------------- search

    private fun push(code: Int) {
        tokens[pathLen] = Rules.applyCode(s, ruleSet, code)
        path[pathLen++] = code
    }

    private fun popTo(len: Int) {
        while (pathLen > len) Rules.undoCode(s, ruleSet, tokens[--pathLen])
    }

    private fun search(depth: Int): Boolean {
        val mark = pathLen
        autoMoves()
        if (Rules.isWon(s)) return true
        if (depth >= MAX_DEPTH || pathLen > MAX_PATH - 64) {
            truncated = true
            popTo(mark)
            return false
        }
        if (!seen.add(hash())) {
            popTo(mark)
            return false
        }
        nodes++
        if (nodes > attemptLimit) throw OutOfBudget()
        if ((nodes and 1023L) == 0L && (System.nanoTime() > deadline || isCancelled())) throw Abort()

        val n = generate(depth)
        val c = cands[depth]
        for (i in 0 until n) {
            val before = pathLen
            push(c[i])
            if (search(depth + 1)) return true
            popTo(before)
        }
        popTo(mark)
        return false
    }

    /** A card no other card could ever need in the tableau goes straight up. */
    private fun isSafe(card: Int): Boolean {
        if (inSuit) return true // only the same-suit card one lower could rest on it, and that is already up
        val r = Card.rank(card)
        if (r <= 1) return true
        return if (Card.isRed(card)) s.found[Card.CLUBS] >= r && s.found[Card.SPADES] >= r
        else s.found[Card.DIAMONDS] >= r && s.found[Card.HEARTS] >= r
    }

    private fun autoMoves() {
        var changed = true
        while (changed) {
            changed = false
            for (k in s.cells.indices) {
                val card = s.cells[k]
                if (card >= 0 && Rules.fitsOnFoundation(s, card) && isSafe(card)) {
                    push(Move.encode(Move.TYPE_CELL, k, 0, Move.CELL_UP))
                    changed = true
                }
            }
            for (c in 0 until s.columns) {
                val size = s.tabSize[c]
                if (size == 0) continue
                val top = s.tab[c][size - 1]
                if (Rules.fitsOnFoundation(s, top) && isSafe(top)) {
                    push(Move.encode(Move.TYPE_T2F, c, 0, 0))
                    changed = true
                }
            }
        }
    }

    // ---------------------------------------------------------------- move generation

    private var nCand = 0
    private lateinit var cBuf: IntArray
    private lateinit var kBuf: IntArray

    private fun add(code: Int, priority: Int) {
        if (nCand >= MAX_CANDIDATES) {
            truncated = true
            return
        }
        cBuf[nCand] = code
        val rng = shuffleRng
        kBuf[nCand] = priority * 1024 + if (rng == null) 0 else rng.nextInt(1024)
        nCand++
    }

    private fun firstEmptyColumn(): Int {
        for (c in 0 until s.columns) if (s.tabSize[c] == 0) return c
        return -1
    }

    private fun generate(depth: Int): Int {
        cBuf = cands[depth]
        kBuf = keys[depth]
        nCand = 0
        val firstEmpty = firstEmptyColumn()
        val n = Rules.legalMoveCodes(s, ruleSet, legal)
        for (i in 0 until n) {
            val code = legal[i]
            val p = priority(code, firstEmpty)
            if (p >= 0) add(code, p)
        }
        sortCandidates()
        return nCand
    }

    /** Rank of a legal move, higher first, or -1 to leave it out. */
    private fun priority(code: Int, firstEmpty: Int): Int {
        val a = Move.a(code)
        return when (Move.type(code)) {
            Move.TYPE_T2F -> {
                val size = s.tabSize[a]
                if (size - 1 == s.tabDown[a] && s.tabDown[a] > 0) P_FOUNDATION + 5 else P_FOUNDATION
            }
            Move.TYPE_F2T -> {
                if (inSuit) return -1 // nothing could ever go on it
                val card = Card.of(a, s.found[a] - 1)
                if (isSafe(card) || !somethingCouldGoOn(card, -1, -1)) -1
                else if (s.tabSize[Move.b(code)] == 0 && Move.b(code) != firstEmpty) -1
                else P_FROM_FOUNDATION
            }
            Move.TYPE_T2T -> tableauPriority(a, Move.b(code), Move.c(code), firstEmpty)
            Move.TYPE_CELL -> when (Move.c(code)) {
                Move.CELL_UP -> P_FOUNDATION
                Move.CELL_OUT -> {
                    val to = Move.b(code)
                    when {
                        s.tabSize[to] > 0 -> P_CELL_OUT
                        to == firstEmpty -> P_CELL_TO_EMPTY
                        else -> -1
                    }
                }
                else -> cellInPriority(a)
            }
            else -> -1
        }
    }

    private fun tableauPriority(from: Int, to: Int, count: Int, firstEmpty: Int): Int {
        val size = s.tabSize[from]
        val col = s.tab[from]
        val i = size - count
        val down = s.tabDown[from]
        val toEmpty = s.tabSize[to] == 0
        if (toEmpty && (i == 0 || to != firstEmpty)) return -1
        val reveals = i == down && down > 0
        if (reveals) return P_REVEAL + minOf(down, 9)
        if (i == 0) return P_EMPTY_COLUMN
        val under = col[i - 1]
        val restsProperly = i > down && Rules.stacks(ruleSet, col[i], under)
        if (toEmpty) {
            // Yukon: only Kings get here. FreeCell: only whole sequences, the longest first.
            if (freeCell && restsProperly) return -1
            return if (freeCell) P_TO_EMPTY + minOf(count, 9) else P_KING_TO_EMPTY
        }
        if (!restsProperly) return P_BUILD
        // The group already rests where it may: moving it only helps if what it uncovers is useful.
        if (Rules.fitsOnFoundation(s, under)) return P_UNCOVER_FOUNDATION
        return if (somethingCouldGoOn(under, from, i)) P_LATERAL else -1
    }

    /** FreeCell: parking a column's top card. Better when it uncovers a low card or empties the column. */
    private fun cellInPriority(from: Int): Int {
        val size = s.tabSize[from]
        if (size == 1) return P_CELL_IN + 4
        val col = s.tab[from]
        val under = col[size - 2]
        if (Rules.fitsOnFoundation(s, under)) return P_CELL_IN + 6
        return if (Card.stacksOn(col[size - 1], under)) P_CELL_IN - 2 else P_CELL_IN
    }

    /**
     * True if a card that may rest on [target] is face up somewhere outside column [skip] at or above
     * [skipFrom] (the group about to move), in a free cell, or on top of a foundation (Yukon).
     */
    private fun somethingCouldGoOn(target: Int, skip: Int, skipFrom: Int): Boolean {
        val r = Card.rank(target)
        if (r == 0) return false
        for (c in 0 until s.columns) {
            val col = s.tab[c]
            val end = if (c == skip) skipFrom else s.tabSize[c]
            // FreeCell moves only ordered runs, so only a run head (or a top card) could come over.
            for (i in s.tabDown[c] until end) if (Rules.stacks(ruleSet, col[i], target)) return true
        }
        for (card in s.cells) if (card >= 0 && Rules.stacks(ruleSet, card, target)) return true
        if (ruleSet.foundationToTableau) {
            for (suit in 0 until 4) {
                val f = s.found[suit]
                if (f > 0 && Rules.stacks(ruleSet, Card.of(suit, f - 1), target)) return true
            }
        }
        return false
    }

    /** Stable insertion sort by key, highest first. Lists are short. */
    private fun sortCandidates() {
        for (i in 1 until nCand) {
            val c = cBuf[i]
            val k = kBuf[i]
            var j = i - 1
            while (j >= 0 && kBuf[j] < k) {
                cBuf[j + 1] = cBuf[j]
                kBuf[j + 1] = kBuf[j]
                j--
            }
            cBuf[j + 1] = c
            kBuf[j + 1] = k
        }
    }

    // ---------------------------------------------------------------- hashing

    /** Columns and free cells are summed, so their order doesn't matter. The foundations hold the rest. */
    private fun hash(): Long {
        var h = 0L
        for (c in 0 until s.columns) {
            var ch = 0L
            val col = s.tab[c]
            val down = s.tabDown[c]
            for (i in 0 until s.tabSize[c]) {
                ch = ch xor Z_TAB[(col[i] shl 7) or (i shl 1) or (if (i >= down) 1 else 0)]
            }
            h += ch
        }
        for (card in s.cells) if (card >= 0) h += Z_CELL[card]
        return h
    }

    companion object {
        private const val FIRST_ATTEMPT_NODES = 20_000L
        private const val ATTEMPT_SOLVED = 0
        private const val ATTEMPT_EXHAUSTED = 1
        private const val ATTEMPT_OUT_OF_BUDGET = 2
        private const val ATTEMPT_CANCELLED = 3

        private const val MAX_DEPTH = 1500
        private const val MAX_PATH = 1 shl 15
        private const val MAX_CANDIDATES = 512

        private const val P_FOUNDATION = 100
        private const val P_REVEAL = 80
        private const val P_EMPTY_COLUMN = 70
        private const val P_CELL_OUT = 65
        private const val P_BUILD = 60
        private const val P_UNCOVER_FOUNDATION = 58
        private const val P_KING_TO_EMPTY = 45
        private const val P_TO_EMPTY = 35
        private const val P_LATERAL = 30
        private const val P_CELL_TO_EMPTY = 25
        private const val P_CELL_IN = 20
        private const val P_FROM_FOUNDATION = 10

        private val Z_TAB: LongArray
        private val Z_CELL: LongArray

        init {
            val r = Xoshiro256.seeded(0x5EED_0FE7)
            Z_TAB = LongArray(52 shl 7) { r.nextLong() }
            Z_CELL = LongArray(52) { r.nextLong() }
        }
    }
}
