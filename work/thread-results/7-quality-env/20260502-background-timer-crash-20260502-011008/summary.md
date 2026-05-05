# Background Timer Evidence Summary

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env\20260502-background-timer-crash-20260502-011008
- Device serial: emulator-5584
- Scenario: background-timer-crash
- Receiver instrumentation: False
- UI flow instrumentation: True
- Capture seconds: 0

## Global Logcat Pattern Check
- RestTimerReceiver: False
- ForegroundServiceStartNotAllowedException: False
- SecurityException: False
- SQLiteException: False
- IllegalStateException: True
- IndexOutOfBoundsException: False
- NullPointerException: True
- FATAL EXCEPTION: True

## App Crash Attribution
- FATAL EXCEPTION present: True
- First app stack frame present: True
- First app stack frame: 05-01 17:11:05.477 E/AndroidRuntime( 2990): 	at com.codex.streetstrength.timer.RestTimerService$Companion.stop(RestTimerService.kt:422)

## Required Follow-up
- If this capture was not taken during a real click crash, hand this folder plus a crash-time logcat to thread 1 before business-code changes.
- If a crash-time first app stack frame points to Room or repository code, involve thread 4.
- If a crash-time first app stack frame points to task ordering or set index calculation, involve thread 2.
