#!/usr/bin/env bash
# Draws the compatibility corpus's widget trees as real RemoteViews on a device and compares the
# PNGs with the last accepted ones.
#
#   scripts/compat-snapshots.sh <corpus> [--update]
#
# Needs <corpus>/_golden/ from the compatibility report (see README). PNGs go to
# <corpus>/_snapshots/; --update accepts them into <corpus>/_snapshots_golden/.
# With several devices connected, pick one with ANDROID_SERIAL. Compare PNGs from one device only:
# fonts and density differ between devices.
set -euo pipefail

corpus=${1:?usage: $0 <corpus> [--update]}
update=${2:-}
trees="$corpus/_golden"
[ -d "$trees" ] || { echo "no $trees: run the compatibility report with -PcompatUpdateGolden first" >&2; exit 1; }

root=$(cd "$(dirname "$0")/.." && pwd)
pkg=kr.b1ink.scriptwidget.widget.test

"$root/gradlew" -p "$root" -q :widget:installDebugAndroidTest
# The test app's private files folder (shared storage folders made by adb aren't readable by the app).
adb shell "run-as $pkg sh -c 'rm -rf files/snapshots && mkdir -p files/snapshots/trees'"
tar -C "$trees" -cf - . | adb exec-in "run-as $pkg tar -xf - -C files/snapshots/trees"
adb shell am instrument -w -e snapshots true \
  -e class kr.b1ink.scriptwidget.widget.CorpusSnapshotRender "$pkg/androidx.test.runner.AndroidJUnitRunner" | tail -2

out="$corpus/_snapshots"
rm -rf "$out" && mkdir -p "$out"
adb exec-out "run-as $pkg tar -cf - -C files/snapshots/png ." | tar -xf - -C "$out"
echo "Drew $(ls "$out" | wc -l | tr -d ' ') widgets into $out"
adb exec-out "run-as $pkg cat files/snapshots/failures.txt" > "$corpus/_snapshots_failures.txt"
if [ -s "$corpus/_snapshots_failures.txt" ] && [ "$(tr -d '\n' < "$corpus/_snapshots_failures.txt")" != "" ]; then
  echo "Could not draw (see $corpus/_snapshots_failures.txt):"
  sed 's/^/  /' "$corpus/_snapshots_failures.txt"
fi

golden="$corpus/_snapshots_golden"
if [ "$update" = "--update" ]; then
  rm -rf "$golden" && cp -R "$out" "$golden"
  echo "Accepted as $golden"
elif [ -d "$golden" ]; then
  changed=0
  for png in "$out"/*.png; do
    name=$(basename "$png")
    if [ ! -f "$golden/$name" ]; then echo "new      $name"; changed=$((changed + 1))
    elif ! cmp -s "$png" "$golden/$name"; then echo "changed  $name"; changed=$((changed + 1)); fi
  done
  for png in "$golden"/*.png; do [ -f "$out/$(basename "$png")" ] || { echo "gone     $(basename "$png")"; changed=$((changed + 1)); }; done
  echo "$changed differ from $golden"
fi
