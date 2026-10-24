package io.github.thomasvanriel.winnable.engine.shuffle

/**
 * xoshiro256** (Blackman and Vigna), seeded with SplitMix64. Implemented here rather than using
 * kotlin.random or java.util.Random so that seed → deal never changes on any device or JVM.
 */
class Xoshiro256 private constructor(
    private var s0: Long,
    private var s1: Long,
    private var s2: Long,
    private var s3: Long,
) {
    fun nextLong(): Long {
        val result = java.lang.Long.rotateLeft(s1 * 5, 7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = java.lang.Long.rotateLeft(s3, 45)
        return result
    }

    /** Uniform int in [0, bound), without modulo bias. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        while (true) {
            val r = (nextLong() ushr 33).toInt() // 31 random bits
            val v = r % bound
            if (r - v + (bound - 1) >= 0) return v
        }
    }

    companion object {
        fun seeded(seed: Long): Xoshiro256 {
            val sm = SplitMix64(seed)
            return Xoshiro256(sm.next(), sm.next(), sm.next(), sm.next())
        }

        /** For checking against the reference implementation. */
        internal fun fromState(a: Long, b: Long, c: Long, d: Long) = Xoshiro256(a, b, c, d)
    }
}

class SplitMix64(private var x: Long) {
    fun next(): Long {
        x += GOLDEN
        var z = x
        z = (z xor (z ushr 30)) * M1
        z = (z xor (z ushr 27)) * M2
        return z xor (z ushr 31)
    }

    private companion object {
        val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
        val M1 = 0xBF58476D1CE4E5B9uL.toLong()
        val M2 = 0x94D049BB133111EBuL.toLong()
    }
}
