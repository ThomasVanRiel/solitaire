package io.github.thomasvanriel.solitaire.engine.model

/**
 * Plain-text form of a position, used for hand-built test positions and debugging:
 *
 * ```
 * stock: 2H 3C          (bottom to top)
 * waste: 5S             (bottom to top)
 * found: 3 0 0 1        (cards on the Clubs, Diamonds, Hearts and Spades foundations)
 * t1: 5S 2D | KH QS     (bottom to top; cards left of '|' are face down)
 * t2:                   (empty column; missing lines are empty too)
 * recycles: 0           (optional)
 * ```
 */
object StateFormat {
    fun parse(text: String): GameState {
        val s = GameState()
        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            when {
                key == "stock" -> cards(value).forEach { s.stock[s.stockSize++] = it }
                key == "waste" -> cards(value).forEach { s.waste[s.wasteSize++] = it }
                key == "found" -> {
                    val n = value.split(Regex("\\s+")).map { it.toInt() }
                    require(n.size == 4 && n.all { it in 0..13 }) { "found needs 4 counts from 0 to 13" }
                    n.forEachIndexed { i, v -> s.found[i] = v }
                }
                key == "recycles" -> s.recycles = value.toInt()
                key.length == 2 && key[0] == 't' && key[1] in '1'..'7' -> {
                    val c = key[1] - '1'
                    val down = if ('|' in value) cards(value.substringBefore('|')) else emptyList()
                    val up = cards(value.substringAfter('|'))
                    val all = down + up
                    require(all.size <= GameState.COLUMN_CAPACITY) { "Column too long" }
                    all.forEachIndexed { i, card -> s.tab[c][i] = card }
                    s.tabSize[c] = all.size
                    s.tabDown[c] = down.size
                }
                else -> throw IllegalArgumentException("Unknown line '$raw'")
            }
        }
        return s
    }

    fun format(s: GameState): String = buildString {
        appendLine("stock: " + s.stockList().joinToString(" ") { Card.code(it) })
        appendLine("waste: " + s.wasteList().joinToString(" ") { Card.code(it) })
        appendLine("found: " + s.found.joinToString(" "))
        for (c in 0 until GameState.COLUMNS) {
            val col = s.column(c).map { Card.code(it) }
            val down = col.take(s.tabDown[c]).joinToString(" ")
            val up = col.drop(s.tabDown[c]).joinToString(" ")
            appendLine("t${c + 1}: " + if (s.tabDown[c] > 0) "$down | $up" else up)
        }
        append("recycles: ${s.recycles}")
    }

    private fun cards(text: String): List<Int> =
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Card.parse(it) }
}
