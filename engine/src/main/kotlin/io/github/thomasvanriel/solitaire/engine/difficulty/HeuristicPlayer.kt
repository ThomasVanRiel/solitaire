package io.github.thomasvanriel.solitaire.engine.difficulty

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

/**
 * A fair-information player: it decides using only what a human can see (face-up tableau cards, the
 * number of face-down cards, the waste top, the foundations and the stock size). It never looks at
 * face-down cards or the stock order. It plays by simple priorities, with a little randomness to break ties.
 *
 * Used to rate how hard a deal is for a person; the solver alone can't tell that.
 */
class HeuristicPlayer(
    private val ruleSet: RuleSet,
    private val rng: Xoshiro256,
    private val maxMoves: Int = 1500,
) {
    private val buf = IntArray(Rules.MAX_MOVES)

    /** Plays one game from [start]; true if it wins (or reaches a position with nothing left to hide). */
    fun play(start: GameState): Boolean {
        val s = start.copy()
        var stockMovesInARow = 0
        repeat(maxMoves) {
            if (Rules.isWon(s) || allOpen(s)) return true
            val code = chooseMove(s)
            if (code < 0) return false
            val type = Move.type(code)
            if (type == Move.TYPE_DRAW || type == Move.TYPE_RECYCLE) {
                stockMovesInARow++
                // Two full passes through the stock without playing anything: nothing will change.
                val pass = (s.stockSize + s.wasteSize + ruleSet.drawCount - 1) / ruleSet.drawCount + 1
                if (stockMovesInARow > 2 * pass + 2) return false
            } else {
                stockMovesInARow = 0
            }
            Rules.applyCode(s, ruleSet, code)
        }
        return false
    }

    /** The move this player would make, or -1 if it sees nothing worth doing. */
    fun chooseMove(s: GameState): Int {
        val n = Rules.legalMoveCodes(s, ruleSet, buf)
        var best = -1
        var bestScore = Int.MIN_VALUE
        for (i in 0 until n) {
            val code = buf[i]
            val score = score(s, code)
            if (score < 0) continue
            val noisy = score * 8 + rng.nextInt(24)
            if (noisy > bestScore) {
                bestScore = noisy
                best = code
            }
        }
        return best
    }

    /** Score of a move from visible information only; negative means "don't". */
    private fun score(s: GameState, code: Int): Int {
        val a = Move.a(code)
        return when (Move.type(code)) {
            Move.TYPE_T2F -> {
                val size = s.tabSize[a]
                val card = s.tab[a][size - 1]
                when {
                    size - 1 == s.tabDown[a] && s.tabDown[a] > 0 -> 90
                    isSafe(s, card) -> 85
                    else -> 55
                }
            }
            Move.TYPE_W2F -> if (isSafe(s, s.waste[s.wasteSize - 1])) 85 else 55
            Move.TYPE_T2T -> {
                val from = a
                val to = Move.b(code)
                val count = Move.c(code)
                val down = s.tabDown[from]
                val up = s.tabSize[from] - down
                when {
                    count == up && down > 0 -> 70 + down
                    count == up && s.tabSize[to] == 0 -> -1 // King run that already owns a column
                    count == up -> if (kingWantsAColumn(s, from)) 40 else -1
                    else -> {
                        val exposed = s.tab[from][s.tabSize[from] - count - 1]
                        if (Rules.fitsOnFoundation(s, exposed)) 50 else -1
                    }
                }
            }
            Move.TYPE_W2T -> if (s.tabSize[a] == 0) 45 else 42
            Move.TYPE_F2T -> -1
            Move.TYPE_DRAW -> 10
            Move.TYPE_RECYCLE -> 8
            else -> -1
        }
    }

    private fun isSafe(s: GameState, card: Int): Boolean {
        val r = Card.rank(card)
        if (r <= 1) return true
        return if (Card.isRed(card)) s.found[Card.CLUBS] >= r && s.found[Card.SPADES] >= r
        else s.found[Card.DIAMONDS] >= r && s.found[Card.HEARTS] >= r
    }

    /** Is there a visible King (waste top, or heading a run over face-down cards) outside [except]? */
    private fun kingWantsAColumn(s: GameState, except: Int): Boolean {
        val w = s.wasteTop()
        if (w >= 0 && Card.rank(w) == Card.KING) return true
        for (c in 0 until GameState.COLUMNS) {
            if (c == except || s.tabDown[c] == 0) continue
            if (Card.rank(s.tab[c][s.tabDown[c]]) == Card.KING) return true
        }
        return false
    }

    private fun allOpen(s: GameState): Boolean {
        if (s.stockSize != 0 || s.wasteSize != 0) return false
        for (c in 0 until GameState.COLUMNS) if (s.tabDown[c] != 0) return false
        return true
    }
}
