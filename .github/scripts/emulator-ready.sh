#!/usr/bin/env bash
# Run on the booted emulator before the device tests. Waits until the package manager answers,
# then installs the debug APK once: when installing fails, Gradle's connected task reports an
# empty reason and ends successfully with no tests run, so the reason is printed here instead.
set -u

for i in $(seq 1 90); do
  if adb shell pm path android 2>/dev/null | grep -q '^package:'; then
    echo "Package manager ready (attempt $i)"
    break
  fi
  sleep 2
done

apk=app/build/outputs/apk/debug/app-debug.apk
if ! output=$(adb install -r -t "$apk" 2>&1); then
  echo "::error::Installing $apk on the emulator failed: $output"
  echo "--- device: $(adb shell getprop ro.build.version.release_or_codename) / $(adb shell getprop ro.product.cpu.abilist) / page size $(adb shell getconf PAGE_SIZE)"
  adb logcat -d | grep -iE 'PackageManager|PackageInstaller|installd|INSTALL_FAILED' | tail -n 80
  exit 1
fi
echo "$output"
