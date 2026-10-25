# Working rules for the coding agent

**Solitaire** is an open-source Android patience app (Klondike, Spider, Scorpion II) where every deal is
proven winnable. `README.md` covers features and building; `docs/DESIGN.md` covers how the engine, solver,
difficulty rating, deal packs and app work.

## How to work
- After a change, run `./gradlew :engine:test :extras:test :app:assembleDebug :app:lint`, keep
  `docs/DESIGN.md` and the README in step with the code, then make a git commit with a clear message.
- Keep going on your own. Only stop and ask the owner when a tool or SDK is missing (say exactly what to
  install), or when something stays blocked after several real attempts to fix it.

## The one rule that must never break
No deal reaches the player unless its stored solution has been replayed through the rules engine
(`ProofVerifier`) and reaches a won game. The solver and the game must use the same `Rules` code.
If you're tempted to weaken this to make something faster or simpler, don't.

## Hard constraints
- No `INTERNET` permission, no analytics, no ads, no Google Play Services, no closed-source dependencies.
- No third-party card images. Draw the cards in code. Fonts must be OFL-licensed or system fonts.
- Use only versions of dependencies that are open source and stable.
- Keep the UI simple.
- Don't change the shuffle (`SHUFFLE_V1`); deal numbers and packs depend on it.
- Stay inside this project folder. Don't change files elsewhere on the computer, apart from installing
  build tools when the owner has agreed to it.
- Never create, commit or print signing keys or passwords.
- Don't push to a remote or publish anything unless the owner asks you to.

## Build environment
- The agent may run in a jail without the Android SDK. Then it builds and tests `:engine`, `:extras` and
  `:dealgen` itself, and the owner runs the `:app` builds on the host (output in `logs/`).
  `settings.gradle.kts` skips `:app` when no SDK is found.
- The host's adb server (127.0.0.1:5037) is reachable, so the agent can install APKs and take screenshots.
- JDK: use `JAVA_HOME=/opt/android-studio/jbr` (`/usr/bin/java` has no `javac`).

## Build loop with the owner (no SDK in the jail)
1. Write the app code carefully: you can't compile it. Put logic that can live without Android in a JVM
   module (`:engine`, `:extras`) and test it there first.
2. Ask the owner to build, giving this exact command:
   `JAVA_HOME=/opt/android-studio/jbr ./gradlew :engine:test :app:assembleDebug :app:lint > logs/build.log 2>&1`
   (Without `JAVA_HOME` Gradle picks a JDK with no `javac` and fails before compiling.)
3. Read the result: `grep -E "^e:|What went wrong|BUILD" logs/build.log` and the last line of
   `app/build/reports/lint-results-debug.txt` (0 errors needed; the UseKtx/AutoboxingStateCreation warnings
   are known and fine). The APK is `app/build/outputs/apk/debug/app-debug.apk`.
4. Install and check on the device, then commit. Batch several fixes per build round; each one costs the
   owner a step.

## Checking your work on the device
- Use an emulator or a connected phone through `adb`.
- Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, then confirm with
  `adb shell dumpsys package io.github.thomasvanriel.solitaire | grep versionName`. The emulator can come
  back from a crash or a snapshot with an older version installed: always check before trusting a screen.
- If the emulator dies with gfxstream/GL errors ("null ctx", segfault), ask the owner to restart it with
  `emulator -avd Medium_Phone_API_37.0 -gpu swiftshader_indirect -no-snapshot-load`. Software rendering is
  slow: allow time, and expect doubled keystrokes from `adb shell input text` (check the field, retry).
- Start the app with `adb shell am force-stop <pkg>` and then `am start -n <pkg>/.MainActivity ...`; with
  `am start -S` alone the extras are sometimes delivered to the running activity and ignored.
- To check a screen, take a screenshot with `adb exec-out screencap -p > screenshots/<name>.png` and
  look at the image yourself. `screenshots/` is git-ignored scratch space.
- To find and tap things, use `adb shell uiautomator dump` and tap the centre of a node's bounds (see
  `tools/screenshot.sh`) rather than guessing coordinates. The top-bar icon buttons have content
  descriptions (Undo, Hint, Statistics). Plain `sleep` is blocked in the agent shell; poll a dump instead.
- To check gameplay, use the debug-only autoplay, which replays a deal's stored solution in the real UI
  (see "Debug tools" in the README). It must not be present in release builds. Autoplayed games never count
  in the statistics, so they can't earn achievements.
- Debug intent extras: `--ez autoplay true`, `--ei stopAt N`, `--el stepMs 20`, `--ez hideDebugUi true`,
  `--ez scorecards true` (always show the rare scorecards), `--es achievement <id>` (pop one up).
  Short-lived things (scorecards ~2 s, pop-ups ~3 s, quips ~4.5 s) need a screenshot taken straight away.
- Dark mode: `adb shell cmd uimode night yes|no`. Landscape: `adb shell settings put system
  accelerometer_rotation 0` then `user_rotation 1` (back with `0`). Put both back when done.

## README screenshots
Retake them with `tools/screenshot.sh` after visible UI changes, and copy them to `docs/screenshots/`.
No easter eggs in them (no scorecards, pop-ups or felt surprise; the deals below have none). Fortune lines
and greetings are normal features and may show.
- `klondike.png`: K1-6 at move 70. `spider.png`: S2-1 at 140. `scorpion.png`: SC-6 at 24.
- `win.png`: K1-6 played to the win (`-1`).
- `share.png`: SC-6 to the win, then tap "Share deal".
- `new-game-dark.png`: SC-6 to the win in dark mode, tap the win card's "New game", select Spider, 2 suits,
  Hard. Make one real move in a dealt game first, or the first-ever greeting shows.
- `landscape.png`: load K1-6 in portrait (the code field is off screen in landscape), rotate, then autoplay
  with `--ei stopAt 70`.

## Commands
- Engine tests (fast, no device needed): `./gradlew :engine:test` (and `:extras:test` for the fun bits)
- Build and install: `./gradlew :app:installDebug`
- Generate deal packs: `./gradlew :dealgen:run --args="..."` (options in the README)
- On Windows, use `gradlew.bat`.
