# Debug Selftest Clean V2

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\1-training-timer\20260504-single-vibration-selftest\20260504-213503-debug-selftest-clean-v2
- Device: bbc478f4
- Scenario: debug 15s rest -> HOME -> wait 35s -> MainActivity extra_stop_alert=true

## Key log lines
05-04 21:35:05.504 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=189706481
05-04 21:35:05.561 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=102545505
05-04 21:35:15.636 I/VibratorManagerServiceExtImpl( 3496): vibrate is uid=1000, pid=3496, opPkg=android, effect=Mono{mEffect=Composed{segments=[Step{amplitude=0.0, frequencyHz=0.0, duration=0}, Step{amplitude=-1.0, frequencyHz=0.0, duration=200}, Step{amplitude=0.0, frequencyHz=0.0, duration=200}, Step{amplitude=-1.0, frequencyHz=0.0, duration=0}], repeat=-1}}, attributes=VibrationAttributes{mUsage=NOTIFICATION, mAudioUsage= USAGE_NOTIFICATION, mFlags=0}, reason=Notification (com.codex.streetstrength 10421) , token=39382921
05-04 21:35:44.561 V/AlarmManager( 3496): sending alarm Alarm{70395c3 type 0 origWhen 1777901720505 whenElapsed 2142341044 com.codex.streetstrength} uid 10421 whenElapsed 2142341044 windowLength 0 maxWhenElapsed 2142341044 repeatInterval 0 action com.codex.streetstrength.timer.FINISH component ComponentInfo{com.codex.streetstrength/com.codex.streetstrength.timer.RestTimerReceiver} flags 0x9 procName com.codex.streetstrength
05-04 21:35:44.580 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=189706481
05-04 21:35:44.585 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=1000, pid=3496, usageFilter=-15, token=39382921
05-04 21:35:44.596 I/RestTimerReceiver(25119): Received rest finish alarm for timer 96
05-04 21:35:44.603 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->removeLiveAlert: 0|com.codex.streetstrength|401|null|10421
05-04 21:35:44.603 I/PluginSeedling--Track( 8145): RequestHandler-->notificationRemoved 0|com.codex.streetstrength|401|null|10421
05-04 21:35:44.607 I/ActivityManager( 3496): Background started FGS: Allowed [callingPackage: com.codex.streetstrength; callingUid: 10421; uidState: TOP ; uidBFSL: [BFSL]; intent: Intent { act=com.codex.streetstrength.timer.FINISH xflg=0x4 cmp=com.codex.streetstrength/.timer.RestTimerService (has extras) mCallingUid=10421 }; code:PROC_STATE_TOP; tempAllowListReason:<ac166bd com.codex.streetstrength.timer.FINISH/u0,reasonCode:ALARM_MANAGER_ALARM_CLOCK,duration:10000,callingUid:10421>; allowWiu:12; targetSdkVersion:34; callerTargetSdkVersion:34; startForegroundCount:0; bindFromPackage:null: isBindService:false]
05-04 21:35:44.725 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=102545505
05-04 21:35:44.749 I/VibratorManagerServiceExtImpl( 3496): vibrate is uid=10421, pid=25119, opPkg=com.codex.streetstrength, effect=Mono{mEffect=Composed{segments=[Step{amplitude=0.0, frequencyHz=0.0, duration=0}, Step{amplitude=-1.0, frequencyHz=0.0, duration=220}, Step{amplitude=0.0, frequencyHz=0.0, duration=140}, Step{amplitude=-1.0, frequencyHz=0.0, duration=320}, Step{amplitude=0.0, frequencyHz=0.0, duration=180}, Step{amplitude=-1.0, frequencyHz=0.0, duration=540}], repeat=0}}, attributes=VibrationAttributes{mUsage=ALARM, mAudioUsage= USAGE_UNKNOWN, mFlags=0}, reason=null, token=209738522
05-04 21:35:44.752 D/NotificationService--OplusNotificationTrackHelper( 3496): PostNotification : {channel_name=计时结束, notification_type=others, app_name=ad0b2f0ee95d7eb7586d5d533a5e44688a738e063f64f03d52535162dfdb3865, push_id=null, importance=4, notification_id=401, system_state=5, channel_id=rest_finished_silent_v2, pkg=d6f6087426fd3659b99ebccd37b5831e66b7e72d08254fc4b60007277d2301b5, post_time=2026-05-04 21:35:44, notification_source=local}
