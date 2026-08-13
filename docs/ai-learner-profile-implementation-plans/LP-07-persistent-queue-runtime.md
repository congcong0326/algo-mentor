# LP-07：队列节点运行、清理与可观测性实施计划

> 波次：C
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-06

## 1. 任务目标与完成标准

把 queue dispatcher 装配为配置控制的 topic worker，补齐优雅停止、SUCCEEDED 清理和运行指标。

完成后：`enabled=false` 不创建 worker/清理任务但 Publisher 可用；每 topic 单线程、不同 topic 可并行；有消息时连续轮转、空轮次后等待；异常不永久退出；清理每小时最多删除 1,000 条超过 7 天的 SUCCEEDED，绝不触碰 PENDING。

## 2. 当前实现基线

- LP-06 提供注册表和“一轮派发”能力，但不会启动线程。
- 当前项目没有队列 scheduling、worker manager 或消费节点选主。
- 仓库当前未统一启用 `@EnableScheduling`；直接依赖全局 scheduler 会改变其他模块运行行为。
- Micrometer/Actuator 已在 `mentor-api` 可用。
- 第一版明确由部署配置人工保证单消费节点。

## 3. 已确认的代码冲突或缺口

1. 不能通过普通 `@Scheduled` 为动态 topic 注册独立线程且保证每 topic 串行。
2. 空队列若无等待会高频轮询；有积压时若每轮都等待会降低吞吐。
3. shutdown 若直接 interrupt，可能在 callback 中间退出；无限等待又会阻塞停机。
4. 清理不能单次循环清空历史积压，否则形成长事务和 IO 峰值。
5. 多节点同时 enabled 会破坏 LP-06 的单节点假设，必须用配置和发布门禁控制。

## 4. 范围、非目标和依赖

范围：运行配置、每 topic worker、`SmartLifecycle`、有界 executor、等待/唤醒、优雅停止、清理调度、pending/age/异常/清理指标和运维说明。

非目标：不做自动选主、分布式锁、故障转移、租约、PROCESSING、重试、死信或人工回放 API。

依赖：LP-06；LP-09 需要本任务可运行环境，LP-13 负责生产发布门禁。

## 5. 关键技术决策

- `PersistentQueueWorkerManager` 实现 `SmartLifecycle`，不启用全局 `@EnableScheduling`。
- enabled 时为 registry 中每个 topic 创建一个命名单线程 executor；同 topic 串行，不同 topic 并行。
- worker 调用 `dispatcher.dispatchRound(topic)`；只要本轮派发过消息就立即进入下一轮，完全空轮次才等待 poll interval。
- worker 捕获所有非致命异常，记录后按 poll interval 退避，线程继续存活。
- stop 先设置停止标记，禁止取新批次，再等待当前 callback 最多 30 秒；超时记录并终止 executor，不改变消息状态。
- cleanup 使用独立单线程 scheduler，仅 enabled 节点运行；每次只执行一个短事务和一个 batch。
- 默认 enabled=false，生产唯一节点显式开启。

## 6. 领域模型、接口、常量和配置契约

```yaml
algo-mentor:
  queue:
    consumer:
      enabled: false
      poll-interval: 10s
      shutdown-timeout: 30s
    cleanup:
      succeeded-retention: 7d
      fixed-delay: 1h
      batch-size: 1000
```

- 所有 key 放入 `PersistentQueuePropertyKeys` 或 properties 类型，不散落字符串。
- `QueueWorkerState`：`STARTING/RUNNING/STOPPING/STOPPED/FAILED_RETRYING`，仅用于内部观测。
- `QueueCleanupResult(deletedCount,duration)`；清理顺序固定 `succeeded_at,id`。
- 非正 duration/batch size 使应用启动失败。

## 7. 数据库迁移、约束、索引和事务边界

- 预期无新迁移，复用 `idx_queue_message_succeeded_cleanup`。
- Repository 增加 pending count、oldest pending created_at 和 cleanup 方法。
- cleanup SQL 先选最多 batchSize 个过期 SUCCEEDED id，再删除这些 id；单事务，不循环。
- PENDING、`succeeded_at IS NULL`、未过 retention 的 SUCCEEDED 都不得删除。
- gauges 查询需有节流/缓存，不能每次 metrics scrape 对每条消息做扫描；按 topic 聚合且 topic 集合来自 registry。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/runtime/PersistentQueueWorkerManager.java` | 生命周期和 topic executor |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/runtime/QueueTopicWorker.java` | 单 topic 循环 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/runtime/QueueWorkerState.java` | 状态枚举 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/runtime/QueueCleanupService.java` | 清理事务 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/runtime/QueueCleanupScheduler.java` | fixed-delay 调度 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/metrics/QueueMetrics.java` | 指标端口 |
| Create | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/metrics/MicrometerQueueMetrics.java` | Micrometer 适配 |
| Modify（LP-05/06 Create 后） | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/config/PersistentQueueProperties.java` | consumer/cleanup 配置；当前仓库尚不存在 |
| Modify（LP-05/06 Create 后） | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/config/PersistentQueueAutoConfiguration.java` | enabled 条件装配；当前仓库尚不存在 |
| Modify（LP-05/06 Create 后） | `backend/persistent-queue/src/main/java/org/congcong/algomentor/queue/repository/QueueMessageRepository.java` | 清理和积压查询；当前仓库尚不存在 |
| Modify（LP-05/06 Create 后） | `backend/persistent-queue/src/main/resources/mapper/queue/QueueMessageMapper.xml` | cleanup/pending SQL；当前仓库尚不存在 |
| Modify | `backend/mentor-api/src/main/resources/application.yml` | 安全默认值 |
| Modify | `backend/mentor-api/src/main/resources/application-local.yml` | 本地显式消费选择 |
| Modify | `.env.example` | 生产环境变量示例 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/queue/PersistentQueueCleanupIT.java` | 清理 IT |

## 9. 分阶段实施步骤

1. 扩展配置绑定和校验，默认 enabled=false。
2. 实现 topic worker 循环、命名线程和派发/等待规则。
3. 实现 `SmartLifecycle` 启停、当前 callback 有界等待。
4. 扩展 Repository 并实现单批 cleanup/scheduler。
5. 接入 worker、积压、最老年龄、异常、清理和耗时指标。
6. 增加配置、并发、停机、异常恢复和 PostgreSQL 清理测试。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | properties binding tests | 默认 10s/30s/7d/1h/1000 且 disabled |
| 2 | latch-based worker tests | 同 topic 串行、不同 topic 并行 |
| 3 | lifecycle tests | stop 后不取新批，当前 callback 有界完成 |
| 4 | `PersistentQueueCleanupIT` | 仅删除过期 SUCCEEDED，最多 1000 |
| 5 | metrics tests | 低基数 tags，pending/age 可观测 |
| 6 | context tests | disabled 无 worker/cleanup、Publisher 仍存在 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 配置测试覆盖默认值、环境覆盖、零/负 duration、batchSize 非法。
- worker 用 latch/原子计数器验证：同 topic 最大并发 1、不同 topic 可重叠、callback 异常后继续、空轮次才等待。
- stop 测试：停止标记后不再 dequeue；正在运行 callback 在 timeout 内可完成；超时会记录但不重置 SUCCEEDED。
- cleanup IT 插入过期/近期 SUCCEEDED 和 PENDING，断言只删过期成功消息。
- 插入 1,005 条过期成功消息，单次只删前 1,000，按 `succeeded_at,id` 确定。
- disabled Spring context 断言 registry 可校验、Publisher 存在、worker manager 不启动、cleanup scheduler 不运行。

## 12. 日志、指标、隐私和 AI governance 要求

- 指标至少包含：worker active、pending count、oldest pending age、dequeue count、callback failure、cleanup count/duration。
- tag 仅 topic、outcome、workerState；不得使用 key/userId/reviewId/messageId。
- 日志记录 topic、线程状态、数量、等待/关闭耗时，不记录 message value/key。
- 本任务不调用 AI，但其指标将作为 LP-13 发布门禁输入。

## 13. 发布顺序、兼容性和回滚方案

- 先部署 LP-05/06/07 代码，所有环境默认 consumer disabled。
- 发布画像生产者后仍保持 disabled，确认消息稳定入库，再在唯一节点显式开启。
- 切换节点必须先停旧节点、确认 worker 停止和当前 callback 结束，再启新节点。
- 出现积压、异常循环或资源问题时先关闭 enabled；PENDING 保留，Publisher 继续工作。
- 已确认 SUCCEEDED 消息不因关闭/重启回放；PROCESSING 租约到期消息可重新领取，FAILED 不自动重放。

## 14. 风险与开放项

- 人工单节点配置错误会造成重复选取风险；LP-13 必须把部署检查列为硬门禁。
- callback 超过 shutdown timeout 时应用退出可能留下 PROCESSING 租约；租约到期后由后续 worker 重新领取，业务必须具备幂等性。
- topic 数量直接决定线程数；第一版 topic 数量小，新增 topic 需评估资源。
- 不在本任务加入自动选主或分布式锁来掩盖运维约束。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl persistent-queue -am -Dtest='*Queue*Worker*Test,*Queue*Properties*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=PersistentQueueCleanupIT verify

make backend-test
git diff --check
```

## 16. 最终验收 checklist

- [ ] enabled 默认 false 且不影响 Publisher。
- [ ] 每 topic 单线程，不同 topic 可并行。
- [ ] 有派发时连续轮转，空轮次后才等待。
- [ ] worker 异常后可继续运行且无高频空轮询。
- [ ] 停机停止取新批并有界等待 callback。
- [ ] 清理只删过期 SUCCEEDED、每次最多 1000。
- [ ] 积压、最老年龄、异常和清理指标可观测。
- [ ] 未实现自动选主、重试、租约或回放。
