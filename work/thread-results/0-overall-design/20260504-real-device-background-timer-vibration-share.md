# 2026-05-04 真机后台休息计时与震动问题共享任务单

## 当前问题

模拟器本地测试正常，但接入真机后仍复现：

- 进入休息倒计时后切到后台，顶部通知/状态中的倒计时运行一小段时间后停止。
- 休息时间到达后，如果用户仍在后台，没有按时提醒或震动。
- 回到 App 后才触发休息结束提醒/震动。
- 休息结束后点击“进入下一组”或“结束训练”，震动仍可能继续。

这说明问题不只是 Compose 页面刷新，而是真机后台状态下的休息计时、通知触发、持续震动停止链路没有闭环。

## 设备约束

- 禁止使用 5G 移动终端：`97257126520013`，`model:sprdisk`。
- 本次真机调试只允许使用：`bbc478f4`，`model:PKG110`。
- 所有 ADB 命令必须显式指定 `-s bbc478f4`。
- 不允许执行卸载、清数据、恢复出厂、删除用户数据类命令，除非用户单独确认。

## 总体判断

优先按以下方向定位：

- 休息倒计时 UI/通知不能依赖后台持续递减 ticker，必须以 `endAtMillis - System.currentTimeMillis()` 计算剩余时间。
- 休息开始时必须持久化 active timer，并注册真机可触发的 `AlarmManager` 兜底。
- `BroadcastReceiver -> ForegroundService -> 休息结束通知 + 持续震动` 链路必须在 App 后台也能执行。
- “进入下一组”“跳过休息”“结束训练”“返回训练页恢复状态”都必须统一调用停止休息结束警报/停止震动逻辑。
- 如果真机系统限制后台启动或闹钟触发，7 线程必须用日志和 dumpsys 明确证据，不能只凭模拟器结论。

## 1-训练执行与计时线程任务

### 目标

修复真机后台休息计时和震动停止逻辑。

### 主要检查文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/**`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/**`
- `packages/StreetStrengthApp-source-20260422/app/src/main/AndroidManifest.xml`

### 必做项

- 检查休息开始时是否保存了稳定的 `endAtMillis`，通知展示和页面展示都要从绝对结束时间计算剩余时间。
- 检查后台后通知倒计时停止的原因：不能依赖 Compose `LaunchedEffect`、Activity 生命周期内 ticker、或者会被系统暂停的 UI 协程作为唯一计时源。
- 检查 `AlarmManager` 注册的 PendingIntent 是否广播到 `RestTimerReceiver`，并且 receiver 能启动 `RestTimerService` 的 finish action。
- 检查真机 Android 版本下后台启动前台服务是否需要特殊处理；如果有 `ForegroundServiceStartNotAllowedException` 或权限异常，记录并修复。
- 把“停止休息结束警报”做成单一入口，例如 `stopRestAlert()` 或等价方法；以下路径必须全部调用：
  - 点击“进入下一组”
  - 点击“跳过休息”
  - 点击“结束训练”
  - 训练页恢复后发现当前状态已进入下一动作/下一组
  - 通知中的停止/返回动作
- 保证进入下一组后旧休息 timer 被清理，不再保留旧 vibration job、old notification 或 active rest record。
- 若已有 debug 自测页，增加或更新“真机后台休息提醒自测”入口：10-15 秒休息，切后台等待触发，点击停止后震动必须停止。

### 不要做

- 不改计划编辑、动作库、统计页。
- 不发布 APK，不改 release-chain；最终发布交给总体设计线程。

### 交付

写报告到：

`work/thread-results/1-training-timer/20260504-real-device-background-rest-timer-fix.md`

报告必须包含：

- 修改文件列表。
- 休息开始、后台触发、休息结束、停止震动的完整链路说明。
- 真机仍需 7 线程验证的点。
- 执行过的验证命令和结果。

## 6-UI 设计与交互线程任务

### 目标

只处理用户可感知的状态展示和操作入口，不改底层计时逻辑。

### 主要检查文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/**`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/components/**`
- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/theme/**`

### 必做项

- 训练页需要明确区分三种状态：
  - 训练中
  - 休息倒计时中
  - 休息结束，等待手动确认
- 休息结束后，页面主按钮文案必须明确，例如“停止震动并进入下一组”或“进入下一组”。
- 如果后台提醒权限缺失或系统限制可能影响提醒，UI 要有明确入口提示用户去设置，但不要把权限检测业务写在 UI 线程内，具体检测结果由 1/7 线程提供。
- 确认“结束训练”按钮有二次确认或明显危险操作样式，避免误触；但不要改变训练状态机。
- 小屏手机上主状态和主按钮必须可见，不能被详情卡片挤出屏幕。

### 不要做

- 不改 `RestTimerService`、`AlarmManager`、BroadcastReceiver 的核心逻辑。
- 不改 Room / Repository 数据结构。
- 不发布 APK。

### 交付

写报告到：

`work/thread-results/6-ui-interaction/20260504-real-device-rest-timer-ui.md`

报告必须包含：

- 修改文件列表。
- 休息中/休息结束/震动中三个状态的 UI 表现。
- 小屏可用性说明。
- 需要 1 线程提供的数据或回调。

## 7-测试、环境与质量线程任务

### 目标

在真机 `bbc478f4` 上抓到问题证据，验证 1 线程修复是否真正解决后台触发和震动停止。

### 设备

- 只用 `bbc478f4`。
- 不使用 `97257126520013`。

### 建议命令

```powershell
$adb = "E:\Workspace\GitHub\StreetStrengthApp\.local-tools\android-sdk\platform-tools\adb.exe"
$serial = "bbc478f4"

& $adb -s $serial devices -l
& $adb -s $serial logcat -c
```

复现完成后：

```powershell
& $adb -s $serial logcat -d -v time > E:\Workspace\GitHub\StreetStrengthApp\work\phone-rest-timer-logcat.txt
& $adb -s $serial shell dumpsys alarm > E:\Workspace\GitHub\StreetStrengthApp\work\phone-dumpsys-alarm.txt
& $adb -s $serial shell dumpsys activity services com.codex.streetstrength > E:\Workspace\GitHub\StreetStrengthApp\work\phone-dumpsys-services.txt
& $adb -s $serial shell dumpsys notification > E:\Workspace\GitHub\StreetStrengthApp\work\phone-dumpsys-notification.txt
& $adb -s $serial shell dumpsys deviceidle > E:\Workspace\GitHub\StreetStrengthApp\work\phone-dumpsys-deviceidle.txt
```

日志关键词：

```powershell
Select-String -Path E:\Workspace\GitHub\StreetStrengthApp\work\phone-rest-timer-logcat.txt -Pattern "RestTimer|RestTimerService|RestTimerReceiver|Vibrator|AlarmManager|ForegroundService|AndroidRuntime|FATAL|Exception|BackgroundServiceStartNotAllowed|ForegroundServiceStartNotAllowed|SecurityException|Exact alarm"
```

### 必测场景

- 场景 A：休息 15 秒，App 保持前台，结束后震动，点击进入下一组，震动停止。
- 场景 B：休息 15 秒，开始后立刻切桌面，等待 25 秒，确认后台是否按时通知/震动。
- 场景 C：休息结束震动后，从通知或 App 回到训练页，点击进入下一组，震动必须停止。
- 场景 D：休息结束震动后，点击结束训练，不能闪退，震动必须停止。
- 场景 E：手机锁屏后重复场景 B/C。

### 权限和系统状态记录

报告中记录：

- 手机型号、Android 版本。
- App 通知权限状态。
- 精确闹钟权限状态。
- 电池优化状态。
- App 是否允许后台运行、自启动、悬浮/通知相关限制。
- `dumpsys alarm` 中是否能看到 StreetStrengthApp 的休息闹钟。
- `dumpsys services` 中休息期间 `RestTimerService` 是否存活。

### 交付

写报告到：

`work/thread-results/7-quality-env/20260504-real-device-background-rest-timer-evidence.md`

证据文件放入：

`work/thread-results/7-quality-env/20260504-real-device-background-rest-timer-evidence/`

报告必须包含：

- 复现步骤。
- 失败或通过结论。
- logcat/dumpsys 文件路径。
- 是否确认 1 线程修复有效。
- 仍需总体设计线程发布前注意的风险。

## 总体设计线程后续集成标准

只有当 1/6/7 都提交报告后，总体设计线程才做集成发布：

- 检查三个报告路径是否存在。
- 汇总代码改动，解决冲突。
- 递增版本号，例如从 `1.1.20 / 22` 升到下一版本。
- 新建 `packages/release-chain/vX.Y.Z/`。
- 生成 APK 和 `CHANGELOG.md`。
- 至少通过：
  - `assembleDebug`
  - `testDebugUnitTest`
  - 如涉及 androidTest，补跑 `assembleDebugAndroidTest`
  - APK 内 `versionName/versionCode` 检查
- 不删除任何旧 APK 或旧 release-chain 文件夹。
