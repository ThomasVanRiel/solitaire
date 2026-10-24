package io.github.thomasvanriel.solitaire.engine.difficulty

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

/**
 * The fair-information player for Spider and Scorpion II, like [HeuristicPlayer] for Klondike: it decides
 * using only face-up cards, the number of face-down cards per column and the stock size. It plays by
 * simple priorities with some randomness, and never goes back to a position it has already seen.
 */
class RunGamePlayer(
    private val ruleSet: RuleSet,
    private val rng: Xoshiro256,
    private val maxMoves: Int = 1500,
) : Player {
    init {
        require(ruleSet.game == GameType.SPIDER || ruleSet.game == GameType.SCORPION) { "Not a Spider or Scorpion rule set" }
    }

    private val spider = ruleSet.game == GameType.SPIDER
    private val buf = IntArray(Rules.MAX_MOVES)
    private val scores = IntArray(Rules.MAX_MOVES)
    private val visited = HashSet<Int>()

    /**
     * How far the last [play] got, from 0 to 1: half for the share of face-down cards turned over, half for
     * the share of cards taken off in complete runs. 1 for a win.
     */
    var progress: Double = 0.0
        private set

    override fun play(start: GameState): Boolean {
        val s = start.copy()
        visited.clear()
        visited += s.hashCode()
        var won = false
        for (k in 0 until maxMoves) {
            if (Rules.isWon(s)) { won = true; break }
            val code = chooseMove(s)
            if (code < 0) break
            Rules.applyCode(s, ruleSet, code)
        }
        progress = if (won) 1.0 else 0.5 * fraction(start, s)
        return won
    }

    private fun fraction(start: GameState, end: GameState): Double {
        var down0 = 0
        var down1 = 0
        for (c in 0 until start.columns) { down0 += start.tabDown[c]; down1 += end.tabDown[c] }
        val revealed = if (down0 == 0) 1.0 else (down0 - down1).toDouble() / down0
        return revealed + end.foundationCount.toDouble() / ruleSet.deckSize
    }

    /**
     * The move this player would make, or -1 if it sees nothing worth doing. Moves back to a position seen
     * earlier in this game are left out. With probability [EXPLORE] it picks a random acceptable move.
     */
    fun chooseMove(s: GameState): Int {
        val n = Rules.legalMoveCodes(s, ruleSet, buf)
        var acceptable = 0
        val before = if (spider) evaluate(s, null) else 0
        if (spider) s.tabDown.copyInto(downBefore)
        var fallback = -1
        var fallbackScore = Int.MIN_VALUE
        for (i in 0 until n) {
            val code = buf[i]
            val score = if (spider) spiderGain(s, code, before) else score(s, code)
            if (spider && score <= 0 && Move.type(code) == Move.TYPE_T2T && s.tabSize[Move.b(code)] == 0 &&
                s.tabSize[Move.a(code)] > Move.c(code)
            ) {
                // Not an improvement, but filling an empty column may be needed before the next deal.
                val k = score * 8 + rng.nextInt(NOISE)
                if (k > fallbackScore) { fallbackScore = k; fallback = code }
            }
            if (score <= 0) continue
            buf[acceptable] = code
            scores[acceptable] = score
            acceptable++
        }
        while (acceptable > 0) {
            val pick = if (acceptable > 1 && rng.nextInt(1000) < EXPLORE * 1000) {
                rng.nextInt(acceptable)
            } else {
                var best = 0
                var bestScore = Int.MIN_VALUE
                for (i in 0 until acceptable) {
                    val noisy = scores[i] * 8 + rng.nextInt(NOISE)
                    if (noisy > bestScore) { bestScore = noisy; best = i }
                }
                best
            }
            val code = buf[pick]
            if (isFresh(s, code)) return code
            buf[pick] = buf[acceptable - 1]
            scores[pick] = scores[acceptable - 1]
            acceptable--
        }
        // Spider deals only when nothing improves the position, after filling any empty columns.
        if (spider && Rules.canDeal(s, ruleSet)) return Move.Draw.code
        if (fallback >= 0 && isFresh(s, fallback)) return fallback
        return -1
    }

    private fun isFresh(s: GameState, code: Int): Boolean {
        val token = Rules.applyCode(s, ruleSet, code)
        val fresh = visited.add(s.hashCode())
        Rules.undoCode(s, ruleSet, token)
        return fresh
    }

    private val downBefore = IntArray(16)

    /** Spider: how much a move improves [evaluate]'s score. Never looks at the card the move turns over. */
    private fun spiderGain(s: GameState, code: Int, before: Int): Int {
        val type = Move.type(code)
        if (type == Move.TYPE_T2F) return 1000
        if (type != Move.TYPE_T2T) return -1
        val from = Move.a(code)
        val count = Move.c(code)
        val size = s.tabSize[from]
        // Never split a same-suit run.
        if (count < size - s.tabDown[from] && Card.followsInSuit(s.tab[from][size - count], s.tab[from][size - count - 1])) return -1
        val token = Rules.applyCode(s, ruleSet, code)
        val after = evaluate(s, downBefore)
        Rules.undoCode(s, ruleSet, token)
        return after - before
    }

    /**
     * Spider: a score from what a player can see: fewer face-down cards, more empty columns, and more cards
     * resting on the next rank up (much more in the same suit). With [visibleFrom], cards that were face down
     * before the move (just turned over) are ignored, so the player can't use a card it hasn't seen yet.
     */
    private fun evaluate(s: GameState, visibleFrom: IntArray?): Int {
        var score = 0
        for (c in 0 until s.columns) {
            val size = s.tabSize[c]
            if (size == 0) { score += EMPTY; continue }
            score -= s.tabDown[c] * FACE_DOWN
            val first = maxOf(s.tabDown[c], visibleFrom?.get(c) ?: 0)
            val col = s.tab[c]
            for (i in first + 1 until size) {
                if (Card.followsInSuit(col[i], col[i - 1])) score += IN_SUIT
                else if (Card.rank(col[i]) + 1 == Card.rank(col[i - 1])) score += IN_RANK
            }
        }
        for (suit in 0 until 4) score += s.found[suit] * 10
        return score
    }

    /** Scorpion: score of a move from visible information only; negative means "don't". */
    private fun score(s: GameState, code: Int): Int = when (Move.type(code)) {
        Move.TYPE_T2F -> 100
        Move.TYPE_DRAW -> 10
        Move.TYPE_T2T -> scorpionScore(s, code)
        else -> -1
    }

    private fun scorpionScore(s: GameState, code: Int): Int {
        val from = Move.a(code)
        val to = Move.b(code)
        val count = Move.c(code)
        val index = s.tabSize[from] - count
        val down = s.tabDown[from]
        val reveals = index == down && down > 0
        return when {
            s.tabSize[to] == 0 -> if (index == 0) -1 else 45 + (if (reveals) 40 else 0)
            reveals -> 90
            index == 0 -> 60
            else -> 50
        }
    }

    private companion object {
        const val NOISE = 8 * 12
        const val EXPLORE = 0.15

        // Spider position score weights.
        const val EMPTY = 25
        const val FACE_DOWN = 30
        const val IN_SUIT = 10
        const val IN_RANK = 3
    }
}
