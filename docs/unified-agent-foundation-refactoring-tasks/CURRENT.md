# 当前执行上下文

更新时间：2026-07-29

当前状态：完成

当前任务：无

下一任务文件：无

## 完成状态

- `UAF-00` 至 `UAF-13` 已全部完成，统一 Agent 底座重构已结束。
- 九个正式场景均有唯一类型化 Definition：Mentor Conversation、Topic Explanation、Practice Chat、
  Learning Plan Draft/Revision/Extension、Practice Code Review、Learner Declared Profile Update、
  Code Review Profile Update。
- 完整 Spring 配置门禁固定 key/scenario 一致性、输入类型、工具白名单和最大步数；源码架构门禁固定
  模块依赖、旧入口清理和 observer 通用性。

## 最终模块入口

- `agent-core.runtime.api.AgentRuntime` 与 `agent-core.runtime.definition.AgentDefinition` 是业务层唯一
  Runtime 契约；`agent-runtime.DefaultAgentRuntime` 实现审计准备、治理租约和同步/流式终态。
- `MentorAiConfiguration` 是 API composition root，装配 Runtime、Definition registry 和 run-local
  工具 registry；业务模块不再注入旧 runner 或 completion gateway。
- `V45__agent_runtime_run_audit.sql` 增加 `agent_key`、父 run/step、调用模式和 retry 来源；未修改任何
  已应用 Flyway 历史脚本。

## 最终验证

- Definition/application 与架构门禁通过；UAF-13 API 目标测试集通过（56 tests）。
- `make backend-test` 通过：276 份 Surefire 报告，无 failures/errors。
- `make backend-build` 通过。
- 旧入口源码检查无命中：mentor-application 的 `AgentLoopRunner`/`AgentRunner`/
  `AiCompletionGateway`，正式 controller/SSE 的 admission/lifecycle，以及 agent-runtime 的
  `AiCompletionGateway`。
- `git diff --check` 通过。

## 残余兼容项

- `AgentLoopRunner` 仅作为 agent-core 低层兼容 stream facade 保留；正式业务模块没有生产引用。
