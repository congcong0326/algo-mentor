# LP-04：画像查询、版本更新与并发一致性实施计划

> 波次：B
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-data-model-and-storage-design.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-03

## 1. 任务目标与完成标准

实现 `LearnerProfileQueryService` 与 `LearnerProfileUpdateService`，为同步工具、异步消费者和 Prompt 召回提供统一的 ACTIVE 查询、`NO_CHANGE/REPLACE`、批量原子 apply 和并发陈旧检测。

完成后：`NO_CHANGE` 零写入；`REPLACE` 形成连续版本链；并发更新无双 ACTIVE、revision 冲突或静默覆盖；查询默认排除历史和 SUPPRESSED；模型调用方可通过 `STALE` 结果在事务外基于最新正文重算。

## 2. 当前实现基线

- LP-03 将提供画像枚举、身份、表、Repository、当前/历史/锁定读取。
- 当前仓库没有画像 Query/Update service、snapshot token、批量 apply 或并发策略。
- `auth_users` 对每个用户始终存在，画像首次创建时却没有画像行可锁。
- 项目大量使用 Spring `@Transactional` 和 `TransactionTemplate`，适合短事务版本切换。
- 设计明确要求模型返回后重新读取当前版本；模型调用不得持有数据库事务。

## 3. 已确认的代码冲突或缺口

1. 仅 `SELECT learner_profile_entry ... FOR UPDATE` 无法串行首次创建。
2. 仅依赖 ACTIVE 唯一索引会把并发冲突暴露为数据库异常，无法识别并重算陈旧模型结果。
3. 若 UpdateService 内部调用 LLM，会在锁期间持有长事务或混淆职责。
4. 多维同步更新和异步批量更新需要全有或全无，但 LP-03 只有单条存储能力。
5. `SUPPRESSED` 和物理删除需要内部边界，但第一版不应顺带开放管理 API。

## 4. 范围、非目标和依赖

范围：ACTIVE 批量查询、snapshot、decision/apply result、单条与同用户批量版本切换、用户行锁、陈旧检测、抑制/删除内部边界、指标和并发 IT。

非目标：不编写具体画像 Prompt，不调用 LLM，不决定场景召回范围，不实现队列、Agent tool、REST API 或管理 UI。

依赖：LP-03；LP-09、LP-10、LP-11 直接依赖本任务。

## 5. 关键技术决策

- 第一版并发锁固定为 `auth_users` 用户行 `FOR UPDATE`，不使用 advisory lock。理由是首次创建可锁、无 hash 碰撞、实现和测试更直观；代价是同一用户不同 dimension 串行，画像写频率可接受。
- 模型调用流程固定为：无事务读取 snapshot -> 事务外模型决策 -> 短事务锁用户 -> 重读 snapshot -> 比较 token -> apply 或返回 STALE。
- snapshot token 由 `entryId + revisionNo` 表达；不存在当前条目时 token 为 `ABSENT`。
- UpdateService 不负责自动重新调用模型；LP-09/LP-10 对 STALE 最多重算一次，第二次仍 STALE 则失败。
- `applyBatch` 只接受同一 userId，先完整校验，锁一次用户行，所有 REPLACE 全有或全无。
- `NO_CHANGE` decision 不要求 content；`REPLACE` content 是新的完整正文，不做 patch/append。

## 6. 领域模型、接口、常量和配置契约

- `ProfileUpdateDecision(action,content,reason)`：action 仅 `NO_CHANGE/REPLACE`。
- `LearnerProfileSnapshot(identity,currentEntry,snapshotToken)`。
- `ProfileUpdateApplyStatus`：`NO_CHANGE`、`APPLIED`、`STALE`。
- `ProfileUpdateApplyResult(status,currentEntry)`；STALE 返回最新 entry/snapshot，供调用方重算。
- `ProfileUpdateCommand` 由服务端指定 identity、origin、provider/model/promptVersion 和 expected token。
- `LearnerProfileQueryService`：按 user/kind/dimensions/tagIds 批量返回 ACTIVE，输入集合去重并保持确定性顺序。
- `LearnerProfileUpdateService.apply` 与 `applyBatch`；`suppress`、`deleteIdentity` 为内部方法，不暴露 Controller。
- 公共状态、metadata 字段和失败码放入 profile 常量/枚举；不在 LP-09/LP-10 重复字面量。

## 7. 数据库迁移、约束、索引和事务边界

- 本任务原则上无新迁移，复用 LP-03 表和索引；若实施发现缺少必要查询索引，必须以独立唯一 Flyway 版本补充并更新 LP-03 契约测试。
- 用户锁 SQL：`SELECT id FROM auth_users WHERE id = #{userId} FOR UPDATE`，必须是版本切换事务中的第一把锁。
- 锁后按固定 identity 排序读取当前 ACTIVE，避免批量更新锁顺序漂移。
- 对每个 REPLACE：旧 ACTIVE 更新为 SUPERSEDED/valid_to；插入 revision+1 ACTIVE，`supersedes_entry_id` 指向旧版本。
- 首次创建 revision=1；旧版本不存在且 expected token 也是 ABSENT 才可创建。
- 新版本 insert 失败时整个事务回滚，旧版本恢复 ACTIVE。
- `suppress` 将当前 ACTIVE 改为 SUPPRESSED 并设置 valid_to；`deleteIdentity` 物理删除整条版本链，第一版仅内部调用。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileSnapshot.java` | 当前正文与 token |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ProfileUpdateDecision.java` | 模型最小决策 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ProfileUpdateCommand.java` | 受信 apply 命令 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ProfileUpdateApplyStatus.java` | NO_CHANGE/APPLIED/STALE |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ProfileUpdateApplyResult.java` | apply 结果 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileQueryService.java` | ACTIVE 查询 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileUpdateService.java` | 版本切换服务 |
| Modify（LP-03 Create 后） | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileRepository.java` | user lock、批量 apply 基础方法；当前仓库尚不存在 |
| Modify（LP-03 Create 后） | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/profile/mapper/LearnerProfileMapper.java` | 用户锁、批量更新 SQL；当前仓库尚不存在 |
| Modify（LP-03 Create 后） | `backend/mentor-api/src/main/resources/mapper/profile/LearnerProfileMapper.xml` | ACTIVE 重读、状态切换、批量写；当前仓库尚不存在 |
| Modify（LP-03 Create 后） | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/profile/repository/MyBatisLearnerProfileRepository.java` | 新 SQL 适配；当前仓库尚不存在 |
| Create | `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileUpdateServiceTest.java` | 服务单测 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/profile/LearnerProfileConcurrencyIT.java` | 双连接并发验证 |

## 9. 分阶段实施步骤

1. 固定 decision、snapshot token、apply status 和批量原子语义。
2. 实现 QueryService 的 ACTIVE 过滤、批量去重和确定性排序。
3. 扩展 Repository/Mapper，增加用户行锁、锁后重读和状态切换 SQL。
4. 实现单条 apply：NO_CHANGE、首次创建、REPLACE、STALE。
5. 实现同用户 batch apply、suppress/delete 内部边界和追踪字段写入。
6. 增加单元、事务失败和双连接并发 PostgreSQL IT。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | contract tests | token 与三种 apply 状态稳定 |
| 2 | QueryService tests | 仅返回指定 ACTIVE，顺序确定 |
| 3 | Mapper XML/Repository tests | 用户锁先于画像写，批量 SQL 可加载 |
| 4 | UpdateService tests | NO_CHANGE 零写，REPLACE 连续 revision |
| 5 | batch/suppress/delete tests | 同用户批次全有或全无 |
| 6 | `LearnerProfileConcurrencyIT` | 并发无双 ACTIVE、无分叉、陈旧返回 STALE |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 单元测试：首次创建、NO_CHANGE、REPLACE、SUPPRESSED 排除、批量查询过滤、跨用户 batch 拒绝、追踪字段透传。
- 失败测试：新版本 insert 故障后旧版本仍 ACTIVE；状态更新成功但 insert 失败时事务完全回滚。
- 陈旧测试：模型基于 revision 1，另一路先写 revision 2，旧命令返回 STALE 且零写入。
- 并发 IT 使用两个连接/线程屏障同时更新同一 absent identity 和已有 identity。
- 断言：只有一条 ACTIVE、revision 连续、supersedes 不分叉、无静默覆盖；另一调用获得 STALE 或基于新 snapshot 成功。
- batch IT 故障注入任一 dimension insert，断言所有 dimension 都未切换。

## 12. 日志、指标、隐私和 AI governance 要求

- 日志记录 entryKind/dimension、action、applyStatus、revision 和批次大小，不记录 content、userId/tagId 作为指标 tag。
- 指标：`learner.profile.update{action,outcome}`、`learner.profile.query{outcome}`、`learner.profile.update.stale`、耗时。
- 本任务不调用 AI；provider/model/promptVersion 仅作为持久化审计字段，不作为高基数 metrics tag。
- 锁等待日志只记录耗时和 outcome，不输出 SQL 参数正文。

## 13. 发布顺序、兼容性和回滚方案

- 仅在 LP-03 迁移和 Repository 通过后部署。
- 新 service 在没有 LP-09/LP-10/LP-11 接线时无用户可见行为。
- 若并发冲突率、锁等待或版本增长异常，停止上游更新入口并回滚应用；保留版本数据。
- 不回退数据库历史版本，不执行 down migration。

## 14. 风险与开放项

- 用户行锁会串行同一用户所有画像 dimension；第一版接受该吞吐权衡，后续有数据再评估更细锁。
- 与其他业务同时锁 `auth_users` 可能形成死锁；必须固定“先用户行、后画像行”的锁顺序并用超时/死锁 IT 观察。
- UpdateService 不重算模型，调用方必须显式处理 STALE；LP-09/LP-10 固定最多一次重算。
- suppress/delete 只提供内部边界，不代表本版本有用户管理能力。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='LearnerProfile*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=LearnerProfileConcurrencyIT verify

make backend-test
git diff --check
```

## 16. 最终验收 checklist

- [ ] QueryService 默认只返回目标 ACTIVE。
- [ ] NO_CHANGE 完全零写入。
- [ ] REPLACE 保留历史并形成连续版本链。
- [ ] 首次创建也受用户行锁串行保护。
- [ ] 陈旧模型结果返回 STALE 且不落库。
- [ ] 同用户 batch apply 全有或全无。
- [ ] 双连接 IT 无双 ACTIVE、revision 冲突或链路分叉。
- [ ] 未在事务或锁内调用模型。
