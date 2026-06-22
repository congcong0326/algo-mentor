# 学习计划 Agent 研发设计

## 背景

`algo-mentor` 已经具备 Agent loop、SSE、工具调用、运行锁、取消、上下文组装、结构化输出和题库 Agent 工具。当前更适合用一个真实业务闭环验证这些能力，而不是继续脱离场景补框架能力。

第一版学习计划场景选择“会话式计划生成 + 独立确认保存”：用户先提交基础表单，Agent 通过多轮追问补齐缺口，在信息足够时自动生成阶段级学习计划草案。草案经用户确认后保存为正式学习计划。计划执行、每日任务、进度追踪和编辑不在第一版范围内。

## 目标

- 支持用户选择或表达学习计划意图，并提交基础学习信息。
- 支持 Agent 多轮追问缺失或模糊信息，每轮只追问一个关键问题。
- 在必填信息齐全后，自动生成结构化阶段级学习计划草案。
- 每个阶段推荐 3-5 道本地题库中的真实题目，保存题目 `slug` 和展示元数据。
- 用户确认后把草案保存为正式学习计划。
- 前端提供计划创建、追问、草案确认、正式计划列表和详情。
- 保持 Agent 生成、业务状态推进、持久化保存三者边界清晰。

## 非目标

- 不做每日任务、打卡、完成状态和进度看板。
- 不做正式计划编辑。
- 不接入错题、历史练习记录或能力画像。
- 不让模型直接执行保存动作。
- 不把保存正式计划作为 Agent tool 暴露给模型。
- 不要求读取完整题面；第一版推荐题通常只依赖题库列表元数据。

## 用户意图

第一版支持有限枚举，避免把学习计划入口做成泛聊天。

```text
PRACTICE_GOAL       刷题目标
ABILITY_DIAGNOSIS   能力诊断
INTERVIEW_SPRINT    面试冲刺
TOPIC_BREAKTHROUGH  专题突破
MISTAKE_REVIEW      错题复盘
LONG_TERM_LEARNING  长期学习
```

不同意图影响 prompt、追问重点和计划侧重点，但第一版不拆成六套独立流程。

## 核心流程

```text
前端提交基础表单
  -> 创建 LearningPlanDraft
  -> Agent 检查信息是否足够
  -> 信息不足：返回一个追问，draft 保持 COLLECTING
  -> 用户回答追问
  -> Agent 继续检查
  -> 信息足够：调用题库工具并生成结构化草案
  -> 后端校验草案
  -> draft 进入 GENERATED
  -> 用户确认保存
  -> 创建正式 LearningPlan
  -> 前端跳转计划详情
```

核心边界：

- Agent 负责追问和生成草案。
- 业务服务负责字段校验、状态推进、草案校验和正式保存。
- 持久化层负责保存 draft、正式计划、阶段和推荐题。
- API 层负责 HTTP 请求、错误映射和 DTO 转换。

## 后端模块边界

建议在 `mentor-application` 新增 `learningplan` 包，承载用例和业务编排：

- `LearningPlanDraftService`
- `LearningPlanAgentService`
- `LearningPlanService`
- `LearningPlanDraftValidator`
- `LearningPlanDraftConfirmUseCase`

建议在 `mentor-api` 新增学习计划 controller 和 DTO 包：

- `controller/learningplan`
- `learningplan/model`
- `learningplan/service` 或复用 application DTO

正式计划持久化可以放在 `mentor-api` 当前 PostgreSQL/MyBatis 边界内，后续如果业务层继续加厚，再单独迁移到更清晰的 persistence 模块。

## API 设计

API 路径统一放在 `/api/learning-plans` 下，公共路径、请求头和固定字段名应放入已有 API contract 常量类或学习计划专用常量类。

认证侧已新增最小用户身份接口：

```http
GET /api/auth/me
```

该接口返回当前登录用户 ID，响应 `data.id` 即后端业务使用的本地 `userId`。学习计划 API 不再接收客户端传入的 `userId`，所有草案、正式计划、列表和详情的用户归属都从当前请求的认证上下文解析。前端如需判断登录状态或调试当前用户，可先调用 `/api/auth/me`；业务请求本身不携带 `userId`。

### 创建草案

```http
POST /api/learning-plans/drafts
```

请求字段：

- `intent`
- `goal`
- `durationWeeks`
- `level`
- `weeklyHours`
- `programmingLanguage`
- `difficultyPreference`
- `interviewOriented`
- `topicPreferences`

响应字段：

- `draftId`
- `status`
- `assistantMessage`
- `missingFields`
- `draftPlan`

如果信息足够，首次创建可以直接返回 `GENERATED` 和 `draftPlan`。

### 继续追问

```http
POST /api/learning-plans/drafts/{draftId}/messages
```

请求字段：

- `message`

响应结构与创建草案一致。字段齐全后，后端自动触发草案生成。

### 确认保存

```http
POST /api/learning-plans/drafts/{draftId}/confirm
```

只有 `GENERATED` 状态允许确认。接口需要幂等：同一个 draft 重复确认时返回同一个正式 `planId`。

响应字段：

- `planId`
- `title`
- `status`

### 计划列表

```http
GET /api/learning-plans
```

列表接口按当前登录用户过滤，不接收 `userId` 查询参数。

列表项字段：

- `id`
- `title`
- `intent`
- `goal`
- `durationWeeks`
- `level`
- `weeklyHours`
- `status`
- `createdAt`

### 计划详情

```http
GET /api/learning-plans/{planId}
```

返回正式计划基础信息、阶段列表和每阶段推荐题目。详情接口必须用当前登录用户校验计划归属，只允许读取自己的计划。

## 数据模型

### 草案状态

```text
COLLECTING         信息收集中
GENERATED          草案已生成，等待确认
CONFIRMED          已确认并创建正式计划
GENERATION_FAILED  草案生成或校验失败，可补充要求后重试
EXPIRED            草案过期
```

`READY_TO_GENERATE` 不作为持久化状态暴露，它只是服务内部的瞬时判断。

### 正式计划

正式计划状态第一版只需要：

```text
ACTIVE    已确认保存，可展示
ARCHIVED  已归档，第一版可以只预留不暴露操作
```

正式计划第一版保存字段：

- `id`
- `userId`
- `title`
- `intent`
- `goal`
- `durationWeeks`
- `level`
- `weeklyHours`
- `programmingLanguage`
- `difficultyPreference`
- `interviewOriented`
- `topicPreferences`
- `profileSummary`
- `status`
- `createdAt`
- `updatedAt`

### 计划阶段

阶段字段：

- `id`
- `planId`
- `phaseIndex`
- `title`
- `durationWeeks`
- `focus`
- `objectives`
- `recommendedTags`
- `acceptanceCriteria`
- `reviewAdvice`

阶段数量按周期自动确定：

- 1-2 周：1-2 个阶段
- 3-6 周：3 个阶段
- 7 周及以上：4 个阶段

### 推荐题目

推荐题字段：

- `id`
- `phaseId`
- `slug`
- `frontendId`
- `title`
- `titleCn`
- `difficulty`
- `tags`
- `reason`
- `sortOrder`

每阶段目标推荐 3-5 道题。不足 3 道时允许保存 1-2 道，并记录降级标记；不能编造不存在的题。

### 草案表

草案可以先用一张 draft 表保存结构化 JSON：

- 表单输入
- 对话消息
- 缺失字段
- 生成草案 JSON
- 状态
- 已确认的正式 `planId`
- 过期时间
- 创建和更新时间

正式计划仍拆成计划、阶段、推荐题三类表，方便列表和详情查询。

## 必填与可选字段

生成计划前必须具备：

- `intent`
- `goal`
- `durationWeeks`
- `level`
- `weeklyHours`

可选字段：

- `programmingLanguage`
- `difficultyPreference`
- `interviewOriented`
- `topicPreferences`

前端可以通过表单提交可选字段。Agent 只在字段缺失、目标模糊、周期和目标明显不匹配、题目方向不清楚时追问。

## Agent 结构化输出

学习计划 Agent 每轮只输出一种结构化结果，建议命名为 `LearningPlanAgentResult`。

```json
{
  "action": "ASK_CLARIFICATION",
  "assistantMessage": "你希望这份计划更偏面试冲刺，还是长期系统学习？",
  "collectedProfile": {},
  "missingFields": ["goal"],
  "draftPlan": null
}
```

当信息不足时：

- `action = ASK_CLARIFICATION`
- `assistantMessage` 是下一条追问。
- `missingFields` 返回仍缺的字段。
- `draftPlan = null`

当信息足够时：

- `action = GENERATE_DRAFT`
- `draftPlan` 必须存在。
- 后端校验通过后，draft 进入 `GENERATED`。

草案结构：

```json
{
  "title": "四周 Java 算法面试冲刺计划",
  "summary": "围绕数组、哈希表、二分和动态规划建立高频题型能力。",
  "intent": "INTERVIEW_SPRINT",
  "goal": "准备 Java 后端算法面试",
  "durationWeeks": 4,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "phases": [
    {
      "phaseIndex": 1,
      "title": "基础题型恢复",
      "durationWeeks": 1,
      "focus": "数组、哈希表和双指针",
      "objectives": ["恢复高频基础题解题手感"],
      "recommendedTags": ["Array", "Hash Table", "Two Pointers"],
      "acceptanceCriteria": ["能独立解释每道题的核心状态或指针含义"],
      "reviewAdvice": "整理错误原因和可复用模板。",
      "problems": [
        {
          "slug": "two-sum",
          "frontendId": "1",
          "title": "Two Sum",
          "titleCn": "两数之和",
          "difficulty": "EASY",
          "tags": ["Array", "Hash Table"],
          "reason": "用于恢复哈希表查找和边界处理。"
        }
      ]
    }
  ]
}
```

## 题库工具使用规则

生成草案前，Agent 必须使用本地题库事实：

1. 调用 `list_problem_filters`，了解当前可用标签、难度和排序。
2. 按用户目标、阶段主题和偏好难度调用 `search_problems`。
3. 计划中的题目只能来自 `search_problems` 返回结果。
4. 第一版不强制调用 `get_problem_statement`，除非推荐理由需要题面细节。
5. 后端 confirm 前仍需要按 `slug` 二次校验题目存在。

推荐题不足时不允许编造题目。服务可在 draft metadata 中记录：

```text
problemRecommendationIncomplete=true
```

## 状态推进

```text
COLLECTING + ASK_CLARIFICATION
  -> COLLECTING

COLLECTING + GENERATE_DRAFT + 校验通过
  -> GENERATED

COLLECTING + GENERATE_DRAFT + 校验失败
  -> GENERATION_FAILED

GENERATION_FAILED + 用户补充要求
  -> COLLECTING 或 GENERATED

GENERATED + confirm
  -> CONFIRMED + 创建正式 LearningPlan
```

confirm 只允许作用于 `GENERATED` draft，且必须幂等。

## 前端设计

前端新增学习计划工作区，第一版包含三个视图。

### 计划列表

展示已保存计划：

- 标题
- 意图
- 周期
- 当前水平
- 每周时间
- 创建时间

操作：

- 新建计划
- 查看详情

### 新建计划

顶部是结构化表单：

- 意图
- 目标
- 周期
- 当前水平
- 每周可用时间
- 编程语言
- 偏好难度
- 是否面试导向
- 目标主题

提交后进入同一页面的 Agent 追问区域。

- 后端返回 `COLLECTING`：显示 `assistantMessage` 和输入框。
- 后端返回 `GENERATED`：显示计划草案预览和确认保存按钮。
- 保存成功：跳转正式计划详情。

草案生成后第一版不支持直接编辑。如果用户不满意，提供“补充要求并重新生成”的入口。

### 计划详情

展示正式计划：

- 摘要
- 基础信息
- 阶段列表
- 每阶段目标
- 推荐标签
- 验收标准
- 复盘建议
- 推荐题目

推荐题目可以跳转到已有题库能力。如果暂时没有独立题目详情页，先跳到题库列表并带 `keyword` 或 `slug`。

前端类型集中放在 `frontend/src/types/api.ts`，请求封装放在 `frontend/src/services/api.ts`。页面可以新增 `LearningPlans.tsx`，或按后续复杂度拆成 `frontend/src/learning-plan` 目录。

## 错误处理

- 表单必填字段缺失：API 返回 400，前端展示字段级错误。
- draft 不存在、已过期、已确认：返回明确业务错误。
- 用户回答后仍不够清楚：继续 `COLLECTING`，每轮只追问一个问题。
- 结构化输出 JSON 解析失败：进入 `GENERATION_FAILED`。
- schema 或业务校验失败：进入 `GENERATION_FAILED`，前端允许用户补充要求后重试。
- 题库工具不可用：生成失败，不保存半成品。
- confirm 时发现草案非法或题目不存在：拒绝保存并返回业务错误。
- 推荐题不足：保存可降级草案，但必须标记推荐不完整。

错误信息不得泄露 API key、模型 token、完整 Authorization 头或用户隐私内容。

## 校验规则

- 必填事实必须具备：`intent`、`goal`、`durationWeeks`、`level`、`weeklyHours`。
- 阶段数按周期规则校验，允许误差最多 1 个阶段。
- 每阶段题目目标 3-5 道；不足时可接受 1-2 道并记录降级；不能超过 5 道。
- 题目必须来自本轮题库工具结果，或由后端按 `slug` 二次校验存在。
- 总阶段周数应等于 `durationWeeks`，或由最后一个阶段吸收余数。
- 正式保存只允许 `GENERATED` draft confirm。
- confirm 幂等返回同一个正式计划。

## 测试策略

后端测试：

- `mentor-application` 单元测试覆盖字段缺失判断、draft 状态推进、confirm 幂等、阶段数规则。
- `mentor-api` controller 测试覆盖创建 draft、继续消息、confirm、列表、详情和非法状态。
- 题库推荐校验测试覆盖 slug 存在性、不足题目降级和禁止保存编造题目。
- 数据库迁移和 MyBatis XML 保持现有 mapper/XML 测试风格。

Agent 测试：

- 优先在学习计划业务层用 fake Agent 或 fake LLM 验证结构化输出处理。
- `agent-core` 只在发现通用结构化输出能力缺口时补测试。

前端测试：

- 新建计划表单提交。
- Agent 追问消息流转。
- 草案确认保存。
- 计划列表和详情渲染。

交付前建议运行：

```bash
make backend-test
make frontend-test
```

如果新增数据库迁移，确认 Flyway migration 资源测试和 mapper XML 测试通过。

## 后续演进

- 增加正式计划编辑。
- 增加每日任务和执行进度。
- 接入错题、练习记录和薄弱标签。
- 将推荐题从固定 3-5 道扩展为按可用时间动态计算。
- 增加计划滚动更新能力，例如每周根据完成情况重新规划下一阶段。
- 增加结构化输出自动修复 loop，在 schema 校验失败时让模型按错误信息修正草案。
