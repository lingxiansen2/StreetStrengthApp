# 2026-05-04 第 6 线程：真机后台休息计时 UI 与交互

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`

本线程未修改 `RestTimerService`、`AlarmManager`、BroadcastReceiver、Room/Repository 数据结构或 release-chain。

## UI 状态表现

### 训练中

- 焦点页主状态显示“训练中”，副标题显示训练已开始时长。
- 主按钮为“完成本组”，继续沿用现有 `onCompleteSet` 回调。
- “跳过本组”在非休息状态可用。

### 休息倒计时中

- 焦点页主状态显示“休息倒计时中”，副标题显示“剩余 MM:SS”。
- 详情页状态卡显示“休息倒计时中 · 剩余 MM:SS”。
- 主按钮为“跳过休息”，继续沿用现有 `onSkipRest` 回调。
- “跳过本组”在休息中禁用，避免绕过休息状态机。

### 休息结束 / 震动提醒可能正在进行

- 焦点页主状态显示“休息结束”，副标题显示“提醒已触发，等待手动确认”。
- 详情页状态卡显示“休息结束 · 提醒已触发，等待手动确认”。
- 休息结束详情块显示 `00:00`，并提示“进入下一组会停止震动提醒”。
- 主按钮改为“停止震动并进入下一组”，仍调用现有 `onSkipRest`，不改变底层停止震动链路。

## 结束训练交互

- 保留“结束训练”二次确认弹窗。
- 训练页和焦点页的“结束训练”按钮文字使用错误色，作为危险操作提示。
- 确认弹窗文案明确会退出并停止休息提醒，不改变训练状态机。

## 小屏可用性

- 焦点页改为“上方内容区可滚动 + 底部主操作固定”的结构。
- 详情页同样改为“详情内容可滚动 + 底部主操作固定”的结构。
- 小屏上主状态卡不会把主按钮挤出屏幕；长详情、权限提示和训练要点进入滚动区域。

## 需要 1 线程提供的数据或回调

- 当前 UI 只能根据 `isRestComplete` 推断“提醒已触发 / 震动可能正在进行”。如需更精确文案，1 线程应提供独立状态，例如 `isRestAlertActive` 或 `restAlertState`。
- 后台提醒权限和系统限制的最终检测结果应由 1/7 线程提供到 UI state；本线程没有新增底层权限检测业务。
- “停止震动并进入下一组”目前复用 `onSkipRest`。1 线程需要保证该回调统一清理 active timer、通知、Alarm 和 vibration。

## 验证记录

- 已阅读最新任务单：`work/thread-results/0-overall-design/20260504-real-device-background-timer-vibration-share.md`。
- 已阅读最新发布链：`packages/release-chain/v1.1.20/CHANGELOG.md`，当前版本记录为 `1.1.20 / versionCode 22`。
- 执行 `scripts/gradlew-local.cmd assembleDebug`：第一次 120 秒超时，第二次在 `compileDebugKotlin` 阶段收到 Gradle daemon stop。
- 执行 `scripts/gradlew-local.cmd --no-daemon assembleDebug`：暴露 Kotlin 增量缓存损坏，错误包含 `storage size = 0, file size = 4096` 和无法删除 `app/build/kotlin/kaptGenerateStubsDebugKotlin/cacheable/caches-jvm`。
- 执行 `scripts/gradlew-local.cmd clean assembleDebug` 和 `scripts/gradlew-local.cmd --stop` 后重试：仍受 Gradle daemon / Kotlin cache 问题阻断。

结论：本线程 UI 改动已完成，但本机 Gradle 验证当前受 Kotlin/Gradle 缓存或并发 daemon 状态阻断，未拿到通过的 `assembleDebug` 结果。
