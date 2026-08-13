# AI 记忆系统发布与止损手册

## 1. 适用范围

本手册用于部署包含 `V49__remove_legacy_learner_profile_storage.sql` 的版本。该版本只使用
claim/evidence 五表，不恢复 `learner_profile_entry`，也不重新启用
`learner-profile.code-review.v1`。

所有开关默认关闭：

- `LEARNER_MEMORY_DECLARED_UPDATE_ENABLED=false`
- `LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED=false`
- `LEARNER_MEMORY_RECALL_PRACTICE_CHAT_ENABLED=false`
- `QUEUE_CONSUMER_ENABLED=false`

`LEARNER_PROFILE_DOCUMENT_STATEMENT_REF_HMAC_SECRET` 必须由部署环境的密钥管理系统提供；不得把
实际值写入配置文件、工单、日志或本手册。

## 2. 发布顺序

1. 确认四个开关均关闭，停止 queue worker，并暂缓同步含新 `/me` 入口的前端静态产物。
2. 记录 v1 topic 消息数、五张 learner memory 表的行数和当前健康检查结果。备份仅用于基础设施事故恢复，不承诺恢复旧画像业务数据。
3. 部署新后端，完成 Flyway 升级；在应用未开启记忆功能时验证数据库健康状态。
4. 仅开启 declared update，以测试用户验证 update run、claim revision 和 message evidence 均已写入。
5. 开启 queue worker 与 v2 Code Review consumer，提交五条正式 Review，验证一个完整批次、claim revision 与 Review evidence；不启用历史回放、DLQ 或临时重试。
6. 先在内部账号验证 `/api/me/learner-profile` 的文档投影和引用分页，再开启 Practice Chat recall；确认 bootstrap 和按需工具均在预算内。
7. 最后同步前端静态产物，发布 `/me` 文档入口和 Review 深链。缩短未上线环境的观察窗口可以，但必须记录实际观察时间和验证账号。

## 3. 数据库健康检查

迁移完成后，在目标 schema 执行以下只读检查：

```sql
SELECT to_regclass('learner_profile_entry') IS NULL AS legacy_table_removed;

SELECT COUNT(*) AS legacy_topic_messages
FROM queue_message
WHERE topic = 'learner-profile.code-review.v1';

SELECT COUNT(*) AS active_claims
FROM learner_memory_claim_revision
WHERE status = 'ACTIVE';
```

前两项必须分别为 `true` 和 `0`。同时确认五张新表均存在：
`learner_memory_update_run`、`learner_memory_update_run_review`、
`learner_memory_claim_revision`、`learner_memory_claim_review_evidence`、
`learner_memory_claim_message_evidence`。新表已有数据时只核对，不删除、不回填。

应用层检查使用 `/actuator/health` 和 `/actuator/prometheus`。先确认 Flyway、数据库和应用 health 正常，再逐步开启功能开关。

## 4. 观察指标与阈值

按功能开启后至少观察 15 分钟，并记录以下指标：

- `learner_memory_update_run_total`：按 trigger/status 观察 declared 和 Review run 的成功、失败、陈旧重算情况。
- `learner_memory_invalid_output_total`、`learner_memory_operation_total`、`learner_memory_evidence_count`：确认模型输出和证据写入符合预期。
- `learner_memory_tool_call_total`、`learner_memory_recall_count`、`learner_memory_recall_range_read_total`：确认工具仅在启用 recall 后调用。
- `learner_memory_bootstrap_token_estimate`、`learner_memory_bootstrap_trimmed_total`、`learner_memory_recall_tool_result_chars`：确认 bootstrap 不超过 1500 token，单次和总工具结果预算未越界。
- `learner_memory_profile_projection_total`、`learner_memory_profile_citation_count`：确认文档投影可用且引用数量合理。

任一跨用户访问拒绝失效、敏感正文进入日志/诊断快照、Flyway 失败、旧表/topic 仍存在时，立即关闭全部 learner-memory 开关并停止发布。单一写入链路在 15 分钟内失败或 `STALE` 比例超过 5%，关闭该链路；recall 的预算越界、工具拒绝异常增长或投影失败率超过 1%，先关闭 recall 和前端入口。恢复开启必须重新从第 4 步按顺序进行。

## 5. 关闭式回滚

止损只允许前向操作：关闭 declared update、v2 consumer、recall 和必要时 queue worker，停止新增记忆读写；已经发布的画像 API 和页面保持只读，必要时由兼容新 schema 的前向补丁隐藏入口。

不得重新创建旧表、回填旧正文、恢复 v1 topic 或部署依赖 `learner_profile_entry` 的旧二进制。五张新表、update run、claim revision 和 evidence 必须保留。v2 queue 未消费消息继续遵循成功确认的至少一次语义：失败按队列配置有限重试，达到上限进入 FAILED 并告警停止 topic；不增加临时 DLQ 或自动人工回放。
