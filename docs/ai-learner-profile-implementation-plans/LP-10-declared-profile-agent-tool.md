# LP-10：用户自述同步更新 Agent 工具实施计划

> 波次：C
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-04

## 1. 任务目标与完成标准

提供 `update_learner_declared_profile` 批量结构化 Agent 工具，使 `PRACTICE_CHAT` 在识别到明确长期自述或纠正时同步更新五个 `DECLARED_FACT` dimension。

完成后：工具身份只来自受信 execution context；非法 kind/dimension/tag 被拒绝；多维更新全有或全无；结果固定为 `UPDATED/NO_CHANGE/FAILED`；FAILED 不抛 `TOOL_EXECUTION_FAILED`、不终止 run；本轮通过 tool result 使用结果，下一 run 才召回新快照。

## 2. 当前实现基线

- `MentorAiConfiguration` 已按 `List<AgentTool>` 自动注册工具，`AgentLoopRunner` 已发布通用 `agent_tool_start/end`。
- `PracticeCodeReviewAgentTool` 展示了从 `AgentExecutionContext` 读取 userId/scenario/runId 并使用 `AiCompletionContext.parentRun` 的现有模式。
- 当前没有画像更新工具、工具常量、Schema、Prompt、模型适配或前端状态。
- LP-04 将提供 snapshot、批量 apply 和 STALE 结果。
- 当前 `AiPurpose` 无画像专用值，`AiRunSource` 无画像来源；OpenAI provider 实际使用全局 `algo-mentor.ai.openai.timeout`，未消费请求级 timeout。

## 3. 已确认的代码冲突或缺口

1. 单一 `FAILED` 前端状态无法准确表达部分成功，因此不能逐 dimension 独立提交。
2. 工具不得接受 userId、entryKind、tagId、revision/status 等模型参数。
3. 若画像异常直接抛 AgentException，会产生 `TOOL_EXECUTION_FAILED` 并可能改变主 run 行为。
4. 同一 run 的 Prompt 已在 loop 前组装，工具完成后不能热重载画像。
5. 场景级 timeout 当前没有真实 provider 落点，计划不能虚构独立超时已生效。

## 4. 范围、非目标和依赖

范围：工具常量、参数/结果 Schema、长期自述 Prompt 边界、批量模型决策、治理接入、全原子 apply、STALE 一次重算、工具装配和后端测试。

非目标：不扫描所有聊天自动抽取，不在 loop 前调用分类模型，不更新 GENERAL/TAG，不新增画像 SSE，不做画像管理 API/UI，不在本任务实现前端文案。

依赖：LP-04；LP-12 消费工具结果，LP-11 在下一 run 召回。

## 5. 关键技术决策

- 工具只在 `PRACTICE_CHAT` 场景注册/执行；userId、runId、stepIndex 从 `AgentExecutionContext` 读取。
- 参数先整体校验，再进行一次批量画像模型调用；模型为每个 dimension 返回 `NO_CHANGE/REPLACE`。
- 参数 `intent` 只允许 `DECLARE/CORRECT`，服务端分别映射 `USER_EXPLICIT/USER_CORRECTION`；首次与普通补充都属于 DECLARE。
- 所有 REPLACE 通过 LP-04 `applyBatch` 同事务提交；任一失败则全回滚并返回 FAILED。
- 聚合状态：至少一项 APPLIED -> UPDATED；全部 NO_CHANGE -> NO_CHANGE；校验/AI/数据库/二次 STALE -> FAILED。
- STALE 时基于最新 snapshot 重调模型一次；不在同一 run 无限重试。
- 复用 `AiPurpose.LEARNING_CHAT`，新增 `AiRunSource.LEARNER_PROFILE_DECLARED_UPDATE`。
- 第一版使用 gateway 默认模型和全局 OpenAI timeout；若要场景专属 timeout，需另行完善 provider 请求级 timeout，不在本任务假装配置生效。

## 6. 领域模型、接口、常量和配置契约

固定工具契约：

- tool name：`update_learner_declared_profile`。
- argument：`updates`；item 字段 `dimension`、`statement`、`intent`。
- `dimension` 仅五个 DECLARED_FACT dimension；`intent` 仅 `DECLARE/CORRECT`。
- result type：`learner_declared_profile_update`。
- result 字段：`status`、`message`、`items`；item 仅 `dimension`、`status`、`contentSummary`。
- result status：`UPDATED`、`NO_CHANGE`、`FAILED`。
- `contentSummary` 是受长度限制的当前正文摘要，不返回数据库错误或完整历史。

配置：

```yaml
algo-mentor:
  learner-profile:
    declared-update:
      enabled: false
      max-stale-retries: 1
      result-summary-max-chars: 300
```

工具名、字段、状态和失败码统一放入 `LearnerDeclaredProfileToolContracts`。

## 7. 数据库迁移、约束、索引和事务边界

- 无新迁移，复用 LP-03/04。
- 读取 snapshot 和 AI 调用在事务外。
- `applyBatch` 短事务：锁用户 -> 复核所有 expected token -> 任一 stale 则零写入 -> 所有 REPLACE 一次提交。
- 模型返回非法 dimension/kind/tag/action/content 时在事务前拒绝整个批次。
- 工具捕获所有画像相关 RuntimeException，转换普通 FAILED JSON；框架级参数不是 JSON object 等调用契约错误也优先返回 FAILED，不泄露内部信息。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/LearnerDeclaredProfileToolContracts.java` | 工具/字段/状态常量 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/UpdateLearnerDeclaredProfileAgentTool.java` | AgentTool 实现 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/DeclaredProfileUpdateRequest.java` | 受控参数模型 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/DeclaredProfileUpdateIntent.java` | DECLARE/CORRECT |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/DeclaredProfileUpdateResult.java` | 聚合结果 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ai/DeclaredProfileUpdatePromptBuilder.java` | 当前正文 + 自述 Prompt |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ai/DeclaredProfileUpdateJsonSchema.java` | 批量 decision Schema |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ai/DeclaredProfileUpdateService.java` | AI、校验、STALE 重算 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java` | 长期自述调用边界 |
| Modify | `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java` | 新增 declared update source |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/LearnerProfileAgentProperties.java` | enabled/retry/summary 配置 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java` | 条件注册工具 |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | 安全默认关闭 |

## 9. 分阶段实施步骤

1. 固定工具参数、结果、常量和五维白名单。
2. 编写工具 Schema 和 practice Prompt 调用边界，禁止一次性表现/短期情绪升格。
3. 实现批量 AI Prompt/Schema/mapper，接入 parentRun governance。
4. 实现全原子 apply、状态聚合和最多一次 STALE 重算。
5. 实现 AgentTool，捕获失败为普通 JSON，并按 enabled 条件装配。
6. 增加工具、AI mapper、Agent flow 和 PostgreSQL 批量事务测试。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | contract/schema tests | 仅五维、DECLARE/CORRECT、无 userId/tagId |
| 2 | Prompt tests | 长期事实与短期内容边界明确 |
| 3 | AI service tests | 一次批量调用，source/purpose/stepIndex 正确 |
| 4 | update tests/IT | 任一失败全回滚，STALE 最多重算一次 |
| 5 | AgentTool/flow tests | FAILED 仍发布 tool_end，run 继续 |
| 6 | scenario tests | 本轮不重载，下一 run 才读取新内容 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 参数测试：空 updates、重复 dimension、非法 dimension、tag/kind/user 字段、超长 statement、非法 intent。
- AI 输出测试：缺项、多项重复、非法 action、REPLACE 空正文、额外 dimension；均整批失败。
- 状态测试：任一 APPLIED -> UPDATED；全部 NO_CHANGE -> NO_CHANGE；任何异常 -> FAILED。
- PostgreSQL IT：两个/多个 dimension 同事务写入；第二项故障时第一项不提交。
- STALE 测试：第一次 apply stale 后读取最新正文并重调一次；再次 stale 返回 FAILED、零部分写入。
- Agent flow：FAILED 不产生 `TOOL_EXECUTION_FAILED`，主 Agent继续回答且不得声称保存成功。
- 同一 run Prompt snapshot 不变，tool result 含最新摘要；下一 run 由 LP-11 读取。

## 12. 日志、指标、隐私和 AI governance 要求

- 使用 `AiPurpose.LEARNING_CHAT`、`AiRunSource.LEARNER_PROFILE_DECLARED_UPDATE`、`AiCompletionContext.parentRun`。
- 日志记录 dimension 数、decision action、status、provider/model/promptVersion、耗时，不记录 statement、content 或完整 tool arguments/result。
- 指标：`learner.profile.declared_tool{outcome}`、`learner.profile.declared_ai{outcome}`、stale retry；不使用 userId/dimension 组合高基数 tag，dimension 可作为固定十值内低基数 tag。
- 工具结果摘要必须截断并经过正文安全策略；FAILED 不暴露数据库/模型错误。
- OpenAI timeout 真实来源为现有全局配置，计划和运维文档必须如实说明。

## 13. 发布顺序、兼容性和回滚方案

- 先部署工具代码但 `enabled=false`，验证 Spring 装配和测试；LP-04 稳定后灰度开启。
- 不新增 SSE 事件，现有客户端忽略未知工具结果仍可工作。
- 若模型成本、非法输出或失败率异常，关闭 declared-update enabled；已有画像保留。
- 回滚工具不删除画像版本；下一 run 仍可读取历史已提交 ACTIVE。

## 14. 风险与开放项

- 风险：主 Agent 过度调用工具。Prompt 和指标需观察工具调用率，不增加循环内自动重试。
- 风险：用户纠正意图判断错误。工具 `intent` 仅映射来源，不改变正文正确性；历史版本保留可追踪。
- 开放项：场景专属 timeout 需要 `OpenAiLlmProvider` 支持请求级 timeout，属于跨模块能力扩展，第一版复用全局 timeout。
- 明确不新增用户确认权限流程；如产品要求写画像前 ASK，需另立任务评估交互。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='*DeclaredProfile*Test,*Agent*Flow*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=DeclaredProfileUpdateIT verify

make backend-test
git diff --check
```

## 16. 最终验收 checklist

- [ ] 工具名、字段、状态均由公共常量/枚举管理。
- [ ] 身份只来自受信 execution context。
- [ ] 仅允许五个 DECLARED_FACT dimension。
- [ ] 多维更新全有或全无。
- [ ] STALE 最多重算一次且无无限重试。
- [ ] UPDATED/NO_CHANGE/FAILED 聚合语义明确。
- [ ] FAILED 不终止 Agent run、不暴露内部错误。
- [ ] 本轮不热重载画像，下一 run 才召回。
- [ ] AI governance source、全局 timeout 和成本台账真实接入。
