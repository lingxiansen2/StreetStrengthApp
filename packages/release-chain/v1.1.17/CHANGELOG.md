# v1.1.17

- Version: `1.1.17`
- VersionCode: `19`
- APK: `app-debug-20260429-v1.1.17.apk`
- Date: `2026-04-29`

## 本版整合范围

本版整合 `1-训练执行与计时线程` 和 `2-计划编辑与训练编排线程` 的 debug 测试能力。

## 主要更新

- 新增 debug-only 休息提醒自测入口。
- 自测入口提供 10 秒休息倒计时，用于不依赖真实训练计划地验证休息结束通知和持续震动链路。
- 自测链路调用正式 `RestTimerService`、`RestTimerReceiver`、通知和震动逻辑，不使用页面内假计时。
- debug 构建下，当天训练完成后仍允许继续新增/调整/开始当天测试计划，用于反复测试计时和提醒。
- release 构建保留正式限制：当天已完成后不允许重复开始正式训练。
- debug 下重新测试会新建 `IN_PROGRESS` session，避免复用已完成 session 导致无法重测。

## 验证结果

- `assembleDebug`: passed
- `testDebugUnitTest`: passed
- `assembleDebugAndroidTest`: passed
- `compileReleaseKotlin`: passed

## 注意事项

- 本版 APK 是 debug 包，会包含额外的 debug 自测入口。
- 未连接或操作物理 5G 移动终端。
- 本次完成本地构建与测试包编译验证；实际手机震动马达表现仍建议安装后手动确认。
- 旧版本目录和旧 APK 均已保留。
