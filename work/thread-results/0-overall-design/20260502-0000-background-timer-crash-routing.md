# 线程交付报告

- 线程：总体设计与集成线程
- 目标：记录 2026-05-02 用户实测回归，并拆分给负责线程处理
- 修改文件：
  - 无 App 源码修改
- 新增文件：
  - `work/thread-results/0-overall-design/20260502-0000-background-timer-crash-routing.md`
- 当前版本：
  - `1.1.17 / versionCode 19`
- 验证命令：
  - 未运行构建；本次为问题分析和分派
- 验证结果：
  - 不适用
- 需要总体设计集成的点：
  - 等 `1/4/7` 修复和验证完成后，再由总体设计线程升到 `1.1.18` 并生成新 APK
- 风险或未完成事项：
  - 当前缺少本次闪退的 logcat 堆栈，不能只靠猜测修改
  - 后台计时问题必须用真机或可用模拟器做端到端验证，单纯 `assembleDebug` 不能证明修复

## 用户实测问题

问题 A：休息倒计时后台停止

- 进入休息倒计时后切到桌面或其他 App。
- 倒计时走一段时间后停止。
- 只有回到 App 主界面后，倒计时才继续。
- 到点后提醒/震动依旧依赖回到 App 才触发。

问题 B：休息结束后操作闪退

- 震动后点击“进入下一组”直接闪退。
- 或点击“结束训练”直接闪退。

## 总体判断

这不是单纯通知文案问题。当前现象说明至少有一个后台触发源没有真正独立于 UI 生命周期：

- 休息结束事件仍可能被 UI coroutine、Activity 恢复、ViewModel 状态刷新或 Service 进程内逻辑间接触发。
- `AlarmManager -> BroadcastReceiver -> alert/vibration -> training state` 这条链路没有在后台稳定完成。
- 闪退很可能发生在“休息结束后，timer/session/task 状态已经变化，但 UI 点击进入下一组或结束训练时又执行了一次状态迁移”的重复处理场景。
- 如果 logcat 指向 Room/SQLite/foreign key/transaction，则 `4-data-storage` 必须介入。
- 如果 logcat 指向训练索引、任务顺序、下一组计算，则 `2-planner-programming` 必须介入。

## 分派优先级

1. `7-测试、环境与质量线程` 先抓 logcat 和系统状态。
2. `1-训练执行与计时线程` 根据日志修后台计时和点击崩溃。
3. `4-数据、备份与本地存储线程` 审计 session/timer 状态迁移是否幂等。
4. `2-计划编辑与训练编排线程` 仅在下一组计算、组合动作顺序或计划结构异常时介入。

## 给 `7-测试、环境与质量线程` 的提示词

```text
你是 StreetStrengthApp 的【7-测试、环境与质量线程】。请优先为 2026-05-02 的后台计时和闪退问题抓取证据，不要先改业务代码。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env

请先读取：
1. THREAD_PROMPTS.md
2. THREAD_WORKSPLIT.md
3. work/thread-results/0-overall-design/20260502-0000-background-timer-crash-routing.md

需要复现的问题：
- 进入休息倒计时后切后台，倒计时停止，只有回到 App 才继续。
- 震动后点击“进入下一组”或“结束训练”闪退。

最低要求：
- 明确测试设备型号、Android 版本、App 版本、是否开启通知权限、是否开启精确闹钟/闹钟提醒权限、省电策略状态。
- 抓取 logcat，必须覆盖：开始休息、切后台、到点、回 App、点击进入下一组/结束训练、闪退。
- 抓取系统状态：`adb shell dumpsys alarm`、`adb shell dumpsys deviceidle`、`adb shell dumpsys package com.codex.streetstrength`、`adb shell cmd appops get com.codex.streetstrength`。
- 如果使用 ADB，必须确认目标设备，避免误操作不相关设备。

建议命令：
- `adb devices -l`
- `adb -s <serial> logcat -c`
- 复现问题
- `adb -s <serial> logcat -d -v time > work/thread-results/7-quality-env/20260502-background-timer-crash-logcat.txt`
- `adb -s <serial> shell dumpsys alarm > work/thread-results/7-quality-env/20260502-dumpsys-alarm.txt`
- `adb -s <serial> shell dumpsys deviceidle > work/thread-results/7-quality-env/20260502-dumpsys-deviceidle.txt`
- `adb -s <serial> shell dumpsys package com.codex.streetstrength > work/thread-results/7-quality-env/20260502-dumpsys-package.txt`
- `adb -s <serial> shell cmd appops get com.codex.streetstrength > work/thread-results/7-quality-env/20260502-appops.txt`

报告必须说明：
- 后台到点时是否出现 `RestTimerReceiver` 日志。
- 是否有 `ForegroundServiceStartNotAllowedException`、`SecurityException`、`SQLiteException`、`IllegalStateException`、`IndexOutOfBoundsException`、`NullPointerException`。
- 闪退堆栈第一处 App 代码文件和行号。
- 判断应交给 `1`、`4`、`2` 中哪一个继续修。
```

## 给 `1-训练执行与计时线程` 的提示词

```text
你是 StreetStrengthApp 的【1-训练执行与计时线程】。请修复 2026-05-02 实测的后台休息倒计时停止，以及休息结束后点击“进入下一组/结束训练”闪退。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\1-training-timer

请先读取：
1. THREAD_PROMPTS.md
2. THREAD_WORKSPLIT.md
3. work/thread-results/0-overall-design/20260502-0000-background-timer-crash-routing.md
4. `7-quality-env` 新抓取的 logcat 和 dumpsys 报告；如果还没有，请先要求 `7` 补齐，不要盲修

修复方向：
- 休息结束必须由后台可靠源触发，不能依赖 Compose、Activity、ViewModel、页面恢复或 UI coroutine。
- 到点主路径必须是：`AlarmManager` 的 broadcast `PendingIntent` -> manifest `BroadcastReceiver` -> 立即发休息结束通知 + 持续震动 -> 持久化 timer 为 `FIRED/FINISHED_WAITING_USER` -> App 回前台只读取状态。
- 通知倒计时刷新只能是展示层，不能作为到点触发源。
- 检查 `RestTimerService` 是否在后台被系统停掉后丢失到点能力；必要时减少对进程内 `OnAlarmListener` 和 service coroutine 的依赖。
- 检查 `RestTimerReceiver` 是否 manifest 注册正确、action 匹配正确、`PendingIntent` requestCode 不冲突、flags 正确、timerId 正确。
- 检查精确闹钟权限不可用时的降级策略是否仍会后台触发，而不是等 App 恢复。
- 点击“进入下一组”和“结束训练”必须幂等：先停止 alert/vibration，再处理 timer 状态，再推进训练状态；重复点击、旧 timer、已 fired timer、session 已 abandon 都不能崩溃。
- 如果点击崩溃堆栈指向 Repository/Room，请在报告中明确转给 `4-data-storage`。
- 如果点击崩溃堆栈指向下一组/下一项计算，请在报告中明确转给 `2-planner-programming`。

建议增加的内部防线：
- `StopRestTimer`、`WorkoutEnded`、`EnterNextSet/EnterNextRound` 事件加防重复执行。
- 对 `currentTask/currentSet/sessionId/timerId` 为空或过期的状态，显示可恢复 UI，而不是 crash。
- receiver 收到 timerId 后即使 service 启动失败，也必须先 alert/vibrate，并把状态写入数据库。
- App 回到前台时，如果发现过期 RUNNING timer，不直接走 UI 推进，而是补发 receiver/alert 并进入“休息结束，等待手动确认”状态。

最低验证：
- `assembleDebug`
- `testDebugUnitTest`
- `assembleDebugAndroidTest`
- 使用 `7` 提供的设备或模拟器复现：切后台直到休息结束，不回 App 也应触发提醒；回 App 后点击“进入下一组”和“结束训练”不闪退。
```

## 给 `4-数据、备份与本地存储线程` 的提示词

```text
你是 StreetStrengthApp 的【4-数据、备份与本地存储线程】。请审计休息计时、session 和训练推进相关的数据一致性，重点处理“休息结束后进入下一组/结束训练闪退”可能的数据根因。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\4-data-storage

请先读取：
1. THREAD_PROMPTS.md
2. THREAD_WORKSPLIT.md
3. work/thread-results/0-overall-design/20260502-0000-background-timer-crash-routing.md
4. `7-quality-env` 新抓取的闪退 logcat

审计重点：
- `ActiveRestTimerEntity` 的 RUNNING/FIRED/CANCELLED 状态迁移是否幂等。
- session 被 abandon/completed 后，是否仍有 active timer 指向旧 session。
- 点击“进入下一组”和“结束训练”是否可能同时更新同一 session/timer/task，导致外键、唯一约束或状态不一致。
- `abandonSession(activeDayId)` 这类调用是否参数语义明确，避免 dayId/sessionId 混用。
- 是否需要新增 Repository 级事务方法，例如 `finishRestAndAwaitUser(timerId)`、`advanceAfterRest(sessionId, taskId, setIndex)`、`abandonSessionAndClearRestTimers(dayId/sessionId)`。

要求：
- 不做 destructive migration。
- 如不需要改 Room schema，就只改 Repository/DAO 事务和幂等保护。
- 如果需要 schema 改动，必须提供 Migration 和 schema 更新。
- 给 `1-training-timer` 明确可调用的数据接口契约。

最低验证：
- `testDebugUnitTest`
- 为 timer/session 幂等迁移补单元测试。
- 如果 logcat 指向数据库异常，必须用测试覆盖该异常路径。
```

## 给 `2-计划编辑与训练编排线程` 的提示词

```text
你是 StreetStrengthApp 的【2-计划编辑与训练编排线程】。本问题默认不由你主修；只有当 logcat 或 `1-training-timer` 报告显示“进入下一组”崩溃来自训练编排/下一项计算时再介入。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\2-planner-programming

介入条件：
- 闪退堆栈指向任务顺序、组合动作轮次、setIndex/taskIndex 越界。
- 当前轮/组/动作计算在“组合动作模式”和“顺序动作模式”之间状态不一致。
- debug 当天重测产生多个 session 后，训练编排读取到了错误 session 或错误 day task。

检查重点：
- “进入下一组”在组合模式下是否推进到下一轮/下一动作正确。
- “进入下一组”在顺序模式下是否推进到同动作下一组或下一个动作正确。
- 已完成/已取消/已 abandon session 不应还能参与下一步计算。
- 空计划、单动作、最后一组、最后一轮、递减组数为 0 等边界必须有保护。

最低验证：
- `testDebugUnitTest`
- 覆盖组合动作模式、顺序动作模式、最后一组、debug 重测多 session 场景。
```

