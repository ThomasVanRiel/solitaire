#!/bin/bash
# Takes a screenshot of a deal at a given move, for the README (debug build on the emulator/phone).
# Usage: tools/screenshot.sh CODE STOPAT OUT.png      e.g. tools/screenshot.sh K1-6 70 screenshots/klondike.png
#   STOPAT -1 plays to the win and captures the win screen.
# It types CODE in New game, then restarts the app with the debug autoplay (which replays the stored solution,
# never counts in the statistics and so never earns achievements) and waits for "<STOPAT> moves" on screen.
# Waiting uses uiautomator dumps, since the agent shell blocks plain `sleep`.
set -e
PKG=io.github.thomasvanriel.solitaire
code=$1; stop=$2; out=$3
dump() { adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml; }
center() { # print centre of the first node whose text or content-desc matches $1
  dump | grep -o "<node[^>]*\(text\|content-desc\)=\"$1\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' |
    sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}
waitfor() { for i in $(seq 1 60); do dump | grep -q "$1" && return 0; done; echo "timeout waiting for $1"; return 1; }
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity --ez hideDebugUi true >/dev/null
waitfor 'text="New game"'
adb shell input tap $(center "New game")
waitfor 'Or play a deal code'
adb shell input tap $(center "Or play a deal code")
for try in 1 2 3 4 5; do
  adb shell input keyevent 123 $(printf '67 %.0s' $(seq 1 12))   # end, then clear the field
  adb shell input text "$code"
  dump | grep -q "text=\"$code\"" && break
done
adb shell input keyevent 66
waitfor "#${code#*-} "
adb shell am force-stop $PKG
if [ "$stop" = "-1" ]; then
  adb shell am start -n $PKG/.MainActivity --ez autoplay true --ez hideDebugUi true --el stepMs 20 >/dev/null
  waitfor 'You won'
  for i in 1 2 3; do dump >/dev/null; done   # let the win card settle
else
  adb shell am start -n $PKG/.MainActivity --ez autoplay true --ez hideDebugUi true --ei stopAt $stop --el stepMs 20 >/dev/null
  waitfor "text=\"$stop moves"
  for i in 1 2; do dump >/dev/null; done      # let the last card land
fi
adb exec-out screencap -p > "$out"
echo "saved $out"
