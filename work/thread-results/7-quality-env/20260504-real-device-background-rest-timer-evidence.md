# 2026-05-04 Real Device Background Rest Timer Evidence

## Scope

- Thread: 7-quality-env
- Design source: `work/thread-results/0-overall-design/20260504-real-device-background-timer-vibration-share.md`
- Required device used: `bbc478f4`
- Excluded device observed but not targeted: `97257126520013`
- App package: `com.codex.streetstrength`
- Installed version on test device: `versionName=1.1.20`, `versionCode=22`
- Device: OnePlus `PKG110`, Android `16`, SDK `36`

## Evidence Directories

- Baseline running-rest capture:
  - `work/thread-results/7-quality-env/20260504-real-device-background-rest-timer-evidence/20260504-184549-scenario-B-C-D-manual`
- Reproduced finish/vibration-stop failure:
  - `work/thread-results/7-quality-env/20260504-real-device-background-rest-timer-evidence/20260504-211127-scenario-B-C-finish-background-return-stop`

## Permissions And State

From `20260504-211127-scenario-B-C-finish-background-return-stop`:

- `before-adb-devices.txt`: `bbc478f4` online; `97257126520013` also online but was not targeted.
- `before-dumpsys-package.txt`:
  - `POST_NOTIFICATIONS: granted=true`
  - `USE_EXACT_ALARM: granted=true`
- `before-appops.txt` and `after-appops.txt`:
  - `SCHEDULE_EXACT_ALARM: allow`
  - `VIBRATE: allow`
  - `START_FOREGROUND: allow`
  - `BACKGROUND_START_ACTIVITY: allow`

This rules out missing notification permission, exact-alarm permission, and basic foreground-service permission as the primary failure.

## Reproduction Result

User-reported result during the 360s capture:

- App was opened and a 3-minute rest was running.
- App was switched to background.
- Rest finished and vibration/notification appeared.
- Returning to the app and continuing/ending training did not stop vibration.
- Vibration stopped only after the app process was manually closed.

## Log Evidence

File: `20260504-211127-scenario-B-C-finish-background-return-stop/logcat.txt`

- Line 3483: AlarmManager delivered `com.codex.streetstrength.timer.FINISH` to `RestTimerReceiver`.
- Line 3815: app direct vibration started, `uid=10421`, token `60822642`, `repeat=0`.
- Line 3822: app direct vibration started again, `uid=10421`, token `153737219`, `repeat=0`.
- Line 3835: system notification vibration started for `com.codex.streetstrength`, token `39382921`, `repeat=-1`.
- Lines 4004/4112/4113/4288/4297: cancel calls only reference app token `60822642`.
- Line 4006: system notification vibration token `39382921` was cancelled.
- No cancel record was found for app token `153737219`.
- Lines 4839-4840: process was killed/force-stopped by user action (`remove task`, `o-stop(40)`).

Interpretation:

- Background alarm delivery works on real device.
- The failure is not "timer did not fire".
- The app starts direct vibration twice for the same rest-finished event.
- At least one direct vibration token remains uncancelled until process removal.

## Dumpsys Evidence

File: `20260504-211127-scenario-B-C-finish-background-return-stop/after-dumpsys-notification.txt`

- Line 318: active notification remains:
  - `pkg=com.codex.streetstrength`
  - `id=401`
  - `channel=rest_finished`
  - flags include `ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE`
- Line 1186: existing `rest_finished` channel on device still has vibration configuration:
  - `mImportance=4`
  - `mVibrationPattern=[0, 200, 120, 240]`
  - `mVibrationEnabled=true`

File: `20260504-211127-scenario-B-C-finish-background-return-stop/after-dumpsys-services.txt`

- At 21:17:31, `dumpsys activity services` reports `(nothing)` for the package.

Interpretation:

- After user removed the app task, no service was listed, but the finished notification record still appeared in notification dump.
- Existing notification-channel state is stale from earlier builds/device settings and cannot be reliably overwritten by calling `enableVibration(false)` on the same channel id.

## Code Pointers For Thread 1

Likely relevant files:

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlert.kt`
  - `showFinishedAlert()` calls `startContinuousVibration()`.
  - `startContinuousVibration()` uses `VibrationEffect.createWaveform(FINISH_VIBRATION_PATTERN, 0)`, where repeat index `0` means indefinite repeat.
  - `stop()` cancels vibration and notification, but only works if the app path calls it.
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - `startFinishedAlert()` also calls `RestTimerAlert.startContinuousVibration()`.
  - This duplicates the receiver-side vibration for the same finish event.
  - `resetForNewTimer()` and `stopTimerService()` cancel vibration/notification, but the user-facing next/end-training paths need to invoke the same cleanup.
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerController.kt`
  - `stopRestAlert()` is the expected central cleanup entry point.
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/MainActivity.kt`
  - Notification content intent can call `stopRestAlert()` through `EXTRA_STOP_ALERT`.
  - Returning via normal app navigation or pressing next/end training is not proven to use this cleanup path.

## Recommended Fix Direction For Thread 1

1. Ensure there is only one owner for rest-finished vibration.
   - Prefer letting `RestTimerService.startFinishedAlert()` own both the foreground notification and direct vibration.
   - Make `RestTimerReceiver` avoid starting direct vibration if it will start the service, or make receiver notification-only with no `startContinuousVibration()`.

2. Make stop cleanup idempotent and reachable from every user path.
   - `next set`, `end training`, notification open, notification close action, and any finished-rest dismissal should all call `RestTimerController.stopRestAlert(context)`.
   - Cleanup should cancel alarm, cancel direct vibration, cancel notification id `401`, and stop/remove foreground service notification.

3. Fix existing `rest_finished` channel migration.
   - Current device still has `rest_finished` with vibration enabled.
   - Because Android notification channels preserve user/device state, changing `enableVibration(false)` on the same channel id is not enough for already-installed users.
   - Use a new silent finished channel id, or explicitly delete/recreate the old app-owned channel during migration if acceptable.

4. Add verification after the fix.
   - Repeat this exact real-device scenario on `bbc478f4`.
   - Expected logcat: one direct `vibrate is uid=10421` token only, and a matching `cancelVibrate` for that token after next/end/notification action.
   - Expected dumpsys notification: no active `com.codex.streetstrength` id `401` notification after stop.

## Thread 7 Conclusion

Real-device background finish trigger is available on `bbc478f4`: alarm delivery, receiver execution, notification, foreground-service permission, and vibration permission all work.

The remaining real-device failure is stop/cleanup routing for the rest-finished alert. The observed issue should be handed to thread 1 as an app logic fix, not as a local environment, emulator, ADB, or permission problem.
