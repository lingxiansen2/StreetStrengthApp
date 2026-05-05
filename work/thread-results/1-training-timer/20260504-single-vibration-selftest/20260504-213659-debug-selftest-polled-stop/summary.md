# Debug Selftest Polled Stop

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\1-training-timer\20260504-single-vibration-selftest\20260504-213659-debug-selftest-polled-stop
- Device: bbc478f4
- Scenario: debug 15s rest -> HOME -> poll until RestTimerReceiver + app vibration -> wait 5s -> MainActivity extra_stop_alert=true
- Poll result: fired=True

## Key log lines
05-04 21:38:02.555 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=1000, pid=3496, usageFilter=-15, token=39382921
05-04 21:38:08.659 I/VibratorManagerServiceExtImpl( 3496): vibrate is uid=1000, pid=3496, opPkg=android, effect=Mono{mEffect=Composed{segments=[OplusVibrationEffectSegment{mEffectId=49, mEffectStrength=-1, mRingtonePath=''}], repeat=-1}}, attributes=VibrationAttributes{mUsage=TOUCH, mAudioUsage= USAGE_UNKNOWN, mFlags=0}, reason=HapticFeedback (android 1000) (FP Authenticate Successfully), token=39382921
05-04 21:39:51.420 V/AlarmManager( 3496): sending alarm Alarm{602afe6 type 0 origWhen 1777901838807 whenElapsed 2142459346 com.codex.streetstrength} uid 10421 whenElapsed 2142459346 windowLength 0 maxWhenElapsed 2142459346 repeatInterval 0 action com.codex.streetstrength.timer.FINISH component ComponentInfo{com.codex.streetstrength/com.codex.streetstrength.timer.RestTimerReceiver} flags 0x9 procName com.codex.streetstrength
05-04 21:39:51.700 I/VibratorManagerServiceExtImpl( 3496): vibrate is uid=10421, pid=25119, opPkg=com.codex.streetstrength, effect=Mono{mEffect=Composed{segments=[Step{amplitude=0.0, frequencyHz=0.0, duration=0}, Step{amplitude=-1.0, frequencyHz=0.0, duration=220}, Step{amplitude=0.0, frequencyHz=0.0, duration=140}, Step{amplitude=-1.0, frequencyHz=0.0, duration=320}, Step{amplitude=0.0, frequencyHz=0.0, duration=180}, Step{amplitude=-1.0, frequencyHz=0.0, duration=540}], repeat=0}}, attributes=VibrationAttributes{mUsage=ALARM, mAudioUsage= USAGE_UNKNOWN, mFlags=0}, reason=null, token=26997350
05-04 21:39:55.813 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=1000, pid=3496, usageFilter=-15, token=39382921
05-04 21:39:58.892 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=1200968
05-04 21:39:58.900 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->removeLiveAlert: 0|com.codex.streetstrength|401|null|10421
05-04 21:39:58.900 I/PluginSeedling--Track( 8145): RequestHandler-->notificationRemoved 0|com.codex.streetstrength|401|null|10421
05-04 21:39:58.911 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=26997350
05-04 21:40:03.649 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=1000, pid=3496, usageFilter=-15, token=39382921
