# 线程 1 交付报告：正式训练后台休息不震动补修

时间：2026-05-04

## 背景

用户正式训练复测时反馈：进入休息后切后台，超过结束时间很久仍不震动，只有点回 App 后才震动。debug-only 自测曾通过，但正式训练点击链路仍存在后台启动和到点提醒缺口。

## 判断

debug 自测能触发说明 `RestTimerReceiver -> RestTimerService -> 通知/震动` 基线可用。正式训练失败更像是：

- 用户点击“完成本组”后很快切后台，`completeSet()` 的持久化和 `startForegroundService()` 可能在 App 已后台后才执行。
- 如果 finish 到点时 `RestTimerReceiver` 成功收到 alarm，但后台启动 finish FGS 被系统限制，原 fallback 只发通知、不震动，会造成“进 App 后才震动”的体感。
- 运行中 service coroutine 和 UI ticker 都不能作为唯一后台到点来源。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`
  - 正式训练点击“完成本组”后，如果下一步需要休息，先立即 `RestTimerService.prepare()` 预启动前台计时服务。
  - rest timer 真正持久化后再用正式 `RestTimerService.start()` 更新 timerId/endElapsed 并调度 alarm。
  - 如果 set 完成失败或最终没有创建 rest timer，停止预启动服务，避免悬挂通知。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - 新增 `ACTION_PREPARE`/`prepare()`，用于正式训练点击时抢在 Activity 后台前启动 FGS。
  - prepare 状态 30 秒内未收到正式 timer 会自动停止。
  - 保留到点后经 `RestTimerAlarmScheduler.dispatchFinish()` 进入正式 Receiver 的 fallback。
  - 增加 finish service 启动成功日志，便于只抓 app tag 定位。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerReceiver.kt`
  - 只有当 `RestTimerService.startFinishFromAlarm()` 失败时，Receiver 才接管通知和持续震动。
  - 正常路径仍由 Service 启动震动，避免恢复“双震动 token”问题。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlert.kt`
  - `startContinuousVibration()` 增加进程内幂等保护，避免 alarm 主/备份接近同时送达时重复启动震动。
  - `cancelVibration()` 会清除幂等状态。

## 验证

本地验证通过：

```powershell
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

真机 `bbc478f4` 已安装最新 debug APK。

debug-only 10 秒后台自测通过，证据目录：

`work/thread-results/1-training-timer/20260504-formal-flow-fgs-prewarm-fallback`

关键日志：

- `23:28:19` timer 108 调度 exact elapsed alarm 和 alarm clock backup。
- `23:28:29` App 在后台时 `RestTimerReceiver` 收到 finish alarm。
- `23:28:29` finish service 启动成功。
- `23:28:29` `RestTimerAlert` 启动持续震动。
- `23:28:32` 点击关闭后取消持续震动。

## 仍需复测

- 需要用户用正式训练路径再次验证：完成本组 -> 进入休息 -> 立刻 HOME/锁屏 -> 到点不回 App 也应震动。
- 物理机脚本禁止跑 instrumentation，所以没有在 `bbc478f4` 自动执行正式训练 UI 流程；本次真机自动验证覆盖的是同一计时/Receiver/Service/震动链路。
