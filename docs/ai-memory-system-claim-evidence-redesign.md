# AI 记忆系统 Claim、证据链与画像文档投影重构设计

## 文档信息

- 设计日期：2026-07-30
- 文档性质：破坏性重构设计稿，待审核
- 前置现状：`docs/ai-memory-system-current-state-audit.md`
- 前置讨论：`docs/ai-memory-system-optimization-discussion.md`
- 适用项目：`algo-mentor`
- 数据策略：项目尚未上线，允许删除现有学习者画像和旧画像队列数据，不做历史数据回填

## 一、已确认的产品决策

本文以以下决策为约束，不再作为待讨论项：

1. Code Review 画像仍采用批处理，固定每个用户积累 5 条正式 Review 后触发一次付费分析。
2. 记忆不要求实时生效，1 至 4 条未满批消息可以继续等待。
3. 画像 Agent 可以获得只读工具，自主决定是否查询同一道题的历史提交版本。
4. 不把全部历史提交一次性塞入 Prompt，历史数据按需探索。
5. 用户应能理解某条 AI 判断由哪些提交形成，并能跳转到具体提交版本。
6. 当前整段自然语言画像不再作为存储主模型，重构为原子 claim。
7. 不使用模型自报的 `confidence` 作为写入门禁；证据强度由服务端根据证据结构计算。
8. 允许破坏性删除 `learner_profile_entry` 现有数据和旧 topic 待消费消息。
9. `/me` 默认只展示一篇连续的 Markdown 风格学习画像，不把 dimension、claim 类型、revision 或 evidence grade 暴露为用户需要理解的信息架构。
10. 每个主要判断保持句子级出处：引用编号始终可见，桌面端支持 hover/focus 预览，点击或移动端轻触可查看完整依据并跳转到具体提交版本。
11. 长期存储容量与 Prompt 注入预算解耦；允许保存数百条 ACTIVE claim，并长期保留历史 revision 和 evidence。
12. Practice Chat 的 token 预算只约束首次注入的记忆索引和直接命中项，不代表本次 run 可使用的全部记忆。
13. Agent 获得受控只读记忆工具，自主搜索、读取章节和查看证据；长结果复用现有 tool result preview、blob 和范围读取能力。

## 二、设计结论

新系统采用四层结构：

```text
业务事实层
Review / 用户消息 / 标签 / 评分 / 代码证据
        |
        v
Agent 证据探索层
横向最新窗口 + 纵向历史工具 + 严格结构化输出
        |
        v
原子记忆层
Claim revision + typed evidence + update run
        |
        v
消费投影层
Practice Chat bootstrap index + 记忆工具探索 + /me 画像文档投影
```

核心原则是：

> 一个 claim 只表达一个主要判断；一次 claim 版本变化必须关联可验证的来源记录；用户看到的是一篇聚合文档，但文档中的每个主要判断仍保持独立出处；聚合展示和 Prompt 渲染只是 claim 的投影，不再反向成为事实源。

## 三、目标与非目标

### 3.1 目标

- 将多个事实聚合成整段正文的模型改为多个可独立更新、失效和展示的 claim。
- 保留用户自述、跨题观察和标签评价三类产品语义。
- 让 Code Review 画像 Agent 同时利用跨题横向事实和单题纵向轨迹。
- 对模型输出执行来源归属、范围、结构和证据形态校验。
- 记录每个系统 claim 使用的具体 Review 版本和证据角色。
- 将 ACTIVE claim 确定性投影为一篇 Markdown 风格画像，隐藏底层 dimension、claim 和版本模型。
- 在画像句子上展示“AI 判断依据”，并跳转到具体 Review。
- 扩大 ACTIVE claim 容量和单条内容上限，使长期刷题产生的稳定模式可以持续积累。
- 将 Practice Chat 召回改为“启动索引 + 少量直接命中 + Agent 按需探索”，避免长期容量受系统提示词长度限制。
- 保留当前用户行锁、快照令牌、模型调用不持有事务和批量全有或全无语义。
- 保持 Practice Chat 首次注入和后续工具探索均有界，不在普通聊天 Prompt 中注入完整画像或完整证据。

### 3.2 非目标

- 不建设向量数据库或通用语义记忆平台。
- 不让记忆实时更新，不改变固定 5 条满批策略。
- 不让 Agent 无边界查询全部 Review、全部题目或其他用户数据。
- 不默认向画像 Agent 返回全部历史原始代码。
- 不把全部 ACTIVE claim 注入每次 Practice Chat，也不因 Prompt 未出现某条 claim 就把它视为不存在。
- 第一版不为单用户数百条 claim 引入向量数据库，优先使用结构化 scope/tag 过滤、确定性排序和当前用户范围内的 PostgreSQL 文本匹配。
- 不宣称证据链能够完全消除模型对代码或语义的错误理解。
- 不对旧画像正文使用 LLM 反推或补造历史证据。
- 不在读取画像时再次调用 LLM 自由改写整篇文档，避免新增成本、幻觉和证据错配。
- 不把聚合 Markdown 或画像文档投影作为可独立编辑、可反向写回 claim 的事实存储。
- 不在本阶段建设用户可编辑画像的完整产品闭环，但数据模型预留拒绝和抑制状态。
- 不改变正式 Code Review 自身的评分与完成门槛。

## 四、领域模型

### 4.1 Claim

Claim 是用户能够直接阅读的一条原子判断，例如：

- “在多道题中容易遗漏边界条件。”
- “收到反馈后通常能在下一版完成修正。”
- “二分查找的边界定义仍不稳定。”
- “当前主要使用 Java 进行算法练习。”

以下内容不能合并成一个 claim：

> 经常遗漏边界条件，但收到提示后通常能快速修正。

它包含“错误模式”和“成长能力”两个主要判断，必须拆成两个 claim，并分别关联证据。

### 4.2 Claim 类型和维度

继续保留现有三类语义和十个固定维度：

| Claim 类型 | 维度 | 写入来源 |
|---|---|---|
| `DECLARED_FACT` | `LEARNER_BACKGROUND` | 用户明确陈述 |
| `DECLARED_FACT` | `GOALS_AND_INTENTS` | 用户明确陈述 |
| `DECLARED_FACT` | `TIME_AND_RESOURCE_CONSTRAINTS` | 用户明确陈述 |
| `DECLARED_FACT` | `LEARNING_AND_INTERACTION_PREFERENCES` | 用户明确陈述 |
| `DECLARED_FACT` | `SELF_ABILITY_ASSESSMENT` | 用户明确陈述 |
| `GENERAL_OBSERVATION` | `PROBLEM_SOLVING_APPROACH` | 跨题正式 Review |
| `GENERAL_OBSERVATION` | `IMPLEMENTATION_AND_ERROR_PATTERN` | 跨题正式 Review |
| `GENERAL_OBSERVATION` | `LEARNING_INTERACTION_AND_INDEPENDENCE` | 当前不允许 Code Review Agent 生成 |
| `GENERAL_OBSERVATION` | `REVIEW_AND_GROWTH_PERFORMANCE` | 同题多版本或跨时间正式 Review |
| `TAG_ASSESSMENT` | `TAG_MASTERY` | 绑定受信 `tag_id` 的正式 Review |

与旧模型不同，同一个 dimension 或 tag 可以存在多个 ACTIVE claim。容量按“scope 上限 + 用户软硬上限”治理：

- 每个 `DECLARED_FACT` dimension 最多 10 条。
- 每个 `GENERAL_OBSERVATION` dimension 最多 10 条。
- 每个 tag 最多 5 条 `TAG_MASTERY` claim。
- 每用户 500 条 ACTIVE claim 为软上限；达到后不立即丢弃数据，但后续批次优先 `CONFIRM / REVISE / RETIRE` 和去重合并，并记录容量告警。
- 每用户 1000 条 ACTIVE claim 为防失控硬上限；达到后只拒绝新的 `ADD`，仍允许修订、确认和退役已有 claim。
- `SUPERSEDED / RETIRED / SUPPRESSED / REJECTED` revision 和 evidence 不计入 ACTIVE 上限，不因容量治理自动删除。

这些数字是安全边界而不是目标。正常成熟用户预计保留数百条 ACTIVE claim；刷题数量主要增加证据和 revision，不应按一次提交生成一条新 claim。

### 4.3 Claim 版本

每个逻辑 claim 使用服务端生成的 `claim_key` 标识，后续修订沿用相同 `claim_key`：

```text
claim_key=A
  revision 1 ACTIVE      “容易遗漏边界条件。”
  revision 2 ACTIVE      “边界遗漏已减少，但复杂分支下仍会出现。”
  revision 3 RETIRED     后续证据表明该问题已稳定解决
```

Claim 状态：

| 状态 | 含义 | 是否召回 |
|---|---|---|
| `ACTIVE` | 当前有效判断 | 是 |
| `SUPERSEDED` | 已被同一 claim 的新版本替代 | 否 |
| `RETIRED` | 当前终态，新证据表明判断已经不再成立 | 否 |
| `SUPPRESSED` | 当前终态，用户暂时不允许使用 | 否 |
| `REJECTED` | 当前终态，用户明确认为判断错误 | 否 |

同一 `claim_key` 最多存在一个当前 revision。`ACTIVE / RETIRED / SUPPRESSED / REJECTED` 都属于当前 revision，只有 `SUPERSEDED` 属于历史 revision：

- `CONFIRM / REVISE`：旧当前 revision 转为 `SUPERSEDED`，插入新的 `ACTIVE` revision。
- `RETIRE`：旧当前 revision 转为 `SUPERSEDED`，复制原文本并插入新的 `RETIRED` 终态 revision，同时关联导致退役的证据。
- 后续用户抑制或拒绝：同样通过新建 `SUPPRESSED / REJECTED` 终态 revision 保留完整决策历史。
- 如果未来使用新证据重新激活，终态 revision 转为 `SUPERSEDED`，再插入新的 `ACTIVE` revision。

### 4.4 Agent 操作

Agent 只能输出以下操作：

| 操作 | 含义 |
|---|---|
| `ADD` | 创建新的逻辑 claim 和 revision 1 |
| `CONFIRM` | 保持 claim 文本，但以新的证据集生成下一 revision |
| `REVISE` | 修改 claim 文本并生成下一 revision |
| `RETIRE` | 使用新证据结束当前 claim |

没有输出操作等价于 `NO_CHANGE`。模型不能直接输出 `SUPPRESSED` 或 `REJECTED`，这两个状态只由用户操作产生。

## 五、证据模型

### 5.1 证据不是结论证明

产品和代码统一使用“AI 判断依据”，不使用“证明”。证据链表达的是：

- Agent 在形成判断时实际引用了哪些业务记录。
- 这些记录在判断中承担什么角色。
- 服务端验证了来源归属和结构条件。

它不能保证模型对代码和自然语言的语义理解绝对正确。

### 5.2 Review 证据角色

系统观察的 Review evidence role 固定为：

| 角色 | 含义 |
|---|---|
| `OBSERVED` | 该版本观察到 claim 所描述的现象 |
| `PERSISTED` | 后续版本仍然存在同类现象 |
| `RESOLVED` | 后续版本已经修正此前现象 |
| `REGRESSED` | 此前改善后再次出现 |
| `CONTRADICTS` | 与当前 claim 相反的证据 |

用户自述的 message evidence role 固定为：

| 角色 | 含义 |
|---|---|
| `DECLARED` | 用户首次明确陈述 |
| `CORRECTED` | 用户明确纠正此前陈述 |

这些字符串属于跨模块公共契约，必须由枚举和常量统一管理，不能散落在 Prompt、JSON Schema、SQL 和前端中。

### 5.3 Evidence pattern

Agent 输出受控的 `evidencePattern`，服务端按引用记录重新计算和校验：

| Pattern | 最小结构要求 |
|---|---|
| `USER_DECLARATION` | 至少一条当前用户消息 |
| `USER_CORRECTION` | 当前纠正消息，必要时包含旧声明消息 |
| `SINGLE_REVIEW` | 一条正式 Review，仅允许标签评价使用 |
| `SAME_PROBLEM_PERSISTENCE` | 同题至少两个不同版本 |
| `SAME_PROBLEM_RECOVERY` | 同题早期和后续版本，至少包含 `OBSERVED + RESOLVED` |
| `SAME_PROBLEM_REGRESSION` | 同题至少三个时间有序版本，包含改善后再次出现 |
| `CROSS_PROBLEM_RECURRENCE` | 至少两个不同 problem slug |
| `CROSS_PROBLEM_LONGITUDINAL` | 至少两个不同 problem slug，且至少一个包含多版本轨迹 |
| `TAG_BREADTH` | 至少两个不同题目，且 Review 均关联目标 tag |

### 5.4 Evidence grade

不接收模型输出的 confidence。服务端根据最终通过校验的证据计算 `evidenceGrade`：

| Grade | 含义 |
|---|---|
| `LIMITED` | 单题、单版本或证据跨度有限 |
| `SUPPORTED` | 同题多版本或至少两个不同题目 |
| `STRONG` | 至少两个不同题目且包含纵向轨迹，或跨两个批次持续确认 |
| `USER_AUTHORED` | 用户明确声明或纠正，不与系统能力评价混用 |

`evidenceGrade` 只描述证据结构强度，不表示结论为真的概率，第一版不在 UI 中展示“置信度百分比”。

## 六、数据模型

破坏性迁移新增四张主表，删除旧 `learner_profile_entry`。

### 6.1 `learner_memory_update_run`

记录一次业务画像更新尝试，与 Agent runtime run 建立关联：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `user_id` | 当前用户，FK `auth_users` |
| `trigger_type` | `DECLARED_FACT` 或 `CODE_REVIEW_BATCH` |
| `status` | `RUNNING / SUCCEEDED / NO_CHANGE / FAILED` |
| `idempotency_key` | 业务幂等键，唯一 |
| `agent_run_id` | 对应 `agent_run.id`，允许失败前为空 |
| `prompt_version` | 画像 Prompt 版本 |
| `schema_version` | Agent 输出 schema 版本 |
| `input_count` | 触发输入数量 |
| `operation_count` | 最终应用操作数量 |
| `tool_call_count` | 工具调用数量 |
| `failure_code` | 低敏稳定失败码 |
| `started_at` | 开始时间 |
| `completed_at` | 结束时间 |
| `created_at` | 创建时间 |

Code Review 批次幂等键继续基于 `user_id + sorted(reviewIds)` 计算 SHA-256。

### 6.2 `learner_memory_update_run_review`

记录固定 5 条触发 Review：

| 字段 | 说明 |
|---|---|
| `update_run_id` | FK `learner_memory_update_run` |
| `review_id` | FK `practice_code_review` |
| `sequence_no` | 批次内稳定顺序 |

唯一约束保证同一 run 不重复引用 Review。历史工具额外读取但未作为最终证据的 Review 不复制到该表，详细访问仍由 Agent tool trace 保存。

### 6.3 `learner_memory_claim_revision`

| 字段 | 说明 |
|---|---|
| `id` | claim revision 主键 |
| `claim_key` | 服务端生成 UUID，标识逻辑 claim |
| `user_id` | 当前用户 |
| `entry_kind` | 三类 claim kind |
| `dimension` | 固定 dimension |
| `tag_id` | `TAG_ASSESSMENT` 必填，其他类型为空 |
| `revision_no` | 同一 `claim_key` 从 1 递增 |
| `status` | `ACTIVE / SUPERSEDED / RETIRED / SUPPRESSED / REJECTED` |
| `claim_text` | 单一主要判断，硬上限 600 字符，生成目标不超过 300 字符 |
| `claim_text_hash` | 规范化正文 SHA-256，用于阻止完全重复 ACTIVE claim |
| `origin_type` | `USER_EXPLICIT / USER_CORRECTION / SYSTEM_DERIVED` |
| `evidence_pattern` | 受控 pattern |
| `evidence_grade` | 服务端计算结果 |
| `decision_reason` | 简短说明，不进入默认 Prompt |
| `update_run_id` | 生成本 revision 的业务 run |
| `supersedes_revision_id` | 前一 revision，第一版为空 |
| `valid_from` | 生效时间 |
| `valid_to` | 被下一 revision 替代的时间；当前 revision 为空 |
| `created_at` | 创建时间 |
| `updated_at` | 状态更新时间 |

核心约束：

- 同一 `claim_key` 最多一个当前 revision，即 partial unique index 覆盖 `status <> 'SUPERSEDED'`。
- 同一 `claim_key + revision_no` 唯一。
- `supersedes_revision_id` 唯一，且不能指向自身。
- 当前 revision 的 `valid_to IS NULL`；`SUPERSEDED` 必须有 `valid_to`。
- kind、dimension 和 tag scope 继续由数据库 check constraint 限制。
- 同一 scope 下 ACTIVE 的 `claim_text_hash` 唯一，阻止完全重复 claim。
- claim 文本 trim 后非空且不超过 600 字符统一硬上限；超过 300 字符允许写入，但仍必须只表达一个主要判断。

### 6.4 `learner_memory_claim_review_evidence`

| 字段 | 说明 |
|---|---|
| `claim_revision_id` | FK `learner_memory_claim_revision` |
| `review_id` | FK `practice_code_review` |
| `evidence_role` | Review evidence role |
| `sequence_no` | UI 时间线稳定顺序 |
| `created_at` | 创建时间 |

Review FK 使用 `ON DELETE RESTRICT`，避免来源记录被删除后 claim 静默失去依据。未来如增加 Session 删除能力，必须先显式删除或退役相关 claim。整用户删除时按“memory -> practice -> identity”顺序清理。

每个 revision 的 evidence 集合必须自包含，不采用只记录增量的语义：

- 新 `ACTIVE` revision 必须重新关联支撑当前 claim 文本所需的完整证据集。
- `CONFIRM` 可以沿用旧证据并加入新证据。
- `REVISE` 只保留仍然支撑新文本的旧证据，并加入新证据。
- `RETIRED` revision 主要关联导致退役的 `RESOLVED / CONTRADICTS` 证据，历史支撑证据通过 `supersedes_revision_id` 回看。

### 6.5 `learner_memory_claim_message_evidence`

| 字段 | 说明 |
|---|---|
| `claim_revision_id` | FK `learner_memory_claim_revision` |
| `message_id` | FK `agent_message` |
| `evidence_role` | `DECLARED / CORRECTED` |
| `sequence_no` | 稳定顺序 |
| `created_at` | 创建时间 |

第一版前端只对 Review evidence 提供精确跳转。消息 evidence 在引用浮层和依据抽屉中展示来源场景、时间和受限长度的用户原话摘录；后续再补会话深链，不能因为暂时没有深链而隐藏其出处。

## 七、Code Review 批处理链路

### 7.1 触发语义

保持现有成本策略：

- topic 升级为 `learner-memory.code-review.v2`。
- key 仍为用户 ID。
- payload v2 仍只包含 `reviewId`。
- 同一用户严格满 5 条才消费。
- 每批正常只执行一次付费画像 Agent run。
- 1 至 4 条 PENDING 可以长期等待，不增加最大等待时间。
- 画像不是关键事务，也不承诺实时生效。

旧 `learner-profile.code-review.v1` 消息在破坏性迁移期间直接删除，不做转换或回放。

### 7.2 横向窗口

批次消费后构造最多 10 道不同题目的最新 Review：

1. 先纳入本批次涉及 problem slug 的最新 Review。
2. 不足 10 道时补充用户最近其他不同题目的最新 Review。
3. 同一道题在横向窗口中仍然只出现最新版本。

横向窗口用于判断当前能力、跨题重复和标签广度，不能因为同题多次提交而增加题目数量。

### 7.3 当前 Claim 快照

Agent 初始输入同时包含：

- 允许更新 scope 内的所有 ACTIVE claim。
- 每个 claim 的 `id`、`claimKey`、revision、文本、dimension、tag 和证据摘要。
- 用户级 snapshot token、ACTIVE 总数和 `NORMAL / SOFT_LIMIT / HARD_LIMIT` 容量状态。

即使用户拥有数百条 ACTIVE claim，也不把无关 scope 注入更新 Agent。当前批次只读取三个可更新 general dimension 和横向窗口受影响 tag 的 claim；达到软上限时在这些相关 scope 内优先合并和退役重复判断。

Code Review Agent 允许更新：

- `PROBLEM_SOLVING_APPROACH`。
- `IMPLEMENTATION_AND_ERROR_PATTERN`。
- `REVIEW_AND_GROWTH_PERFORMANCE`。
- 横向窗口受影响 tag 的 `TAG_MASTERY`。

不允许根据代码提交推断 `LEARNING_INTERACTION_AND_INDEPENDENCE`。

## 八、Agent 工具设计

工具名称、参数名、结果字段和 metadata key 必须集中到 `LearnerMemoryAgentToolContracts` 等常量类。

### 8.1 `get_problem_review_trajectory`

参数：

```json
{
  "problemSlug": "two-sum"
}
```

权限和边界：

- user ID 只取 Agent invocation context，不接受模型传入。
- problem slug 必须属于当前横向窗口。
- 最多返回最近 5 个正式 Review 版本。
- 结果按版本升序，包含 `reviewId`、时间、评分、是否通过、扣分原因、建议、标签和 Review evidence。
- 服务端确定性计算相邻版本的分数 delta、持续项、已解决项和新出现项。
- 不返回完整 raw code。

### 8.2 `get_code_review_evidence`

参数：

```json
{
  "reviewId": 123
}
```

边界：

- Review 必须属于当前用户和当前允许 problem slug。
- 返回 Review detection evidence、context summary、扣分原因和建议。
- 不返回完整 Markdown 和完整 raw code。
- 单次返回设置字符上限并提供 `truncated` 标记。

### 8.3 `compare_submission_versions`

参数：

```json
{
  "fromReviewId": 123,
  "toReviewId": 127
}
```

边界：

- 两个 Review 必须属于当前用户、同一 problem slug 且版本有序。
- 服务端生成有长度上限的 normalized code unified diff。
- 返回评分变化和 Review finding 变化，不返回两份完整代码。

### 8.4 探索预算

- Agent 最大 4 个模型步骤。
- 每个 run 最多 3 次工具调用。
- 每个 problem slug 最多查询一次 trajectory。
- code diff 最多调用一次。
- 工具全部只读，不暴露画像写入能力。
- 超出预算时 Agent 必须基于已有证据输出操作或不输出操作。

## 九、Agent 结构化输出

输出根对象只包含 `operations`：

```json
{
  "operations": [
    {
      "action": "ADD",
      "claimRevisionId": null,
      "entryKind": "GENERAL_OBSERVATION",
      "dimension": "IMPLEMENTATION_AND_ERROR_PATTERN",
      "tagId": null,
      "claimText": "在多道题中容易遗漏边界条件。",
      "evidencePattern": "CROSS_PROBLEM_RECURRENCE",
      "reason": "两个不同题目的最新正式 Review 都出现边界遗漏。",
      "evidence": [
        {"reviewId": 123, "role": "OBSERVED"},
        {"reviewId": 156, "role": "OBSERVED"}
      ]
    }
  ]
}
```

严格约束：

- 根对象和 operation 均 `additionalProperties=false`。
- 最多 12 个 operation。
- `claimText` 硬上限 600 字符，Prompt 生成目标不超过 300 字符；长度放宽不能用来合并多个主要判断。
- `ADD` 不允许提供 `claimRevisionId`，必须提供合法 scope、文本和证据。
- `CONFIRM / REVISE / RETIRE` 必须引用当前 ACTIVE revision ID。
- `CONFIRM` 的 claim 文本由服务端沿用当前文本，模型不得另传文本。
- `REVISE` 必须提供新文本，scope 不得改变。
- `RETIRE` 不提供新文本，但必须提供导致退役的证据和原因。
- 不允许模型输出 `evidenceGrade`、provider、model、用户 ID、Prompt version 或状态时间。
- 同一 active claim 在一个批次中最多出现一次 operation。
- 任何 operation 非法时拒绝整个批次。

## 十、服务端校验与应用

### 10.1 证据校验

服务端重新查询并校验：

- 每个 Review 存在且属于当前用户。
- 每个 Review 的 problem slug 属于本次横向窗口。
- tag claim 的证据 Review 确实关联目标 tag。
- `evidencePattern` 的题目数、版本数、时间顺序和 role 组合满足最小要求。
- 同一证据不重复。
- 证据顺序按 `created_at + review_id` 重新生成，不信任模型顺序。

服务端不能完全证明自然语言 claim 与 Review 语义一致，因此仍需要 Prompt 约束、shadow eval 和人工样本验收。

### 10.2 Claim 校验

- `claimText` 规范化、长度和公共内容策略校验。
- 一个 claim 只允许一个主要判断，主要依赖 Prompt 和评估，不使用脆弱的字符串规则强拆句子。
- `ADD` 后 scope 和用户级 ACTIVE 数量不得超过硬上限；达到 500 条软上限后不单独启动付费任务，在下一次既有批处理中提高合并、修订和退役优先级。
- 规范化文本 hash 不得与同 scope 的其他 ACTIVE claim 重复。
- `REVISE / CONFIRM / RETIRE` 引用的 revision 必须仍然 ACTIVE。
- Code Review Agent 不得操作用户自述 claim。

### 10.3 事务与并发

模型与工具调用全部发生在事务外。应用阶段：

1. 开启短事务。
2. 锁定用户行。
3. 重新读取 ACTIVE claim 集合并复核 snapshot token。
4. 复核所有证据归属和 scope。
5. 按稳定顺序执行全部 operation。
6. 写 claim revision、Review evidence 和 update run 终态。
7. 任一 operation 失败则整个批次回滚。

STALE 时最多重新计算一次，沿用当前配置策略。

## 十一、用户自述链路

用户自述也改为 claim 操作，不再整段替换 dimension 正文。

### 11.1 输入

- 当前用户消息 ID。
- 用户明确声明或纠正的原文。
- 对应 dimension 下的 ACTIVE declared claims。
- 用户级 snapshot token。

### 11.2 输出

声明 Agent 只允许：

- `ADD` 新的用户自述 claim。
- `REVISE` 明确纠正的 claim。
- `RETIRE` 用户明确表示不再成立的 claim。
- 无操作表示不需要更新。

每个操作必须关联当前用户消息 evidence。用户的自我能力评价仍然属于 `SELF_ABILITY_ASSESSMENT`，不能直接覆盖系统 `TAG_MASTERY`。

## 十二、召回设计

### 12.1 Run-local 记忆快照

Practice Chat 的长期记忆单位仍是 ACTIVE claim，不回退到 dimension 级整段正文，也不把用户画像 Markdown 当作 Agent 的事实源。

每个 Agent run 开始时，服务端创建只读 `LearnerMemoryRecallSnapshot`：

- 从受信认证上下文获取 user ID，模型不能传入 user ID。
- 固定本次 run 可见的有序 ACTIVE claim revision ID、`documentRevision` 和自然主题索引。
- 记录当前题目 slug 和受信 tag，用于直接命中排序。
- 快照内容保存在 run context，不要求完整正文进入 LLM Prompt。
- 本次 run 内所有记忆工具只读取该快照包含的 revision；并发画像更新从下一个 run 开始可见。

排序优先级：

1. 与当前任务相关的用户明确自述。
2. 当前题目 tag 直接命中的 claim。
3. 当前问题语义匹配的通用观察。
4. 服务端 evidence grade 较高者。
5. `last confirmed` 或当前 revision 时间较新者。

### 12.2 启动索引与系统提示词预算

原 `max-token-budget` 改名为 `bootstrap-token-budget`。它只限制首次进入系统提示词的记忆导航信息，不限制本次 run 后续通过工具可读取的记忆总量。

默认启动注入包括：

- 记忆使用边界、可用工具名和“Prompt 未出现不等于记忆不存在”的约束。
- `documentRevision` 和自然主题索引：`sectionRef`、标题、claim 数、最近更新时间、当前题目命中数。
- 3 至 8 条与当前任务最相关的完整 claim。
- 每条直接命中 claim 的低成本来源提示，例如“基于 3 次 Review、2 道题”。

不包含：

- 全量 ACTIVE claim。
- Review ID、原始代码和完整 evidence timeline。
- decision reason。
- 由模型自由生成且无法回溯 source claim 的整篇画像摘要。

预算规则：

- 默认 `bootstrap-token-budget=1000`，配置硬上限为 1500 token。
- 裁剪单位为完整索引项或完整 claim，不能从 claim 文本中间截断。
- 预算不足时先减少低优先级直接命中项，再压缩确定性索引字段；必须保留工具使用说明和当前题目命中入口。
- 索引第一版由服务端根据 claim metadata 确定性生成，不额外调用 LLM。
- 后续如增加段落摘要，摘要必须记录 `summaryVersion + sourceClaimRevisionIds`，只能作为导航提示；形成个性化判断前仍应读取原始 claim。

Prompt 明确指导模型：当前注入信息足够时直接回答；涉及未展开的学习历史、长期错误模式、偏好、成长变化或用户明确追问依据时，自主调用记忆工具；与任务无关时不要为了“检查一下”而读取全部画像。

### 12.3 Practice Chat 记忆探索工具

Practice Chat Agent Definition 增加三项业务只读工具：

| Tool | 用途 | 主要参数 | 返回 |
|---|---|---|---|
| `search_learner_memory` | 按自然语言、主题或 tag 搜索相关记忆 | `query`、可选 `sectionRef/tagValues`、`limit/cursor` | claim ref、正文、来源摘要、更新时间、下一 cursor |
| `read_learner_memory_section` | 按画像自然主题顺序浏览长内容 | `sectionRef`、`afterStatementRef`、`limit` | 完整 statement、citation 摘要、下一 cursor |
| `get_learner_memory_evidence` | 在需要核对判断来源时查看证据 | `statementRef`、`limit/cursor` | Review/message evidence 摘要和下一 cursor |

统一约束：

- 工具参数不接受 user ID、任意 `documentRevision` 或任意 claim revision ID；这些值从 run-local 受信 context 解析。
- `sectionRef`、`statementRef` 和 cursor 均为服务端生成的不透明引用，不能枚举其他用户数据。
- 搜索第一版使用 scope/tag 过滤、确定性排序和当前用户范围内的 PostgreSQL 文本匹配；最多 1000 行时允许从简单规范化匹配开始，不把中文分词或向量基础设施作为首发依赖。
- 搜索最多返回 20 条，章节单次最多读取 20 条 statement，证据单次最多读取 20 条。
- 每个 Practice Chat run 最多 3 次业务记忆工具调用；单次模型可见结果最多 8000 字符，三项工具合计最多 24000 字符。
- 对记忆工具生成的 `resultRef` 每个 run 最多追加 2 次 `read_tool_result`，其返回内容同样计入 24000 字符总预算。
- 工具只读，不提供 `ADD / REVISE / RETIRE` 等写入能力。
- 模型不得把“搜索无结果”扩大解释为用户从未有过相关经历，只能说明当前记忆快照未找到匹配项。

语义分页优先于原始字符范围读取，避免在 claim 或 citation 中间切断。若任一工具仍返回大结果，直接复用现有 `ToolResultCompactor + ToolResultStore`：

```text
memory tool result
  -> inline 或 preview + resultRef
  -> PostgreSQL agent_content_blob 保存完整脱敏结果
  -> read_tool_result 按 offset/line range 读取 immutable run-local blob
```

通用 `read_tool_result` 继续使用现有同 run 权限校验和 `range-read-max-chars=8000`。字符 offset 只用于已经冻结的 tool result blob，不用于直接定位会随画像更新而变化的在线 Markdown 文档。

### 12.4 画像文档投影

`/me` 不直接展示 claim 列表。后端使用 `LearnerProfileDocumentProjector` 将当前 ACTIVE claim 投影为一篇只读的 Markdown 风格文档：

```text
ACTIVE claims + evidence summaries
  -> 固定产品主题与稳定排序
  -> heading / paragraph / supported-text 文档节点
  -> citation map
  -> 前端连续文档渲染
```

投影规则：

- 文档投影不是新的持久化事实表；读取时根据 ACTIVE revision、语言和 `projectorVersion` 确定性生成。
- 第一版不调用 LLM 生成标题、过渡句或整篇摘要。标题和中性连接文本只能来自受信模板。
- 内部 dimension 映射到“学习背景与目标”“学习方式与条件”“解题与实现”“复盘与成长”“知识点表现”等自然主题；空主题不渲染，界面不显示枚举名和模型层级。
- claim 文本按纯文本处理并转义 Markdown/HTML 元字符。一个 claim 对应一个 `SUPPORTED_TEXT` 句子和一个 citation group。
- 同一段落可以顺序容纳多个 claim 句子，但不能把不同 claim 改写成一个无法区分出处的复合句。
- 一个 citation group 可以包含同一 claim revision 的多条 Review 或消息 evidence；用户只看到一个论文式引用编号，展开后查看完整来源列表。
- 引用编号按当前文档首次出现顺序生成；citation ID 和 statement anchor 是文档内不透明标识，不把编号本身当作稳定业务主键。
- 主题按产品固定顺序排列；主题内按内部 scope 顺序、用户自述优先级、更新时间倒序和 claim revision ID 依次打破平局，不能依赖数据库默认顺序。
- `documentRevision` 由 `projectorVersion + locale + 有序 ACTIVE claim revision ID` 计算，用于 ETag、前端刷新和确定性测试。
- 没有 ACTIVE claim 时返回空文档状态，不生成“你还没有……”之类的模型推断正文。

若未来需要复制或导出纯 Markdown，只能从同一个结构化投影确定性序列化为正文加脚注；序列化结果仍不是 claim 的写入来源。

## 十三、API 设计

继续保留产品路径：

```http
GET /api/me/learner-profile
```

### 13.1 响应结构

破坏性升级后，接口返回结构化 Markdown 文档投影和 citation map，不再把 declared/general/tag claim 数组作为主展示契约：

```json
{
  "format": "MARKDOWN_DOCUMENT_V1",
  "projectorVersion": "v1",
  "locale": "zh-CN",
  "documentRevision": "sha256:4c9f...",
  "title": "我的学习画像",
  "blocks": [
    {
      "type": "HEADING",
      "level": 2,
      "text": "解题与实现"
    },
    {
      "type": "PARAGRAPH",
      "spans": [
        {
          "type": "SUPPORTED_TEXT",
          "text": "在多道题中容易遗漏边界条件。",
          "citationId": "citation-1",
          "anchorId": "memory-statement-101"
        },
        {
          "type": "TEXT",
          "text": " "
        },
        {
          "type": "SUPPORTED_TEXT",
          "text": "收到反馈后通常能在后续版本完成修正。",
          "citationId": "citation-2",
          "anchorId": "memory-statement-102"
        }
      ]
    }
  ],
  "citationMap": {
    "citation-1": {
      "displayNumber": 1,
      "statementRef": "st_01J4M7N9A1",
      "claimRevisionId": 101,
      "claimKey": "9ca2c624-3b41-4c76-9d1c-c2b39b65ba36",
      "originType": "SYSTEM_DERIVED",
      "sourceSummary": "基于 2 道题的 3 次正式 Review",
      "evidenceCount": 3,
      "previewEvidence": [
        {
          "type": "CODE_REVIEW",
          "reviewId": 123,
          "sessionId": 11,
          "planId": 7,
          "phaseIndex": 1,
          "problemSlug": "two-sum",
          "versionNo": 1,
          "totalScore": 6.8,
          "passed": false,
          "role": "OBSERVED",
          "createdAt": "2026-07-20T10:00:00Z"
        }
      ]
    },
    "citation-2": {
      "displayNumber": 2,
      "statementRef": "st_01J4M7N9A2",
      "claimRevisionId": 102,
      "claimKey": "1ff0175d-e101-4d9c-a34d-338280579b73",
      "originType": "SYSTEM_DERIVED",
      "sourceSummary": "基于同一道题的 2 个提交版本",
      "evidenceCount": 2,
      "previewEvidence": [
        {
          "type": "CODE_REVIEW",
          "reviewId": 123,
          "sessionId": 11,
          "planId": 7,
          "phaseIndex": 1,
          "problemSlug": "two-sum",
          "versionNo": 1,
          "totalScore": 6.8,
          "passed": false,
          "role": "OBSERVED",
          "createdAt": "2026-07-20T10:00:00Z"
        },
        {
          "type": "CODE_REVIEW",
          "reviewId": 124,
          "sessionId": 11,
          "planId": 7,
          "phaseIndex": 1,
          "problemSlug": "two-sum",
          "versionNo": 2,
          "totalScore": 9.2,
          "passed": true,
          "role": "RESOLVED",
          "createdAt": "2026-07-21T09:00:00Z"
        }
      ]
    }
  },
  "updatedAt": "2026-07-30T08:00:00Z"
}
```

文档节点第一版只允许：

- block：`HEADING / PARAGRAPH`。
- span：`TEXT / SUPPORTED_TEXT`。
- `SUPPORTED_TEXT` 必须引用存在于 `citationMap` 的 citation，且一个 span 只对应一个 claim revision。
- `TEXT` 只能承载空格、标点或受信模板文本，不能承载新的学习者判断。

`claimRevisionId`、`claimKey` 和 origin 等字段只服务于深链、后续反馈和诊断，前端不把它们显示成产品概念。标签名称可以作为“知识点表现”中的自然正文或小标题，但 `tag_id` 和 `TAG_MASTERY` 不进入用户界面。

### 13.2 为什么不直接返回一串自由 Markdown

普通 Markdown 字符串不能稳定表达“哪一个句子对应哪一组来源”。依赖文本匹配、字符 offset、原始 HTML 或渲染后 DOM 回查，会受到标点、转义、Markdown normalize、语言和组件升级影响。

因此 API 使用受限的 Markdown 文档 AST：

- 用户体验仍是一篇连续 Markdown 风格文档。
- 交互层直接依据 typed span 绑定颜色、引用、popover 和 drawer。
- 不允许模型输出任意 HTML、链接或 citation token。
- 当前 `MarkdownView` 不负责解析画像引用；画像使用专用 renderer，并复用其排版样式。

如后续增加导出接口，再由服务端把同一 AST 序列化为标准 Markdown 脚注格式，不能维护第二份自由正文。

### 13.3 证据载荷与权限

ACTIVE claim 扩大到数百条后，主画像 API 不再返回每个 citation 的全部 evidence：

- `citationMap` 返回 `evidenceCount` 和最多 2 条 `previewEvidence`，满足 hover/focus 预览。
- 打开依据抽屉时按需调用：

```http
GET /api/me/learner-profile/statements/{statementRef}/evidence?cursor={cursor}&limit=20
```

- evidence endpoint 按稳定时间顺序分页，单页最多 20 条，返回 `items + nextCursor`。
- `statementRef` 是当前用户 statement 的不透明引用，不使用文档内显示编号作为查询主键。
- 完整 Review、代码和 Review Markdown 继续由现有 Review detail API 按需加载。

所有接口身份只取当前认证用户。前端不能传入 user ID，也不能通过 statement ref、cursor 或 Review ID 读取其他用户证据。即使 statement ref 合法，服务端仍需同时校验其 ACTIVE revision、用户归属和当前可见状态。

## 十四、前端交互设计

### 14.1 组件结构

将画像展示从 `MyPage.tsx` 拆到独立目录：

```text
frontend/src/learner-profile/
  LearnerProfileSection.tsx
  LearnerProfileDocumentRenderer.tsx
  LearnerProfileSupportedText.tsx
  LearnerProfileCitationPopover.tsx
  LearnerProfileEvidenceDrawer.tsx
  LearnerProfileEvidenceTimeline.tsx
```

`frontend/src/types/api.ts` 和 `frontend/src/services/api.ts` 继续集中维护公共 API 类型和请求。

### 14.2 单篇画像的信息架构

`/me` 默认展示一篇连续、无分类 tab 的学习画像：

```text
我的学习画像

学习背景与目标
你当前主要使用 Java 进行算法练习。[1]

解题与实现
在多道题中容易遗漏边界条件。[2] 收到反馈后通常能在后续版本完成修正。[3]

知识点表现
二分查找的边界定义仍不稳定。[4]
```

界面规则：

- 不再展示 declared/general/tag 分类 tab、条目计数、revision number 或 claim 卡片。
- 自然主题标题只帮助阅读，不要求用户理解底层维度；没有内容的主题直接省略。
- 每个主要判断保留为一个独立句子。段落可以聚合多个句子，但不得跨 citation 合并语义。
- 长章节可以按自然段折叠或渐进渲染，但完整 ACTIVE claim 必须仍可在同一篇画像中展开访问，不能复用 Practice Chat 的 bootstrap token 预算裁掉用户可见内容。
- 更新时间作为文档级次要信息展示，不在每个句子旁重复。
- 页面加载、空态和错误态仍由 `LearnerProfileSection` 负责，文档本身不生成说明功能或操作方式的正文。

### 14.3 句子级引用

有依据的句子使用克制的强调色或下划线，并显示论文式引用编号 `[n]`：

- 颜色只表达“这里可以查看来源”，不表达证据强弱、正确概率或好坏评价。
- 引用编号始终可见，不能只依赖 hover；打印、截图和移动端都能看出该句有出处。
- 桌面端 hover 或键盘 focus 句子/编号时，显示轻量 popover，包含来源摘要和最多 2 条预览证据。
- 点击句子或编号打开完整依据抽屉；移动端轻触直接打开抽屉，不依赖悬浮能力。
- `Enter`/`Space` 可打开抽屉，popover 与 drawer 具备可读的 ARIA 关系和焦点返回。
- 用户自述句同样有引用，显示“来自你在题目聊天中的陈述”、时间和受限摘录；第一版可以没有消息深链。

一个句子对应一个 citation group，而不是每条 Review 都在正文生成一个编号。citation group 内再展示所有来源，避免连续多版本让正文充满脚注标记。

### 14.4 依据抽屉

点击引用后打开证据抽屉：

```text
AI 判断依据

Two Sum V1 · 6.8 分 · 未通过
观察到边界条件遗漏
[查看本次提交]

Two Sum V2 · 9.2 分 · 已通过
后续版本已修正
[查看本次提交]
```

抽屉打开后通过 statement evidence endpoint 懒加载完整来源列表，并使用 cursor 继续加载；popover 的预览数据不能被误当成完整证据集。抽屉按 evidence role 和时间展示来源；角色在界面翻译为“观察到”“后续仍存在”“后续已修正”“再次出现”“相反记录”等自然文案。UI 不显示模型 confidence 百分比，也不把 evidence grade 描述为正确概率。

### 14.5 精确 Review 深链

扩展现有提交历史路由：

```text
/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/submissions?review={reviewId}&from=learner-profile&profileAnchor={anchorId}
```

`PracticeSubmissionHistoryPage`：

- 读取 `review` 参数。
- 历史加载完成后自动选择对应 review。
- 在版本列表中高亮并滚动到可见区域。
- 加载现有 Review detail。
- `from=learner-profile` 时根据受限格式的 `profileAnchor` 返回 `/me` 原句位置，而不是固定返回 Practice Chat。

前端扩展现有 `learningPlanPracticeSubmissionsPath` 的 options 参数构造路径，不由后端返回拼接后的 URL。`profileAnchor` 只能使用 API 返回的文档内锚点，并在前端按固定前缀和字符集校验，不能作为任意跳转地址。

### 14.6 用户反馈预留

第一版只展示依据。数据状态和组件预留后续操作：

- “这条判断不准确” -> `REJECTED`。
- “暂时不要用于个性化” -> `SUPPRESSED`。
- “判断准确” -> 用户确认事件。

这些操作不在本阶段实现，但 citation metadata 保留稳定 `claimKey`，避免后续只能对整篇文档操作。聚合文档不能直接被用户整体“纠正”；反馈必须落回具体句子对应的 claim。

## 十五、安全与隐私

- 工具、证据校验、画像 API 和 Review detail 全部从受信认证或 Agent context 获取 user ID。
- 模型不能传入或扩大 user ID、tag ID、claim scope 和 problem slug。
- 画像 API 只返回受限文档节点、Review 摘要和截断后的消息摘录，不返回 raw code、normalized code、完整 Review Markdown 或完整消息正文。
- Practice Chat 记忆工具只能读取 run-local snapshot 中的 statement；`sectionRef / statementRef / cursor / resultRef` 均需验证当前用户、当前 run 和允许的 snapshot 范围。
- `read_tool_result` 只能读取当前 run 内由记忆工具产生的脱敏 blob，不能把任意 blob ID 当作记忆查询入口。
- claim 文本作为纯文本节点渲染；文档投影不接受任意 HTML、脚本、URL 或模型生成的 citation 标记，前端不得使用 `dangerouslySetInnerHTML`。
- `citationMap` 中的 Review/message 必须在返回前再次校验属于当前认证用户；不能仅依赖写入 evidence 时已经校验过。
- 点击证据后才通过已有受保护接口读取 Review detail。
- 日志只记录 claim 数、evidence 数、operation 数、dimension、tag ID 和失败码，不记录 claim 正文、代码和用户消息。
- Agent 最终请求快照仍可能包含 claim 正文和 Review 事实，继续受现有 30 天诊断保留约束；重构实施时需同步复核诊断快照权限和删除传播。

## 十六、破坏性迁移

当前共享 Flyway 最新版本为 V45。新设计使用新的全局唯一版本，例如：

```text
V46__rebuild_learner_memory_claims.sql
```

不能修改已存在的 V34 migration。

迁移步骤：

1. 部署前关闭 declared update、Review consumer 和 profile recall。
2. 删除 topic `learner-profile.code-review.v1` 的全部 PENDING/SUCCEEDED 消息。
3. `DROP TABLE learner_profile_entry`，不做数据备份或回填。
4. 创建 update run、claim revision 和两类 evidence 表。
5. 部署新 topic `learner-memory.code-review.v2`、新 Prompt 和新 JSON Schema。
6. 同步发布破坏性 API DTO、确定性画像 projector 和前端文档引用 UI。
7. 先开启写入和 shadow 验证，再开启 recall，最后开启前端可见入口。

由于项目尚未上线，不提供新旧双写、数据兼容视图或回滚回填。应用回滚时保留新表，可关闭全部记忆功能，不恢复旧画像数据。

## 十七、容量、召回与成本边界

建议配置：

```yaml
algo-mentor:
  learner-memory:
    declared-update:
      enabled: false
      max-stale-retries: 1
    code-review-consumer:
      enabled: false
      batch-size: 5
      max-distinct-problems: 10
      max-stale-retries: 1
    storage:
      active-soft-limit: 500
      active-hard-limit: 1000
      declared-scope-active-limit: 10
      general-scope-active-limit: 10
      tag-scope-active-limit: 5
      claim-text-max-chars: 600
      claim-text-target-max-chars: 300
    update-agent:
      max-steps: 4
      max-tool-calls: 3
      max-history-versions: 5
      max-diff-chars: 8000
    recall:
      practice-chat:
        enabled: false
        bootstrap-token-budget: 1000
        bootstrap-token-hard-limit: 1500
        bootstrap-max-direct-claims: 8
        max-memory-tool-calls: 3
        max-memory-range-reads: 2
        search-max-results: 20
        section-read-max-statements: 20
        evidence-read-max-items: 20
        memory-tool-result-max-chars: 8000
        memory-tool-results-total-max-chars: 24000
```

`batch-size=5` 和 `max-distinct-problems=10` 可以继续作为代码业务契约，不一定开放环境变量；其余预算通过受控配置映射。`bootstrap-token-budget` 只约束首次系统提示词，不与 ACTIVE claim 容量绑定。通用 `agent.tool-result.range-read-max-chars=8000` 继续作为 `read_tool_result` 的最终单次范围上限。

正常成本上限：

- 每 5 条正式 Review 最多一个背景画像 run。
- 每个画像更新 run 最多 4 个模型步骤和 3 次历史探索工具调用。
- 不自动分析未满批数据。
- 普通 Practice Chat 首次只注入索引和最多 8 条直接命中 claim；模型仅在任务需要时调用记忆工具。
- 每个 Practice Chat run 最多 3 次业务记忆工具调用和 2 次记忆结果范围读取；工具调用会增加模型 step 和延迟，因此当前题目直接命中的高价值记忆仍由服务端自动注入。
- 长期存储不因 Prompt token 预算裁剪；达到软上限通过既有批次治理，达到硬上限只阻止新增 claim。

## 十八、可观测性

新增低基数指标：

- `learner_memory_update_run_total{trigger,status}`。
- `learner_memory_operation_total{action,kind,dimension}`，tag 不作为 label。
- `learner_memory_tool_call_total{purpose,tool,status}`，`purpose` 仅允许 `UPDATE / RECALL`。
- `learner_memory_evidence_count{pattern,grade}`。
- `learner_memory_invalid_output_total{reason}`。
- `learner_memory_claim_active_count{kind,dimension}`。
- `learner_memory_active_limit_total{level}`，`level` 仅允许 `SOFT / HARD`。
- `learner_memory_recall_count{scenario,kind}`。
- `learner_memory_bootstrap_token_estimate{scenario}`。
- `learner_memory_bootstrap_direct_claim_count{scenario}`。
- `learner_memory_bootstrap_trimmed_total{scenario}`。
- `learner_memory_recall_tool_result_chars{tool}`。
- `learner_memory_recall_range_read_total{status}`。
- `learner_memory_profile_projection_total{status,projector_version}`。
- `learner_memory_profile_citation_count` 分布指标，不使用 user ID、claim key 或 tag 作为 label。

运行 metadata 记录：

- 横向窗口题目数。
- 初始 ACTIVE claim 数。
- 工具调用数和读取历史版本数。
- 输出 operation 数。
- 最终 evidence 数。
- Practice Chat 的 `documentRevision`、索引 section 数、直接命中 claim 数和 bootstrap token 估算。
- 业务记忆工具调用数、返回字符数、cursor 翻页数和 `read_tool_result` 范围读取数。
- token、耗时、provider、model、Prompt version 和 schema version。

## 十九、测试与验收

### 19.1 数据库与 repository

- kind、dimension、tag scope 全矩阵约束。
- 同一 `claim_key` 当前 revision 唯一和 revision_no 唯一。
- ACTIVE 文本 hash 去重。
- `claim_text` 600 字符硬上限，以及 declared/general/tag scope 的 `10 / 10 / 5` ACTIVE 上限。
- 用户 500 条软上限只记录治理状态，1000 条硬上限只阻止 `ADD`；历史 revision 不计入。
- Review/message evidence FK 和 role 约束。
- Review `ON DELETE RESTRICT` 行为。
- V34 存在时执行 V46 的全量升级测试。

### 19.2 更新 Agent 与历史工具

- 画像更新 Agent Definition 只暴露 trajectory、Review evidence 和 code diff 三项业务只读工具。
- 工具不能跨用户、跨窗口 slug 或越过版本/字符上限。
- 同题 trajectory 顺序和 delta 确定性。
- 非同题 diff 请求拒绝。
- 结构化输出未知字段、非法 action、重复 claim、伪造证据、非法 tag 整批拒绝。

### 19.3 Practice Chat 启动索引与记忆工具

- 相同 run-local snapshot 生成稳定 `documentRevision`、section index 和直接命中顺序。
- bootstrap 默认不超过 1000 token，硬上限 1500；claim 只按完整项裁剪，不出现半句截断。
- 当前题目命中、用户自述和通用观察的优先级正确，直接注入不超过 8 条。
- `search_learner_memory / read_learner_memory_section / get_learner_memory_evidence` 均不接受 user ID，并只能读取当前 run snapshot。
- section/statement cursor 稳定分页，无重复、无遗漏，伪造和跨用户 ref 返回不存在或无权限。
- 单工具 20 条、单结果 8000 字符、每 run 3 次业务调用、2 次记忆范围读取和总 24000 字符边界生效。
- 大结果返回 preview + `resultRef`，`read_tool_result` 只能在同 run 读取最多 8000 字符范围。
- 并发画像更新不改变当前 run 工具结果，下一个 run 才看到新 revision。
- eval 覆盖“已有直接命中不调用工具”“涉及未展开历史必须查询”“无关问题不查询”“长结果继续范围读取”和“搜索无结果不臆断用户从未经历”。

### 19.4 Claim 更新

- `ADD / CONFIRM / REVISE / RETIRE` 正确版本切换。
- 批量全有或全无。
- 用户锁和 snapshot token 防陈旧覆盖。
- 模型调用不持有数据库事务。
- Code Review Agent 不能修改 declared claim。
- scope ACTIVE 数量上限、用户软硬上限和达到硬上限后仍允许 `REVISE / CONFIRM / RETIRE`。

### 19.5 业务语义样例

1. 同题 V1 有错误、V2 修正：生成成长 claim，不保留为当前稳定弱点。
2. 同题 V1-V3 持续错误：允许形成纵向持续 claim，但不能声称跨题重复。
3. 两道不同题出现同类错误：允许生成跨题错误模式 claim。
4. 一道题提交五版：标签掌握广度仍为一道题。
5. 已改善后再次出现：生成或修订回退 claim，并关联完整时间线。
6. 证据不足：输出空 operations，run 为 `NO_CHANGE`。

### 19.6 API 与前端

- 画像 API 只把当前用户 ACTIVE claim 投影进文档，RETIRED/SUPPRESSED/REJECTED 不出现。
- 相同 locale、projector version 和 ACTIVE revision 集合生成完全相同的 block 顺序、引用编号与 `documentRevision`。
- 每个 `SUPPORTED_TEXT` 都引用存在的 citation；citation 不孤悬、不跨 claim 复用，`TEXT` 节点不承载模型判断。
- 主画像 API 每个 citation 只返回 evidence 总数和最多 2 条预览；statement evidence endpoint 使用 cursor 懒加载完整摘要列表。
- Review evidence 摘要和消息摘录字段完整，但不包含代码、完整 Review Markdown 或完整消息正文。
- claim 中的 Markdown/HTML/链接样式输入按纯文本渲染，覆盖 XSS 和意外链接测试。
- 单篇文档、空态、错误态、自然主题省略和多 evidence 展示正确，界面不显示 dimension、claim 类型、revision 或 evidence grade。
- 引用编号始终可见；桌面 hover/focus 展示来源预览，点击、键盘和移动端轻触打开正确依据抽屉。
- 深链自动选中指定 Review 版本。
- 其他用户 Review ID 返回不存在或无权限。
- 从 Review 返回时恢复 `/me` 的原句锚点和滚动位置。

### 19.7 端到端验收

完整链路：

```text
5 条正式 Review
  -> v2 queue 满批
  -> 横向窗口
  -> Agent 按需查询历史/证据/diff
  -> 严格 operations 校验
  -> claim revision + evidence 原子写入
  -> Practice Chat 注入记忆索引 + 当前题目直接命中
  -> Agent 按需 search/read/evidence
  -> 大结果按需 read_tool_result 范围读取
  -> projector 生成 Markdown 风格画像文档 + citation map
  -> /me 渲染单篇画像
  -> 点击句子引用跳转指定 Review
```

## 二十、模块组织建议

后端按职责拆分，避免继续把所有代码放在 `profile` 根包：

```text
mentor-application/.../profile/
  claim/
    model/
    service/
    repository/
  evidence/
    model/
    service/
  declared/
    agent/
    service/
  review/
    agent/
    tool/
    service/
  recall/
    index/
    snapshot/
    tool/
    service/
  projection/

mentor-api/.../profile/
  controller/
  model/
  repository/
  service/
```

公共 API 路径、JSON 字段、tool 名称、metadata key、状态值、evidence role、document format、block/span type 和路由 query key 均使用常量或枚举。

## 二十一、实施顺序

1. 新建 V46 破坏性迁移和 claim/evidence domain/repository。
2. 重写用户自述为原子 claim 操作。
3. 实现 Review trajectory、evidence、diff 三项工具。
4. 重写 Code Review 画像 Agent Definition、Prompt、JSON Schema 和 mapper。
5. 实现用户锁、snapshot 和批量 claim apply。
6. 实现 run-local 记忆快照、确定性 bootstrap index、直接命中选择和 Practice Chat 三项只读记忆工具；接入现有 tool result compaction 与 `read_tool_result`。
7. 实现确定性 `LearnerProfileDocumentProjector`，破坏性更新 `/api/me/learner-profile` 为 document blocks + citation map，并增加 statement evidence 分页接口。
8. 拆分前端画像组件，移除分类 tab/claim 列表，增加句子级 citation popover、懒加载 evidence drawer 和 Review 深链。
9. 完成 PostgreSQL IT、Agent contract、前端交互和端到端测试。
10. 删除旧 profile entry、旧 prompt、旧 mapper、旧 v1 topic 代码和已失效文档描述。

## 二十二、审核重点

请重点确认以下设计选择：

1. 是否接受“一个 dimension/tag 下多个原子 claim”，而不是继续维护一个聚合条目。
2. 是否接受底层保留原子 claim，但用户界面只展示一篇 Markdown 风格画像，不暴露 dimension、claim 类型和 revision。
3. 是否接受第一版 API 使用受限 document blocks/spans + citation map，而不是返回自由 Markdown 字符串或 claim 数组。
4. 是否接受一个主要判断对应一个句子和一个 citation group，同段可放多个句子但不跨 claim 改写合并。
5. 是否接受第一版由确定性 projector 组织画像，不增加一次 LLM 整篇重写调用。
6. 是否接受 claim 600 字符硬上限、300 字符生成目标、scope `10 / 10 / 5` 上限，以及用户 ACTIVE `500 / 1000` 软硬上限。
7. 是否接受 Practice Chat token 预算只约束 bootstrap index 和最多 8 条直接命中，不再充当全部长期记忆上限。
8. 是否接受 Practice Chat 第一版提供 search、section read、evidence read 三项记忆工具，并复用 `read_tool_result` 处理大结果范围读取。
9. 是否接受第一版不引入向量数据库，使用结构化过滤、确定性排序和当前用户范围内的 PostgreSQL 文本匹配支撑单用户最多 1000 条 ACTIVE claim。
10. Code Review 更新 Agent 是否第一版同时提供 trajectory、Review evidence 和 code diff 三个工具。
11. 是否接受只有标签评价可以使用 `SINGLE_REVIEW`，通用观察必须具备跨题或纵向证据。
12. 是否接受画像 API 每个 citation 只返回最多 2 条 evidence 预览，完整列表通过 statement evidence endpoint 懒加载。
13. 是否接受 Review evidence 使用 `ON DELETE RESTRICT`，未来删除提交或 Session 前必须先处理相关 memory claim。
14. 是否接受第一版只展示来源，不同步实现用户拒绝和抑制操作。
15. 至少一次批处理的业务幂等键、最大失败次数、告警接收方和 FAILED 运维恢复权限；这不会改变满 5 条和非实时策略。
