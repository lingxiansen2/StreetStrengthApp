# 训练执行与计时线程：Debug 休息提醒自测

## 目标

- 增加 debug-only 的“休息提醒自测”入口。
- 提供 10 秒休息倒计时，不依赖当天是否存在未完成计划。
- 倒计时结束走正式 `RestTimerService`、`RestTimerReceiver`、通知、持续震动链路。
- 支持手动关闭提醒和震动。

## 改动

- 新增 `app/src/debug/AndroidManifest.xml`
  - debug 构建额外注册 `RestReminderSelfTestActivity`。
  - 该 Activity 使用独立 launcher 入口，release source set 不包含此入口。
- 新增 `app/src/debug/java/com/codex/streetstrength/timer/RestReminderSelfTestActivity.kt`
  - 启动时请求 Android 13+ 通知权限。
  - 点击“启动 10 秒自测”后创建隔离的 debug 计时数据，并调用正式 `RestTimerService.start(...)`。
  - 同时安排一个指向正式 `RestTimerReceiver` 的 10 秒 `AlarmManager.setAlarmClock(...)` 自测闹钟，确保 Receiver 路径参与。
  - 点击“关闭震动”会取消自测 Receiver 闹钟、停止 `RestTimerAlert`、停止 `RestTimerService`，并把当前 debug timer 标记为 `CANCELLED`。

## 验证

- `.\scripts\gradlew-local.cmd --no-daemon --max-workers=1 assembleDebug`：通过。
- `.\scripts\gradlew-local.cmd --no-daemon --max-workers=1 testDebugUnitTest`：通过。
- `.\scripts\gradlew-local.cmd --no-daemon --max-workers=1 assembleDebugAndroidTest`：通过。

## 注意

- 自测会写入 debug 标记数据，用于满足 `active_rest_timers` 的外键约束；计划日期会选择当前日期 200 年后的空闲日期，避免依赖今日计划。
- 未执行模拟器端手动点击自测入口后的 10 秒通知和震动验证；当前只完成构建与单测验证。
