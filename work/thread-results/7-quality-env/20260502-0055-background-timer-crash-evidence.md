# 线程交付报告

- 线程：7-测试、环境与质量线程
- 目标：为 2026-05-02 后台休息计时停止与休息结束后点击闪退问题补齐可复用证据采集链路，并在本地模拟器上产出一份基线证据
- 修改文件：
  - `packages/StreetStrengthApp-source-20260422/scripts/QUALITY_CHECKLIST.md`
  - `packages/StreetStrengthApp-source-20260422/scripts/capture-background-timer-evidence.ps1`
- 新增文件：
  - `packages/StreetStrengthApp-source-20260422/scripts/capture-background-timer-evidence.cmd`
  - `packages/StreetStrengthApp-source-20260422/scripts/capture-background-timer-evidence.ps1`
  - `packages/StreetStrengthApp-source-20260422/app/src/androidTest/java/com/codex/streetstrength/timer/BackgroundRestTimerUiFlowInstrumentedTest.kt`
  - `work/thread-results/7-quality-env/20260502-background-timer-crash-20260502-005132/**`
  - `work/thread-results/7-quality-env/20260502-background-timer-crash-20260502-011313/**`
  - `work/thread-results/7-quality-env/20260502-0055-background-timer-crash-evidence.md`
- 验证命令：
  - `scripts\capture-background-timer-evidence.cmd -StartEmulator -RunReceiverInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584`
  - `scripts\capture-background-timer-evidence.cmd -StartEmulator -RunUiFlowInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584`
  - `scripts\verify-local.cmd -NoClean`
  - `scripts\gradlew-local.cmd --max-workers=1 assembleDebugAndroidTest`
- 验证结果：
  - `assembleDebug`: passed
  - `testDebugUnitTest`: passed
  - `assembleDebugAndroidTest`: passed
  - `RestTimerReceiverInstrumentedTest`: passed, `OK (1 test)`
  - 模拟器自动启动、APK 安装、logcat/dumpsys 采集、模拟器收尾关闭均通过
  - `BackgroundRestTimerUiFlowInstrumentedTest`: failed as evidence, not as infrastructure failure. 自动流程已完成“开始训练 -> 完成本组 -> 休息中 -> 退后台 -> 等待 -> 回前台”，但回前台后 15 秒内未出现 `开始下一项`
- 需要总体设计集成的点：
  - 本线程没有改 App 业务代码，也没有改版本号或发布链
  - 后续发布前可把新增脚本作为 1/4/2 线程修复后的回归采集入口
- 需要其他线程注意的点：
  - 本次有效证据目录：`work/thread-results/7-quality-env/20260502-background-timer-crash-20260502-005132`
  - 测试设备：`emulator-5584`，model `sdk_gphone64_x86_64`，Android `14` / SDK `34`
  - App 版本：`1.1.17`，`versionCode=19`
  - 权限状态：`POST_NOTIFICATIONS` granted；`USE_EXACT_ALARM` granted；`SCHEDULE_EXACT_ALARM` 在 dumpsys package 中显示 granted=false，但当前包声明了 `USE_EXACT_ALARM`，receiver instrumentation 的 alarm-clock 路径成功触发
  - appops：`VIBRATE: allow`，`START_FOREGROUND: allow`
  - deviceidle：`mState=ACTIVE`，`mLightState=ACTIVE`，`mForceIdle=false`
  - logcat 中出现 `RestTimerReceiver: Received rest finish alarm for timer 9999401`
  - logcat 中 `ForegroundServiceStartNotAllowedException`、`SecurityException`、`SQLiteException`、`IndexOutOfBoundsException` 均未出现
  - logcat 中没有 `FATAL EXCEPTION`，也没有 `at com.codex.streetstrength` 的崩溃栈；全局 `IllegalStateException` / `NullPointerException` 来自系统或其他进程日志，不能归因到本 App
  - 自动 UI 证据目录：`work/thread-results/7-quality-env/20260502-background-timer-crash-20260502-011313`
  - 自动 UI 证据中没有 `FATAL EXCEPTION`，没有 `ForegroundServiceStartNotAllowedException`；失败点是 instrumentation 在 `BackgroundRestTimerUiFlowInstrumentedTest.kt:66` 等待 `开始下一项` 超时
  - 自动 UI 证据中没有出现 `RestTimerReceiver`，`after-dumpsys-alarm.txt` 也没有 `com.codex.streetstrength` / `timer.FINISH` 相关 alarm；这说明真实点击链路进入 `休息中` 后，没有可靠留下后台 alarm/receiver 触发证据
  - 该结论应交给 `1-训练执行与计时线程` 优先排查训练页面进入休息后的服务/闹钟调度触发点；若后续抓到点击闪退的生产 App 栈，再按首个 App 栈归属补充给 4 或 2
- 风险或未完成事项：
  - 模拟器基线能证明 `AlarmManager -> RestTimerReceiver -> 通知/FGS` 链路在本地可达，但不能替代用户真机的后台省电策略和真实震动马达表现
  - 本次自动模拟复现了后台休息无法自然推进到 `开始下一项` 的核心现象，但没有复现生产 App 点击闪退

## 复用方式

自动启动模拟器并采集 receiver 基线：

```powershell
.\scripts\capture-background-timer-evidence.cmd -StartEmulator -RunReceiverInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584
```

自动模拟人工点击、退后台、回前台链路：

```powershell
.\scripts\capture-background-timer-evidence.cmd -StartEmulator -RunUiFlowInstrumentation -AvdName StreetStrengthApi34 -EmulatorPort 5584
```

手动复现闪退时，在已经运行的模拟器上执行：

```powershell
.\scripts\capture-background-timer-evidence.cmd -DeviceSerial emulator-5584 -CaptureSeconds 90
```

在 90 秒窗口内完成“开始休息、切后台、到点、回 App、点击进入下一组或结束训练”的操作，脚本会把 logcat、dumpsys alarm、dumpsys deviceidle、dumpsys package、appops、power、battery 和 notification 状态写入新的证据目录。
