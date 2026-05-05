# 2026-05-04 Single Vibration / Stop Alert Fix

## Scope

- Thread: `1-training-timer`
- Source task: `work/thread-results/0-overall-design/20260504-real-device-background-timer-vibration-share.md`
- Real-device evidence source: `work/thread-results/7-quality-env/20260504-real-device-background-rest-timer-evidence.md`
- Target device used for retest: `bbc478f4`
- Excluded device not targeted: `97257126520013`

## Root Cause

The real-device failure was caused by two independent alert owners for one rest-finished event:

- `RestTimerReceiver.handleFinish()` called `RestTimerAlert.showFinishedAlert(...)`, which posted the finished notification and started direct continuous vibration.
- `RestTimerService.startFinishedAlert()` then started the foreground finished notification and called `RestTimerAlert.startContinuousVibration(...)` again.

On the OnePlus device this produced two app vibration tokens. Stop/cancel only cancelled one reliably, so vibration could continue after returning to training.

The installed device also had an old `rest_finished` notification channel with vibration still enabled. Android preserves existing channel settings, so calling `enableVibration(false)` on the same channel id cannot reliably silence already-installed users.

## Code Changes

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerReceiver.kt`
  - Receiver no longer starts direct vibration on the normal finish path.
  - Receiver now starts `RestTimerService.ACTION_FINISH` first.
  - If the service start call itself fails, Receiver posts a finished notification only, without direct vibration.

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - `startFinishFromAlarm(...)` now returns `Boolean` so Receiver can avoid duplicate alert work.
  - `RestTimerService.startFinishedAlert()` remains the only normal owner of `startContinuousVibration(...)`.
  - Finished foreground notification channel changed to `rest_finished_silent_v2`.
  - `onDestroy()` now always cancels vibration, cancels notification id `401`, and calls `stopForeground(STOP_FOREGROUND_REMOVE)`. Alarm cancellation remains guarded by explicit stop state.

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlert.kt`
  - Finished notification channel changed to `rest_finished_silent_v2`.
  - Finished notification is explicitly silent: `setDefaults(0)` and `setSilent(true)`.
  - `showFinishedAlert(...)` was replaced with `showFinishedNotification(...)`; this fallback posts notification only and does not start direct vibration.

Existing unified stop routes were rechecked:

- `TrainingScreen.kt`: next/rest skip/end paths call `RestTimerController.stopRestAlert(...)`.
- `MainActivity.kt`: notification content/open path with `EXTRA_STOP_ALERT` calls `RestTimerController.stopRestAlert(...)`.
- `RestTimerReceiver.kt`: notification close action calls `RestTimerController.stopRestAlert(...)`.
- `RestTimerController.kt`: cancels alarm, alert notification/vibration, and service.

## Resulting Runtime Path

Rest finish:

1. `AlarmManager` sends `com.codex.streetstrength.timer.FINISH` to `RestTimerReceiver`.
2. Receiver marks/checks the timer, then starts `RestTimerService.ACTION_FINISH`.
3. Service posts foreground notification id `401` on `rest_finished_silent_v2`.
4. Service starts the single direct continuous vibration.

Stop alert:

1. Next/rest skip/end training, notification open, and notification close all route through `RestTimerController.stopRestAlert(context)`.
2. Controller cancels the pending alarm and calls `RestTimerAlert.stop(...)`.
3. Controller stops `RestTimerService`; service destruction also removes foreground notification and cancels vibration.

## Verification

Local build/test:

```powershell
cd E:\Workspace\GitHub\StreetStrengthApp\packages\StreetStrengthApp-source-20260422
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebug
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" testDebugUnitTest
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebugAndroidTest
```

Results:

- `assembleDebug`: PASS
- `testDebugUnitTest`: PASS
- `assembleDebugAndroidTest`: PASS

Real device:

- Installed debug APK to `bbc478f4` with `adb -s bbc478f4 install -r ...app-debug.apk`: PASS
- Evidence directory:
  - `work/thread-results/1-training-timer/20260504-single-vibration-selftest/20260504-213659-debug-selftest-polled-stop`

Key evidence:

- `07-polled-logcat.txt:5313`: `AlarmManager` delivered `com.codex.streetstrength.timer.FINISH` to `RestTimerReceiver`.
- `07-polled-logcat.txt:7017`: `RestTimerReceiver` received timer `97`.
- `07-polled-logcat.txt:7136`: finished notification posted on `channel_id=rest_finished_silent_v2`.
- `07-polled-logcat.txt:7144`: exactly one app direct vibration token for the finished alert: `token=26997350`.
- `09-after-finish-notification.txt:3` and `:83`: active notification id `401` used `rest_finished_silent_v2`, `SILENT`, `mVibrationPattern=null`, `mVibrationEnabled=false`.
- `12-after-stop-logcat.txt:7752-7753`: notification id `401` removed.
- `12-after-stop-logcat.txt:7855`: direct vibration token `26997350` cancelled.
- `13-after-stop-notification.txt`: no active `NotificationRecord` for `pkg=com.codex.streetstrength id=401`; remaining StreetStrength `StatusBarNotification` lines are under `mArchive`.
- `14-after-stop-services.txt`: `dumpsys activity services com.codex.streetstrength` reports `(nothing)`.
- No production `FATAL EXCEPTION`, `ForegroundServiceStartNotAllowedException`, or app `SecurityException` found in the final evidence.

Note: this automated retest used the debug-only self-test and the notification-open-equivalent `MainActivity extra_stop_alert=true` path. The real training buttons for next group/end workout were code-verified to route through the same `RestTimerController.stopRestAlert(...)` entry point; a full manual A-E training-flow pass can still be repeated by thread 7 if release sign-off requires human UI confirmation.

