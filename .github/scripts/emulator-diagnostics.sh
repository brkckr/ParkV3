#!/usr/bin/env bash
# Run after the device tests: shows whether the emulator's system restarted or crashed during
# the run. Information only, never fails the job.
echo "--- uptime: $(adb shell uptime 2>&1)"
echo "--- boot completed: $(adb shell getprop sys.boot_completed 2>&1)"
echo "--- crash buffer:"
adb logcat -b crash -d 2>&1 | tail -n 120
echo "--- system_server / watchdog:"
adb logcat -d 2>&1 | grep -E 'Watchdog|system_server|FATAL EXCEPTION|SystemServer' | tail -n 60
exit 0
