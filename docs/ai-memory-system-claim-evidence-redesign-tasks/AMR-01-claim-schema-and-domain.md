# AMR-01：Claim、证据与更新 Run 表结构及领域模型

> 波次：A
>
> 状态：PENDING
>
> 直接依赖：AMR-00
>
> 建议首轮文件上限：12

## 1. 目标与完成标准

以增量方式创建五张 learner memory 新表，并建立不依赖 Spring、MyBatis 或 AI 的强类型领域模型。旧 `learner_profile_entry` 暂时保留，但本任务不双写、不切换任何生产入口。

完成后 PostgreSQL 能拒绝非法 scope、状态、revision、hash 和 evidence role，Java 合法矩阵与 SQL 一致，后续 Repository 可以直接基于新模型开发。

## 2. 必须读取

- `CURRENT.md` 和 `AMR-00` 完成备注。
- `CONTRACTS.md` 第 2 至 6 节。
- `backend/mentor-api/src/main/resources/db/migration/V34__learner_profile_entry.sql`。
- `backend/mentor-api/src/main/resources/db/migration/V13__practice_code_review_schema.sql`。
- `backend/mentor-api/src/main/resources/db/migration/V36__practice_code_review_tags.sql`。
- `backend/agent-persistence-postgres/src/main/resources/db/migration/agent/V2__agent_conversation_context.sql`。
- 旧 `LearnerProfileContract.java` 和枚举，仅用于复用合法矩阵，不沿用整段 entry 身份语义。
- `PostgresIntegrationTestSupport` 和现有 `LearnerProfileMigrationIT` 的迁移测试模式。

## 3. 数据库契约

使用实施时重新扫描得到的全局唯一版本，创建：

- `learner_memory_update_run`
- `learner_memory_update_run_review`
- `learner_memory_claim_revision`
- `learner_memory_claim_review_evidence`
- `learner_memory_claim_message_evidence`

必须落实：

- 所有 `CONTRACTS.md` 固定字段、枚举 check、kind/dimension/tag 合法矩阵和 600 字符硬上限。
- `claim_text_hash` 使用固定 64 位小写 SHA-256 文本格式。
- 同一 `claim_key` 的当前 revision 局部唯一，条件为 `status <> 'SUPERSEDED'`。
- `claim_key + revision_no`、`supersedes_revision_id` 和同 scope ACTIVE 文本 hash 唯一。
- 当前 revision 与 `valid_to`、`SUPERSEDED` 与 `valid_to` 的一致性约束。
- user FK 级联删除；tag、Review、message 和前一 revision 不允许静默失源。
- nullable `agent_run_id` 与 `agent_run` 建立 FK；删除诊断 run 时允许置空，不删除业务 update run。
- 触发批次 Review 关联按 `sequence_no` 唯一、同一 run 不重复 Review。

本迁移不得删除旧表或旧 queue 数据。

## 4. 领域模型与目录

在 `mentor-application/.../profile/` 下按职责建立：

```text
claim/model/
evidence/model/
run/model/
```

至少包含：

- kind、dimension、revision status、origin、operation action。
- review/message evidence role、evidence pattern、evidence grade。
- claim scope、claim revision、review/message evidence、update run、capacity state。
- Java 合法矩阵和公共限制常量；跨模块字符串不散落在 Prompt、SQL 和 API 中。

模型构造器必须拒绝空文本、非法 tag scope、非正 ID/revision、非法状态时间组合和超长 claim。

## 5. 实施步骤

1. 先定义枚举、scope 和合法矩阵，使用参数化单测固定全部组合。
2. 新增迁移，创建表、约束、索引和注释；不修改任何历史迁移。
3. 建立领域 record/class 和公共限制常量。
4. 增加从 V34 已存在状态升级到新版本的 PostgreSQL IT，验证旧表仍存在、新表为空且约束生效。
5. 增加 Flyway 资源唯一性检查，确保版本没有跨模块冲突。

## 6. 重点测试

- kind/dimension/tag 全矩阵。
- 五种 revision 状态与 `valid_to` 组合。
- 当前 revision 唯一、revision_no 唯一、supersedes 唯一和自引用拒绝。
- ACTIVE scope 文本 hash 去重，其他 scope 或历史 revision 不冲突。
- claim 600 字符边界、空白、hash 格式。
- Review/message role check 和 `ON DELETE RESTRICT`。
- update run 幂等键、状态和触发 Review 顺序约束。
- V34 -> 新版本全量迁移成功且没有历史数据回填。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='*LearnerMemory*Contract*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=FlywayMigrationResourceTest \
  -Dit.test='*LearnerMemory*MigrationIT' verify

git diff --check
```

## 8. 非目标与停止条件

- 不实现 Mapper、Repository、更新事务、Agent、API 或前端。
- 不删除或读取旧画像数据。
- SQL 与 Java 合法矩阵任一不一致时不得开始 `AMR-02`。

## 9. 上下文交接

记录实际迁移版本、表名、关键索引名、领域包路径和测试结果。不要复制完整 SQL。

## 10. 完成备注

完成时间：2026-07-30T09:38:27Z

状态：DONE

主要改动：

- 新增 `V46__rebuild_learner_memory_claims.sql`，创建五张表、索引、外键、check 和注释；旧表仍保留。
- 新增 `profile/{claim,evidence,run}/model` 强类型契约、scope、revision、evidence 和 update run 模型。
- 增加 Flyway 资源唯一性断言及 V34 -> V46 的约束升级 IT。

验证：

- `mvn -f backend/pom.xml ... -pl mentor-application -am -Dtest='*LearnerMemory*Contract*Test' test`：PASS（2 tests）。
- `mvn -f backend/pom.xml ... -pl mentor-api -am -Dtest=FlywayMigrationResourceTest -Dit.test='*LearnerMemory*MigrationIT' verify`：PASS（IT 2 tests）。
- `git diff --check`：PASS。

偏离计划：

- 为修复新增 IT fixture，额外精确读取 V45 的 `agent_run` trigger 约束；未扩展生产范围。

遗留事项：

- 无；`AMR-02` 直接以新领域模型和 V46 表开发 Repository。

下一任务：`AMR-02`
