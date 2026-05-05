# 训练执行与计时线程：v1.1.17 回归修复

## 处理范围

- 问题 A：休息倒计时切后台后，到点不触发通知/持续震动，回到 App 后才继续。
- 问题 B：点击“结束训练”后 App 像自动关闭。

## 根因判断

### 问题 A

`RestTimerService` 虽然已经安排了 persistent `AlarmManager`，但服务内的倒计时 coroutine、进程内 `OnAlarmListener`、恢复过期 timer 等路径仍会直接调用 `finishTimer()`。这让“到点结束”的实际行为仍可能受服务进程/调度恢复影响，debug 自测入口还额外安排了一条 self-test alarm，不能清晰证明正式 Receiver 链路被触发。

修复后，所有到点路径都统一分发到正式 `RestTimerReceiver`：

`AlarmManager / in-process fallback / service countdown expiry -> RestTimerReceiver -> RestTimerAlert.showFinishedAlert -> notification + continuous vibration -> RestTimerService.ACTION_FINISH -> mark fired / foreground alert state`

通知倒计时刷新仍然只是展示层，不再是休息结束提醒的权威触发源。

### 问题 B

训练页确认结束后原逻辑立即执行：

`viewModel.endWorkout(); onBack()`

`endWorkout()` 内部是异步 repository 写入和停止提醒事件，页面会先 `popBackStack()`。如果训练页没有可返回的上一页，或返回栈状态不符合预期，就会表现为 Activity 退出/回到桌面。未能抓取真机 logcat，因此没有证据表明是 Room/Repository crash；当前修复按导航时序回归处理，暂不标记 `4-data-storage` 协助。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - `startTimer()` 遇到已过期 timer 时改为 `dispatchFinishThroughReceiver()`。
  - `restoreLatestActiveTimer()` 遇到过期 RUNNING timer 时改为走 Receiver。
  - 进程内 `AlarmManager.OnAlarmListener` 到点后不再直接 `finishTimer()`，改为发送正式 Receiver `PendingIntent`。
  - 服务倒计时 coroutine 到点后不再直接 `finishTimer()`，改为发送正式 Receiver `PendingIntent`。
  - 增加 Receiver alarm 调度/分发日志，便于后续 logcat 追踪。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerReceiver.kt`
  - Receiver 收到休息结束 alarm 时记录日志。
  - 保持 Receiver 先发通知和持续震动，再尝试启动 `RestTimerService.ACTION_FINISH`，避免后台 FGS 启动受限时完全无提醒。

- `packages/StreetStrengthApp-source-20260422/app/src/debug/java/com/codex/streetstrength/timer/RestReminderSelfTestActivity.kt`
  - debug 10 秒自测不再安排额外 self-test alarm。
  - 自测只创建 debug timer 数据并调用正式 `RestTimerService.start(...)`，由正式服务调度正式 `RestTimerReceiver`。
  - 保留旧 self-test alarm 取消逻辑，用于清理旧版本可能遗留的 debug PendingIntent。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`
  - 新增 `TrainingEvent.WorkoutEnded`。
  - `endWorkout()` 改为先 `abandonSession()`，再发 `StopRestTimer`，最后发 `WorkoutEnded`。
  - UI 点击“结束训练”不再立即 `onBack()`。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/StreetStrengthRoot.kt`
  - 训练结束事件导航回 `Routes.Calendar`，避免训练页作为栈顶/孤立页面时 `popBackStack()` 导致 Activity 看起来被关闭。

## 验证结果

- `.\scripts\gradlew-local.cmd --no-daemon --max-workers=1 assembleDebug`：通过。
  - Kotlin 增量缓存先报 `EOFException`，随后自动 fallback 到非增量编译并成功。
- `.\scripts\gradlew-local.cmd --no-daemon --max-workers=1 testDebugUnitTest`：通过。
- `.\scripts\gradlew-local.cmd --no-daemon --max-workers=1 assembleDebugAndroidTest`：通过。

## 设备/模拟器验证状态

- `adb devices -l`：
  - `97257126520013 device product:sprdtrum model:sprdisk device:spxxxx`
  - `emulator-5584 offline`
- 在线设备不是正常 Android runtime：
  - `adb shell pm path com.codex.streetstrength` 返回 `/bin/sh: pm: command not found`
  - `adb shell getprop ...` 返回 `/bin/sh: getprop: command not found`
  - `adb shell logcat ...` 返回 `/bin/sh: logcat: command not found`
- 项目内 `.local-tools/android-user/.android/avd` 不存在，`emulator -list-avds` 无可启动 AVD。

因此本轮无法完成“复现并抓 logcat”和“后台 10 秒自测实机触发”的设备侧验证；报告中不把该部分标记为已验证。

## 仍需真机确认

- Android 12+ 精确闹钟权限关闭时，`setAndAllowWhileIdle` 可能延迟触发，但不应依赖 Compose/UI 恢复。
- Android 13+ 通知权限关闭时，通知不可见；当前 Receiver 仍会尝试持续震动。
- OEM 省电策略/Doze 下，`AlarmManager -> RestTimerReceiver -> 通知 + 持续震动` 需要真机后台测试确认。
- 通知“关闭提醒”动作需要在真机上确认能取消持续震动和通知。
