# LP-06：队列消费者注册与单条/满批派发实施计划

> 波次：B
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-05

## 1. 任务目标与完成标准

在 `persistent-queue` 模块实现消费者 Bean 注册、单条和严格满批派发、按 key 公平轮转，以及明确的最多一次出队语义。

完成后：重复 topic 在启动期失败；同 topic 不可同时注册单条/批量消费者；批量只按 `(topic,key)` 派发恰好 `batchSize` 条；事务提交后才调用业务回调；回调异常不回退 SUCCEEDED，也不阻断后续 key。

## 2. 当前实现基线

- LP-05 将提供 `QueueMessage`、PENDING/SUCCEEDED、Repository、Publisher 和 `queue_message` 表。
- 当前没有消费者接口、注册表、派发器、key 查询或出队事务。
- 第一版部署约束是单消费节点、每 topic 单线程，但 worker 生命周期属于 LP-07。
- PostgreSQL 表已有 pending topic 和 pending topic+key 局部索引，可支持单条/满批查询。

## 3. 已确认的代码冲突或缺口

1. 不能把“不满批”交给业务消费者决定；队列必须在 SQL/dispatcher 层保持 PENDING。
2. 若回调在出队事务内执行，LLM 等长任务会持锁并把业务失败变成队列重试语义。
3. 若先回调后标记成功，会隐式变成至少一次，违背上游固定契约。
4. 仅按单个积压 key 循环会饿死其他 key。
5. 消费开关关闭时也必须校验重复 topic，避免启用时才暴露装配错误。

## 4. 范围、非目标和依赖

范围：消费者接口、批量策略、注册表、topic 唯一路由、eligible key 查询、短事务出队、事务后回调、公平轮转和派发指标。

非目标：不启动 worker、不做轮询等待、不清理数据、不实现画像消费者、重试、死信、租约、自动选主或人工回放。

依赖：LP-05；LP-07 和 LP-09 依赖本任务。

## 5. 关键技术决策

- `QueueConsumer` 和 `BatchQueueConsumer` 为互斥接口；一个 Bean 可注册多个 topic。
- `BatchConsumerPolicy.batchSize` 必须大于 1；无需聚合的场景使用单条 consumer。
- 启动期构建不可变 `QueueConsumerRegistry`，同 topic 多 Bean或单/批冲突直接抛装配异常。
- dispatcher 每轮先查询所有 eligible key，按各 key 最早 PENDING id 升序。
- 每轮每 key 最多派发一条/一批；完成整轮后再重新查询，避免热点 key 垄断。
- 出队使用 `TransactionTemplate`：选择 ID -> 更新为 SUCCEEDED/succeeded_at -> 提交；提交成功后事务外调用 callback。
- callback 异常只计数和记录，不改变状态、不抛出终止 worker 的异常。

## 6. 领域模型、接口、常量和配置契约

```java
interface QueueConsumer {
  Set<String> topics();
  void consume(QueueMessage message);
}

interface BatchQueueConsumer {
  Set<String> topics();
  BatchConsumerPolicy policy();
  void consume(List<QueueMessage> messages);
}
```

- `BatchConsumerPolicy` 第一版只有 `batchSize`，不加入等待时间、最大延迟或 retry。
- `QueueConsumerRegistration` 固定记录 consumerName、topic、mode、batchSize 和 callback。
- `QueueDispatchOutcome`：`NO_ELIGIBLE_KEY`、`DISPATCHED`、`DEQUEUE_FAILED`、`CALLBACK_FAILED`。
- consumerName 仅用于日志/指标，来源为受控 Bean 名，不存入消息表。
- 本任务无新配置 key；LP-07 提供 enabled/poll/cleanup 配置。

## 7. 数据库迁移、约束、索引和事务边界

- 预期无新迁移，复用 LP-05 表和索引；若 SQL 计划证明索引不足，另加唯一版本迁移，不修改已执行脚本。
- 单条 eligible：topic 下存在 PENDING 即可；按最早 id 选择一条。
- 批量 eligible：按 topic/key 分组 `HAVING count(*) >= batchSize`，返回每组 `MIN(id)` 用于轮转。
- 出队只更新本次明确选定的 messageId 列表，禁止按 topic/key 无界更新。
- 批量必须重新查询并选取恰好 batchSize 条，按 id 升序；新并发消息不加入已选批次。
- 更新行数不等于期望数量时事务回滚并返回 DEQUEUE_FAILED，callback 不执行。
- 事务提交后数据库查询必须已经能看到 SUCCEEDED，再调用业务消费者。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/consumer/QueueConsumer.java` | 单条消费者 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/consumer/BatchQueueConsumer.java` | 批量消费者 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/consumer/BatchConsumerPolicy.java` | 满批阈值 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/consumer/QueueConsumerRegistry.java` | 唯一 topic 注册 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/dispatch/QueueDispatcher.java` | 一轮派发 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/dispatch/QueueDequeueService.java` | 短事务出队 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/dispatch/QueueDispatchOutcome.java` | 结果枚举 |
| Modify（LP-05 Create 后） | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/repository/QueueMessageRepository.java` | eligible key、选取、更新；当前仓库尚不存在 |
| Modify（LP-05 Create 后） | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/postgres/QueueMessageMapper.java` | 派发 SQL；当前仓库尚不存在 |
| Modify（LP-05 Create 后） | `backend/persistent-queue/src/main/resources/mapper/queue/QueueMessageMapper.xml` | 满批/轮转/出队；当前仓库尚不存在 |
| Modify（LP-05 Create 后） | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/config/PersistentQueueAutoConfiguration.java` | 注册表与 dispatcher Bean；当前仓库尚不存在 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/queue/PersistentQueueDispatchIT.java` | PostgreSQL 派发验证 |

## 9. 分阶段实施步骤

1. 定义单条/批量接口、策略校验和注册冲突异常。
2. 实现启动期 registry，覆盖多 topic Bean 和 enabled=false 场景。
3. 扩展 Mapper/Repository，查询 eligible key 和有序消息 ID。
4. 实现 `QueueDequeueService` 的短事务 PENDING -> SUCCEEDED。
5. 实现 dispatcher 一轮 key 轮转和事务后 callback 隔离。
6. 增加注册单测、公平性/异常单测和 PostgreSQL 满批 IT。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | consumer contract tests | batchSize 非法立即失败 |
| 2 | registry tests | 重复 topic、单批冲突使上下文启动失败 |
| 3 | repository/Mapper tests | eligible key 以最早 id 排序 |
| 4 | dequeue IT | 提交前无 callback，提交后全为 SUCCEEDED |
| 5 | dispatcher tests | 每轮每 key 一批，callback 异常不中断 |
| 6 | `PersistentQueueDispatchIT` | 4 条不派发、5 条恰好一批 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 注册表：重复 topic、单条/批量冲突、空 topic、重复 topic 自身、非法 batchSize、一个 Bean 多 topic。
- dispatcher：同 topic/key 有序，不同 key 不混批；一轮每 key 只取一批；热点 key 后其他 key 仍被处理。
- PostgreSQL IT：4 条保持 PENDING；第 5 条后恰好 5 条 SUCCEEDED；第 6 条留 PENDING。
- callback 内查询数据库，断言收到前状态已提交为 SUCCEEDED。
- callback 抛异常后状态不回退，dispatcher 继续下一 key。
- 注入更新行数不匹配或事务异常，断言 callback 从未执行，消息仍 PENDING。
- 单条消费者每次只取最早一条，并参与同样 key 轮转。

## 12. 日志、指标、隐私和 AI governance 要求

- 日志记录 topic、consumerName、mode、batchSize、消息数量和 outcome；不记录 key/value/userId。
- 指标：`queue.dequeue{topic,mode,outcome}`、`queue.callback{topic,outcome}`、出队/回调耗时。
- `CALLBACK_FAILED` 与 `DEQUEUE_FAILED` 必须分开，避免把业务失败误判为数据库失败。
- 本任务不调用 AI；画像消费者的治理在 LP-09。

## 13. 发布顺序、兼容性和回滚方案

- 可随 LP-05 基础设施部署，但 LP-07 consumer enabled 默认 false，因而不会自动运行。
- 注册冲突无论 enabled 值都阻止启动，属于主动配置门禁。
- 回滚应用时已 SUCCEEDED 消息保持状态，不允许重置为 PENDING。
- 若派发顺序或状态异常，保持 consumer disabled 并回滚代码；Publisher 不受影响。

## 14. 风险与开放项

- 最多一次语义存在“提交后、回调前”丢失窗口，这是已接受契约，不作为 bug 自动重试。
- 单消费节点是外部运维保证；本任务不通过 SQL 锁支持多节点抢占。
- eligible key 查询需用真实数据量检查执行计划，但不提前增加设计外索引。
- consumer callback 必须无共享可变状态，尤其是一个 Bean 注册多个 topic 时会被并发调用。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl persistent-queue -am -Dtest='*QueueConsumer*Test,*QueueDispatch*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=PersistentQueueDispatchIT verify

make backend-test
git diff --check
```

## 16. 最终验收 checklist

- [ ] topic 到消费者的映射全局唯一。
- [ ] 同 topic 不可同时注册单条和批量 consumer。
- [ ] 满批严格限定同 topic、同 key、恰好 batchSize。
- [ ] 不满批消息保持 PENDING。
- [ ] 出队事务提交后才执行 callback。
- [ ] callback 失败不回退 SUCCEEDED、不终止后续派发。
- [ ] 每轮每 key 只派发一批且无长期饥饿。
- [ ] 未实现 worker、清理、重试或画像消费者。
