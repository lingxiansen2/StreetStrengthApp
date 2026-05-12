# v1.1.25

## 修复
- 修复休息结束提醒在重复 FINISH 投递时可能重复启动/重启震动的问题。
- 修复点击「关闭提醒」或从 App 内停止提醒后，迟到的 Receiver/Service 仍可能重新拉起同一 timer 震动的问题。
- 修复回到 App 后过期休息计时补偿路径可能绕过 Receiver、直接标记 FIRED 的风险。
- 优化训练页休息结束状态：区分「只关闭提醒」和「停止提醒并进入下一组」，并增加按钮处理中状态防止连点。
- 优化结束训练确认文案，明确会先停止当前休息提醒或震动。

## 集成来源
- `work/thread-results/1-training-timer/20260508-rest-vibration-reliability.md`
- `work/thread-results/6-ui-interaction/20260508-rest-alert-stop-ui.md`
- `work/thread-results/7-quality-env/20260508-v1124plus-10x-rest-vibration-loop`
- `work/thread-results/7-quality-env/20260508-v1124plus-10x-filtered-ui-stop`
- `work/thread-results/7-quality-env/20260508-v1124plus-3x-dedup-stop-check`

## 验证
- `assembleDebug` 通过。
- `testDebugUnitTest` 通过。
- `assembleDebugAndroidTest` 通过。
- `aapt dump badging` 确认 APK 内版本：`versionCode=27`，`versionName=1.1.25`。

## 备注
- 无 Room schema 变更，不需要数据库迁移。
- 未删除旧版本，历史发布链从 `v1.1.15` 保留到 `v1.1.25`。
