# 题目复习卡与题目笔记重构实施计划

> 状态：待实施  
> 日期：2026-07-24  
> 适用范围：错题本、复习中心、题目长期笔记、FSRS 调度  
> 前提：项目尚未上线，允许删除本地历史复习数据、旧接口、旧代码和旧表，不提供兼容层。

## 0. 结论

复习中心重构为一张以 `userId + problemSlug` 为业务身份的单页复习卡：

- **题面区：题目与直接自评**。默认展示完整题面，用户可以直接选择 `AGAIN / HARD / GOOD / EASY`，不要求填写回答或查看笔记。
- **笔记折叠区：题目级用户笔记**。放在完整题面下方并默认折叠，展开后展示固定结构化笔记；笔记不属于学习计划、训练会话或某一次复习。
- **调度：用户自评 + FSRS**。评级是复习主流程，也是 FSRS 的唯一输入；填写思路、算法和复杂度只是用户可选的笔记维护行为。
- **AI 完全退出复习链路**。删除 AI 卡片生成、后台预生成、AI 复述判定、评价开关、临时评价表、配额和相关缓存。
- **存储边界拆开**。复习调度、每次复习记录、题目长期笔记分别拥有独立表，不再把题目笔记塞进复习调度聚合表。

## 1. 产品模型

### 1.1 单页主流程：题目与直接自评

页面按从上到下的单列工作台布局组织：

1. 顶部工具栏：返回复习中心、题目标题、难度、当前进度、FSRS 状态。
2. 完整题面：进入当前卡片后默认加载并展示，不需要点击“查看题面”。
3. 题目笔记折叠区：紧跟在完整题面之后，默认折叠。
4. 复习记录折叠区：位于题目笔记之后，可展开最近的评级、复习时间和间隔变化。
5. 底部固定评级区：始终提供 `AGAIN / HARD / GOOD / EASY` 和对应间隔预览。

页面不要求输入任何文字。用户可以只看题目、在脑中回忆并直接评级，这是默认且最短的复习路径。

### 1.2 题面下方的笔记折叠区

折叠状态只展示：

1. “我的题目笔记”；
2. “已有笔记”或“暂无笔记”；
3. 最近更新时间；
4. 展开图标。

折叠标题不得预览算法、数据结构、复杂度、核心思路或其他笔记正文，避免在用户主动展开前泄露提示。

展开后展示：

1. 固定结构化题目笔记：核心思路、数据结构、算法、复杂度、边界与易错点。
2. 自由笔记正文：记录实现细节、自己的理解、反复出错点等补充内容。
3. 保存状态：显式保存、未保存提示和 revision 冲突处理。
4. 收起入口；收起不能清空尚未保存的编辑内容。

若当前题还没有题目笔记，展开后展示空表单。笔记保存与复习评级是两条独立操作：保存笔记不推进 FSRS，评级也不要求已经保存笔记。切换下一题前若存在未保存修改，必须提示保存或放弃。

### 1.3 评级规则

- `AGAIN`：基本没有想起来。
- `HARD`：虽然想起来了，但回忆过程费力或不够完整。
- `GOOD`：独立回忆出主要思路，只有少量遗漏。
- `EASY`：快速、完整且无需辅助地回忆出来。
- 所有评级都允许在没有填写题目笔记的情况下直接提交。
- 系统不跟踪用户是否查看过笔记，也不推断或修正用户评级。
- 用户评级是 FSRS 的唯一调度输入，不再存在 AI 建议评级。

### 1.4 页面状态机

```text
LOADING
  -> READY
  -> SUBMITTING
  -> CONFIRMED
  -> NEXT_CARD | COMPLETED
```

- `READY` 中可以直接评级，也可以展开或收起题目笔记；折叠状态只是局部展示状态，不是主流程阶段。
- 保存题目笔记后仍停留在当前卡片，评级栏始终可用。
- `SUBMITTING` 必须禁止重复点击；服务端同时使用幂等键兜底。
- 切换下一题时保留已经保存的题目笔记，不维护本次回答草稿。

## 2. 领域与作用域

### 2.1 业务身份

```text
problem
  ^ problem_slug
  |
  +-- problem_review_card       用户对该题的复习调度聚合
  |      |
  |      +-- problem_review_attempt  每次复习的不可变记录
  |
  +-- user_problem_note         用户对该题的长期笔记
```

- `problem_review_card` 和 `user_problem_note` 都以 `(user_id, problem_slug)` 唯一。
- `user_problem_note` 不引用 `plan_id`、`phase_index`、`practice_session_id` 或 `review_card_id`。
- 题目笔记可从题库详情、训练聊天页和复习卡题面下方访问；这些页面使用同一个 API 和同一份数据。
- 删除复习卡或取消调度不删除题目笔记；删除题目笔记也不影响 FSRS 调度。

### 2.2 题目笔记结构

题面下方折叠区中的结构化题目笔记使用 `ProblemSolutionOutlineV1`：

```json
{
  "schemaVersion": 1,
  "coreIdea": "使用滑动窗口维护无重复区间",
  "dataStructures": ["HASH_MAP"],
  "customDataStructures": [],
  "dataStructureNotes": "记录字符最后一次出现的位置，帮助左边界向前收缩",
  "algorithms": ["SLIDING_WINDOW"],
  "customAlgorithms": [],
  "algorithmNotes": "右边界逐步扩张，遇到重复字符时更新左边界",
  "timeComplexity": {
    "key": "O_N",
    "customText": null
  },
  "spaceComplexity": {
    "key": "OTHER",
    "customText": "O(k)"
  },
  "edgeCases": "空字符串、重复字符、窗口左边界不能回退"
}
```

`dataStructureNotes` 和 `algorithmNotes` 是对应类别的可选补充说明，只在至少选择一个数据结构或算法时填写。它们作为 `outline_json` 的向后兼容字段保留在 `schemaVersion: 1` 中；旧 JSON 缺少字段时按空字符串处理，不新增数据库列，也不需要数据回填。

首版固定数据结构选项：

`ARRAY`、`HASH_MAP`、`LINKED_LIST`、`STACK`、`QUEUE`、`HEAP`、`TREE`、`GRAPH`、`TRIE`、`UNION_FIND`、`OTHER`。

首版固定算法选项：

`TWO_POINTERS`、`SLIDING_WINDOW`、`BINARY_SEARCH`、`DFS`、`BFS`、`BACKTRACKING`、`GREEDY`、`DYNAMIC_PROGRAMMING`、`PREFIX_SUM`、`SORTING`、`MONOTONIC_STACK`、`DIJKSTRA`、`OTHER`。

首版固定复杂度选项：

`O_1`、`O_LOG_N`、`O_N`、`O_N_LOG_N`、`O_N2`、`O_N3`、`O_2N`、`OTHER`。

复杂度选择 `OTHER` 时 `customText` 必填，以覆盖 `O(m+n)`、`O(V+E)`、`O(nk)` 等常见表达。

这些 key 是前后端公共契约：后端使用枚举或常量统一校验，前端使用集中定义的 TypeScript 联合类型和本地化标签，不允许散落字符串字面量。

## 3. 数据库重建

### 3.1 迁移策略

新增破坏性迁移 `V40__rebuild_problem_review_card.sql`。不修改已经存在的 V19/V20/V22/V23，以避免本地 Flyway checksum 分裂；V40 按依赖顺序删除旧表并创建目标表。

允许删除现有本地复习数据，不做任何数据回填：

1. 删除 `review_recall_evaluation`。
2. 删除 `review_log`。
3. 删除 `mistake_note`。
4. 从 `user_review_preference` 删除 `ai_suggestion_enabled`。
5. 创建 `problem_review_card`。
6. 创建 `problem_review_attempt`。
7. 创建 `user_problem_note`。

不要使用宽泛 `CASCADE`；迁移必须显式按依赖顺序删除准确目标。

### 3.2 `problem_review_card`

建议字段：

```text
id
user_id
problem_slug
source                     REVIEW_FAILED | REVIEW_PASSED | USER_MARKED
source_detail_json
repetitions
interval_days
fsrs_state                 LEARNING | REVIEW | RELEARNING
fsrs_step
fsrs_stability
fsrs_difficulty
due_at
lapses
last_reviewed_at
last_rating                AGAIN | HARD | GOOD | EASY
archived
created_at
updated_at
```

约束和索引：

- 唯一约束 `(user_id, problem_slug)`；
- `user_id` 外键指向 `auth_users(id)`；
- `problem_slug` 外键指向 `problem(slug)`，默认限制删除；
- 待复习部分索引 `(user_id, due_at) WHERE archived = FALSE`；
- 不保留 `origin_plan_id`、`origin_phase_index`、`origin_practice_session_id`；
- 不保留 `user_note_persistent` 和任何 `pending_card_*` 字段；
- 删除 `AI_WEAK` 来源。

### 3.3 `problem_review_attempt`

每次评级成功追加一条不可变记录。复习流水只记录用户评级和 FSRS 状态变化，不保存一份强制的“本次回答”：

```text
id
review_card_id
user_id
client_attempt_id          UUID
rating
scheduling_before_json
scheduling_after_json
reviewed_at
```

约束和索引：

- 唯一约束 `(user_id, client_attempt_id)`，保证重复提交幂等；
- `review_card_id` 外键删除时级联删除复习流水；
- 调度快照必须是 JSON object；
- 索引 `(review_card_id, reviewed_at DESC)` 和 `(user_id, reviewed_at DESC)`；
- 不再保存 `card_variant`、`card_prompt_json`、`ai_judgment_json`、`rating_source`、`review_mode`、`recall_message_id`。

### 3.4 `user_problem_note`

题目长期笔记独立建模：

```text
id
user_id
problem_slug
outline_json               ProblemSolutionOutlineV1，可为空纲要
note_markdown              自由笔记正文，首版限制 10000 字符
revision                   乐观锁版本
created_at
updated_at
```

约束和索引：

- 唯一约束 `(user_id, problem_slug)`；
- `user_id` 外键指向 `auth_users(id)`；
- `problem_slug` 外键指向 `problem(slug)`；
- 笔记写接口携带 `expectedRevision`，版本冲突返回 HTTP 409，避免训练页和复习页多标签覆盖；
- 笔记内容不进入复习调度、AI Prompt 或学习计划数据。

## 4. 后端目标结构

将当前平铺的 `mentor.application.review` 按职责拆分：

```text
review/card/        复习卡聚合、队列、来源入库
review/attempt/     用户评级、提交事务、历史查询
review/note/        题目级笔记模型、服务和 repository
review/schedule/    FSRS 状态、评级和调度
review/preference/  保留率、每日上限、fuzzing
review/catalog/     题目元数据与完整题面读取端口
```

核心服务：

- `ReviewCardService`：创建/归档用户题目复习卡、处理 Code Review 观察事件。
- `ReviewQueueService`：根据 FSRS 状态与每日限额返回队列。
- `ReviewAttemptService`：幂等提交用户评级、原子更新 FSRS 并写流水。
- `UserProblemNoteService`：按 `userId + problemSlug` 查询和乐观锁 upsert。
- `FsrsReviewSchedulerService`：保留现有算法实现，移动到 `schedule` 包并收敛输入输出。

`ReviewAttemptService.submit()` 必须在一个事务中完成：

1. 锁定当前用户的 `problem_review_card` 行；
2. 按 `clientAttemptId` 检查是否已提交；
3. 计算 FSRS 新状态；
4. 插入 `problem_review_attempt`；
5. 更新 `problem_review_card` 调度状态；
6. 返回已落库的 attempt 和下次到期时间。

## 5. API 契约

### 5.1 复习卡与队列

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/review-cards` | 复习中心列表与筛选 |
| `POST` | `/api/review-cards` | 按 `problemSlug` 加入复习 |
| `PATCH` | `/api/review-cards/{cardId}/archive` | 归档或恢复 |
| `GET` | `/api/review-sessions/queue` | 获取当前待复习队列 |
| `GET` | `/api/review-cards/{cardId}/context` | 题面、题目笔记、最近记录和间隔预览 |
| `POST` | `/api/review-cards/{cardId}/attempts` | 保存用户评级并推进 FSRS |
| `GET` | `/api/review-cards/{cardId}/attempts` | 查询历史复习记录 |

`GET /context` 的完整题面直接来自题库服务，不写入复习卡表或题目笔记表。

### 5.2 题目笔记

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/problems/{problemSlug}/note` | 获取当前用户的题目笔记，不存在返回空结构 |
| `PUT` | `/api/problems/{problemSlug}/note` | 创建或按 revision 更新题目笔记 |
| `DELETE` | `/api/problems/{problemSlug}/note` | 删除题目笔记，不影响复习卡 |

题目笔记 API 不接受 `planId`、`phaseIndex`、`sessionId` 或 `reviewCardId`。

### 5.3 提交请求

```json
{
  "clientAttemptId": "474d9564-535c-4bd6-af45-52992b7d7124",
  "rating": "GOOD"
}
```

### 5.4 删除的旧接口

删除以下接口，不保留转发或 deprecated 版本：

- `/api/mistake-notes/{id}/card`
- `/api/mistake-notes/{id}/problem-statement`
- `/api/mistake-notes/{id}/recall`
- `/api/mistake-notes/{id}/recall/evaluation`
- `/api/mistake-notes/{id}/recall/confirm`
- `/api/mistake-notes/{id}/recall/rating`
- 设置接口中的 `aiSuggestionEnabled`

## 6. 前端改造

### 6.1 页面结构

复用训练聊天工作台的三段式页面骨架：

```text
顶部工具栏
中间可滚动内容：完整题面 -> 题目笔记折叠区 -> 复习记录折叠区
底部固定操作区：始终可用的评级按钮
```

- 桌面和移动端均使用上下布局，不做左右分栏。
- 完整题面默认展示，题面过长时由中间内容区统一滚动。
- 题目笔记必须位于完整题面之后，默认折叠，不能放在题面之前。
- 展开编辑题目笔记时展示未保存状态、保存按钮和 revision 冲突提示。
- 收起题目笔记时保留本地未保存内容；切换下一题前处理未保存状态。
- 评级按钮始终显示间隔预览；评级成功后才能进入下一题。

### 6.2 组件拆分

建议新增：

```text
ReviewCardWorkbench
ReviewProblemContent
ProblemNoteDisclosure
ProblemSolutionOutlineForm
ProblemNoteEditor
ReviewAttemptHistory
ReviewRatingBar
```

`ProblemNoteEditor` 以 `problemSlug` 为唯一业务参数，可复用于：

- 复习卡题面下方的折叠区；
- `PracticeChatWorkbench`；
- 题库详情页。

不得让该组件接收或依赖学习计划、训练 session 或复习 card ID。

### 6.3 笔记持续性

- 题目笔记是 `(userId, problemSlug)` 下的同一份长期数据，每次复习直接读取，不需要“继承上一次”。
- 复习流水只保存评级历史，不保存每次临时填写的一份思路副本。
- 用户修改并保存题目笔记后，后续复习、训练聊天页和题库详情页直接看到更新后的内容。
- 首版不提供题目笔记版本历史、从历史 attempt 恢复或自动合并。

### 6.4 设置页

- 删除“AI 评价建议”开关和所有相关文案、类型、保存逻辑与测试。
- 保留 FSRS 保留率、每日新卡/学习卡/复习卡上限、最大间隔和 fuzzing 配置。

## 7. 必须删除的旧实现

### 7.1 AI 卡片生成

删除：

- `CardGenerationOutcome`
- `CardVariant`
- `ReviewCardCache`
- `ReviewCardJsonSchema`
- `ReviewCardPrompt`
- `ReviewCardScaffold`
- `ReviewCardProperties`
- 旧 `ReviewCardService`
- `ReviewCardPregenerationService`
- `NoopReviewCardPregenerationService`
- `RuleBasedCardComposer`
- `REVIEW_CARD_GEN` 配额、配置和环境变量

### 7.2 AI 复述评价

删除：

- `RecallJudgeService`
- `RecallJudgeJsonSchema`
- `RecallJudgeOutcome`
- `RecallJudgment`
- `ReviewRecallEvaluation`
- `ReviewRecallEvaluationRepository`
- `ReviewRecallEvaluationResult`
- API/MyBatis 层全部 evaluation mapper、row、repository 和 DTO
- `AiRunSource.RECALL_JUDGE`
- `AiRunSource.REVIEW_CARD_GENERATION`

同步更新 AI 治理测试、管理员文档和用量场景说明，确认复习功能不再注册直接 completion。

### 7.3 旧错题与复习模型

删除后由新模型替代：

- `MistakeNote`、`MistakeNoteRepository`、`MistakeNoteService`
- `ReviewLogEntry`、`ReviewLogRepository`
- `ReviewMode`、`RatingSource`
- `ReviewCardDetail`、旧 `ReviewRecallHistoryItem`
- `MistakeNoteController` 及旧 DTO、mapper、repository
- 前端 `ReviewCard`、`RecallEvaluationResult`、`RecallConfirmResult` 等旧类型
- `evaluateRecall`、`confirmRecall`、`rateRecall`、`submitRecall` 等旧 API 函数

保留但重命名或移动：

- `FsrsReviewSchedulerService`
- `SchedulingState`
- `ReviewRating`
- `ReviewSeedPolicy` 及 Code Review 入卡逻辑
- 复习队列、摘要和偏好中的非 AI 部分

## 8. 实施任务

### 阶段 A：契约与破坏性迁移

- [ ] RC-01：新增题目笔记纲要、题目笔记和直接评级提交 DTO。
- [ ] RC-02：定义数据结构、算法、复杂度公共 key 和校验规则。
- [ ] RC-03：编写 `V40__rebuild_problem_review_card.sql`，删除三张旧表并创建三张新表。
- [ ] RC-04：从 `user_review_preference` 删除 `ai_suggestion_enabled`。
- [ ] RC-05：补充干净数据库迁移与已有 V39 数据库升级测试。

### 阶段 B：后端新主链路

- [ ] RC-06：实现 `review/card` 聚合、repository、MyBatis mapper 和队列查询。
- [ ] RC-07：实现 `review/note` 独立领域、乐观锁 upsert、删除和权限校验。
- [ ] RC-08：实现 `review/attempt` 评级历史和调度快照查询。
- [ ] RC-09：实现一次提交事务和 `clientAttemptId` 幂等。
- [ ] RC-10：接入现有 FSRS 调度与间隔预览。
- [ ] RC-11：让 Practice Code Review 观察事件只创建或更新复习卡，不写题目笔记。
- [ ] RC-12：实现新 Controller 和响应映射，删除旧复述 API。

### 阶段 C：前端单页复习卡

- [ ] RC-13：新增题目笔记结构化表单、固定选项和自定义项输入。
- [ ] RC-14：按聊天工作台骨架重写复习会话页，完整题面默认展示。
- [ ] RC-15：实现完整题面、题面下方默认折叠的题目笔记和复习记录折叠区。
- [ ] RC-16：实现题目笔记保存、未保存状态和 revision 冲突处理。
- [ ] RC-17：实现评级栏、间隔预览、提交幂等状态和下一题。
- [ ] RC-18：新增可复用 `ProblemNoteEditor`，接入训练聊天页和题库详情页。
- [ ] RC-19：删除 AI 建议设置和旧前端 API/types/tests。

### 阶段 D：删除旧代码与配置

- [ ] RC-20：删除 AI 卡片生成、预生成、缓存、配额、指标和配置。
- [ ] RC-21：删除 AI 复述评价、临时 evaluation 持久化和 AI run source。
- [ ] RC-22：删除旧 `mistake_note`/`review_log` Java 与 MyBatis 实现。
- [ ] RC-23：删除旧页面组件、样式、测试夹具和文案。
- [ ] RC-24：更新 `application.yml`、`.env.example`、AI 治理用量场景和管理员文档。
- [ ] RC-25：删除或重写已失效的旧复习设计文档并更新 `docs/code-index.md`。

## 9. 测试与验收

### 9.1 后端

- 同一用户同一题只能有一张 `problem_review_card` 和一份 `user_problem_note`。
- 不同用户对同一题的复习卡、题目笔记和历史严格隔离。
- 题目笔记不依赖复习卡存在，可在训练页先创建。
- 删除或归档复习卡不删除题目笔记。
- 重复 `clientAttemptId` 不重复推进 FSRS，返回第一次提交结果。
- 所有评级都允许在没有题目笔记的情况下直接提交。
- note revision 冲突返回 409，不能静默覆盖。
- 复习 attempt 插入失败时 FSRS 状态不更新，FSRS 更新失败时 attempt 不落库。
- Code Review 入卡不创建或修改题目笔记。

### 9.2 前端

- 进入复习默认看到完整题面和四个评级按钮。
- 用户无需填写或展开题目笔记即可完成评级。
- 题目笔记位于完整题面下方，默认折叠且折叠标题不泄露内容。
- 展开后可以查看和编辑同一题目长期笔记。
- 收起题目笔记不会丢失未保存内容，切换下一题前会处理未保存状态。
- 题目笔记保存后在复习页、训练聊天页和题库详情页一致展示。
- 评级按钮正确展示间隔，重复点击只产生一次提交。
- 关闭或不存在 AI 服务不影响任何复习功能。
- 设置页不再出现 AI 评价开关。

### 9.3 删除验收

以下搜索必须无产品代码命中，只允许新设计文档在“删除清单”中提及：

```bash
rg 'ReviewCardPregeneration|RecallJudge|ReviewRecallEvaluation|REVIEW_CARD_GEN' backend frontend
rg 'aiSuggestionEnabled|recall/evaluation|recall/confirm' backend frontend
rg 'pending_card_|card_prompt_json|ai_judgment_json' backend frontend
```

数据库验收：

- 不存在 `mistake_note`、`review_log`、`review_recall_evaluation`；
- 不存在 `user_review_preference.ai_suggestion_enabled`；
- 存在 `problem_review_card`、`problem_review_attempt`、`user_problem_note`；
- 全量 Flyway 从空 PostgreSQL 可执行成功；
- 从当前 V39 本地数据库升级到 V40 可执行成功。

建议验证命令：

```bash
make backend-test
make frontend-test
make build
```

## 10. 发布边界

项目尚未上线，本次不提供：

- 旧复习数据迁移；
- 旧 API 兼容；
- 旧 URL 重定向；
- AI 评价或 AI 检查入口；
- 官方题解或旧代码进入题目笔记折叠区；
- 题目笔记版本历史；
- 富文本编辑器和图片附件。

本次完成后，复习中心的唯一核心闭环是：**查看题面 -> 用户直接自评 -> FSRS 调度**；题面下方默认折叠的题目笔记是可选的长期学习辅助，不是完成复习的前置条件。
