package io.github.thomasvanriel.solitaire.extras

import kotlin.random.Random

/** Mixes numbers into one well-spread seed (SplitMix64 finaliser), so nearby inputs give unrelated picks. */
internal fun mix(vararg parts: Long): Long {
    var h = 0x9E3779B97F4A7C15uL.toLong()
    for (p in parts) {
        h = (h xor p) * -4658895280553007687L
        h = h xor (h ushr 31)
    }
    h = (h xor (h ushr 30)) * -4658895280553007687L
    h = (h xor (h ushr 27)) * -7723592293110705685L
    return h xor (h ushr 31)
}

/** Deterministic choices: the same seed always picks the same lines. */
internal class Picker(seed: Long) {
    private val random = Random(seed)

    fun chance(p: Double): Boolean = random.nextDouble() < p

    fun int(from: Int, until: Int): Int = random.nextInt(from, until)

    /** One of [lines], but not [avoid] when there is any other choice. */
    fun one(lines: List<String>, avoid: String?): String {
        require(lines.isNotEmpty())
        val choices = lines.filter { it != avoid }.ifEmpty { lines }
        return choices[random.nextInt(choices.size)]
    }

    /** One of [lines] that isn't [avoid], or null if there is none (so the caller tries other lines). */
    fun fresh(lines: List<String>, avoid: String?): String? {
        val choices = lines.filter { it != avoid }
        return if (choices.isEmpty()) null else choices[random.nextInt(choices.size)]
    }
}

internal fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatClock(hour: Int, minute: Int): String = "%d:%02d".format(hour, minute)
