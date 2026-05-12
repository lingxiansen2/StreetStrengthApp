# 2026-05-08 第 6 线程：休息结束提醒 UI 与交互

## 线程

6-UI 设计与交互

## 目标

根据 1 线程的计时语义，优化训练页在休息结束后的状态提示和操作入口，重点避免：

- 用户连点“进入下一组”导致重复停止/清理。
- “关闭提醒”和“进入下一组”语义混在一起。
- 从通知回到 App 后，用户仍以为提醒正在震动。
- 结束训练时不清楚是否会停止当前休息提醒。

## 修改文件

- `packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/TrainingScreen.kt`

## 新增文件

- `work/thread-results/6-ui-interaction/20260508-rest-alert-stop-ui.md`

## 具体 UI 变化

### 休息倒计时中

- 主按钮仍为“跳过休息”。
- 点击后按钮短暂进入“正在跳过休息...”并禁用，防止连点。
- “跳过本组”保持禁用，避免休息状态下绕过训练状态机。

### 休息结束，提醒仍可能在响

- 主状态显示“休息结束 / 提醒已触发，等待手动确认”。
- 主按钮改为“停止提醒并进入下一组”，语义是：停止通知/震动，并清理当前 rest timer 进入下一组。
- 主按钮点击后短暂显示“正在进入下一组...”并禁用，防止重复点击。
- 新增次级按钮“只关闭提醒，不进入下一组”，语义是：只停止通知/震动，不清理当前 rest timer，不推进训练。
- 详情页休息结束提示改为“可只关闭提醒，或直接停止提醒并进入下一组”。

### 提醒已关闭，等待继续

- UI 根据 1 线程记录的 stopped `timerId` 显示“提醒已关闭，等待进入下一组”。
- 主按钮在此状态下变为“进入下一组”，不再暗示仍有提醒或震动需要停止。
- 次级提醒按钮显示“提醒已关闭”并禁用，避免重复停止。
- 从通知“打开训练”回到 App 后，如果 MainActivity 已停止该 timer 的提醒，训练页能显示已关闭状态。

### 结束训练

- “结束训练”仍是危险色和二次确认。
- 确认文案改为“当前训练会退出，并先停止当前休息提醒或震动”，明确说明会关闭当前提醒。

## 是否触碰业务/计时代码

- 未修改 `RestTimerService`、`RestTimerReceiver`、`RestTimerAlert`、`RestTimerController`、AlarmManager 注册或 Room/Repository。
- UI 线程只在 `TrainingScreen.kt` 内读取 1 线程已有的 `RestTimerAlert.isStopRequested(context, timerId)`，用于显示“提醒已关闭”。
- 新增 App 内“只关闭提醒”入口复用现有 `RestTimerController.stopRestAlert(timerId)` 链路，不清理 rest timer，不推进训练。

## 验证命令

- `scripts/gradlew-local.cmd assembleDebug`
- `scripts/gradlew-local.cmd testDebugUnitTest`

## 验证结果

- `assembleDebug` 通过。
- `testDebugUnitTest` 通过。

## 手动检查

- 静态检查了焦点页和详情页的三类状态文案：
  - 休息倒计时中。
  - 休息结束，提醒待确认。
  - 提醒已关闭，等待进入下一组。
- 静态检查了底部按钮顺序：主操作始终在最上方，次级“只关闭提醒”只在休息结束状态出现，不影响训练中的主操作可见性。
- 本线程未做真机截图；真机交互验证仍应由 7 线程覆盖。

## 需要其他线程注意

- 1 线程需要继续保证 `onSkipRest` 对 active `timerId` 的清理、通知、震动停止链路保持幂等。
- 7 线程真机验证时应覆盖：
  - 通知打开训练后 UI 是否显示“提醒已关闭”。
  - App 内点“只关闭提醒，不进入下一组”后是否停止震动且仍停留在休息结束状态。
  - 再点“进入下一组”后是否清理 timer 并进入下一组。
  - 连点主按钮时是否不会重复触发提醒或重复推进。

## 风险或未完成事项

- UI 的“提醒已关闭”依赖 1 线程的 stopped timerId 记录；如果未来 stopped 状态改成多 timer 集合或持久化策略变化，UI 派生逻辑需要同步。
- 未生成 APK，未修改版本号，未修改 release-chain；发布应交给总体设计线程。
