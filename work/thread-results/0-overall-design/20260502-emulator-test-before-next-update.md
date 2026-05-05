# 2026-05-02 v1.1.18 模拟器复测记录

## 结论

已在本地 Android Emulator `emulator-5584` 上测试当前 `v1.1.18 / versionCode 20`，本轮不生成新版本。

模拟器未复现用户实机反馈的两类问题：

- 休息倒计时切后台后不触发提醒。
- 休息结束后点击“进入下一组/结束训练”直接闪退。

这说明当前问题很可能依赖实机环境，例如厂商后台限制、通知/震动策略、系统省电、权限状态，或真实交互路径与现有自动化路径不同。不能仅凭模拟器通过就认为实机问题已解决。

## 测试范围

### 1. 训练 UI flow instrumentation

证据目录：
`work/thread-results/7-quality-env/20260502-background-timer-manual-emulator-check-20260502-131507`

覆盖路径：

- 安装当前 debug APK 与 androidTest APK。
- 创建 2 组测试训练。
- 点击开始训练。
- 完成本组进入 30 秒休息。
- 将 App 切后台等待 90 秒。
- 回前台点击“开始下一项”。
- 再点击“结束训练”并确认返回今日计划。

结果：

- `instrumentation-ui-flow.txt`: `OK (1 test)`
- `RestTimerReceiver: True`
- `ForegroundServiceStartNotAllowedException: False`
- `FATAL EXCEPTION: False`
- `First production app stack frame present: False`

### 2. 非 instrumentation debug 自测手势

证据目录：
`work/thread-results/7-quality-env/20260502-debug-selftest-emulator-manual-132057`

覆盖路径：

- 启动 debug-only `RestReminderSelfTestActivity`。
- adb 模拟点击“启动 10 秒自测”。
- adb 模拟 Home 切后台。
- 等待超过 10 秒倒计时。
- 抓取 logcat、dumpsys alarm、dumpsys notification。
- 回到 App 并点击“关闭震动”。

关键证据：

- `RestTimerAlarmScheduler`: 成功注册 `com.codex.streetstrength.timer.FINISH` alarm。
- `RestTimerReceiver`: 在后台收到 `Received rest finish alarm for timer 7`。
- `dumpsys notification`: 已出现 `pkg=com.codex.streetstrength id=401 channel=rest_finished`。
- 通知内容为 `休息结束` / `回到训练页后手动开始下一项`。
- UI 回前台后显示 `提醒和震动已关闭`。

限制：

- 模拟器没有可用的 `dumpsys vibrator` 服务，不能证明真实硬件震动，只能证明 App 执行了提醒链路。
- 模拟器未复现实机的后台卡死和点击闪退，因此下一步仍需要实机 crash-time 证据。

## 下一步分工建议

- `7-测试、环境与质量线程`：在实机复现时抓取 crash-time logcat、`dumpsys alarm`、`dumpsys notification`、`cmd appops get com.codex.streetstrength`、`dumpsys deviceidle`，并标注点击时间点。
- `1-训练执行与计时线程`：拿到实机日志后定位后台 timer/Receiver/Service/通知点击/训练页按钮路径。
- `2-计划编辑与训练编排线程`：仅当实机日志指向任务索引、轮次、下一组计算时介入。
- `4-数据、备份与本地存储线程`：仅当实机日志指向 Room、Repository、session/timer 状态不一致时介入。

## 当前建议

先不要更新版本。下一步应优先做实机带日志复现，尤其是：

- 切后台 8 秒后通知倒计时停止时的日志。
- 到点未震动时的 alarm/notification/appops 状态。
- 点击“进入下一组”或“结束训练”闪退瞬间的 `FATAL EXCEPTION` 栈。
