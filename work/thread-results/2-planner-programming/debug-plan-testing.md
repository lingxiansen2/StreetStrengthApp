# 计划编辑与训练编排线程：Debug 计划测试能力

## 目标

当天训练完成后，debug 构建仍可继续新增/调整当天计划并再次开始训练，用于反复测试训练计时、休息提醒和通知链路。release 构建保持正式限制：当天已完成后不可重复开始。

## 已改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/domain/PlanDayActionPolicy.kt`
  - 增加 `allowCompletedTodayTesting` 参数。
  - 仅在“今天 + DONE + debug 测试开关开启”时允许继续编辑和开始。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/planner/PlannerScreen.kt`
  - planner 使用 `PlanTestingSwitch.enabled` 传入 debug 测试开关。
  - debug 下 DONE 当天显示测试文案，并解锁添加、模板、模式切换、顺序调整和开始按钮。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/calendar/CalendarScreen.kt`
  - calendar 使用相同策略。
  - debug 下 DONE 当天显示“Debug 可再次测试 / Debug 再测”。
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt`
  - 写入保护保留 release 限制。
  - debug 下仅当天已完成计划允许再次修改。
  - debug 下当天 latest session 为 `COMPLETED` 时，新建新的 `IN_PROGRESS` session，避免复用已完成 session 导致无法重测。
- `packages/StreetStrengthApp-source-20260422/app/src/debug/java/com/codex/streetstrength/debug/PlanTestingSwitch.kt`
  - debug 构建开关为 `true`。
- `packages/StreetStrengthApp-source-20260422/app/src/release/java/com/codex/streetstrength/debug/PlanTestingSwitch.kt`
  - release 构建开关为 `false`。
- `packages/StreetStrengthApp-source-20260422/app/src/test/java/com/codex/streetstrength/domain/PlanDayActionPolicyTest.kt`
  - 覆盖 debug 下 DONE 当天可重新打开，默认/release 下仍锁定。

## 跨模块修复

- `packages/StreetStrengthApp-source-20260422/app/src/debug/java/com/codex/streetstrength/timer/RestReminderSelfTestActivity.kt`
  - debug 构建原本有 `PendingIntent?` 传给 `setAlarmClock` 的编译错误。
  - 为了完成 debug 构建验证，已在调度前做 null guard。

## 验证结果

- `scripts/gradlew-local.cmd :app:compileDebugKotlin --no-daemon` 通过。
- `scripts/gradlew-local.cmd testDebugUnitTest --no-daemon` 通过。
- `scripts/gradlew-local.cmd assembleDebug --no-daemon` 通过。

## 风险

- 未改 Room 表结构，不需要 Migration。
- debug 测试模式会让同一天产生多条有效 session，目的是测试计时链路；release 不启用该开关。
- repository 仍禁止过去计划修改；debug 只放开“今天已完成”的测试场景。
