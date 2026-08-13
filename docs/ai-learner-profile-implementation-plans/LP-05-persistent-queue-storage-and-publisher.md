# LP-05：持久化队列契约、表结构与发布能力实施计划

> 波次：A
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：无

## 1. 任务目标与完成标准

建立与画像业务无关的 PostgreSQL 单播持久化队列最小内核，包括独立 Maven 模块、消息契约、`queue_message` 表、Repository 和 Publisher。

完成后，业务方可在已有 Spring 事务中发布 JSON 消息；消息只以 `PENDING` 入库；消费开关关闭不影响发布；超过 UTF-8 64 KiB 的 value 被拒绝且不截断。

## 2. 当前实现基线

- 仓库没有 `QueueMessage`、Publisher、消费者接口、`queue_message` 表或队列配置。
- `backend/pom.xml` 当前聚合 12 个模块，没有通用队列模块。
- `mentor-api` 已提供共享 `SqlSessionTemplate`、Jackson、Flyway、PostgreSQL 和事务管理器。
- `make backend-it` 与 Failsafe 目前只在 `mentor-api` 执行；现有 `PostgresIntegrationTestSupport` 可加载依赖模块的迁移和 mapper 资源。
- Flyway 递归扫描共享版本空间，当前最高版本 V33。

## 3. 已确认的代码冲突或缺口

1. 若把队列放进 `mentor-application`，会让通用基础设施依赖学习业务语义。
2. 若新模块自行创建全局 `SqlSessionFactory`，会与现有 API MyBatis 配置竞争。
3. 设计中的 `enabled=false` 仅关闭消费，不能条件化移除 Publisher。
4. JSON 字节上限必须按 Jackson 序列化后的 UTF-8 计算，不能按 Java 字符数或数据库 TEXT 长度估算。
5. 当前 IT 基座只存在于 `mentor-api`，需要明确跨模块测试落位。

## 4. 范围、非目标和依赖

范围：新建 `backend/persistent-queue` 单一模块；定义消息、状态、Publisher、Repository、异常、配置、MyBatis 适配、迁移和发布指标。

非目标：不注册消费者，不启动线程，不派发、不清理，不实现重试、死信、广播、租约、`PROCESSING`、多 Provider 或人工回放。

依赖：无；`LP-06`、`LP-07`、`LP-08` 直接依赖本任务。

## 5. 关键技术决策

- 模块名固定为 `persistent-queue`，包根为 `org.congcong.algomentor.queue`。
- 模块同时拥有稳定契约和 PostgreSQL/MyBatis 适配，第一版不拆 core/provider 多模块。
- 模块复用宿主应用的 `SqlSessionTemplate`、`ObjectMapper`、`PlatformTransactionManager` 和 `MeterRegistry`。
- `QueuePublisher.publish(topic,key,payload)` 使用共享 Jackson 序列化，队列不解析 payload 业务字段。
- Publisher 方法本身不使用 `REQUIRES_NEW`；存在调用方事务时参与同一事务，无事务时按默认 Spring 事务提交。
- PostgreSQL IT 放在 `mentor-api`，复用现有 Failsafe/Testcontainers；队列模块保留纯单元测试。
- `consumer.enabled` 在 `LP-07` 生效；本任务只保证 Publisher Bean 无条件可用。

## 6. 领域模型、接口、常量和配置契约

固定契约：

- `QueueMessage`：`messageId`、`topic`、`key`、`value`、`createdAt`。
- `QueueMessageStatus`：Publisher 只创建 `PENDING`；派发阶段扩展为 `PROCESSING`、`SUCCEEDED`、`FAILED`。
- `QueuePublisher.publish(String topic, String key, Object payload)` 返回持久化后的 `QueueMessage`。
- `QueueMessageRepository` 提供 insert、按 ID 查询、按 topic/key 查询 PENDING 的基础能力；派发 SQL 在 `LP-06` 扩展。
- `PersistentQueueConstants` 统一字段名、状态值和配置前缀；业务 topic 不放在通用模块。

配置：

```yaml
algo-mentor:
  queue:
    message:
      max-value-bytes: 65536
```

校验：topic trim 后 1..128；key trim 后 1..256；payload 不为 null；value UTF-8 字节数不超过配置上限。

## 7. 数据库迁移、约束、索引和事务边界

- Create `backend/persistent-queue/src/main/resources/db/migration/queue/V<实施时唯一版本>__persistent_queue_message.sql`；实际实施前扫描共享版本空间。
- 表字段固定为 `id BIGSERIAL`、`topic VARCHAR(128)`、`message_key VARCHAR(256)`、`message_value TEXT`、`status VARCHAR(16)`、`created_at`、`succeeded_at`。
- Check：topic/key 非空；status 约束和租约/失败字段由 LP-06 的成功确认迁移补充；Publisher 只写 PENDING。
- 三个局部索引固定为 pending topic、pending topic+key、succeeded cleanup。
- Publisher 插入只能写 PENDING 和空 `succeeded_at`，不接受调用方指定状态或 messageId。
- `@Transactional(propagation=REQUIRED)` 仅包围序列化后 insert；序列化/大小校验在写库前完成。
- 外层业务事务回滚时 queue row 必须一起回滚；独立发布时正常提交。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Modify | `backend/pom.xml` | 聚合 `persistent-queue` |
| Create | `backend/persistent-queue/pom.xml` | 模块依赖与测试 |
| Modify | `backend/mentor-application/pom.xml` | 后续业务发布/消费契约依赖 |
| Modify | `backend/mentor-api/pom.xml` | 运行时资源和 IT 直接依赖 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/model/QueueMessage.java` | 消息模型 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/model/QueueMessageStatus.java` | 状态枚举 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/publisher/QueuePublisher.java` | 发布接口 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/publisher/PostgresQueuePublisher.java` | 序列化、校验、入库 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/repository/QueueMessageRepository.java` | 持久化端口 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/postgres/MyBatisQueueMessageRepository.java` | PostgreSQL 适配 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/postgres/QueueMessageMapper.java` | MyBatis mapper |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/config/PersistentQueueProperties.java` | 配置绑定 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/config/PersistentQueueAutoConfiguration.java` | Bean 装配 |
| Create | `backend/persistent-queue/src/main/resources/mapper/queue/QueueMessageMapper.xml` | SQL |
| Create | `backend/persistent-queue/src/main/resources/db/migration/queue/V<实施时唯一版本>__persistent_queue_message.sql` | 表与索引 |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | max-value-bytes 默认值 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/queue/PersistentQueueStorageIT.java` | PostgreSQL 集成验证 |

## 9. 分阶段实施步骤

1. 建立 Maven 模块和依赖方向，先确保全仓 compile 不产生循环依赖。
2. 定义消息、状态、异常、Publisher/Repository 和配置常量。
3. 实现 Jackson 序列化、UTF-8 大小校验和安全错误映射。
4. 分配唯一迁移版本，创建表、check 和三类局部索引。
5. 实现 Mapper/XML/Repository 与自动配置，复用宿主 `SqlSessionTemplate`。
6. 增加 Publisher 单测、配置绑定测试和 PostgreSQL 事务 IT。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | Maven reactor compile | 模块方向为 queue -> 基础依赖，业务 -> queue |
| 2 | contract tests | 空 topic/key/payload 被确定性拒绝 |
| 3 | `PostgresQueuePublisherTest` | 65,536 bytes 成功，65,537 bytes 失败且不截断 |
| 4 | `PersistentQueueStorageIT` | check 与局部索引存在并生效 |
| 5 | 自动配置测试 | consumer disabled 场景仍存在 Publisher |
| 6 | `TransactionTemplate` IT | 业务写入与消息全回滚或全提交 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- Publisher 单测覆盖：空白 topic/key、null payload、Jackson 序列化异常、ASCII 边界、多字节字符边界、超限一字节。
- 配置测试覆盖默认 65,536 和非法非正值启动失败。
- PostgreSQL IT 验证新消息必为 PENDING、`succeeded_at` 为空、状态/时间 check、三个局部索引、按 ID/topic/key 查询。
- 用外层 `TransactionTemplate` 写一个业务标记行、调用 publish、主动抛异常，断言两者均回滚。
- 独立 publish 断言消息提交；消费配置关闭后同样可提交。
- Flyway 唯一版本测试必须包含 queue 模块迁移资源。

## 12. 日志、指标、隐私和 AI governance 要求

- 日志仅记录 topic、value 字节数、messageId、outcome；不记录 key、value、payload、userId 或异常中的序列化对象。
- 指标：`queue.publish{topic,outcome}`、`queue.publish.value.bytes`；topic 只允许来自业务常量并在注册表中受控。
- 不把 message key、messageId 作为 metric tag。
- 本任务不调用 AI，不新增治理 purpose/source。

## 13. 发布顺序、兼容性和回滚方案

- 先部署迁移和 Publisher，尚无 worker，因而不会消费消息。
- 新模块为增量依赖；消费开关缺省在 `LP-07` 固定为 false。
- 迁移成功后不删除表；应用回滚保留 queue row。
- 若 Publisher 错误率或事务回滚异常，停止接入业务发布方，回滚应用，不执行 down migration。

## 14. 风险与开放项

- 风险：宿主 MyBatis 未扫描 queue mapper。自动配置测试必须从真实 `mentor-api` 上下文验证。
- 风险：Jackson 自引用对象导致序列化失败。Publisher 必须在写库前失败并给出稳定异常类型。
- 风险：模块测试与 `make backend-it` 脱节。PostgreSQL IT 固定落在 `mentor-api`，无需修改现有 Failsafe 入口。
- 明确不讨论至少一次、重试、死信或多节点消费。

## 15. 可复制执行的验证命令

```bash
find backend -path '*/src/main/resources/db/migration/*' -type f | sort -V

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl persistent-queue -am test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=FlywayMigrationResourceTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=PersistentQueueStorageIT verify

git diff --check
```

## 16. 最终验收 checklist

- [ ] `persistent-queue` 模块无画像或 Review 依赖。
- [ ] Publisher 在 consumer disabled 时仍可用。
- [ ] JSON 按共享 Jackson 序列化并按 UTF-8 字节限流。
- [ ] 超限消息失败且不截断、不写库。
- [ ] 新消息只以 PENDING 入库。
- [ ] 三类局部索引和 check 已由 PostgreSQL IT 验证。
- [ ] 外层业务事务可原子包含 queue publish。
- [ ] 未实现消费者、线程、重试、死信或广播。
