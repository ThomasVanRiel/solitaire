package io.github.thomasvanriel.solitaire.engine.difficulty

import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Xoshiro256

/**
 * The fair-information player for Yukon, Russian Solitaire and FreeCell, like [HeuristicPlayer] for
 * Klondike: it decides using only face-up cards, the number of face-down cards per column, the free cells
 * and the foundations. It plays by simple priorities with some randomness, and never goes back to a
 * position it has already seen in the same game.
 */
class OpenGamePlayer(
    private val ruleSet: RuleSet,
    private val rng: Xoshiro256,
    private val maxMoves: Int = 1500,
) : Player {
    init {
        require(ruleSet.game == GameType.YUKON || ruleSet.game == GameType.FREECELL) { "Not a Yukon or FreeCell rule set" }
    }

    private val freeCell = ruleSet.game == GameType.FREECELL
    private val buf = IntArray(Rules.MAX_MOVES)
    private val scores = IntArray(Rules.MAX_MOVES)
    private val visited = HashSet<Int>()

    /** How far the last [play] got, from 0 to 1: the share of cards it put on the foundations. */
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
        progress = s.foundationCount / 52.0
        return won
    }

    /**
     * The move this player would make, or -1 if it sees nothing worth doing. Moves back to a position seen
     * earlier in this game are left out. With probability [EXPLORE] it picks a random acceptable move.
     */
    fun chooseMove(s: GameState): Int {
        val n = Rules.legalMoveCodes(s, ruleSet, buf)
        var acceptable = 0
        for (i in 0 until n) {
            val score = score(s, buf[i])
            if (score <= 0) continue
            buf[acceptable] = buf[i]
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
        return -1
    }

    private fun isFresh(s: GameState, code: Int): Boolean {
        val token = Rules.applyCode(s, ruleSet, code)
        val fresh = visited.add(s.hashCode())
        Rules.undoCode(s, ruleSet, token)
        return fresh
    }

    /** Score of a move from visible information only; 0 or less means "don't". */
    private fun score(s: GameState, code: Int): Int {
        val a = Move.a(code)
        return when (Move.type(code)) {
            Move.TYPE_T2F -> {
                val size = s.tabSize[a]
                when {
                    size - 1 == s.tabDown[a] && s.tabDown[a] > 0 -> 95
                    isSafe(s, s.tab[a][size - 1]) -> 100
                    else -> 70
                }
            }
            Move.TYPE_F2T -> -1
            Move.TYPE_T2T -> tableauScore(s, a, Move.b(code), Move.c(code))
            Move.TYPE_CELL -> when (Move.c(code)) {
                Move.CELL_UP -> if (isSafe(s, s.cells[a])) 100 else 70
                Move.CELL_OUT -> if (s.tabSize[Move.b(code)] > 0) 65 else 15
                else -> {
                    val size = s.tabSize[a]
                    when {
                        size >= 2 && Rules.fitsOnFoundation(s, s.tab[a][size - 2]) -> 40
                        s.freeCells() >= 2 -> 12
                        else -> 5
                    }
                }
            }
            else -> -1
        }
    }

    private fun tableauScore(s: GameState, from: Int, to: Int, count: Int): Int {
        val size = s.tabSize[from]
        val i = size - count
        val down = s.tabDown[from]
        val toEmpty = s.tabSize[to] == 0
        if (i == down && down > 0) return 90 + down
        if (i == 0) return if (toEmpty) -1 else 60
        val col = s.tab[from]
        // Only face-up cards are looked at: i > down here, so col[i - 1] is face up.
        val restsProperly = Rules.stacks(ruleSet, col[i], col[i - 1])
        if (toEmpty) return if (restsProperly) (if (freeCell) 8 else -1) else if (freeCell) 30 else 45
        if (!restsProperly) return 50
        return if (Rules.fitsOnFoundation(s, col[i - 1])) 55 else -1
    }

    private fun isSafe(s: GameState, card: Int): Boolean {
        if (ruleSet.buildInSuit) return true
        val r = Card.rank(card)
        if (r <= 1) return true
        return if (Card.isRed(card)) s.found[Card.CLUBS] >= r && s.found[Card.SPADES] >= r
        else s.found[Card.DIAMONDS] >= r && s.found[Card.HEARTS] >= r
    }

    private companion object {
        const val NOISE = 8 * 12
        const val EXPLORE = 0.15
    }
}
