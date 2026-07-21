# LP-09：Code Review 画像批量消费者实施计划

> 波次：D
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-02、LP-04、LP-06、LP-07、LP-08

## 1. 任务目标与完成标准

实现第一版系统观察源：同一用户每累计 5 条有效正式 Review 消息，异步聚合最近最多 10 道不同题目的最新 Review，批量更新两个通用观察和已归因标签的 TAG_MASTERY。

完成后：4 条不触发、5 条同 key 恰好一次模型调用；不同用户不混批；当前批次题目必在最多 10 道窗口内；非法输出整批拒绝；NO_CHANGE 零写、REPLACE 合法版本链；消费失败不回滚 SUCCEEDED、不自动重试。

## 2. 当前实现基线

- LP-08 固定 topic `learner-profile.code-review.v1`、key=userId、payload 仅 reviewId。
- LP-06/07 提供 batchSize 满批、最多一次出队和 topic worker。
- 当前 `PracticeCodeReviewRepository` 只支持按 session/id/userMessage 查询，无法按用户构造跨题窗口。
- Review 主表保存完整代码、Markdown、分数和建议；LP-02 关联表保存 `affectedTagIds`。
- LP-04 提供 snapshot、全原子 batch apply 和 STALE。
- 当前没有画像消费者、模型 Prompt/Schema、治理 source 或 per-consumer feature switch。

## 3. 已确认的代码冲突或缺口

1. 简单 `ORDER BY created_at DESC LIMIT 10` 会把同题多版本当多题，且可能排除当前批次题目。
2. 消费者不得加载 raw_code 或完整 review_markdown，否则扩大隐私和 token 成本。
3. 队列在 callback 前已经 SUCCEEDED，模型/数据库失败不能通过抛异常获得重试。
4. 模型只能更新两个 GENERAL dimension 和本窗口已归因 tagId，必须双重白名单。
5. 多个画像 decision 若逐条落库会形成部分成功；应与 LP-10 一致采用批次全有或全无。
6. 场景级 provider timeout 当前不生效，需如实复用全局 timeout。

## 4. 范围、非目标和依赖

范围：BatchQueueConsumer、消息解析/归属校验、轻量事实投影、10 题窗口、批量 Prompt/Schema/mapper、AI governance、全原子 apply、STALE 一次重算和指标。

非目标：不接入错题复述、计划进度或聊天求助，不引入 Strategy Registry，不做 retry/dead-letter/人工回放，不更新另外两个 GENERAL dimension。

依赖：LP-02/04/06/07/08；LP-13 负责端到端与生产开启。

## 5. 关键技术决策

- 消费者实现 `BatchQueueConsumer`，只注册固定 topic，`batchSize=5` 为代码常量，不配置化。
- 首先验证 5 条消息 topic/key 一致、payload 只有合法 reviewId、Review 存在且 userId 与 key 匹配。
- 窗口算法：按批次 reviewId 找出涉及题目 -> 查询这些题目的用户当前最新正式 Review -> 再按最新时间补充其他不同题目，合计最多 10 道。
- 同题只传最新事实；同题版本变化不用于第一版 `REVIEW_AND_GROWTH_PERFORMANCE`。
- 输入投影仅含 problemSlug、各评分、passed、扣分原因、改进建议、affectedTagIds；不含代码、evidence 原文、完整 Markdown。
- 模型一次返回全部 general/tag decisions；先整体验证，后一次 `applyBatch`。
- STALE 时重建最新 snapshot 和模型输入再调用一次；再次 STALE 或任何失败只记录，不重试队列。
- 使用 `AiPurpose.LEARNING_CHAT`、`AiRunSource.LEARNER_PROFILE_CODE_REVIEW_BATCH`、`AiCompletionContext.background()`。

## 6. 领域模型、接口、常量和配置契约

- `CodeReviewProfileConsumerConstants.BATCH_SIZE=5`、`MAX_DISTINCT_PROBLEMS=10`、两个 GENERAL 白名单。
- `CodeReviewProfileFact`：reviewId、problemSlug、versionNo、score components、passed、deductionReasons、improvementSuggestions、affectedTagIds、createdAt。
- 模型 JSON：`generalObservations[] {dimension,action,content?,reason?}`、`tagAssessments[] {tagId,action,content?,reason?}`。
- action 仅 `NO_CHANGE/REPLACE`；REPLACE content 必填完整正文。
- allowed tagIds 是窗口内 `affectedTagIds` 并集；非法 tag 决策导致整批模型结果失败，不做部分过滤后写库。

配置：

```yaml
algo-mentor:
  learner-profile:
    code-review-consumer:
      enabled: false
      max-stale-retries: 1
```

batchSize 和 10 题窗口是固定业务契约，不通过环境修改。

## 7. 数据库迁移、约束、索引和事务边界

- 预期无新迁移；复用 Review、tag association、profile 和 queue 表。
- 新增轻量 SQL 投影，不使用现有完整 `PracticeCodeReviewRow`，避免读取大字段。
- 当前批次题目查询和历史补足查询均为只读短事务/自动提交，不持锁调用模型。
- `applyBatch` 使用 LP-04 用户行锁和全原子事务；任一 expected token stale 时零写入。
- queue 状态在 callback 前已是 SUCCEEDED；消费者任何失败均不修改 queue 表。
- 若执行计划显示跨用户/题目查询缺索引，只能新增独立 Flyway 索引迁移并补回归，不能修改既有迁移。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileBatchConsumer.java` | BatchQueueConsumer |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileFact.java` | 轻量事实 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileFactRepository.java` | 事实读取端口 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfilePromptBuilder.java` | 10 题聚合 Prompt |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileJsonSchema.java` | 批量 decision Schema |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileStructuredOutputMapper.java` | 白名单校验 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileUpdateService.java` | AI + apply + stale |
| Modify | `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java` | 新增 batch source |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeCodeReviewMapper.java` | 轻量跨题查询 |
| Modify | `backend/mentor-api/src/main/resources/mapper/practice/PracticeCodeReviewMapper.xml` | 当前批次优先 + 历史补足 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/profile/repository/MyBatisCodeReviewProfileFactRepository.java` | SQL 适配 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/CodeReviewProfileConsumerProperties.java` | enabled/stale 配置 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java` | 条件注册 consumer |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | 默认关闭 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/profile/CodeReviewProfileFactsIT.java` | 10 题窗口 PostgreSQL IT |

## 9. 分阶段实施步骤

1. 固定 batch/topic/payload 校验和 consumer feature switch。
2. 新增轻量事实端口与 SQL，先实现批次题目优先、其他题补足到 10。
3. 编写 Prompt/Schema/mapper，固定两个 GENERAL 和 allowed tag 白名单。
4. 接入 background AI governance 和全原子 apply，处理 NO_CHANGE/REPLACE/STALE。
5. 注册 BatchQueueConsumer，捕获所有业务失败并返回正常 callback 完成。
6. 增加消息、窗口、AI、事务、最多一次失败和端到端前置测试。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | consumer contract tests | batchSize=5、同 key/topic、payload 严格解析 |
| 2 | `CodeReviewProfileFactsIT` | 当前批次题目包含且总数 <=10，同题最新 |
| 3 | Prompt/schema/mapper tests | 仅两 GENERAL 和已归因 tag 可通过 |
| 4 | AI/update tests | 一次模型调用、批次原子、STALE 一次重算 |
| 5 | callback failure tests | 失败不抛出重试语义，queue 保持 SUCCEEDED |
| 6 | queue + consumer scenario | 4 条不触发、5 条恰好一次 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 消息测试：非 5 条、不同 key、非法 JSON、缺 reviewId、重复 reviewId、Review 缺失、userId 不匹配。
- 窗口 IT：批次含重复题、历史含多版本、超过 10 题、批次题较旧；断言当前批次题目仍包含且每题最新。
- 投影测试断言 SQL 不选择 raw_code、normalized_code、review_markdown。
- 模型白名单：禁止 `LEARNING_INTERACTION_AND_INDEPENDENCE`、`REVIEW_AND_GROWTH_PERFORMANCE` 和未归因 tagId。
- 空 affectedTagIds 时仍可更新两个 GENERAL；tagAssessments 必须为空或 NO_CHANGE 范围内。
- 4 条 PENDING 不 callback；第 5 条触发一次；第 6-9 条等待下一批。
- LLM 超时、非法输出、apply 失败、二次 STALE 后 queue 仍 SUCCEEDED且不再自动调用。

## 12. 日志、指标、隐私和 AI governance 要求

- 使用 `AiPurpose.LEARNING_CHAT`、`AiRunSource.LEARNER_PROFILE_CODE_REVIEW_BATCH`、background context。
- 日志记录消息数、不同题数、tag 数、decision 数、provider/model/promptVersion、outcome，不记录代码、Markdown、queue value 或画像正文。
- 指标：`learner.profile.review_consumer{outcome}`、窗口题数、模型非法输出、stale retry、版本更新数和耗时。
- topic 可作为低基数 tag；userId/key/reviewId/tagId 不得作为 tag。
- provider timeout 复用 `algo-mentor.ai.openai.timeout`；成本/Token 进入调用级台账。

## 13. 发布顺序、兼容性和回滚方案

- 先部署消费者 Bean 但 `code-review-consumer.enabled=false` 且全局 queue consumer disabled。
- 确认 LP-08 消息稳定积压后，在唯一节点同时开启业务 consumer 与全局 worker。
- 失败率、成本或画像异常时先关闭业务 consumer/全局 worker；PENDING 保留。
- 已 SUCCEEDED 但 callback 失败的消息按最多一次语义永久不回放；补偿需未来发布新消息。
- 回滚应用保留 queue/profile 数据，不修改 Review 主流程。

## 14. 风险与开放项

- 最多一次语义会丢失模型失败批次，这是上游明确接受的第一版权衡，必须在运维说明中突出。
- 10 题窗口 SQL 复杂，需用 PostgreSQL `EXPLAIN` 检查，但不在计划中预设额外索引。
- 模型对 Review 的判断是 AI 主观事实，不得表述为真实 AC。
- 不引入第二观察源或 Strategy Registry。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='*CodeReviewProfile*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=CodeReviewProfileFactsIT,PracticeCodeReviewProfilePublishIT verify

make backend-test
make backend-it
git diff --check
```

## 16. 最终验收 checklist

- [ ] consumer 唯一注册固定 topic、batchSize=5。
- [ ] 不同用户不混批，消息归属逐条校验。
- [ ] 当前批次题目优先且窗口最多 10 道不同题。
- [ ] 同题仅使用最新正式 Review。
- [ ] 输入不含完整代码或 Review Markdown。
- [ ] 仅更新两个 GENERAL 和已归因 TAG_MASTERY。
- [ ] 模型结果整批校验、画像写入全有或全无。
- [ ] 失败后 queue 保持 SUCCEEDED且不自动重试。
- [ ] AI governance、Token/成本和低敏观测已接入。
