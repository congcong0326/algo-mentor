# AMR-07：v2 队列、满批消费与写入闭环

> 波次：C
>
> 状态：DONE
>
> 直接依赖：AMR-06
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

把正式 Review 提交、持久化队列和后台消费者切换到 `learner-memory.code-review.v2`，接通 AMR-06 更新服务，并删除所有旧 topic 队列数据。

完成后，每条新正式 Review 与受信标签、v2 message 同事务提交；同一用户严格满 5 条后形成一批，队列成功确认后更新 claim/evidence。

## 2. 必须读取

- `CURRENT.md`、`AMR-06` 完成备注。
- `CONTRACTS.md` 第 1、5、6、7、12 节。
- `PracticeCodeReviewCommitService.java` 及测试。
- `CodeReviewProfileQueueContracts.java`、Event、BatchConsumer、ConsumerConstants、Metrics 及测试。
- `QueuePublisher.java`、`BatchQueueConsumer.java`、`QueueDispatcher.java` 的成功确认和至少一次语义。
- `V35__persistent_queue_message.sql` 和 queue Mapper 的 topic 查询。
- `AgentConversationApiAutoConfiguration.java` 的 consumer/properties Bean。
- `LearnerProfileEndToEndIT.java`、`LearnerProfileFailureDegradationIT.java` 的相关路径。

## 3. 队列契约

- topic 固定为 `learner-memory.code-review.v2`。
- key 继续为 canonical 十进制 user ID。
- payload v2 仍只有 `reviewId`，严格未知字段拒绝。
- batch size 固定 5，不通过环境变量扩大或缩小。
- consumer callback 成功后才确认 `SUCCEEDED`；失败由队列有限重试，终态 `FAILED` 告警并停止 topic，FAILED 不自动重放。

使用实施时下一个全局唯一迁移版本执行：

```sql
DELETE FROM queue_message
WHERE topic = 'learner-profile.code-review.v1';
```

删除 PENDING 和 SUCCEEDED，不转换为 v2。

## 4. 提交与消费

- `PracticeCodeReviewCommitService` 仍是正式 Review 唯一关键提交入口。
- Review、`practice_code_review_tag` 和 v2 queue message 必须加入同一 REQUIRED 事务。
- 幂等命中旧 Review 时不得重复发布 message。
- consumer 先校验恰好 5 条、同 topic、同 key、review ID 唯一、ownership 完整，再调用 AMR-06 更新服务。
- 横向 10 题窗口和历史工具不在 consumer 中重复实现。
- callback 的低敏结果记录 update run ID、window problem count、operation count，不记录正文。

## 5. 配置与装配

- 新配置前缀使用 `algo-mentor.learner-memory.code-review-consumer`。
- 基础配置默认关闭；local 可以开启，但只有 Runtime、Definition、Repository、queue worker 全部存在时才创建 consumer。
- 删除运行时代码对 v1 topic 的引用；v1 字符串只允许暂存于清理迁移和历史文档，最终由 `AMR-14` 源码门禁确认。

## 6. 重点测试

- 新 Review、标签和 v2 message 三者原子提交；任一步失败全部回滚。
- 幂等 Review 不重复发布。
- 4 条不消费，第 5 条严格触发；一题五版仍是五条消息但横向窗口只计一题。
- 跨 user、重复 review、缺失事实、非法 payload 在 Agent 前失败。
- callback/AI/apply 失败后 queue 回到 PENDING 或转为 FAILED，update run 为 FAILED，无部分 claim。
- 旧 topic 迁移后 PENDING/SUCCEEDED 均为 0，新 topic 不受影响。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl persistent-queue,mentor-application -am \
  -Dtest='Queue*Test,PersistentQueue*Test,CodeReviewProfileBatchConsumerTest,PracticeCodeReviewCommitServiceTest' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dit.test='LearnerProfileEndToEndIT,LearnerProfileFailureDegradationIT,*LearnerMemory*QueueIT' verify

git diff --check
```

## 8. 非目标与停止条件

- 不改变 persistent-queue 通用至少一次成功确认模型；不增加 DLQ 或自动补偿接口。
- Review、标签、queue 任一无法证明同事务，或 v1 消息仍可被生产消费时不得开始最终联调。

## 9. 上下文交接

记录实际清理迁移版本、v2 topic、配置前缀、原子提交测试、重试和终态失败语义。不要记录 queue payload 样本之外的业务内容。

## 10. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 正式 Review 队列 topic 切换为 `learner-memory.code-review.v2`，保留 canonical 十进制 user ID key 与只含 `reviewId` 的严格 payload。
- 新增 `V47__delete_legacy_code_review_queue_messages.sql`，删除旧 `learner-profile.code-review.v1` 的 PENDING 与 SUCCEEDED 消息，不转换为 v2。
- 后台消费者在调用更新服务前校验五条同 topic、同用户、唯一 review ID、完整归属和 review facts；失败进入 persistent queue 的重试或终态 FAILED 语义。
- 更新服务结果携带低敏 `updateRunId`，消费者只记录 run ID、窗口题目数和 operation 数；配置前缀调整为 `algo-mentor.learner-memory.code-review-consumer`，并仅在 runtime、Definition、持久化端口及 queue worker 完整可用时创建消费者。
- 自动配置测试使用最小真实 final 服务 fixture 验证装配，避免以 Mockito mock final 编排服务。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl persistent-queue,mentor-application -am -Dtest='Queue*Test,PersistentQueue*Test,CodeReviewProfileBatchConsumerTest,PracticeCodeReviewCommitServiceTest' test` 通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dit.test='LearnerProfileEndToEndIT,LearnerProfileFailureDegradationIT,*LearnerMemory*QueueIT' verify` 通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest='AgentConversationApiAutoConfigurationTest,FlywayMigrationResourceTest' test` 通过。
- `git diff --check` 通过。

偏离计划：

- 无。

遗留事项：

- 无；旧 topic 的剩余历史文档引用由 AMR-14 源码门禁统一清理。

下一任务：`AMR-08`
