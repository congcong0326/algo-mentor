# LP-12：前端画像工具状态反馈实施计划

> 波次：D
>
> 状态：待实施
>
> 依据：`docs/ai-learner-profile-agent-integration-technical-direction.md`、`docs/ai-learner-profile-implementation-plan-task-list.md`
>
> 直接依赖：LP-10

## 1. 任务目标与完成标准

让练习聊天工作台基于现有 `agent_tool_start/end` 事件展示学习记忆“更新中、已更新、无需更新、更新失败”状态，不新增画像管理页面或后端 SSE 事件。

完成后：四种状态可见且可访问；重复 SSE/reconnect 不重复追加；FAILED 不覆盖主 Agent 后续回答；Review 工具、权限提示和未知工具行为不回归。

## 2. 当前实现基线

- `frontend/src/types/api.ts` 已有通用 `AgentToolStartEvent` 和 `AgentToolEndEvent`。
- `PracticeChatWorkbench.tsx` 顶部局部硬编码 Review tool name/result type，只对 Review 工具专门处理。
- tool start 当前会替换 assistant placeholder；tool end 可能向 assistant 正文追加 Review 分数。
- 组件没有基于 `runId/stepIndex/toolCallId` 的已处理事件集合，SSE 重放可能重复追加。
- `locales.ts` 已提供中英文练习工作台文案模式。

## 3. 已确认的代码冲突或缺口

1. 若复用 assistant placeholder 展示画像状态，FAILED 可能覆盖或打断后续正常回答。
2. 工具常量继续散落在组件内会使 LP-01 Review 和 LP-12 画像处理漂移。
3. 仅按 toolName/status 去重不能区分同 run 多次工具调用。
4. 后端 FAILED result 不包含内部错误，前端不能自行拼接数据库/模型失败细节。

## 4. 范围、非目标和依赖

范围：前端共享工具契约、结果解析、独立状态提示、事件幂等、中英文文案、无障碍和组件测试。

非目标：不新增画像查看/编辑/删除/历史 UI，不新增 REST API，不新增 SSE 事件，不启动 Vite 开发服务器。

依赖：LP-10 固定工具名、result type 和三种终态。

## 5. 关键技术决策

- 新建共享 `profileToolContract.ts`，集中工具名、result type、status 类型和安全 parser。
- 状态提示独立于 assistant Markdown，使用组件本地 `toolStatuses` map 渲染；ARIA 使用 `role="status"`。
- 幂等键固定为 `runId:stepIndex:toolCallId`；start 更新为 RUNNING，end 更新为终态，不追加重复元素。
- end 终态只接受 `UPDATED/NO_CHANGE/FAILED`；未知值按未知工具结果忽略并记录前端开发诊断，不展示内部错误。
- 同一 toolCall 的重复 start/end 幂等；不同 toolCall 可分别展示或按完成顺序保留。
- Agent run 结束不删除当前轮已完成状态，开始下一次发送时按现有消息周期清理临时 RUNNING。

## 6. 领域模型、接口、常量和配置契约

- `LEARNER_DECLARED_PROFILE_TOOL_NAME = 'update_learner_declared_profile'`。
- `LEARNER_DECLARED_PROFILE_RESULT_TYPE = 'learner_declared_profile_update'`。
- `LearnerDeclaredProfileToolStatus = 'UPDATED' | 'NO_CHANGE' | 'FAILED'`。
- 前端展示状态增加内部 `RUNNING`，不作为后端 result status。
- 去重 key 字段严格使用通用事件的 `runId`、`stepIndex`、`toolCallId`。
- 文案：
  - RUNNING：正在更新学习记忆...
  - UPDATED：已更新学习记忆
  - NO_CHANGE：学习记忆无需更新
  - FAILED：学习记忆暂未更新
- 英文提供等价、非技术化文案。

## 7. 数据库迁移、约束、索引和事务边界

本任务无数据库、迁移、MyBatis 或事务变更，也不需要 PostgreSQL IT。

前端状态只反映本轮工具生命周期，不作为后端提交事实的替代来源；刷新页面后无需从新 API 恢复该提示。

## 8. 目标模块和主要文件清单

| 动作 | 文件 | 职责 |
| --- | --- | --- |
| Create | `frontend/src/learning-plans/profileToolContract.ts` | 工具名、状态、result parser |
| Modify | `frontend/src/types/api.ts` | 如需导出画像 tool result 类型 |
| Modify | `frontend/src/learning-plans/PracticeChatWorkbench.tsx` | 独立状态 map、事件处理、ARIA |
| Modify | `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx` | 四状态、去重和兼容测试 |
| Modify | `frontend/src/i18n/locales.ts` | 中英文文案 |

## 9. 分阶段实施步骤

1. 固定前端工具名/result/status 类型和安全 parser。
2. 在 Workbench 增加按 toolCall identity 管理的独立状态 map。
3. 接入 start -> RUNNING、end -> 三种终态，不改 assistant 正文。
4. 增加 i18n、`role=status`、加载和失败可访问性。
5. 增加重复事件/reconnect、多个调用和后续 content_delta 回归。
6. 复跑 Review 工具、权限事件和未知工具既有测试。

## 10. 每个步骤对应的测试和可观察结果

| 步骤 | 测试 | 可观察结果 |
| --- | --- | --- |
| 1 | parser unit tests | 非法/未知 result 安全忽略 |
| 2 | component state tests | 同 identity 只有一个状态节点 |
| 3 | SSE event tests | start/UPDATED/NO_CHANGE/FAILED 映射正确 |
| 4 | accessibility/i18n tests | 中英文文案、role=status 可查询 |
| 5 | duplicate/reconnect tests | 重复事件不重复文案，回答继续追加 |
| 6 | existing Review/permission tests | 原行为不回归 |

## 11. 单元测试与 PostgreSQL 集成测试设计

- Vitest/RTL 覆盖四种显示状态。
- 同一 `runId+stepIndex+toolCallId` 重复 start、重复相同 end、重连后 start+end 重放，DOM 中只存在一个对应提示。
- 同 run 两个不同 toolCallId 可分别完成，不互相覆盖。
- FAILED 后继续收到 `content_delta`，assistant 正文完整且失败提示仍独立。
- 未知工具按现有逻辑处理；Review tool start/end/score refresh 不回归。
- permission denied/timeout 结果不误判为画像 FAILED。
- 本任务不设计 PostgreSQL IT。

## 12. 日志、指标、隐私和 AI governance 要求

- 前端不展示后端 message 以外的 exception、数据库或模型细节；FAILED 使用固定低敏文案。
- 不把 tool arguments、contentSummary 或画像正文写入 console/analytics。
- 若项目已有前端错误采集，只记录 toolName、status 和事件解析 outcome，不记录 result payload。
- AI governance 由 LP-10 后端负责，前端不自行判断调用成功或计费。

## 13. 发布顺序、兼容性和回滚方案

- LP-10 契约稳定后实现；可在后端工具 enabled=false 时先发布前端兼容代码。
- 旧后端不发送该 toolName 时前端无变化；新后端配旧前端时 tool 仍可执行，只缺状态提示。
- 出现状态错乱时回滚前端即可，不影响后端画像或消息数据。
- 不增加路由/API，因此无数据迁移和服务端回滚要求。

## 14. 风险与开放项

- 风险：把状态混入 assistant content。实现必须使用独立状态节点。
- 风险：SSE identity 字段缺失。parser 应忽略不完整事件，不用随机 key 制造重复。
- 风险：LP-01 同时调整 Review 工具常量；实现时应评估是否把 Review 契约也迁到共享文件，但不扩大为全量 SSE 重构。
- 明确不做画像管理页面。

## 15. 可复制执行的验证命令

```bash
npm --cache ./.npm --prefix frontend test -- PracticeChatWorkbench.test.tsx
npm --cache ./.npm --prefix frontend run build
git diff --check
```

## 16. 最终验收 checklist

- [ ] 工具名、result type、status 使用共享类型/常量。
- [ ] RUNNING、UPDATED、NO_CHANGE、FAILED 均有中英文状态。
- [ ] 状态使用独立 UI，不覆盖 assistant 回答。
- [ ] 重复 SSE/reconnect 不重复追加。
- [ ] 不完整或未知事件安全忽略。
- [ ] Review 工具、权限提示和未知工具行为不回归。
- [ ] 无画像正文、tool arguments 或内部错误泄露。
- [ ] 未新增 API、SSE 事件或画像管理 UI。
