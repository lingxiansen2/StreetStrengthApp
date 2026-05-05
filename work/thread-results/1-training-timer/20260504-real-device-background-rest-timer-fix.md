# 2026-05-04 真机后台休息计时与震动停止修复

## 结论

已按 `20260504-real-device-background-timer-vibration-share.md` 完成线程 1 范围内的代码修复：

- 训练页和 debug 自测页的剩余休息时间改为基于稳定墙钟结束时间计算：`createdAt + durationSec * 1000 - System.currentTimeMillis()`。
- 运行中通知改为使用系统 notification chronometer，以 `endAtWallClockMs` 作为 `setWhen(...)`，不再依赖后台 service coroutine 每秒刷新通知文本。
- `AlarmManager` 调度仍使用正式 `RestTimerReceiver` broadcast，并同步传入同一个墙钟结束时间用于 alarm-clock 展示。
- 新增统一停止入口 `RestTimerController.stopRestAlert(...)`，集中取消 alarm、通知、持续震动和 `RestTimerService`。
- “进入下一组/跳过休息/结束训练/通知关闭/通知返回/训练页恢复后 timer 已清理”都走统一停止入口。
- debug-only 自测页更新为“真机后台休息提醒自测”，15 秒倒计时，走正式 `RestTimerService -> AlarmManager -> RestTimerReceiver -> 通知/持续震动` 链路。

本线程没有直接操作真机；真机 `bbc478f4` 的 A-E 场景仍需 7 线程按任务单采集。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerClock.kt`
  - 新增稳定结束时间与剩余时间计算入口。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerController.kt`
  - 新增统一 `stopRestAlert(context)`，集中停止 alarm、notification、vibration、service。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlarmScheduler.kt`
  - `schedule(...)` 支持 `endAtWallClockMs`，`setAlarmClock(...)` 使用稳定墙钟结束时间。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - `ACTION_START` 携带并读取 `EXTRA_END_AT_WALL_CLOCK_MS`。
  - 运行中通知使用系统 chronometer count-down，不靠后台 ticker 刷新。
  - service 内部 countdown job 不再每秒 notify，只保持轻量等待；到点仍由 alarm/receiver 链路负责。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerReceiver.kt`
  - 通知“关闭提醒”统一调用 `RestTimerController.stopRestAlert(...)`。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/MainActivity.kt`
  - 通知返回训练页携带 stop alert 标记时，统一调用 `RestTimerController.stopRestAlert(...)`。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/StreetStrengthApp.kt`
  - App 启动恢复和 Application watchdog 调度时传递稳定墙钟结束时间。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`
  - UI 剩余时间、过期判断改为基于 `activeTimerEndAtMillis`。
  - 发现 active timer 从有到无时自动调用统一停止入口，覆盖“恢复后已经进入下一动作/下一组”的清理路径。
  - 创建 rest timer 后启动 service 时传递 `endAtWallClockMs`。
- `packages/StreetStrengthApp-source-20260422/app/src/debug/java/com/codex/streetstrength/timer/RestReminderSelfTestActivity.kt`
  - 自测倒计时改为 15 秒真机后台自测文案。
  - 自测剩余时间也使用墙钟结束时间计算。
  - 启动/停止自测均复用正式停止入口和正式 timer 链路。

## 完整链路

休息开始：

1. `TrainingViewModel.completeCurrentSet()` 调用 `TrainingRepository.completeSet(...)`。
2. Repository 持久化 `ActiveRestTimerEntity`，其中已有稳定字段组合：`createdAt` 与 `durationSec`。
3. ViewModel 立即计算 `endAtWallClockMs = createdAt + durationSec * 1000`。
4. `RestTimerService.start(context, timerId, endElapsedMs, endAtWallClockMs)` 先调度 `RestTimerAlarmScheduler.schedule(...)`，再启动 foreground service。

后台触发：

1. `RestTimerAlarmScheduler` 注册 `RestTimerReceiver` broadcast PendingIntent。
2. `AlarmManager.setAlarmClock(...)` 使用同一个 `endAtWallClockMs` 作为触发墙钟时间。
3. App 退后台后，通知倒计时由系统 chronometer 展示，不依赖 Compose 或 service 每秒 notify。
4. 到点后 `RestTimerReceiver.ACTION_FINISH` 收到 timerId。

休息结束：

1. Receiver 先取消旧 alarm。
2. Receiver 在 IO 协程中确认 timer 状态：RUNNING 标记为 FIRED，FIRED 允许重复提醒，CANCELLED/null 不提醒。
3. Receiver 先调用 `RestTimerAlert.showFinishedAlert(...)`，即使 FGS 启动受限，也优先发休息结束通知和持续震动。
4. Receiver 再尝试 `RestTimerService.startFinishFromAlarm(...)`，由 FGS 承接正式前台通知。

停止震动：

统一入口为：

```kotlin
RestTimerController.stopRestAlert(context)
```

该入口会：

- `RestTimerAlarmScheduler.cancel(...)`
- `RestTimerAlert.stop(...)`
- `RestTimerService.stop(...)`

已覆盖路径：

- 训练页点击“开始下一项”。
- 训练页点击“跳过休息”。
- 训练页点击“结束训练”。
- 训练页恢复后发现 active timer 已被清理。
- 通知“关闭提醒”action。
- 通知返回训练页的 content/open action。
- debug 自测页启动前清理旧提醒、点击“关闭震动”。

进入下一组后，`TrainingRepository.clearRestTimer(...)` 会将旧 active rest record 标记为 `CANCELLED`；结束/完成 session 时 repository 会删除该 session 的 rest timers。

## 验证命令

本机 `verify-local.cmd -NoClean` 首次遇到 Kotlin/Gradle daemon 与 kapt 增量缓存异常：

- `StreamCorruptedException: unexpected EOF in middle of data block`
- `AccessDeniedException` 删除旧 Kotlin class
- kapt incrementalData 缺失

这不是业务代码编译错误。随后停掉 Gradle daemon，并用禁用 Kotlin daemon/增量编译的方式验证通过。

已通过：

```powershell
cd E:\Workspace\GitHub\StreetStrengthApp\packages\StreetStrengthApp-source-20260422
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebug
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" testDebugUnitTest
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebugAndroidTest
```

结果：

- `assembleDebug`: PASS
- `testDebugUnitTest`: PASS
- `assembleDebugAndroidTest`: PASS

## 仍需 7 线程真机验证

必须在 `bbc478f4 / model:PKG110` 上验证，不使用 `97257126520013`：

- 场景 A：15 秒休息，App 前台到点震动，点击进入下一组后震动停止。
- 场景 B：15 秒休息，开始后立刻切桌面，等待 25 秒，后台应按时通知/震动。
- 场景 C：后台到点震动后，从通知或 App 回到训练页，点击进入下一组，震动必须停止。
- 场景 D：休息结束震动后，点击结束训练，不能闪退，震动必须停止。
- 场景 E：锁屏后重复 B/C。

建议重点抓取：

- `RestTimerAlarmScheduler`
- `RestTimerReceiver`
- `RestTimerService`
- `RestTimerAlert`
- `ForegroundServiceStartNotAllowedException`
- `SecurityException`
- `Vibrator`
- `AlarmManager`

还需记录真机状态：

- Android 版本、通知权限、精确闹钟权限。
- 电池优化、后台运行、自启动、通知限制。
- `dumpsys alarm` 中是否出现 `com.codex.streetstrength.timer.FINISH`。
- `dumpsys activity services com.codex.streetstrength` 中休息期间/到点后 service 状态。

## 风险

- 若厂商系统禁止后台广播触发后的震动或限制 FGS，receiver 已优先直接通知/震动，但仍需要真机 logcat/dumpsys 判断是否被系统策略拦截。
- 当前未做 Room schema 变更；`endAtMillis` 由现有 `createdAt + durationSec` 派生，避免新增 migration 风险。
- 如果用户手动修改系统时间，UI/通知墙钟显示会跟随系统时间变化；Alarm fallback 仍保留 elapsed realtime 调度信息。
