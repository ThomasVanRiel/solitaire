package io.github.thomasvanriel.solitaire.game

import android.content.Context
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.pack.DealPack

/** A deal that is about to be played, with its verified winning moves. */
class ServedDeal(
    val seed: Long,
    val ruleSet: RuleSet,
    val tier: Tier,
    val shuffleVersion: Int,
    val solution: List<Move>,
)

/**
 * Serves deals from the packs in `assets/deals`, in order, remembering how far each pack has got.
 *
 * Every deal's stored solution is replayed by the rules engine ([DealPack.verifiedSolution], which uses
 * ProofVerifier) right before it is served. A deal whose proof fails is skipped, never served.
 */
class DealRepository(private val context: Context) {
    // Opened lazily: the first access reads from disk, and happens on a background thread.
    private val prefs by lazy { context.getSharedPreferences("deals", Context.MODE_PRIVATE) }
    private val packs = HashMap<String, DealPack>()

    @Synchronized
    private fun pack(ruleSet: RuleSet, tier: Tier): DealPack {
        val name = DealPack.fileName(ruleSet, tier)
        return packs.getOrPut(name) { context.assets.open("deals/$name").use { DealPack.read(it) } }
    }

    /**
     * The next deal for [ruleSet] and [tier]. Call off the main thread. Synchronized, so two calls never serve
     * the same deal.
     */
    @Synchronized
    fun next(ruleSet: RuleSet, tier: Tier): ServedDeal {
        val pack = pack(ruleSet, tier)
        val size = pack.entries.size
        val key = "next_" + DealPack.fileName(ruleSet, tier)
        var index = prefs.getInt(key, 0)
        repeat(size) {
            val entry = pack.entries[index % size]
            index = (index + 1) % size
            val solution = pack.verifiedSolution(entry)
            if (solution != null) {
                prefs.edit().putInt(key, index).apply()
                return ServedDeal(entry.seed, ruleSet, tier, pack.shuffleVersion, solution)
            }
        }
        error("No deal in ${DealPack.fileName(ruleSet, tier)} passed verification")
    }

    /**
     * A deal named by a deal code: looked up in every tier's pack for [ruleSet], served only if its proof
     * replays. Null if this version's packs don't have it. Call off the main thread.
     */
    fun findInAnyTier(seed: Long, ruleSet: RuleSet): ServedDeal? =
        Tier.entries.firstNotNullOfOrNull { tier -> find(seed, ruleSet, tier) }

    /** The verified solution of a deal from the packs, or null. Used to resume a saved game. */
    fun find(seed: Long, ruleSet: RuleSet, tier: Tier): ServedDeal? {
        val pack = pack(ruleSet, tier)
        val entry = pack.entries.firstOrNull { it.seed == seed } ?: return null
        val solution = pack.verifiedSolution(entry) ?: return null
        return ServedDeal(seed, ruleSet, tier, pack.shuffleVersion, solution)
    }
}
