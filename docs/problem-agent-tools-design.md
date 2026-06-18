# 题目 Agent 工具设计

## 背景

项目已经有题库 HTTP API，支持按关键词、难度、标签、分类查询题目，也支持按 `slug` 查看题目详情。这些接口适合前端页面或外部客户端直接调用，但 Agent 在对话中仍缺少主动使用题库的能力。

在算法学习场景里，用户经常不会一次性给出精确题目信息。例如：

- “帮我找几道二分查找的中等题。”
- “讲一下 two-sum 这道题。”
- “我想练数组和哈希表相关的简单题。”
- “根据这道题题面帮我分析思路，不要直接给完整答案。”

如果 Agent 只能依赖用户输入和模型记忆，就容易出现题目定位不准、题面内容过时、讲解和本地题库不一致等问题。因此需要把现有题库能力封装成 Agent 可调用工具，让模型在回答前可以先了解题库可用筛选项，再检索本地题库，最后读取准确题面。

## 当前题库调研

基于本地 PostgreSQL 题库的当前数据：

- `problem` 表有 1828 道题。
- 难度只有 3 个枚举：`EASY`、`MEDIUM`、`HARD`。
- 标签有 38 个固定英文值，例如 `Array`、`Hash Table`、`Binary Search`、`Dynamic Programming`。
- `problem_category` 和 `problem_category_item` 当前为空，分类查询暂时没有实际数据支撑。
- 230 道题没有标签。
- 题面内容完整，平均约 1745 字符，最大约 8138 字符。

这个现状说明：`difficulty` 和 `tag` 不是自然语言自由输入，而是数据库中的离散值。模型如果不知道可用枚举，直接调用 `search_problems` 时很容易猜错标签名称，例如把 `Hash Table` 写成 `哈希表`、`hash_table` 或 `HashMap`。因此第一版工具体系需要提供“过滤项发现”能力，不能只依赖搜索工具的自由字符串参数。

当前可用标签：

```text
Array
Backtracking
Binary Indexed Tree
Binary Search
Binary Search Tree
Bit Manipulation
Brainteaser
Breadth-first Search
Depth-first Search
Design
Divide and Conquer
Dynamic Programming
Geometry
Graph
Greedy
Hash Table
Heap
Line Sweep
Linked List
Math
Memoization
Minimax
Ordered Map
Queue
Random
Recursion
Rejection Sampling
Reservoir Sampling
Segment Tree
Sliding Window
Sort
Stack
String
Topological Sort
Tree
Trie
Two Pointers
Union Find
```

## 设计目标

- 让 Agent 能先发现当前题库可用的难度、标签、排序和分类，再进行确定性搜索。
- 让 Agent 能根据用户的自然语言需求主动查找题目。
- 让 Agent 能按稳定的 `slug` 获取题面，基于本地题库事实进行讲解。
- 复用现有 `ProblemService`、`ProblemRepository` 和题库数据模型，不复制查询逻辑。
- 控制工具返回内容大小，避免题库列表或完整题面无界占用模型上下文。
- 保持工具职责清晰：过滤项发现、题目定位、题面读取分别由独立工具承担。

## 工具清单

### list_problem_filters

`list_problem_filters` 用于返回当前题库支持的搜索过滤项。它解决的是“模型不知道该怎么搜”的问题。

输入参数：

- 无必填参数。
- 可选 `includeCounts`，默认 `true`，表示是否返回每个过滤项命中的题目数量。

建议输出字段：

- `problemCount`：题库题目总数。
- `difficulties`：难度列表，包含 `value` 和可选 `problemCount`。
- `tags`：标签列表，包含 `value` 和可选 `problemCount`。
- `sorts`：搜索工具支持的排序列表。
- `categories`：分类列表。当前数据库为空时返回空数组。
- `notes`：面向模型的简短提示，例如“当前 category 无可用值，优先使用 tag 搜索”。

当前 `tags` 数量只有 38 个，完整返回不会造成明显上下文压力。后续标签或分类变多后，可以增加 `kind` 参数，只返回指定类型的过滤项。

### search_problems

`search_problems` 用于根据明确参数查找题目，返回轻量列表。

建议输入参数：

- `keyword`：关键词，可匹配标题、中文标题、`slug` 或题目前端编号。
- `difficulty`：难度，只允许 `EASY`、`MEDIUM`、`HARD`。
- `tag`：标签，必须使用 `list_problem_filters` 返回的标签值。
- `sort`：排序方式，复用现有 `ProblemSort`。
- `page`：页码，默认从 1 开始。
- `pageSize`：每页数量，复用现有最大限制。

第一版不建议把 `category` 作为主要搜索参数，因为当前分类表没有数据。后续分类 seed 和导入链路补齐后，再把 `category` 加回工具输入，并要求它使用 `list_problem_filters.categories` 中的 `slug` 或名称。

建议输出字段：

- `items`：题目列表。
- `total`：匹配总数。
- `page`：当前页。
- `pageSize`：当前分页大小。
- `appliedFilters`：工具实际使用的过滤条件，便于模型确认自己搜索了什么。

每个题目列表项只返回：

- `slug`
- `frontendId`
- `title`
- `titleCn`
- `difficulty`
- `tags`

列表工具不返回题面正文、代码模板或导入信息。它的核心作用是帮助 Agent 定位候选题目，而不是一次性读取大量内容。

### get_problem_statement

`get_problem_statement` 用于根据 `slug` 获取题面和必要元数据。

建议输入参数：

- `slug`：题目的稳定标识，例如 `two-sum`。

建议输出字段：

- `found`：是否找到题目。
- `slug`
- `frontendId`
- `title`
- `titleCn`
- `difficulty`
- `tags`
- `contentMarkdown`
- `sampleTestCase`
- `leetcodeUrl`

该工具只返回题面和元数据，不返回 `python3Template`、`sourceCommit` 等内部导入或实现相关字段。这样可以让 Agent 基于准确题面讲解，同时减少无关上下文。

当 `slug` 不存在时，工具应返回结构化结果，例如 `found=false` 和原始 `slug`，而不是直接中断整个 Agent run。这样模型可以自然地告诉用户没有找到该题，并尝试重新搜索。

## 为什么设计为三个工具

### 先发现过滤项，再搜索

当前题库不是向量搜索，`tag`、`difficulty`、`sort` 都是离散值。模型如果不知道精确枚举，就会把用户自然语言直接翻译成不存在的过滤条件。

`list_problem_filters` 提供当前数据库事实，让 Agent 可以先知道“二分查找”对应的可用标签是 `Binary Search`，“动态规划”对应 `Dynamic Programming`。这比把所有标签硬编码在 prompt 里更稳，也能适应后续题库更新。

### 符合先定位、再读取的使用节奏

查找题目和读取题面是两个不同动作。

当用户说“找几道哈希表简单题”时，Agent 只需要列表结果，不需要读取每一道题的完整题面。当用户确认某一道题，或者直接给出 `slug` 时，Agent 再调用题面工具读取详情。

拆成搜索和题面两个工具后，模型可以按需逐步调用，避免把所有候选题面的正文都塞进上下文。

### 降低上下文成本

题面 Markdown 往往比列表元数据长很多。如果搜索工具直接返回完整题面，用户一次模糊查询就可能带回大量无关文本，影响模型后续推理质量和调用成本。

因此 `search_problems` 返回轻量列表，`get_problem_statement` 才返回单题题面。这个边界和现有工具结果压缩机制互补：小列表可以直接进入上下文，大题面则由 Agent run 内工具结果压缩策略控制。

### 避免模型凭记忆讲题

算法题有稳定的题面、样例和约束。Agent 直接基于模型记忆回答时，可能出现题面混淆、样例错误或标题同名问题。

通过 `get_problem_statement` 读取本地题库，Agent 可以先拿到项目内真实题面，再进行讲解、提示、复杂度分析或生成练习计划。

### 保持领域边界清晰

工具实现应放在题库业务边界内，底层复用 `ProblemService`。`agent-core` 只认识通用的 `AgentTool` 抽象，不应反向依赖题库领域。

这种设计让题库工具可以作为 `mentor-api` 中的 Spring bean 自动注册到 `AgentToolRegistry`，不需要改动 Agent 主循环。

## 工具使用流程

用户提供模糊学习目标时，推荐流程：

```text
user: 找几道二分查找的中等题
  -> list_problem_filters
  -> search_problems(tag="Binary Search", difficulty="MEDIUM", pageSize=5)
  -> assistant 返回候选题目和 slug
```

用户提供明确 `slug` 时，推荐流程：

```text
user: 讲一下 two-sum
  -> get_problem_statement(slug="two-sum")
  -> assistant 基于题面讲解
```

用户提供中文题名或模糊标题时，推荐流程：

```text
user: 讲一下两数之和
  -> search_problems(keyword="两数之和", pageSize=5)
  -> get_problem_statement(slug="two-sum")
  -> assistant 基于题面讲解
```

## 工具的学习场景价值

### 题目定位

用户可能只记得题目中文名、英文关键词、标签或大概难度。`list_problem_filters` 和 `search_problems` 可以帮助 Agent 从本地题库中找出候选题目，并向用户确认。

### 题面讲解

用户给出 `slug` 或确认某道题后，`get_problem_statement` 可以提供准确题面。Agent 后续可以基于题面进行：

- 需求和约束解释。
- 思路拆解。
- 提示式引导。
- 边界条件分析。
- 复杂度讨论。
- Java 解法讲解。

### 练习推荐

在学习计划或错题复盘场景中，Agent 可以先用 `list_problem_filters` 了解可用标签，再用 `search_problems` 按标签和难度筛选题目，最后根据用户水平推荐练习顺序。

### 对话连续性

工具返回的 `slug` 可以作为后续对话中的稳定引用。用户从“给我几道数组题”进入“讲第二题”时，Agent 可以把之前列表中的 `slug` 用于读取题面。

## 非目标

- 不新增新的 HTTP API。现有 `/api/problems` 和 `/api/problems/{slug}` 已覆盖前端调用场景。
- 不提供解法检索工具。当前工具只解决过滤项发现、题目查找和题面读取，不直接暴露答案库。
- 不实现语义向量搜索。第一阶段先复用已有关键词、难度和标签查询。
- 不做用户练习状态过滤。后续有练习记录和学习计划数据后再扩展。
- 不让搜索工具返回完整题面。完整题面只能按单题 `slug` 读取。
- 不把当前为空的 `category` 当作稳定能力。分类数据补齐前，Agent 不应优先使用分类搜索。

## 实现建议

工具实现建议放在 `backend/mentor-api` 的题库相关包下，例如 `org.congcong.algomentor.api.problem.tool`。

建议类：

- `ProblemAgentToolNames`：统一维护工具名和共享字段名。
- `ListProblemFiltersTool`：实现 `list_problem_filters`。
- `SearchProblemsTool`：实现 `search_problems`。
- `GetProblemStatementTool`：实现 `get_problem_statement`。

仓储能力建议：

- 现有 `findProblems` 和 `findProblemBySlug` 可直接支持 `search_problems` 和 `get_problem_statement`。
- 为 `list_problem_filters` 增加只读查询能力，返回难度、标签、分类和计数。
- 标签从 `problem.tags` 聚合，分类从 `problem_category` 和 `problem_category_item` 聚合。

配置建议：

- `algo-mentor.agent.tools.problem-filters.enabled=true`
- `algo-mentor.agent.tools.problem-search.enabled=true`
- `algo-mentor.agent.tools.problem-statement.enabled=true`

默认开启三个工具，并允许本地或测试环境通过配置关闭。

异常处理建议：

- 题库仓库不可用时，抛出 `AgentException`，错误码使用 `TOOL_EXECUTION_FAILED`，metadata 带 `toolName`。
- 参数格式错误时，返回明确的工具执行错误，避免模型误以为查不到题。
- `tag` 不是当前可用标签时，返回明确错误，并提示先调用 `list_problem_filters`。
- `slug` 未找到时返回 `found=false`，让 Agent 自然恢复。

## 测试建议

- `list_problem_filters`：
  - 返回 `EASY`、`MEDIUM`、`HARD`。
  - 返回当前标签列表和计数。
  - 分类表为空时返回空数组和提示信息。
- `search_problems`：
  - 能按 `keyword`、`difficulty`、`tag` 查询。
  - 无效 `tag` 返回清晰错误。
  - 输出不包含题面、代码模板或导入字段。
- `get_problem_statement`：
  - 已存在 `slug` 返回题面和元数据。
  - 不存在 `slug` 返回 `found=false`。
  - 输出不包含 `python3Template` 和 `sourceCommit`。
- 配置测试：
  - 默认注册三个题目工具。
  - 分别关闭配置后，对应工具不进入 `AgentToolRegistry`。

## 后续演进

后续可以在这三个基础工具之上继续扩展：

- `recommend_similar_problems`：根据当前题目的标签、难度和分类推荐相似题。
- `search_problems_by_progress`：结合用户练习状态筛选未做、错题或待复习题。
- `get_problem_hints`：返回分层提示，而不是直接返回完整解法。
- `semantic_search_problems`：接入向量索引，支持按自然语言意图检索题目。
- 分类 seed 和导入链路：补齐 `problem_category` 后，再把 `category` 作为稳定搜索参数。

这些能力都应基于当前工具体系建立的原则继续设计：先让 Agent 发现可用搜索空间，再基于数据库事实检索，最后读取必要题面，并始终控制上下文大小。
