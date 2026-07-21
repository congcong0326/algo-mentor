# LP-03：画像领域契约与 PostgreSQL 存储实施计划

> 波次：A
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-data-model-and-storage-design.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：题目标签建模门禁（V33 与规范化读取链路通过）

## 1. 任务目标与完成标准

落地 `learner_profile_entry` 统一版本表、强类型领域契约和 PostgreSQL/MyBatis 基础持久化边界。完成后，后续任务可按 dimension 身份或 tag 身份读取当前条目和历史版本，但本任务不调用模型、不执行版本切换编排。

完成标准：数据库拒绝非法 kind/dimension/tag/status/origin 组合；同一业务身份最多一条 `ACTIVE`；revision 和 supersedes 链在数据库层唯一；Repository 支持当前、历史和锁定读取。

## 2. 当前实现基线

- 仓库目前不存在 `LearnerProfile*` Java 类型、`learner_profile_entry` 表、Mapper、Repository 或配置。
- `mentor-application` 已是算法学习业务应用层，适合拥有画像语义和 Repository 端口。
- `mentor-api` 已集中承载业务 MyBatis mapper、XML、repository 和 API 自有迁移。
- `auth_users` 由 identity/auth 链路维护；`problem_tag` 由 V33 创建并通过 `problem_tag_assignment` 规范化。
- Flyway 使用 `classpath:db/migration` 递归扫描，跨模块共享版本空间，当前最高版本为 V33。
- `PostgresIntegrationTestSupport` 已提供 PostgreSQL 16 Testcontainers、Flyway、MyBatis XML 和事务基座，但 `migrateToV32()` 是硬编码方法。

## 3. 已确认的代码冲突或缺口

1. 没有画像领域模型和合法性矩阵，无法阻止模型自由生成 dimension 或 tag 范围。
2. 没有可支持 dimension/tag 两种业务身份的 Repository 契约。
3. 没有当前 ACTIVE、历史 revision 和锁定当前版本的 SQL。
4. 当前没有可复用的通用敏感正文校验器；`PracticeCodeReviewPermissionHook` 的脱敏实现是私有局部逻辑。
5. Flyway 测试支持仍绑定 V32，无法复用到新增迁移的前后版本验证。

## 4. 范围、非目标和依赖

范围：领域枚举、条目/身份/来源模型、内容策略、Repository 端口、Flyway 表结构、Mapper/XML/MyBatis 适配、配置绑定和存储测试。

非目标：不调用 LLM，不实现 `NO_CHANGE/REPLACE` 服务编排，不做 Agent tool、Prompt 注入、REST API、画像管理 UI、candidate/evidence/detail JSON 表。

依赖：题目标签 V33 门禁；后续 `LP-04`、`LP-09`、`LP-10`、`LP-11` 依赖本任务。

## 5. 关键技术决策

- 画像领域类型放在 `mentor-application/profile`，不下沉到 `agent-core`。
- PostgreSQL 实现放在 `mentor-api/profile`，复用当前 `SqlSessionTemplate`，不新建竞争的 `SqlSessionFactory`。
- `content_text` 是完整自然语言正文，不作为 JSON 或机器可解析协议。
- 首次和普通补充均使用 `USER_EXPLICIT`；明确纠正使用 `USER_CORRECTION`；系统推导使用 `SYSTEM_DERIVED`，不新增第四种 origin。
- 应用层正文上限固定为配置 `algo-mentor.learner-profile.content.max-chars`，默认 4,000 字符；数据库只校验 trim 后非空，避免配置调整需要迁移。
- 内容安全由 `LearnerProfileContentPolicy` 统一校验长度和明显密钥模式；不复用私有 Review hook 实现。
- 本任务只提供 `findCurrentForUpdate` 基础能力，真正的并发编排在 `LP-04`。

## 6. 领域模型、接口、常量和配置契约

固定枚举：

- `LearnerProfileEntryKind`：`DECLARED_FACT`、`GENERAL_OBSERVATION`、`TAG_ASSESSMENT`。
- `LearnerProfileDimension`：上游设计中的十个固定 dimension。
- `LearnerProfileEntryStatus`：`ACTIVE`、`SUPERSEDED`、`SUPPRESSED`。
- `LearnerProfileOriginType`：`USER_EXPLICIT`、`USER_CORRECTION`、`SYSTEM_DERIVED`。
- `ProfileUpdateAction`：`NO_CHANGE`、`REPLACE`；本任务只定义契约，不执行更新。

领域类型：

- `LearnerProfileIdentity` 封装两类身份：`userId + entryKind + dimension` 或 `userId + tagId`。
- `LearnerProfileEntry` 表示完整持久化版本。
- `LearnerProfileEntryDraft` 表示受信服务端待写字段，不允许模型指定 user/kind/status/revision。
- `LearnerProfileRepository` 提供 `findCurrent`、`findCurrentForUpdate`、`findHistory`、`findCurrentByDimensions`、`findCurrentByTagIds`、`insert`、`markInactive` 基础方法。
- `LearnerProfileProperties` 绑定 `algo-mentor.learner-profile.content.max-chars=4000`。

合法映射必须由 `LearnerProfileContract` 单点维护，并与数据库 `ck_learner_profile_entry_scope` 一致。

## 7. 数据库迁移、约束、索引和事务边界

- Create `backend/mentor-api/src/main/resources/db/migration/V<实施时唯一版本>__learner_profile_entry.sql`；编码前扫描全仓版本，不在计划阶段锁死 V34。
- 表字段按上游设计固定：`user_id`、`entry_kind`、`dimension`、`tag_id`、`revision_no`、`status`、`content_text`、`supersedes_entry_id`、`origin_type`、provider/model/prompt version、valid/created/updated 时间。
- 外键：`user_id -> auth_users(id) ON DELETE CASCADE`；`tag_id -> problem_tag(id)`；`supersedes_entry_id -> learner_profile_entry(id)`。
- Check：kind、dimension、status、origin、revision 正数、content 非空、ACTIVE/非 ACTIVE validity、scope 合法矩阵。
- 局部唯一索引：ACTIVE tag、ACTIVE dimension、supersedes 唯一、tag revision 唯一、dimension revision 唯一。
- Repository 写方法不自行开启跨调用大事务；调用方事务存在时必须加入同一事务。
- `findCurrentForUpdate` 只锁已经存在的画像行；首次创建锁策略由 `LP-04` 通过 `auth_users` 用户行解决。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileEntryKind.java` | kind 枚举 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileDimension.java` | dimension 枚举 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileEntryStatus.java` | 状态枚举 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileOriginType.java` | 来源枚举 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ProfileUpdateAction.java` | 更新 action |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileIdentity.java` | 两类业务身份 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileEntry.java` | 领域条目 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileRepository.java` | 持久化端口 |
| Create | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileContentPolicy.java` | 正文校验 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/profile/mapper/LearnerProfileMapper.java` | MyBatis mapper |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/profile/mapper/model/LearnerProfileEntryRow.java` | 行模型 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/profile/repository/MyBatisLearnerProfileRepository.java` | PostgreSQL 适配 |
| Create | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/LearnerProfileProperties.java` | 配置绑定 |
| Create | `backend/mentor-api/src/main/resources/mapper/profile/LearnerProfileMapper.xml` | SQL |
| Create | `backend/mentor-api/src/main/resources/db/migration/V<实施时唯一版本>__learner_profile_entry.sql` | 表与约束 |
| Modify | `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java` | 注册 mapper/repository |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | 默认正文上限 |
| Modify | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/support/PostgresIntegrationTestSupport.java` | 增加通用 `migrateTo(String)` |

## 9. 分阶段实施步骤

1. 定义枚举、业务身份和合法矩阵，先以纯 Java 单测固定契约。
2. 新增配置与正文策略，覆盖空白、长度边界和明显密钥模式。
3. 分配唯一 Flyway 版本并创建表、约束和索引。
4. 新增 Mapper/XML/行模型和 Repository 适配，覆盖当前、历史和锁定查询。
5. 接入 MyBatis/Properties 自动配置，不暴露 HTTP API。
6. 增加 XML 加载测试、Repository 单测和 PostgreSQL 约束 IT。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | `LearnerProfileContractTest` | 合法矩阵与枚举一一对应 |
| 2 | `LearnerProfileContentPolicyTest` | 4,000 边界、空白和敏感模式可重复验证 |
| 3 | `LearnerProfileMigrationIT` | 非法 scope/validity/revision 被 PostgreSQL 拒绝 |
| 4 | `MyBatisLearnerProfileRepositoryTest` | 两类身份映射和排序正确 |
| 5 | 配置上下文测试 | Repository/Properties Bean 可按条件装配 |
| 6 | Mapper XML + IT | 唯一索引、外键和锁定查询真实生效 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 单元测试覆盖 kind/dimension/tag/status/origin 全矩阵，特别是 `TAG_ASSESSMENT + TAG_MASTERY + tagId`。
- Mapper XML 测试沿用 `PracticeCodeReviewMapperXmlTest` 模式，检查所有 statement ID。
- PostgreSQL IT 验证：用户/标签外键、空正文、validity、两类 ACTIVE 局部唯一、revision 唯一、supersedes 唯一、删除用户级联。
- 验证 `findCurrent` 默认只返回 ACTIVE；历史按 revision 降序；`findCurrentForUpdate` 在事务内持锁。
- 本任务不测试并发版本切换，只验证存储能力；并发双连接测试在 `LP-04`。
- `PostgresIntegrationTestSupport` 新增 `migrateTo(String version)`，保留 `migrateToV32()` 兼容既有测试。

## 12. 日志、指标、隐私和 AI governance 要求

- Repository 日志只记录 kind/dimension、计数、revision 和状态，不记录 `content_text`、userId/tagId 作为指标 tag。
- 存储层指标建议：`learner.profile.repository{operation,outcome}` 和耗时；tag 仅使用受控 operation/outcome。
- 本任务不调用 AI，不新增 `AiPurpose/AiRunSource`。
- `content_text` 属于用户内容；错误日志不得拼接正文，Mapper 参数日志在生产环境不得开启明文。

## 13. 发布顺序、兼容性和回滚方案

- 迁移为纯增量，可与 `LP-01`、`LP-05` 并行开发并先行部署。
- 迁移成功后不做 down migration；应用回滚时保留空表或已写画像数据。
- 新模块代码在没有消费者和工具时无用户可见行为。
- 迁移失败立即停止发布，禁止跳过约束后继续。

## 14. 风险与开放项

- 风险：Java 合法矩阵与 SQL check 漂移。必须共享同一测试样例表并逐项断言。
- 风险：正文安全检测误报。第一版仅拦截明显密钥形态，不推断敏感身份属性。
- 开放项：精确正文上限可在实施前依据 Prompt 样例调整；若改变默认值，只改配置与测试，不改表结构。
- 明确不处理画像查看、抑制、删除 API 和历史 UI。

## 15. 可复制执行的验证命令

```bash
find backend -path '*/src/main/resources/db/migration/*' -type f | sort -V

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='*LearnerProfile*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=FlywayMigrationResourceTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=LearnerProfileMigrationIT verify

git diff --check
```

## 16. 最终验收 checklist

- [ ] 画像枚举和合法矩阵已由单元测试固定。
- [ ] Flyway 版本在全仓共享空间唯一。
- [ ] 数据库拒绝所有非法 scope/validity/revision 组合。
- [ ] 同一业务身份最多一条 ACTIVE。
- [ ] revision 与 supersedes 唯一索引生效。
- [ ] Repository 覆盖 dimension 和 tag 两类身份。
- [ ] 正文和日志隐私边界明确。
- [ ] 未实现模型调用、更新编排、工具、Prompt 或管理 API。
