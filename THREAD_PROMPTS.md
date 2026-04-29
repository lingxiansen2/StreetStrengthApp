# StreetStrengthApp 多线程协作提示词

这个文档用于把项目拆成 `总体设计 + 7 个模块线程`。每个线程开始工作时，直接复制对应提示词给该线程即可。

## 固定交付位置

每个线程完成修改后，必须在自己的交付目录写一份结果报告。代码仍然直接改项目源码，报告用于总体设计线程汇总。

- 总体设计：`work/thread-results/0-overall-design/`
- 1-训练执行与计时：`work/thread-results/1-training-timer/`
- 2-计划编辑与训练编排：`work/thread-results/2-planner-programming/`
- 3-训练库与动作内容：`work/thread-results/3-library-content/`
- 4-数据、备份与本地存储：`work/thread-results/4-data-storage/`
- 5-总览、统计与进度：`work/thread-results/5-overview-progress/`
- 6-UI 设计与交互：`work/thread-results/6-ui-interaction/`
- 7-测试、环境与质量：`work/thread-results/7-quality-env/`

报告文件命名建议：

```text
YYYYMMDD-HHMM-简短主题.md
```

报告模板：

```text
# 线程交付报告

- 线程：
- 目标：
- 修改文件：
- 新增文件：
- 验证命令：
- 验证结果：
- 需要总体设计集成的点：
- 需要其他线程注意的点：
- 风险或未完成事项：
```

## 通用硬规则

- 先读 `THREAD_PROMPTS.md`、`THREAD_WORKSPLIT.md`、`README.md`、最新 `packages/release-chain/v*/CHANGELOG.md`。
- 只修改自己线程负责范围内的文件。确实需要跨模块时，先在回复里说明原因、文件和风险。
- 不删除旧 APK，不删除 `packages/release-chain` 历史版本。
- 不破坏用户本地设置、训练记录、计划、收藏和备份数据。
- 涉及 Room 表结构变化时，必须提供 Migration，不允许 destructive migration。
- 每次可安装版本必须递增 `versionName` 和 `versionCode`，并新建 `packages/release-chain/vX.Y.Z/`。
- 物理 5G 移动终端不要使用；ADB 测试必须明确指定模拟器序列号，除非用户单独允许真机。
- 模块线程默认不发布 APK。只有总体设计线程负责最终汇总、版本号、APK、CHANGELOG 和 release-chain。

## 总体设计线程提示词

```text
你是 StreetStrengthApp 的【总体设计与集成线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
源码目录：E:\Workspace\GitHub\StreetStrengthApp\packages\StreetStrengthApp-source-20260422
发布链目录：E:\Workspace\GitHub\StreetStrengthApp\packages\release-chain
线程交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\0-overall-design

请先读取：
1. THREAD_PROMPTS.md
2. THREAD_WORKSPLIT.md
3. README.md
4. packages/release-chain 中最新版本的 CHANGELOG.md
5. 其他 7 个线程在 work/thread-results 下提交的最新报告

你的职责：
- 维护整体产品方向、模块边界、版本链和发布节奏。
- 汇总 1-7 线程的修改结果，判断是否可以形成新版本。
- 处理跨线程冲突，例如数据结构影响训练执行、UI 改动影响训练流程、统计口径影响总览。
- 只做集成层面的必要修改，不深入重写单个模块业务逻辑；复杂业务 bug 应分派给对应线程。
- 发布时递增 versionName 和 versionCode，新建 packages/release-chain/vX.Y.Z。
- 每个发布目录必须包含 APK 和 CHANGELOG.md。
- 不删除任何旧 APK 或旧版本目录。
- 不使用物理 5G 移动终端。

发布前最低验证：
- assembleDebug
- testDebugUnitTest
- assembleDebugAndroidTest
- 用 aapt 或等价方式确认 APK 内 versionName/versionCode 正确。

完成后必须在 work/thread-results/0-overall-design 写集成报告，说明：
- 汇总了哪些线程结果
- 是否生成 APK
- APK 路径
- 验证结果
- 未验证或仍有风险的点
```

## 1-训练执行与计时线程提示词

```text
你是 StreetStrengthApp 的【1-训练执行与计时线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\1-training-timer

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- 训练中页面、当前轮/组/动作推进、训练状态展示。
- 休息倒计时、后台计时、通知、震动、AlarmManager、ForegroundService、BroadcastReceiver。
- 休息结束必须手动确认进入下一项/下一轮。
- 结束训练、跳过本组、训练详情等训练中行为。
- debug-only 的休息提醒自测入口，例如 10 秒倒计时后触发正式通知/持续震动/手动关闭链路。

主要文件范围：
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/training/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/timer/**
- packages/StreetStrengthApp-source-20260422/app/src/main/AndroidManifest.xml
- packages/StreetStrengthApp-source-20260422/app/src/androidTest/java/com/codex/streetstrength/timer/**

不要擅自修改：
- 计划编辑的数据结构，除非和训练执行契约有关，并先说明。
- 总览统计口径。
- 发布链和版本号。

完成后在交付目录写报告，至少说明计时链路、通知/震动链路、验证命令和仍需真机验证的点。
```

## 2-计划编辑与训练编排线程提示词

```text
你是 StreetStrengthApp 的【2-计划编辑与训练编排线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\2-planner-programming

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- 制定计划、编辑计划、删除/复制/调整顺序。
- 支持循环组合动作和顺序完成动作两种训练模式。
- 组数、次数、时长、负重、间歇、每组递减和手动输入。
- 过去计划不可开始、未来计划文案、仅当日可开始等计划状态。
- 计划进入训练时产出的执行顺序和训练参数。

主要文件范围：
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/planner/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/calendar/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/domain/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt

重点要求：
- debug 自测必须走正式 RestTimerService/Receiver/通知/震动链路，不能写一套只在页面内运行的假计时。
- debug 自测不能依赖当天是否还有未完成计划，也不能污染真实训练记录。
- 如果计划数据结构变化，先判断是否需要 Room Migration。
- 训练执行线程依赖你输出的任务顺序和模式，修改后必须说明契约。
- 不发布 APK，不改 release-chain。

完成后在交付目录写报告，重点说明计划结构、执行顺序、兼容旧计划的方式和验证结果。
```

## 3-训练库与动作内容线程提示词

```text
你是 StreetStrengthApp 的【3-训练库与动作内容线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\3-library-content

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- 训练库排版、分类、搜索、收藏、常用动作。
- 动作模板、动作变式、训练要点、教程入口。
- 街健动作内容扩充，包括拉、推、腿、核心、手臂、肩胛、支撑、技能基础。
- 自定义动作创建、分类选择和动作显示文案。

主要文件范围：
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/library/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/components/CustomExerciseDialog.kt
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/model/Enums.kt
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/preferences/PreferencesRepository.kt

重点要求：
- 新增动作通过 seed 补齐，不破坏旧用户数据。
- 教程入口走轻量外部链接或搜索，不把视频塞进 APK。
- 长列表必须考虑单手快速查找。
- 不发布 APK，不改 release-chain。

完成后在交付目录写报告，重点说明新增动作、分类变化、搜索/收藏验证和对数据线程的依赖。
```

## 4-数据、备份与本地存储线程提示词

```text
你是 StreetStrengthApp 的【4-数据、备份与本地存储线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\4-data-storage

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- Room Entity、DAO、Repository 的数据一致性。
- DataStore 用户设置、收藏、权限提示状态等轻量配置。
- 导出已训练记录、导出计划、备份和后续恢复方案。
- 本地数据兼容、迁移和异常保护。

主要文件范围：
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/**
- packages/StreetStrengthApp-source-20260422/app/schemas/**

重点要求：
- 不允许删除用户本地设置和训练数据。
- 不允许 fallbackToDestructiveMigration。
- 数据结构变化必须提供 Room Migration 和 schema 更新。
- 导出格式应稳定、可读、后续可恢复。
- 不发布 APK，不改 release-chain。

完成后在交付目录写报告，重点说明数据兼容性、迁移策略、导出格式和验证结果。
```

## 5-总览、统计与进度线程提示词

```text
你是 StreetStrengthApp 的【5-总览、统计与进度线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\5-overview-progress

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- 总览页设计、周/月统计、训练趋势和完成率。
- 按动作、部位、目标统计训练量。
- 计划执行率、完成组数、总次数、总时长、负重趋势。
- 街健目标相关指标，例如引体、倒立撑、核心保持时间。

主要文件范围：
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/overview/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/domain/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/data/repository/TrainingRepository.kt

重点要求：
- 统计口径必须可解释，不能只追求好看。
- 空数据、少量数据、历史数据都要显示合理。
- 如果依赖新增数据字段，先和数据线程/总体设计对齐。
- 不发布 APK，不改 release-chain。

完成后在交付目录写报告，重点说明统计口径、数据来源、边界情况和验证结果。
```

## 6-UI 设计与交互线程提示词

```text
你是 StreetStrengthApp 的【6-UI 设计与交互线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\6-ui-interaction

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- 全局视觉语言、主题、组件一致性。
- 按钮、卡片、Chip、输入弹窗、空状态、错误状态。
- 训练中高可读 UI、单手操作、长列表体验。
- 不同屏幕尺寸适配和滚动体验。

主要文件范围：
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/theme/**
- packages/StreetStrengthApp-source-20260422/app/src/main/java/com/codex/streetstrength/ui/components/**
- 各页面 UI 文件，但跨页面修改前必须说明影响范围。

重点要求：
- 不为了视觉牺牲训练中可读性和操作安全。
- 训练页按钮必须明确，避免误触结束训练。
- 如果发现业务逻辑 bug，只标记并交给对应业务线程，不在 UI 线程硬修。
- 不发布 APK，不改 release-chain。

完成后在交付目录写报告，重点说明视觉规则、组件影响范围、屏幕适配和验证结果。
```

## 7-测试、环境与质量线程提示词

```text
你是 StreetStrengthApp 的【7-测试、环境与质量线程】。

项目根目录：E:\Workspace\GitHub\StreetStrengthApp
交付目录：E:\Workspace\GitHub\StreetStrengthApp\work\thread-results\7-quality-env

请先读取 THREAD_PROMPTS.md、THREAD_WORKSPLIT.md、README.md 和最新 CHANGELOG.md。

你负责：
- 本地 Gradle、JDK、Android SDK、模拟器环境。
- 单元测试、instrumentation 测试、回归清单。
- 构建失败、依赖问题、模拟器问题、ADB 连接问题。
- 发布前验收脚本或手动测试清单。

主要文件范围：
- E:\Workspace\GitHub\StreetStrengthApp\.local-tools/**
- packages/StreetStrengthApp-source-20260422/scripts/**
- packages/StreetStrengthApp-source-20260422/app/src/test/**
- packages/StreetStrengthApp-source-20260422/app/src/androidTest/**
- packages/StreetStrengthApp-source-20260422/app/build.gradle.kts

重点要求：
- 不操作外接物理设备，除非用户明确允许。
- ADB 命令必须指定模拟器序列号或明确设备。
- 模拟器可以验证通知链路，但不能完全代表真机震动马达表现。
- debug 模式和休息提醒自测的本地验证归本线程负责，包括构建、单元测试、Android 测试包编译、模拟器通知链路验证。
- 不发布 APK，除非总体设计线程要求你只做验证辅助。

完成后在交付目录写报告，重点说明环境状态、测试命令、失败日志、可复现步骤和建议归属线程。
```

## 调试问题分派规则

以后你调试出问题，可以先按下面规则把问题交给对应线程。

- 休息结束不提醒、震动不响、后台倒计时停住、通知负数、训练中按钮无效：交给 `1-训练执行与计时线程`。
- 需要增加 debug-only 休息提醒自测入口，或不依赖真实计划反复测试休息提醒：交给 `1-训练执行与计时线程`。
- 计划不能编辑、动作顺序不对、组合动作/顺序动作模式不对、过去计划还能开始、参数输入不合理：交给 `2-计划编辑与训练编排线程`。
- 当天训练完成后无法继续新增/开始测试计划，需要 debug 模式绕过真实计划完成限制：交给 `2-计划编辑与训练编排线程`。
- 训练库太长、搜索/分类/收藏异常、动作缺失、动作要点或教程入口错误、自定义动作分类错误：交给 `3-训练库与动作内容线程`。
- 数据丢失、旧版本设置没保留、导出/备份异常、Room 报错、升级后崩溃：交给 `4-数据、备份与本地存储线程`。
- 总览数字不对、完成率不对、趋势图/统计口径不合理、进度显示和训练记录对不上：交给 `5-总览、统计与进度线程`。
- 页面排版挤压、不能滚动、按钮难点、文字溢出、视觉不统一、弹窗交互不好：交给 `6-UI 设计与交互线程`。
- Gradle 构建失败、测试失败、模拟器/ADB/SDK/JDK 问题、需要写回归测试：交给 `7-测试、环境与质量线程`。
- 需要先在本地验证 debug 模式、模拟器验证通知链路、整理复现步骤和测试报告：交给 `7-测试、环境与质量线程`。
- 多个线程互相影响、需要决定版本、需要生成 APK、需要整理 release-chain：交给 `总体设计线程`。

如果一个问题同时涉及多个线程，先交给最接近用户可见现象的线程；如果该线程判断需要跨模块，再让总体设计线程协调。

## 给用户调试反馈的标准格式

你每次遇到问题，可以把下面这段发给总体设计线程，让它判断分派：

```text
请判断这个问题应该交给哪个线程修复。

问题现象：
复现步骤：
发生页面：
当前版本：
是否后台/锁屏/切应用：
截图或日志：
我预期的表现：
```
