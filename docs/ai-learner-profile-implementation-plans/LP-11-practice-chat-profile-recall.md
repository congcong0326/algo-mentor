# LP-11：PRACTICE_CHAT 画像召回与 Prompt 注入实施计划

> 波次：C
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：题目标签门禁、LP-02、LP-04

## 1. 任务目标与完成标准

在 Agent loop 前按场景读取一次画像快照，并向 `PRACTICE_CHAT` 注入独立、受 800 预算控制的参考性 Prompt section。

完成后：每个 practice run 最多查询一次；只召回五个自述、两个允许的通用观察和当前题目标签评价；未知场景和正式 Review disabled；超预算按固定优先级裁剪；查询失败或无画像不阻断聊天；本 run 不二次刷新。

## 2. 当前实现基线

- `AgentConversationService.assemblePracticeChatContext` 在 loop 前读取学习计划、题目详情和历史，并调用 `practicePromptAssembler`。
- `defaultPracticePromptAssembler()` 固定只注册 `PracticeChatPromptSectionProvider`；仅增加 Spring Bean 不会自动接线。
- 当前总预算实际来自 `ContextAssemblyPolicy.defaultPolicy().tokenBudget=8_000`，`PracticeChatPromptConstants.DEFAULT_TOKEN_BUDGET` 也硬编码 8,000。
- 会话摘要已使用 `PromptSlot.MEMORY_SUMMARY`、section ID `practice.memory.active-summary`、priority 50。
- `PracticeChatProblemDetail.tags` 只有展示 value/label；LP-02 才提供受信 tagId catalog。
- `PersistentAgentTraceObserver` 会持久化最终 request messages，现有字段级 redactor 不会自动删除嵌在文本中的画像正文。

## 3. 已确认的代码冲突或缺口

1. 固定 assembler 不接受新增 provider，必须改造构造/自动配置。
2. 全局 budget planner只能裁剪整个 section，不能表达自述 > 当前题 tag > 通用观察的内部优先级。
3. 两处 8,000 硬编码可能漂移，新增 800 配置不能只改常量。
4. 从展示标签反查 tagId 会破坏受信边界，必须复用 LP-02 catalog。
5. 画像查询若放进 provider 内重复执行，难以保证每 run 一次和失败降级。
6. Prompt 日志可避免正文，但诊断快照仍包含最终消息，需要发布前明确保留/访问接受条件。

## 4. 范围、非目标和依赖

范围：Policy/Resolver、快照协调器、每 run 一次查询、Prompt provider、总预算/画像预算配置、确定性裁剪、metadata/metrics、正式 Review 隔离和测试。

非目标：不修改 `AgentLoopRunner`，不做向量召回、模型压缩、跨标签语义扩展，不把正式设置迁移到画像，不向其他 AI 场景注入。

依赖：LP-02 受信标签端口、LP-04 QueryService；LP-13 完成隐私和发布门禁。

## 5. 关键技术决策

- `LearnerProfilePolicyResolver` 使用白名单：仅 `PRACTICE_CHAT` enabled，其余及未知场景 disabled。
- `AgentConversationService` 在组装 Prompt 前调用 `LearnerProfileRecallService` 一次，把 snapshot 放入 assembly variables；provider 只渲染，不查数据库。
- 召回集合固定：五个 DECLARED、`PROBLEM_SOLVING_APPROACH`、`IMPLEMENTATION_AND_ERROR_PATTERN`、当前题 assignment 对应 TAG_MASTERY。
- profile section ID 固定 `practice.memory.learner-profile`，slot `MEMORY_SUMMARY`，priority 45；active summary 保持 50，排序稳定且 identity 独立。
- provider 内部先按类别和固定顺序渲染到 800 估算预算：自述 > 当前题 tag > 通用观察；不调用模型压缩。
- 查询异常返回 empty snapshot 并计数；不生成 section。
- total 8,000 和 profile 800 均通过 Spring properties 注入；不根据模型 context window 自动扩大。

## 6. 领域模型、接口、常量和配置契约

- `LearnerProfileScenario` 至少包含 `PRACTICE_CHAT`、`PRACTICE_CODE_REVIEW`，未知值走 disabled。
- `LearnerProfilePolicy(enabled,declaredDimensions,generalDimensions,includeCurrentProblemTags,maxTokenBudget)`。
- `LearnerProfileRecallSnapshot` 分三组保存已排序 entry，不暴露历史或 SUPPRESSED。
- Prompt variable：`learnerProfileSnapshot`；metadata：`learnerProfileTokenEstimate`、`learnerProfileTrimmed`、`learnerProfileEntryCount`。
- section 使用 `PromptTrustLevel.MODEL_GENERATED`、`PromptSensitivity.USER_CONTENT`、`DROP_IF_NEEDED`。

配置：

```yaml
algo-mentor:
  practice-chat:
    prompt:
      total-token-budget: 8000
  learner-profile:
    recall:
      practice-chat:
        enabled: false
        max-token-budget: 800
```

配置 key 由 `PracticeChatPromptPropertyKeys`/`LearnerProfileRecallPropertyKeys` 管理。

## 7. 数据库迁移、约束、索引和事务边界

- 无新迁移，复用 LP-03/04 和 V33 题目标签关系。
- recall 以只读短查询执行：先按 problemSlug 取受信 tagIds，再批量取目标 ACTIVE entries。
- 不在 Agent run 生命周期持有数据库事务，不在 LLM 调用期间持连接或锁。
- QueryService 默认排除 SUPERSEDED/SUPPRESSED；当前题无标签时跳过 tag 查询。
- PostgreSQL IT 验证用户隔离、ACTIVE 过滤和当前题 tag 范围。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfilePolicy.java` | 场景策略 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfilePolicyResolver.java` | 白名单解析 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfileRecallSnapshot.java` | 单 run 快照 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfileRecallService.java` | 查询协调与降级 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfilePromptSectionProvider.java` | 800 内部裁剪与渲染 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java` | 每 run 一次 snapshot |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptConstants.java` | section/variable/metadata 常量 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptProfileResolver.java` | 配置化 8,000 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java` | 与新 provider 稳定共存 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/PracticeChatPromptProperties.java` | total budget |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/LearnerProfileRecallProperties.java` | enabled/800 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java` | 注入 assembler/providers/services |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | 默认关闭和预算 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/profile/LearnerProfileRecallIT.java` | PostgreSQL 范围验证 |

## 9. 分阶段实施步骤

1. 定义 Policy/Resolver 和安全默认配置，仅 PRACTICE_CHAT enabled。
2. 实现 RecallService，复用 LP-02 tag catalog 和 LP-04 QueryService，每 run 一次。
3. 实现三类固定排序与 800 内部裁剪，生成独立 MEMORY_SUMMARY section。
4. 改造 AgentConversationService/自动配置，注入 provider 列表和统一 8,000 来源。
5. 增加 metadata、日志/指标和查询异常降级。
6. 增加 Policy、裁剪、单次查询、正式 Review隔离、PostgreSQL范围和诊断隐私测试。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | resolver/properties tests | 仅 PRACTICE_CHAT enabled，默认 false 可灰度 |
| 2 | recall service tests/IT | 五自述、两通用、当前题 tag，查询一次 |
| 3 | provider tests | 800 上限和固定裁剪结果可重复 |
| 4 | AgentConversationService tests | 真实 assembler 包含独立 section，8,000 单一来源 |
| 5 | failure tests | 查询异常无 section，聊天继续 |
| 6 | Review isolation/trace review | 正式 Review零画像查询，诊断保留边界明确 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- Policy：PRACTICE_CHAT enabled；PRACTICE_CODE_REVIEW、普通聊天、学习计划、主题讲解、复述判定和未知场景 disabled。
- PostgreSQL IT：仅 ACTIVE；五个自述、两个通用观察；只返回当前题 assignment tag；其他题 tag 不返回。
- 每 run 查询计数器断言最多一次；tool 更新后同 run snapshot 不变，下一 run 才变化。
- 裁剪测试使用超长多条内容，断言类别优先级、dimension 固定顺序、tag ordinal 顺序和结果确定性。
- 800 值不随模型 context window 变化；总 budget 8,000 可通过 properties 覆盖。
- 查询异常、无画像、无 tag 时不生成 section，原聊天消息/工具保持正常。
- `PracticeCodeReviewPromptBuilder` 路径断言不调用 RecallService。

## 12. 日志、指标、隐私和 AI governance 要求

- 记录总 Prompt 估算、profile 估算、entry 数、是否裁剪和查询 outcome，不记录 `content_text`。
- 指标：`learner.profile.recall{scenario,outcome}`、`learner.profile.prompt.trimmed`、profile estimate；scenario 为受控枚举。
- Prompt 明确画像是模型生成参考，不覆盖当前用户消息、题面、计划和正式设置。
- section 标记 USER_CONTENT，使 section snapshot variables 脱敏。
- 已确认风险：最终 LLM request diagnostic snapshot 仍可能包含画像正文。第一版只有在既有 30 天保留、管理员访问控制和审计被安全负责人明确接受后才能开启；否则 LP-13 阻止发布并要求补充 trace 专用画像正文脱敏。

## 13. 发布顺序、兼容性和回滚方案

- 先部署代码和配置，`recall.practice-chat.enabled=false`。
- LP-04 数据和 LP-10/09 写入稳定后，小范围开启 PRACTICE_CHAT recall。
- 若回答质量、Prompt 裁剪率、查询耗时或隐私审计异常，关闭 recall 开关；Agent 和画像更新继续工作。
- 回滚不删除画像数据，不影响正式 Review 独立评分。

## 14. 风险与开放项

- 发布阻塞开放项：确认最终 request diagnostic snapshot 中画像正文的 30 天保留和管理员访问是否可接受；未确认不得开启 recall。
- 风险：双 8,000 来源未彻底统一。测试必须验证 runtime request 和 profile resolver 使用同一 properties 值。
- 风险：provider 内裁剪与全局 planner 双重裁剪。profile 先压到 800，全局仍可整体 drop，但不得改变内部顺序。
- 明确不把其他 GENERAL dimensions 或其他题 tag 偷偷加入本任务。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='*LearnerProfile*Recall*Test,*PracticeChat*Prompt*Test,*AgentConversationServiceTest' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=LearnerProfileRecallIT verify

make backend-test
git diff --check
```

## 16. 最终验收 checklist

- [ ] 仅 PRACTICE_CHAT 可启用画像召回。
- [ ] 每 run 最多查询一次且无长事务。
- [ ] 召回范围固定为五自述、两通用、当前题 tag。
- [ ] profile section identity 独立、顺序稳定。
- [ ] 8,000/800 均由 Spring 配置注入。
- [ ] 超预算按固定类别和条目顺序裁剪。
- [ ] 查询异常或无画像不阻断聊天。
- [ ] 正式 Review不查询、不注入画像。
- [ ] 诊断快照隐私接受条件已完成或开关保持关闭。
