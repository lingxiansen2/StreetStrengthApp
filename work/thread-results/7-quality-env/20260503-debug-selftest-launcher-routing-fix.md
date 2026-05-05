# Debug 自测入口误路由修复

## 背景

在可见 emulator 上完成正式训练流程后，点击休息结束通知的“打开训练/返回训练”回到了 `休息提醒自测` 页面。该页面是 debug-only 的 `RestReminderSelfTestActivity`，不应作为正式训练提醒的返回目标。

## 根因

debug APK 中同时存在两个 `LAUNCHER` activity：

- `MainActivity`
- `RestReminderSelfTestActivity`

休息通知和 alarm-clock show intent 使用 `packageManager.getLaunchIntentForPackage(...)` 时，debug 包可能解析到自测入口，导致“返回训练”进入自测页面。

## 修改

- `RestTimerAlert.kt`
  - 通知 content intent 和“打开训练” action 改为显式启动 `MainActivity`。
- `RestTimerService.kt`
  - 运行中通知 content intent 改为显式启动 `MainActivity`。
- `RestTimerAlarmScheduler.kt`
  - `setAlarmClock` 的 show intent 改为显式启动 `MainActivity`。
- `app/src/debug/AndroidManifest.xml`
  - 移除 `RestReminderSelfTestActivity` 的 `MAIN` / `LAUNCHER` intent-filter。
  - 自测页仍可通过 adb 显式启动。
- `scripts/QUALITY_CHECKLIST.md`
  - 补充自测页 adb 启动方式。

## 验证

- `gradlew-local.cmd --max-workers=1 clean assembleDebug`: PASS
- merged debug manifest 已确认：
  - `RestReminderSelfTestActivity` 仍存在。
  - 只有 `MainActivity` 保留 `android.intent.action.MAIN` / `android.intent.category.LAUNCHER`。

## 未完成

尝试将新 debug APK 安装到当前 `emulator-5584` 时，`adb install` 超时；随后 `adb shell echo ok` 也超时。ADB 设备状态仍显示 `device`，但 shell/package manager 不响应。需要重启 emulator 后再安装本次新 APK 进行手动确认。
