package io.github.thomasvanriel.solitaire.engine.pack

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.engine.solver.SolveResult
import io.github.thomasvanriel.solitaire.engine.solver.Solver
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PackTest {
    private val rs = RuleSet.DRAW_3_UNLIMITED

    private fun solved(seed: Long): List<Move>? =
        (Solver(rs, maxNodes = 100_000).solve(Deals.deal(seed)) as? SolveResult.Solved)?.moves

    @Test
    fun solutionCodecRoundTrips() {
        val seed = (1L..50L).first { solved(it) != null }
        val moves = solved(seed)!!
        val bytes = SolutionCodec.encode(Deals.deal(seed), rs, moves)
        assertEquals(moves.size, bytes.size)
        assertEquals(moves, SolutionCodec.decode(Deals.deal(seed), rs, bytes))
        assertThrows<IllegalArgumentException> { SolutionCodec.encode(Deals.deal(seed), rs, listOf(Move.Recycle)) }
        assertThrows<IllegalArgumentException> { SolutionCodec.decode(Deals.deal(seed), rs, byteArrayOf(-1)) }
    }

    @Test
    fun packRoundTripsAndRejectsBadProofs() {
        val entries = (1L..40L).mapNotNull { seed ->
            solved(seed)?.let { PackEntry(seed, (seed % 31).toInt(), seed * 1000, SolutionCodec.encode(Deals.deal(seed), rs, it)) }
        }.take(5)
        val pack = DealPack(Deals.SHUFFLE_V1, rs, Tier.HARD, 30, entries)
        val bytes = ByteArrayOutputStream().also { DealPack.write(pack, it) }.toByteArray()
        val back = DealPack.read(ByteArrayInputStream(bytes))
        assertEquals(rs, back.ruleSet)
        assertEquals(Tier.HARD, back.tier)
        assertEquals(30, back.playouts)
        assertEquals(entries.size, back.entries.size)
        for ((a, b) in entries.zip(back.entries)) {
            assertEquals(a.seed, b.seed)
            assertEquals(a.wins, b.wins)
            assertEquals(a.solverNodes, b.solverNodes)
            assertArrayEquals(a.solution, b.solution)
            assertNotNull(back.verifiedSolution(b))
        }
        // A truncated solution, or one attached to the wrong seed, must not verify.
        val e = entries[0]
        assertNull(back.verifiedSolution(PackEntry(e.seed, 0, 0, e.solution.copyOf(e.solution.size - 1))))
        assertNull(back.verifiedSolution(PackEntry(e.seed + 1000, 0, 0, e.solution)))
        assertThrows<IllegalArgumentException> { DealPack.read(ByteArrayInputStream(gzip("nope".toByteArray()))) }
    }

    private fun gzip(b: ByteArray): ByteArray =
        ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
}
