#!/usr/bin/env bash
# Run on the booted emulator before the device tests. Waits until the package and activity
# services have answered shell commands for 30 s in a row (on the Android 17 image the package
# manager is overloaded for a while after first boot and `cmd package` fails with "Can't find
# service"), then installs the debug APK: when installing fails, Gradle's connected task
# reports an empty reason and ends successfully with no tests run, so the reason is printed
# here instead.
set -u

services_up() {
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] &&
    adb shell cmd package list packages android 2>/dev/null | tr -d '\r' | grep -q '^package:android' &&
    adb shell cmd activity get-current-user 2>/dev/null | tr -d '\r' | grep -qE '^[0-9]+$'
}

stable=0
for i in $(seq 1 120); do
  if services_up; then stable=$((stable + 1)); else stable=0; fi
  if [ "$stable" -ge 6 ]; then
    echo "System services up for 30 s (check $i)"
    break
  fi
  sleep 5
done
[ "$stable" -ge 6 ] || echo "::warning::System services did not answer for 30 s in a row within 10 min"

apk=app/build/outputs/apk/debug/app-debug.apk
for attempt in 1 2 3; do
  output=$(adb install -r -t "$apk" 2>&1) && break
  echo "Install attempt $attempt failed: $output"
  sleep 20
done
if ! adb shell cmd package list packages com.brkckr.parkv3 2>/dev/null | tr -d '\r' | grep -q '^package:com.brkckr.parkv3$'; then
  echo "::error::Installing $apk on the emulator failed: $output"
  echo "--- device: $(adb shell getprop ro.build.version.release_or_codename) / $(adb shell getprop ro.product.cpu.abilist) / page size $(adb shell getconf PAGE_SIZE)"
  adb logcat -d | grep -iE 'PackageManager|PackageInstaller|installd|INSTALL_FAILED' | grep -vE 'Instant App installer|ephemeral installer' | tail -n 80
  exit 1
fi
echo "$output"
# Gradle installs its own build next, which can be signed with a different debug key; a copy
# left here would block it (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
adb uninstall com.brkckr.parkv3
