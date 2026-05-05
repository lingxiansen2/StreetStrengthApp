# 线程 1 交付报告：负数运行通知与后台休息结束

时间：2026-05-04

## 背景

用户真机截图显示休息运行通知已经显示 `-00:07`，并反馈“不返回就不会停止，返回之后倒是正常了”。这说明 SystemUI 的运行中 chronometer 已经过点，但后台 finish 链路没有及时触发，回到 App 后才由恢复/补偿逻辑推进到休息结束。

## 根因

1. `RestTimerService.startCountdown()` 原逻辑只 `delay()` 到结束时间，之后没有触发 finish。它不能作为后台到点兜底。
2. 运行中通知使用 `setUsesChronometer(true)` + `setChronometerCountDown(true)`。当 finish 被系统延迟时，SystemUI 会继续把倒计时显示成负数。
3. 真机 `bbc478f4` 复测显示，仅使用 `setAlarmClock` 的短休息倒计时可能被延迟；服务 coroutine 也曾在后台延后到回前台才执行。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - `startCountdown(timerId, endElapsedMs)` 到点后通过 `RestTimerAlarmScheduler.dispatchFinish()` 补发正式 `RestTimerReceiver`，不直接在 coroutine 内完成提醒。
  - 运行中通知改为固定结束时间，不再启用系统 chronometer，避免出现负数倒计时。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlarmScheduler.kt`
  - 调度时同时设置 `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` 主 alarm 和 `setAlarmClock` 备份 alarm。
  - 两个 alarm 使用不同 requestCode，全部指向正式 `RestTimerReceiver`。
  - `cancel()` 同时取消主 alarm 和备份 alarm。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlert.kt`
  - 增加应用内日志，方便只抓 StreetStrength tag 验证开始/取消持续震动，不需要导出整机通知内容。
- `packages/StreetStrengthApp-source-20260422/app/src/debug/java/com/codex/streetstrength/timer/RestReminderSelfTestActivity.kt`
  - debug-only 自测入口改回 10 秒，文案和按钮同步更新。

## 验证

本地验证全部通过：

```powershell
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

真机 `bbc478f4` 已安装最新 debug APK，未卸载、未清数据。

10 秒自测退后台验证通过，证据目录：

`work/thread-results/1-training-timer/20260504-negative-chronometer-fallback-selftest`

关键日志：

- `22:44:13` timer 101 调度 exact elapsed alarm 和 alarm clock backup。
- `22:44:24` App 仍在后台时，`RestTimerService` fallback 通过 `RestTimerAlarmScheduler.dispatchFinish()` 补发正式 Receiver。
- `22:44:24` `RestTimerReceiver` 收到 timer 101，并取消两个 alarm requestCode。
- `22:44:24` `RestTimerAlert` 开始持续震动。
- `22:44:25` 点击自测页“关闭震动”后，`RestTimerAlert` 取消持续震动。

## 仍需确认

- 已验证 debug-only 10 秒链路；建议再用真实训练流程在同一台真机上确认一次“完成本组 -> 休息 -> HOME -> 到点提醒 -> 进入下一组/结束训练”。
- 本次为避免读取个人手机其它 App 通知内容，真机证据只保留 StreetStrength 计时相关 log tag，没有导出全量 `dumpsys notification --noredact`。
