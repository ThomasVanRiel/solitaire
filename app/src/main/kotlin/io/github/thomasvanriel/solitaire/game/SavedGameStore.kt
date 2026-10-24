package io.github.thomasvanriel.solitaire.game

import android.content.Context
import android.util.Base64
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/** The current game as (seed, rule set, tier, moves, time), so it can be rebuilt by replaying the moves. */
class SavedGame(
    val seed: Long,
    val ruleSet: RuleSet,
    val tier: Tier,
    val moves: List<Move>,
    val elapsedSeconds: Long,
    val counted: Boolean = false,
    val usedUndo: Boolean = false,
    val assisted: Boolean = false,
    val winRecorded: Boolean = false,
)

class SavedGameStore(private val context: Context) {
    // Opened lazily: the first access reads from disk, and happens on a background thread.
    private val prefs by lazy { context.getSharedPreferences("game", Context.MODE_PRIVATE) }

    fun save(g: SavedGame) {
        prefs.edit()
            .putLong("seed", g.seed)
            .putInt("ruleSet", g.ruleSet.id)
            .putInt("tier", g.tier.id)
            .remove("moves") // the old two-byte format, from before Spider and Scorpion
            .putString("moves3", Base64.encodeToString(Move.encodeAll(g.moves), Base64.NO_WRAP))
            .putLong("elapsed", g.elapsedSeconds)
            .putBoolean("counted", g.counted)
            .putBoolean("usedUndo", g.usedUndo)
            .putBoolean("assisted", g.assisted)
            .putBoolean("winRecorded", g.winRecorded)
            .apply()
    }

    fun load(): SavedGame? {
        if (!prefs.contains("seed")) return null
        return try {
            SavedGame(
                seed = prefs.getLong("seed", 0),
                ruleSet = RuleSet.byId(prefs.getInt("ruleSet", RuleSet.DRAW_1_UNLIMITED.id)),
                tier = Tier.byId(prefs.getInt("tier", Tier.MEDIUM.id)),
                moves = loadMoves(),
                elapsedSeconds = prefs.getLong("elapsed", 0),
                counted = prefs.getBoolean("counted", false),
                usedUndo = prefs.getBoolean("usedUndo", false),
                assisted = prefs.getBoolean("assisted", false),
                winRecorded = prefs.getBoolean("winRecorded", false),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Three bytes per move; games saved by version 0.2 used two (Klondike only) under the old key. */
    private fun loadMoves(): List<Move> {
        prefs.getString("moves3", null)?.let { return Move.decodeAll(Base64.decode(it, Base64.NO_WRAP)) }
        return Move.decodeAll(Base64.decode(prefs.getString("moves", "") ?: "", Base64.NO_WRAP), bytesPerMove = 2)
    }

    var lastRuleSet: RuleSet
        get() = runCatching { RuleSet.byId(prefs.getInt("lastRuleSet", RuleSet.DRAW_1_UNLIMITED.id)) }
            .getOrDefault(RuleSet.DRAW_1_UNLIMITED)
        set(v) = prefs.edit().putInt("lastRuleSet", v.id).apply()

    var lastTier: Tier
        get() = runCatching { Tier.byId(prefs.getInt("lastTier", Tier.MEDIUM.id)) }.getOrDefault(Tier.MEDIUM)
        set(v) = prefs.edit().putInt("lastTier", v.id).apply()
}
