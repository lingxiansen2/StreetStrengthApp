# 2026-05-02 后台休息计时与休息结束点击闪退修复

## 结论

已修复 2026-05-02 实测的两类回归：

- 真实训练进入休息后，会在 rest timer 持久化后立即调度后台 `AlarmManager` broadcast；Application 级 watchdog 也会观察持久化 RUNNING timer 并补调度同一条后台 alarm，不再依赖 Compose 页面存活或 UI `LaunchedEffect`。
- 休息结束后的“开始下一项/结束训练”流程已做幂等处理，先停止 alarm/通知/震动/service，再更新 session/timer 状态和导航，避免重复点击、旧 timer、已 fired timer、session 已结束时崩溃。

最终 emulator 证据通过：

- `instrumentation-ui-flow.txt`: `OK (1 test)`
- `summary.md`: `RestTimerReceiver: True`
- `summary.md`: `ForegroundServiceStartNotAllowedException: False`
- `summary.md`: `FATAL EXCEPTION: False`
- `summary.md`: `First production app stack frame present: False`

证据目录：

`E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env\20260502-background-timer-crash-20260502-040526`

## 根因

1. 休息 timer 的后台到点能力之前容易落在 UI 侧事件/Compose 生命周期之后，真实点击后快速退后台时，证据里没有稳定留下 `timer.FINISH` alarm 或 `RestTimerReceiver` 触发记录。
2. `RestTimerService.stop()` 以前有通过 foreground service 动作停止的风险；停止路径如果和结束训练/跳过休息/通知关闭交错，容易出现 service 生命周期和通知/震动清理不一致。
3. Receiver 到点后不应只依赖 service coroutine；receiver 自身需要优先把 timer 标成 FIRED、发出通知/持续震动，再尽力启动 FGS 承接正式前台通知。
4. 回前台看到过期 RUNNING timer 时，UI 应补偿为“休息结束，等待手动继续”，不能继续停在倒计时，也不能自动进入下一组。

## 修改文件

- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\timer\RestTimerAlarmScheduler.kt`
  - 新增统一后台 alarm 调度器。
  - 使用 `AlarmManager.setAlarmClock(...)` 调度 `RestTimerReceiver` broadcast，保留 `setAndAllowWhileIdle`/`setExact` fallback。
  - 提供 `schedule/cancel/dispatchFinish`，统一 PendingIntent action、requestCode、flags。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\timer\RestTimerReceiver.kt`
  - `ACTION_FINISH` 收到后先取消 alarm、校验并持久化 timer 为 FIRED。
  - 即使 FGS 启动失败，也先走 `RestTimerAlert.showFinishedAlert()`，触发通知和持续震动。
  - `ACTION_STOP_ALERT` 统一取消 alarm、震动、通知和 service。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\timer\RestTimerService.kt`
  - `start()` 先调度持久化 alarm，再启动 FGS。
  - `stop()` 改为 `stopService()`，避免 STOP 动作再触发 `startForegroundService` 的 5 秒前台化风险。
  - 运行中通知只负责展示倒计时；到点能力由 alarm/receiver 承担。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\StreetStrengthApp.kt`
  - App 启动恢复最新 active rest timer。
  - 新增 Application 级 watchdog：观察最新 RUNNING/FIRED rest timer，RUNNING future timer 自动补调度 alarm，过期 RUNNING timer 自动 dispatch receiver。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\data\local\TrainingDao.kt`
  - 新增 `observeLatestActiveRestTimer()`。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\data\repository\TrainingRepository.kt`
  - `completeSet()` 支持在 rest timer 插入后回传创建结果。
  - 新增 timer 查询/观察方法，供 receiver、service、app watchdog 使用。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\ui\training\TrainingScreen.kt`
  - 移除依赖 Compose `LaunchedEffect(activeTimer...)` 的启动链路。
  - `completeCurrentSet()` 在创建 rest timer 后立即启动正式 timer 基础设施。
  - `skipRest/endWorkout` 幂等化，先清理 alarm/通知/震动/service，再更新 repository 和导航。
  - 过期 RUNNING timer 回前台补偿为 FIRED，UI 显示“休息结束，等待手动继续”。
- `packages\StreetStrengthApp-source-20260422\app\src\main\java\com\codex\streetstrength\MainActivity.kt`
  - 通知入口带 `EXTRA_STOP_ALERT` 时同步取消 alarm、通知、震动和 service。
- `packages\StreetStrengthApp-source-20260422\app\src\debug\java\com\codex\streetstrength\timer\RestReminderSelfTestActivity.kt`
  - debug-only 休息提醒自测仍走正式 `RestTimerService -> AlarmManager -> RestTimerReceiver -> 通知/震动` 链路。
- `packages\StreetStrengthApp-source-20260422\app\src\androidTest\java\com\codex\streetstrength\timer\BackgroundRestTimerUiFlowInstrumentedTest.kt`
  - UI flow 先确认进入“休息中”再退后台。
  - 30 秒 rest，后台等待 90 秒，回前台验证“开始下一项”和“结束训练”不崩溃。

## 验证结果

已运行：

```powershell
cd E:\Workspace\GitHub\StreetStrengthApp\packages\StreetStrengthApp-source-20260422
.\scripts\verify-local.cmd -NoClean
.\scripts\gradlew-local.cmd --max-workers=1 assembleDebugAndroidTest
.\scripts\capture-background-timer-evidence.cmd -StartEmulator -RunUiFlowInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584
```

结果：

- `verify-local.cmd -NoClean`: PASS
  - `assembleDebug`: PASS
  - `testDebugUnitTest`: PASS
- `assembleDebugAndroidTest`: PASS
- `capture-background-timer-evidence`: PASS
  - 第一次在 Codex sandbox 内失败于 `adb` 无法创建 `C:\Users\CodexSandboxOnline\.android`，未进入业务测试。
  - 提升权限重跑同一命令后通过。

关键证据：

```text
RestTimerAlarmScheduler: Scheduling rest finish receiver alarm for timer 5
RestTimerAlarmScheduler: Scheduled rest finish receiver alarm for timer 5
StreetStrengthApp: Scheduling persisted rest timer 5 from app watchdog
RestTimerReceiver: Received rest finish alarm for timer 5
```

`after-dumpsys-alarm.txt` 中可见：

```text
*walarm*:com.codex.streetstrength.timer.FINISH
u0a192:com.codex.streetstrength ... 1 wakeups, 1 alarms
```

`instrumentation-ui-flow.txt`：

```text
OK (1 test)
```

没有发现生产 App crash：

- `FATAL EXCEPTION: False`
- `First production app stack frame present: False`
- 未发现 `ForegroundServiceStartNotAllowedException`

## 仍需真机确认

- Android 12+ / 13+ / 14+ 真机在通知权限关闭、精确闹钟权限关闭、锁屏、Doze、厂商省电策略下的提醒延迟表现。
- 真机震动硬件和系统勿扰模式对持续震动的影响。
- 用户从通知“关闭提醒”和从 App 内“开始下一项/结束训练”同时操作时的体验细节；当前代码路径已按幂等处理。

本次日志没有生产 Room/Repository crash 堆栈，暂不需要 `4-data-storage` 介入。
