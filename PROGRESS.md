# Progress

## Working setup (how this project is being built)

The coding agent runs inside a jail that only mounts the project folder. It has no Android SDK and no
emulator access, so:
- the agent builds and tests `:engine` and `:dealgen` itself (pure JVM);
- the owner runs the `:app` Gradle builds on the host and writes the output to `logs/`;
- the agent installs APKs, launches the app and takes screenshots itself through `adb`, because the host's adb
  server on 127.0.0.1:5037 is reachable from the jail.

`settings.gradle.kts` leaves `:app` out when it can't find an SDK, so the JVM modules still build.

## M0: Environment and skeleton

### Toolchain found
| Tool | Where | Notes |
|---|---|---|
| JDK | `/opt/android-studio/jbr` (JBR 25.0.3) | `/usr/bin/java` is OpenJDK 21 but a runtime only, with no `javac`. Use `JAVA_HOME=/opt/android-studio/jbr`. |
| Gradle | wrapper 9.8.2 | Generated from the official distribution, checksum verified. |
| Android SDK | owner's host, `~/Android/Sdk` | Not visible to the agent. |
| adb | `/usr/bin/adb` | No emulator was reachable from the jail during M0. |
| Emulator | owner's host | Run by the owner. |

### Versions chosen (latest stable as of 2026-10-24)
- AGP 9.4.1. It needs Gradle ≥ 9.6 and supports up to API 37. AGP 9 has built-in Kotlin, so
  `org.jetbrains.kotlin.android` is **not** applied. The Compose compiler plugin still is.
- Kotlin 2.4.21, Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0,
  core-ktx 1.19.1, DataStore 1.3.0, coroutines 1.11.0, JUnit 6.1.3, Kover 0.9.9.
- `compileSdk = 37`, `targetSdk = 36`. Google Play has required API 36 for new apps and updates since
  2026-08-31 (developer.android.com/google/play/requirements/target-sdk). `minSdk = 26`.
- Bytecode target: JVM 17.

### Decisions
- Application ID: `io.github.thomasvanriel.winnable`. The owner's GitHub username is ThomasVanRiel,
  lowercased because Java package names are lowercase by convention.
- License: GPL-3.0-or-later (the default in the plan).
- Engine benchmarks are JUnit tests tagged `benchmark`. They only run with `-Pbench`.

### Status
- `:engine:test` and `:dealgen:run` pass in the agent's jail.
- The owner ran `:engine:test :app:assembleDebug` on the host: it passed (log in `logs/m0-build.log`, not committed).
- The agent installed the APK on `emulator-5554` with `adb install` (reaching the host's adb server over the
  shared network), launched it, and checked `screenshots/m0-hello.png`. **M0 done.**

### Known issues
- Gradle reports a deprecation (`Configuration.setVisible`). It comes from a plugin, not from our build scripts.
- AGP prints "Unable to strip libandroidx.graphics.path.so". This is harmless.
