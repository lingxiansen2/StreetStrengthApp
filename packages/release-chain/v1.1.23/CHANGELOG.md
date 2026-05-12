# v1.1.23

- 修复 v1.1.22 在 OPPO/OPlus 真机上休息倒计时切后台后不主动震动的问题定位与加固。
- 新增 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限，并在训练页后台提醒卡片中检测“忽略电池优化/后台权限”状态。
- 当系统未允许忽略电池优化时，App 会明确提示 ColorOS/OPlus 可能延后休息结束闹钟，并提供后台权限设置入口。
- 休息结束震动改为每次触发都重新启动连续震动，避免系统或其他通知打断后状态仍被误判为 active。
- `RestTimerService` 销毁时如果休息结束提醒已激活，不再主动取消提醒通知和连续震动，避免 receiver 已触发后又被 service 生命周期清掉。
- 保留 v1.1.22 及更早 APK；本版本不涉及 Room schema 变更，不需要数据库迁移。

## Validation

- `assembleDebug` 通过。
- `testDebugUnitTest` 通过。
- 真机抓取证据见 `work/thread-results/7-quality-env/20260506-v1122-real-training-5min-20260506-215236`：v1.1.22 已注册 `com.codex.streetstrength.timer.FINISH`，但 OPlus 系统将 alarm policy 延后约 3 天，且 app 不在 device idle whitelist。
