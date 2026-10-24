package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Loads every deal pack the app ships and replays every proof. If a single one fails, the build fails.
 */
class ShippedPacksTest {
    @Test
    fun everyShippedProofVerifies() {
        val dir = File(System.getProperty("packs.dir") ?: error("packs.dir not set"))
        for (rs in RuleSet.ALL) {
            for (tier in Tier.entries) {
                val file = File(dir, DealPack.fileName(rs, tier))
                assertTrue(file.isFile, "missing pack $file")
                val pack = file.inputStream().use { DealPack.read(it) }
                assertEquals(rs, pack.ruleSet, file.name)
                assertEquals(tier, pack.tier, file.name)
                assertTrue(pack.entries.isNotEmpty(), "${file.name} is empty")
                val seeds = HashSet<Long>()
                for (e in pack.entries) {
                    assertTrue(seeds.add(e.seed), "${file.name}: duplicate seed ${e.seed}")
                    assertTrue(pack.verifiedSolution(e) != null, "${file.name}: proof for seed ${e.seed} does not verify")
                }
            }
        }
    }
}
