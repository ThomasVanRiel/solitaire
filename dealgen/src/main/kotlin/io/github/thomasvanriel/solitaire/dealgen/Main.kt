package io.github.thomasvanriel.solitaire.dealgen

import io.github.thomasvanriel.solitaire.engine.difficulty.DifficultyRater
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.pack.DealPack
import io.github.thomasvanriel.solitaire.engine.pack.PackEntry
import io.github.thomasvanriel.solitaire.engine.pack.SolutionCodec
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.engine.solver.ProofVerifier
import io.github.thomasvanriel.solitaire.engine.solver.SolveResult
import io.github.thomasvanriel.solitaire.engine.solver.Solver
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.system.exitProcess

private const val USAGE = """
Usage: ./gradlew :dealgen:run --args="[options]"

  --rules draw1,draw3   rule sets to generate (default: draw1,draw3)
  --per-tier N          deals per tier per rule set (default: 2500)
  --start-seed S        first seed to try (default: 1)
  --threads T           worker threads (default: CPU count - 1)
  --playouts P          heuristic playouts per deal for the difficulty rating (default: 30)
  --nodes N             solver node budget per deal (default: 300000)
  --out DIR             output folder (default: app/src/main/assets/deals)
  --calibrate N         only rate the first N winnable deals and print the win-rate histogram
"""

private class Options(
    val ruleSets: List<RuleSet>,
    val perTier: Int,
    val startSeed: Long,
    val threads: Int,
    val playouts: Int,
    val nodes: Long,
    val out: File,
    val calibrate: Int,
)

private class Outcome(val seed: Long, val entry: PackEntry?, val tier: Tier?, val kind: String)

fun main(args: Array<String>) {
    val o = try {
        parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        System.err.println(USAGE)
        exitProcess(2)
    }
    val pool = Executors.newFixedThreadPool(o.threads)
    try {
        for (rs in o.ruleSets) {
            if (o.calibrate > 0) calibrate(rs, o, pool) else generate(rs, o, pool)
        }
    } finally {
        pool.shutdownNow()
    }
}

private fun parse(args: Array<String>): Options {
    val map = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        require(key.startsWith("--") && i + 1 < args.size) { "Bad argument '$key'" }
        map[key.removePrefix("--")] = args[i + 1]
        i += 2
    }
    val known = setOf("rules", "per-tier", "start-seed", "threads", "playouts", "nodes", "out", "calibrate")
    val unknown = map.keys - known
    require(unknown.isEmpty()) { "Unknown option(s): $unknown" }
    val rules = (map["rules"] ?: "draw1,draw3").split(',').map {
        when (it.trim()) {
            "draw1" -> RuleSet.DRAW_1_UNLIMITED
            "draw3" -> RuleSet.DRAW_3_UNLIMITED
            else -> throw IllegalArgumentException("Unknown rule set '$it'")
        }
    }
    return Options(
        ruleSets = rules,
        perTier = map["per-tier"]?.toInt() ?: 2500,
        startSeed = map["start-seed"]?.toLong() ?: 1L,
        threads = map["threads"]?.toInt() ?: maxOf(1, Runtime.getRuntime().availableProcessors() - 1),
        playouts = map["playouts"]?.toInt() ?: DifficultyRater.DEFAULT_PLAYOUTS,
        nodes = map["nodes"]?.toLong() ?: 300_000L,
        out = File(map["out"] ?: "app/src/main/assets/deals"),
        calibrate = map["calibrate"]?.toInt() ?: 0,
    )
}

private val solvers = ThreadLocal<HashMap<Pair<RuleSet, Long>, Solver>>()

/** Solves, verifies and rates one seed. Nothing is accepted unless ProofVerifier replays it to a win. */
private fun process(seed: Long, rs: RuleSet, o: Options): Outcome {
    val cache = solvers.get() ?: HashMap<Pair<RuleSet, Long>, Solver>().also { solvers.set(it) }
    val solver = cache.getOrPut(rs to o.nodes) { Solver(rs, maxNodes = o.nodes, timeLimitMs = 120_000) }
    val start = Deals.deal(seed)
    return when (val r = solver.solve(start)) {
        is SolveResult.Unsolvable -> Outcome(seed, null, null, "unsolvable")
        is SolveResult.Unknown -> Outcome(seed, null, null, "unknown")
        is SolveResult.Solved -> {
            check(ProofVerifier.verify(seed, rs, r.moves)) { "Solver produced a bad proof for seed $seed ($rs)" }
            val solution = try {
                SolutionCodec.encode(start, rs, r.moves)
            } catch (_: IllegalArgumentException) {
                return Outcome(seed, null, null, "unencodable")
            }
            val wins = DifficultyRater.wins(start, rs, seed, o.playouts)
            Outcome(seed, PackEntry(seed, wins, r.nodes, solution), DifficultyRater.tierFor(wins, o.playouts), "solved")
        }
    }
}

/** Processes seeds in ordered batches; the callback sees outcomes in seed order and returns false to stop. */
private fun runBatches(rs: RuleSet, o: Options, pool: java.util.concurrent.ExecutorService, onOutcome: (Outcome) -> Boolean) {
    var next = o.startSeed
    val batch = 64 * o.threads
    while (true) {
        val futures = (0 until batch).map { k ->
            val seed = next + k
            pool.submit(Callable { process(seed, rs, o) })
        }
        next += batch
        for (f in futures) {
            if (!onOutcome(f.get())) {
                futures.forEach { it.cancel(true) }
                return
            }
        }
    }
}

private fun calibrate(rs: RuleSet, o: Options, pool: java.util.concurrent.ExecutorService) {
    val histogram = IntArray(o.playouts + 1)
    var solved = 0
    var other = 0
    val t0 = System.nanoTime()
    runBatches(rs, o, pool) { out ->
        if (out.entry != null) {
            histogram[out.entry.wins]++
            solved++
        } else other++
        solved < o.calibrate
    }
    println("== Calibration for $rs: $solved winnable deals (plus $other skipped) in ${secs(t0)} s")
    var cumulative = 0
    for (w in 0..o.playouts) {
        cumulative += histogram[w]
        println("wins %2d/%d: %5d  (%5.1f%%, cumulative %5.1f%%)".format(w, o.playouts, histogram[w], 100.0 * histogram[w] / solved, 100.0 * cumulative / solved))
    }
}

private fun generate(rs: RuleSet, o: Options, pool: java.util.concurrent.ExecutorService) {
    val byTier = Tier.entries.associateWith { ArrayList<PackEntry>() }
    val counts = HashMap<String, Int>()
    var processed = 0
    val t0 = System.nanoTime()
    var lastReport = System.nanoTime()
    runBatches(rs, o, pool) { out ->
        processed++
        counts.merge(out.kind, 1, Int::plus)
        val list = out.tier?.let { byTier.getValue(it) }
        if (list != null && list.size < o.perTier) list += out.entry!!
        if (System.nanoTime() - lastReport > 30_000_000_000L) {
            lastReport = System.nanoTime()
            println("$rs: ${processed} seeds, ${secs(t0)} s, tiers " + byTier.entries.joinToString { "${it.key.label}=${it.value.size}" })
        }
        byTier.values.any { it.size < o.perTier }
    }
    o.out.mkdirs()
    println("== $rs: $processed seeds in ${secs(t0)} s. Outcomes: $counts")
    for ((tier, entries) in byTier) {
        val pack = DealPack(Deals.CURRENT_SHUFFLE, rs, tier, o.playouts, entries)
        val file = File(o.out, DealPack.fileName(rs, tier))
        file.outputStream().use { DealPack.write(pack, it) }
        // Read back and verify every proof in the written file.
        val back = file.inputStream().use { DealPack.read(it) }
        val bad = back.entries.count { back.verifiedSolution(it) == null }
        check(bad == 0) { "$bad entries in ${file.name} failed verification" }
        val avgLen = entries.map { it.solution.size }.average()
        println("   ${file.name}: ${entries.size} deals, ${file.length() / 1024} KiB, avg solution %.0f moves, all proofs verified".format(avgLen))
    }
}

private fun secs(t0: Long) = "%.1f".format((System.nanoTime() - t0) / 1e9)
