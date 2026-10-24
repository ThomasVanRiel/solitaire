package io.github.thomasvanriel.winnable.engine.model

/**
 * The rules that decide which deals are winnable. Every verified deal stores the [id] of the rule set
 * it was verified for.
 *
 * @param drawCount cards turned from the stock per draw (1 or 3).
 * @param maxRecycles how many times the waste may be turned back into the stock; -1 means unlimited.
 */
class RuleSet private constructor(
    val id: Int,
    val name: String,
    val drawCount: Int,
    val maxRecycles: Int,
) {
    val unlimitedRecycles: Boolean get() = maxRecycles < 0

    override fun toString(): String = name

    companion object {
        val DRAW_1_UNLIMITED = RuleSet(1, "DRAW_1_UNLIMITED", 1, -1)
        val DRAW_3_UNLIMITED = RuleSet(2, "DRAW_3_UNLIMITED", 3, -1)

        val ALL: List<RuleSet> = listOf(DRAW_1_UNLIMITED, DRAW_3_UNLIMITED)

        fun byId(id: Int): RuleSet = ALL.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown rule set id $id")

        fun byName(name: String): RuleSet = ALL.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Unknown rule set $name")

        /** For tests and future modes (for example Vegas with a pass limit). Not served to players. */
        fun custom(drawCount: Int, maxRecycles: Int): RuleSet {
            require(drawCount >= 1) { "drawCount must be at least 1" }
            return RuleSet(1000 + drawCount * 100 + maxRecycles, "CUSTOM_D${drawCount}_R$maxRecycles", drawCount, maxRecycles)
        }
    }
}
