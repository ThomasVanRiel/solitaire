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
 * Spider has columns t1 to t10, and its foundation counts are multiples of 13 (up to 104).
 */
object StateFormat {
    fun parse(text: String, ruleSet: RuleSet = RuleSet.DRAW_1_UNLIMITED): GameState {
        val s = GameState.forRules(ruleSet)
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
                    require(n.size == 4 && n.all { it in 0..ruleSet.deckSize }) { "found needs 4 counts" }
                    n.forEachIndexed { i, v -> s.found[i] = v }
                }
                key == "recycles" -> s.recycles = value.toInt()
                key.length >= 2 && key[0] == 't' && key.drop(1).toIntOrNull() in 1..s.columns -> {
                    val c = key.drop(1).toInt() - 1
                    val down = if ('|' in value) cards(value.substringBefore('|')) else emptyList()
                    val up = cards(value.substringAfter('|'))
                    val all = down + up
                    require(all.size <= s.tab[c].size) { "Column too long" }
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
        for (c in 0 until s.columns) {
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
