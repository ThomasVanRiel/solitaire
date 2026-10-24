# Releasing

Signing keys are created and kept by the owner only. Never commit them, and never share the passwords.

## 1. Create a release keystore (once)

Run this on your own machine, **outside** the project folder, and keep the file and passwords safe (for
example in a password manager, plus an offline backup). If you lose the key you can't update the app on
Google Play (unless you use Play App Signing) or F-Droid reproducible builds.

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
./gradlew :app:bundleRelease                            # AAB for Google Play
```

Without `keystore.properties` the release APK is unsigned (`app-release-unsigned.apk`).

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
- Update the store text in `fastlane/metadata/android/en-US/`.
- Tag the release in git.
