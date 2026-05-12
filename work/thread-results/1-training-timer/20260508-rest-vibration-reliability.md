# 2026-05-08 rest vibration reliability

## Scope

Thread 1 handled the training timer side of `20260508-rest-vibration-reliability-routing.md`.

The fix focuses on keeping one timer's finish alert idempotent across:

- `RestTimerReceiver -> RestTimerAlert -> RestTimerService.ACTION_FINISH`
- duplicate exact alarm / alarmClock delivery
- notification stop, app UI stop, skip rest, end workout, and fallback `stopService()`
- app restore/watchdog expired-timer compensation

## Code Changes

- `RestTimerAlert.kt`
  - Stop now records the stopped `timerId` and logs that future FINISH delivery for that timer is suppressed.
  - Vibration cancellation clears the in-process active alert timer id after the stop request has been persisted.
  - Existing `VibratorManager.cancel()` plus `defaultVibrator.cancel()` behavior is preserved.

- `RestTimerService.kt`
  - `ACTION_STOP` now carries and reads `timerId`, calls `RestTimerAlert.stop(timerId)`, then performs normal service cleanup.
  - `RestTimerService.stop(context, timerId)` sends the timer id through the stop intent before falling back to `stopService()`.
  - `onDestroy()` checks whether the active/finished timer was already marked stopped; if yes, it removes the foreground notification and cancels vibration even when destruction came from fallback `stopService()`.
  - Duplicate service FINISH calls now log and return without restarting the alert.
  - FIRED timer restore refreshes the finished notification without restarting vibration.
  - Service FINISH for a timer already marked `FIRED` keeps the notification path alive but does not start vibration again.

- `RestTimerController.kt`
  - Propagates `timerId` into `RestTimerService.stop()`.

- `RestTimerReceiver.kt`
  - Re-checks stopped timer suppression after claiming the timer and before posting/vibrating.
  - Logs inspected timer state when duplicate delivery reaches the receiver.

- `StreetStrengthApp.kt`
  - Restore/watchdog paths now log `timerId/sessionId/taskId/createdAt/endElapsedRealtimeMs/source`.
  - Expired RUNNING timers still go through `RestTimerAlarmScheduler.dispatchFinish()`.
  - Removed the direct fallback `markRestTimerFired()` call; if receiver dispatch fails, the app falls back to `RestTimerService.startFinishFromAlarm()` instead of silently marking FIRED.

- `TrainingScreen.kt`
  - Back navigation now stops the finished rest alert for the active `timerId` when the UI is in `isRestComplete`, without cancelling a still-running rest countdown.

- `RestReminderSelfTestActivity.kt`
  - Debug self-test stop now passes the active self-test `timerId` into `RestTimerController.stopRestAlert()`.

## Verification

- `scripts/gradlew-local.cmd testDebugUnitTest` passed.
- `scripts/gradlew-local.cmd assembleDebug` passed.
- `scripts/gradlew-local.cmd assembleDebugAndroidTest` passed.

No connected real-device matrix was run in this turn. The next validation should install the newly built APK and run the 7-quality real training matrix against device `bbc478f4`.

## Release Note

This change does not bump `versionName/versionCode` and does not generate a release-chain APK. Overall integration can decide whether to publish `v1.1.25 / versionCode 27` after the 1/6/7 reports are reviewed.
