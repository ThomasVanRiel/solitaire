# Solitaire: Project Plan

An open-source Klondike patience (solitaire) app for Android where **every deal is winnable**.
A deal can be brutally hard, but the app never gives you a deal that can't be won.

This file is the spec and the work plan. An AI coding agent (Claude Code) is expected to carry it out
milestone by milestone, on the owner's PC, with as little supervision as possible.
Read `CLAUDE.md` for the working rules.

---

## 1. Decisions (defaults: change before kickoff if you want)

| Decision | Default | Notes |
|---|---|---|
| App name | **Solitaire** | Working title. |
| Application ID | `io.github.thomasvanriel.solitaire` | Owner's GitHub username, ThomasVanRiel. Changing it after release breaks updates. |
| License | **GPL-3.0-or-later** | Keeps forks open source, and F-Droid is happy with it. Use Apache-2.0 if you'd rather allow closed forks. |
| Min SDK | 26 (Android 8.0) | Covers nearly all active devices. |
| Target SDK | Whatever Google Play currently requires | The agent must check this when it starts, not guess. |
| Language / UI | Kotlin + Jetpack Compose (Material 3) | |
| Network | **None.** No `INTERNET` permission. | No ads, analytics, accounts or Google Play Services. |
| Card art | Drawn in code (Compose Canvas) | No third-party card images. Fonts must be OFL or system fonts. |

---

## 2. What "always winnable" means, exactly

1. **Winnable means winnable with perfect play, given full knowledge of the deal.** A solver that can see
   the face-down cards has found a complete winning sequence for every deal the app serves. The player
   can't see those cards, so a player who's playing well can still get stuck and need to undo.
   That's how every "winnable deals" mode works, and the app's About screen should say so in plain words.
2. **The solver and the game share one rules engine.** Any difference between "what the solver thinks is
   legal" and "what the UI allows" would quietly break the guarantee. So there is exactly one `Rules`
   implementation, and both use it.
3. **Every served deal comes with a proof.** A deal gets served only when it has a stored solution
   (a list of moves) that has been **replayed through the rules engine from the start position and reaches
   a won game.** The solver is allowed to be wrong in one direction only: it may reject deals that were
   actually winnable (false negatives are harmless; we just skip that seed). It can never get an
   unwinnable deal through, because replaying the proof would fail.
4. **Solvability depends on the rule set.** Draw-1 and draw-3, with or without a pass limit, are different
   problems. Each deal is verified for one specific `RuleSet`, and the deal stores which one.
5. **Undo is unlimited.** With unlimited undo, a winnable deal stays winnable whatever the player does.

---

## 3. Game rules (v1)

Standard Klondike:

- 7 tableau columns dealt 1..7, top card face-up. Stock holds the other 24 cards. 4 foundations.
- Tableau: build down in alternating colours. Any valid face-up run can be moved. Only a King (or a run
  starting with a King) can go into an empty column.
- Foundations: build up by suit from Ace to King. A card may come **back** from a foundation to the
  tableau (standard rule; the solver must include this move).
- When a face-down card becomes exposed, it flips automatically.
- Stock: draw 1 or draw 3 (setting). Recycling the waste back into the stock is **unlimited** by default.
  `RuleSet` also supports a pass limit, so a "Vegas-style" mode can be added later.
- RuleSets offered in v1: `DRAW_1_UNLIMITED` and `DRAW_3_UNLIMITED`.

Scoring: skip it in v1. Track moves and time only.

---

## 4. Architecture

```
winnable/
├── engine/          Pure Kotlin/JVM library. No Android dependencies.
│   ├── model/       Card, Pile, GameState, Move, RuleSet
│   ├── rules/       legal move generation, applying moves, undo, win detection
│   ├── shuffle/     deterministic, versioned RNG and deal construction
│   ├── solver/      the solver, the difficulty rater, proof replay
│   └── test/        JUnit tests, golden tests, fuzz tests, benchmarks
├── dealgen/         JVM command-line app: generates verified deal packs for the app's assets
├── app/             Android app (Compose), which depends on :engine
├── .github/workflows/ci.yml
├── fastlane/metadata/android/en-US/   F-Droid / store listing text
├── PLAN.md  CLAUDE.md  PROGRESS.md  README.md  LICENSE
```

Keeping the engine Android-free means the game logic and solver can be tested quickly on the desktop
JVM (`./gradlew :engine:test`) without an emulator.

### 4.1 Engine model (performance matters: the solver runs this millions of times)

- `Card` is an `Int` from 0 to 51 (`suit * 13 + rank`), with helper functions or a value class for readability.
- `GameState` is compact and cheap to copy: arrays of ints for the tableau (plus a face-down count per
  column), stock, waste and foundation tops. Provide both an immutable snapshot API for the UI and a
  fast mutable make/unmake-move path for the solver.
- `Move` is a sealed type: `Draw`, `Recycle`, `WasteToTableau(col)`, `WasteToFoundation`,
  `TableauToFoundation(col)`, `TableauToTableau(from, count, to)`, `FoundationToTableau(suit, col)`.
  Each one serialises to a compact code (a byte or two) for storage in packs and save files.
- `Rules.legalMoves(state, ruleSet)`, `Rules.apply(state, move)`, and `Rules.isWon(state)`.
- Undo works by keeping a move history (plus whatever is needed to reverse each move, such as whether a
  card was flipped).

### 4.2 Deterministic shuffle

- Implement the RNG yourself (for example SplitMix64 to seed xoshiro256\*\*) plus a Fisher–Yates shuffle,
  so seed → deal is stable forever on every device and JVM. Don't use `kotlin.random` or `java.util.Random`.
- Version it as `SHUFFLE_V1`. Add a **golden test** that pins the exact deal for a few seeds, so an
  accidental change fails CI. Deal packs record the shuffle version they were made with.
- Players can see and share a deal number such as "Deal #48213 · Draw 3".

### 4.3 Solver

Goal: decide quickly whether a deal can be won with full knowledge, and if it can, return the winning moves.

Recommended design (from the "thoughtful solitaire" literature; the agent may improve on it):

- **Depth-first search with a transposition table**, using 64-bit Zobrist hashes of a *canonical* state
  (the order of tableau columns doesn't matter for equality, so canonicalise it, for example by sorting
  column hashes).
- **Stock macro-moves:** instead of branching on each `Draw`, work out every waste card that can be
  reached by drawing (respecting draw-3 grouping and the pass limit, if any). Generate one move per
  reachable card, meaning "draw until X is on top, then play X". This cuts search depth a lot. Each
  macro-move expands into primitive moves when the solution is recorded.
- **Safe auto-moves without branching:** a card of rank *r* can go to its foundation with no loss if
  *r* ≤ 2, or if both opposite-colour foundations are already at *r*−1 or higher. Apply these
  automatically inside the search.
- **Move ordering:** foundation moves first, then moves that uncover a face-down card, then moves that
  empty a column, then the rest.
- **Pruning** (each rule must stay sound, or at worst cause false negatives):
  - Don't move a King-led run that already fills a column from the bottom into another empty column.
  - Don't make a tableau-to-tableau move that undoes the previous move.
  - Only move a partial run between columns when it unlocks something (a foundation move or a useful
    placement).
- **Budgets:** a node limit and a time limit. The outcomes are `Solved(moves)`, `Unsolvable` (the search
  space was exhausted) and `Unknown` (out of budget). Only `Solved` deals get served.
- **Proof replay:** `ProofVerifier.verify(seed, ruleSet, moves): Boolean` replays the moves with
  `Rules.apply`, checking each one is legal, and requires `isWon` at the end. This function is what the
  guarantee rests on, so keep it simple and test it hard.

Benchmarks: write a small benchmark over 1,000 seeds per rule set that reports the share solved, proved
unsolvable and unknown, plus median and 95th-percentile time. Rough expectation: about 80% or more of
random deals can be won with full knowledge. Aim to classify most deals in well under a second on a
desktop JVM. If the solve rate is far below that, suspect a bug in the rules or a pruning rule that's too
aggressive.

### 4.4 Difficulty rating ("can be very hard")

The solver tells us a deal **can** be won. Difficulty measures **how hard it is for a player who can't
see the hidden cards.**

- Build a **fair-information heuristic player**: it only looks at face-up cards and the face-up waste,
  uses sensible priorities (foundation moves, uncovering hidden cards, keeping columns available for
  Kings, and so on), and adds a little randomness to break ties.
- Run N playouts per deal (for example 30). `winRate` = the fraction of playouts the heuristic player wins.
- Also record solver effort (nodes searched) and solution length.
- Tiers (calibrate the cut-offs from the real distribution so each tier has plenty of deals):
  - **Easy:** the heuristic player usually wins.
  - **Medium:** it sometimes wins.
  - **Hard:** it rarely wins.
  - **Expert:** it never won in N playouts, but the solver proved the deal can be won. These are the
    "very hard but possible" deals.
- Show the tier on the deal. The player picks a tier when starting a new game, or picks "Any".

### 4.5 Deal supply

- `dealgen` (JVM command-line app) goes through seeds, solves and rates each one, verifies the proof, and
  writes **deal packs** to `app/src/main/assets/deals/`, one file per (RuleSet, tier). Each entry holds the
  seed, tier, the stats used for rating, and the compressed solution. The file starts with a format
  version and the shuffle version. Ship enough for years of play, for example 5,000 per tier per rule set,
  if the APK size stays reasonable (target: packs under about 5 MB total, gzipped).
- A CI test loads every shipped pack and replays every proof. If even one fails, the build fails.
- In the app, `DealRepository` serves unplayed deals from the packs for the chosen rule set and tier,
  and remembers which seeds have been used.
- If a pack runs out, or the player chooses "Fresh random deal", the app runs the solver **on the device**
  in a background coroutine. It keeps one or two verified deals ready ahead of time so starting a game
  never waits. It still replays the proof before serving.
- **Daily deal:** the date picks an entry from the packs deterministically, so everyone gets the same deal.

### 4.6 In-game solver features

- **Hint:** run the solver from the current position (in the background, with a time limit, cancelled if
  the player moves) and highlight the first move. If the solver runs out of time, fall back to the
  heuristic player's best move.
- **"Still winnable?" check** (a setting, off by default, because it uses hidden information): after
  each move, check in the background whether the position can still be won. If it can't, show a quiet
  banner: "This position can't be won any more. Undo to move 23?" Find the last winnable point by
  searching back through the move history (binary search, since winnability only gets lost, never
  regained, as you go forward).
- **Auto-complete:** when the stock and waste are empty and every card is face-up, offer to finish.

### 4.7 Android app

- **MVVM:** `GameViewModel` exposes a `StateFlow<GameUiState>`. The engine stays pure. The solver runs on
  `Dispatchers.Default`.
- **Board layout:** portrait first. The top row holds the stock, the waste (fanned three cards wide for
  draw-3) and the four foundations. Below are 7 columns whose fan spacing shrinks so a long column always
  fits the screen. Landscape and tablet layouts come in a later milestone. Optional left-handed mirroring.
- **Input:** drag and drop with snapping and highlighted legal targets, plus **tap to move** (sends the
  card to the best legal place: foundation first, then the most useful tableau column). Double-tap sends a
  card to its foundation.
- **Animations:** card moves, flips, the deal, and an original win animation. Optional haptics.
- **Controls:** New game (choose rule set and tier), Restart deal, Undo (unlimited), Hint, deal info.
- **Persistence:** save the current game as `(seed, ruleSet, list of moves, elapsed time)` and restore it
  by replaying, so it survives the app being killed. Store stats per rule set and tier: games played,
  won, current and best streak, best time, fewest moves. Use DataStore or Room.
- **Settings:** draw 1 or 3, auto-move to foundation (off / safe cards only / always), the "still
  winnable?" check, a 4-colour deck, left-handed mode, animation speed, haptics, theme (system, light,
  dark).
- **Accessibility:** touch targets of at least 48 dp where the layout allows. TalkBack descriptions such
  as "Seven of hearts, column 3", and a way to move cards with TalkBack (select a card, then select a
  target). Never rely on colour alone (the 4-colour deck helps). Support font scaling in menus.

---

## 5. Milestones

The agent works through these in order. Each milestone ends with: tests passing, the app building,
`PROGRESS.md` updated, and a git commit.

### M0: Environment and skeleton
- Check the toolchain: JDK 17 or newer, Android SDK, `adb`, an emulator or a connected phone. Write down
  what was found in `PROGRESS.md`. If something is missing, stop and tell the owner exactly what to
  install.
- Create the multi-module Gradle project (`:engine`, `:dealgen`, `:app`) with a version catalog, using
  the latest stable versions of AGP, Kotlin and the Compose BOM that work together.
- Make a "Hello" Compose screen that installs and launches on the emulator. Take a screenshot to confirm.
- Add a GitHub Actions CI workflow: engine tests, `assembleDebug`, lint. Add the LICENSE and a README stub.
- **Done when:** `./gradlew :engine:test assembleDebug` passes and the app launches on the device.

### M1: Rules engine
- Model, move generation, applying moves, undo, win detection, deterministic shuffle with golden tests.
- Tests: hand-built positions for every move type and edge case (draw-3 at the end of the stock,
  recycling, King to an empty column, moving cards back from foundations, auto-flip).
- **Fuzz test:** 10,000 random games made of random legal moves. After every move, check the
  invariants: exactly 52 unique cards, face-down cards only underneath face-up ones, every foundation in
  sequence. Undoing everything must return the start position exactly.
- **Done when:** all tests pass and coverage of `rules/` is high.

### M2: Solver and proof verifier
- Solver as described in 4.3, plus `ProofVerifier`.
- Tests: some small, hand-made, nearly finished positions with known answers. Every `Solved` result
  across 1,000 seeds per rule set must pass `ProofVerifier`. Feeding the verifier a corrupted proof
  (one move removed or changed) must make it fail.
- Write the benchmark, and record its results in `PROGRESS.md`.
- **Done when:** proofs always verify and the benchmark looks reasonable (see 4.3).

### M3: Difficulty and deal packs
- Heuristic player, difficulty rater, and the `dealgen` command-line app.
- Generate the packs, calibrate the tier cut-offs from the real distribution, and write the chosen
  cut-offs and the tier sizes into `PROGRESS.md`.
- Add the CI test that replays every shipped proof.
- **Done when:** the packs are in assets, every proof verifies, and the tiers are of sensible size.

### M4: Playable app
- Board UI, drag and tap input, undo, restart, new game from packs, win detection and win screen,
  saving and restoring the game.
- Check on the emulator: play a few deals by tapping (scripted through `adb shell input`, or by replaying
  a stored solution through a debug-only "autoplay proof" action), and take screenshots of the start
  position, the middle of a game, and the win screen.
- **Done when:** a pack deal can be played to a win on the device, and the game survives
  `adb shell am kill`.

### M5: Solver-powered features
- Hint, the "still winnable?" check with "undo to the last winnable position", auto-complete, fresh deals
  generated on the device with a deal kept ready ahead of time, the daily deal.
- **Done when:** hints come back within about a second on the emulator, and the UI never freezes while
  the solver runs (no ANRs; check with StrictMode in debug builds).

### M6: Polish
- Stats screen, settings, animations, haptics, theming, 4-colour deck, left-handed mode, landscape and
  tablet layouts, the accessibility work in 4.7, an app icon (original, drawn as a vector), and an About
  screen that explains the winnability guarantee and links to the source code.
- **Done when:** screenshots in light, dark and landscape look right, and a TalkBack pass makes sense.

### M7: Release preparation
- R8/minification configured for release builds, and a check that the release build runs.
- Full README (features, how the guarantee works, how to build, how to regenerate packs), CONTRIBUTING,
  and the fastlane metadata folder (short and full description, screenshots).
- Write down the steps for creating a release keystore and signing. **The agent never creates or stores
  signing keys itself.** That's for the owner.
- **Done when:** `./gradlew assembleRelease` works, apart from the signing, which the owner sets up.

---

## 6. Later (not in v1)
- More variants that share the engine: FreeCell (nearly every deal is winnable), Spider (1, 2 and 4
  suits), Yukon. Each one needs its own solver and proof checks.
- Vegas scoring with a pass limit (`RuleSet` already supports it).
- Translations.
- Exporting and importing stats.
