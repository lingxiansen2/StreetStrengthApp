# StreetStrength Local Quality Checklist

Scope: testing, environment, and release-readiness checks.

## Default Local Gate

- Run `scripts\verify-local.cmd`.
- Expected Gradle invocations: `--status`, then `clean`, then `assembleDebug`, then `testDebugUnitTest`.
- The default quality script uses `--max-workers=1` to avoid Windows file-lock races in this local workspace.
- The local Gradle wrapper redirects Android and Kotlin user data into `.local-tools`.
- If `app/build` is locked by a stale Gradle process, retry with `scripts\verify-local.cmd -StopDaemons`.
- Treat any non-zero command exit code as a failed gate.

## Emulator Instrumentation Gate

- Use only an explicit emulator serial, for example `emulator-5554`.
- Do not target a physical device unless the user explicitly allows it.
- Run `scripts\verify-local.cmd -AndroidTest -DeviceSerial emulator-5554`.
- To run the full local emulator path, run `scripts\verify-local.cmd -AndroidTest -StartEmulator -AvdName StreetStrengthApi34 -EmulatorPort 5584`.
- The default instrumentation class verifies the rest-timer alarm broadcast posts the rest-finished notification.

## Manual Regression Before Release Handoff

- Start a planned workout for today and complete at least one set.
- Start a rest timer, send the app to background, and wait for the rest-finished notification.
- Tap the notification and confirm the app returns to the workout state.
- Dismiss the rest alert and confirm vibration/notification cleanup.
- Confirm past plans cannot start, future plans do not start early, and today's unfinished plan can resume.

## Handoff Notes

- Record the emulator serial and Android version used for instrumentation.
- Record any skipped checks and why they were skipped.
- Hand off APK/version changes to the release thread; this checklist does not own version bumps.
