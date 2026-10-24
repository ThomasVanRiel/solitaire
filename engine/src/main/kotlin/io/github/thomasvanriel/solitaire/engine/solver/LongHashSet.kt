package io.github.thomasvanriel.solitaire.engine.solver

/** A minimal open-addressing set of longs (the transposition table). Zero is stored as one. */
internal class LongHashSet(initialCapacity: Int = 1 shl 16) {
    private var keys = LongArray(Integer.highestOneBit(maxOf(16, initialCapacity) * 2 - 1))
    private var mask = keys.size - 1
    var size = 0
        private set

    /** Adds [key]; returns false if it was already present. */
    fun add(key: Long): Boolean {
        val k = if (key == 0L) 1L else key
        var i = (k xor (k ushr 29)).toInt() and mask
        while (true) {
            val v = keys[i]
            if (v == 0L) {
                keys[i] = k
                if (++size * 2 > keys.size) grow()
                return true
            }
            if (v == k) return false
            i = (i + 1) and mask
        }
    }

    fun clear() {
        keys.fill(0L)
        size = 0
    }

    private fun grow() {
        val old = keys
        keys = LongArray(old.size * 2)
        mask = keys.size - 1
        size = 0
        for (k in old) if (k != 0L) add(k)
    }
}
