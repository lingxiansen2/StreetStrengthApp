# v1.1.19

- Version: `1.1.19`
- VersionCode: `21`
- APK: `app-debug-20260504-v1.1.19.apk`
- Date: `2026-05-04`

## 本版整合范围

本版必须进入新 APK，不能继续沿用 `v1.1.18` 发布包。

主要修复 debug APK 中休息通知“打开训练/返回训练”误进入 `RestReminderSelfTestActivity` 自测页的问题。

## 主要更新

- 休息结束通知的 content intent 显式启动 `MainActivity`。
- 休息结束通知的“打开训练” action 显式启动 `MainActivity`。
- 运行中休息通知的 content intent 显式启动 `MainActivity`。
- `AlarmManager.setAlarmClock(...)` 的 show intent 显式启动 `MainActivity`。
- debug-only `RestReminderSelfTestActivity` 保留可用，但移除 `MAIN` / `LAUNCHER` 入口，避免被系统当作默认启动页。

## 数据与迁移

- 不需要 Room migration。
- 本版没有数据库结构变更。
- 本地训练计划、训练记录、DataStore 设置继续保留。

## 验证结果

- `assembleDebug`: passed
- `testDebugUnitTest`: passed
- `assembleDebugAndroidTest`: passed
- `compileReleaseKotlin`: passed
- `aapt dump badging`: `versionCode='21' versionName='1.1.19'`
- `aapt dump badging`: `launchable-activity='com.codex.streetstrength.MainActivity'`
- `aapt dump xmltree`: debug APK 中 `RestReminderSelfTestActivity` 存在，但只有 `MainActivity` 拥有 `MAIN` / `LAUNCHER` intent-filter。

## 注意事项

- 本版 APK 是 debug 包，仍包含 debug 自测 Activity，但不会再作为通知返回目标或桌面启动入口。
- 未连接或操作用户的物理 5G 移动终端。
- 旧版本 APK 与旧 release-chain 目录均已保留。
