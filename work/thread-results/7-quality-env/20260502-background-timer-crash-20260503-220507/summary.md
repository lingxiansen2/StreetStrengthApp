# Background Timer Evidence Summary

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env\20260502-background-timer-crash-20260503-220507
- Device serial: emulator-5584
- Device kind: emulator
- Scenario: background-timer-crash
- Physical device explicitly allowed: False
- Receiver instrumentation: False
- UI flow instrumentation: False
- Windowed emulator requested: True
- Emulator restart requested: True
- Capture seconds: 180

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
- First app stack frame present: False
- First app stack frame: Not found in captured logcat.
- First production app stack frame present: False
- First production app stack frame: Not found in captured logcat.

## Required Follow-up
- If this capture was not taken during a real click crash, hand this folder plus a crash-time logcat to thread 1 before business-code changes.
- If this capture was taken on a physical device, hand it to thread 1 even when emulator evidence passes.
- If a crash-time first app stack frame points to Room or repository code, involve thread 4.
- If a crash-time first app stack frame points to task ordering or set index calculation, involve thread 2.
