# Progress

## Working setup (how this project is being built)

The coding agent runs inside a jail that only mounts the project folder. It has no Android SDK and no
emulator access, so:
- the agent builds and tests `:engine` and `:dealgen` itself (pure JVM);
- the owner runs the `:app` Gradle builds on the host and writes the output to `logs/`;
- the agent installs APKs, launches the app and takes screenshots itself through `adb`, because the host's adb
  server on 127.0.0.1:5037 is reachable from the jail.

`settings.gradle.kts` leaves `:app` out when it can't find an SDK, so the JVM modules still build.

## M0: Environment and skeleton

### Toolchain found
| Tool | Where | Notes |
|---|---|---|
| JDK | `/opt/android-studio/jbr` (JBR 25.0.3) | `/usr/bin/java` is OpenJDK 21 but a runtime only, with no `javac`. Use `JAVA_HOME=/opt/android-studio/jbr`. |
| Gradle | wrapper 9.8.2 | Generated from the official distribution, checksum verified. |
| Android SDK | owner's host, `~/Android/Sdk` | Not visible to the agent. |
| adb | `/usr/bin/adb` | No emulator was reachable from the jail during M0. |
| Emulator | owner's host | Run by the owner. |

### Versions chosen (latest stable as of 2026-10-24)
- AGP 9.4.1. It needs Gradle ≥ 9.6 and supports up to API 37. AGP 9 has built-in Kotlin, so
  `org.jetbrains.kotlin.android` is **not** applied. The Compose compiler plugin still is.
- Kotlin 2.4.21, Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0,
  core-ktx 1.19.1, DataStore 1.3.0, coroutines 1.11.0, JUnit 6.1.3, Kover 0.9.9.
- `compileSdk = 37`, `targetSdk = 36`. Google Play has required API 36 for new apps and updates since
  2026-08-31 (developer.android.com/google/play/requirements/target-sdk). `minSdk = 26`.
- Bytecode target: JVM 17.

### Decisions
- Application ID: `io.github.thomasvanriel.solitaire`. The owner's GitHub username is ThomasVanRiel,
  lowercased because Java package names are lowercase by convention.
- License: GPL-3.0-or-later (the default in the plan).
- Engine benchmarks are JUnit tests tagged `benchmark`. They only run with `-Pbench`.

### Status
- `:engine:test` and `:dealgen:run` pass in the agent's jail.
- The owner ran `:engine:test :app:assembleDebug` on the host: it passed (log in `logs/m0-build.log`, not committed).
- The agent installed the APK on `emulator-5554` with `adb install` (reaching the host's adb server over the
  shared network), launched it, and checked `screenshots/m0-hello.png`. **M0 done.**

### Known issues
- Gradle reports a deprecation (`Configuration.setVisible`). It comes from a plugin, not from our build scripts.
- AGP prints "Unable to strip libandroidx.graphics.path.so". This is harmless.

## Plan changes agreed with the owner (2026-10-24)
- **Renamed to "Solitaire"** at the owner's request: application ID `io.github.thomasvanriel.solitaire`,
  package `io.github.thomasvanriel.solitaire`, Gradle project `solitaire`, app name "Solitaire".
  Nothing had been released, so changing the ID is safe. The debug APK installed on the emulator during M0
  used the old ID and should be uninstalled.
- **Keep the app simple.** The owner asked for "only a New game and Undo button, nothing too fancy".
  Main controls: **New game** and **Undo**. Everything else in PLAN 4.6/4.7 that adds UI (hint, the "still
  winnable?" check, stats screen, a large settings screen, tiers chosen per game, daily deal, left-handed
  mode and so on) is dropped or cut down to the minimum. Each milestone below records exactly what was kept.
  The winnability guarantee (packs, proof replay, `ProofVerifier`) is unchanged.
- The agent may not install the Android SDK in its jail. The owner builds `:app` on the host when asked.

## M1: Rules engine

### What was built (`engine/`)
- `model/Card`: a card is an Int `suit*13 + rank`. Suits are C, D, H, S; D and H are red.
- `model/Move`: a sealed type with a 13-bit `code`, stored as 2 bytes. The rules engine works on these codes
  directly, so the UI, the solver and the verifier share one code path.
- `model/GameState`: int arrays (columns with a face-down count, stock, waste, foundation counts, recycle count).
  `copy()`, content `equals`/`hashCode`, and a text format (`StateFormat`) for hand-built positions.
- `model/RuleSet`: `DRAW_1_UNLIMITED` (id 1) and `DRAW_3_UNLIMITED` (id 2), plus `custom(draw, maxRecycles)`
  for a pass limit.
- `rules/Rules`: the only rules implementation.
  - `legalMoveCodes`, plus in-place `applyCode`/`undoCode` with an int undo token (flip flag, number of cards
    drawn, foundation suit).
  - Convenience versions `legalMoves`, `isLegal` and `apply` (which returns a new state).
  - "Legal" is *defined* as "appears in the legal move list", so there's a single definition.
- `rules/Game`: an immutable game (start, moves, all intermediate states) with play, undo, restart and replay.
- `rules/StateValidator`: invariant checker (52 unique cards, face-down cards only under face-up ones,
  foundations in sequence, face-up runs valid).
- `shuffle/`: SplitMix64 seeding xoshiro256** and an unbiased Fisher–Yates shuffle (`Deals.SHUFFLE_V1`).
  - Deal: row by row. Column c gets c+1 cards with the top one face up. The remaining 24 go to the stock, with
    deck[51] on top.

### Decisions
- Rule details: draw-3 at the end of the stock draws whatever is left. A recycle turns the waste over, so the
  first card drawn comes first again. A King, or a run starting with one, may enter an empty column,
  including from the foundation. Every face-up suffix of a column is a movable run; the generator also stops
  at a broken run for safety.
- Foundations are indexed by suit (C, D, H, S), not by slot. The UI can place them however it likes.
- `isLegal` builds the full move list (slower, but simple). The solver uses the raw generator.

### Tests
- Golden tests: the RNG matches the published xoshiro256** values for state (1,2,3,4) and SplitMix64(0).
  The decks for seeds 0, 1, 48213 and -7, and the deal layout for seed 1, are pinned.
- Hand-built positions cover every move type with apply and exact undo, draw-3 at the end of the stock,
  recycling, the pass limit, King to an empty column, moves back from the foundation, auto-flip, illegal moves,
  move-code round trips and validator failures.
- Fuzz test: 10,000 random games of random legal moves (draw-1, draw-3 and draw-3 with 2 recycles), over 1M
  moves, with invariants checked after every move. Undoing everything returns exactly the start position.
  Every move type occurs.
- Coverage (Kover): `rules/` 96.7% lines and 88.5% branches; `shuffle/` 94.1% lines.
- `:app:assembleDebug` was not rebuilt for M1. The app doesn't use the engine yet beyond `EngineInfo`.
  The owner will build it at M4.

## M2: Solver and proof verifier

### What was built (`engine/solver/`)
- `Solver`: depth-first search over the shared `Rules` code with in-place apply/undo.
  - **Transposition table**: 64-bit Zobrist hashes. Column hashes are *summed*, so column order doesn't matter.
    The stock and waste order is part of the key; the recycle count is included only for pass-limited rules.
  - **Stock macro-moves**: one candidate per waste card that drawing can bring to the top and that can then
    be played. The draws are expanded into primitive `Draw`/`Recycle` moves in the recorded solution.
    At most one recycle is simulated, because a second one only repeats positions.
  - **Safe auto-moves**: Aces and Twos, or a card whose opposite-colour foundations both hold rank-1, go up
    without branching.
  - **Ordering**: foundation moves, then moves that reveal a face-down card (more hidden cards first), then
    moves that empty a column, then waste plays, then useful partial runs, then foundation-to-tableau.
  - **Pruning**:
    - Kings go only into the first empty column, and a run already at the bottom of a column never moves
      to another empty column.
    - A partial run moves only if the card it uncovers can go to the foundation, or some card in the stock,
      the waste or a face-up position could then go on it.
    - A foundation card comes down only if it isn't "safe" (it would bounce straight back) and something
      could then go on it.
    - A position with no stock, no waste and no face-down cards is finished directly; it is always winnable.
  - **Restarts**: the first attempt gets 20k nodes, and each further attempt doubles that, with ties between
    equal-priority moves broken at random. Depth-first search is heavy-tailed, and this raised the solve rate
    a lot (draw-1 at 300k nodes: from 79.5% to 83.5%). `Unsolvable` is reported only when an attempt
    exhausts the search within its budget.
  - **Budgets**: a node limit, a time limit and a cancel callback. Outcomes are `Solved(moves)`,
    `Unsolvable` and `Unknown`.
- `ProofVerifier.verify(seed, ruleSet, moves)`: deal, then for each move check `Rules.isLegal`, play it with
  `Rules.apply`, and require `isWon`. Deliberately simple.

### Tests
- Small positions with known answers:
  - already won, and one card left;
  - needs the stock (draw-1 and draw-3);
  - needs a card back from the foundation (the solution must contain a `FoundationToTableau`);
  - a deadlock proven `Unsolvable` for both rule sets;
  - budget and cancel give `Unknown`.
- 60 seeds per rule set solved and verified in the normal test run. `SolverBenchmark` covers 1,000 seeds per
  rule set, asserting every `Solved` proof verifies.
- Corrupted proofs: removing *any single move* from 3 solutions per rule set makes the proof fail. So do
  changing moves, adding a move after the win, and using the wrong seed.

### Benchmark (1,000 seeds per rule set, 1M node budget, single thread, desktop JVM)
`./gradlew :engine:test -Pbench --tests '*SolverBenchmark' -PbenchSeeds=1000 -PbenchNodes=1000000`

| Rule set | Solved | Proved unsolvable | Unknown | Median | p95 | Median nodes | Median solution |
|---|---|---|---|---|---|---|---|
| DRAW_1_UNLIMITED | 84.3% | 2.8% | 12.9% | 1.7 ms | 5.1 s | 60 | 178 moves |
| DRAW_3_UNLIMITED | 77.1% | 12.4% | 10.5% | 3.9 ms | 4.7 s | 714 | 151 moves |

Every `Solved` proof verified.

- About 85% of deals are classified in well under a second. The p95 is the `Unknown` deals using their
  whole budget.
- Published estimates are about 82% of draw-3 deals winnable with full knowledge (Bjarnason et al.) and
  more for draw-1, so these rates look right.
- Many `Unknown` seeds are the same in both rule sets, which suggests most are unwinnable deals that are
  expensive to refute.
- `Unknown` and `Unsolvable` deals are never served, so a missed winnable deal only costs a seed.

### Known issues
- Solve speed is about 200k nodes/s. That's fine for offline pack generation. On-device solving was dropped
  with the simpler UI (no hints, no fresh deals), so it doesn't matter for the app.
