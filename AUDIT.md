# Application audit report

**Application:** Solitaire  
**Audit date:** 2026-10-25  
**Audited revision:** `003589d`  
**Scope:** Architecture, correctness, privacy, accessibility, build configuration, CI, persistence, and the winnable-deal guarantee.

## Executive summary

The application has a sound core design. Game legality is centralized in one rules engine, stored solutions
are replayed before deals are served, and the test suite replays every solution shipped with the app. No
critical security defect or violation of the winnable-deal guarantee was found.

The audit found six issues:

| Severity | Count |
|---|---:|
| Critical | 0 |
| High | 1 |
| Medium | 3 |
| Low | 2 |

The most significant problem is accessibility: the canvas-based board can be described by a screen reader,
but its individual cards and actions cannot be focused or activated. The main correctness risks concern
statistics reset, undo after a recorded win, and overlapping asynchronous deal requests.

## Findings

### A-01: The board is not operable with a screen reader

**Severity:** High  
**Area:** Accessibility  
**Location:** `app/src/main/kotlin/io/github/thomasvanriel/solitaire/ui/Board.kt:113`

The entire playing surface is drawn in one Compose `Canvas`. It exposes one `contentDescription`, which
provides a useful summary of the position, but all gameplay actions are implemented as coordinate-based tap
and drag gestures. The stock, waste, foundations, columns, cards, and movable runs are not separate semantic
nodes and do not expose accessibility actions.

As a result, a TalkBack user can hear the board summary but cannot navigate to or activate the individual
game elements. For a user who depends on a screen reader, the main function of the app is unavailable.

**Recommendation:** Add semantic elements or custom accessibility actions for the stock, waste, foundations,
columns, and legal movable runs. Each action should identify the card or run, describe its location, and allow
the same operations as tap and drag. Add an instrumentation test that traverses the semantics tree and plays
at least one move without coordinate input.

### A-02: Resetting statistics during an active game can create impossible totals

**Severity:** Medium  
**Area:** Correctness / persistence  
**Locations:**

- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/GameViewModel.kt:575`
- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/GameViewModel.kt:602`

`resetStats()` clears the statistics and extras stores, but it does not reconcile the active game's
`counted`, `winRecorded`, or `hintsUsed` fields. Those fields determine which statistics are recorded after
subsequent moves.

One reproducible sequence is:

1. Start a game and make one move. The game becomes `counted`.
2. Open Statistics and reset all statistics.
3. Return to the active game and win it.

The win is recorded, but the game is not recorded as played because its in-memory `counted` flag remains
true. This can produce `won = 1` and `played = 0`, causing an invalid win rate. If a hint was used before the
reset, the result can also contain a hinted win while the total hint count is zero.

**Recommendation:** Define how an active game should behave after reset and update its flags atomically. A
simple policy is to treat an unfinished game as new for statistics by clearing `counted`, `winRecorded`, and
`hintsUsed`, then immediately save the revised state. Alternatively, finish or replace the active game as
part of the reset operation. Add tests for reset-before-win, reset-after-hint, and reset-after-win.

### A-03: Undoing a recorded win can later turn that game into a loss

**Severity:** Medium  
**Area:** Correctness / statistics  
**Locations:**

- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/GameViewModel.kt:263`
- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/GameViewModel.kt:366`

`undo()` permits undo after a win and preserves `winRecorded = true`. Once the final move has been undone,
the board is no longer won. If the player then starts another game, `abandonCurrent()` sees a counted,
unwon, unassisted game and records a loss, even though that game has already been recorded as a win. The
extra loss can incorrectly end a streak. Winning again after the undo does not record another win because
`winRecorded` is still true.

**Recommendation:** Prefer disabling undo after a recorded win. If post-win undo remains a feature,
`abandonCurrent()` should not record a loss when `winRecorded` is true, and the intended behavior for a
second completion should be made explicit and tested.

### A-04: Overlapping deal requests can complete out of order

**Severity:** Medium  
**Area:** Concurrency / correctness  
**Locations:**

- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/GameViewModel.kt:230`
- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/GameViewModel.kt:244`
- `app/src/main/kotlin/io/github/thomasvanriel/solitaire/game/DealRepository.kt:35`

Each request to start a random deal or resolve a deal code launches a new coroutine. There is no request
generation, cancellation of an earlier load, or final check that the result still belongs to the newest
request. A slow request can therefore replace a newer game when it completes. Repeated requests can also
run abandonment logic more than once for the same current game.

In addition, `DealRepository.next()` reads and updates its SharedPreferences index without synchronization.
Concurrent calls for the same pack can read the same index and serve the same deal, with the later write
overwriting the other request's progress.

**Recommendation:** Keep a single cancellable deal-loading job or assign monotonically increasing request
IDs and discard stale results. Disable New Game while a request is loading. Serialize the complete
read-verify-update operation in `DealRepository.next()`. Add coroutine tests in which requests deliberately
finish in the opposite order.

### A-05: Platform backup conflicts with the documented on-device privacy claim

**Severity:** Low  
**Area:** Privacy / documentation  
**Locations:**

- `app/src/main/AndroidManifest.xml:6`
- `README.md:69`

The README says that statistics stay on the phone, while the manifest sets `android:allowBackup="true"`.
Depending on Android version, device settings, and manufacturer behavior, SharedPreferences may be copied
through platform cloud backup or device-to-device transfer. This does not mean the application itself uses
the network, and no `INTERNET` permission is present, but the privacy wording is broader than the actual
behavior.

**Recommendation:** Either disable backup or add explicit backup/data-extraction rules. If backup is
intentional, document that saved games and statistics may participate in Android's system backup and device
transfer facilities.

### A-06: CI does not exercise all tests or the release build

**Severity:** Low  
**Area:** Build and release assurance  
**Location:** `.github/workflows/ci.yml:23`

CI runs engine tests, assembles the debug app, and runs Android lint. It does not run `:extras:test` and does
not assemble the minified release variant. There are also no app-module unit or instrumentation tests for
the persistence, statistics, lifecycle, accessibility, or concurrency behavior identified above.

Consequently, changes can break the extras module's behavior or introduce release-only R8 problems without
failing CI.

**Recommendation:** Run at least:

```sh
./gradlew :engine:test :extras:test :app:assembleDebug :app:lint :app:assembleRelease
```

Use an unsigned release build in CI. Add focused app tests for statistics transitions, saved-game restore,
overlapping deal requests, and the Compose semantics tree.

## Positive observations

- The manifest declares no `INTERNET` permission.
- No analytics, advertising, account system, or Google Play Services dependency was found.
- Release and debug tooling are separated into source sets; release `DebugTools` is inert.
- Signing configuration is externalized to an ignored `keystore.properties` file.
- The rules engine is shared by gameplay, solvers, proof verification, and difficulty players.
- `DealRepository` verifies a stored solution before serving its deal.
- `ShippedPacksTest` checks pack metadata, rejects empty packs, detects duplicate seeds, and replays every
  shipped proof.
- The shuffle implementation is versioned and protected by golden tests.
- Saved games are reconstructed by replaying their moves, which prevents arbitrary persisted state from
  bypassing the rules engine.
- Android lint is configured to abort on errors.
- GitHub Actions uses read-only repository permissions.

## Verification performed

The following commands were executed for this audit:

```sh
./gradlew :engine:test :extras:test :dealgen:compileKotlin --no-daemon --rerun-tasks
```

Results:

- Engine: 72 tests, 0 failures, 0 errors, 0 skipped.
- Extras: 13 tests, 0 failures, 0 errors, 0 skipped.
- Deal generator: compiled successfully.
- Every shipped deal solution was replayed successfully by the engine tests.
- Total command result: `BUILD SUCCESSFUL`.

The Android SDK was not available inside the audit environment, so the Android tasks could not be
independently rerun there. The workspace's latest host-side build log records a successful debug build and
lint run. The current lint report contains 0 errors, 16 warnings, and 3 hints. Its warnings are primarily KTX
style suggestions, plus an old-target-API warning; no lint security error was reported.

## Test and assurance gaps

This was a source and build audit, not a full device compatibility or penetration test. The following were
not independently verified:

- TalkBack behavior on a physical device or emulator.
- Rotation, process-death, and low-memory behavior under Android instrumentation.
- Performance across the full supported Android 8.0+ device range.
- Dependency vulnerability status against a current external advisory database.
- Reproducibility and signature verification of the published GitHub APK.
- Release behavior after R8 optimization on a device.

## Recommended remediation order

1. Make the board operable through accessibility semantics.
2. Correct statistics reset and post-win undo accounting, with regression tests.
3. Serialize/cancel overlapping deal requests and protect the pack index update.
4. Add app-level tests and expand CI to extras and release builds.
5. Decide and document the intended Android backup policy.

## Conclusion

The core deal-integrity claim is well supported by the architecture and tests reviewed. The application is
appropriately small, avoids unnecessary permissions and services, and has a strong separation between its
pure Kotlin engine and Android UI. The identified issues do not undermine proof verification, but the
accessibility and statistics defects should be resolved before treating the app as production-ready.
