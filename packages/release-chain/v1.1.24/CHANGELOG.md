# v1.1.24

## 修复
- 修复真实训练流程中后台休息倒计时可能只更新数据库/通知、未重新拉起休息计时前台服务的问题。
- 修复训练页回到前台发现休息已过期时直接 `markRestTimerFired`，导致未经过 `RestTimerReceiver`、无法可靠触发结束提醒和持续震动的问题。

## 验证
- `assembleDebug` 通过。
- `testDebugUnitTest` 通过。
- 在真机 `bbc478f4` 上验证 debug 10 秒后台自测：后台收到 `RestTimerReceiver`，并由 `com.codex.streetstrength` 主动发起振动。

## 备注
- 无 Room schema 变更，不需要数据库迁移。
- 不删除旧版本，v1.1.24 APK 已放入本目录。
