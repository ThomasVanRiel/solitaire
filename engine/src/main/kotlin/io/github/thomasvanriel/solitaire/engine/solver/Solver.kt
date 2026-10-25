package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

/**
 * Full-information Klondike solver: depth-first search with a transposition table.
 *
 * - Positions are hashed with Zobrist keys. Column hashes are summed, so the order of columns doesn't matter.
 * - Stock macro-moves: instead of branching on single draws, every waste card that drawing (and recycling)
 *   can bring to the top and that can then be played gives one candidate, "draw n times, then play it".
 *   Draws only affect the stock and waste, so they can always be postponed until just before the waste play.
 *   This keeps the search complete.
 * - Safe auto-moves: cards no other card could ever need go to the foundation without branching.
 * - Pruning that can only lose solutions, never invent them: pointless moves to empty columns, partial run
 *   moves that don't unlock anything.
 *
 * All moves are played through [Rules], so every recorded solution is a sequence of legal primitive moves.
 * The result is still replayed by [ProofVerifier] before anything is served.
 *
 * Not thread-safe: use one instance per thread. Instances can be reused.
 */
class Solver(
    private val ruleSet: RuleSet,
    private val maxNodes: Long = 2_000_000,
    private val timeLimitMs: Long = 10_000,
    private val isCancelled: () -> Boolean = { false },
) : DealSolver {
    init {
        require(ruleSet.game == io.github.thomasvanriel.solitaire.engine.model.GameType.KLONDIKE) { "Not a Klondike rule set" }
    }

    private var s = GameState.forRules(ruleSet)
    private val path = IntArray(MAX_PATH)
    private val tokens = IntArray(MAX_PATH)
    private var pathLen = 0
    private var nodes = 0L
    private var deadline = 0L
    private var truncated = false
    private val seen = LongHashSet()

    private val cands = Array(MAX_DEPTH) { IntArray(MAX_CANDIDATES) }
    private val keys = Array(MAX_DEPTH) { IntArray(MAX_CANDIDATES) }
    private val simTokens = IntArray(MAX_SIM)

    private class Abort : RuntimeException(null, null, false, false)
    private class OutOfBudget : RuntimeException(null, null, false, false)

    /** Total nodes used by the last [solve], across all restarts. */
    private var totalNodes = 0L
    private var attemptLimit = 0L
    private var shuffleRng: Xoshiro256? = null

    /**
     * Searches with restarts: short attempts with growing node budgets. Each attempt after the first breaks
     * ties between equally ranked moves at random. Depth-first search is heavy-tailed (one bad early choice
     * can trap it in a huge subtree), and restarts fix most of those cases cheaply.
     * `Unsolvable` is only reported when an attempt exhausts the search space within its budget.
     */
    override fun solve(start: GameState): SolveResult {
        if (isCancelled()) return SolveResult.Unknown(0)
        deadline = System.nanoTime() + timeLimitMs * 1_000_000
        totalNodes = 0
        var budget = FIRST_ATTEMPT_NODES
        var attempt = 0
        while (true) {
            val remaining = maxNodes - totalNodes
            if (remaining <= 0) return SolveResult.Unknown(totalNodes)
            // The last attempt gets everything that is left.
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
        if (allOpen()) {
            finishOpenGame()
            return true
        }
        if (depth >= MAX_DEPTH || pathLen > MAX_PATH - 2 * MAX_SIM) {
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
            playCandidate(c[i])
            if (search(depth + 1)) return true
            popTo(before)
        }
        popTo(mark)
        return false
    }

    private fun playCandidate(cand: Int) {
        var draws = cand ushr Move.CODE_BITS
        while (draws-- > 0) push(if (s.stockSize > 0) DRAW else RECYCLE)
        push(cand and Move.CODE_MASK)
    }

    // ---------------------------------------------------------------- safe moves and endgame

    /** A card can go up for good if it is an Ace or Two, or both opposite-colour suits already hold rank-1. */
    private fun isSafe(card: Int): Boolean {
        val r = Card.rank(card)
        if (r <= 1) return true
        return if (Card.isRed(card)) s.found[Card.CLUBS] >= r && s.found[Card.SPADES] >= r
        else s.found[Card.DIAMONDS] >= r && s.found[Card.HEARTS] >= r
    }

    private fun autoMoves() {
        var changed = true
        while (changed) {
            changed = false
            if (s.wasteSize > 0) {
                val w = s.waste[s.wasteSize - 1]
                if (Rules.fitsOnFoundation(s, w) && isSafe(w)) {
                    push(W2F)
                    changed = true
                }
            }
            for (c in 0 until GameState.COLUMNS) {
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

    /** No stock, no waste, no face-down cards: always winnable by playing the lowest card up each time. */
    private fun allOpen(): Boolean {
        if (s.stockSize != 0 || s.wasteSize != 0) return false
        for (c in 0 until GameState.COLUMNS) if (s.tabDown[c] != 0) return false
        return true
    }

    private fun finishOpenGame() {
        while (!Rules.isWon(s)) {
            var moved = false
            for (c in 0 until GameState.COLUMNS) {
                val size = s.tabSize[c]
                if (size > 0 && Rules.fitsOnFoundation(s, s.tab[c][size - 1])) {
                    push(Move.encode(Move.TYPE_T2F, c, 0, 0))
                    moved = true
                }
            }
            check(moved) { "Open game got stuck, which should be impossible:\n$s" }
        }
    }

    // ---------------------------------------------------------------- move generation

    private var nCand = 0
    private lateinit var cBuf: IntArray
    private lateinit var kBuf: IntArray

    private fun add(draws: Int, code: Int, priority: Int) {
        if (nCand >= MAX_CANDIDATES) return
        cBuf[nCand] = (draws shl Move.CODE_BITS) or code
        val rng = shuffleRng
        kBuf[nCand] = priority * 1024 + if (rng == null) -draws else rng.nextInt(1024)
        nCand++
    }

    private fun firstEmptyColumn(): Int {
        for (c in 0 until GameState.COLUMNS) if (s.tabSize[c] == 0) return c
        return -1
    }

    /** Fills the candidate list for [depth], best first, and returns its length. */
    private fun generate(depth: Int): Int {
        cBuf = cands[depth]
        kBuf = keys[depth]
        nCand = 0
        val empty = firstEmptyColumn()

        generateStockPlays(empty)

        for (from in 0 until GameState.COLUMNS) {
            val size = s.tabSize[from]
            if (size == 0) continue
            val col = s.tab[from]
            val down = s.tabDown[from]
            val top = col[size - 1]
            if (Rules.fitsOnFoundation(s, top)) {
                add(0, Move.encode(Move.TYPE_T2F, from, 0, 0), if (size - 1 == down && down > 0) P_FOUNDATION + 5 else P_FOUNDATION)
            }
            val up = size - down
            for (count in 1..up) {
                val card = col[size - count]
                if (count > 1 && !Card.stacksOn(col[size - count + 1], card)) break
                val whole = count == up
                val priority = when {
                    whole && down > 0 -> P_REVEAL + down
                    whole -> P_EMPTY_COLUMN
                    partialRunUnlocks(from, count) -> P_PARTIAL
                    else -> continue
                }
                val isKing = Card.rank(card) == Card.KING
                for (to in 0 until GameState.COLUMNS) {
                    if (to == from) continue
                    if (s.tabSize[to] == 0) {
                        // Only Kings, only into one empty column, and never a whole open column (pointless).
                        if (!isKing || to != empty || (whole && down == 0)) continue
                    } else if (!Card.stacksOn(card, s.tab[to][s.tabSize[to] - 1])) continue
                    add(0, Move.encode(Move.TYPE_T2T, from, to, count), priority)
                }
            }
        }

        sortCandidates()
        return nCand
    }

    /**
     * Walks through every stock/waste position reachable by drawing (with at most one recycle, since a
     * second recycle only repeats positions) and adds the plays of each reachable waste card.
     */
    private fun generateStockPlays(empty: Int) {
        val startStock = s.stockSize
        val startWaste = s.wasteSize
        var k = 0
        var recycled = false
        while (true) {
            if (s.wasteSize > 0) addWastePlays(k, s.waste[s.wasteSize - 1], empty)
            val code = when {
                s.stockSize > 0 -> DRAW
                !recycled && Rules.canRecycle(s, ruleSet) -> { recycled = true; RECYCLE }
                else -> break
            }
            if (k >= MAX_SIM) break
            simTokens[k++] = Rules.applyCode(s, ruleSet, code)
            if (recycled && s.stockSize == startStock && s.wasteSize == startWaste) break
        }
        while (k > 0) Rules.undoCode(s, ruleSet, simTokens[--k])
    }

    private fun addWastePlays(draws: Int, w: Int, empty: Int) {
        if (Rules.fitsOnFoundation(s, w)) add(draws, W2F, P_FOUNDATION)
        val isKing = Card.rank(w) == Card.KING
        for (c in 0 until GameState.COLUMNS) {
            val size = s.tabSize[c]
            if (size == 0) {
                if (isKing && c == empty) add(draws, Move.encode(Move.TYPE_W2T, c, 0, 0), P_WASTE_KING)
            } else if (Card.stacksOn(w, s.tab[c][size - 1])) {
                add(draws, Move.encode(Move.TYPE_W2T, c, 0, 0), P_WASTE)
            }
        }
    }

    /**
     * Moving only part of a face-up run is worth trying only if the card it uncovers can then go to its
     * foundation, or some other card (not in the moving run) could then be placed on it.
     */
    private fun partialRunUnlocks(from: Int, count: Int): Boolean {
        val exposed = s.tab[from][s.tabSize[from] - count - 1]
        if (Rules.fitsOnFoundation(s, exposed)) return true
        return somethingCouldGoOn(exposed, from)
    }

    /** True if a card that could be stacked on [target] is in the stock, the waste, or face up outside column [skip]. */
    private fun somethingCouldGoOn(target: Int, skip: Int): Boolean {
        val exposed = target
        val r = Card.rank(exposed)
        if (r == 0) return false
        val oppA: Int
        val oppB: Int
        if (Card.isRed(exposed)) { oppA = Card.of(Card.CLUBS, r - 1); oppB = Card.of(Card.SPADES, r - 1) }
        else { oppA = Card.of(Card.DIAMONDS, r - 1); oppB = Card.of(Card.HEARTS, r - 1) }
        for (i in 0 until s.wasteSize) if (s.waste[i] == oppA || s.waste[i] == oppB) return true
        for (i in 0 until s.stockSize) if (s.stock[i] == oppA || s.stock[i] == oppB) return true
        for (c in 0 until GameState.COLUMNS) {
            if (c == skip) continue
            val col = s.tab[c]
            for (i in s.tabDown[c] until s.tabSize[c]) if (col[i] == oppA || col[i] == oppB) return true
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

    private fun hash(): Long {
        var h = 0L
        for (c in 0 until GameState.COLUMNS) {
            var ch = 0L
            val col = s.tab[c]
            val down = s.tabDown[c]
            for (i in 0 until s.tabSize[c]) {
                ch = ch xor Z_TAB[(col[i] shl 6) or (i shl 1) or (if (i >= down) 1 else 0)]
            }
            h += ch // sum: column order doesn't matter
        }
        for (i in 0 until s.stockSize) h = h xor Z_STOCK[s.stock[i] * 52 + i]
        for (i in 0 until s.wasteSize) h = h xor Z_WASTE[s.waste[i] * 52 + i]
        if (!ruleSet.unlimitedRecycles) h = h xor (s.recycles * Z_RECYCLE)
        return h
    }

    companion object {
        private const val FIRST_ATTEMPT_NODES = 20_000L
        private const val ATTEMPT_SOLVED = 0
        private const val ATTEMPT_EXHAUSTED = 1
        private const val ATTEMPT_OUT_OF_BUDGET = 2
        private const val ATTEMPT_CANCELLED = 3

        private const val MAX_DEPTH = 1000
        private const val MAX_PATH = 1 shl 15
        private const val MAX_SIM = 128
        private const val MAX_CANDIDATES = 1024

        private const val P_FOUNDATION = 100
        private const val P_REVEAL = 80
        private const val P_EMPTY_COLUMN = 60
        private const val P_WASTE = 50
        private const val P_WASTE_KING = 45
        private const val P_PARTIAL = 30

        private val DRAW = Move.Draw.code
        private val RECYCLE = Move.Recycle.code
        private val W2F = Move.WasteToFoundation.code

        private val Z_TAB: LongArray
        private val Z_STOCK: LongArray
        private val Z_WASTE: LongArray
        private val Z_RECYCLE: Long

        init {
            val r = Xoshiro256.seeded(0x5EED_CA2D)
            Z_TAB = LongArray(52 * 64) { r.nextLong() }
            Z_STOCK = LongArray(52 * 52) { r.nextLong() }
            Z_WASTE = LongArray(52 * 52) { r.nextLong() }
            Z_RECYCLE = r.nextLong() or 1L
        }
    }
}
