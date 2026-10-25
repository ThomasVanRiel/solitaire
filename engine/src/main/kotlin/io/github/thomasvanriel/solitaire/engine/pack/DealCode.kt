package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/**
 * A short code that names a deal, for sharing: a two-letter variant prefix and the deal number, such as
 * `K1-100` (Klondike draw 1), `S2-17` (Spider 2 suits), `SC-6` (Scorpion II) or `FC-9` (FreeCell). The prefix is needed
 * because the same number is a different deal in every variant.
 *
 * Parsing is forgiving: case, spaces, a leading '#' and the dash don't matter.
 * A code only names a deal; whether it can be played depends on it being in the shipped (verified) packs.
 */
object DealCode {
    private val PREFIXES: List<Pair<RuleSet, String>> = listOf(
        RuleSet.DRAW_1_UNLIMITED to "K1",
        RuleSet.DRAW_3_UNLIMITED to "K3",
        RuleSet.SPIDER_1_SUIT to "S1",
        RuleSet.SPIDER_2_SUITS to "S2",
        RuleSet.SPIDER_4_SUITS to "S4",
        RuleSet.SCORPION_II to "SC",
        RuleSet.YUKON to "YU",
        RuleSet.RUSSIAN to "RU",
        RuleSet.FREECELL to "FC",
    )

    fun prefix(ruleSet: RuleSet): String =
        PREFIXES.firstOrNull { it.first == ruleSet }?.second ?: throw IllegalArgumentException("No code for $ruleSet")

    fun format(ruleSet: RuleSet, seed: Long): String {
        require(seed >= 0) { "Deal numbers are never negative" }
        return "${prefix(ruleSet)}-$seed"
    }

    /** The rule set and deal number in [text], or null if it isn't a deal code. */
    fun parse(text: String): Pair<RuleSet, Long>? {
        val t = text.uppercase().filterNot { it.isWhitespace() }.removePrefix("#")
        if (t.length < 3) return null
        val rs = PREFIXES.firstOrNull { it.second == t.substring(0, 2) }?.first ?: return null
        val digits = t.substring(2).removePrefix("-")
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val seed = digits.toLongOrNull() ?: return null // too big
        return rs to seed
    }
}
