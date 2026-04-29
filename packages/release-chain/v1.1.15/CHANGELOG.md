# v1.1.15

## APK

- `app-debug-20260428-v1.1.15.apk`

## 修复内容

- 修复后台休息倒计时结束后不提醒、只有回到 App 才触发的问题。
- 休息结束闹钟改用更适合用户可见提醒的 `AlarmManager.setAlarmClock()` 路径；无法使用精确闹钟时仍保留 `setAndAllowWhileIdle()` 降级兜底。
- `RestTimerReceiver` 收到系统闹钟广播后会直接发出高优先级“休息结束”通知并启动持续震动，不再完全依赖后台启动 `ForegroundService`。
- 保留 `ForegroundService` 作为状态同步和数据库标记兜底，若系统允许后台服务启动，会继续把休息计时状态标记为已结束。
- 通知“关闭提醒”和点击进入 App 都会停止持续震动。
- 新增 `USE_EXACT_ALARM` 权限声明，并继续保留通知、震动、精确闹钟权限。

## 本地验证

- `clean assembleDebug` 通过。
- `testDebugUnitTest` 通过。
- `assembleDebugAndroidTest` 通过。
- 本地 Android 14 模拟器 `StreetStrengthApi34 / emulator-5584` 启动通过。
- 模拟器 instrumentation 通过：`AlarmManager.setAlarmClock -> RestTimerReceiver -> 休息结束通知`，结果 `OK (1 test)`。
- APK 解析确认：`versionName=1.1.15`，`versionCode=17`。
- APK 解析确认包含：`POST_NOTIFICATIONS`、`VIBRATE`、`SCHEDULE_EXACT_ALARM`、`USE_EXACT_ALARM`、`FOREGROUND_SERVICE`。

## 说明

- 没有删除旧版本 APK。
- 没有使用外接 5G 移动终端做安装验证。
- 模拟器不能真实验证手机震动马达手感，只验证系统闹钟、广播和通知链路。
