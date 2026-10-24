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
- Application ID: `io.github.thomasvanriel.winnable`. The owner's GitHub username is ThomasVanRiel,
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
