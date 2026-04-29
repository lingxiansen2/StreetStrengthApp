# 线程交付报告

- 线程：总体设计与集成线程
- 目标：记录 v1.1.17 用户实测反馈，并分派给对应修复线程
- 修改文件：
  - 无 App 源码修改
- 新增文件：
  - `work/thread-results/0-overall-design/20260429-2035-training-timer-regression-routing.md`
- 验证命令：
  - 未运行构建；本次为问题分派记录
- 验证结果：
  - 不适用
- 需要总体设计集成的点：
  - 等 `1-training-timer` 修复完成后，需要重新整合、升版本、生成新 APK
- 需要其他线程注意的点：
  - 主要归属：`1-训练执行与计时线程`
  - 如果 crash 日志显示数据库/Session 写入异常，再转 `4-数据、备份与本地存储线程`
- 风险或未完成事项：
  - 当前缺少 logcat 崩溃堆栈，`结束训练 app 自动关闭` 需要线程 1 复现并抓日志

## 用户实测问题

版本：`v1.1.17 debug`

问题 1：休息倒计时后台不继续触发

- 进入休息倒计时后切到其他应用或桌面。
- 大约 8 秒后，顶端通知里的倒计时停止刷新。
- 如果休息时间到了但用户没切回 App，不会震动。
- 只有切回 App 后，计时才继续走，并触发震动。

预期：

- 切后台后不依赖 Compose 页面继续计时。
- 休息结束必须由后台链路触发通知和持续震动。
- 通知不能卡住，也不能等回到 App 才触发。

问题 2：结束训练导致 App 自动关闭

- 训练中点击“结束训练”。
- App 自动关闭，疑似崩溃或 Activity 被异常 finish。

预期：

- 点击结束训练应稳定结束 session，停止计时/震动/通知，回到日历/今日/总结页。
- 不应崩溃，不应直接关闭 App。

## 给 `1-训练执行与计时线程` 的提示词

```text
你是 StreetStrengthApp 的【1-训练执行与计时线程】。请优先修复 v1.1.17 用户实测的两个回归问题。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\1-training-timer

请先读取：
1. THREAD_PROMPTS.md
2. THREAD_WORKSPLIT.md
3. packages/release-chain/v1.1.17/CHANGELOG.md
4. work/thread-results/0-overall-design/20260429-2035-training-timer-regression-routing.md

问题 A：休息倒计时后台不触发
- 进入休息倒计时后切到桌面/其他 App。
- 大约 8 秒后通知顶部倒计时停止。
- 时间到了如果没切回 App，不会震动。
- 只有切回 App 后才继续走并震动。

修复要求：
- 休息结束不能依赖 Compose 页面、Activity 生命周期或 UI coroutine。
- 休息结束必须由后台可靠链路触发，例如 AlarmManager -> BroadcastReceiver -> startForegroundService(ACTION_FINISH) -> 通知 + 持续震动。
- 检查 debug 自测入口是否真的会在后台触发正式 Receiver，而不是只写入数据后等待 App 恢复。
- 检查 RestTimerService 是否因前台服务类型、通知权限、Alarm PendingIntent、receiver exported/permission、Doze/精确闹钟权限或 service lifecycle 导致后台失效。
- 通知倒计时可以停止刷新，但休息结束提醒不能因此失效；必要时通知显示固定结束时间，不依赖 chronometer。

问题 B：点击“结束训练”App 自动关闭
- 请复现并抓 logcat。
- 判断是 crash、Activity finish 后无返回栈、还是异常导航。
- 修复结束训练流程：停止休息计时、取消通知/震动、保存 session 状态，然后回到合理页面。
- 如果堆栈指向 Repository/Room 写入，再在报告中标记需要 `4-data-storage` 协助。

最低验证：
- assembleDebug
- testDebugUnitTest
- assembleDebugAndroidTest
- 尽量用模拟器验证：进入休息倒计时 -> 切后台超过倒计时 -> 不回 App 也应触发通知/提醒链路。

完成后请把报告写到 work/thread-results/1-training-timer，说明修改文件、根因、验证结果和仍需真机确认的点。
```
