# Background Timer Evidence Summary

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env\20260502-background-timer-crash-20260502-015034
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
- FATAL EXCEPTION: False

## App Crash Attribution
- FATAL EXCEPTION present: False
- First app stack frame present: True
- First app stack frame: 05-01 17:56:11.819 E/AndroidXTracer( 3310): 	at com.codex.streetstrength.timer.BackgroundRestTimerUiFlowInstrumentedTest$seedRule$1$1.evaluate(BackgroundRestTimerUiFlowInstrumentedTest.kt:45)
- First production app stack frame present: False
- First production app stack frame: Not found in captured logcat.

## Required Follow-up
- If this capture was not taken during a real click crash, hand this folder plus a crash-time logcat to thread 1 before business-code changes.
- If a crash-time first app stack frame points to Room or repository code, involve thread 4.
- If a crash-time first app stack frame points to task ordering or set index calculation, involve thread 2.
