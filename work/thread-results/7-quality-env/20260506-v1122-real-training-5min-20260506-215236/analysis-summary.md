# v1.1.22 real training background timer diagnosis

## Device

- Target device: `bbc478f4`
- Package: `com.codex.streetstrength`
- Tested version: `1.1.22 / versionCode 24`
- Excluded device: `97257126520013` was not used.

## Finding

The app registered the rest-finish alarm correctly, but the OPPO/OPlus power policy delayed both the exact elapsed alarm and the alarm-clock fallback by about 3 days. The foreground service remained alive, but OPlus compression prevented the coroutine fallback from dispatching the finish event while the app was in background.

Key evidence from `dumpsys-alarm-after-5min.txt`:

- Active pending alarm existed for `com.codex.streetstrength.timer.FINISH`.
- `origWhen` was already about 2 minutes overdue.
- `policyWhenElapsed` showed an adjustment of about `+2d23h57m`.

Key evidence from `dumpsys-power-after-5min.txt`:

- `com.codex.streetstrength` was not in the device idle whitelist.
- OPlus proxy state was present for the app uid.

Key evidence from `logcat-after-5min.txt`:

- OPlus repeatedly logged `osense.compress` for `com.codex.streetstrength`.

## Fix applied in v1.1.23

- Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Added battery optimization detection and settings entry in the training reminder card.
- Restart continuous vibration on every finish trigger instead of returning early when the app-side flag is already active.
- Prevent service destruction from canceling the already active finish notification/vibration unless the stop was explicit.
