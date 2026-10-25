# Releasing

Signing keys are created and kept by the owner only. Never commit them, and never share the passwords.

## 1. Create a release keystore (once)

Run this on your own machine, **outside** the project folder, and keep the file and passwords safe (for
example in a password manager, plus an offline backup). If you lose the key, Android won't install a new
version over the old one: you'd have to uninstall first, which deletes the saved game and statistics.

```sh
mkdir -p ~/keys && chmod 700 ~/keys     # keytool does not create the folder
keytool -genkeypair -v \
  -keystore ~/keys/solitaire-release.jks \
  -alias solitaire \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` comes with the JDK (for example `/opt/android-studio/jbr/bin/keytool`).

## 2. Tell Gradle where it is

Create `keystore.properties` in the project root. It is git-ignored; check with `git status` that it does
not show up.

```properties
storeFile=/home/you/keys/solitaire-release.jks
storePassword=...
keyAlias=solitaire
keyPassword=...
```

## 3. Build

```sh
./gradlew :engine:test :app:lint :app:assembleRelease   # signed APK in app/build/outputs/apk/release/
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties` the release APK is unsigned (`app-release-unsigned.apk`).

Or in one step: `tools/build-release.sh` runs the tests and lint, builds the release APK and copies it to
the project root as `solitaire-<versionName>.apk` (git-ignored). An unsigned build gets an `-unsigned`
suffix, and one made with `-PsignReleaseWithDebugKey` (passed through to Gradle) a `-debugkey` suffix.

To try a release build on your own device without a release key, sign it with the local debug key
(testing only, never publish this):

```sh
./gradlew :app:assembleRelease -PsignReleaseWithDebugKey
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 4. Before each release

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- Optionally regenerate bigger deal packs: `./gradlew :dealgen:run --args="--per-tier 2500"`.
  Then run `:engine:test`, which replays every shipped proof.
- Tag the release in git.

## 5. Publish the APK on GitHub

The README's [Download](README.md#download) section links to the latest GitHub release, so each new release
shows up there without editing the README.

With the GitHub CLI (`gh auth login` once):

```sh
tools/build-release.sh                  # makes solitaire-0.4.0.apk
git tag v0.4.0 && git push origin v0.4.0
gh release create v0.4.0 solitaire-0.4.0.apk --title "0.4.0" --generate-notes
```

Or on the website: the repository's **Releases** page, **Draft a new release**, pick or create the tag,
drag the APK into the assets box and press **Publish release**.

Only upload an APK signed with the release key (never the debug-key build), always with the same key, or
updates won't install over the previous version.
