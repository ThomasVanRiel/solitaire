# Solitaire

> [!WARNING]
> **Disclaimer: this app is FULLY vibe coded, for personal use only.** All of the code was written by an AI
> coding agent (Claude Code), directed by the owner but not written or reviewed line by line by a person.
> It comes with no warranty and no support, and it is not intended for distribution or production use.
> Use it at your own risk.

An open-source patience (solitaire) game for Android, with Klondike, Spider and Scorpion II, where
**every deal is proven winnable**.
Deals can be very hard, but each one comes with a stored winning sequence that has been replayed through
the game's own rules engine before it is dealt to you.

No ads, no analytics, no accounts, and no network access (the app has no `INTERNET` permission).

## Screenshots

| Klondike | Spider (2 suits) | Scorpion II |
|:---:|:---:|:---:|
| <img src="docs/screenshots/klondike.png" width="240" alt="Klondike draw 1, mid-game"> | <img src="docs/screenshots/spider.png" width="240" alt="Spider with two suits, two runs completed; small gaps mark where same-suit sequences break"> | <img src="docs/screenshots/scorpion.png" width="240" alt="Scorpion II mid-game, with long same-suit runs"> |

| Won | New game (dark mode) | Share a deal |
|:---:|:---:|:---:|
| <img src="docs/screenshots/win.png" width="240" alt="The win screen with Share deal and New game"> | <img src="docs/screenshots/new-game-dark.png" width="240" alt="The New game dialog in dark mode: game, suits, difficulty and a deal code field"> | <img src="docs/screenshots/share.png" width="240" alt="The share dialog showing deal code SC-6"> |

<img src="docs/screenshots/landscape.png" width="740" alt="Klondike in landscape: foundations on the left, the stock and waste on the right">

## Features

**Games**
- **Klondike** with draw 1 or draw 3, unlimited passes through the stock. When every card is face up, the
  game finishes itself.
- **Spider** with 1, 2 or 4 suits (two decks, ten columns). Only runs of one suit move together; a
  complete King-to-Ace run of one suit is taken off automatically. A deal needs every column filled.
- **Scorpion II**: one deck, seven columns, build down in suit. Any face-up card moves together with
  everything on top of it, only Kings fill empty columns, and the three reserve cards are dealt once.
  Complete runs are taken off automatically.
- Four difficulty levels per game: Easy, Medium, Hard and Expert.

**Playing**
- Tap a card to send it to the best place, or drag it; legal targets light up while you drag. Cards slide
  to their new place.
- Unlimited undo, and a Restart button to replay the same deal from the start.
- In Spider and Scorpion II there is a little extra space wherever a same-suit sequence breaks, so valid
  sequences sit together.
- The stock sits on the right, under your thumb; the foundations are on the left. In landscape they move
  beside the columns: foundations down the left edge, the stock down the right.
- Your game is saved after every move, and survives the app being closed or killed.

**Sharing and statistics**
- **Deal codes**: tap the deal info at the top (or *Share deal* on the win screen) to see a short code such
  as `S2-17`, and copy it or send it with the share sheet. Type a code in *New game* to play that exact
  deal. Codes are a two-letter variant (`K1`, `K3`, `S1`, `S2`, `S4`, `SC`) and the deal number; only deals
  in the app's packs can be played. Sharing uses Android's share sheet, so the app still needs no network
  access.
- Statistics per game variant and difficulty: games played and won, win rate, streaks, fastest win, fewest
  moves, and wins without undo. They stay on your phone.

Cards are drawn in code: no image assets.

## How the guarantee works

1. **One rules engine.** The game and the solver share exactly one implementation of the rules
   (`engine/.../rules/Rules.kt`), so "legal for the solver" and "legal in the game" can't differ.
2. **A solver finds a win.** A depth-first solver that can see every card searches for a winning move
   sequence for each candidate deal. Deals it can't solve within its budget are skipped.
3. **Every win is replayed.** `ProofVerifier` deals the cards again from the seed, checks every stored
   move with the rules engine, plays it, and requires a won game at the end. This happens when the deal
   packs are made, again in the test suite for every shipped deal, and once more in the app right before a
   deal is served. A deal whose proof doesn't replay is never shown.
4. **Undo keeps it winnable.** With unlimited undo you can always get back to a position that can be won.

"Winnable" means winnable with perfect play and knowledge of the face-down cards. You can't see those, so
you can still get stuck and need to undo. That's how every "winnable deals" mode works.

**Difficulty** is measured for a player who *can't* see the hidden cards: a simple computer player that
only looks at face-up cards plays each deal 30 times, and the share of games it wins sets the tier. In
Klondike and Scorpion II, Expert deals were never won by it, yet the solver proved them winnable. In Spider
that simple player hardly ever wins, so a lost game also scores partial credit for how far it got (cards
turned over, runs completed), and the tiers are set on that score.

Deals are numbered by seed (shown as `#1234`). The shuffle algorithm is versioned and pinned by tests, so a
deal number always gives the same deal.

## Project layout

| Module | What it is |
|---|---|
| `engine/` | Pure Kotlin/JVM: cards, rules for all three games, shuffle, solvers, proof verifier, difficulty rater, deal pack format. No Android dependencies. |
| `dealgen/` | Command-line tool that generates the verified deal packs. |
| `app/` | The Android app (Jetpack Compose). |

## Building

Requirements: JDK 17 or newer, and the Android SDK (only for the app module).

```sh
./gradlew :engine:test          # engine tests, no device needed
./gradlew :app:assembleDebug    # build the debug APK
./gradlew :app:installDebug     # install on a connected device or emulator
./gradlew :app:lint             # Android lint

# Generate the deal packs the app ships (solve, verify and rate deals):
./gradlew :dealgen:run --args="--per-tier 2500"
```

The packs currently shipped hold 100 deals per difficulty per game variant (`--per-tier 100`).
`--per-tier 2500` would take several hours on 8 threads (4-suit Spider and Scorpion II are the slow ones,
because few of their deals are solved) and the packs would grow to about 6.4 MB in total (see "Open
decisions" in `PROGRESS.md`). All options are listed under [Deal packs](#deal-packs).

If no Android SDK is found (`ANDROID_HOME`, or `sdk.dir` in `local.properties`), the `:app` module is
skipped so that the engine and deal generator can still be built and tested.

Release builds (`./gradlew :app:assembleRelease`) are minified with R8. See `RELEASING.md` for signing.

### Benchmark

```sh
./gradlew :engine:test -Pbench --tests '*SolverBenchmark' -PbenchSeeds=1000 -PbenchNodes=1000000
# Other rule sets (names as in RuleSet.kt):
./gradlew :engine:test -Pbench --tests '*SolverBenchmark' -PbenchRules=SPIDER_2_SUITS,SCORPION_II
```

On a desktop JVM, about 84% of draw-1 deals and 77% of draw-3 deals are solved, with a median of a few
milliseconds. Spider 1 and 2 suits solve almost always; 4-suit Spider and Scorpion II much less often (only
solved deals are shipped). The full numbers are in `PROGRESS.md`.

## Deal packs

The app ships verified deals in `app/src/main/assets/deals/`, one gzipped file per rule set and difficulty
(`draw_1_unlimited_easy.wpak`, `spider_2_suits_hard.wpak`, `scorpion_ii_expert.wpak`, ...). The `dealgen`
tool makes them:
- it solves each seed and replays the solution through the rules engine (`ProofVerifier`);
- it rates the difficulty and writes the packs;
- it reads every pack back and checks every proof again.

`./gradlew :engine:test` also replays every shipped proof, so a bad pack fails the build.

```sh
./gradlew :dealgen:run --args="--per-tier 2500"
```

| Option | Default | Meaning |
|---|---|---|
| `--rules draw1,draw3,...` | all | Rule sets to generate: `draw1`, `draw3`, `spider1`, `spider2`, `spider4`, `scorpion2` |
| `--per-tier N` | 2500 | Deals per difficulty tier per rule set |
| `--start-seed S` | 1 | First seed to try |
| `--threads T` | CPU count − 1 | Worker threads |
| `--playouts P` | 30 | Heuristic-player games per deal, for the difficulty rating |
| `--nodes N` | 300000 | Solver node budget per deal (deals that run out are skipped) |
| `--out DIR` | `app/src/main/assets/deals` | Output folder |
| `--calibrate N` | off | Only rate the first N winnable deals and print the rating histogram |

The output is deterministic for the same options. Each solution is stored as one byte per move: the move's
index in the rules engine's legal-move list (an index of 255 or more, which only Spider can reach, takes
three bytes). After gzip that comes to about 85 bytes per Klondike deal, 20 for Scorpion II, and 90–250 for
Spider (longer solutions).

## Debug tools

Debug builds have an **Auto** button that restarts the deal and plays its stored winning sequence in the
real UI. It can also be started from adb:

```sh
adb shell am start -S -n io.github.thomasvanriel.solitaire/.MainActivity --ez autoplay true
```

For screenshots, `--ez hideDebugUi true` hides the Auto button and `--ei stopAt N` stops the replay after
N moves, so a mid-game position can be captured:

```sh
adb shell am start -S -n io.github.thomasvanriel.solitaire/.MainActivity \
    --ez autoplay true --ei stopAt 60 --ez hideDebugUi true
```

These tools live in the `debug` source set and are not in release builds.

## Contributing

See `CONTRIBUTING.md`.

## License

GPL-3.0-or-later. See `LICENSE`.
