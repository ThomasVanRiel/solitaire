package io.github.thomasvanriel.winnable.engine.model

/**
 * A Klondike position, stored as plain int arrays so it is cheap to copy and fast to change.
 *
 * - `tab[c]` holds column c from the bottom (index 0) up. The lowest `tabDown[c]` cards are face down.
 * - `stock` and `waste` are stored bottom to top: the top card is at index `size - 1`.
 * - `found[s]` is how many cards of suit s are on their foundation (0..13).
 *
 * The fields are public for speed. Only [io.github.thomasvanriel.winnable.engine.rules.Rules] should
 * change them. The UI should treat states as read-only and use `Rules.apply`, which returns a new state.
 */
class GameState(
    @JvmField val tab: Array<IntArray>,
    @JvmField val tabSize: IntArray,
    @JvmField val tabDown: IntArray,
    @JvmField val stock: IntArray,
    @JvmField var stockSize: Int,
    @JvmField val waste: IntArray,
    @JvmField var wasteSize: Int,
    @JvmField val found: IntArray,
    @JvmField var recycles: Int,
) {
    constructor() : this(
        Array(COLUMNS) { IntArray(COLUMN_CAPACITY) }, IntArray(COLUMNS), IntArray(COLUMNS),
        IntArray(52), 0, IntArray(52), 0, IntArray(4), 0,
    )

    fun copy(): GameState = GameState(
        Array(COLUMNS) { tab[it].copyOf() }, tabSize.copyOf(), tabDown.copyOf(),
        stock.copyOf(), stockSize, waste.copyOf(), wasteSize, found.copyOf(), recycles,
    )

    // ---- read helpers ----

    fun column(c: Int): List<Int> = List(tabSize[c]) { tab[c][it] }
    fun faceUpCount(c: Int): Int = tabSize[c] - tabDown[c]
    fun isFaceUp(c: Int, index: Int): Boolean = index >= tabDown[c]

    /** Top card of column [c], or -1 if it is empty. */
    fun columnTop(c: Int): Int = if (tabSize[c] == 0) -1 else tab[c][tabSize[c] - 1]

    fun stockList(): List<Int> = List(stockSize) { stock[it] }
    fun wasteList(): List<Int> = List(wasteSize) { waste[it] }

    /** Top waste card, or -1. */
    fun wasteTop(): Int = if (wasteSize == 0) -1 else waste[wasteSize - 1]

    /** Top card on suit [s]'s foundation, or -1. */
    fun foundationTop(s: Int): Int = if (found[s] == 0) -1 else Card.of(s, found[s] - 1)

    val foundationCount: Int get() = found[0] + found[1] + found[2] + found[3]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false
        if (stockSize != other.stockSize || wasteSize != other.wasteSize || recycles != other.recycles) return false
        if (!found.contentEquals(other.found)) return false
        if (!tabSize.contentEquals(other.tabSize) || !tabDown.contentEquals(other.tabDown)) return false
        for (i in 0 until stockSize) if (stock[i] != other.stock[i]) return false
        for (i in 0 until wasteSize) if (waste[i] != other.waste[i]) return false
        for (c in 0 until COLUMNS) for (i in 0 until tabSize[c]) if (tab[c][i] != other.tab[c][i]) return false
        return true
    }

    override fun hashCode(): Int {
        var h = recycles
        for (c in 0 until COLUMNS) {
            h = h * 31 + tabDown[c]
            for (i in 0 until tabSize[c]) h = h * 31 + tab[c][i]
            h = h * 31 + 99
        }
        for (i in 0 until stockSize) h = h * 31 + stock[i]
        h = h * 31 + 77
        for (i in 0 until wasteSize) h = h * 31 + waste[i]
        for (s in 0 until 4) h = h * 31 + found[s]
        return h
    }

    override fun toString(): String = StateFormat.format(this)

    companion object {
        const val COLUMNS = 7

        /** 6 face-down cards plus a 13-card run is 19. The extra room is for hand-built test positions. */
        const val COLUMN_CAPACITY = 32
    }
}
