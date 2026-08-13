# AI 学习者画像第一版发布运行手册

## 适用范围

本手册覆盖 LP-01 至 LP-13 的第一版画像闭环：正式 Code Review、受信标签、持久化队列、异步系统观察、用户声明更新和 PRACTICE_CHAT 召回。第一版支持队列至少一次投递、租约和有限退避重试；不包含死信、人工回放、自动选主、第二观察源、画像管理 UI 或向量召回。

队列 topic 固定为 `learner-profile.code-review.v1`，key 为用户 ID，payload v1 只含 `reviewId`。消息进入 `PROCESSING` 后执行 callback，只有 callback 成功或返回 `NO_CHANGE` 才确认 `SUCCEEDED`；达到最大次数的批次进入 `FAILED`，触发告警并停止对应 topic worker。

## 发布前门禁

1. 确认全仓 Flyway 版本唯一，当前新增版本为 V34、V35、V36，且 V33 可升级并通过 `validate`。
2. 确认只有一个应用节点设置 `QUEUE_CONSUMER_ENABLED=true`。不得在第二个节点并行开启；切换时必须先停止旧节点，并确认当前 callback 已结束。
3. 确认 `PersistentAgentTraceObserver` 的最终 request snapshot 可能包含画像正文。画像召回只能在安全负责人明确接受既有 30 天保留、管理员访问控制和审计后开启；未确认时 `LEARNER_PROFILE_PRACTICE_RECALL_ENABLED` 必须保持 `false`。
4. 确认 AI governance 能查询 `LEARNING_CHAT` 的 `LEARNER_PROFILE_DECLARED_UPDATE` 与 `LEARNER_PROFILE_CODE_REVIEW_BATCH` source、实际 Token、当前价格成本及 provider timeout。
5. 确认日志、指标和排障查询不输出代码、Review Markdown、queue value、画像正文、Authorization、cookie 或密钥。

## 配置

所有开关安全默认关闭。下表是 `application.yml`、`.env.example` 和部署环境使用的唯一映射。

| 配置 key | 默认值 | 环境变量 |
| --- | --- | --- |
| `algo-mentor.queue.message.max-value-bytes` | `65536` | `QUEUE_MESSAGE_MAX_VALUE_BYTES` |
| `algo-mentor.queue.consumer.enabled` | `false` | `QUEUE_CONSUMER_ENABLED` |
| `algo-mentor.queue.consumer.poll-interval` | `10s` | `QUEUE_CONSUMER_POLL_INTERVAL` |
| `algo-mentor.queue.consumer.shutdown-timeout` | `30s` | `QUEUE_CONSUMER_SHUTDOWN_TIMEOUT` |
| `algo-mentor.queue.consumer.lease-duration` | `10m` | `QUEUE_CONSUMER_LEASE_DURATION` |
| `algo-mentor.queue.consumer.max-attempts` | `5` | `QUEUE_CONSUMER_MAX_ATTEMPTS` |
| `algo-mentor.queue.consumer.retry-initial-backoff` | `30s` | `QUEUE_CONSUMER_RETRY_INITIAL_BACKOFF` |
| `algo-mentor.queue.consumer.retry-max-backoff` | `15m` | `QUEUE_CONSUMER_RETRY_MAX_BACKOFF` |
| `algo-mentor.queue.cleanup.succeeded-retention` | `7d` | `QUEUE_CLEANUP_SUCCEEDED_RETENTION` |
| `algo-mentor.queue.cleanup.fixed-delay` | `1h` | `QUEUE_CLEANUP_FIXED_DELAY` |
| `algo-mentor.queue.cleanup.batch-size` | `1000` | `QUEUE_CLEANUP_BATCH_SIZE` |
| `algo-mentor.learner-profile.content.max-chars` | `4000` | `LEARNER_PROFILE_CONTENT_MAX_CHARS` |
| `algo-mentor.learner-profile.code-review-consumer.enabled` | `false` | `LEARNER_PROFILE_REVIEW_CONSUMER_ENABLED` |
| `algo-mentor.learner-profile.code-review-consumer.max-stale-retries` | `1` | `LEARNER_PROFILE_CODE_REVIEW_CONSUMER_MAX_STALE_RETRIES` |
| `algo-mentor.learner-profile.declared-update.enabled` | `false` | `LEARNER_PROFILE_DECLARED_UPDATE_ENABLED` |
| `algo-mentor.learner-profile.declared-update.max-stale-retries` | `1` | `LEARNER_PROFILE_DECLARED_UPDATE_MAX_STALE_RETRIES` |
| `algo-mentor.learner-profile.declared-update.result-summary-max-chars` | `300` | `LEARNER_PROFILE_DECLARED_UPDATE_RESULT_SUMMARY_MAX_CHARS` |
| `algo-mentor.learner-profile.recall.practice-chat.enabled` | `false` | `LEARNER_PROFILE_PRACTICE_RECALL_ENABLED` |
| `algo-mentor.learner-profile.recall.practice-chat.max-token-budget` | `800` | `LEARNER_PROFILE_PROMPT_TOKEN_BUDGET` |
| `algo-mentor.practice-chat.prompt.total-token-budget` | `8000` | `PRACTICE_CHAT_PROMPT_TOKEN_BUDGET` |

`batchSize=5`、10 题窗口、topic、payload 和用户 key 是代码契约，禁止通过环境变量调整。

## 运行时发布顺序

1. 发布数据与基础设施：部署 V34 `learner_profile_entry`、V35 `queue_message`、V36 `practice_code_review_tag`，保持所有开关关闭。
2. 发布正式 Review 生产者：确认一次正式 Review 同事务写入主表、合法标签和一条 `PENDING` queue message；消费者仍关闭。
3. 启用异步观察：仅在唯一节点同时设置 `QUEUE_CONSUMER_ENABLED=true` 和 `LEARNER_PROFILE_REVIEW_CONSUMER_ENABLED=true`。先用单个受控用户积压五条正式 Review，确认一次出队、一次模型调用和合法 ACTIVE 版本链。
4. 灰度用户可见能力：先启用 `LEARNER_PROFILE_DECLARED_UPDATE_ENABLED`，在隐私门禁通过后才启用 `LEARNER_PROFILE_PRACTICE_RECALL_ENABLED`。召回仅限 PRACTICE_CHAT；正式 Code Review 永不读取或注入画像。

每一步都先观察再扩大范围，不能跳过前置步骤。关闭全局 worker 时 Publisher 仍可写入 PENDING 消息。

## 观测与止损

发布面板至少观察：

- `learner.profile.queue.pending`、`learner.profile.queue.oldest_pending_age`、`learner.profile.queue.dequeue`、worker failure 和 cleanup 指标。
- `learner.profile.review_consumer{outcome}`、`window_problems`、`invalid_output`、`stale_retry`、`version_updates`、`duration`。
- `learner.profile.recall`、`learner.profile.prompt.trimmed`、AI 调用级 Token 和成本。

1 至 4 条同 key 的 PENDING 是正常的未满批状态。告警应基于已满足 `batchSize=5` 的 eligible key 最老年龄，不能仅以全局最老 PENDING 判断异常。

在任意连续 15 分钟窗口内，若画像 consumer callback 失败率或非法输出率持续超过 5%，立即关闭 `LEARNER_PROFILE_REVIEW_CONSUMER_ENABLED`，保留 PENDING 供后续新批次继续处理。若画像 Prompt 裁剪率持续超过 20%，暂停扩大 `LEARNER_PROFILE_PRACTICE_RECALL_ENABLED` 灰度并检查内容长度和预算；不自动扩大 800/8000 预算。AI 成本超出治理预算时立即关闭对应 feature switch。

## 故障处理与回滚

回滚顺序固定为：关闭 recall，关闭 declared update，关闭 Review 画像 consumer，关闭全局 queue worker，最后回滚应用版本。不得删除 `learner_profile_entry`、`practice_code_review_tag` 或 `queue_message`，也不得把 `SUCCEEDED` 重置为 `PENDING`。

- PENDING 在 worker 重启或重新开启后可继续达到满批并处理。
- callback 失败的消息回到 `PENDING` 并按指数退避重试；达到 `max-attempts` 后为 `FAILED`，告警并停止 topic。`FAILED` 不自动重放，恢复需显式运维动作。
- 节点切换先停止旧节点，等待最多 `QUEUE_CONSUMER_SHUTDOWN_TIMEOUT`；超时后仍不改变既有消息状态，再启动新节点。
- 画像异常或回答质量下降时，优先关闭 recall；这不影响正式 Review 的独立评分路径。

## 验证命令

```bash
make backend-test
make backend-it
npm --cache ./.npm --prefix frontend test -- PracticeChatWorkbench.test.tsx
make frontend-build
make build
git diff --check
```
