package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DealCodeTest {
    @Test
    fun roundTripsForEveryRuleSet() {
        val prefixes = RuleSet.ALL.map { DealCode.prefix(it) }
        assertEquals(prefixes.size, prefixes.toSet().size, "prefixes are unique")
        for (rs in RuleSet.ALL) for (seed in listOf(0L, 1L, 71L, 123456789L, Long.MAX_VALUE)) {
            assertEquals(rs to seed, DealCode.parse(DealCode.format(rs, seed)))
        }
    }

    @Test
    fun parsingIsForgiving() {
        assertEquals(RuleSet.SPIDER_2_SUITS to 17L, DealCode.parse("s2-17"))
        assertEquals(RuleSet.SPIDER_2_SUITS to 17L, DealCode.parse(" S2 17 "))
        assertEquals(RuleSet.SCORPION_II to 6L, DealCode.parse("#sc6"))
        assertEquals(RuleSet.DRAW_3_UNLIMITED to 100L, DealCode.parse("K3-100"))
    }

    @Test
    fun rejectsNonCodes() {
        for (bad in listOf("", "100", "#100", "K2-5", "S3-1", "K1-", "K1-x", "K1--5", "K1-5a", "SC-12345678901234567890")) {
            assertNull(DealCode.parse(bad), "'$bad' is not a code")
        }
    }

    /** Every shipped deal's code leads back to the same rule set and seed. */
    @Test
    fun shippedDealsHaveCodes() {
        assertEquals("K1-100", DealCode.format(RuleSet.DRAW_1_UNLIMITED, 100))
        assertEquals("SC-6", DealCode.format(RuleSet.SCORPION_II, 6))
    }
}
