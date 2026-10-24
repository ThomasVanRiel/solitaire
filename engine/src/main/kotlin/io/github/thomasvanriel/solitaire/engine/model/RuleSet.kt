package io.github.thomasvanriel.solitaire.engine.model

/** The patience game a rule set belongs to. */
enum class GameType(val label: String) {
    /** 7 columns, stock and waste, foundations built up by suit from the Ace. */
    KLONDIKE("Klondike"),

    /**
     * Two decks, 10 columns. Build down regardless of suit; only same-suit runs move together; any card may
     * go into an empty column. A deal puts one card on every column and needs every column filled.
     * A complete King-to-Ace run of one suit leaves the board.
     */
    SPIDER("Spider"),

    /**
     * Scorpion II: one deck, 7 columns of 7, the first three with 3 face-down cards. Build down in suit.
     * Any face-up card moves together with every card on top of it. Only Kings go into empty columns.
     * The 3 reserve cards are dealt once, one onto each of the first three columns.
     * A complete King-to-Ace run of one suit leaves the board.
     */
    SCORPION("Scorpion II"),
}

/**
 * The rules that decide which deals are winnable. Every verified deal stores the [id] of the rule set
 * it was verified for.
 *
 * @param drawCount Klondike: cards turned from the stock per draw (1 or 3). Not used by other games.
 * @param maxRecycles Klondike: how many times the waste may be turned back into the stock; -1 means unlimited.
 * @param suits Spider: how many different suits the two decks use (1, 2 or 4). 4 for the other games.
 */
class RuleSet private constructor(
    val id: Int,
    val name: String,
    val game: GameType,
    val drawCount: Int,
    val maxRecycles: Int,
    val suits: Int = 4,
) {
    val unlimitedRecycles: Boolean get() = maxRecycles < 0

    val columns: Int get() = if (game == GameType.SPIDER) 10 else 7
    val deckSize: Int get() = if (game == GameType.SPIDER) 104 else 52

    /** Longest a column can ever get (plus room for hand-built test positions in Klondike). */
    val columnCapacity: Int get() = when (game) {
        GameType.KLONDIKE -> 32
        else -> deckSize
    }

    /** Short name for the player, such as "Draw 3", "2 suits" or "Scorpion II". Unique within a [game]. */
    val variantLabel: String get() = when (game) {
        GameType.KLONDIKE -> "Draw $drawCount"
        GameType.SPIDER -> if (suits == 1) "1 suit" else "$suits suits"
        GameType.SCORPION -> "Scorpion II"
    }

    /** Full name for the player, such as "Draw 3", "Spider 2 suits" or "Scorpion II". */
    val label: String get() = when (game) {
        GameType.KLONDIKE -> variantLabel
        GameType.SPIDER -> "Spider $variantLabel"
        GameType.SCORPION -> variantLabel
    }

    override fun toString(): String = name

    companion object {
        val DRAW_1_UNLIMITED = RuleSet(1, "DRAW_1_UNLIMITED", GameType.KLONDIKE, 1, -1)
        val DRAW_3_UNLIMITED = RuleSet(2, "DRAW_3_UNLIMITED", GameType.KLONDIKE, 3, -1)
        val SPIDER_1_SUIT = RuleSet(3, "SPIDER_1_SUIT", GameType.SPIDER, 0, 0, suits = 1)
        val SPIDER_2_SUITS = RuleSet(4, "SPIDER_2_SUITS", GameType.SPIDER, 0, 0, suits = 2)
        val SPIDER_4_SUITS = RuleSet(5, "SPIDER_4_SUITS", GameType.SPIDER, 0, 0, suits = 4)
        val SCORPION_II = RuleSet(6, "SCORPION_II", GameType.SCORPION, 0, 0)

        val ALL: List<RuleSet> = listOf(DRAW_1_UNLIMITED, DRAW_3_UNLIMITED, SPIDER_1_SUIT, SPIDER_2_SUITS, SPIDER_4_SUITS, SCORPION_II)

        fun byId(id: Int): RuleSet = ALL.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown rule set id $id")

        fun byName(name: String): RuleSet = ALL.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Unknown rule set $name")

        /** For tests and future modes (for example Vegas with a pass limit). Not served to players. */
        fun custom(drawCount: Int, maxRecycles: Int): RuleSet {
            require(drawCount >= 1) { "drawCount must be at least 1" }
            return RuleSet(1000 + drawCount * 100 + maxRecycles, "CUSTOM_D${drawCount}_R$maxRecycles", GameType.KLONDIKE, drawCount, maxRecycles)
        }
    }
}
