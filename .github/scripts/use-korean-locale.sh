#!/usr/bin/env bash
# The app's device tests look for Korean texts. KoreanLocaleTestRunner sets the per-app language,
# which exists from Android 13; on older emulators this switches the system language instead
# (emulator images allow adb root) and restarts the framework.
set -euo pipefail

sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$sdk" -ge 33 ]; then exit 0; fi

adb root >/dev/null
adb wait-for-device
adb shell setprop persist.sys.locale ko-KR
adb shell stop
adb shell start
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
# The launcher and system UI settle a little after boot_completed.
sleep 10
echo "System language: $(adb shell getprop persist.sys.locale | tr -d '\r') (API $sdk)"
