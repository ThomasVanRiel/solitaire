# Contributing

Thanks for helping! A few ground rules keep the project trustworthy and simple.

## The rule that must never break

No deal reaches a player unless its stored solution has been replayed through the rules engine
(`ProofVerifier`) and reaches a won game. The solver and the game must use the same `Rules` code.
Changes that weaken this to make something faster or simpler will not be accepted.

## Other constraints

- No `INTERNET` permission, no analytics, no ads, no Google Play Services, no closed-source dependencies.
- No third-party card images: cards are drawn in code. Fonts must be OFL-licensed or system fonts.
- Never commit signing keys or passwords.
- Keep the UI simple.

## Setting up

[`docs/SETUP.md`](docs/SETUP.md) walks through installing the JDK, the Android SDK and an emulator, and
generating the deal packs.

## Before sending a change

```sh
./gradlew :engine:test :app:assembleDebug :app:lint
```

- Engine changes need tests. The rules have hand-built position tests and a 10,000-game fuzz test; keep
  both passing.
- **Don't change the shuffle.** `ShuffleGoldenTest` pins `SHUFFLE_V1`, because deal numbers and deal packs
  depend on it. If the shuffle must change, add a new version.
- Changing the rules or the move-list order invalidates the deal packs: regenerate them with `dealgen`
  (see the README). The tests replay every shipped proof and will tell you.

## License

By contributing you agree that your contribution is licensed under GPL-3.0-or-later.
