# 线程 1 交付报告：ColorOS 正式休息 alarm 被推迟与 wake lock 修复

时间：2026-05-05

## 用户现场

用户提示正式训练休息通知显示 `23:56` 结束，但到 `23:57` 仍没有震动。监视期间没有打开 App，没有清数据，只抓 StreetStrength 计时 tag 和包级 alarm 状态。

## 现场证据

证据目录：

`work/thread-results/1-training-timer/20260504-formal-monitor-2356`

关键发现：

- `23:53:56` 正式训练 timer 110 已经调度 `com.codex.streetstrength.timer.FINISH`。
- `dumpsys alarm com.codex.streetstrength` 显示 `ELAPSED_WAKEUP` 和 `RTC_WAKEUP` 两个 alarm 都还在。
- 原始触发时间是 `2026-05-04 23:56:56.353`，但 `policyWhenElapsed` 被系统改成 `+2d23h58m42s900ms`，即被 ColorOS/系统 alarm 策略推迟到约 3 天后。
- 当时 `RestTimerService` 仍是 foreground service，`isForeground=true foregroundId=401`。
- `am get-standby-bucket com.codex.streetstrength` 为 `10`，电池省电为 OFF，说明不是普通 standby bucket 限制造成，偏向 OEM alarm proxy/电池策略。

## 根因判断

正式链路已经调度 alarm，但这台 ColorOS 设备会把 StreetStrength 的短时间 `AlarmManager` 到点推迟很久。只靠 `AlarmManager` 无法满足训练休息提醒。

旧服务倒计时运行在 `Dispatchers.Main.immediate`，后台后也可能被系统压慢或暂停；需要让前台服务在休息期间自己维持短时 CPU 唤醒，并让计时线程脱离 UI/Main looper。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/AndroidManifest.xml`
  - 新增 `android.permission.WAKE_LOCK`。

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/RestTimerService.kt`
  - 休息计时开始时获取 partial wake lock，时长为 `remaining + 60s`，最短 10s，最长 30min。
  - prepare 阶段也获取短时 wake lock，避免正式训练点击后快速后台导致 START/timeout 不执行。
  - 计时 countdown 和 prepare timeout 移到独立 `Dispatchers.Default` 的 `timerScope`，不再依赖 Main/UI looper。
  - 到点后仍通过 `RestTimerAlarmScheduler.dispatchFinish()` 进入正式 Receiver 链路，不直接在计时线程里震动。
  - finish/stop/destroy 时释放 wake lock。

## 验证

本地验证通过：

```powershell
.\scripts\gradlew-local.cmd --max-workers=1 "-Dkotlin.compiler.execution.strategy=in-process" "-Dkotlin.incremental=false" assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

真机 `bbc478f4` 已安装最新 debug APK。

干净 10 秒后台自测证据目录：

`work/thread-results/1-training-timer/20260504-wakelock-background-selftest`

关键日志：

- `00:06:34.736` `RestTimerService` 获取 wake lock，timer 113 剩余 `9979ms`。
- `00:06:44.775` `RestTimerReceiver` 后台收到 finish alarm。
- `00:06:44.795` finish service 收到 `ACTION_FINISH`。
- `00:06:44.804` 开始持续震动。
- `00:06:52` 点击关闭后取消震动。

## 仍需正式复测

需要用户重新执行正式训练路径：

完成本组 -> 进入休息 -> 立刻 HOME/锁屏 -> 到点不回 App 也应震动。

如果仍失败，下一步应记录最新 `RestTimerService` 日志中是否出现 `Acquired rest timer wake lock` 和 `Starting rest timer ... remaining=...`；如果这两行出现但到点仍不触发，说明 ColorOS 还冻结了 foreground service 进程，需要引导用户把 App 加入电池优化白名单或自启动/后台运行白名单。
