# StreetStrength

极简风格的街头健身基础力量训练 App，本地离线运行，当前实现基于：

- `Kotlin`
- `Jetpack Compose`
- `Room`
- `DataStore`
- `ViewModel + Coroutines`

## 当前已完成

- 日历页：按天查看是否有计划、当天训练项目、完成状态
- 总览页：按周 / 按月查看计划天数、训练天数、完成组数、总次数、总静止时长、负重总和
- 计划页：手动排周计划，支持选择动作、变式、组数、次数 / 时长、负重、间歇
- 训练库：内置拉力 / 推力项目，支持新增自定义训练项目
- 沉浸式训练页：只保留当前动作、当前组、目标值、休息倒计时
- 休息计时：`AlarmManager.setExact + Foreground Service + vibration`
- 本地数据保留：已移除 destructive migration，后续升级默认保留本地数据

## 内置训练库

拉力训练：

- 引体向上
- 变式包含标准正握、标准反握、正手窄握、正手宽握、反手窄握、反手宽握、前半程、后半程、顶端锁定 + 离心、顶端锁定到力竭

推力训练：

- 腰间俯卧撑
- 倒立撑
- 冲肩

## 版本信息

- App 版本：`1.1.0`
- `versionCode = 2`
- `compileSdk = 34`
- `targetSdk = 34`
- 推荐 `JDK 17`
- Gradle Wrapper：`8.7`

## 数据与升级说明

- 数据库文件名：`street-strength.db`
- 当前 Room 版本：`1`
- 应用启动时不再使用 `fallbackToDestructiveMigration()`
- 只要后续升级继续提供正确的 Room Migration，就不会丢本地数据

## 关键目录

- `app/src/main/java/com/codex/streetstrength`
  应用主代码
- `app/src/main/java/com/codex/streetstrength/ui`
  Compose 页面与导航
- `app/src/main/java/com/codex/streetstrength/data`
  Room、Repository、Preferences
- `app/src/main/java/com/codex/streetstrength/timer`
  休息计时服务
- `app/schemas`
  Room schema 导出

## 本地构建

### Android Studio

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle Sync 完成
3. 配置本机 Android SDK
4. 直接运行 `app`

### 命令行

Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

macOS / Linux:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 已验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- APK 可在本地模拟器和手机端打开

## 继续开发建议

- 在计划页添加任务时，直接支持“新建自定义项目”
- 给总览页补更多趋势统计，例如按动作聚合、近 4 周变化
- 后续如需升级 Room 表结构，先新增 Migration，再提升数据库版本
- 发布正式版本前补 `release` 签名、应用图标、隐私说明和回归测试
