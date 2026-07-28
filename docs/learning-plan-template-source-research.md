# 学习计划模板资料源调研

调研日期：2026-07-06

## 背景与结论

当前学习计划生成主要依赖 AI 根据用户目标搜索本地题库并生成阶段计划。这个方式的问题是：模型容易受提示词和候选题质量影响，稳定性不足；用户也缺少“先选一个成熟路线，再个性化调整”的入口。

建议把“新建方案”扩展为三层能力：

1. 高价值模板：先覆盖面试系统备战、短期冲刺、新手系统入门、专项突破这几类最高频学习目标。
2. 官方参考模板：展示 LeetCode 官方 Study Plan 入口，使用本地题库按相同目标生成近似计划。
3. AI 个性化：在模板基础上按用户周期、每周时间、语言、薄弱标签、是否面试导向进行裁剪、重排和补题。

从最终计划价值看，第一批应优先做这些计划：

- 系统面试备战计划：8-12 周，覆盖 Top Interview 150 / NeetCode 150 这类完整面试题单，是最能体现产品价值的主模板。
- 短期面试冲刺计划：4-6 周，覆盖 Blind 75 / LeetCode 75 / Grind 75 / Best Practice 50，适合有明确面试时间的用户。
- 中文新手系统刷题计划：8-16 周，参考代码随想录这类按知识脉络组织的路线，解决“从哪里开始、下一步刷什么”的问题。
- 专项突破计划：2-4 周，覆盖动态规划、图论、二分、滑动窗口、树、回溯、贪心等薄弱主题，适合能力画像驱动的补强。
- 高频经典题计划：8-10 周，覆盖 LeetCode 热题 100，并按常见题型完成系统训练和复盘。

许可证不作为第一版排序的核心依据，但仍在文档中保留为后续真正内置和发布时的风险提示。

## 按最终计划价值排序

这里的排序不是按资料源流行度或许可友好度，而是按“最后生成出来的学习计划对用户是否有用”排序。

| 排名 | 计划方向 | 典型用户 | 可参考资料源 | 最终计划价值 | 第一版建议 |
| ---: | --- | --- | --- | --- | --- |
| 1 | 系统面试备战计划 | 有 2-3 个月准备周期，目标是覆盖主流算法面试 | LeetCode Top Interview 150、NeetCode 150、LeetCode 75 | 目标明确、题量完整、覆盖面广，最适合做默认推荐模板 | P0 |
| 2 | 短期面试冲刺计划 | 1 个月左右有面试，需要抓高频题 | Blind 75、Grind 75、LeetCode 75、Tech Interview Handbook Best Practice 50 | 用户感知强，能快速从“无计划”变成“每周该刷什么” | P0 |
| 3 | 中文新手系统入门计划 | 刚开始刷题、中文学习偏好强、需要按知识脉络推进 | 代码随想录、LeetCode Programming Skills、NeetCode beginner 类路线 | 降低入门门槛，适合作为产品留存型长期计划 | P0 |
| 4 | 专项突破计划 | 已刷过一部分题，但某类题明显薄弱 | LeetCode Dynamic Programming、Graph Theory、Binary Search、halfrost 标签题单、labuladong 专题 | 和能力画像结合价值高，适合后续自动推荐 | P1 |
| 5 | 模式化刷题路线 | 想按题型模式建立解题套路 | NeetCode pattern、leetcode-patterns、Tech Interview Handbook algorithm guides | 比纯题单更强调解题模式，适合 AI 讲解和复盘 | P1 |
| 6 | LeetCode 热题 100 | 希望系统覆盖常见经典算法题型 | LeetCode Hot 100 结构化题目元数据 | 题量完整、用户认知强，适合系统训练与面试前复盘 | P1 |
| 7 | 长周期 CS/面试综合计划 | 目标不只是刷题，还要补 CS 基础 | Coding Interview University、CS-Notes、doocs/leetcode | 内容广但不够聚焦“新建刷题方案”，可后续扩展 | P2 |
| 8 | 非算法专项计划 | SQL、前端 JS、数据分析方向用户 | SQL 50、Advanced SQL 50、30 Days of JavaScript、Pandas plans | 有价值但偏离当前算法学习主线 | P2 |

## 当前项目对接点

当前后端已有 `LearningPlanDraftPlan`、`LearningPlanPhaseDraft`、`LearningPlanProblemDraft` 等模型，模板导入可以自然映射为：

- 模板：标题、摘要、目标、周期、难度、标签偏好、来源元数据。
- 阶段：阶段序号、标题、周数、重点、目标、推荐标签、验收标准、复盘建议。
- 题目：slug、frontendId、标题、难度、tags、推荐理由、排序。

现有 `LearningPlanProblemCatalog` 已要求题目最终来自本地题库，这一点应保留：模板只提供题单顺序和标签意图，落库前必须用本地题库 slug 做归一化，找不到的题目要记录为缺失并跳过或提示。

## 价值评估标准

第一版先按最终计划价值评估资料源：

- 用户目标强：能直接对应“面试备战、短期冲刺、从零入门、专项突破、复盘查漏”这类明确场景。
- 计划结构完整：不仅有题目列表，还能自然拆成阶段、主题、目标和验收标准。
- 覆盖主流知识点：数组、哈希、双指针、滑动窗口、链表、栈队列、树、图、回溯、贪心、动态规划、堆、并查集等。
- 可个性化空间大：能按周期、每周时间、难度偏好、薄弱标签裁剪或扩展。
- 和本项目能力贴合：能映射到 `LearningPlanDraftPlan` / `LearningPlanPhaseDraft` / `LearningPlanProblemDraft`，并能与本地题库 slug 对齐。
- 可解释性强：用户能看懂为什么先刷这一组、为什么下一阶段切到另一个主题。
- 可持续迭代：未来能结合能力画像、错题本、复盘队列，自动推荐下一套计划。

许可证和内容授权仍需要记录，但在第一版产品决策中只作为“交付方式约束”：能直接内置的内置，不能直接内置的先做参考路线、外链或人工重建版本。

## LeetCode 官方 Study Plan

LeetCode 官方计划适合作为“参考模板”和用户外链入口。建议展示官方名称、简介、外链和目标标签；实际生成计划时，由我们用本地题库和开源题单组合出可执行版本。

| 计划 | 地址 | 适用场景 | 建议处理 |
| --- | --- | --- | --- |
| LeetCode 75 | `https://leetcode.com/studyplan/leetcode-75/` | 面试基础、高频核心题 | P0 参考；可用本地题库生成 4-8 周版本 |
| Top Interview 150 | `https://leetcode.com/studyplan/top-interview-150/` | 系统面试准备 | P0 参考；可映射到 NeetCode 150 / 本地题库 |
| LeetCode 热题 100 | `https://leetcode.com/studyplan/top-100-liked/` | 高频经典题系统训练 | P1 参考；保留完整 100 题并按题型拆分阶段 |
| Binary Search | `https://leetcode.com/studyplan/binary-search/` | 二分专项，官方标注 8 类模式 / 42 题 / 1 个月 | P1 参考；可做 1-2 周专题模板 |
| Graph Theory | `https://leetcode.com/studyplan/graph-theory/` | 图论专项，官方标注 8 个主题 / 45 题 | P1 参考；适合 DFS/BFS/拓扑/并查集分阶段 |
| Dynamic Programming | `https://leetcode.com/studyplan/dynamic-programming/` | 动态规划专项，官方标注 10 个核心 DP 模式 | P1 参考；建议按一维、二维、背包、区间、状态机拆阶段 |
| Programming Skills | `https://leetcode.com/studyplan/programming-skills/` | 编程基础、实现能力，官方标注 50 题 | P1 参考；适合初学者和语法过渡 |
| SQL 50 | `https://leetcode.com/studyplan/top-sql-50/` | SQL 练习 | P2；本项目算法主线之外，可后续扩展 |
| Advanced SQL 50 | `https://leetcode.com/studyplan/premium-sql-50/` | SQL 进阶，官方标注 50 道高频 SQL 题 | P2；后续扩展 |
| 30 Days of JavaScript | `https://leetcode.com/studyplan/30-days-of-javascript/` | JS 基础与前端候选人 | P2；和算法主线弱相关 |
| Introduction to Pandas | `https://leetcode.com/studyplan/introduction-to-pandas/` | Pandas 入门，官方标注 15 题 | P2；数据分析方向扩展 |
| 30 Days of Pandas | `https://leetcode.com/studyplan/30-days-of-pandas/` | Pandas 练习 | P2；数据分析方向扩展 |
| Premium Algo 100 | `https://leetcode.com/studyplan/premium-algo-100/` | 付费题单 | 暂不内置；只可标注为 Premium 参考 |
| Algorithm / Data Structure 旧计划 | `https://leetcode.com/studyplan/algorithm/`、`https://leetcode.com/studyplan/data-structure/` | 旧版基础计划 | 作为历史参考，需人工确认当前可访问性 |

第一版交付方式建议：

- 官方计划作为用户熟悉的入口和目标描述，例如“跟随 LeetCode 75”“跟随 Top Interview 150”。
- 具体阶段和题目用本地题库重建，保证计划能进入现有练习、复盘和 AI 讲解流程。
- Premium 题默认不进入第一版计划；后续可以作为“可选扩展题”标记。

## GitHub 资料源价值排序

星标和许可证来自 GitHub API，时间为 2026-07-06；排序按最终计划价值。

| 价值级别 | 资料源 | 星标 | 能产出的计划 | 价值判断 | 数据形态 | 许可备注 |
| --- | --- | ---: | --- | --- | --- | --- |
| S | `neetcode-gh/leetcode` | 6,405 | 面试核心 75、系统面试 150、按 pattern 的专项计划 | 题单目标极清晰，题目、难度、pattern、Blind 75 / NeetCode 150 标记都结构化，最适合快速落到产品 | `.problemSiteData.json`，450 条题目数据 | MIT |
| S | LeetCode 官方 Study Plan | - | LeetCode 75、Top Interview 150、Top 100 Liked、DP、Graph、Binary Search、Programming Skills | 用户认知最强，适合作为模板市场的一等入口；即使不复制题目内容，也能用官方目标来组织本地计划 | 官方页面和计划 URL | 官方参考，不直接复制题面/题解 |
| A+ | `youngyangyang04/leetcode-master` | 61,855 | 中文新手系统入门、从数组到图论的长期刷题计划 | 对中文用户价值很高，模块顺序清晰，特别适合解决入门用户“路线不清楚”的问题 | README 模块化路线、中文题解 | 无 LICENSE，第一版可作为路线参考 |
| A+ | `yangshun/tech-interview-handbook` | 140,687 | 5 周面试冲刺、Best Practice 50、算法主题 essential/recommended 计划 | 面试导向强，题单质量高，适合短期冲刺和复盘；内容也能补充阶段目标与复盘建议 | Markdown 题单、算法主题页、Grind 75 跳转函数 | MIT |
| A | `labuladong/fucking-algorithm` | 134,704 | 动态规划专项、数据结构设计专项、算法思维训练计划 | 不只是题单，更适合生成“为什么这样刷”的思维框架型计划，尤其是 DP 和数据结构设计 | 文章目录、快速/完整学习规划、专题文章 | 无 LICENSE，第一版可作为路线参考 |
| A | `seanprashad/leetcode-patterns` | 13,340 | Beginner Roadmap、Experienced Roadmap、模式化刷题路线 | Roadmap 结构贴近我们最终计划模型，阶段说明和题目 note 对 AI 个性化很有参考价值 | `questions.json` 179 题、`roadmaps.ts` 两条 roadmap | CC-BY-NC-4.0 |
| A- | `halfrost/LeetCode-Go` | 33,787 | 专项突破计划、标签补强计划、按复杂度/难度组织的复盘计划 | 适合补齐专题模板的数据底座，尤其是题号、标签、复杂度这类结构化元数据 | `ctl/meta/*`，约 300 个唯一 LeetCode ID，19 个算法标签 | MIT |
| B+ | `doocs/leetcode` | 36,258 | 中文题库覆盖、剑指 Offer / 面试金典辅助计划 | 覆盖面大，适合作为中文标题、题库覆盖和扩展题单参考，但不是最强路线源 | 大规模多语言题解、目录体系 | CC-BY-SA-4.0 |
| B | `azl397985856/leetcode` | 55,769 | 中文专题补充、图解参考、复盘解释素材 | 可补讲解和专题视角，但生成计划的结构化价值弱于前面几类 | LeetCode 题解、专题文章、图解 | Other / NOASSERTION |
| B | `CyC2018/CS-Notes` | 184,666 | 面试综合学习计划、CS 基础补强 | 适合长期学习，不适合作为第一版刷题计划主线 | 面试基础、LeetCode、网络、OS 等 | 无 LICENSE |
| B- | `jwasham/coding-interview-university` | 355,185 | 长周期 CS 基础与面试综合计划 | 价值高但范围过大，不是当前“新建刷题方案”的核心 | 多月 CS 学习计划、算法/系统资源 | CC-BY-SA-4.0 |
| C | `MisterBooo/LeetCodeAnimation` | 76,619 | 可视化讲解参考 | 对计划生成帮助有限，更适合后续讲解体验 | 动画形式讲解 LeetCode | 无 LICENSE |
| C | `ashishps1/awesome-leetcode-resources` | 17,187 | 资源发现、模式样例 | 更像资源目录，不是计划主源 | 资源列表和模式示例 | GPL-3.0 |
| C | `armankhondker/best-leetcode-resources` | 3,797 | 资源发现 | 可辅助发现资料，但产品模板价值低 | 资源集合和部分解法 | MIT |

## 重点候选源详情

### neetcode-gh/leetcode

仓库地址：`https://github.com/neetcode-gh/leetcode`

关键文件：

- `.problemSiteData.json`
- `README.md`
- `LICENSE`

实测数据：

- 总题目：450
- Blind 75：75
- NeetCode 150：150
- Blind 75 覆盖模式：Trees、1-D Dynamic Programming、Arrays & Hashing、Linked List、Graphs、Intervals、Bit Manipulation、Sliding Window、Two Pointers、Tries、Math & Geometry、Binary Search、Backtracking、2-D Dynamic Programming、Greedy、Stack、Heap / Priority Queue、Advanced Graphs。
- NeetCode 150 覆盖模式：Trees、Graphs、1-D/2-D Dynamic Programming、Linked List、Arrays & Hashing、Backtracking、Greedy、Math & Geometry、Stack、Binary Search、Heap / Priority Queue、Bit Manipulation、Sliding Window、Advanced Graphs、Intervals、Two Pointers、Tries。

建议落地模板：

- `neetcode_blind_75_interview_core`：4-8 周，适合面试核心题。
- `neetcode_150_systematic_interview`：8-12 周，适合系统化面试准备。
- `neetcode_pattern_track_*`：按 pattern 拆专题，例如 Trees、Graphs、DP、Sliding Window。

导入策略：

- 只导入 slug、题名、difficulty、pattern、`blind75`、`neetcode150` 和排序。
- 不导入题解源码，不导入文章内容。
- 题目链接通过 slug 组装，最终以本地题库为准。

### yangshun/tech-interview-handbook

仓库地址：`https://github.com/yangshun/tech-interview-handbook`

关键内容：

- `apps/website/contents/best-practice-questions.md`：Blind 75 作者整理的 5 周 Best Practice 题单，约 50 道核心题，另含 optional 题。
- `apps/website/contents/algorithms/*.md`：算法主题页，包含 essential/recommended practice questions。
- `apps/website/functions/grind75/[[catchall]].js`：Grind 75 外部页面跳转，不建议把跳转目标页面作为直接数据源。

实测数据：

- 算法主题文件：21 个。
- 主题页中 LeetCode 链接：156 个链接，126 个唯一 slug。

建议落地模板：

- `tih_best_practice_50_5weeks`：5 周面试冲刺。
- `tih_algorithm_essentials`：按算法主题生成短期专项。
- `tih_interview_revision_12weeks_reference`：12 周复习路线可作为计划结构参考。

导入策略：

- Markdown 表格可解析 Question、Difficulty、LeetCode URL。
- essential/recommended 可映射到 `phase.objectives` 和 `recommendedTags`。
- 保留来源和 MIT license notice。

### halfrost/LeetCode-Go

仓库地址：`https://github.com/halfrost/LeetCode-Go`

关键文件：

- `ctl/meta/*`
- `LICENSE`

实测数据：

- 唯一题目 ID：约 300。
- 标签文件：19 个。
- 标签包括：Array、Backtracking、Binary Search、Bit Manipulation、BFS、DFS、Dynamic Programming、Hash Table、Linked List、Math、Segment Tree、Sliding Window、Sorting、Stack、String、Tree、Two Pointers、Union Find 等。

建议落地模板：

- `topic_array_foundation`
- `topic_binary_search`
- `topic_sliding_window`
- `topic_tree_traversal`
- `topic_graph_bfs_dfs`
- `topic_dynamic_programming_foundation`

导入策略：

- 解析 Markdown 表格中的题号、标题、难度、时间复杂度、空间复杂度、推荐标记。
- 用题号匹配本地题库 frontendId，再反查 slug。
- 复杂度字段只作为内部参考，不一定展示给用户。

### seanprashad/leetcode-patterns

仓库地址：`https://github.com/seanprashad/leetcode-patterns`

关键文件：

- `src/data/questions.json`
- `src/data/roadmaps.ts`
- `LICENSE`

实测数据：

- 题目：179。
- Premium：13。
- 标签：48。
- Roadmap：Beginner Roadmap、Experienced。
- Beginner phase：11。
- Experienced phase：15。
- License：CC-BY-NC-4.0。

第一版使用方式：

- 优先借鉴 beginner / experienced 的阶段拆分方式，而不是直接把它作为唯一题单来源。
- 它的题目 note 很适合启发 AI 生成“为什么推荐这题”，但第一版可以由我们自己重写推荐理由。
- 如果后续要直接内置原始 roadmap 数据，再单独处理授权和署名。

### 代码随想录 leetcode-master

仓库地址：`https://github.com/youngyangyang04/leetcode-master`

优势：

- 中文用户熟悉度高。
- README 已按清晰模块组织：数组、链表、哈希表、字符串、双指针、栈与队列、二叉树、回溯、贪心、动态规划、单调栈、图论。
- 对初学者友好，适合做“从零到系统刷题”的产品模板。

第一版使用方式：

- 把它作为“中文系统刷题入门”的主参考，优先复用知识脉络：数组、链表、哈希、字符串、双指针、栈队列、树、回溯、贪心、动态规划、单调栈、图论。
- 具体题目从本地题库按 slug / frontendId 匹配，阶段目标、验收标准和复盘建议由 algo-mentor 自己生成。
- 授权备注：GitHub API 未发现 LICENSE，因此第一版更适合参考后重建或外链，不直接复制文章、图示和题解内容。

## 初始模板池建议

第一版建议按最终用户价值推出 10 个模板。它们不是资料源的一比一搬运，而是把高价值路线转成 algo-mentor 自己的阶段计划。

| 优先级 | 用户看到的模板 | 参考来源 | 推荐周期 | 核心价值 | 计划形态 |
| ---: | --- | --- | ---: | --- | --- |
| 1 | 系统面试 150 题 | Top Interview 150、NeetCode 150 | 8-12 周 | 覆盖完整，适合作为默认推荐；用户明确知道刷完后能覆盖主流面试题型 | 按数组/哈希、双指针、滑窗、链表、树、图、DP、堆、回溯等阶段推进 |
| 2 | 面试核心 75 题 | LeetCode 75、Blind 75、NeetCode Blind 75 | 4-8 周 | 题量适中，完成率更高，是新用户最容易开始的一套计划 | 按高频基础、数据结构、非线性结构、DP/图论收尾 |
| 3 | 5 周面试冲刺 | Tech Interview Handbook Best Practice 50、Grind 75 | 5 周 | 有强时间约束，适合面试倒计时；每周目标清晰 | 每周 8-12 题，按序列、数据结构、树图、进阶结构、DP 分周 |
| 4 | 中文系统刷题入门 | 代码随想录、Programming Skills、NeetCode beginner | 8-16 周 | 对中文初学者价值最高，解决“刷题顺序”和“知识先后关系” | 数组 -> 链表 -> 哈希 -> 字符串/双指针 -> 栈队列 -> 树 -> 回溯 -> 贪心 -> DP -> 图 |
| 5 | 动态规划专项突破 | LeetCode Dynamic Programming、labuladong DP、halfrost DP | 2-4 周 | DP 是用户最常见痛点，专项计划感知强 | 一维 DP -> 背包 -> 子序列 -> 股票/状态机 -> 区间/博弈 |
| 6 | 图论专项突破 | LeetCode Graph Theory、代码随想录图论、halfrost BFS/DFS/Union Find | 2-4 周 | 能补一个明确薄弱区，适合能力画像推荐 | DFS/BFS -> 岛屿问题 -> 拓扑排序 -> 并查集 -> 最短路/MST 选修 |
| 7 | 二分与边界专项 | LeetCode Binary Search、halfrost Binary Search | 1-2 周 | 小而高频，容易形成“短计划完成体验” | 基础二分 -> 搜索旋转数组 -> 答案二分 -> 矩阵/区间变体 |
| 8 | 滑动窗口与双指针专项 | NeetCode pattern、halfrost Two Pointers / Sliding Window、Tech Interview Handbook | 1-2 周 | 适合快速提升字符串和数组题正确率 | 固定窗口 -> 可变窗口 -> 双端指针 -> 去重与排序组合 |
| 9 | LeetCode 热题 100 | LeetCode Hot 100 结构化题目元数据 | 8-10 周 | 覆盖常见经典题型，适合系统训练与面试前复盘 | 保留完整 100 题，按哈希/数组、链表、树图、二分、堆和 DP 分阶段 |
| 10 | 编程基础与实现力 | LeetCode Programming Skills、Easy/Medium 本地题库 | 2-4 周 | 适合刚换语言或基础实现薄弱的用户 | 模拟、字符串、数组、矩阵、基础数据结构操作 |

第二版再考虑：

- SQL 50 / Advanced SQL 50。
- JavaScript 30 / Pandas 30。
- 长周期 CS 基础计划：Coding Interview University、CS-Notes。
- 更完整的模式化 Roadmap：`leetcode-patterns` beginner / experienced 路线。

## 模板数据模型建议

后续可以新增独立模板域模型，不要把模板硬编码到 prompt。

建议字段：

- `templateId`：稳定 ID，例如 `neetcode_blind_75_interview_core`。
- `title`、`summary`、`targetAudience`。
- `sourceType`：`OPEN_SOURCE`、`OFFICIAL_REFERENCE`、`MANUAL_CURATED`。
- `sourceName`、`sourceUrl`、`sourceRepo`、`sourceCommit`、`sourceLicense`。
- `licenseNotice`：展示或导出时使用。
- `durationWeeksMin`、`durationWeeksMax`、`defaultWeeklyHours`。
- `level`：BEGINNER / INTERMEDIATE / ADVANCED。
- `interviewOriented`。
- `tags`：模板标签。
- `premiumPolicy`：`EXCLUDE_BY_DEFAULT`、`ALLOW_WITH_NOTICE`。
- `phases`：阶段模板。
- `problemRefs`：slug/frontendId/order/sourceTags/sourceDifficulty/isPremium。
- `metadata`：原始来源字段和导入版本。

## 导入与生成流程建议

1. 离线导入资料源，生成规范化模板 JSON。
2. 每条题目用 slug 或 frontendId 匹配本地题库。
3. 缺失题目写入 `metadata.missingProblems`，不阻塞模板导入。
4. 用户选择模板后，系统先按模板生成基础计划。
5. AI 只负责裁剪、解释和个性化重排，不再从零决定整套路线。
6. 最终仍走现有 `LearningPlanDraftValidator`，保证阶段数、周数、每阶段题量等规则。

## 交付风险备注

第一版调研和产品设计以价值排序为主，但实现时需要区分三种交付方式：

- 可直接内置：结构化题单元数据、来源说明和许可证信息一起保存，例如 NeetCode、Tech Interview Handbook、Halfrost。
- 参考后重建：对代码随想录、labuladong、LeetCode 官方计划这类高价值路线，先抽象阶段结构和学习目标，再用本地题库重建题单，不复制题解和原文内容。
- 外链参考：无法确认授权或范围太宽的资料，先作为“参考来源”展示，不进入模板数据。

这个风险备注不改变第一版价值排序，只影响每个模板实际落地时的数据来源和文案方式。

## 下一步

建议按最终计划价值推进，而不是按资料源导入难度推进：

1. 先定义 `learning_plan_template` 数据模型和规范化 JSON schema，支持阶段、题目引用、来源、推荐周期、目标用户和价值标签。
2. 手工整理第一批 10 个模板的“计划骨架”：标题、目标用户、阶段顺序、每阶段目标、推荐周期、验收标准。
3. 对系统面试 150、面试核心 75、5 周冲刺这 3 个 P0 模板，优先接入 NeetCode / LeetCode / Tech Interview Handbook 的题目 slug。
4. 对中文系统入门模板，先按代码随想录的知识脉络重建阶段，不直接复制文章内容；题目从本地题库匹配。
5. 对 DP、图论、二分、滑动窗口专项模板，融合 LeetCode 官方专题、halfrost 标签题单和 labuladong / 代码随想录的主题结构。
6. 增加模板选择 API：列模板、预览模板、基于模板生成草案。
7. 修改 AI prompt：输入中加入模板阶段、题目顺序、可调整范围和不可编造约束，让 AI 做个性化而不是从零生成。
8. 前端新建方案入口增加“从模板开始”和“AI 自由生成”两个路径；默认推荐系统面试 150、面试核心 75、中文系统入门。
