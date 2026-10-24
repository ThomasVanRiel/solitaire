package io.github.thomasvanriel.solitaire.engine.solver

import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Run with: ./gradlew :engine:test -Pbench --tests '*SolverBenchmark' [-PbenchSeeds=1000] [-PbenchNodes=2000000]
 * Also checks that every Solved result passes ProofVerifier.
 */
@Tag("benchmark")
class SolverBenchmark {
    @Test
    fun benchmark() {
        val seeds = System.getProperty("bench.seeds", "1000").toInt()
        val maxNodes = System.getProperty("bench.nodes", "2000000").toLong()
        for (rs in listOf(RuleSet.DRAW_1_UNLIMITED, RuleSet.DRAW_3_UNLIMITED)) {
            // Warm up the JIT so the first deals aren't unfairly slow.
            val warm = Solver(rs, maxNodes = 50_000)
            for (seed in 100_000L until 100_020L) warm.solve(Deals.deal(seed))

            val solver = Solver(rs, maxNodes = maxNodes, timeLimitMs = 60_000)
            var solved = 0; var unsolvable = 0; var unknown = 0
            val times = DoubleArray(seeds)
            val nodes = LongArray(seeds)
            val lengths = ArrayList<Int>()
            val slow = ArrayList<String>()
            for (i in 0 until seeds) {
                val seed = i.toLong()
                val t0 = System.nanoTime()
                val r = solver.solve(Deals.deal(seed))
                times[i] = (System.nanoTime() - t0) / 1e6
                nodes[i] = r.nodes
                when (r) {
                    is SolveResult.Solved -> {
                        solved++
                        lengths += r.moves.size
                        assertTrue(ProofVerifier.verify(seed, rs, r.moves), "proof failed for seed $seed $rs")
                    }
                    is SolveResult.Unsolvable -> unsolvable++
                    is SolveResult.Unknown -> unknown++
                }
                if (times[i] > 1000) slow += "$seed:${r.javaClass.simpleName}:${"%.0f".format(times[i])}ms"
            }
            val sorted = times.sorted()
            fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
            val sortedNodes = nodes.sorted()
            println(
                "BENCH $rs seeds=$seeds maxNodes=$maxNodes solved=${pct(solved, seeds)} unsolvable=${pct(unsolvable, seeds)} " +
                    "unknown=${pct(unknown, seeds)} medianMs=${"%.1f".format(pct(0.5))} p95Ms=${"%.1f".format(pct(0.95))} " +
                    "maxMs=${"%.0f".format(sorted.last())} totalS=${"%.1f".format(times.sum() / 1000)} " +
                    "medianNodes=${sortedNodes[seeds / 2]} medianLen=${lengths.sorted().getOrElse(lengths.size / 2) { 0 }}",
            )
            println("BENCH slow (>1s): ${slow.take(40)}")
        }
    }

    private fun pct(n: Int, of: Int) = "%.1f%%".format(100.0 * n / of)
}
