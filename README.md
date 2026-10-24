# Solitaire

An open-source Klondike patience (solitaire) game for Android where **every deal is proven winnable**.
Deals can be very hard, but each one comes with a stored winning sequence that has been replayed
through the game's own rules engine before the deal is served.

No ads, no analytics, no network access.

Status: early development. See `PLAN.md` for the plan and `PROGRESS.md` for where things stand.

## Building

Requirements: JDK 17 or newer, and the Android SDK (only for the app module).

```sh
./gradlew :engine:test          # engine tests, no device needed
./gradlew :app:assembleDebug    # build the debug APK
./gradlew :app:installDebug     # install on a connected device or emulator

# Generate the deal packs the app ships (solve, verify and rate deals):
./gradlew :dealgen:run --args="--per-tier 2500"
```

The packs currently shipped were made with `--per-tier 10` (10 deals per difficulty per draw mode).
`--per-tier 2500` takes about an hour on 7 threads and adds about 1.7 MB to the app. All options are
listed under [Deal packs](#deal-packs).

If no Android SDK is found (`ANDROID_HOME`, or `sdk.dir` in `local.properties`), the `:app` module is
skipped so that the engine and deal generator can still be built and tested.

## Deal packs

The app ships verified deals in `app/src/main/assets/deals/`, one gzipped file per rule set and difficulty
(`draw_1_unlimited_easy.wpak`, ...). They are made by the `dealgen` tool, which solves each seed, replays the
solution through the rules engine (`ProofVerifier`), rates the difficulty, and writes the packs. It then
reads every pack back and checks every proof again. `./gradlew :engine:test` also replays every shipped
proof, so a bad pack fails the build.

```sh
./gradlew :dealgen:run --args="--per-tier 2500"
```

| Option | Default | Meaning |
|---|---|---|
| `--rules draw1,draw3` | both | Rule sets to generate |
| `--per-tier N` | 2500 | Deals per difficulty tier per rule set |
| `--start-seed S` | 1 | First seed to try |
| `--threads T` | CPU count − 1 | Worker threads |
| `--playouts P` | 30 | Heuristic-player games per deal, for the difficulty rating |
| `--nodes N` | 300000 | Solver node budget per deal (deals that run out are skipped) |
| `--out DIR` | `app/src/main/assets/deals` | Output folder |
| `--calibrate N` | off | Only rate the first N winnable deals and print the win-rate histogram |

The output is deterministic for the same options.

## License

GPL-3.0-or-later. See `LICENSE`.
