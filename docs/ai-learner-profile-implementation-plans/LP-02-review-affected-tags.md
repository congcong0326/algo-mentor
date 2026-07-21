# LP-02：Review 受信标签归因与关联持久化实施计划

> 波次：B
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-data-model-and-storage-design.md`、`docs/ai-learner-profile-agent-integration-technical-direction.md`
>
> 直接依赖：题目标签建模门禁、LP-01

## 1. 任务目标与完成标准

让正式 Code Review 模型只能从当前题目的规范化受信标签中选择 `affectedTagIds`，由服务端校验后与 Review 主表原子保存。

完成后：模型不能创建自由文本标签或选择题目候选外 ID；非法/重复 ID 被丢弃且不阻断有效 Review；空关联合法；可以按 Review 和按 tag 双向查询；保存结果能区分新建与幂等复用。

## 2. 当前实现基线

- V33 已创建 `problem_tag` 和 `problem_tag_assignment`，但现有 `ProblemTag(value,label)` HTTP 读取模型不暴露 `tagId`。
- `ProblemTagRepository` 与 `ProblemTagMapper` 主要服务 seed 写入和一致性校验，没有业务侧受信标签读取端口。
- `PracticeCodeReviewAgentTool` 构造 `PracticeTurnContext` 时 `problemFacts`、标签候选等均为空。
- `PracticeCodeReviewPromptBuilder`、`PracticeCodeReviewJsonSchema`、structured mapper、Draft、领域模型和 Mapper 均无 `affectedTagIds`。
- `MyBatisPracticeCodeReviewRepository.save` 锁 session 后使用 `ON CONFLICT ... DO UPDATE` 返回记录，无法表达本次是 insert 还是 reuse。

## 3. 已确认的代码冲突或缺口

1. 不能从现有展示 DTO `ProblemTag` 反推受信 tagId，也不能让 `mentor-application` 依赖 API 包。
2. Review 模型当前没有候选集合，无法限制自由标签输出。
3. `affectedTagIds` 若只放 JSON 或正文，后续消费者无法做确定性关系查询。
4. 主表与关联表若由两个独立 repository 事务保存，会产生部分提交。
5. 并发相同 `userMessageId` 时现有返回契约无法阻止 LP-08 重复发布消息。

## 4. 范围、非目标和依赖

范围：application 受信标签模型/端口、题目标签查询适配、Review context/prompt/schema/mapper/domain 扩展、关联表迁移、原子保存、双向查询和 created/reused 契约。

非目标：不更新 `learner_profile_entry`，不发布队列消息，不实现异步消费者，不改能力雷达“一题贡献全部题目标签”语义，不向前端展示 `affectedTagIds`。

依赖：题目标签规范化迁移与读取门禁、LP-01 正式事实语义；LP-08、LP-09、LP-11 复用本任务端口和关联。

## 5. 关键技术决策

- 新建 application 端口 `TrustedProblemTagCatalog`，返回 `TrustedProblemTag(tagId,value,labelEn,labelZh)`；不修改现有 HTTP `ProblemTag`。
- `PracticeCodeReviewAgentTool` 在调用 Review 服务前按 `problemSlug` 读取候选，并放入 `PracticeTurnContext`。
- Review schema 升级为 `PracticeCodeReviewConstants.SCHEMA_VERSION=v2`，`affectedTagIds` 为 required array，可为空。
- structured mapper 只接收正整数 ID，先去重，再与 context 候选交集；候选外 ID丢弃。
- `PracticeCodeReviewDraft` 和 `PracticeCodeReview` 保存合法 ID 列表，但数据库权威来源是 `practice_code_review_tag`。
- `PracticeCodeReviewRepository.save` 改为返回 `PracticeCodeReviewSaveResult(review,created)`；session 加锁后重新查询 user message，已有则 `created=false`。
- 新建 Review 时主表和合法关联同一事务；幂等复用不重写原标签关联。

## 6. 领域模型、接口、常量和配置契约

- `TrustedProblemTagCatalog.findByProblemSlug(String problemSlug)` 返回按 assignment ordinal 排序的受信标签。
- JSON 字段固定为 `affectedTagIds`，统一放入 `PracticeCodeReviewConstants`，不得在 Prompt、Schema、mapper 中重复字面量。
- Schema v2：`affectedTagIds.items.type=integer`、`minimum=1`，数组为空合法。
- `PracticeCodeReviewSaveResult.created` 是 LP-08 的唯一发布判断；不得通过 reviewId、versionNo 或预查结果推断。
- 非法 ID 处理 outcome 固定为 `accepted`、`duplicate_dropped`、`candidate_miss_dropped`、`empty`，仅用于日志/指标。
- 本任务不新增配置 key；候选数量来自题目规范化关联。

## 7. 数据库迁移、约束、索引和事务边界

- Create `backend/mentor-api/src/main/resources/db/migration/V<实施时唯一版本>__practice_code_review_tags.sql`；编码前重新扫描共享 Flyway 空间。
- 表：`review_id BIGINT REFERENCES practice_code_review(id) ON DELETE CASCADE`，`tag_id BIGINT REFERENCES problem_tag(id) ON DELETE RESTRICT`，主键 `(review_id,tag_id)`。
- 索引：`idx_practice_code_review_tag_tag_review(tag_id,review_id)`；主键已支持按 review 查询。
- `MyBatisPracticeCodeReviewRepository.save` 是 Review 主表和关联的单一事务边界。
- 顺序：锁 session -> 事务内按 userMessageId 复查 -> 已有返回 reused -> 插入主表 -> 批量插入合法 tag -> 返回 created。
- 关联写失败必须回滚主表；非法候选在进入事务前丢弃，不触发有效 Review 回滚。
- 不允许删除被历史 Review 关联的 `problem_tag`；标签停用不删除历史归因。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/TrustedProblemTag.java` | 受信标签模型 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/TrustedProblemTagCatalog.java` | 读取端口 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewSaveResult.java` | created/reused 契约 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnContext.java` | 携带候选标签 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentTool.java` | 读取受信候选 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPromptBuilder.java` | 渲染候选和选择约束 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewJsonSchema.java` | 增加 required 数组 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewStructuredOutputMapper.java` | 校验/去重/过滤 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewDraft.java` | 合法 tagIds |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReview.java` | 读取关联 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewRepository.java` | save result 与查询扩展 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/mapper/ProblemTagMapper.java` | 受信标签查询 |
| Modify | `backend/mentor-api/src/main/resources/mapper/problem/ProblemTagMapper.xml` | assignment + catalog 查询 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/service/MyBatisTrustedProblemTagCatalog.java` | application 端口适配 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeCodeReviewMapper.java` | 关联写读 |
| Modify | `backend/mentor-api/src/main/resources/mapper/practice/PracticeCodeReviewMapper.xml` | 幂等复查和关联 SQL |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeCodeReviewRepository.java` | 原子保存与映射 |
| Create | `backend/mentor-api/src/main/resources/db/migration/V<实施时唯一版本>__practice_code_review_tags.sql` | 关联表 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java` | 注入 tag catalog |

## 9. 分阶段实施步骤

1. 建立 `TrustedProblemTag`/Catalog 和 API 读取适配，先用 V33 真实关联验证顺序与语言标签。
2. 扩展 context、Prompt、Schema v2 和 structured mapper，固定缺失/空/重复/非法语义。
3. 扩展 Draft/Review/Repository save result，更新所有测试替身。
4. 分配迁移版本，创建关联表和索引。
5. 重写 repository 保存流程，事务内复查幂等并原子写主表/关联。
6. 增加双向查询、Mapper XML 测试和 PostgreSQL 原子性/外键 IT。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | trusted catalog repository test/IT | 返回 tagId/value/双语 label，顺序稳定 |
| 2 | prompt/schema/mapper tests | 候选外 ID 被丢弃，空数组合法 |
| 3 | repository contract tests | 新建 `created=true`，复用 false |
| 4 | migration IT | PK、FK、索引和级联行为生效 |
| 5 | transaction IT | 关联失败时主表回滚，并发只一条 created |
| 6 | query tests | 按 review/tag 双向读取一致 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- Prompt/Schema 测试覆盖候选包含 `tagId/value/labelEn/labelZh`，Schema version 为 v2。
- mapper 覆盖 `affectedTagIds` 缺失、空、重复、负数、非整数、候选外和混合合法输入。
- repository 单测覆盖空关联、批量关联、幂等复用不重写标签。
- PostgreSQL IT 覆盖主键去重、Review 删除级联、标签删除受限、按 tag/review 查询、空关联合法。
- 故障注入关联 insert，断言主表和关联全无；非法 ID 在入库前丢弃时正式 Review仍成功。
- 两连接并发同一 userMessageId，断言一个 `created=true`、一个 false、只有一组关联。
- 回归运行 `ProblemTagNormalizationMigrationIT`、`ProblemTagReadModelIT`，确保不改变题库 HTTP 契约。

## 12. 日志、指标、隐私和 AI governance 要求

- 日志只记录候选数量、返回数量、accepted/dropped 数量和 schema version，不记录代码、Review Markdown 或完整候选正文。
- 指标：`practice.review.affected_tags{outcome}`；不得以 tagId/problemSlug/userId 作 tag。
- Review LLM 仍使用 `AiPurpose.LEARNING_CHAT` 与 `AiRunSource.PRACTICE_CODE_REVIEW`，本任务不新增画像 source。
- 受信 tagId 只来自数据库和服务端 context，不接受模型传入题目身份。

## 13. 发布顺序、兼容性和回滚方案

- 先确认题目标签门禁，再部署 LP-01，然后部署迁移、Schema v2 和关联保存为同一发布单元。
- 现有 HTTP `ProblemTag(value,label)` 和前端类型保持不变。
- 迁移为增量；回滚应用时保留关联表，不做 down migration。
- 若 Schema v2 provider 兼容失败或非法 ID 比例异常，回滚应用到无标签归因版本；正式 Review主表仍兼容。

## 14. 风险与开放项

- 风险：当前 `PracticeCodeReviewAgentTool` 的 `problemFacts` 仍为空。LP-02 只保证受信标签候选，不顺带重构完整题面注入。
- 风险：模型返回大量候选外 ID。通过指标观察，不允许因此拒绝有效 Review。
- 风险：读取 inactive 标签。Catalog 查询应只返回当前题目已关联且目录存在的标签；历史关联保留 inactive tag 的读取能力供 LP-09。
- 明确不修改能力雷达归因和前端展示。

## 15. 可复制执行的验证命令

```bash
find backend -path '*/src/main/resources/db/migration/*' -type f | sort -V

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='PracticeCodeReview*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=FlywayMigrationResourceTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=PracticeCodeReviewTagIT,ProblemTagNormalizationMigrationIT,ProblemTagReadModelIT verify

git diff --check
```

## 16. 最终验收 checklist

- [ ] 受信标签端口位于 application 层且不改变 HTTP DTO。
- [ ] Review Schema v2 固定 `affectedTagIds`。
- [ ] 非法/重复 ID 被安全丢弃，空集合合法。
- [ ] 主表和合法关联同事务提交。
- [ ] save result 可区分 created/reused。
- [ ] 并发相同 userMessageId 只创建一条 Review 和一组关联。
- [ ] 可按 Review 和 tag 双向查询。
- [ ] 未实现画像更新、队列发布或能力雷达语义改变。
