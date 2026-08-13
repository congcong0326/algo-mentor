# 持久化队列成功确认与画像更新可靠性设计

## 背景

非用户自述的学习者记忆更新由 Code Review 等系统观察驱动，并通过 PostgreSQL 持久化队列异步执行。旧实现采用最多一次语义：消息在回调执行前即被提交为 `SUCCEEDED`；回调失败仅记录日志。这会造成画像更新失败后消息被静默跳过。

用户自述更新仍保持同步 Agent Tool 调用，本文只约束持久化队列驱动的异步系统观察更新。

## 目标与非目标

目标：

- 业务回调成功或返回可接受的 `NO_CHANGE` 后才确认消息成功。
- 回调异常时持久化重试状态，不得静默丢失批次。
- 进程在业务执行中退出时，经租约到期后可重新投递。
- 持续失败达到上限时可观测、告警并停止对应 topic worker，保留失败消息供人工处置。
- 保持批量的 `(topic, key)` 严格满批与 key 公平轮转语义。

非目标：

- 不在长事务内执行 LLM 或画像更新。
- 不自动重放 `FAILED` 消息；重放必须由后续运维入口显式实现。
- 不改变用户自述同步更新的失败反馈语义。

## 状态机

```text
PENDING --领取--> PROCESSING --业务成功并确认--> SUCCEEDED
   ^                  |
   |                  +--回调异常且未到上限--> PENDING（available_at 延后）
   |                  |
   +--租约到期---------+
                      +--回调异常且达到上限--> FAILED + 告警 + 停止 topic worker
```

- `PROCESSING` 必须持有随机 `lease_token` 与 `lease_expires_at`；确认或失败回写均以 token 限定，避免过期 worker 覆盖新 owner 的结果。
- `delivery_attempt` 在成功领取时递增。默认最多 5 次，默认退避为 30 秒起、上限 15 分钟。
- `FAILED` 不参与派发和成功记录清理，保留 `last_error_type`、次数与失败时间用于排查。

## 消费与画像幂等

每次领取、确认和失败回写均使用短事务；LLM 调用及 Claim 原子写入始终在事务外执行。

“业务成功、确认前”发生进程故障时，批次可能被再次投递，因此语义为至少一次。Code Review 批次沿用稳定的业务批次幂等键；若上次画像 run 已失败，重开同一业务 run 并保留触发 Review。每次队列重投会使用新的 Agent invocation 幂等键，避免 Agent 运行记录与此前已失败的调用冲突。

`UPDATED` 与 `NO_CHANGE` 都属于消费成功，可确认消息；`FAILED`、无效 payload、归属校验失败等均通过抛出异常进入重试路径。

## 告警、停止与恢复

达到 `max-attempts` 时：

1. 全批消息转为 `FAILED`；
2. 记录 `learner.profile.queue.terminal_failure{topic}`；
3. 通过 `QueueAlertNotifier` 输出不含 key/value 的高优先级告警；默认实现写 ERROR 日志，部署可替换为告警平台实现；
4. 当前 topic worker 转为 `FAILED_STOPPED` 并停止，不再继续消费该 topic。

恢复流程应先定位失败类型与模型/数据库依赖，再由人工确认后提供专用运维动作将目标批次置回 `PENDING` 并重启或滚动重启消费者。禁止通过修改历史 `SUCCEEDED` 记录实现回放。

## 配置

队列基础设施的部署边界：本地开发直接安装运行 Redis；测试环境和生产环境的 Redis（包括未来的 Redis Streams relay/consumer transport）由外部基础设施直接提供，不打进应用容器。当前 PostgreSQL 持久队列仍以数据库为可靠事实来源；Redis Streams 接入必须保留 outbox/relay、至少一次投递、成功后 ACK、超时回收和 DLQ 语义，不能用缓存实例的淘汰策略承载队列消息。详见 `docs/deployment-topology-and-infrastructure-boundary.md`。

配置位于 `algo-mentor.queue.consumer`：

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `lease-duration` | `10m` | 单批处理持有的最大租约时间，应覆盖 Agent 最大执行时长。 |
| `max-attempts` | `5` | 达到次数后转 `FAILED` 并停止 topic。 |
| `retry-initial-backoff` | `30s` | 第一次失败后的等待时间。 |
| `retry-max-backoff` | `15m` | 指数退避上限。 |

发布前应确保 `lease-duration` 大于画像 Agent 的最大执行时长，并由监控接入 `terminal_failure`、`dequeue{outcome=CALLBACK_RETRY_SCHEDULED|CALLBACK_TERMINAL_FAILURE}`、Pending 积压和最老 Pending 年龄。
