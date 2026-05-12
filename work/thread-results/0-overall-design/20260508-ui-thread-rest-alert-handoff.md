# 2026-05-08 handoff to thread 6: rest alert UI and interaction

## Current status

Thread 1 has completed the timer-side fix report:

- `work/thread-results/1-training-timer/20260508-rest-vibration-reliability.md`

Thread 7 evidence visible in this workspace:

- `work/thread-results/7-quality-env/20260508-v1124plus-10x-rest-vibration-loop`
- `work/thread-results/7-quality-env/20260508-v1124plus-10x-filtered-ui-stop`
- `work/thread-results/7-quality-env/20260508-v1124plus-3x-dedup-stop-check`

Note: I do not see a new thread-7 markdown summary report in `work/thread-results/7-quality-env/`. If thread 7 produced one in another terminal/thread, overall design should review that path before release.

## What changed in thread 1

Timer-side semantics are now:

- A finished rest alert belongs to a specific `timerId`.
- Stopping an alert records that `timerId` as stopped.
- Late or duplicate FINISH delivery for that same `timerId` must not restart vibration.
- Duplicate exact alarm / alarmClock delivery is expected; duplicate delivery should log that the timer is already fired and skip duplicate alert.
- App UI stop, notification stop, skip rest, end workout, and back navigation should all pass the active `timerId` when stopping an alert.

## Task for thread 6

You are thread 6: UI design and interaction.

Project root:

- `E:\Workspace\GitHub\StreetStrengthApp`

Source root:

- `E:\Workspace\GitHub\StreetStrengthApp\packages\StreetStrengthApp-source-20260422`

Please read first:

- `THREAD_PROMPTS.md`
- `THREAD_WORKSPLIT.md`
- `work/thread-results/0-overall-design/20260508-rest-vibration-reliability-routing.md`
- `work/thread-results/1-training-timer/20260508-rest-vibration-reliability.md`

Main goal:

- Adjust the training/rest-complete UI so users cannot accidentally leave the alert in an unclear state.
- Do not change the AlarmManager / ForegroundService / Receiver core logic unless you find a UI-triggered bug that cannot be fixed in UI code.

Required checks and changes:

- In rest-complete state, the primary button should clearly mean: stop vibration/reminder and continue to the next group.
- After tapping the rest-complete primary button, immediately disable the button or show a transient "processing" state to prevent double tap.
- If the user opens the app from the finished notification, the training page should make it clear that the reminder has been acknowledged or is waiting for manual continue.
- Distinguish wording between:
  - "关闭提醒": only stop notification/vibration.
  - "进入下一组": stop notification/vibration and clear the current rest timer.
  - "结束训练": stop notification/vibration and abandon/end current session.
- End-workout confirmation text must mention it will stop the current rest reminder.
- Back navigation during rest-complete should not silently leave vibration running.
- Do not make any layout change that makes the current action, rest state, or stop button harder to find during training.

Suggested files:

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`
- Shared UI components only if necessary.

Validation required:

- `scripts/gradlew-local.cmd assembleDebug`
- If logic changes beyond UI state, also run `scripts/gradlew-local.cmd testDebugUnitTest`
- Manual check or screenshot-level check for:
  - resting
  - rest complete / reminder active
  - after tapping continue/stop
  - end workout confirmation

Report path:

- `work/thread-results/6-ui-interaction/20260508-rest-alert-stop-ui.md`

Report must include:

- Files changed.
- Exact UI state changes.
- Whether any business/timer code was touched.
- Verification commands and results.
- Any remaining risk for thread 1 or thread 7.

## After thread 6

Overall design should:

1. Review thread 1, thread 6, and thread 7 reports.
2. Run final `assembleDebug` and `testDebugUnitTest`.
3. If thread 7 real-device matrix is acceptable, bump to `v1.1.25 / versionCode 27`.
4. Create `packages/release-chain/v1.1.25/`.
5. Copy APK and write `CHANGELOG.md`.
6. Do not delete old APKs.
