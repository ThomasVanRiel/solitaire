package io.github.thomasvanriel.solitaire.engine.model

/**
 * A single move. Columns are 0-based (0..6). Suits use [Card]'s numbering.
 *
 * Every move has a compact [code] that fits in 13 bits (two bytes in storage):
 * bits 0-2 type, bits 3-5 `a`, bits 6-8 `b`, bits 9-12 `c`.
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

    companion object {
        const val TYPE_DRAW = 0
        const val TYPE_RECYCLE = 1
        const val TYPE_W2T = 2
        const val TYPE_W2F = 3
        const val TYPE_T2F = 4
        const val TYPE_T2T = 5
        const val TYPE_F2T = 6

        const val CODE_BITS = 13
        const val CODE_MASK = (1 shl CODE_BITS) - 1

        fun encode(type: Int, a: Int, b: Int, c: Int): Int =
            type or (a shl 3) or (b shl 6) or (c shl 9)

        fun type(code: Int): Int = code and 7
        fun a(code: Int): Int = (code shr 3) and 7
        fun b(code: Int): Int = (code shr 6) and 7
        fun c(code: Int): Int = (code shr 9) and 15

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
                else -> throw IllegalArgumentException("Bad move type in code $code")
            }
            require(move.code == code) { "Non-canonical move code $code" }
            return move
        }

        /** Two bytes per move, big-endian. */
        fun encodeAll(moves: List<Move>): ByteArray {
            val out = ByteArray(moves.size * 2)
            moves.forEachIndexed { i, m ->
                out[2 * i] = (m.code shr 8).toByte()
                out[2 * i + 1] = m.code.toByte()
            }
            return out
        }

        fun decodeAll(bytes: ByteArray): List<Move> {
            require(bytes.size % 2 == 0) { "Odd number of bytes in a move list" }
            return List(bytes.size / 2) { i ->
                decode(((bytes[2 * i].toInt() and 0xFF) shl 8) or (bytes[2 * i + 1].toInt() and 0xFF))
            }
        }
    }
}
