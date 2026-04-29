# 线程交付报告

- 线程：总体设计与集成线程
- 目标：补充 debug 模式和休息提醒自测的线程分派规则
- 修改文件：
  - `THREAD_PROMPTS.md`
- 新增文件：
  - `work/thread-results/0-overall-design/20260429-1745-debug-mode-routing.md`
- 验证命令：
  - 未运行构建；本次仅修改协作文档，不涉及 App 代码
- 验证结果：
  - 文档已写入固定分派规则
- 需要总体设计集成的点：
  - 后续如果 1/2/7 线程完成 debug 功能实现，需要由总体设计线程统一升版本、生成 APK、写入 release-chain
- 需要其他线程注意的点：
  - `1-training-timer` 负责 debug-only 休息提醒自测入口
  - `2-planner-programming` 负责 debug 下绕过当天已完成导致无法新增/开始测试计划的问题
  - `7-quality-env` 负责本地构建、测试和模拟器通知链路验证
- 风险或未完成事项：
  - 当前只是分派和文档更新，实际功能尚未实现
