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
)

class SavedGameStore(context: Context) {
    private val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)

    fun save(g: SavedGame) {
        prefs.edit()
            .putLong("seed", g.seed)
            .putInt("ruleSet", g.ruleSet.id)
            .putInt("tier", g.tier.id)
            .putString("moves", Base64.encodeToString(Move.encodeAll(g.moves), Base64.NO_WRAP))
            .putLong("elapsed", g.elapsedSeconds)
            .apply()
    }

    fun load(): SavedGame? {
        if (!prefs.contains("seed")) return null
        return try {
            SavedGame(
                seed = prefs.getLong("seed", 0),
                ruleSet = RuleSet.byId(prefs.getInt("ruleSet", RuleSet.DRAW_1_UNLIMITED.id)),
                tier = Tier.byId(prefs.getInt("tier", Tier.MEDIUM.id)),
                moves = Move.decodeAll(Base64.decode(prefs.getString("moves", "") ?: "", Base64.NO_WRAP)),
                elapsedSeconds = prefs.getLong("elapsed", 0),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    var lastRuleSet: RuleSet
        get() = runCatching { RuleSet.byId(prefs.getInt("lastRuleSet", RuleSet.DRAW_1_UNLIMITED.id)) }
            .getOrDefault(RuleSet.DRAW_1_UNLIMITED)
        set(v) = prefs.edit().putInt("lastRuleSet", v.id).apply()

    var lastTier: Tier
        get() = runCatching { Tier.byId(prefs.getInt("lastTier", Tier.MEDIUM.id)) }.getOrDefault(Tier.MEDIUM)
        set(v) = prefs.edit().putInt("lastTier", v.id).apply()
}
