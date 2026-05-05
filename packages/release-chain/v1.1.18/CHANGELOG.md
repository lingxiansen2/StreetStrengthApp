# v1.1.18

- Version: `1.1.18`
- VersionCode: `20`
- APK: `app-debug-20260502-v1.1.18.apk`
- Date: `2026-05-02`

## 本版整合范围

本版整合 `1-训练执行与计时线程` 和 `7-测试、环境与质量线程` 对后台休息计时、休息结束提醒、震动后按钮闪退的修复与验证。

## 主要更新

- 真实训练进入休息后，休息计时会在 rest timer 持久化后立即注册后台 `AlarmManager` broadcast，不再依赖训练页 Compose 生命周期存活。
- 新增应用级 watchdog：App 启动或回前台时会检查最新活跃休息 timer，未来 timer 自动补注册 alarm，过期 RUNNING timer 自动派发结束提醒。
- `RestTimerReceiver` 收到休息结束广播后优先持久化为 `FIRED`，并先发布“休息结束”通知和持续震动，再尝试交给前台服务接管。
- `RestTimerService.stop()` 改为直接 `stopService()`，降低停止提醒时再次触发前台服务启动限制的风险。
- “开始下一项/进入下一组/结束训练/跳过休息”路径改为幂等清理，先停止 alarm、通知、震动和 service，再更新 session/timer 与导航状态。
- 回到 App 时如果休息 timer 已过期，会显示“休息结束，等待手动继续”，不再继续卡在倒计时或自动跳转下一组。
- debug-only 休息提醒自测仍走正式 `RestTimerService -> AlarmManager -> RestTimerReceiver -> 通知/震动` 链路。

## 输入报告

- 修复报告：`work/thread-results/1-training-timer/20260502-background-rest-timer-crash-fix.md`
- 验证证据：`work/thread-results/7-quality-env/20260502-background-timer-crash-20260502-040526`

## 验证结果

- `assembleDebug`: passed
- `testDebugUnitTest`: passed
- `assembleDebugAndroidTest`: passed
- `compileReleaseKotlin`: passed
- 7 线程 emulator UI flow instrumentation: `OK (1 test)`
- 7 线程证据中 `RestTimerReceiver: True`
- 7 线程证据中 `ForegroundServiceStartNotAllowedException: False`
- 7 线程证据中 `FATAL EXCEPTION: False`
- 7 线程证据中 `First production app stack frame present: False`

## 注意事项

- 本版 APK 是 debug 包，包含 debug 自测入口。
- 本次没有连接或操作用户的物理 5G 移动终端。
- Android 厂商省电、通知权限、精确闹钟权限和勿扰模式仍可能影响真实手机表现，安装后建议重点复测“切后台等待休息结束”和“震动后点击进入下一组/结束训练”。
- 旧版本 APK 与旧 release-chain 目录均已保留。
