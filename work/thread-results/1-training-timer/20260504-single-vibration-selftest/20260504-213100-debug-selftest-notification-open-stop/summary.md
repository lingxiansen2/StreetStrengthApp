# Debug Selftest Single Vibration Capture

- Output directory: E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\1-training-timer\20260504-single-vibration-selftest\20260504-213100-debug-selftest-notification-open-stop
- Device: bbc478f4
- Stop path: MainActivity extra_stop_alert=true (same path used by notification content/open intent)

## Key log lines
05-04 21:31:02.883 D/NotificationShadeWindowController( 8145): setIsAboutToOccluded:false, needApply:false
05-04 21:31:04.960 I/AidlLazyServiceRegistrar(23750): Process has 0 (of 1 available) client(s) in use after notification artd has clients: 0
05-04 21:31:05.103 D/UAH-UahAdaptHelper( 3496): adaptSetNotification identity = OplusUAwareInputHelpersrc = 1000 , type 0 ,p1 = -1 ,p2 = -1 ,p3 = -1 ,p4 =
05-04 21:31:05.119 D/UAH-UahAdaptHelper( 3496): adaptSetNotification identity = OplusUAwareInputHelpersrc = 1000 , type 1 ,p1 = -1 ,p2 = -1 ,p3 = -1 ,p4 =
05-04 21:31:05.128 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=62776331
05-04 21:31:05.162 I/ActivityManager( 3496): Background started FGS: Allowed [callingPackage: com.codex.streetstrength; callingUid: 10421; uidState: TOP ; uidBFSL: [BFSL]; intent: Intent { act=com.codex.streetstrength.timer.START xflg=0x4 cmp=com.codex.streetstrength/.timer.RestTimerService (has extras) mCallingUid=10421 }; code:PROC_STATE_TOP; tempAllowListReason:<null>; allowWiu:12; targetSdkVersion:34; callerTargetSdkVersion:34; startForegroundCount:0; bindFromPackage:null: isBindService:false]
05-04 21:31:05.194 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=33061599
05-04 21:31:05.199 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->onNotificationChannelModified: com.codex.streetstrength 0 计时结束 1
05-04 21:31:05.203 D/ActivityManager( 3496): sync unfroze 983 com.oplus.notificationmanager for 4
05-04 21:31:05.206 D/NotificationService--OplusNotificationFixHelper( 3496): fixAppIcon: pkg=com.codex.streetstrength
05-04 21:31:05.206 D/NotificationService--OplusNotificationFixHelper( 3496): fixAppIcon: appIcon = null, pkg = com.codex.streetstrength
05-04 21:31:05.245 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->onNotificationChannelModified: com.codex.streetstrength 0 计时结束 2
05-04 21:31:05.255 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->onNotificationChannelModified: com.codex.streetstrength 0 计时结束 2
05-04 21:31:05.473 I/metis_v2_SettingUtils(25509): switchObserver, onChange: false uri:content://settings/secure/enabled_notification_listeners flags:32769
05-04 21:31:06.212 E/LockScreenNotificationDispatcherImp( 8145): updateNotificationListOnKg size:0
05-04 21:31:06.216 I/SystemUi--Notification( 8145): LockScreenNotificationDispatcherImp-->updateNotificationListOnKg resultCode: 0
05-04 21:31:06.711 D/ActivityManager( 3496): Async freezing 983 com.oplus.notificationmanager
05-04 21:31:06.996 W/Settings( 3496): Setting notification_bubbles has moved from android.provider.Settings.Global to android.provider.Settings.Secure, returning read-only value.
05-04 21:31:07.438 I/SystemUi--Notification( 8145): RowMenuPanelController-->dismissPopupMenu:currentPanelContainer =null, originNotificationRonull, showingNotificationRow=null, cause=close system dialogs, isPopupMenuShowing=false, isNeedAnnotation=true
05-04 21:31:07.438 I/SystemUi--Notification( 8145): OplusCustomPopupMenuController-->dismissPopupMenu: close system dialogs, isNeedAnnotation: true
05-04 21:31:07.470 D/OplusAppStartupManager( 3496): prevent restart service, pkgName = com.oplus.notificationmanager, scenePriority = -1
05-04 21:31:07.470 I/sensors-hal( 2034): send_sync_sensor_request:475, wait for notification of response
05-04 21:31:07.492 I/PluginSeedling--Origin( 8145): LiveAlertInteractorImpl-->notifyEntryDataChanged ENTRY_NOTIFICATION, result:, noe: false
05-04 21:31:07.492 I/PluginSeedling--Notification( 8145): NotificationRepository-->onDataChanged, []
05-04 21:31:07.523 D/ActivityManager( 3496): Cancel freezing 983 com.oplus.notificationmanager
05-04 21:31:07.540 D/NotificationCenter(  983): NotificationBackend-->pkg:com.codex.streetstrength,uid:10421,areNotificationsEnabledForPackage:true
05-04 21:31:07.545 D/NotificationContentProvider(  983): queryBadge:querySignal:pkg=com.codex.streetstrength&uid=10421,cost 19,use:selection ,type:0
05-04 21:31:15.215 D/NotificationService--OplusNotificationFixHelper( 3496): fixAppIcon: pkg=com.codex.streetstrength
05-04 21:31:15.215 D/NotificationService--OplusNotificationFixHelper( 3496): fixAppIcon: appIcon = null, pkg = com.codex.streetstrength
05-04 21:31:15.216 D/NotificationService--NotificationRecord( 3496): isSupportRearLight , isRearLight = false mLedRM = false isMultilLed = false
05-04 21:31:15.218 D/NotificationService--OplusNotificationManagerServiceExtImpl( 3496): isForwardToAssistants: true, case : Is local notification
05-04 21:31:15.218 D/NotificationService( 3496): This application enqueue notifications is need postDelayed mcsAssistantDelayTime: 50
05-04 21:31:15.219 D/NotificationService--OplusNavigationManager( 3496): Notification--isSuppressedByDriveMode--userId:0,mode:false
05-04 21:31:15.219 D/NotificationService--OplusNotificationTrackHelper( 3496): PostNotification : {channel_name=休息计时, notification_type=others, app_name=ad0b2f0ee95d7eb7586d5d533a5e44688a738e063f64f03d52535162dfdb3865, push_id=null, importance=3, notification_id=401, system_state=null, channel_id=rest_running, pkg=d6f6087426fd3659b99ebccd37b5831e66b7e72d08254fc4b60007277d2301b5, post_time=2026-05-04 21:31:15, notification_source=local}
05-04 21:31:15.282 D/NotifAttentionHelper( 3496): vibrateLinearmotorIfNeed, null
05-04 21:31:15.282 D/NotificationService--OplusSoundVibrateManager( 3496): Support ringtone vibration : false
05-04 21:31:15.282 I/VibratorManagerServiceExtImpl( 3496): vibrate is uid=1000, pid=3496, opPkg=android, effect=Mono{mEffect=Composed{segments=[Step{amplitude=0.0, frequencyHz=0.0, duration=0}, Step{amplitude=-1.0, frequencyHz=0.0, duration=200}, Step{amplitude=0.0, frequencyHz=0.0, duration=200}, Step{amplitude=-1.0, frequencyHz=0.0, duration=0}], repeat=-1}}, attributes=VibrationAttributes{mUsage=NOTIFICATION, mAudioUsage= USAGE_NOTIFICATION, mFlags=0}, reason=Notification (com.codex.streetstrength 10421) , token=39382921
05-04 21:31:15.283 D/FlashNotifController( 3496): requestStartFlashNotification
05-04 21:31:15.284 I/FlashNotifController( 3496): startFlashNotification: type=1, tag=android
05-04 21:31:15.284 D/FlashNotifController( 3496): Flash notification is disabled
05-04 21:31:15.298 W/QosSceneRecognizer[NotificationScene]( 3496): get pid failed while enterNotificationScene, processInfos is null! uid: -1
05-04 21:31:15.307 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->onRankingApplied
05-04 21:31:15.333 E/LockScreenNotificationDispatcherImp( 8145): updateNotificationListOnKg size:0
05-04 21:31:15.337 I/SystemUi--Notification( 8145): LockScreenNotificationDispatcherImp-->updateNotificationListOnKg resultCode: 0
05-04 21:31:15.385 I/SystemUi--Notification( 8145): OplusNotificationTempla-->resolveContentMargin: marginTop=96 isChildInGroup=false hasSubtitle=true params.topMargin=96
05-04 21:31:15.386 W/SystemUi--Notification( 8145): OplusNotificationGroupExtImpl-->setChildrenExpanded: groupHeader is null
05-04 21:31:15.404 I/PluginSeedling--Track( 8145): RequestHandler-->notificationAdded 0|com.codex.streetstrength|401|null|10421 postTime: 1777901475215
05-04 21:31:15.404 E/LockScreenNotificationDispatcherImp( 8145): updateNotificationListOnKg size:0
05-04 21:31:15.416 I/SystemUi--Notification( 8145): NotificationStackScrollLayoutExtImpl-->bind scrollGridCount 2 o.visibleChildrenCount 3 o.childCount 7
05-04 21:31:15.417 I/SystemUi--Notification( 8145): SeparatePanelAnimation-->updateVisibleChildCount change, currentChildCount: 3 totalCount 4
05-04 21:31:15.418 W/SystemUi--Notification( 8145): OplusNotificationGroupExtImpl-->setChildrenExpanded: groupHeader is null
05-04 21:31:15.418 I/SystemUi--Notification( 8145): LockScreenNotificationDispatcherImp-->updateNotificationListOnKg resultCode: 0
05-04 21:31:15.420 I/SystemUi--Statusbar( 8145): NotifIconContainerViewBinder-->Icons().visibility: 0, notificationsNum: 2, maxIconSize: 999, prevIcons.size: 1, icons.width: 48
05-04 21:31:32.709 W/dumpsys (25785): Thread Pool max thread count is 0. Cannot cache binder as linkToDeath cannot be implemented. serviceName: notification
05-04 21:31:33.218 V/AlarmManager( 3496): sending alarm Alarm{9776654 type 0 origWhen 1777901480130 whenElapsed 2142100668 com.codex.streetstrength} uid 10421 whenElapsed 2142100668 windowLength 0 maxWhenElapsed 2142100668 repeatInterval 0 action com.codex.streetstrength.timer.FINISH component ComponentInfo{com.codex.streetstrength/com.codex.streetstrength.timer.RestTimerReceiver} flags 0x9 procName com.codex.streetstrength
05-04 21:31:33.234 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=62776331
05-04 21:31:33.244 I/PluginSeedling--Origin( 8145): LiveAlertInteractorImpl-->notifyEntryDataChanged ENTRY_NOTIFICATION, result:, noe: false
05-04 21:31:33.244 I/PluginSeedling--Notification( 8145): NotificationRepository-->onDataChanged, []
05-04 21:31:33.246 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=1000, pid=3496, usageFilter=-15, token=39382921
05-04 21:31:33.246 D/NotificationService--OplusBreathLights( 3496):  updateLightsStateLocked 
05-04 21:31:33.246 D/NotificationService--OplusBreathLights( 3496):  scheduleLightsOffTimeoutLocked 
05-04 21:31:33.257 D/NotificationGroupType( 3496): maybeUngroupOplus:after all cleared
05-04 21:31:33.261 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->removeLiveAlert: 0|com.codex.streetstrength|401|null|10421
05-04 21:31:33.261 I/PluginSeedling--Track( 8145): RequestHandler-->notificationRemoved 0|com.codex.streetstrength|401|null|10421
05-04 21:31:33.262 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->onRankingApplied
05-04 21:31:33.264 I/NotificationHistoryJob( 3496): NotifJob#onStartJob 237039804
05-04 21:31:33.265 I/NotificationHistoryJob( 3496): NotifJob#cleanupHistoryFiles 237039804
05-04 21:31:33.277 E/LockScreenNotificationDispatcherImp( 8145): updateNotificationListOnKg size:0
05-04 21:31:33.284 I/SystemUi--Notification( 8145): NotificationStackScrollLayoutExtImpl-->bind scrollGridCount 3 o.visibleChildrenCount 2 o.childCount 6
05-04 21:31:33.284 I/SystemUi--Notification( 8145): SeparatePanelAnimation-->updateVisibleChildCount change, currentChildCount: 4 totalCount 3
05-04 21:31:33.284 I/SystemUi--Notification( 8145): LockScreenNotificationDispatcherImp-->updateNotificationListOnKg resultCode: 0
05-04 21:31:33.284 I/SystemUi--Statusbar( 8145): NotifIconContainerViewBinder-->Icons().visibility: 0, notificationsNum: 1, maxIconSize: 999, prevIcons.size: 2, icons.width: 96
05-04 21:31:33.498 I/RestTimerReceiver(25119): Received rest finish alarm for timer 95
05-04 21:31:33.499 I/VibratorManagerServiceExtImpl( 3496): cancelVibrate uid=10421, pid=25119, usageFilter=-1, token=33061599
05-04 21:31:33.519 I/ActivityManager( 3496): Background started FGS: Allowed [callingPackage: com.codex.streetstrength; callingUid: 10421; uidState: TOP ; uidBFSL: [BFSL]; intent: Intent { act=com.codex.streetstrength.timer.FINISH xflg=0x4 cmp=com.codex.streetstrength/.timer.RestTimerService (has extras) mCallingUid=10421 }; code:PROC_STATE_TOP; tempAllowListReason:<1f5ea16 com.codex.streetstrength.timer.FINISH/u0,reasonCode:ALARM_MANAGER_ALARM_CLOCK,duration:10000,callingUid:10421>; allowWiu:12; targetSdkVersion:34; callerTargetSdkVersion:34; startForegroundCount:0; bindFromPackage:null: isBindService:false]
05-04 21:31:33.550 I/sensors-hal( 2034): send_sync_sensor_request:475, wait for notification of response
05-04 21:31:33.571 I/VibratorManagerServiceExtImpl( 3496): vibrate is uid=10421, pid=25119, opPkg=com.codex.streetstrength, effect=Mono{mEffect=Composed{segments=[Step{amplitude=0.0, frequencyHz=0.0, duration=0}, Step{amplitude=-1.0, frequencyHz=0.0, duration=220}, Step{amplitude=0.0, frequencyHz=0.0, duration=140}, Step{amplitude=-1.0, frequencyHz=0.0, duration=320}, Step{amplitude=0.0, frequencyHz=0.0, duration=180}, Step{amplitude=-1.0, frequencyHz=0.0, duration=540}], repeat=0}}, attributes=VibrationAttributes{mUsage=ALARM, mAudioUsage= USAGE_UNKNOWN, mFlags=0}, reason=null, token=247226718
05-04 21:31:33.574 D/NotificationService--OplusNotificationFixHelper( 3496): fixAppIcon: pkg=com.codex.streetstrength
05-04 21:31:33.574 D/NotificationService--OplusNotificationFixHelper( 3496): fixAppIcon: appIcon = null, pkg = com.codex.streetstrength
05-04 21:31:33.574 D/NotificationService--NotificationRecord( 3496): isSupportRearLight , isRearLight = false mLedRM = false isMultilLed = false
05-04 21:31:33.577 D/NotificationService--OplusNavigationManager( 3496): Notification--isSuppressedByDriveMode--userId:0,mode:false
05-04 21:31:33.577 D/NotificationService--OplusNotificationTrackHelper( 3496): PostNotification : {channel_name=计时结束, notification_type=others, app_name=ad0b2f0ee95d7eb7586d5d533a5e44688a738e063f64f03d52535162dfdb3865, push_id=null, importance=4, notification_id=401, system_state=5, channel_id=rest_finished_silent_v2, pkg=d6f6087426fd3659b99ebccd37b5831e66b7e72d08254fc4b60007277d2301b5, post_time=2026-05-04 21:31:33, notification_source=local}
05-04 21:31:33.577 D/NotificationService--OplusNotificationManagerServiceExtImpl( 3496): isForwardToAssistants: true, case : Is local notification
05-04 21:31:33.577 D/NotificationService( 3496): This application enqueue notifications is need postDelayed mcsAssistantDelayTime: 50
05-04 21:31:33.636 I/SystemUi--Notification( 8145): OplusLiveAlertNotificationsRepository-->onRankingApplied
05-04 21:31:33.636 W/QosSceneRecognizer[NotificationScene]( 3496): get pid failed while enterNotificationScene, processInfos is null! uid: -1
05-04 21:31:33.645 E/LockScreenNotificationDispatcherImp( 8145): updateNotificationListOnKg size:0
05-04 21:31:33.647 I/SystemUi--Notification( 8145): LockScreenNotificationDispatcherImp-->updateNotificationListOnKg resultCode: 0
05-04 21:31:33.661 D/ActivityManager( 3496): Async freezing 983 com.oplus.notificationmanager
05-04 21:31:33.664 D/OplusNotificationDateTimeView( 8145): setTime mSettingTimeMillis 1777901493569, mSettingLocalDateTime 2026-05-04T21:31:00.569
05-04 21:31:33.677 D/OplusNotificationDateTimeView( 8145): setTime mSettingTimeMillis 1777901493569, mSettingLocalDateTime 2026-05-04T21:31:00.569
05-04 21:31:33.681 I/SystemUi--Notification( 8145): OplusNotificationTempla-->resolveContentMargin: marginTop=96 isChildInGroup=false hasSubtitle=true params.topMargin=96
05-04 21:31:33.681 W/SystemUi--Notification( 8145): OplusNotificationGroupExtImpl-->setChildrenExpanded: groupHeader is null
05-04 21:31:33.693 I/PluginSeedling--Track( 8145): RequestHandler-->notificationAdded 0|com.codex.streetstrength|401|null|10421 postTime: 1777901493574
05-04 21:31:33.693 E/LockScreenNotificationDispatcherImp( 8145): updateNotificationListOnKg size:0
05-04 21:31:33.700 I/SystemUi--Notification( 8145): NotificationStackScrollLayoutExtImpl-->bind scrollGridCount 2 o.visibleChildrenCount 3 o.childCount 7
05-04 21:31:33.700 I/SystemUi--Notification( 8145): SeparatePanelAnimation-->updateVisibleChildCount change, currentChildCount: 3 totalCount 4
05-04 21:31:33.701 W/SystemUi--Notification( 8145): OplusNotificationGroupExtImpl-->setChildrenExpanded: groupHeader is null
05-04 21:31:33.701 I/SystemUi--Notification( 8145): LockScreenNotificationDispatcherImp-->updateNotificationListOnKg resultCode: 0
05-04 21:31:33.702 I/SystemUi--Statusbar( 8145): NotifIconContainerViewBinder-->Icons().visibility: 0, notificationsNum: 2, maxIconSize: 999, prevIcons.size: 1, icons.width: 48
05-04 21:31:37.010 W/Settings( 3496): Setting notification_bubbles has moved from android.provider.Settings.Global to android.provider.Settings.Secure, returning read-only value.
