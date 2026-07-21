# LP-01：正式 Code Review 事实语义收敛实施计划

> 波次：A
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-implementation-plan-task-list.md`、`docs/ai-learner-profile-agent-integration-technical-direction.md`
>
> 直接依赖：无

## 1. 任务目标与完成标准

本任务把 `practice_code_review` 收敛为“已经形成有效评分和点评的正式 Review”事实表。完成后：

- `NOT_CODE_LIKE`、`NOT_COMPLETE_SUBMISSION`、结构化输出失败和 LLM 失败只返回明确结果，不写正式 Review；
- 只有有效 Review 调用 `PracticeCodeReviewRepository.save` 和 `PracticeCodeReviewObserver.onReviewSaved`；
- replay 命中既有 Review 时复用，replay 缺失时返回失败且不重新调用模型；
- 完成门槛、Review 历史、错题观察者和能力雷达只消费正式 Review；
- 当前 Agent run 在不可 Review 或失败时仍能继续回答。

## 2. 当前实现基线

- `PracticeCodeReviewService.reviewWithLlm` 在 LLM 异常或 mapper 返回非 `REVIEWED` 时调用 `saveRejectedAttempt`。
- `saveRejectedAttempt` 生成零分 `PracticeCodeReviewDraft`，写入 `REVIEW_ATTEMPT_REJECTED` evidence 后仍走正式保存和 Observer。
- `PracticeCodeReviewStructuredOutputMapper` 已有 `NOT_CODE_LIKE` 状态，但当前三个布尔字段任一为 false 都返回 `NOT_COMPLETE_SUBMISSION`。
- `PracticeCodeReviewToolResultMapper` 已输出 `status`，但所有结果都使用 `practice_code_review_submitted` result type。
- `PracticeCompletionGateService`、`PracticeSessionService`、Review 历史和 `MistakeNoteService` 都通过正式 Review repository 读取现有记录。
- 前端 `PracticeChatWorkbench.tsx` 主要按 result type 触发 Review 刷新，未把 `status=SAVED` 作为必要条件。

## 3. 已确认的代码冲突或缺口

1. `saveRejectedAttempt` 把诊断尝试伪装成正式业务事实，直接违背上游画像设计。
2. 非代码、非当前题和非完整解法缺少稳定、互斥的状态映射。
3. 失败尝试若不再落库，现有 `reviewAttemptStatus` metadata 分支和对应测试将失效。
4. 历史零分 rejected row 没有独立状态列；只能通过 `detection_evidence_json` 中的 `REVIEW_ATTEMPT_REJECTED` 识别。
5. 前端若只检查 result type，失败结果仍可能触发无意义的 Review 刷新。

## 4. 范围、非目标和依赖

范围：Review 状态映射、正式保存条件、tool result、指标、完成 gate/历史/错题兼容检查和最小前端兼容调整。

非目标：不增加 `affectedTagIds`，不新增迁移，不发布画像消息，不修改画像表、画像 Prompt 或队列。

依赖：无；完成后是 `LP-02` 和 `LP-08` 的语义前置。

## 5. 关键技术决策

- `isCodeSubmission=false` 映射为 `NOT_CODE_LIKE`。
- `isCodeSubmission=true` 且 `belongsToCurrentProblem=false` 或 `isCompleteLeetCodeSolution=false` 映射为 `NOT_COMPLETE_SUBMISSION`。
- Schema 缺字段、类型错误、分数越界、正文缺失和 LLM 异常映射为 `FAILED`。
- 只有 mapper 返回 `REVIEWED` 才可构造并保存 draft；保存成功后返回 `SAVED`。
- 失败尝试通过低基数指标、脱敏日志和现有 AI 调用台账诊断，不新增“尝试表”。
- replay 只读既有 Review；缺失时保留 `PRACTICE_CODE_REVIEW_REPLAY_MISSING`。
- 不在本任务清理历史 rejected row；清理和已派生错题修复列为开放项。

## 6. 领域模型、接口、常量和配置契约

- 保留 `PracticeReviewStatus`：`NOT_CODE_LIKE`、`NOT_COMPLETE_SUBMISSION`、`REVIEWED`、`SAVED`、`FAILED`。
- `REVIEWED` 仅为服务内部临时状态，不直接返回前端；外部稳定状态为其余四种。
- 只有 `SAVED` 的 tool result 可携带 `reviewId`、`versionNo`、`totalScore`、`passed`；其他状态对应字段必须为 `null`。
- 保留公共失败码常量：`PRACTICE_CODE_REVIEW_LLM_FAILED`、`PRACTICE_CODE_REVIEW_SAVE_FAILED`、`PRACTICE_CODE_REVIEW_REPLAY_MISSING`、`INVALID_STRUCTURED_OUTPUT`。
- 不新增配置 key；失败指标继续使用 `PracticeCodeReviewMetricStatus.COMPLETED/UNREVIEWABLE/FAILED`。

## 7. 数据库迁移、约束、索引和事务边界

- 本任务无 Flyway 迁移，不修改 `practice_code_review` 表、约束和索引。
- 成功保存仍复用 `MyBatisPracticeCodeReviewRepository.save` 的现有事务和 session `FOR UPDATE`。
- 不可 Review、LLM 失败和结构化失败必须在 repository 调用前结束，数据库行数保持不变。
- 既有 `ON CONFLICT (practice_session_id, user_message_id)` 幂等行为暂时保留；“新建/复用”返回值在 `LP-02/LP-08` 收敛。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewService.java` | 删除 rejected attempt 落库分支 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewStructuredOutputMapper.java` | 稳定状态映射 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeReviewResult.java` | 清理 attempt metadata 依赖 |
| Modify | `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewToolResultMapper.java` | 仅 `SAVED` 输出正式 Review 字段 |
| Modify | `frontend/src/learning-plans/PracticeChatWorkbench.tsx` | 仅 `SAVED` 刷新 Review/追加分数 |
| Modify | `frontend/src/types/api.ts` | 若现有 Review result 状态类型未枚举则补齐 |
| Modify | `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewServiceTest.java` | 反转失败尝试落库断言 |
| Modify | `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewFlowTest.java` | 完整流程回归 |
| Modify | `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx` | 非 SAVED 不刷新 |
| Create | `backend/mentor-api/src/test/java/org/congcong/algomentor/api/practice/PracticeCodeReviewFormalFactIT.java` | PostgreSQL 行数与唯一约束验证 |

## 9. 分阶段实施步骤

1. 先固定状态判定表，并为 output mapper 增加非代码、非当前题、非完整和非法输出用例。
2. 删除 `saveRejectedAttempt`、`rejectedAttemptDraft`、零分 score 和 attempt metadata 分支。
3. 收敛 tool result：非 `SAVED` 不暴露正式 Review 标识和评分。
4. 复核完成 gate、历史列表、错题 Observer、能力雷达 SQL，不增加兼容零分逻辑。
5. 调整前端 Review 刷新条件并补充重复/失败结果回归。
6. 增加 PostgreSQL IT，验证失败路径不写行、成功路径仍满足幂等约束。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | `PracticeCodeReviewStructuredOutputMapperTest` | 四类非成功输入得到固定状态 |
| 2 | `PracticeCodeReviewServiceTest` | repository/Observer 调用次数为 0 或 1 |
| 3 | `PracticeCodeReviewToolResultMapperTest` | 只有 `SAVED` 有 reviewId/score |
| 4 | gate、history、mistake、ability 相关测试 | 无 rejected attempt 被识别为新正式版本 |
| 5 | `PracticeChatWorkbench.test.tsx` | 失败结果不刷新 Review、不追加分数 |
| 6 | `PracticeCodeReviewFormalFactIT` | 失败前后表行数相同，成功只新增一行 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- 单元测试覆盖：LLM 异常、空/非法 structured output、非代码、非当前题、非完整代码、repository 失败、既有 Review 复用、replay 命中和缺失。
- 验证非成功路径不调用 `repository.save`、不调用 Observer；成功新建各调用一次。
- 完成 gate 使用最新正式 Review，历史列表不新增失败版本，错题 ingestion 不接收失败尝试。
- PostgreSQL IT 在相同 `userMessageId` 下验证正式成功仍只有一行；所有失败路径行数不变。
- 历史 rejected row 只做识别审计测试，不在本任务自动删除。

## 12. 日志、指标、隐私和 AI governance 要求

- 日志记录 `status`、`failureCode`、代码长度、provider/model 和 run 标识，不记录完整代码、Prompt、Authorization 或异常 metadata 原文。
- `practice.review{status=completed|unreviewable|failed}` 使用低基数 tag；不得以 userId/messageId 作 tag。
- LLM 失败仍通过 `AiCompletionGateway` 和 `AiRunSource.PRACTICE_CODE_REVIEW` 进入现有治理台账。
- 移除 attempt 落库后，排障依赖日志、指标和 `ai_llm_call_usage`，实施说明必须同步给运维。

## 13. 发布顺序、兼容性和回滚方案

- 先发布状态与保存语义，再开始 `LP-02` 标签归因。
- 对外 HTTP Review API 不变；Agent tool result 字段保留，只调整非成功字段为空的语义。
- 若正式 Review 数异常下降、完成 gate 错误阻断或前端不再刷新成功 Review，回滚应用版本即可；无迁移回滚。
- 历史 rejected row 和其派生错题保留，避免未经产品确认的数据删除。

## 14. 风险与开放项

- 开放项：是否离线清理历史 rejected row，以及如何处理由它们生成的错题记录；不阻塞本任务代码语义收敛。
- 风险：模型把真实代码误判为不可 Review。需通过状态指标和样例回归观察，而不是恢复零分落库。
- 风险：前端仍按 result type 处理。必须以 `status=SAVED` 为刷新门禁。

## 15. 可复制执行的验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest=PracticeCodeReviewStructuredOutputMapperTest,PracticeCodeReviewServiceTest,PracticeCodeReviewFlowTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test=PracticeCodeReviewFormalFactIT verify

npm --cache ./.npm --prefix frontend test -- PracticeChatWorkbench.test.tsx
git diff --check
```

## 16. 最终验收 checklist

- [ ] 四类非成功结果均不写 `practice_code_review`。
- [ ] 只有有效正式 Review 调用 Observer。
- [ ] replay 缺失不调用 LLM 或 repository。
- [ ] `SAVED` 与非成功 tool result 字段边界明确。
- [ ] 完成 gate、历史、错题和能力雷达回归通过。
- [ ] 前端仅在 `SAVED` 时刷新 Review。
- [ ] PostgreSQL IT 证明失败路径零写入。
- [ ] 未引入标签、画像、队列或 Prompt 实现。
