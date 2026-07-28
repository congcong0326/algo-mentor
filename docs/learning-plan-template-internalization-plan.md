# 学习计划模板内部化实施计划

编写日期：2026-07-06

## 背景和范围

本计划面向“把调研资料源转换为 algo-mentor 内部学习计划模板”的工程落地。资料源评估以 `docs/learning-plan-template-source-research.md` 为准，seed 最小闭环以 `docs/learning-plan-template-seed-design.md` 为准。

当前状态：

- P0 首批 10 个内部模板已写入 `data/learning-plan-template-seed/`，生成器已从 NeetCode 单源脚本演进为多 source 生成器。
- P1-A 两个核心批次和动态规划进阶扩展已完成，共形成 17 个专项模板；P1-B 已完成 TIH 核心专题、剑指 Offer、算法模式入门、程序员面试金典、LeetCode 75、LeetCode 面试经典 150、代码随想录完整版和 labuladong 核心算法框架路线。
- 当前 seed manifest 显示：模板数 `35`，题目引用数 `1738`，本地匹配 `1699`，本地缺失 `39`；本批新增 `6` 模板 / `336` refs / `331` 匹配 / `5` 缺失。
- 当前 35 个模板已拆分到 `data/learning-plan-template-sources/templates/<templateId>/`，每个模板目录包含 `template.json` 和 `problem_refs.jsonl`；`template_order.json` 控制聚合顺序。
- 当前 seed 目录固定为 `data/learning-plan-template-seed/`，包含 `learning_plan_templates.jsonl`、`learning_plan_template_problem_refs.jsonl`、`learning_plan_template_seed_manifest.json`、`learning_plan_template_seed_metadata.md` 四个文件。
- `data/learning-plan-template-seed/` 是后端导入使用的聚合产物，必须由 `tools/learning_plan_template_seed/prepare_template_seed.py` 从模板源目录生成，不再作为人工维护入口。
- 后端导入已经要求每个模板有完整 source attribution、非空 phases、至少一个 problem ref；缺失本地题目保留在 refs 和 metadata，但不进入生成草稿。
- 从模板生成草稿必须满足：阶段周数合计等于总周期；模板阶段承载完整路线；所有本地匹配 refs 默认进入草稿推荐题；缺失 refs 只保留在模板明细和草稿 metadata；草稿状态继续落为 `GENERATED`，并复用现有草稿确认流程。
- 本轮将热题模板扩展为完整 100 题并完成生成与回归验证：`python3 -m unittest discover -s tools -p '*_test.py'` 共 61 个测试通过；模板导入、草稿生成和模板 API 定向 Maven 测试共 24 个测试通过；前端模板入口定向测试共 1 个文件、5 个测试通过。本轮未执行 `make db-seed`，运行中的本地数据库需在发布时通过该命令重新导入 35 模板 / 1738 refs / 1699 匹配 / 39 缺失。

实施边界：

- seed 只保存题单元数据、阶段目标、推荐理由和来源归因；不复制第三方题解、文章正文、图示、代码或题面。
- 模板内部化不是一比一搬运外部计划，而是把高价值路线重建为 algo-mentor 自己的阶段计划。
- 第一批 P0 目标是先把内部模板池扩展到 10 个可发布模板；当前可直接落入 phase/problem-ref 模型的 P1/P2 路线已继续扩展到 35 个。剩余长周期 CS 路线需等非题目任务和外链材料模型完善后再处理。

## 待执行列表

状态枚举：`当前已完成`、`已生成待回归`、`进行中`、`待执行`、`后续批次`。

| 批次 | 状态 | 任务 | 输出 | 验收条件 |
| --- | --- | --- | --- | --- |
| P0 | 当前已完成 | 按完整阶段规则重建 NeetCode seed | 2 个模板、225 条 problem refs、manifest、metadata | 已复跑 Python seed 测试、真实 seed 导入测试、模板生成草稿测试；212 条本地匹配题默认进入草稿 |
| P0 | 当前已完成 | 固化第一批 10 个内部模板的实施口径 | 本文档、templateId 列表、数据来源策略、子 agent 模式 | 每个模板都有稳定 ID、标题、周期、intent、level、来源策略和验收重点 |
| P0 | 当前已完成 | 把第一批剩余 8 个模板转成 seed | 8 个新增模板、284 条新增 problem refs、更新 manifest 和 metadata | 每个模板有完整归因、非空 phases、至少一个本地匹配题；新增模板合计匹配 277、缺失 7 |
| P0 | 当前已完成 | 将 `prepare_template_seed.py` 从 NeetCode 单源脚本演进为多 source 生成器 | source adapter、统一 schema 校验、确定性排序、统计报告 | 相同输入重复生成 hash 稳定；四个 seed 文件一次性生成；metadata 不输出 Java 不可导入的 null |
| P0 | 当前已完成 | 验证后端导入和草稿生成 | 后端测试、导入审计、草稿生成样例 | 10 个模板均可导入；2/4/5/6/12 周模板可生成草稿；匹配 refs 默认全部进入草稿 |
| P1-A 批次 1 | 当前已完成 | 扩展核心专项第一波 | Trees、Backtracking、Heap、Greedy、Stack/Monotonic Stack、Bit Manipulation 六个专项模板 | 已生成 6 模板 / 122 refs / 122 匹配 / 0 缺失；每模板题量不低于 15 条 refs；Python seed、后端导入和草稿生成回归已通过 |
| P1-A 批次 2 | 当前已完成 | 扩展核心专项第二波 | Linked List、Union Find + Advanced Graph、Prefix Sum、Trie、Intervals、Data Structure Design 六个专项模板 | 已生成 6 模板 / 111 refs / 111 匹配 / 0 缺失；每模板不少于 18 条 refs，每阶段不少于 7 条匹配题 |
| P1-A 扩展 | 当前已完成 | 扩展动态规划进阶专项 | `topic_dp_advanced`，4 阶段覆盖区间、树形、状压、数位与博弈 DP | 31 refs / 31 匹配 / 0 缺失；四阶段每阶段至少 7 题，草稿生成回归已通过 |
| P1-B | 当前已完成 | 扩展模式化和面试路线模板 | 已完成 TIH essentials、剑指 Offer、seanprashad Beginner、程序员面试金典、LeetCode 75、LeetCode 面试经典 150、代码随想录完整版和 labuladong 核心算法框架 | 本轮 2 个 roadmap 模板 / 208 refs / 208 匹配 / 0 缺失；P1-B 候选中 Experienced Roadmap 和 NeetCode pattern 因高度重复不再单列 |
| P1 | 当前已完成 | 增强多来源 manifest 和导入审计 | `sources` 统计、每模板来源版本、每来源匹配率 | import run metadata 已保存完整 manifest，可定位来源版本、路径和缺失题 |
| P1 | 待执行 | 前端模板入口优化 | 模板列表分组、标签筛选、模板详情缺失题提示 | 用户能按面试冲刺、长期学习、专项突破、复盘选择模板 |
| P2 | 当前已完成 | 当前 phase/problem-ref 模型兼容的非算法专项模板 | SQL 50、JavaScript 30 天、Pandas 入门、Pandas 30 天 | 4 模板 / 128 refs / 123 匹配 / 5 缺失；前端编程语言选项已补充 SQL |
| P2 | 后续批次 | 非题目任务和外链型长周期计划 | Coding Interview University、CS-Notes | 需要先扩展任务类型和外链学习材料模型 |

## 第一批 10 个内部模板

这 10 个是 P0 首批用户可见模板，用来覆盖最高频目标并打通“资料源 -> seed -> 导入 -> 模板草稿 -> 草稿确认”的稳定闭环。它们不是全部仓库转换任务；一个外部仓库可能产出多个模板，一个内部模板也可能融合多个来源。首批先控制在 10 个，是为了保证每个模板都有完整阶段、题目引用、本地匹配统计、授权说明和草稿生成回归，而不是一次性堆大量低质量题单。

| templateId | 状态 | 用户看到的标题 | 参考来源 | 周期 | intent | level | 数据来源策略 | 验收重点 |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| `neetcode_150_systematic_interview` | 当前已完成 | NeetCode 150 系统面试计划 | NeetCode 150、LeetCode Top Interview 150 | 12 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 直接内置；NeetCode MIT 题单元数据固定 commit，LeetCode 官方计划只作目标参考 | 150 refs，匹配 143、缺失 7；真实 seed 导入和 12 周草稿生成已回归 |
| `neetcode_blind_75_interview_core` | 当前已完成 | NeetCode Blind 75 面试核心计划 | Blind 75、LeetCode 75、NeetCode Blind 75 | 4 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 直接内置；NeetCode MIT 题单元数据固定 commit，官方 LeetCode 75 只作外链参考 | 75 refs，匹配 69、缺失 6；4 周草稿默认包含全部本地匹配题 |
| `tih_best_practice_50_5weeks` | 当前已完成 | 5 周面试冲刺计划 | Tech Interview Handbook Best Practice 50、Grind 75 | 5 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 直接内置 TIH MIT 题单元数据；Grind 75 仅作外链和结构参考 | 61 refs，匹配 55、缺失 6；optional 11、premium 6 均可审计 |
| `cn_algorithm_foundation_12weeks` | 当前已完成 | 中文系统刷题入门计划 | 代码随想录、LeetCode Programming Skills、NeetCode beginner | 12 周 | `LONG_TERM_LEARNING` | `BEGINNER` | 参考后重建；不复制代码随想录文章、图示或题解，按本地题库重建阶段和题目 | 46 refs，匹配 46、缺失 0；12 周 9 阶段草稿生成已覆盖 |
| `topic_dynamic_programming_foundation` | 当前已完成 | 动态规划专项突破计划 | LeetCode Dynamic Programming、labuladong DP、halfrost DP | 4 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | halfrost MIT 可直接内置题号/标签；LeetCode 官方和 labuladong 作参考后重建 | 23 refs，匹配 23、缺失 0；按一维、背包、子序列、状态机/回文拆阶段 |
| `topic_graph_bfs_dfs` | 当前已完成 | 图论专项突破计划 | LeetCode Graph Theory、代码随想录图论、halfrost BFS/DFS/Union Find | 4 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | halfrost MIT 题号/标签直接内置；其他来源外链参考和阶段重建 | 20 refs，匹配 19、缺失 1（`alien-dictionary`）；草稿不推荐未匹配题 |
| `topic_binary_search_boundaries` | 当前已完成 | 二分与边界专项计划 | LeetCode Binary Search、halfrost Binary Search | 2 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | halfrost 可直接内置；LeetCode 官方计划外链参考 | 18 refs，匹配 18、缺失 0；2 周 2 阶段草稿生成已覆盖 |
| `topic_sliding_window_two_pointers` | 当前已完成 | 滑动窗口与双指针专项计划 | NeetCode pattern、halfrost Two Pointers / Sliding Window、Tech Interview Handbook | 2 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | 多源参考后重建；只内置 slug、题号、标签和自写推荐理由 | 15 refs，匹配 15、缺失 0；固定窗口、可变窗口和双指针分层 |
| `leetcode_top_100_liked_revision` | 当前已完成 | LeetCode 热题 100 | LeetCode Hot 100 结构化题目元数据 | 10 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 保留 Hot 100 完整题目 ID、标题、难度和顺序，再按题型重组为 10 个阶段；不复制题面、题解或代码 | 100 refs，匹配 99、缺失 1（`meeting-rooms-ii`）；本地匹配题默认进入草稿 |
| `programming_skills_implementation_foundation` | 当前已完成 | 编程基础与实现力计划 | LeetCode Programming Skills、本地 Easy/Medium 题库 | 4 周 | `PRACTICE_GOAL` | `BEGINNER` | 外链参考后用本地题库重建；官方计划只作为目标描述和入口 | 34 refs，匹配 34、缺失 0；Easy/Medium 基础实现路线完整 |

## 后续模板扩展池

P0 完成后，后续任务按资料源和用户场景继续拆分。以下模板不进入 P0 首批 10 个，但需要在 P1/P2 计划中保留：

| 批次 | 候选模板方向 | 参考来源 | 进入后续批次原因 |
| --- | --- | --- | --- |
| P1-A 批次 1 | Trees 专项、Backtracking 专项、Heap / Priority Queue 专项、Greedy 专项、Stack / Monotonic Stack 专项、Bit Manipulation 专项 | NeetCode pattern、halfrost 标签、TIH algorithm guides、本地题库 tag | 当前已完成；适合能力画像命中薄弱标签 |
| P1-A 批次 2 | Linked List、Union Find + Advanced Graph、Prefix Sum、Trie、Intervals、Data Structure Design 专项 | halfrost 标签、本地题库 tag | 当前已完成；补齐高频薄弱标签并保持 111 refs 全部本地匹配 |
| P1-A 扩展 | 动态规划进阶专项 | 本地题库 dynamic-programming / bitmask / tree / game-theory 标签 | 当前已完成；31 refs 全部匹配，覆盖区间、树形、状压、数位与博弈 DP |
| P1 | NeetCode pattern track 系列 | `neetcode-gh/leetcode` | NeetCode 已有 pattern 结构，但 P0 先只发布 75/150 两条高认知路线 |
| P1 | TIH algorithm essentials / recommended 系列 | `yangshun/tech-interview-handbook` | 当前已完成 `tih_algorithm_essentials`；119 refs、110 匹配、9 缺失 |
| P1 | leetcode-patterns Beginner / Experienced Roadmap | `seanprashad/leetcode-patterns` | Beginner 当前已完成；Experienced 与现有 Blind 75 高度重合，暂不单独发布 |
| P1 | LeetCode 75 / Top Interview 150 官方路线 | LeetCode 官方 Study Plan、本地题库 | 当前已完成；两模板合计 225 refs、225 匹配、0 缺失 |
| P1 | 代码随想录完整版 / labuladong 算法框架 | `youngyangyang04/leetcode-master`、`labuladong/fucking-algorithm`、本地题库 | 当前已完成；两模板合计 208 refs、208 匹配、0 缺失 |
| P2 | Coding Interview University / CS-Notes 长周期 CS 综合计划 | `jwasham/coding-interview-university`、`CyC2018/CS-Notes` | 范围超过刷题计划，需要非题目任务、外链资源和学习材料模型支持 |
| P1 | doocs / 剑指 Offer / 面试金典路线 | `doocs/leetcode`、本地题库 | 当前已完成；两模板合计 184 refs、184 匹配、0 缺失 |
| P2 | SQL 50、JavaScript 30 天、Pandas 入门 / 30 天 | LeetCode 官方 Study Plan | 当前已完成；四模板合计 128 refs、123 匹配、5 缺失 |
| P2 | Advanced SQL 50、Premium Algo 100 | LeetCode 官方 Study Plan | 官方接口当前不返回计划数据，暂不生成空模板 |

## 资料源和仓库转换清单

本节和“第一批 10 个内部模板”是输入与输出的关系：

- “第一批 10 个内部模板”定义 P0 要交付给用户选择的模板结果，是产品输出清单。
- “资料源和仓库转换清单”定义这些模板背后的资料来源、仓库处理方式和子 agent 执行单元，是工程输入和任务派发清单。
- 两者不是一一对应关系，而是多对多关系：一个资料源可以产出多个模板，一个模板也可以融合多个资料源。

每完成一个来源转换，必须同时更新本节勾选状态、第一批模板表状态、seed manifest 数字和 metadata markdown。`已生成待回归` 只能表示 seed 产物已经存在；只有完成 Python、后端导入、草稿生成三类验证后，才能改为 `当前已完成`。

| 完成标记 | 来源或仓库 | 覆盖模板 | 转换方式 | 子 agent 任务 | 完成时必须记录 |
| --- | --- | --- | --- | --- | --- |
| [x] 当前已完成 | `neetcode-gh/leetcode` | `neetcode_blind_75_interview_core`、`neetcode_150_systematic_interview` | 直接内置 MIT 题单元数据，固定 commit `9907b7fed441fa55083c0751e208b7197101dbba` | NeetCode 重建 | 225 refs，匹配 212、缺失 13；缺失题示例已写入 metadata；验证命令见当前状态 |
| [x] 当前已完成 | `yangshun/tech-interview-handbook` | `tih_best_practice_50_5weeks`、`tih_algorithm_essentials` | 直接内置 MIT 题单元数据；不复制文章正文，固定 commit `8ee2acb54a05c4add123a824d15e7dfc4e703b2f` | TIH 面试路线 | 两模板合计 180 refs，匹配 165、缺失 15；算法专题按 18 个有效主题重组为 6 阶段 |
| [x] 当前已完成 | `youngyangyang04/leetcode-master` | `cn_algorithm_foundation_12weeks`、`carl_algorithm_roadmap_full` | 参考后重建；不复制文章、图示、题解和 README 原文，固定 commit `86f78fde8cb62d10c3b5e38b7e6b6e0705850f92` | 中文系统路线 | 代码随想录完整版 11 阶段 / 144 refs，全部匹配；阶段说明和复盘建议均为自写 |
| [x] 当前已完成 | `halfrost/LeetCode-Go` | `topic_dynamic_programming_foundation`、`topic_graph_bfs_dfs`、`topic_binary_search_boundaries`、`topic_sliding_window_two_pointers` | 直接内置 MIT 题号、slug、标签和难度；复杂度只作内部参考，固定 commit `3bcc916680298295e06060ca9790304c1f1b78b6` | 专项模板包 | P0 四个专项合计 76 refs，匹配 75、缺失 1；每个模板题量不低于 15；MIT notice 已记录 |
| [x] 当前已完成 | `halfrost/LeetCode-Go` + 本地题库 tag 重组 | `topic_tree_binary_tree_foundation`、`topic_backtracking_foundation`、`topic_heap_priority_queue`、`topic_greedy_strategies`、`topic_stack_monotonic`、`topic_bit_manipulation` | 直接内置 halfrost MIT 题号/标签中可审计元数据，并用本地题库 tag 补齐 heap/greedy 等专题结构；固定 commit `3bcc916680298295e06060ca9790304c1f1b78b6` | P1-A 批次 1 | 六个专项合计 122 refs，匹配 122、缺失 0；2 周模板 2 阶段，3 周模板 3 阶段；Python、后端导入、草稿生成回归已通过 |
| [x] 当前已完成 | `algo-mentor` 本地题库 | `topic_dp_advanced` | 按 dynamic-programming、bitmask、tree、game-theory 标签精选并人工分阶段 | P1-A 动态规划进阶 | 31 refs，匹配 31、缺失 0；4 周 4 阶段，草稿生成回归已通过 |
| [x] 当前已完成 | `labuladong/fucking-algorithm` | `topic_dynamic_programming_foundation`、`labuladong_algo_thinking` | 参考后重建；不复制文章正文、图示或代码，固定 commit `b1f23cb9605f6146ff78bafad71e795176439b99` | 专项和算法框架路线 | 算法框架模板 8 阶段 / 64 refs，全部匹配；题目从本地题库精选重建 |
| [x] 当前已完成 | LeetCode 官方 Study Plan | `leetcode_75_core_sprint`、`leetcode_top_interview_150`、`leetcode_sql_50`、`leetcode_javascript_30_days`、`leetcode_pandas_introduction`、`leetcode_pandas_30_days` 等 | 官方结构化计划元数据和外链参考，固定到 `accessed-2026-07-28`，并与本地题库匹配 | 面试、复盘、实现力和非算法练习模板 | 本轮四套非算法计划 128 refs / 123 匹配 / 5 缺失；未复制题面、题解或付费内容 |
| [x] 当前已完成 | `seanprashad/leetcode-patterns` | `leetcode_patterns_beginner_roadmap` | 固定 commit `514b971570bcc8d6cd9a354d561245e8ef52a603`，直接转换 Beginner roadmap 顺序和 questions 元数据 | P1 模式化模板 | 10 阶段 / 68 refs / 64 匹配 / 4 缺失；Experienced 因与 Blind 75 高度重合暂不单列 |
| [x] 当前已完成 | `doocs/leetcode` | `sword_offer_classic`、`cracking_coding_interview_classic` | 固定 commit `c0a8f9df1b2e6e2da564acda398d345cb3dd0710`，直接转换 `lcof/lcof.json` 和 `lcci/lcci.json` 结构化题单 | P1 中文经典路线 | 剑指 Offer 8 阶段 / 75 refs，程序员面试金典 10 阶段 / 109 refs；合计 184 refs 全部匹配 |
| [ ] 后续批次 | `jwasham/coding-interview-university`、`CyC2018/CS-Notes` | 长周期 CS/综合学习模板 | 外链参考或任务型路线，不进入当前算法刷题 seed 主线 | P2 综合学习 | 非题目任务展示方式、外链授权备注、是否需要新数据模型 |

### 完成标记规则

1. 子 agent 完成一个来源后，把候选输出交给主 agent，不直接覆盖 `data/learning-plan-template-seed/` 四个固定文件。
2. 主 agent 合并来源 adapter 后重新生成四个 seed 文件，并更新 manifest、metadata 和本节完成标记。
3. 每次从 `待执行` 推进到 `已生成待回归`，必须记录模板数、refs 数、matched/missing、缺失题示例和来源版本。
4. 每次从 `已生成待回归` 推进到 `当前已完成`，必须记录已运行的验证命令和结果；如果只完成 seed 生成但未跑后端测试，不得标记为 `当前已完成`。
5. 若某来源因授权、匹配率或结构问题被暂停，在完成标记中保留为 `待执行`，并在“完成时必须记录”列补充阻塞原因和下一步。

## Subagent 执行模式

主 agent 负责派发、集成和验收。子 agent 按来源或批次处理，不能同时直接改四个固定 seed 文件；统一由主 agent 做最终合并，避免 JSONL 排序、统计和 checksum 冲突。

### 主 agent 职责

- 维护第一批模板总表、templateId 命名、intent/level/difficulty 枚举和优先级。
- 为每个子任务提供输入包：目标模板、参考来源、固定 schema、当前本地题库、现有 seed 文件和验收条件。
- 汇总子 agent 输出，更新 `data/learning-plan-template-sources/templates/<templateId>/` 下的模板源文件；只有新增解析能力时才更新 `tools/learning_plan_template_seed/prepare_template_seed.py`。
- 一次性生成并提交 `data/learning-plan-template-seed/` 下四个固定文件。
- 运行最小相关验证：Python seed 测试、后端导入测试、模板草稿生成测试。
- 审核来源归因、授权备注、缺失题列表、matched/missing 统计和用户可见标题。

### 子任务拆分

| 子任务 | 建议处理者 | 输入 | 输出 | 写入范围 | 验收条件 |
| --- | --- | --- | --- | --- | --- |
| NeetCode 重建 | 来源子 agent | 固定 commit、目标两个模板、完整阶段规则 | 完整阶段规划、refs、matched/missing 报告 | 只提交候选片段给主 agent；如需改脚本，由主 agent 合并 | 2 个模板统计可审计；problem refs 排序、阶段归属和 matched/missing 稳定 |
| TIH 面试冲刺 | 来源子 agent | TIH 仓库固定 commit、Best Practice 50 Markdown、local problems | `tih_best_practice_50_5weeks` 候选模板、refs、license note | 临时候选目录或 PR patch；不直接覆盖 seed 目录 | core/optional 标记清楚；MIT 归因完整；5 周计划生成 5 个有效阶段 |
| 中文入门路线 | 来源子 agent | 代码随想录目录、Programming Skills 外链、本地题库标签 | `cn_algorithm_foundation_12weeks` 阶段草案和题目候选 | 临时候选目录；不复制第三方正文 | 阶段顺序可解释；所有推荐理由为自写；无 LICENSE 来源只作为参考后重建 |
| 专项模板包 | 批次子 agent | halfrost meta、NeetCode pattern、LeetCode 官方专题外链 | DP、图、二分、滑窗/双指针四个专项候选 | 临时候选目录；仅提交候选 JSONL 片段 | 每个模板至少 15 条 refs；2 周模板生成 2 阶段，4 周模板生成约 3 阶段 |
| 热题和实现力模板 | 批次子 agent | LeetCode Hot 100 结构化题目元数据、本地题库 | 热题 100 和编程基础模板候选 | 临时候选目录；不直接写前端 | 热题模板保留完整 100 refs 与缺失审计；基础模板 Easy/Medium 结构合理 |
| 集成验收 | 主 agent | 所有候选片段、脚本、测试 | 四个 seed 文件、测试结果、变更说明 | `tools/learning_plan_template_seed/`、`data/learning-plan-template-seed/`、必要测试文件 | 所有模板可导入、可查询、可生成草稿；manifest/metadata 数字一致 |

### 子 agent 交付格式

每个子 agent 只交付一个来源或一个明确模板包，交付说明必须包含：

- `taskId`：例如 `template-source-tih-best-practice-50`。
- `templateIds`：本次覆盖的模板 ID。
- `sourceAudit`：来源名称、URL、固定 commit 或访问日期、源文件路径、许可证、是否直接内置。
- `candidateOutput`：候选模板 JSON、problem refs JSON、阶段规划说明和缺失题清单；候选文件放在临时目录或补丁中，不直接写 seed 目录。
- `stats`：模板数、refs 数、matched/missing、premium/optional 数量、每阶段题量。
- `validation`：已运行命令、结果、未运行原因和剩余风险。
- `handoffNotes`：需要主 agent 人工确认的 slug/frontendId 映射、授权边界和产品命名问题。

### 长任务质量 checkpoint

- 每个来源转换前，子 agent 必须先按 `.codex/skills/learning-plan-template-integrator/references/source-evaluation-checklist.md` 评估，结构、匹配或归因不过关则只保留为研究备注。
- 每个来源只在通过本地匹配和阶段周数校验后进入主 agent 合并队列；不得把待人工确认的模糊匹配写入 seed。
- 主 agent 每合并一个来源就运行 Python seed 单测和最小 schema 校验；后端导入和草稿生成测试至少在 P0 全部来源合并后跑一次。
- 任何一次合并不得手工编辑聚合 JSONL、manifest 或 metadata；模板源目录也不维护本地匹配数、缺失列表和难度分布等派生统计，这些必须由生成器统一输出。
- 子 agent 并行时只能读当前 seed 文件，写候选片段；主 agent 串行合并，避免四个固定文件出现排序和 checksum 冲突。

## Seed 转换规则

### 模板 JSONL 字段

每行一个模板，必须包含并校验以下字段：

- 身份和展示：`templateId`、`title`、`summary`、`goal`。
- 生成参数：`intent`、`defaultDurationWeeks`、`level`、`defaultWeeklyHours`、`programmingLanguage`、`difficultyPreference`、`interviewOriented`、`topicPreferences`。
- 用户边界：`targetAudience`、`difficultyMix`、`prerequisites`、`recommendedFor`、`notRecommendedFor`、`expectedOutcome`。
- 来源归因：`sourceName`、`sourceUrl`、`sourceCommit`、`sourceDataPath`、`sourceDescription`、`curationNotes`、`licenseNotice`。
- 扩展信息：`metadata`，用于记录 `sourceStrategy`、`matchedProblemCount`、`missingProblemCount`、`missingProblems`、`sourceProblemCount`、`sourceTags`、`premiumPolicy` 等。
- 阶段：`phases` 非空，阶段内包含 `phaseIndex`、`title`、`durationWeeks`、`focus`、`objectives`、`recommendedTags`、`acceptanceCriteria`、`reviewAdvice`。

枚举必须使用后端现有值：

- `intent`：`PRACTICE_GOAL`、`ABILITY_DIAGNOSIS`、`INTERVIEW_SPRINT`、`TOPIC_BREAKTHROUGH`、`MISTAKE_REVIEW`、`LONG_TERM_LEARNING`。
- `level`：`BEGINNER`、`INTERMEDIATE`、`ADVANCED`。
- `difficultyPreference`：`EASY`、`MEDIUM`、`HARD`、`MIXED`。

阶段规则：

- `phaseIndex` 从 1 开始连续递增。
- `durationWeeks` 合计必须等于 `defaultDurationWeeks`。
- 模板阶段是生成草稿的完整计划来源，不应被后端按旧预览规则合并到固定阶段数。
- 长周期计划可以有更多阶段，只要阶段周数、题目分配和用户执行节奏合理。

### Problem Ref 字段

每行一个模板题目引用，必须包含：

- `templateId`：必须指向存在的模板。
- `phaseIndex`：必须落在模板阶段范围内。
- `sortOrder`：阶段内排序，从 1 开始。
- `sourceOrder`：来源路线中的全局顺序，从 1 开始。
- `problemSlug`：本地题库匹配和后端生成草稿的主键。
- `sourceTitle`、`sourceDifficulty`、`pattern`、`sourceUrl`：用于展示、审计和推荐理由生成。
- `metadata`：至少包含 `matchedLocalProblem`，建议包含 `sourceFrontendId`、`sourceTags`、`sourceBucket`、`premium`、`optional`、`sourceLicense`、`originalPattern`。

排序规则：

- 同一模板内先按 `phaseIndex`，再按 `sortOrder`，最后按 `sourceOrder` 稳定排序。
- 同一题出现在多个模板时允许重复；同一模板内默认去重，除非某来源明确要求同题跨阶段复盘，此时必须在 metadata 记录原因。

### Manifest 和 Metadata 要求

`learning_plan_template_seed_manifest.json` 必须记录：

- `schemaVersion`、`generatedAt`、四个 seed 文件的 `path`、`bytes`、`sha256`。
- 全局 `templateCount`、`problemRefCount`、`matchedProblemCount`、`missingProblemCount`。
- 每个模板的 `title`、`phaseCount`、`problemCount`、`matchedProblemCount`、`missingProblemCount`、`difficultyMix`。
- 兼容当前后端的根级 `source` 字段；多来源阶段新增 `sources` 数组，按来源记录 `name`、`url`、`commitOrVersion`、`dataPath`、`licenseNotice`、`templateIds`、`problemRefCount`。

`learning_plan_template_seed_metadata.md` 必须面向产品和工程读者说明：

- 本次包含哪些模板、每个模板的目标用户、周期、难度分布和数据来源策略。
- 每个来源的固定版本、源路径、授权备注、是否直接内置、是否仅作外链参考。
- 每个模板的 matched/missing 数量、缺失题示例、premium/未收录题处理方式。
- 已知限制、后续补齐建议、发布前需要复核的授权事项。

### Slug 和 FrontendId 匹配策略

匹配优先级：

1. 有 LeetCode slug 时，先用规范化 slug 匹配本地 `data/seed/problems.jsonl` 的 `slug`。
2. 只有题号或 `frontendId` 时，用本地题库的 `frontendId` 反查 slug。
3. 标题只作为人工核对辅助，不作为自动匹配的唯一依据。
4. Premium、已下架、同名变体、中文标题不一致的题目必须进入 missing 审计，不允许模糊落到错误 slug。

规范化规则：

- slug 全部小写，去掉 URL query/hash，保留连字符形式。
- LeetCode URL 统一解析最后一个有效 path segment。
- 来源只给题号时，保留 `metadata.sourceFrontendId`，并在 metadata 记录是否找到本地 slug。

### 缺失题处理

- 缺失本地题目必须保留在 `learning_plan_template_problem_refs.jsonl`，并标记 `metadata.matchedLocalProblem=false`。
- 缺失题必须进入模板 `metadata.missingProblems` 和 metadata markdown 的缺失题示例。
- 后端导入会重新按当前数据库计算 matched/missing；seed 中的匹配统计只作为生成时审计。
- 从模板生成草稿时，缺失题不能进入 `LearningPlanProblemDraft`，但完整 refs 应保留在草稿 metadata。
- 如果某模板本地匹配题少于每阶段 3 题，应降低模板优先级或补充本地题库后再发布。

### 授权和来源记录规则

- 直接内置只允许保存题单元数据：slug、题号、题名、难度、标签、顺序、来源 URL、来源版本和自写推荐理由。
- `licenseNotice` 必须明确来源许可证、是否只使用元数据、是否排除题解/文章/图示/题面。
- 无 LICENSE、官方网页、Premium、CC-BY-NC 或 CC-BY-SA 来源默认不能直接复制结构化全文；第一版使用“参考后重建”或“外链参考”。
- 每个模板必须有独立 `sourceName/sourceUrl/sourceCommit/sourceDataPath`。多来源模板的主来源放在模板字段，其他来源放入 `metadata.secondarySources` 和 metadata markdown。

## 实施步骤

### 1. 脚本演进

1. 将 `tools/learning_plan_template_seed/prepare_template_seed.py` 从单一 NeetCode 常量脚本演进为多 source 注册式生成器。
2. 抽出通用 schema 校验、slug/frontendId 匹配、difficulty mix 统计、manifest 文件 hash、metadata markdown 渲染。
3. 为每个来源建立 adapter：NeetCode、TIH、halfrost、本地重建模板、外链参考模板。
4. 保持默认命令一次性生成四个固定 seed 文件，避免手工编辑 JSONL。
5. 增加针对多来源、缺失题、重复题、阶段周数、必填归因字段的 Python 单元测试。

### 2. Seed 生成

1. 固定每个直接使用来源的 commit 或版本；官方网页和无 LICENSE 来源记录访问日期和 URL。
2. 读取 `data/seed/problems.jsonl` 建立 `slug -> problem`、`frontendId -> slug` 索引。
3. 子 agent 输出候选模板和 refs 后，由主 agent 合并排序并生成四个文件。
4. 生成后检查模板数为 10，problem refs 覆盖每个模板至少一个引用且每个模板至少一个本地匹配题。
5. 更新 metadata markdown，确保第一批 10 个模板逐个有来源、授权、匹配统计和缺失题说明。

### 3. Seed 文件验证

1. 运行 `python3 -m unittest discover -s tools -p '*_test.py'`。
2. 增加或更新 `tools/learning_plan_template_seed/prepare_template_seed_test.py`，覆盖 10 模板输出、必填字段、manifest/metadata 存在、缺失题保留。
3. 用脚本检查 JSONL 每行可被 Jackson 解析需要的字段覆盖，避免 Python 输出字段名和 Java record 不一致。
4. 对四个 seed 文件做稳定排序和 hash 检查，确认重复生成没有非确定性 diff。

### 4. 后端导入验证

1. 运行 `LearningPlanTemplateSeedImportServiceTest`，覆盖每个模板必须有 refs、source attribution、非空 phases。
2. 需要时扩展导入测试，断言多来源 manifest 不破坏当前 `source.commit` 兼容读取。
3. 通过 `make db-seed` 或目标测试验证导入顺序仍在题库导入之后。
4. 查询导入后的模板表、阶段表和题目引用表，确认 matched/missing 与 manifest 统计一致。

### 5. 草稿生成验证

1. 扩展 `LearningPlanTemplateDraftServiceTest`，至少覆盖 2 周、4 周、5 周、6 周、12 周模板。
2. 断言每个生成草稿的 `durationWeeks` 等于输入或默认周期，阶段周数合计等于总周期。
3. 断言所有本地匹配 refs 默认进入草稿推荐题，且缺失 refs 不进入 `LearningPlanProblemDraft`。
4. 断言缺失题保留在草稿 metadata 的模板 refs 中，但不出现在 `LearningPlanProblemDraft`。
5. 对 `POST /api/learning-plans/drafts/from-template` 增加一个非 NeetCode 模板 API 测试，确认不触发 AI governance。

### 6. 前端入口更新建议

前端不作为 seed 内部化的阻塞项，但第一批 10 模板导入后建议补以下入口：

- 在新建方案页增加“从模板开始”入口，调用 `GET /api/learning-plan-templates`。
- 模板列表按 `INTERVIEW_SPRINT`、`LONG_TERM_LEARNING`、`TOPIC_BREAKTHROUGH`、`MISTAKE_REVIEW`、`PRACTICE_GOAL` 分组。
- 模板详情展示标题、摘要、周期、每周小时、level、阶段、来源和缺失题提示。
- 从模板生成草稿调用 `POST /api/learning-plans/drafts/from-template`，生成后复用现有草稿确认流程。
- 缺失题只做轻提示，不阻塞用户生成草稿。

## 风险和回滚方式

| 风险 | 影响 | 规避 | 回滚方式 |
| --- | --- | --- | --- |
| 来源授权边界不清 | 模板不能直接发布或需要下线 | 默认只保存元数据和自写说明；无 LICENSE 来源使用参考后重建或外链参考 | 删除受影响模板行和 refs，重新生成四个 seed 文件；保留其它模板 |
| slug/frontendId 匹配错误 | 草稿推荐到错误题目 | slug 优先、frontendId 次之、标题仅人工核对；missing 不做模糊匹配 | 修正 adapter 映射后重新生成 seed；后端 upsert 会替换阶段和 refs |
| 多来源 manifest 破坏导入 | `make db-seed` 或导入测试失败 | 保留根级 `source` 兼容字段，同时新增 `sources` 扩展字段 | 回退 manifest schema 到单源兼容形态；模板级 attribution 仍保留 |
| 模板过长导致草稿信息噪音 | 草稿题目较多，用户浏览成本上升 | 模板阶段承载完整路线；前端用分阶段摘要、折叠或密度优化降低噪音，不截断题单 | 保留模板但调整默认周期、阶段和排序；不必回滚数据库结构 |
| 本地题库覆盖不足 | 某些模板生成草稿题量偏少 | 发布前设最低匹配阈值；缺失题进入 metadata；必要时先补题库 seed | 暂时隐藏或不导入低匹配模板，后续补齐题库后恢复 |
| 子 agent 并行改同一文件冲突 | JSONL 顺序、统计和 checksum 不稳定 | 子 agent 只产候选片段，主 agent 统一合并生成 | 丢弃候选临时文件，重新从来源 adapter 生成 |
| 后端校验规则变化 | seed 通过脚本但导入或草稿生成失败 | Python schema 测试和 Java 导入/草稿测试一起跑 | 回退到上一版四个 seed 文件，或禁用 `algo-mentor.learning-plan-template.seed.enabled` |

最小回滚策略：

1. seed 内容错误但代码无问题：恢复上一版 `data/learning-plan-template-seed/` 四个文件，重新执行导入。
2. 单个模板错误：从模板 JSONL 和 refs JSONL 删除对应 `templateId`，更新 manifest/metadata 后重新导入。
3. 脚本演进错误：回退 `tools/learning_plan_template_seed/prepare_template_seed.py` 到单源版本，保留已生成且已验证的 seed 文件。
4. 数据库已导入错误模板：用修正后的 seed 重新导入，依赖 `template_id` upsert 和阶段/refs 替换；如需临时下线，由查询接口按 templateId 黑名单或导入配置隐藏。
