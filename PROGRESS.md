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

## M3: Difficulty and deal packs

### What was built
- `engine/difficulty/HeuristicPlayer`: a fair-information player. It reads only face-up tableau cards,
  face-down *counts*, the waste top, the foundations and the stock size.
  - Priorities: foundation moves that reveal a card, then safe foundation moves, then moves revealing
    face-down cards (more hidden cards first), then other foundation moves, then partial runs that free a
    foundation card, then King to an empty column, then waste to tableau, then emptying a column when a
    King is waiting, then draw, then recycle.
  - Randomness: score noise between close options, and a random acceptable move 15% of the time.
  - It gives up after two full stock passes without playing anything.
  - A test shuffles all hidden cards (face-down cards and stock order) and checks the player's choices
    don't change.
- `engine/difficulty/DifficultyRater`: wins out of 30 playouts. The playouts are seeded from the deal seed,
  so ratings are deterministic.
- `engine/pack/SolutionCodec`: each move is stored as its index in `Rules.legalMoveCodes`, one byte per move.
  Decoding can only yield legal moves, and the result is still replayed by `ProofVerifier`.
- `engine/pack/DealPack`: gzipped binary. Header: magic `WPAK`, format version 1, shuffle version, rule set
  id, tier id, playouts, count. Entries: seed, wins, solver nodes, solution. `verifiedSolution(entry)`
  decodes and replays through `ProofVerifier`. The app calls this before serving any deal.
- `dealgen`: solves, verifies, rates and writes one pack per (rule set, tier), then reads every pack back
  and re-verifies every proof. Seeds are processed in ordered parallel batches, so the output is
  deterministic. `--calibrate N` prints the win histogram. The options are documented in the README.
- `ShippedPacksTest` (part of `:engine:test`, so it runs in CI) loads all 8 shipped packs and replays every
  proof. It also checks for duplicate seeds and the right rule set and tier.

### Calibration and tier cut-offs
- First calibration: the player was nearly deterministic, so the results were bimodal. Draw-1 deals were won
  0/30 times 39% of the time and 30/30 times 45%; Medium and Hard were almost empty. Adding the 15% random
  moves and wider noise spread them out.
- Second calibration, 1,000 winnable deals per rule set: draw-1 0/30 = 16%, draw-3 0/30 = 44%. The two
  distributions differ a lot, so **the cut-offs are per rule set** (wins out of 30):

| Rule set | Expert | Hard | Medium | Easy |
|---|---|---|---|---|
| Draw 1 | 0 (16%) | 1–6 (~17%) | 7–20 (~24%) | 21–30 (~43%) |
| Draw 3 | 0 (44%) | 1–3 (~23%) | 4–11 (~20%) | 12–30 (~13%) |

### Packs shipped
- **Owner's decision: only 10 deals per tier per rule set for the first run** (80 deals in total).
  `./gradlew :dealgen:run --args="--per-tier 10"` took about 10 s.
- Pack files are about 0.7–0.9 KiB each, about 85 bytes per deal gzipped. Average solutions: draw-1 178–208
  moves, draw-3 138–154 moves (harder tiers have longer solutions).
- At that rate, 2,500 per tier would be about 1.7 MB in total, well under the 5 MB target, and would take
  about an hour on 7 threads.
- With 10 deals per tier, `DealRepository` cycles back to the first deal after 10 games in a tier.

### Tests
43 engine tests, 0 failures, including the replay of every shipped proof.

## M4: Playable app

### What was built (`app/`)
- **One screen.** The top bar has **New game** and **Undo** (the owner asked for only these two), plus deal
  number, draw mode, tier, moves and time. New game opens a small dialog: draw 1 or 3, and a difficulty.
- **Board** (`ui/BoardLayout`, `ui/Board`, `ui/CardPainter`): one Compose Canvas.
  - Top row: stock, waste (fanned three wide for draw-3) and four foundations. Below: seven columns whose
    fan spacing shrinks so a long column fits.
  - Cards are drawn with shapes and system-font text; there are no images.
- **Input**:
  - Tap the stock to draw or recycle.
  - Tap a card or run to send it to the best place: foundation first, then non-empty columns, then an empty
    column for a King. A King already at the bottom of a column never moves to another empty column.
  - Drag and drop, with legal targets highlighted in yellow while dragging.
- **Auto-finish**: when the stock and waste are empty and every card is face up, the rest is played up
  automatically.
- **Win screen** with deal, moves and time, and a New game button.
- **`GameViewModel`**: `StateFlow<GameUiState>`. Moves go through the engine's `Game`/`Rules`, the same code
  the solver uses.
- **`DealRepository`**: serves pack deals in order per (rule set, tier). It **replays every proof with
  `ProofVerifier` right before serving**; a deal that fails is skipped.
- **`SavedGameStore`** (SharedPreferences): seed, rule set, tier, move list (2 bytes per move) and time,
  saved after every move. On launch the game is rebuilt by replaying the moves.
- **Debug-only tools** (`app/src/debug/.../DebugTools.kt`): StrictMode (log), an "Auto" button, and
  `adb shell am start -S -n io.github.thomasvanriel.solitaire/.MainActivity --ez autoplay true`. These
  replay the deal's stored solution in the real UI. The `release` source set has an empty `DebugTools`, so
  none of this is in release builds.
- A screen-reader description of the board (stock size, waste top, each column's top card and hidden count).

### Checked on the emulator (owner built with `:app:assembleDebug :app:lint`: success, first try)
- `screenshots/m4-start.png`: start position, deal #5 Draw 1 Medium.
- `screenshots/m4-middle.png`: autoplay in progress (99 moves).
- `screenshots/m4-win.png`: the stored proof played to a win in the real UI, 198 moves, win screen shown.
- `screenshots/m4-newgame-dialog.png`: the New game dialog.
- `screenshots/m4-before-kill.png` and `m4-after-kill.png`: deal #11 Draw 3 Expert after 4 moves, then
  HOME and `adb shell am kill`. The process was gone (cold start), and the identical position came back.
- `screenshots/m4-tap-drag.png`: tap sent A♦ to its foundation and the card under it flipped; dragging 6♥
  from the waste onto 7♣ worked.

### Known issues
- Suit symbols rendered as colour emoji (♥ ♦). Fixed in the source by appending U+FE0E (text
  presentation), but the APK hasn't been rebuilt yet.
- An autoplay intent sent while the app is already open is dropped by Android: use `am start -S`.
- The clock keeps counting while the autoplay runs; harmless.

## M5: Solver-powered features (cut down, agreed with the owner)
- **Dropped** because the owner wants only New game and Undo: hints, the "still winnable?" check,
  fresh on-device deals and the daily deal. There's no solver on the device at all, so the UI can't freeze
  on it.
- **Kept**: auto-complete (when everything is face up and the stock is empty, the game plays the cards up),
  and StrictMode in debug builds.
- StrictMode on the device found 3 main-thread disk reads at startup (about 40 ms each) from
  `getSharedPreferences` in the `DealRepository` and `SavedGameStore` constructors. **Fixed**: the
  preferences now open lazily, and the first access happens on `Dispatchers.IO`. Not yet re-checked on the
  device; that needs the next owner build.

## M6: Polish (cut down)
- Suit symbols use U+FE0E (text presentation), so ♥ ♦ no longer show as colour emoji.
- **Landscape and tablets**: card size is limited by height as well as width, and the board is centred.
  Checked in `screenshots/m6-landscape.png`; the game carried through the rotation.
- **Dark mode**: a darker felt and Material dark colours (`screenshots/m6-dark.png`, `m6-dark-dialog.png`).
- **App icon**: an original adaptive vector icon (card back behind a heart card) with a monochrome layer
  for themed icons.
- **About**: instead of a separate screen, the New game dialog explains the guarantee in plain words:
  every deal has a checked winning sequence, you can't see face-down cards, so you may get stuck and
  should use Undo.
- **Accessibility**: the board has a screen-reader description. Full TalkBack move-by-move control, stats,
  settings, animations, haptics, a 4-colour deck and left-handed mode were dropped to keep the app simple.

## M7: Release preparation
- Release build: R8 minification and resource shrinking, plus `proguard-rules.pro` (no extra rules needed).
  The release APK is 1.0 MB (the debug APK is 12 MB).
- Signing:
  - `keystore.properties` (git-ignored, created by the owner only) is used when present; otherwise the APK
    is unsigned.
  - `-PsignReleaseWithDebugKey` signs with the local debug key, for device testing only.
  - **The agent created no keys.** `RELEASING.md` gives the `keytool` steps for the owner.
- The owner built `:engine:test :app:assembleDebug :app:lint :app:assembleRelease -PsignReleaseWithDebugKey`:
  **success**.
- The release APK was installed on the emulator and runs (no crash after R8). The debug Auto button is
  absent, as intended (`screenshots/m7-release.png`).
- Docs: full README (features, how the guarantee works, layout, build, benchmark, deal packs with the
  dealgen command, debug tools, and screenshots in `docs/screenshots/`), `CONTRIBUTING.md`, `RELEASING.md`.
- Store listing: `fastlane/metadata/android/en-US/` holds the title, short and full description, and 4 phone
  screenshots.

### Known issues / for later
- The StrictMode fix still needs a device re-check after the next build.
- 100 deals per tier ship now (see below); regenerate with `--per-tier 2500` before a public release.
- The time on the win screen can read 0:00 after the debug autoplay. Normal play counts time correctly.
- Undo still works through the win overlay. Harmless, arguably useful.
- No GitHub remote yet. Add a source-code link to the store text once the repository is public.

## Tester feedback (2026-10-24)
- **Top row mirrored for ergonomics**: foundations in slots 1–4 on the left, the stock on the far right, and
  the waste just left of the stock. The top waste card is always next to the stock, and draw-3 fans the older
  cards out to the left.
- **Restart button**, asked for by a tester. It goes back to the start of the same deal and resets the clock,
  after a confirmation, because a restart can't be undone.
- The top bar is now two rows (New game · Restart · Undo, then deal info and moves/time), because three
  buttons and the info don't fit in one row on a phone.
- These are a small, explicit extension of the "only New game and Undo" scope.

## Statistics, clearer suits, packs, and a felt surprise (2026-10-24)
- **Packs**: the owner regenerated them with 100 deals per tier per rule set, 800 deals in all, about 7 KiB per
  pack. `ShippedPacksTest` replays every proof: pass.
- **Suits are now drawn as vector shapes** (`ui/SuitShapes.kt`) instead of font glyphs, because the owner
  found ♠ and ♣ too alike. The spade has a pointed top and a stem; the club has three round leaves. They're
  used for the corner symbol, the large centre symbol and the empty foundations. Red/black is unchanged;
  the owner chose this over a four-colour deck.
- **Statistics** (`game/StatsStore.kt`, `ui/StatsScreen.kt`), kept on the device in SharedPreferences.
  - Per draw mode and difficulty: played, won, win rate, fastest win, fewest moves, won without undo.
    Per draw mode: current and best streak.
  - A game counts as played from its first move. **New game** before winning counts as a loss and ends the
    streak. **Restart** stays the same game, but counts as using undo. Undoing after a win and winning again
    doesn't count twice. Games played by the debug autoplay never count.
  - The flags (counted, usedUndo, assisted, winRecorded) are saved with the game, so they survive the app
    being killed.
  - The **Stats** button is at the left of the second top-bar row. The screen has a Draw 1 / Draw 3 switch,
    a summary card, a per-difficulty table, and "Reset statistics" with a confirmation.
  - The win screen adds "Streak: N", and "New best time!" or "Fewest moves yet!" when a record falls (not on
    the first win in a tier).
- **Felt surprise** (`ui/FeltSurprise.kt`), at the owner's request: a tiny, very faint (10% opacity) brown
  three-tier swirl on the felt, drawn under the cards. It appears on about 1 deal in 25, decided by a
  SplitMix64 hash of the deal number, so it's always the same deals. It's placed in the empty bottom part of
  the board. Among the shipped deals it's on #100 (draw 1 expert), #135 (draw 1 easy), #152 (draw 3 expert),
  #190 and others.

## Spider and Scorpion II (2026-10-24, version 0.3.0)

Added at the owner's request: **Spider** (1, 2 and 4 suits) and **Scorpion II**. Klondike is unchanged:
same deals, same move codes, same packs (all 800 Klondike proofs still replay).

### Rules as implemented
- **Spider**: two decks (1 suit = eight sets of spades; 2 suits = spades and hearts; 4 suits = two full
  decks), 10 columns. 54 cards dealt round the columns (columns 1-4 get 6, the rest 5), top card face up;
  50 cards in the stock (five deals). Build down regardless of suit; only a same-suit run moves as a unit;
  any card or run may go into an empty column. A deal puts one card face up on every column and is only
  allowed when no column is empty.
- **Scorpion II** (rules checked against Goodsol's and TASVideos' descriptions): 7 columns of 7; columns
  1-3 have their lowest 3 cards face down, everything else face up. Build down in suit. Any face-up card
  moves together with every card on top of it. Only a King (with what's on it) may go into an empty column.
  The 3 reserve cards are dealt once, one onto each of columns 1-3 (also onto empty columns).
- **Both**: a complete King-to-Ace run of one suit leaves the board. This is a **forced move in the rules**
  (`TableauToFoundation(col)`): while one shows, it is the only legal move. So the solver, the stored proof,
  `ProofVerifier` and the UI all agree, and the UI simply plays it straight after the move that completed
  the run. In classic Scorpion completed runs stay in their columns; taking them off is how most apps do it
  and only makes play easier, never harder.
- Won when every card has left the board (for Klondike this is the same as all 52 on the foundations).

### Engine changes
- `RuleSet` gained `game` (`GameType`: KLONDIKE, SPIDER, SCORPION), `suits`, `columns`, `deckSize`, labels.
  New ids: 3 = Spider 1 suit, 4 = 2 suits, 5 = 4 suits, 6 = Scorpion II.
- `GameState` takes its column count and capacities from the rule set (`GameState.forRules`).
  `found[suit]` counts cards, so it goes in steps of 13 in Spider/Scorpion and up to 104 in 1-suit Spider.
- `Move` codes widened from 13 to 17 bits (column fields up to 15, run length up to 63) **without changing
  any Klondike code value**. Saved games now use 3 bytes per move under a new key (`moves3`); games saved by
  0.2 (2 bytes, key `moves`) still load.
- `Deals.deal(seed, ruleSet)`. The one-deck shuffle is identical, so Klondike deal numbers are unchanged
  (golden tests still pass); Spider shuffles its 104 cards with the same Fisher–Yates and RNG.
- `SolutionCodec`: an index of 255 or more is stored as 255 plus two bytes. Spider can have more than 255
  legal moves (many empty columns and long runs). No Klondike index ever reached 255, so old packs read the
  same; `ShippedPacksTest` proves it by replaying them.
- `StateValidator` checks multisets (Spider has duplicate cards) and only checks run order in Klondike.

### Solver (`RunGameSolver`, chosen by `DealSolver.create`)
Same design as the Klondike solver: DFS, Zobrist transposition table, restarts with random tie-breaks.
- Complete runs come off straight away (forced). Ordering: build in suit, reveal a face-down card, empty a
  column, King to an empty column (Scorpion), other moves, deal last.
- Pruning (can lose solutions, never invents them): never split a same-suit run (Spider), never move a run
  from one off-suit card to another of the same rank, only uncover a face-up card if some run head could then
  go on it, never move a whole column into an empty one, and once the stock is empty only use the first
  empty column. Columns are hashed in order while the stock has cards (a deal treats columns differently),
  and as a sum after that.

Benchmark, 100 seeds each, 300k node budget, single thread:

| Rule set | Solved | Proved unsolvable | Unknown | Median | Median solution |
|---|---|---|---|---|---|
| Spider 1 suit | 99% | 0% | 1% | 0.5 ms | 119 moves |
| Spider 2 suits | 94% | 0% | 6% | 11 ms | 159 moves |
| Spider 4 suits | 11% | 0% | 89% | 563 ms | 326 moves |
| Scorpion II | 24% | 43% | 33% | 318 ms | 55 moves |

At 3M nodes, 4-suit Spider reaches 30% (30 seeds). Only solved deals are ever served, so a weak solve rate
only costs generation time; it also means the 4-suit packs lean towards deals the solver finds easier.

### Difficulty
- `RunGamePlayer` is the fair-information player for both games. It never looks at face-down cards or the
  stock, and never returns to a position it has seen in the same game. A test shuffles all hidden cards and
  checks its choices don't change.
  - Scorpion: simple move priorities (reveal, empty a column, build, King to an empty column, deal).
  - Spider: one-move look-ahead on a visible score (face-down cards, empty columns, cards on the next rank,
    much more in suit). The card a move would turn over is ignored when scoring, so it can't peek. It deals
    only when nothing improves the position.
- **Spider is rated by progress, not wins.** Even this player almost never wins 2- or 4-suit Spider
  (100% of calibration deals had 0 wins out of 30), so counting wins can't separate the tiers. A Spider
  rating is the average over 30 games of: 1 for a win, otherwise 0.8 × (half the share of face-down cards
  turned over + half the share of cards taken off), in percent. For Spider the pack's `wins` byte holds
  this percentage. Klondike and Scorpion keep plain wins out of 30.
- Cut-offs, at roughly the quartiles of 300–400 winnable deals each:

| Rule set | Expert | Hard | Medium | Easy |
|---|---|---|---|---|
| Scorpion II (wins of 30) | 0 (9%) | 1–5 (23%) | 6–16 (33%) | 17–30 (35%) |
| Spider 1 suit (rating %) | ≤33 (24%) | 34–44 (25%) | 45–51 (25%) | ≥52 (27%) |
| Spider 2 suits (rating %) | ≤14 (25%) | 15–17 (24%) | 18–20 (26%) | ≥21 (25%) |
| Spider 4 suits (rating %) | ≤11 (28%) | 12–13 (23%) | 14–15 (27%) | ≥16 (21%) |

### App
- New game dialog: Game (Klondike / Spider / Scorpion II), then Draw or Suits, then difficulty, with a
  one-line rules summary per game. The Stats screen uses the same picker; stats are kept per variant.
- Board: 10 narrower columns for Spider. The stock sits far right as before; Spider shows one face-down card
  per deal left, fanned left. Completed runs show as their Kings on the left (fanned in Spider, one slot per
  suit in Scorpion). Empty Spider columns have no "K" label.
- Tap a card in Spider/Scorpion to move it and everything on it: onto a same-suit card first, then any
  other card, then an empty column. Drag works as before.
- Completed runs are taken off right after the move that completes them; **Undo** undoes both together.
- Tapping the Spider stock with an empty column shows "Fill every empty column before dealing".
- Version 0.3.0 (code 3). Store texts updated.

### Packs and tests
`./gradlew :dealgen:run --args="--rules spider1,spider2,spider4,scorpion2 --per-tier 100"` (8 threads,
log in `logs/dealgen-spider-scorpion.txt`). The Klondike packs were not touched.

| Rule set | Seeds tried | Time | Avg solution | Pack size (each tier) |
|---|---|---|---|---|
| Spider 1 suit | 425 | 2 s | 120 moves | 8–9 KiB |
| Spider 2 suits | 457 | 8 s | 163 moves | 12 KiB |
| Spider 4 suits | 3,084 | 323 s | 339 moves | 23–25 KiB |
| Scorpion II | 5,407 | 467 s | 55 moves | 2 KiB |

All 1,600 new proofs were verified when written and again on read-back. `:engine:test`: 61 tests, 0 failures,
including `ShippedPacksTest` replaying all 2,400 shipped proofs and the new `RunGamesTest` (deal layouts,
every rule, forced run removal, apply/undo, a 2,000-game fuzz over the four new rule sets, wide move codes,
the codec escape, solver proofs, corrupted proofs, and the fairness of the new player).

### Checked on the emulator
The owner built `:engine:test :app:assembleDebug :app:lint`: success on the first try, and lint reports
nothing new (`logs/spider-build.log`).
- `screenshots/s1-newgame*.png`: the dialog with the game and suits pickers and the rules summaries.
- `s1-spider-start.png`, `s1-spider-tap-deal.png`: the Spider 2-suit layout. Tapping 7♠ put it on 8♠ and
  turned over the card underneath, and the deal put one card on each column (4 deals left in the fan).
- `s1-spider-mid.png`, `s1-spider-win.png`: the stored proof for #1 played in the real UI to a win in 192
  moves. `s1-spider-undo.png`: Undo after the win went to 190 moves, taking back the run removal and the move
  that completed it together. Tapping 10♠ then completed the run by hand; it came off and the game was won
  (`s1-spider-manual-win.png`).
- `s1-scorpion-*.png`: the Scorpion II layout (3 face-down cards in columns 1-3, stock of 3). Tapping 4♠
  carried 3♠ onto 5♠. The proof for #6 played to a win in 54 moves, with the Kings in their suit slots.
- `s1-spider4-*.png`: a 4-suit Expert proof (#71, 418 moves) played to a win; long columns fit.
- `s1-stats.png`: the Stats screen with the new picker, version 0.3.0.
- `s1-resume-klondike.png`: the saved Klondike game from 0.2.0 came back after the update.
- No app errors in logcat. The owner saw "Draw context is NULL" in the emulator's console; that comes from
  the emulator's host renderer when windows are torn down (force-restarts and screenshots), not from the app.

### Known issues
- 4-suit Spider deals are limited to the ones the solver can crack within 300k nodes (about 15%).
- The Spider difficulty player is weak (it rarely wins 2- or 4-suit games), so the Spider tiers rest on how
  far it gets rather than on wins. A stronger fair player would make the tiers more meaningful.
