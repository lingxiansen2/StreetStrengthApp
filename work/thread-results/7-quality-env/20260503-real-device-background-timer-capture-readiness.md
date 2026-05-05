# 线程 7 实机证据采集准备

## 背景

总体设计新增两份结论：

- `work/thread-results/0-overall-design/20260502-v1.1.18-background-rest-timer-integration.md`
- `work/thread-results/0-overall-design/20260502-emulator-test-before-next-update.md`

当前 `v1.1.18 / versionCode 20` 已完成 emulator UI flow 与 debug self-test 验证，模拟器未复现用户实机反馈的问题。下一步重点改为实机带日志复现，而不是继续用模拟器证明同一条路径。

## 第七线程本次调整

- 更新 `packages/StreetStrengthApp-source-20260422/scripts/capture-background-timer-evidence.ps1`
  - 默认仍拒绝非 `emulator-*` serial。
  - 新增 `-AllowPhysicalDevice`，仅在用户明确批准后允许物理设备采集 logcat/dumpsys。
  - 物理设备模式下拒绝 `-InstallDebugApks`、`-RunReceiverInstrumentation`、`-RunUiFlowInstrumentation`，避免覆盖用户设备状态。
  - `summary.md` 新增 device kind 与 physical-device 标记。
- 更新 `packages/StreetStrengthApp-source-20260422/scripts/QUALITY_CHECKLIST.md`
  - 增加实机复现采集命令。
  - 明确实机模式只采集日志，不自动安装、不跑 instrumentation。

## 实机采集命令

在用户明确允许操作物理设备、且目标版本已经安装后执行：

```powershell
cd E:\Workspace\GitHub\StreetStrengthApp\packages\StreetStrengthApp-source-20260422
.\scripts\capture-background-timer-evidence.cmd -DeviceSerial <physical-serial> -AllowPhysicalDevice -Scenario real-device-background-rest -CaptureSeconds 180
```

180 秒窗口内需要覆盖：

- 开始训练。
- 完成本组进入休息。
- 切到桌面或其他 App。
- 等待休息到点。
- 记录是否有通知、震动。
- 回到 App。
- 点击“开始下一项/进入下一组”或“结束训练”。
- 如果闪退，等待系统回桌面后不要立刻重启 App，先让脚本完成抓取。

## 交接规则

- 如果实机证据中出现 `FATAL EXCEPTION`，按首个 production app stack 交给 `1-training-timer`，必要时再路由给 `4-data-storage` 或 `2-planner-programming`。
- 如果没有 `FATAL EXCEPTION`，但没有 `RestTimerReceiver` / `com.codex.streetstrength.timer.FINISH` alarm / notification 证据，也交给 `1-training-timer`。
- 如果 emulator 继续通过但实机失败，不应再发布新版本；应先用实机证据定位厂商后台限制、权限、通知、震动或真实交互路径差异。

## 本次未执行

本线程没有实际操作物理设备；只是按总体设计准备了显式授权的实机采集入口。
