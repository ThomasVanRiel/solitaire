package io.github.thomasvanriel.solitaire.engine.model

/**
 * A single move. Columns are 0-based. Suits use [Card]'s numbering.
 *
 * In Spider and Scorpion, [Draw] deals from the stock onto the columns, and [TableauToFoundation] takes a
 * complete King-to-Ace run off a column. The waste moves are Klondike only, foundation-to-tableau is
 * Klondike and Yukon, and the free-cell moves are FreeCell only.
 *
 * Every move has a compact [code] that fits in 17 bits:
 * bits 0-2 type, `a` in bits 3-5 (low) and 13 (high), `b` in bits 6-8 and 14, `c` in bits 9-12 and 15-16.
 * Klondike only ever needs the low bits, so its codes fit in 13 bits and match the original format.
 * The free-cell moves share type 7 and keep their kind in `c` ([CELL_IN], [CELL_OUT], [CELL_UP]).
 * The rules engine works directly on these codes, so the solver and the game share one code path.
 */
sealed class Move {
    abstract val code: Int

    data object Draw : Move() {
        override val code = encode(TYPE_DRAW, 0, 0, 0)
        override fun toString() = "Draw"
    }

    data object Recycle : Move() {
        override val code = encode(TYPE_RECYCLE, 0, 0, 0)
        override fun toString() = "Recycle"
    }

    data class WasteToTableau(val col: Int) : Move() {
        override val code get() = encode(TYPE_W2T, col, 0, 0)
        override fun toString() = "W→T${col + 1}"
    }

    data object WasteToFoundation : Move() {
        override val code = encode(TYPE_W2F, 0, 0, 0)
        override fun toString() = "W→F"
    }

    data class TableauToFoundation(val col: Int) : Move() {
        override val code get() = encode(TYPE_T2F, col, 0, 0)
        override fun toString() = "T${col + 1}→F"
    }

    data class TableauToTableau(val from: Int, val count: Int, val to: Int) : Move() {
        override val code get() = encode(TYPE_T2T, from, to, count)
        override fun toString() = "T${from + 1}→T${to + 1}" + if (count > 1) "×$count" else ""
    }

    data class FoundationToTableau(val suit: Int, val col: Int) : Move() {
        override val code get() = encode(TYPE_F2T, suit, col, 0)
        override fun toString() = "F${"CDHS"[suit]}→T${col + 1}"
    }

    /** FreeCell: the top card of column [col] goes into the first empty free cell. */
    data class TableauToCell(val col: Int) : Move() {
        override val code get() = encode(TYPE_CELL, col, 0, CELL_IN)
        override fun toString() = "T${col + 1}→C"
    }

    /** FreeCell: the card in free cell [cell] goes onto column [col]. */
    data class CellToTableau(val cell: Int, val col: Int) : Move() {
        override val code get() = encode(TYPE_CELL, cell, col, CELL_OUT)
        override fun toString() = "C${cell + 1}→T${col + 1}"
    }

    /** FreeCell: the card in free cell [cell] goes to its foundation. */
    data class CellToFoundation(val cell: Int) : Move() {
        override val code get() = encode(TYPE_CELL, cell, 0, CELL_UP)
        override fun toString() = "C${cell + 1}→F"
    }

    companion object {
        const val TYPE_DRAW = 0
        const val TYPE_RECYCLE = 1
        const val TYPE_W2T = 2
        const val TYPE_W2F = 3
        const val TYPE_T2F = 4
        const val TYPE_T2T = 5
        const val TYPE_F2T = 6
        const val TYPE_CELL = 7

        /** Kinds of [TYPE_CELL] move, stored in `c`. */
        const val CELL_IN = 0
        const val CELL_OUT = 1
        const val CELL_UP = 2

        const val CODE_BITS = 17
        const val CODE_MASK = (1 shl CODE_BITS) - 1

        /** [a] and [b] go up to 15 (columns, suits), [c] up to 63 (run length). */
        fun encode(type: Int, a: Int, b: Int, c: Int): Int =
            type or ((a and 7) shl 3) or ((b and 7) shl 6) or ((c and 15) shl 9) or
                ((a shr 3) shl 13) or ((b shr 3) shl 14) or ((c shr 4) shl 15)

        fun type(code: Int): Int = code and 7
        fun a(code: Int): Int = ((code shr 3) and 7) or (((code shr 13) and 1) shl 3)
        fun b(code: Int): Int = ((code shr 6) and 7) or (((code shr 14) and 1) shl 3)
        fun c(code: Int): Int = ((code shr 9) and 15) or (((code shr 15) and 3) shl 4)

        fun decode(code: Int): Move {
            require(code in 0..CODE_MASK) { "Bad move code $code" }
            val a = a(code)
            val b = b(code)
            val c = c(code)
            val move = when (type(code)) {
                TYPE_DRAW -> Draw
                TYPE_RECYCLE -> Recycle
                TYPE_W2T -> WasteToTableau(a)
                TYPE_W2F -> WasteToFoundation
                TYPE_T2F -> TableauToFoundation(a)
                TYPE_T2T -> TableauToTableau(a, c, b)
                TYPE_F2T -> FoundationToTableau(a, b)
                TYPE_CELL -> when (c) {
                    CELL_IN -> TableauToCell(a)
                    CELL_OUT -> CellToTableau(a, b)
                    CELL_UP -> CellToFoundation(a)
                    else -> throw IllegalArgumentException("Bad cell move in code $code")
                }
                else -> throw IllegalArgumentException("Bad move type in code $code")
            }
            require(move.code == code) { "Non-canonical move code $code" }
            return move
        }

        /** Three bytes per move, big-endian. */
        fun encodeAll(moves: List<Move>): ByteArray {
            val out = ByteArray(moves.size * 3)
            moves.forEachIndexed { i, m ->
                out[3 * i] = (m.code shr 16).toByte()
                out[3 * i + 1] = (m.code shr 8).toByte()
                out[3 * i + 2] = m.code.toByte()
            }
            return out
        }

        /** Reads [encodeAll]'s format, or with [bytesPerMove] = 2 the older two-byte Klondike format. */
        fun decodeAll(bytes: ByteArray, bytesPerMove: Int = 3): List<Move> {
            require(bytesPerMove in 2..3) { "Bad move size $bytesPerMove" }
            require(bytes.size % bytesPerMove == 0) { "Truncated move list" }
            return List(bytes.size / bytesPerMove) { i ->
                var code = 0
                for (k in 0 until bytesPerMove) code = (code shl 8) or (bytes[bytesPerMove * i + k].toInt() and 0xFF)
                decode(code)
            }
        }
    }
}
