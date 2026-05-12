# 2026-05-08 休息提醒可靠性专项分工

## 用户反馈

- 真实训练中休息到点不主动震动，约 10 次出现 2 次。
- 点击通知里的「关闭提醒」，或切回 App 点击「停止震动/进入下一组」后，仍然存在继续震动的情况。
- 需要覆盖所有情况继续调试，不使用 5G 移动终端，只允许使用 `bbc478f4` 这台手机。

## 总控初步判断

问题分为两个面：

- 到点不震动：归属 `1-训练执行与计时线程` 主责，`7-测试、环境与质量线程` 负责真机复现与日志证据。
- 关闭后仍震动：归属 `1-训练执行与计时线程` 主责，`6-UI 设计与交互线程` 负责关闭入口、状态文案、防重复点击和用户确认路径。

已经定位到一个明确竞态：

- 休息结束时 `RestTimerReceiver` 会先启动通知和震动。
- 随后 `RestTimerService.ACTION_FINISH` 可能再次执行 `startFinishedAlert()`，导致同一个 timer 被重复启动/重启震动。
- 如果用户刚好在 Receiver 与 Service 之间点击关闭提醒，后到的 Service 可能再次拉起震动。

此外，exact alarm 和 alarmClock backup 可能对同一个 timer 双投递，旧逻辑对 `TimerState.FIRED` 仍返回 true，会造成重复提醒。

## 总控已做的初步代码修复

修改范围：

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerAlert.kt`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerReceiver.kt`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerController.kt`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/MainActivity.kt`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`

变更摘要：

- 结束提醒通知的「打开训练」「关闭提醒」都携带 `timerId`。
- 停止提醒时记录 stopped timerId，同一个 timer 后续迟到的 FINISH 不允许再启动震动。
- `RestTimerService.stop()` 改为优先发送 `ACTION_STOP`，让 Service 内部设置 `stopRequested=true`，再 fallback 到 `stopService()`。
- Receiver 收到重复 FINISH 时，如果 timer 已经是 `FIRED`，不再重复提醒。
- Service 收到 FINISH 时，如果发现 timer 已经由 Receiver 标记为 `FIRED`，只保活结束通知，不再重启震动。
- `TrainingScreen` 的 StopRestTimer 事件携带 timerId，确保 App 内「进入下一组/结束训练/跳过休息」也能 suppression 同一个 timer。
- `VibratorManager` 停止时同时调用 manager cancel 和 defaultVibrator cancel，减少 OEM 设备上 token 残留。

## 已完成验证

命令：

- `assembleDebug` 通过。
- `testDebugUnitTest` 通过。

真机：`bbc478f4`

证据目录：

- `work/thread-results/7-quality-env/20260508-v1124plus-10x-filtered-ui-stop`
- `work/thread-results/7-quality-env/20260508-v1124plus-3x-dedup-stop-check`

已观察到：

- debug 10 秒后台自测 10 轮均能看到 `opPkg=com.codex.streetstrength` 的振动调用。
- 修复后 3 轮复测中，同一 timer 双投递时第二次会出现 `Timer xx already fired; skip duplicate receiver alert`，不会重复启动震动。
- 点击 debug 页面「关闭震动」后出现 `Marked rest alert stopped` 和 `ACTION_STOP`，之后没有同一个 timer 的新振动调用。

当前尚未证明：

- 用户真实训练路径中 60s/90s/120s 休息在后台 10 轮是否全部稳定。
- 锁屏、切到其他重负载 App、系统清后台、通知栏点击「关闭提醒」这几个路径是否全部稳定。

## 分派给 1-训练执行与计时线程

目标：继续主修休息计时与震动可靠性，不要只依赖 debug 10 秒自测。

必须检查：

- 真实训练完成一组后创建的 `ActiveRestTimerEntity` 是否每次都触发 `RestTimerService.start()`。
- 真实训练路径中是否存在提前 `clearRestTimer()` 或 `StopRestTimer` 导致 alarm/service 被取消。
- `RestTimerReceiver -> RestTimerAlert -> RestTimerService.ACTION_FINISH` 的顺序是否仍有竞态。
- 通知「关闭提醒」、App 内「停止震动并进入下一组」、结束训练、跳过休息、返回键这 5 条路径是否都携带 timerId 并停止同一个 timer。
- 如果系统在后台延迟 alarm，回前台补偿逻辑必须走 `RestTimerAlarmScheduler.dispatchFinish()`，不能直接 `markRestTimerFired()`。

建议继续修复方向：

- 给真实训练路径增加更明确的日志：timerId、sessionId、taskId、createdAt、endElapsedRealtimeMs、source=`completeSet/watchdog/restore/ui-compensation/receiver/service`。
- Receiver 与 Service 对同一 timer 的 alert 应保持幂等，任何 timer 只能有一次“启动震动”。
- 停止提醒后，同一 timer 的任何迟到 FINISH 都必须只写日志并退出。

交付报告位置：

- `work/thread-results/1-training-timer/YYYYMMDD-rest-vibration-reliability.md`

## 分派给 6-UI 设计与交互线程

目标：降低用户误操作和重复点击造成的状态混乱。

必须检查：

- 休息结束状态下，「停止震动并进入下一组」点击后按钮是否立即进入 disabled/loading 或状态切换，避免连点。
- 通知点击回 App 后，训练页是否明确显示“提醒已关闭/等待进入下一组”，不要让用户以为还在休息倒计时。
- 「关闭提醒」与「进入下一组」语义是否区分：关闭只停提醒，进入下一组还要清理 timer 并推进训练。
- 结束训练弹窗中必须说明会停止当前休息提醒。

不要修改：

- AlarmManager、ForegroundService、Receiver 的底层逻辑。

交付报告位置：

- `work/thread-results/6-ui-interaction/YYYYMMDD-rest-alert-stop-ui.md`

## 分派给 7-测试、环境与质量线程

目标：建立可复现的真机测试矩阵，专门覆盖用户反馈的 2/10 间歇失败。

设备规则：

- 只使用 `bbc478f4`。
- 不使用 `97257126520013`，这是 5G 移动终端。

必须跑的矩阵：

- debug 10 秒后台自测 10 轮。
- 真实训练路径 60 秒休息 10 轮。
- 真实训练路径 120 秒休息 10 轮。
- 通知栏点击「关闭提醒」5 轮。
- App 内点击「停止震动并进入下一组」5 轮。
- 切桌面、切抖音/QQ等重负载 App、锁屏三种后台场景分别至少 3 轮。

抓日志建议：

```powershell
adb -s bbc478f4 logcat -c
adb -s bbc478f4 logcat -d -s RestTimerService:I RestTimerReceiver:I RestTimerAlert:I RestTimerAlarmScheduler:I VibratorManagerServiceExtImpl:I ActivityManager:W *:S
adb -s bbc478f4 shell dumpsys alarm
adb -s bbc478f4 shell dumpsys notification
adb -s bbc478f4 shell dumpsys activity services com.codex.streetstrength
```

每轮要记录：

- timerId。
- 是否出现 `Starting rest timer`。
- 是否出现 `Received rest finish alarm`。
- 是否出现 `Starting continuous rest-finished vibration`。
- 是否出现 `opPkg=com.codex.streetstrength`。
- 停止后是否还出现同 timerId 的 `vibrate`。
- 是否有 `Timer xx already fired; skip duplicate receiver alert`。

交付报告位置：

- `work/thread-results/7-quality-env/YYYYMMDD-real-training-rest-vibration-matrix.md`

## 总控下一步

- 等 1/6/7 的报告回来后，再决定是否发布 `v1.1.25 / versionCode 27`。
- 当前代码已通过编译和单元测试，但还没有生成 release-chain 新版本 APK。
