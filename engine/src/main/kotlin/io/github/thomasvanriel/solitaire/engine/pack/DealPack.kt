package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.engine.solver.ProofVerifier
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One verified deal: its seed, how often the heuristic player won it, and its winning moves. */
class PackEntry(
    val seed: Long,
    val wins: Int,
    val solverNodes: Long,
    /** Solution in [SolutionCodec] form. */
    val solution: ByteArray,
)

/**
 * A file of verified deals for one (rule set, tier). Gzipped binary:
 * magic "WPAK", format version, shuffle version, rule set id, tier id, playouts, entry count,
 * then per entry: seed, wins, solver nodes, solution length, solution bytes (varints where it helps).
 */
class DealPack(
    val shuffleVersion: Int,
    val ruleSet: RuleSet,
    val tier: Tier,
    val playouts: Int,
    val entries: List<PackEntry>,
) {
    /** Decodes and replays an entry's solution. Returns the moves only if the proof holds, otherwise null. */
    fun verifiedSolution(entry: PackEntry): List<Move>? {
        val moves = try {
            SolutionCodec.decode(Deals.deal(entry.seed, ruleSet, shuffleVersion), ruleSet, entry.solution)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return if (ProofVerifier.verify(entry.seed, ruleSet, moves, shuffleVersion)) moves else null
    }

    companion object {
        const val FORMAT_VERSION = 1
        private val MAGIC = byteArrayOf('W'.code.toByte(), 'P'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte())

        fun fileName(ruleSet: RuleSet, tier: Tier) = "${ruleSet.name.lowercase()}_${tier.name.lowercase()}.wpak"

        fun write(pack: DealPack, out: OutputStream) {
            DataOutputStream(GZIPOutputStream(out)).use { d ->
                d.write(MAGIC)
                d.writeByte(FORMAT_VERSION)
                d.writeByte(pack.shuffleVersion)
                d.writeByte(pack.ruleSet.id)
                d.writeByte(pack.tier.id)
                d.writeByte(pack.playouts)
                d.writeInt(pack.entries.size)
                for (e in pack.entries) {
                    writeVarLong(d, e.seed)
                    d.writeByte(e.wins)
                    writeVarLong(d, e.solverNodes)
                    writeVarLong(d, e.solution.size.toLong())
                    d.write(e.solution)
                }
            }
        }

        fun read(input: InputStream): DealPack {
            DataInputStream(GZIPInputStream(input)).use { d ->
                val magic = ByteArray(4).also { d.readFully(it) }
                require(magic.contentEquals(MAGIC)) { "Not a deal pack" }
                val format = d.readUnsignedByte()
                require(format == FORMAT_VERSION) { "Unsupported pack format $format" }
                val shuffle = d.readUnsignedByte()
                val ruleSet = RuleSet.byId(d.readUnsignedByte())
                val tier = Tier.byId(d.readUnsignedByte())
                val playouts = d.readUnsignedByte()
                val count = d.readInt()
                require(count in 0..10_000_000) { "Bad entry count $count" }
                val entries = List(count) {
                    val seed = readVarLong(d)
                    val wins = d.readUnsignedByte()
                    val nodes = readVarLong(d)
                    val len = readVarLong(d).toInt()
                    require(len in 0..100_000) { "Bad solution length $len" }
                    PackEntry(seed, wins, nodes, ByteArray(len).also { d.readFully(it) })
                }
                return DealPack(shuffle, ruleSet, tier, playouts, entries)
            }
        }

        private fun writeVarLong(d: DataOutputStream, value: Long) {
            require(value >= 0) { "Negative values are not stored" }
            var v = value
            while (v >= 0x80) {
                d.writeByte(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            d.writeByte(v.toInt())
        }

        private fun readVarLong(d: DataInputStream): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = d.read()
                if (b < 0) throw EOFException()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "Varint too long" }
            }
        }
    }
}
