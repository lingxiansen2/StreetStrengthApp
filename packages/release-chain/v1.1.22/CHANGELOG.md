# v1.1.22

## 本版重点

- 修复 `v1.1.21` 发布包在真机后台休息结束时可能不震动的回归。
- 将休息结束提醒前移到 `RestTimerReceiver`：Alarm 到点后 Receiver 立即发出休息结束通知并启动持续震动。
- `RestTimerService` 仍会继续接管前台服务通知；如果 Service 再次请求震动，会被 `RestTimerAlert.vibrationActive` 拦截，避免重复震动 token。
- 保留 `1` 线程已经验证过的 WakeLock、绝对时间倒计时、静默通知通道和统一停止入口。

## 版本

- `versionName`: `1.1.22`
- `versionCode`: `24`

## 验证

- `assembleDebug` 通过。
- `testDebugUnitTest` 通过。
- `assembleDebugAndroidTest` 通过。
- APK 元数据检查通过：`1.1.22 / 24`。

## 真机复测说明

- 当前只检测到 `97257126520013`，这是之前禁止使用的 5G 移动终端，本次未对它执行安装或复测。
- 需要在 `bbc478f4` 手机上安装本版后复测正式训练路径：完成本组 -> 进入休息 -> 切后台/锁屏 -> 到点应立即震动。
