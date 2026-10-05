#!/usr/bin/env bash
# Run on the booted emulator before the device tests. Waits until the system services have
# stayed up for a while (on the Android 17 image they restarted shortly after boot), then
# installs the debug APK once: when installing fails, Gradle's connected task reports an empty
# reason and ends successfully with no tests run, so the reason is printed here instead.
set -u

services_up() {
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] &&
    adb shell service check package 2>/dev/null | grep -q ': found' &&
    adb shell service check activity 2>/dev/null | grep -q ': found'
}

stable=0
for i in $(seq 1 60); do
  if services_up; then stable=$((stable + 1)); else stable=0; fi
  if [ "$stable" -ge 6 ]; then
    echo "System services up for 30 s (check $i)"
    break
  fi
  sleep 5
done
[ "$stable" -ge 6 ] || echo "::warning::System services did not stay up for 30 s within 5 min"

apk=app/build/outputs/apk/debug/app-debug.apk
if ! output=$(adb install -r -t "$apk" 2>&1); then
  echo "::error::Installing $apk on the emulator failed: $output"
  echo "--- device: $(adb shell getprop ro.build.version.release_or_codename) / $(adb shell getprop ro.product.cpu.abilist) / page size $(adb shell getconf PAGE_SIZE)"
  adb logcat -d | grep -iE 'PackageManager|PackageInstaller|installd|INSTALL_FAILED' | tail -n 80
  exit 1
fi
echo "$output"
# Gradle installs its own build next, which can be signed with a different debug key; a copy
# left here would block it (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
adb uninstall com.brkckr.parkv3
