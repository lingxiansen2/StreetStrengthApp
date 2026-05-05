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
- To start the local AVD separately, run `scripts\start-emulator-local.cmd -AvdName StreetStrengthApi34 -EmulatorPort 5584`.
- To start the local AVD as a visible, manually operable phone window, run `scripts\start-emulator-local.cmd -AvdName StreetStrengthApi34 -EmulatorPort 5584 -WindowedEmulator -RestartEmulator`.
- Run `scripts\verify-local.cmd -AndroidTest -DeviceSerial emulator-5554`.
- To run the full local emulator path, run `scripts\verify-local.cmd -AndroidTest -StartEmulator -AvdName StreetStrengthApi34 -EmulatorPort 5584`.
- Use `-ColdBootEmulator` or `-WipeEmulatorData` only when diagnosing a broken AVD; the default path intentionally reuses the normal AVD boot flow because it is faster and more stable locally.
- The default instrumentation class verifies the rest-timer alarm broadcast posts the rest-finished notification.

## Background Timer Evidence Capture

- Use `scripts\capture-background-timer-evidence.cmd -StartEmulator -RunReceiverInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584` to collect logcat, device properties, package state, alarm state, device-idle state, appops, power state, battery state, notification state, and the receiver instrumentation result.
- Use `scripts\capture-background-timer-evidence.cmd -StartEmulator -RunUiFlowInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584` to automatically simulate the user flow: start workout, finish a set, enter rest, send the app to background, wait for the rest timer, return to the app, and try to continue/end the workout.
- Launch the debug-only self-test explicitly with `..\..\.local-tools\android-sdk\platform-tools\adb.exe -s emulator-5584 shell am start -n com.codex.streetstrength/.timer.RestReminderSelfTestActivity`; it is intentionally not a launcher icon so notification intents cannot resolve to it instead of the training app.
- Use `scripts\capture-background-timer-evidence.cmd -StartEmulator -WindowedEmulator -RestartEmulator -KeepEmulator -AvdName StreetStrengthApi34 -EmulatorPort 5584 -CaptureSeconds 180` to start a visible emulator and capture manual reproduction evidence without closing it at the end. `-RestartEmulator` is required when a headless emulator is already connected, because an existing emulator process cannot be converted into a visible window.
- The capture script refuses non-`emulator-*` serials unless `-AllowPhysicalDevice` is passed after explicit user approval.
- For physical-device reproduction after emulator passes, use `scripts\capture-background-timer-evidence.cmd -DeviceSerial <physical-serial> -AllowPhysicalDevice -Scenario real-device-background-rest -CaptureSeconds 180` against an already installed app. During the wait window, reproduce background rest finish and the next/end click path, then hand off the generated folder.
- Physical-device mode is log/dumpsys capture only. The script refuses `-InstallDebugApks`, `-RunReceiverInstrumentation`, and `-RunUiFlowInstrumentation` on physical devices to avoid overwriting the user's device state.
- For a manual crash reproduction on an already running emulator, use `scripts\capture-background-timer-evidence.cmd -DeviceSerial emulator-5584 -CaptureSeconds 90`, reproduce the rest timer background/return/click path during the wait window, then hand off the generated folder under `work\thread-results\7-quality-env`.
- The generated `summary.md` flags whether `RestTimerReceiver` appeared in logcat, whether common crash exceptions appeared, and both the first captured app stack frame and first production app stack frame if present.

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
