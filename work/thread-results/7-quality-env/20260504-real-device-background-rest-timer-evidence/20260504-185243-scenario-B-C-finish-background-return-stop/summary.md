# Real Device Background Rest Timer Evidence Summary

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env\20260504-real-device-background-rest-timer-evidence\20260504-185243-scenario-B-C-finish-background-return-stop
- Device serial: bbc478f4
- Scenario: scenario-B-C-finish-background-return-stop
- Capture seconds: 240

## Logcat Pattern Check
- RestTimer: False
- RestTimerService: False
- RestTimerReceiver: False
- Vibrator: True
- AlarmManager: True
- ForegroundService: False
- AndroidRuntime: False
- FATAL: False
- Exception: True
- BackgroundServiceStartNotAllowed: False
- ForegroundServiceStartNotAllowed: False
- SecurityException: False
- Exact alarm: False

## Crash Attribution
- FATAL present: False
- First production app stack frame present: False
- First production app stack frame: Not found in captured logcat.

## Required Review
- Check after-dumpsys-alarm.txt for com.codex.streetstrength timer alarms.
- Check after-dumpsys-services.txt for RestTimerService state.
- Check after-dumpsys-notification.txt and after-dumpsys-vibrator.txt for alert/vibration state.
- If failure reproduces, hand this folder to 1-training-timer.
