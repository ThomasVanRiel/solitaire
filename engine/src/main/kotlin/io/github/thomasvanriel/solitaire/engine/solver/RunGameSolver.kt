package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

/**
 * Full-information solver for Spider and Scorpion II: depth-first search with a transposition table and
 * restarts, like the Klondike [Solver].
 *
 * - Complete runs leave the board as soon as they show (the rules allow nothing else).
 * - Moves are ranked: building in suit, revealing a face-down card and emptying a column come first,
 *   the deal comes last.
 * - Pruning that can only lose solutions, never invent them: splitting a same-suit run, moving a run
 *   between two off-suit cards of the same rank, moving a whole column into an empty one, and (once the
 *   stock is empty and the columns are interchangeable) using any empty column but the first.
 * - Positions are hashed with Zobrist keys. Columns are hashed in order while the stock still has cards
 *   (a deal treats the columns differently), and summed after that.
 *
 * All moves are played through [Rules], so every recorded solution is a sequence of legal moves. The result
 * is still replayed by [ProofVerifier] before anything is served.
 *
 * Not thread-safe: use one instance per thread. Instances can be reused.
 */
class RunGameSolver(
    private val ruleSet: RuleSet,
    private val maxNodes: Long = 2_000_000,
    private val timeLimitMs: Long = 10_000,
    private val isCancelled: () -> Boolean = { false },
) : DealSolver {
    init {
        require(ruleSet.game == GameType.SPIDER || ruleSet.game == GameType.SCORPION) { "Not a Spider or Scorpion rule set" }
    }

    private val spider = ruleSet.game == GameType.SPIDER
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
        removeCompleteRuns()
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

    /** The only legal moves while a complete run shows are taking runs off, so play them straight away. */
    private fun removeCompleteRuns() {
        var c = 0
        while (c < s.columns) {
            if (Rules.hasCompleteRun(s, c)) {
                push(Move.encode(Move.TYPE_T2F, c, 0, 0))
                c = 0
            } else c++
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

    private fun generate(depth: Int): Int {
        cBuf = cands[depth]
        kBuf = keys[depth]
        nCand = 0
        if (spider) generateSpider() else generateScorpion()
        if (Rules.canDeal(s, ruleSet)) add(DEAL, P_DEAL)
        sortCandidates()
        return nCand
    }

    /** Empty columns a group may go to: all of them while the stock has cards, otherwise only the first. */
    private fun emptyAllowed(to: Int, firstEmpty: Int): Boolean = s.stockSize > 0 || to == firstEmpty

    private fun firstEmptyColumn(): Int {
        for (c in 0 until s.columns) if (s.tabSize[c] == 0) return c
        return -1
    }

    private fun generateSpider() {
        val cols = s.columns
        val firstEmpty = firstEmptyColumn()
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size == 0) continue
            val col = s.tab[from]
            val down = s.tabDown[from]
            val up = size - down
            // Length of the same-suit run on top.
            var run = 1
            while (run < up && Card.followsInSuit(col[size - run], col[size - run - 1])) run++
            // Splitting a same-suit run only swaps equivalent positions around: skip it.
            val count = run
            val card = col[size - count]
            val reveals = count == up && down > 0
            val empties = count == size
            // The card under the run (if face up) is one higher but of another suit: moving the run onto
            // another off-suit card of that rank changes nothing useful.
            val offSuitJoin = count < up && Card.rank(col[size - count - 1]) == Card.rank(card) + 1
            for (to in 0 until cols) {
                if (to == from) continue
                val tsize = s.tabSize[to]
                if (tsize == 0) {
                    if (empties || !emptyAllowed(to, firstEmpty)) continue
                    add(Move.encode(Move.TYPE_T2T, from, to, count), if (reveals) P_REVEAL_TO_EMPTY + minOf(down, 5) else P_TO_EMPTY)
                    continue
                }
                val top = s.tab[to][tsize - 1]
                if (Card.rank(top) != Card.rank(card) + 1) continue
                val inSuit = Card.suit(top) == Card.suit(card)
                val priority = when {
                    inSuit -> P_BUILD_IN_SUIT + (if (reveals) 5 else 0) + (if (empties) 3 else 0)
                    reveals -> P_REVEAL + minOf(down, 5)
                    empties -> P_EMPTY_COLUMN
                    offSuitJoin -> continue
                    exposeHelps(col[size - count - 1], from) -> P_EXPOSE
                    else -> continue
                }
                add(Move.encode(Move.TYPE_T2T, from, to, count), priority)
            }
        }
    }

    /**
     * Spider: uncovering face-up card [exposed] is only worth a move if the head of a movable run in another
     * column (the card a whole-run move would carry) is one rank lower.
     */
    private fun exposeHelps(exposed: Int, skip: Int): Boolean {
        val want = Card.rank(exposed) - 1
        if (want < 0) return false
        for (c in 0 until s.columns) {
            if (c == skip) continue
            val size = s.tabSize[c]
            if (size == 0) continue
            val col = s.tab[c]
            val up = size - s.tabDown[c]
            var run = 1
            while (run < up && Card.followsInSuit(col[size - run], col[size - run - 1])) run++
            if (Card.rank(col[size - run]) == want) return true
        }
        return false
    }

    private fun generateScorpion() {
        val cols = s.columns
        val firstEmpty = firstEmptyColumn()
        for (from in 0 until cols) {
            val size = s.tabSize[from]
            if (size == 0) continue
            val col = s.tab[from]
            val down = s.tabDown[from]
            for (i in down until size) {
                val card = col[i]
                val count = size - i
                val reveals = i == down && down > 0
                if (Card.rank(card) == Card.KING) {
                    if (i == 0) continue // already heads its column
                    for (to in 0 until cols) {
                        if (s.tabSize[to] != 0 || !emptyAllowed(to, firstEmpty)) continue
                        add(Move.encode(Move.TYPE_T2T, from, to, count), if (reveals) P_REVEAL + minOf(down, 5) else P_KING_TO_EMPTY)
                    }
                    continue
                }
                // Only one card in the deck can take this one: find it on top of a column.
                for (to in 0 until cols) {
                    if (to == from) continue
                    val tsize = s.tabSize[to]
                    if (tsize == 0 || !Card.followsInSuit(card, s.tab[to][tsize - 1])) continue
                    val priority = when {
                        reveals -> P_REVEAL + minOf(down, 5)
                        i == 0 -> P_EMPTY_COLUMN
                        else -> P_BUILD_IN_SUIT - minOf(count, 20)
                    }
                    add(Move.encode(Move.TYPE_T2T, from, to, count), priority)
                }
            }
        }
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

    /**
     * The stock only ever loses cards from the top, so its size identifies it; the foundations hold whatever
     * isn't in the tableau or stock.
     */
    private fun hash(): Long {
        var h = s.stockSize * Z_STOCK
        val ordered = s.stockSize > 0
        for (c in 0 until s.columns) {
            var ch = 0L
            val col = s.tab[c]
            val down = s.tabDown[c]
            for (i in 0 until s.tabSize[c]) {
                ch = ch xor Z_TAB[(col[i] shl 8) or (i shl 1) or (if (i >= down) 1 else 0)]
            }
            h += if (ordered) ch * Z_COL[c] else ch
        }
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

        private const val P_BUILD_IN_SUIT = 90
        private const val P_REVEAL = 70
        private const val P_EMPTY_COLUMN = 60
        private const val P_REVEAL_TO_EMPTY = 50
        private const val P_KING_TO_EMPTY = 40
        private const val P_EXPOSE = 30
        private const val P_TO_EMPTY = 15
        private const val P_DEAL = 5

        private val DEAL = Move.Draw.code

        private val Z_TAB: LongArray
        private val Z_COL: LongArray
        private val Z_STOCK: Long

        init {
            val r = Xoshiro256.seeded(0x5EED_5B1D)
            Z_TAB = LongArray(52 shl 8) { r.nextLong() }
            Z_COL = LongArray(16) { r.nextLong() or 1L }
            Z_STOCK = r.nextLong() or 1L
        }
    }
}
