# LP-08：Code Review 画像消息原子发布实施计划

> 波次：C
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-01、LP-02、LP-05

## 1. 任务目标与完成标准

在每个新建有效正式 Review 提交时发布一条轻量画像消息，并保证 Review 主表、合法标签关联和 queue row 全部提交或全部回滚。

完成后：每个新正式 Review 恰好一条消息；replay、并发幂等复用、不可 Review 和失败尝试不发布；consumer disabled 不影响 Review 与消息入库；错题等现有 Observer 在事务提交后 best-effort 执行。

## 2. 当前实现基线

- LP-02 将把 Review 主表与标签关联放入 repository 事务，并返回 `PracticeCodeReviewSaveResult(review,created)`。
- LP-05 提供可加入调用方事务的 `QueuePublisher`。
- 当前 `PracticeCodeReviewService` 在 `repository.save` 返回后调用 `PracticeCodeReviewObserver`，并吞掉异常。
- 当前唯一 Observer Bean 是 `MistakeNoteService::ingestFromReview`；该链路还会触发复习卡预生成，不适合放入 Review 关键事务。
- 当前 `ON CONFLICT`/事务外预查在并发时不足以判断是否应发布，必须依赖 LP-02 的 created 结果。

## 3. 已确认的代码冲突或缺口

1. 给现有 Observer 加 `@Transactional` 无法回到已经提交的 repository 事务。
2. 若把错题 Observer 和画像 publisher 一并搬入事务，外部预生成副作用可能先发生而数据库后回滚。
3. 事务外预查 miss 的并发请求可能都尝试发布；只有事务内 created 结果可靠。
4. “异步观察不影响 Review 成功”仅适用于消费/模型失败；持久化消息是正式 Review 提交不变量，发布失败时 Review 必须回滚。

## 4. 范围、非目标和依赖

范围：业务 topic/payload 常量、事务协调服务、QueuePublisher 接线、created/reused 发布门禁、提交后 Observer 分层、失败映射、原子性和并发 IT。

非目标：不在 Review 请求内调用画像模型，不等待画像更新，不实现五条聚合、消费者、重试或补偿回放。

依赖：LP-01 正式事实、LP-02 标签与 created 契约、LP-05 Publisher。LP-09 消费本任务消息。

## 5. 关键技术决策

- topic 固定为 `learner-profile.code-review.v1`，放入 `CodeReviewProfileQueueContracts.TOPIC`。
- key 固定为十进制 `userId` 字符串；payload v1 只有 `reviewId`，不携带代码、Markdown、分数或 userId。
- 新建 `PracticeCodeReviewCommitService` 作为公开 Spring 事务协调器，事务内调用 repository.save 和 QueuePublisher。
- `PracticeCodeReviewService` 不包围 LLM 调用开启事务，只在 draft 已形成后调用 commit service。
- `created=false` 直接返回既有 Review，不 publish、不调用 post-commit Observer。
- commit 返回后再调用 `PracticeCodeReviewObserver`；异常继续 best-effort 隔离。
- 若未来有多个非关键 Observer，可使用 `CompositePracticeCodeReviewObserver`，但关键 publisher 不加入该 Composite。

## 6. 领域模型、接口、常量和配置契约

- `CodeReviewProfileQueueContracts.TOPIC = "learner-profile.code-review.v1"`。
- `CodeReviewProfileQueueContracts.JSON_REVIEW_ID = "reviewId"`。
- `CodeReviewProfileEvent(reviewId)` 使用 Jackson；不加入 schemaVersion，topic 后缀承担版本路由。
- `PracticeCodeReviewCommitResult(review,created,queueMessageId)`；reused 时 queueMessageId 为空。
- 发布失败对外仍映射 `PRACTICE_CODE_REVIEW_SAVE_FAILED`，tool result 不暴露数据库、topic 或堆栈。
- consumer enabled 配置不参与 publish 条件。

## 7. 数据库迁移、约束、索引和事务边界

- 本任务无新迁移，复用 `practice_code_review`、`practice_code_review_tag`、`queue_message`。
- 事务边界固定在 `PracticeCodeReviewCommitService.commit(draft)`：
  1. repository 锁 session 并判断 created/reused；
  2. created 时写 Review 和合法标签；
  3. created 时 publish PENDING queue row；
  4. 任一步异常整体回滚；
  5. 提交后返回 commit result。
- Spring 传播使用 REQUIRED，同一 `DataSourceTransactionManager`；禁止 `REQUIRES_NEW`。
- queue insert 失败必须使 Review/标签回滚；Observer 失败不得回滚已提交关键事实。
- 并发相同 userMessageId 依赖 session 锁和事务内复查，最终一条 Review、一组标签、一条消息。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileQueueContracts.java` | topic/JSON/key 契约 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileEvent.java` | 轻量 payload |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewCommitService.java` | 关键事务协调 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewCommitResult.java` | created/message 结果 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewService.java` | 调用 commit、提交后 Observer |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/review/PracticeCodeReviewObserver.java` | 明确 post-commit 语义注释 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/review/CompositePracticeCodeReviewObserver.java` | 可选非关键组合 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java` | 装配 commit service |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/MistakeReviewApiAutoConfiguration.java` | 保持错题 post-commit |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/practice/PracticeCodeReviewProfilePublishIT.java` | 三表原子性与并发 |

## 9. 分阶段实施步骤

1. 固定 topic、payload、key 和序列化契约。
2. 新建 commit service，把 Review/标签/queue publish 放入同一事务。
3. 修改 Review service，仅对 created=true 的提交执行 post-commit Observer。
4. 调整自动配置，关键 publisher 与错题 Observer 分层装配。
5. 增加 Review、标签、Publisher 三类故障注入和并发幂等 IT。
6. 增加 consumer disabled、replay、不可 Review和 Observer 失败回归。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | contract/serialization test | value 仅含 `reviewId`，key 为 userId |
| 2 | transaction service test/IT | 三类记录全有或全无 |
| 3 | ReviewService tests | created 发布/通知，reused 均不执行 |
| 4 | Spring context test | 关键 publisher 不属于吞异常 Observer |
| 5 | concurrency IT | 同 userMessageId 恰好一条 queue row |
| 6 | failure tests | consumer disabled 可入库，Observer 失败不回滚 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 单元测试覆盖 payload 无代码/Markdown、topic/key 常量、QueuePublisher 参数。
- commit service 分别注入 Review insert、tag insert、queue insert 异常，断言事务异常向上抛出。
- PostgreSQL IT 断言三张表计数全 0 或全部正确；queue message 为 PENDING。
- 两线程并发同一 userMessageId，断言一条 Review、一组去重标签、一条 topic 消息；一个 created=true，一个 false。
- replay、既有 Review 预查命中、NOT_CODE_LIKE、NOT_COMPLETE_SUBMISSION、LLM/结构化失败均零消息。
- consumer enabled=false 的完整 Spring context 下 Review 与消息正常提交。
- 错题 Observer 抛异常后正式 Review、标签和消息仍存在。

## 12. 日志、指标、隐私和 AI governance 要求

- 指标：`practice.review.profile_event{outcome=published|reused|failed}`、payload bytes；不得以 userId/reviewId/messageId 作 tag。
- 日志记录 reviewId、queueMessageId、topic、created 和 outcome，不记录 value、代码、Review Markdown 或完整异常 metadata。
- Review AI governance 保持现状；本任务不调用画像模型。
- queue payload 不包含用户正文，降低持久化队列隐私面。

## 13. 发布顺序、兼容性和回滚方案

- 先部署 LP-05 队列表/Publisher，consumer disabled；再部署 LP-01/02/08。
- 部署后观察 Review 成功率和 PENDING 入库，不开启 LP-09 consumer。
- queue publish 故障时 Review 返回保存失败并回滚，这是固定提交语义。
- 回滚应用保留已入库消息；旧应用忽略 queue 表，不需 down migration。
- 若消息数量异常，先停止新 Review 接线或回滚 LP-08，不重置已 SUCCEEDED 消息。

## 14. 风险与开放项

- 风险：错题 Observer 误被纳入关键事务。装配测试必须断言调用时点在 commit 返回后。
- 风险：repository 的 created 结果不可靠会重复发布。必须由事务内复查决定，禁止服务层预查推断。
- 开放项：历史正式 Review 不回填消息；若需要画像重建，后续单独设计回放，不在本任务补发。
- 明确不实现模型调用或等待画像更新。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='PracticeCodeReview*Test,*CodeReviewProfile*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=PracticeCodeReviewProfilePublishIT verify

make backend-test
git diff --check
```

## 16. 最终验收 checklist

- [ ] topic、key、payload 字段使用公共常量。
- [ ] payload 仅携带 reviewId。
- [ ] Review、合法标签和 queue row 同事务。
- [ ] created=false/replay 不重复发布。
- [ ] 并发同 userMessageId 恰好一条消息。
- [ ] consumer disabled 不影响发布。
- [ ] 错题 Observer 在提交后 best-effort，失败不回滚。
- [ ] 未在 Review 请求中调用或等待画像模型。
