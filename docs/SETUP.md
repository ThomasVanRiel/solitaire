# Setting up a development machine

From a fresh machine to running tests, building the app, playing it on an emulator and generating deal
packs. Linux and Windows; on Windows use `gradlew.bat` instead of `./gradlew` and `\` in paths.

## 1. Install the tools

You need Git, a full JDK (with `javac`, version 17 or newer; CI uses 21) and the Android SDK.

**Easiest: Android Studio.** It brings its own JDK (the "JBR") and installs the SDK on first start.

1. Install Git and [Android Studio](https://developer.android.com/studio) (Linux: unpack it, for example to
   `/opt/android-studio`, and run `bin/studio.sh`).
2. On first start, let the setup wizard install the SDK (it picks `~/Android/Sdk` on Linux,
   `%LOCALAPPDATA%\Android\Sdk` on Windows).
3. In *Settings → Languages & Frameworks → Android SDK*, make sure these are installed:
   - *SDK Platforms*: Android API 37 (the app compiles against 37).
   - *SDK Tools*: Android SDK Platform-Tools (adb), Android Emulator, Android SDK Command-line Tools.
   Missing build tools are downloaded by Gradle on the first build.

**Without Android Studio**: install a JDK 21 (for example Eclipse Temurin) and the Android
[command-line tools](https://developer.android.com/studio#command-line-tools-only), unpacked to
`<sdk>/cmdline-tools/latest`, then:

```sh
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37" "emulator" "system-images;android-37;google_apis;x86_64"
```

## 2. Point the build at the JDK and the SDK

**The JDK.** Gradle uses `JAVA_HOME`, or the first `java` on the `PATH`. That must be a full JDK: a runtime
without `javac` fails before compiling anything. With Android Studio, use its JBR:

```sh
export JAVA_HOME=/opt/android-studio/jbr            # Linux (add it to ~/.bashrc or ~/.zshrc)
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"   # Windows (new terminals pick it up)
```

Check with `"$JAVA_HOME/bin/javac" -version`.

**The SDK.** Create `local.properties` in the project folder (Android Studio does this when it opens the
project; the file is git-ignored):

```properties
sdk.dir=/home/you/Android/Sdk
# Windows: sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

`ANDROID_HOME` works too. Without either, the build quietly leaves out the `:app` module and only builds the
engine, extras and deal generator (it prints a warning).

## 3. Get the code and build

```sh
git clone https://github.com/ThomasVanRiel/solitaire.git
cd solitaire
./gradlew :engine:test :extras:test        # first run downloads Gradle and the libraries (needs internet)
./gradlew :app:assembleDebug :app:lint     # APK: app/build/outputs/apk/debug/app-debug.apk
```

All four should pass. `:engine:test` also replays every shipped deal's proof, so it fails if a pack is
stale (after a rules change; see step 5).

## 4. Run it on an emulator or phone

**Emulator.** In Android Studio, *Device Manager → Create virtual device → Medium Phone*, system image API 37.
Or from the command line:

```sh
avdmanager create avd -n Medium_Phone_API_37.0 -k "system-images;android-37;google_apis;x86_64" -d medium_phone
emulator -avd Medium_Phone_API_37.0
```

(`avdmanager list device` shows the device ids if `medium_phone` isn't there.)

The emulator needs hardware virtualisation: on Linux `/dev/kvm` must exist and your user must be in the
`kvm` group; on Windows enable the Windows Hypervisor Platform. If it crashes with graphics errors, start it
with `-gpu swiftshader_indirect` (software rendering: slower, but stable).

**Phone.** Enable *Developer options → USB debugging* and connect it; `adb devices` should list it.

Then:

```sh
./gradlew :app:installDebug
adb shell dumpsys package io.github.thomasvanriel.solitaire | grep versionName   # the version you built
```

(On Windows use `findstr versionName` instead of `grep versionName`.)

Debug builds have an *Auto* button that plays the deal's stored solution (see "Debug tools" in the README).

## 5. Generate the deal packs

The app ships verified deals in `app/src/main/assets/deals/`. Regenerate them after anything that changes
the rules or the order of the legal-move list (the solutions are stored as positions in that list). The
generator uses every core but one by default; give it more memory when you use many threads (about 2 GB
per 16 threads is plenty):

```sh
# Only the rule sets that changed, at the size currently shipped (100 deals per difficulty):
./gradlew :dealgen:run -PdealgenHeap=8g --args="--rules draw1,draw3,yukon,russian --per-tier 100"

# Everything:
./gradlew :dealgen:run -PdealgenHeap=8g --args="--per-tier 100"

# Then check every proof and commit the packs:
./gradlew :engine:test
git add app/src/main/assets/deals && git commit -m "Regenerate deal packs"
```

At 100 per tier the whole set takes a few minutes on 8 threads; 4-suit Spider and Scorpion II take the
longest. Deals are taken in seed order whatever the thread count, so packs made on any computer come out
the same. All options are in the README under "Deal packs".

## Troubleshooting

- *"No Android SDK found … skipping :app"*: `local.properties` or `ANDROID_HOME` is missing or wrong.
- *"Task 'assembleDebug' not found in project ':app'"*: same cause.
- Compilation fails straight away with a toolchain or `javac` error: `JAVA_HOME` points at a JRE or an
  old JDK.
- `ShippedPacksTest` fails: a pack no longer matches the rules; regenerate it (step 5).
- `adb devices` lists nothing while the emulator runs: `adb kill-server && adb start-server`.
