# Working rules for the coding agent

You're building **Winnable Patience**, an open-source Android Klondike app where every deal is
proven winnable. The full spec and the milestones are in `PLAN.md`. Read it completely before starting.

## How to work
- Work through the milestones in `PLAN.md` in order, M0 to M7. Don't start a milestone until the one
  before it meets its "Done when" criteria.
- At the end of every milestone: run `./gradlew :engine:test :app:assembleDebug` (plus `:app:lint` from
  M4 onwards), update `PROGRESS.md` (what you did, the decisions you made, benchmark numbers, known
  issues), then make a git commit with a clear message.
- Keep going on your own. Only stop and ask the owner when:
  - a tool or SDK is missing and needs installing (say exactly what to install), or
  - a decision in PLAN.md section 1 still has `CHANGEME` in it, or
  - a milestone stays blocked after several real attempts to fix it.
- If you change part of the plan, write down what and why in `PROGRESS.md`.

## The one rule that must never break
No deal reaches the player unless its stored solution has been replayed through the rules engine
(`ProofVerifier`) and reaches a won game. The solver and the game must use the same `Rules` code.
If you're tempted to weaken this to make something faster or simpler, don't.

## Hard constraints
- No `INTERNET` permission, no analytics, no ads, no Google Play Services, no closed-source dependencies.
- No third-party card images. Draw the cards in code. Fonts must be OFL-licensed or system fonts.
- Use only versions of dependencies that are open source and stable.
- Stay inside this project folder. Don't change files elsewhere on the computer, apart from installing
  build tools when the owner has agreed to it.
- Never create, commit or print signing keys or passwords.
- Don't push to a remote or publish anything unless the owner asks you to.

## Checking your work on the device
- Use an emulator or a connected phone through `adb`.
- To check a screen, take a screenshot with `adb exec-out screencap -p > screenshots/<name>.png` and
  look at the image yourself.
- To check gameplay, add a debug-only action that replays a deal's stored solution in the real UI.
  It must not be present in release builds.

## Commands
- Engine tests (fast, no device needed): `./gradlew :engine:test`
- Build and install: `./gradlew :app:installDebug`
- Generate deal packs: `./gradlew :dealgen:run --args="..."` (write the options in the README)
- On Windows, use `gradlew.bat`.
