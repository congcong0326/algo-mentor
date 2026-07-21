# LP-13：画像闭环联调、配置与发布门禁实施计划

> 波次：E
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-data-model-and-storage-design.md`、`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-07 至 LP-12 全部完成

## 1. 任务目标与完成标准

把存储、Review 标签与原子发布、持久化队列、异步消费者、同步工具、Prompt 召回和前端状态验证为可发布闭环，并固化配置、灰度、观测和回滚门禁。

完成标准：V33 基线可升级到最新；Review/标签/消息原子；5 条同用户消息触发一次画像更新；同步工具三状态可用且失败不阻断；下一 run 召回、本 run 不热重载；正式 Review不受画像影响；consumer 关闭可积压、重开按最多一次语义运行；全量构建通过。

## 2. 当前实现基线

- 当前最高 Flyway 为 V33，新增计划至少包含 Review tag、profile、queue 三份迁移，版本在实施时动态分配。
- `FlywayMigrationResourceTest` 已检查跨模块版本唯一，但 expected version 列表需覆盖新增资源。
- `PostgresIntegrationTestSupport` 已有 PostgreSQL 16 Testcontainers，LP-03 将补通用 `migrateTo(String)`。
- Failsafe 和 `make backend-it` 在 `mentor-api` 可运行跨模块集成测试。
- AI governance 已有 purpose/source 台账、动态准入、Token 与成本观测；画像计划复用 LEARNING_CHAT并新增两个 source。
- 前端已有 practice SSE 测试和生产 build 命令。

## 3. 已确认的代码冲突或缺口

1. 开发波次与生产发布顺序不同，若直接按 LP 编号上线会出现 consumer 先于稳定生产者或 Prompt 先于数据。
2. queue `SUCCEEDED` 是已出队，不是业务成功；端到端断言不能把 callback 失败理解为可重试。
3. batchSize=5 导致每个 key 的 1-4 条 PENDING 可长期存在，不能只用全局 oldest pending age 判断故障。
4. OpenAI 请求级 timeout 当前未实际生效，画像模型使用全局 provider timeout。
5. `PersistentAgentTraceObserver` 可能保存含画像正文的最终 request snapshot，需安全确认或阻断 recall 开启。
6. 多节点没有自动选主，开启两个 consumer 节点会越过设计保证。

## 4. 范围、非目标和依赖

范围：迁移升级/validate、跨模块 PostgreSQL IT、后端场景 E2E、前端最小测试、完整配置/环境变量、治理/隐私审计、开发与发布顺序、灰度阈值、回滚 runbook。

非目标：不增加第二观察源、重试/死信/回放、自动选主、画像管理 UI、向量召回、证据表或画像重建。

依赖：LP-07 至 LP-12；LP-01 至 LP-06 的基础契约也必须保持通过。

## 5. 关键技术决策

- 区分“研发波次 A-E”和“运行时发布 A-D”；研发可并行，生产必须按依赖逐步开启。
- 所有 feature switch 安全默认 false：queue worker、Review画像 consumer、declared tool、practice recall。
- 端到端测试使用真实 PostgreSQL、真实 Spring 事务/Mapper/queue dispatcher，AI 使用可编程 fake gateway，不依赖外部网络。
- 发布先验证数据写入，再启消费，再启用户可见同步工具/Prompt。
- 回滚优先关开关和回滚应用，保留 `learner_profile_entry`、`practice_code_review_tag`、`queue_message`。
- PENDING 重启后继续；已经 SUCCEEDED但 callback 失败永久不回放，这是验收的一部分。

## 6. 领域模型、接口、常量和配置契约

最终配置清单必须在 `application.yml`、`.env.example` 和运维说明一致：

| 配置 | 默认值 | 环境变量建议 |
| --- | --- | --- |
| `algo-mentor.queue.message.max-value-bytes` | `65536` | `QUEUE_MESSAGE_MAX_VALUE_BYTES` |
| `algo-mentor.queue.consumer.enabled` | `false` | `QUEUE_CONSUMER_ENABLED` |
| `algo-mentor.queue.consumer.poll-interval` | `10s` | `QUEUE_CONSUMER_POLL_INTERVAL` |
| `algo-mentor.queue.consumer.shutdown-timeout` | `30s` | `QUEUE_CONSUMER_SHUTDOWN_TIMEOUT` |
| `algo-mentor.queue.cleanup.succeeded-retention` | `7d` | `QUEUE_SUCCEEDED_RETENTION` |
| `algo-mentor.queue.cleanup.fixed-delay` | `1h` | `QUEUE_CLEANUP_FIXED_DELAY` |
| `algo-mentor.queue.cleanup.batch-size` | `1000` | `QUEUE_CLEANUP_BATCH_SIZE` |
| `algo-mentor.learner-profile.content.max-chars` | `4000` | `LEARNER_PROFILE_CONTENT_MAX_CHARS` |
| `algo-mentor.learner-profile.code-review-consumer.enabled` | `false` | `LEARNER_PROFILE_REVIEW_CONSUMER_ENABLED` |
| `algo-mentor.learner-profile.declared-update.enabled` | `false` | `LEARNER_PROFILE_DECLARED_UPDATE_ENABLED` |
| `algo-mentor.learner-profile.recall.practice-chat.enabled` | `false` | `LEARNER_PROFILE_PRACTICE_RECALL_ENABLED` |
| `algo-mentor.learner-profile.recall.practice-chat.max-token-budget` | `800` | `LEARNER_PROFILE_PROMPT_TOKEN_BUDGET` |
| `algo-mentor.practice-chat.prompt.total-token-budget` | `8000` | `PRACTICE_CHAT_PROMPT_TOKEN_BUDGET` |

固定治理：`AiPurpose.LEARNING_CHAT`；source 为 `LEARNER_PROFILE_DECLARED_UPDATE`、`LEARNER_PROFILE_CODE_REVIEW_BATCH`。

## 7. 数据库迁移、约束、索引和事务边界

- 实施前列出全仓迁移并为 LP-02/03/05 分配唯一版本；禁止修改已执行 V1-V33。
- 新增 `LearnerProfileFullUpgradeIT`：先 target V33，再 migrate latest、`validate()`，验证三类新表/约束/索引。
- 原子 E2E：正式 Review、tag associations、PENDING queue row 全有或全无。
- 画像 E2E：版本链、ACTIVE 唯一、batch apply 原子和并发 STALE。
- 队列 E2E：满批、key 隔离、最多一次、清理和重启积压。
- 任何迁移失败立即停止；已成功迁移不做 down migration。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Modify | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java` | 新迁移唯一性/可发现性 |
| Modify | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/support/PostgresIntegrationTestSupport.java` | 通用 target/validate helpers |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/profile/LearnerProfileFullUpgradeIT.java` | V33 -> latest |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/profile/LearnerProfileEndToEndIT.java` | Review -> queue -> profile -> recall |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/profile/LearnerProfileFailureDegradationIT.java` | 失败/最多一次/降级 |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | 最终默认配置 |
| Modify | `backend/mentor-api/src/main/resources/application-local.yml` | 本地显式开关示例 |
| Modify | `.env.example` | 环境变量映射 |
| Modify | `deploy/docker/docker-compose.yml` | 仅在当前部署确需透传时补环境变量 |
| Create | `docs/ai-learner-profile-rollout-runbook.md` | 灰度、观察、切换和回滚 runbook |
| Modify | `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx` | 最终前端回归集 |

## 9. 分阶段实施步骤

1. 审计最终迁移/配置/常量，消除跨 LP 漂移并完成 V33 升级 IT。
2. 建立后端 E2E fixture：真实 PostgreSQL/Mapper/事务/queue，fake AI gateway。
3. 覆盖 Review -> tag -> PENDING message -> 五条满批 -> profile version。
4. 覆盖 declared tool 三状态、全原子失败、本 run/下一 run 快照语义。
5. 覆盖 recall 范围、800/8,000 裁剪、查询失败和正式 Review隔离。
6. 完成前端状态、全量构建、隐私/治理/运维审计并编写 rollout runbook。
7. 在预发布按运行时 A-D 顺序演练开启和回滚。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | Flyway unique/upgrade/validate | V33 可无损升级，版本全仓唯一 |
| 2 | Spring E2E context | 所有条件 Bean 和 properties 正确 |
| 3 | `LearnerProfileEndToEndIT` | 五条触发一次，形成合法 ACTIVE |
| 4 | tool scenario tests | UPDATED/NO_CHANGE/FAILED 且回答继续 |
| 5 | recall/failure tests | 下一 run 读取，查询异常降级，Review零注入 |
| 6 | frontend/build/security review | 状态幂等、构建通过、无敏感日志 |
| 7 | canary runbook checklist | 开关和节点切换可重复执行 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 运行 LP-01 至 LP-12 全部最小单测和各自 PostgreSQL IT。
- `LearnerProfileEndToEndIT`：创建用户/题目/tag/session，提交 5 条正式 Review，断言每条消息原子入库、第五条派发后一次 AI、画像更新。
- 同步工具：fake AI 返回 APPLIED、全部 NO_CHANGE、异常/非法输出；断言三状态和数据库原子性。
- Prompt：同 run 工具更新后 snapshot 不变，下一 run读取新 ACTIVE；查询异常不影响聊天；Review独立 prompt 不含 profile section。
- 停机积压：PENDING 在重启/重新启用后继续；出队提交后 callback 失败的 SUCCEEDED 不再派发。
- cleanup：过期成功删除、PENDING 保留、每次不超过 1,000。
- 前端：四状态、重复 SSE、FAILED 后 content_delta、Review/权限回归。

## 12. 日志、指标、隐私和 AI governance 要求

- 发布面板至少包含：各 topic PENDING 数、可派发 batch/key 数、可派发最老年龄、dequeue/callback 失败率、画像 update/STALE/非法输出、版本增长、Prompt 裁剪、Token/成本。
- 1-4 条未满批 PENDING 是正常状态；告警使用“已满足 batchSize 的 eligible key 最老年龄”，不能只看全局 oldest pending。
- 日志不得含完整自述、代码、Review Markdown、queue value、Authorization、cookie 或 secret。
- 指标不得以 userId/key/reviewId/tagId 为 tag。
- 发布阻塞：诊断快照中画像正文的 30 天保留、管理员访问与审计必须通过安全确认；否则 recall 保持 disabled。
- 全局 provider timeout、模型、purpose/source、实际 Token 与当前价格成本必须能在治理后台查询。

## 13. 发布顺序、兼容性和回滚方案

研发波次按用户指定执行：

```text
A: LP-01 / LP-03 / LP-05
B: LP-02 / LP-04 / LP-06
C: LP-07 / LP-08 / LP-10 / LP-11
D: LP-09 / LP-12
E: LP-13
```

生产运行时顺序：

```text
发布 A：profile/queue/review-tag 表和队列基础设施，所有开关 false
发布 B：正式 Review 语义、标签归因、原子消息发布，consumer false
发布 C：唯一节点启用 queue worker + Review画像 consumer
发布 D：灰度启用 declared tool、practice recall 和前端状态
```

回滚顺序：先关 recall -> 关 declared tool -> 关业务 consumer -> 关全局 worker -> 回滚应用。保留所有表和数据；不得把 SUCCEEDED 重置 PENDING。节点切换先停旧、确认 callback 结束，再启新。

## 14. 风险与开放项

- 发布阻塞：trace snapshot 画像正文保留策略未获确认。
- 发布阻塞：无法证明只有一个节点 `QUEUE_CONSUMER_ENABLED=true`。
- 风险：最多一次丢失窗口导致观察缺口；第一版接受，必须通过 callback failure 指标暴露。
- 风险：模型成本/非法输出率升高。灰度 15 分钟窗口内 callback/非法输出持续超过 5% 时关闭 consumer；Prompt 裁剪率持续超过 20% 时暂停扩大灰度并调研内容长度。
- AI 成本阈值不在代码中写死，由内测治理预算给出；超预算立即关闭相应 feature switch。
- 明确不以本任务引入 retry、回放或自动选主。

## 15. 可复制执行的验证命令

```bash
find backend -path '*/src/main/resources/db/migration/*' -type f | sort -V

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=FlywayMigrationResourceTest test

make backend-test
make backend-it
npm --cache ./.npm --prefix frontend test -- PracticeChatWorkbench.test.tsx
make frontend-build
make build
git diff --check
```

## 16. 最终验收 checklist

- [ ] V33 -> latest 迁移、validate 和全仓版本唯一通过。
- [ ] Review、tag、queue message 原子且幂等恰好一条。
- [ ] 五条同用户消息触发一次画像批量更新。
- [ ] 同步工具 UPDATED/NO_CHANGE/FAILED 均可用且失败不阻断。
- [ ] 下一 run 召回最新画像，本 run 不热重载。
- [ ] 正式 Review评分不受画像注入。
- [ ] PENDING 可重启续跑，SUCCEEDED callback 失败不回放。
- [ ] consumer 单节点和开关切换已演练。
- [ ] 日志、指标、trace、Token/成本通过治理审计。
- [ ] 前后端测试、PostgreSQL IT 和全量 build 全部通过。
- [ ] 回滚不删表、不重置 SUCCEEDED、不丢失 PENDING。
