# Working rules for the coding agent

**Solitaire** is an open-source Android patience app (Klondike, Spider, Scorpion II) where every deal is
proven winnable. `README.md` covers features and building; `docs/DESIGN.md` covers how the engine, solver,
difficulty rating, deal packs and app work.

## How to work
- After a change, run `./gradlew :engine:test :app:assembleDebug :app:lint`, keep `docs/DESIGN.md` and the
  README in step with the code, then make a git commit with a clear message.
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
- The agent may run in a jail without the Android SDK. Then it builds and tests `:engine` and `:dealgen`
  itself, and the owner runs the `:app` builds on the host (output in `logs/`). `settings.gradle.kts` skips
  `:app` when no SDK is found.
- The host's adb server (127.0.0.1:5037) is reachable, so the agent can install APKs and take screenshots.
- JDK: use `JAVA_HOME=/opt/android-studio/jbr` (`/usr/bin/java` has no `javac`).

## Checking your work on the device
- Use an emulator or a connected phone through `adb`.
- To check a screen, take a screenshot with `adb exec-out screencap -p > screenshots/<name>.png` and
  look at the image yourself.
- To check gameplay, use the debug-only autoplay, which replays a deal's stored solution in the real UI
  (see "Debug tools" in the README). It must not be present in release builds.

## Commands
- Engine tests (fast, no device needed): `./gradlew :engine:test`
- Build and install: `./gradlew :app:installDebug`
- Generate deal packs: `./gradlew :dealgen:run --args="..."` (options in the README)
- On Windows, use `gradlew.bat`.
