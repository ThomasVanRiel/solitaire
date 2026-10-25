#!/bin/bash
# Builds the release APK and copies it to the project root as solitaire-<version>.apk (git-ignored).
# Usage: tools/build-release.sh [extra Gradle args]
#   e.g. tools/build-release.sh -PsignReleaseWithDebugKey   (testing only, never publish that APK)
# Runs the engine and extras tests and lint first. Signing uses keystore.properties (see RELEASING.md);
# without it the APK is unsigned and gets an -unsigned suffix.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/android-studio/jbr/bin/javac ]; then
  export JAVA_HOME=/opt/android-studio/jbr
fi

version=$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' app/build.gradle.kts)
[ -n "$version" ] || { echo "versionName not found in app/build.gradle.kts" >&2; exit 1; }

./gradlew :engine:test :extras:test :app:lint :app:assembleRelease "$@"

dir=app/build/outputs/apk/release
if [ -f "$dir/app-release.apk" ]; then
  src=$dir/app-release.apk
  case " $* " in
    *-PsignReleaseWithDebugKey*) out=solitaire-$version-debugkey.apk ;;
    *) out=solitaire-$version.apk ;;
  esac
elif [ -f "$dir/app-release-unsigned.apk" ]; then
  src=$dir/app-release-unsigned.apk
  out=solitaire-$version-unsigned.apk
  echo "warning: no keystore.properties, so the APK is unsigned" >&2
else
  echo "no release APK found in $dir" >&2; exit 1
fi

cp "$src" "$out"
echo "$out"
