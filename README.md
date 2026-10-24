# Winnable Patience

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
```

If no Android SDK is found (`ANDROID_HOME`, or `sdk.dir` in `local.properties`), the `:app` module is
skipped so that the engine and deal generator can still be built and tested.

## License

GPL-3.0-or-later. See `LICENSE`.
