# 线程 1 交付报告：移除正式训练 PREPARE 占位计时

时间：2026-05-05

## 用户现场

用户反馈：当前已经到 `00:10`，通知显示 `00:09` 应该响铃，刚才在其它 App 中，没有震动。

## 现场检查

证据目录：

`work/thread-results/1-training-timer/20260505-formal-current-0010`

检查结论：

- `RestTimerService` 处于 foreground service，但 `intent` 仍是 `ACTION_PREPARE`。
- 没有看到后续 `ACTION_START`。
- `dumpsys alarm com.codex.streetstrength` 没有当前待触发的 StreetStrength alarm，只看到旧 alarm cancellation 历史。
- 因此这次不是“真实 rest timer 到点但没有震动”，而是 PREPARE 占位服务显示了预计结束时间，但真正的 `ActiveRestTimerEntity` / `ACTION_START` 没接上。

## 根因判断

前一轮为抢前台服务启动窗口，在正式训练点击“完成本组”时先启动了 `RestTimerService.prepare()`。这个设计会先显示运行中通知。如果后续 `completeSet()` 没有成功创建/返回 rest timer，或者 START 没接上，就会留下一个没有 timerId、没有 alarm、没有到点能力的占位通知。

这种占位通知会误导用户以为 `00:09` 应该响铃，但实际上没有正式 timer。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`
  - 移除正式训练路径里的 `RestTimerService.prepare()` 调用。
  - 只有 `repository.completeSet()` 成功创建 `ActiveRestTimerEntity` 后，才启动 `RestTimerService.start()`。
  - 增加 `TrainingViewModel` 日志：
    - `Completing set ... restSec=... shouldStartRest=...`
    - `Rest timer created id=... duration=...`

保留前一轮的后台可靠性修复：

- `RestTimerService` 使用 partial wake lock 覆盖休息时间。
- countdown/prepare timeout 使用独立 `Dispatchers.Default` timer scope。
- 到点仍走正式 `dispatchFinish -> RestTimerReceiver -> RestTimerService -> 通知/震动` 链路。

## 验证

本地验证通过：

```powershell
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

已安装到真机 `bbc478f4`。

debug-only 10 秒后台自测仍能触发：

证据目录：

`work/thread-results/1-training-timer/20260505-remove-prepare-formal-entry`

关键日志：

- `00:17:23` `RestTimerReceiver` 收到 timer 116 finish。
- `00:17:23` `RestTimerService` 收到 `ACTION_FINISH`。
- `00:17:23` `RestTimerAlert` 开始持续震动。
- `00:17:30` 点击关闭后取消持续震动。

## 仍需正式复测

请重新走正式训练路径。预期现在只有真正创建了 rest timer 后才会出现休息通知；如果通知显示预计结束时间，就应同时能在日志里看到 `Rest timer created id=...` 和 `RestTimerService action=START`。
