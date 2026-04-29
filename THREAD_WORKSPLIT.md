# StreetStrengthApp 线程分工文档

## 目标

这个文档用于把 StreetStrengthApp 拆成多个长期线程。每个线程只负责一个稳定模块，减少上下文过大、重复搜索、互相覆盖文件的问题。

当前主项目位置：

- 源码：`packages/StreetStrengthApp-source-20260422`
- Android App：`packages/StreetStrengthApp-source-20260422/app`
- 发布链：`packages/release-chain`
- 本地工具：`.local-tools`

## 总体规则

- 每个线程启动时先阅读本文件、`README.md`、最近一个 `packages/release-chain/v*/CHANGELOG.md`。
- 每个线程默认只修改自己负责的文件范围。
- 如果必须跨模块改文件，先在回复里说明原因、风险和涉及文件。
- 不删除旧 APK，不删除 `packages/release-chain` 里的历史版本。
- 每次可安装版本都递增 `versionName` 和 `versionCode`，并新建 `packages/release-chain/vX.Y.Z`。
- 涉及数据库结构变化时，必须先写 Room Migration，不允许 destructive migration。
- 涉及计时、通知、闹钟、震动时，必须同时考虑前台、后台、锁屏、通知权限、精确闹钟权限。
- 线程完成后必须说明验证结果。最低要求是 `assembleDebug`，核心逻辑变化还要跑 `testDebugUnitTest`。

## 推荐线程

### 0. 总控与发布线程

职责：

- 管理版本号、发布链、APK 产出、CHANGELOG。
- 汇总其他线程的改动，决定发布顺序。
- 维护 `README.md` 和本分工文档。
- 跑最终构建、单测、模拟器或真机验证。

主要文件：

- `README.md`
- `THREAD_WORKSPLIT.md`
- `packages/release-chain/**`
- `packages/StreetStrengthApp-source-20260422/app/build.gradle.kts`

不建议做：

- 不直接深改业务逻辑，除非是集成其他线程结果。

### 1. 训练执行与计时线程

职责：

- 训练中界面、组/轮推进、休息流程。
- 后台倒计时、通知、震动、AlarmManager、ForegroundService。
- “休息结束必须手动进入下一组”等训练中行为。
- 训练中沉浸 UI 和详情 UI 的协调。

主要文件：

- `app/src/main/java/com/codex/streetstrength/ui/training/**`
- `app/src/main/java/com/codex/streetstrength/timer/**`
- `app/src/main/AndroidManifest.xml`
- `app/src/androidTest/java/com/codex/streetstrength/timer/**`

重点风险：

- Android 后台限制、通知权限、精确闹钟权限。
- 计时不能依赖 Compose 页面存活。
- 不能自动跳过用户需要手动确认的休息结束状态。

建议验证：

- `assembleDebug`
- `testDebugUnitTest`
- `assembleDebugAndroidTest`
- 模拟器测试 `AlarmManager -> RestTimerReceiver -> 通知`

### 2. 计划编辑与训练编排线程

职责：

- 制定计划、编辑计划、删除/复制/调整顺序。
- 支持“循环组合动作”和“顺序完成动作”两种训练模式。
- 每组递减、负重、间歇、次数/时长输入。
- 已过期计划不可开始、未来计划文案和状态。

主要文件：

- `app/src/main/java/com/codex/streetstrength/ui/planner/**`
- `app/src/main/java/com/codex/streetstrength/ui/calendar/**`
- `app/src/main/java/com/codex/streetstrength/domain/**`
- `app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt`

重点风险：

- 计划结构一旦改 Room 表，需要 Migration。
- 训练执行线程依赖这里产出的任务顺序和模式。
- UI 编辑能力不能破坏旧计划记录。

建议验证：

- 新建计划、编辑顺序、改变模式、保存。
- 从计划进入训练，确认训练顺序符合预期。

### 3. 训练库与动作内容线程

职责：

- 训练库排版、搜索、分类、收藏。
- 动作库扩充、动作要点、教程入口。
- 自定义动作创建和分类选择。
- 街健动作内容质量维护。

主要文件：

- `app/src/main/java/com/codex/streetstrength/ui/library/**`
- `app/src/main/java/com/codex/streetstrength/ui/components/CustomExerciseDialog.kt`
- `app/src/main/java/com/codex/streetstrength/data/model/Enums.kt`
- `app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt`
- `app/src/main/java/com/codex/streetstrength/data/preferences/PreferencesRepository.kt`

重点风险：

- 新增动作应通过 seed 补齐，不破坏旧用户数据。
- 外部教程入口不把视频塞进 APK。
- 搜索和分类要适合单手快速定位。

建议验证：

- 旧数据库启动后能看到新增动作。
- 搜索、分类、常用收藏都可用。

### 4. 数据、备份与本地存储线程

职责：

- Room Entity、DAO、Repository 数据一致性。
- DataStore 用户设置。
- 导出已训练记录、导出计划、备份恢复。
- 本地数据兼容和迁移策略。

主要文件：

- `app/src/main/java/com/codex/streetstrength/data/**`
- `app/schemas/**`

重点风险：

- 不允许删除用户本地设置。
- 不允许 fallback destructive migration。
- 导出格式要稳定，后续能恢复或人工读取。

建议验证：

- 新旧版本升级后计划、记录、收藏、设置保留。
- 导出 JSON 可读，字段完整。

### 5. 总览、统计与进度线程

职责：

- 总览页设计、周/月统计、训练趋势。
- 按动作、部位、目标统计训练量。
- 训练完成率、计划执行率、负重/次数/时长趋势。
- 街健目标相关指标，例如引体、倒立撑、核心保持时长。

主要文件：

- `app/src/main/java/com/codex/streetstrength/ui/overview/**`
- `app/src/main/java/com/codex/streetstrength/domain/**`
- `app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt`

重点风险：

- 统计口径必须清楚，避免“看起来好看但不可信”。
- 空数据、少量数据、历史数据都要显示合理。

建议验证：

- 无训练数据、当天数据、多周数据都能正常显示。
- 总览统计和训练日志能对上。

### 6. UI 视觉与交互系统线程

职责：

- 全局视觉语言、主题、组件一致性。
- 按钮、卡片、Chip、输入弹窗、空状态。
- 训练中高可读 UI、单手操作、长列表体验。
- 横竖屏、不同尺寸手机适配。

主要文件：

- `app/src/main/java/com/codex/streetstrength/ui/theme/**`
- `app/src/main/java/com/codex/streetstrength/ui/components/**`
- 各页面 UI 文件，但跨页面改动需提前说明。

重点风险：

- 不要为了视觉效果牺牲训练中可读性。
- 训练页按钮必须明确，避免误触结束训练。
- 组件化改动要避免大范围破坏页面布局。

建议验证：

- 小屏手机、正常手机、长列表页面。
- 训练中页面在运动时一眼能看懂当前状态。

### 7. 测试、环境与质量线程

职责：

- 本地 Gradle、JDK、Android SDK、模拟器环境。
- 单元测试、instrumentation 测试、回归清单。
- 构建失败、依赖问题、模拟器问题。
- 发布前验收脚本或手动测试清单。

主要文件：

- `.local-tools/**`
- `packages/StreetStrengthApp-source-20260422/scripts/**`
- `app/src/test/**`
- `app/src/androidTest/**`
- `app/build.gradle.kts`

重点风险：

- 不要操作外接物理设备，除非用户明确允许。
- ADB 命令必须指定模拟器序列号或明确设备。
- 模拟器能验证通知链路，但不能真实验证手机震动马达。

建议验证：

- `assembleDebug`
- `testDebugUnitTest`
- `assembleDebugAndroidTest`
- 指定测试类的 `am instrument`

### 8. 后续联网、账号与云同步线程

职责：

- 未来登录、用户系统、云备份、跨设备同步。
- API 设计、服务器方案、本地优先同步策略。
- 隐私、鉴权、离线兼容。

主要文件：

- 当前先不直接改主 App。
- 可在 `work/` 下写方案文档或接口草案。

重点风险：

- 不要过早把本地训练 App 改成强联网。
- 账号和云同步必须不影响离线训练。
- 服务器、数据库、鉴权要先设计再实现。

建议阶段：

- 第一阶段只写方案。
- 第二阶段做本地 mock。
- 第三阶段再接真实服务器。

## 推荐开线程方式

线程名称建议：

- `StreetStrength 总控与发布`
- `StreetStrength 训练执行与计时`
- `StreetStrength 计划编辑与编排`
- `StreetStrength 训练库与动作内容`
- `StreetStrength 数据备份与存储`
- `StreetStrength 总览统计`
- `StreetStrength UI 视觉系统`
- `StreetStrength 测试环境与质量`
- `StreetStrength 联网账号方案`

每个线程开头建议粘贴：

```text
请先阅读 E:\Workspace\GitHub\StreetStrengthApp\THREAD_WORKSPLIT.md。
你负责其中的【模块名】。
只修改该模块负责文件，跨模块改动前先说明原因。
不要删除旧 APK，不要破坏本地数据。
完成后说明改动、验证、风险。
```

## 跨线程交接格式

每个线程结束时建议输出：

```text
模块：
目标：
已改文件：
新增文件：
验证结果：
需要总控集成的点：
需要其他线程注意的点：
风险：
```

## 推荐集成顺序

1. 数据、备份与本地存储线程。
2. 计划编辑与训练编排线程。
3. 训练执行与计时线程。
4. 训练库与动作内容线程。
5. 总览、统计与进度线程。
6. UI 视觉与交互系统线程。
7. 测试、环境与质量线程。
8. 总控与发布线程做最终集成和 APK。

如果某次只修紧急 bug，直接由对应模块线程处理，再交给总控发布。

## 当前重点风险清单

- 后台计时必须继续重点回归。
- 组合动作模式和顺序动作模式需要稳定数据结构。
- 训练库动作继续增加后，搜索和分类不能退化。
- 所有导出/备份功能都要保证异常时可恢复。
- 发布链必须保留所有历史版本。

