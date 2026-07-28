# 学习计划模板后续导入计划（P1 / P2 批次）

编写日期：2026-07-08

## 目的与范围

- 本文档只关注**后续需要导入的模板计划**，是 `docs/learning-plan-template-internalization-plan.md` 的批次续篇。
- P0 首批 10 个模板和 P1-A 两个专项批次已完成闭环；`data/learning-plan-template-seed/` 当前为 22 模板 / 742 refs / 722 匹配 / 20 缺失。本文档继续规划从 22 个扩展到约 26–30 个的路线。
- 所有资料源统一按“直接内置结构化题单/路线数据”处理：题号、slug、难度、标签、顺序、roadmap 阶段划分与来源 note 均可直接内置；仍不复制文章正文、题解代码与图示。seed 保留 `sourceName/sourceUrl/sourceCommit/licenseNotice` 做归因记录。

## 现状底座

- 本地题库：`data/seed/problems.jsonl` 共 3591 题（EASY 942 / MEDIUM 1815 / HARD 834）。
- 关键标签储量（决定专项模板可行性）：array 2067、string 834、dynamic-programming 662、greedy 433、binary-search 322、depth-first-search 319、bit-manipulation 293、matrix 265、breadth-first-search 250、two-pointers 248、tree 246、prefix-sum 242、heap-priority-queue 202、binary-tree 178、stack 173、graph 171、sliding-window 157、design 128、backtracking 119、linked-list 98、union-find 94、database 94、trie 57、monotonic-stack 65。
- 结论：除极少数外部源自带的 premium/锁题外，P1 专项模板全部能在本地满足“≥15 refs、每阶段 ≥3 匹配题”，无需先补题库即可开跑。

## 规划原则

1. 优先级排序按“最终计划价值 + 落地确定性”：专项突破（能力画像可命中）> 面试路线补全 > 复盘/模式化 > 长周期 CS / 非算法。
2. 复用 P0 已跑通闭环：子 agent 只产候选片段 → 主 agent 写入 `data/learning-plan-template-sources/templates/<templateId>/` → 一次性重生成四个固定 seed 文件 → Python / 后端导入 / 草稿三类验证。禁止手工改聚合 JSONL。
3. 每批控制规模（6 个左右），保证每个模板都有完整阶段、题目引用、matched/missing 统计、来源归因和草稿生成回归。
4. 一个主题只保留一个用户可见专项，避免 NeetCode pattern track 与专项主题重复造模板。

## 批次总览

| 批次 | 主题 | 模板数 | 优先级 | 备注 |
| --- | --- | ---: | --- | --- |
| P1-A 批次 1 | 核心专项突破（第一波） | 6 | 最高 | 题库全就绪、直接内置，先做 |
| P1-A 批次 2 | 核心专项突破（第二波） | 6 | 高 | 已完成，补齐剩余高频薄弱标签 |
| P1-B | 面试路线 / 复盘 / 模式化 roadmap | 5–6 | 中 | 含 seanprashad 两条 roadmap、代码随想录扩容、doocs 中文经典 |
| P2 | 长周期 CS / 非算法 | 视依赖 | 低 | SQL 50 可提前，其余待数据模型扩展 |

---

## P1-A｜专项突破补全（`TOPIC_BREAKTHROUGH`）

现有专项仅 DP、图、二分、滑窗/双指针 4 个。以下补齐最高频薄弱标签，可由能力画像按薄弱 tag 命中。数据底座：halfrost `ctl/meta/*` + 本地题库 tag 重组。

### 批次 1（先做）

> 导入状态：已完成并写入固定 seed 目录，批次合计 6 模板 / 122 refs / 122 匹配 / 0 缺失；Python seed、后端导入和草稿生成回归已通过，状态已同步到 `docs/learning-plan-template-internalization-plan.md`。

| templateId | 标题 | 周期 | level | 阶段骨架 | 目标 refs |
| --- | --- | ---: | --- | --- | ---: |
| `topic_tree_binary_tree_foundation` | 树与二叉树专项 | 3 周 | INTERMEDIATE | 遍历(前中后层)→BST 性质→路径/LCA→构造与序列化 | 24–30 |
| `topic_backtracking_foundation` | 回溯专项突破 | 3 周 | INTERMEDIATE | 子集/组合/排列→棋盘(N 皇后/数独)→字符串切割→剪枝与约束搜索 | 18–22 |
| `topic_heap_priority_queue` | 堆与优先队列专项 | 2 周 | INTERMEDIATE | TopK/第 K 大→多路合并→调度配合→双堆求中位数 | 15–18 |
| `topic_greedy_strategies` | 贪心策略专项 | 3 周 | INTERMEDIATE | 区间调度→跳跃/加油站→分配/找零→字符串与排序贪心 | 20–24 |
| `topic_stack_monotonic` | 栈与单调栈专项 | 2 周 | INTERMEDIATE | 基础栈/括号→表达式求值→单调栈(温度/柱状图)→单调队列 | 16–20 |
| `topic_bit_manipulation` | 位运算专项 | 2 周 | INTERMEDIATE | 基础位运算→异或技巧→状压/子集枚举→进阶(单/双数位) | 15–18 |

### 批次 2

> 导入状态：已完成并写入固定 seed 目录，批次合计 6 模板 / 111 refs / 111 匹配 / 0 缺失；各模板每阶段至少 7 道匹配题，Python seed、后端导入和草稿生成回归已通过，并已写入本地数据库。

| templateId | 标题 | 周期 | level | 阶段骨架 | 目标 refs |
| --- | --- | ---: | --- | --- | ---: |
| `topic_linked_list` | 链表专项 | 2 周 | BEGINNER | 基础操作/反转→双指针(环/中点)→合并/排序→复杂结构(复制/LRU 入门) | 15–18 |
| `topic_union_find_and_advanced_graph` | 并查集与进阶图论 | 3 周 | INTERMEDIATE | 并查集基础/应用→拓扑排序→最短路(Dijkstra/BF)→MST 选修 | 18–24 |
| `topic_prefix_sum_difference` | 前缀和与差分专项 | 2 周 | INTERMEDIATE | 一维前缀和→二维前缀和/矩阵→差分数组→前缀和+哈希 | 15–20 |
| `topic_trie_and_string_advanced` | 字典树与字符串进阶 | 2 周 | INTERMEDIATE | 字典树构建/检索→前缀应用→字符串匹配→回文/编辑距离衔接 | 15–18 |
| `topic_intervals_scheduling` | 区间与调度专项 | 2 周 | INTERMEDIATE | 区间合并→区间覆盖/删除→会议室/调度→扫描线入门 | 15–18 |
| `topic_data_structure_design` | 数据结构设计专项 | 2 周 | INTERMEDIATE | 栈/队列设计→LRU/LFU→迭代器/随机集合→前缀树/时间序列设计 | 15–18 |

> DP 进阶（区间/树形/状压/数位）可作为 `topic_dp_advanced`（3–4 周）在批次 2 之后补充，定位为 `topic_dynamic_programming_foundation` 的延伸。

**统一验收口径**：每模板 ≥15 refs、每阶段 ≥3 本地匹配题；2 周=2 阶段、3 周≈3 阶段；缺失题进 metadata 不进草稿；来源归因完整。

---

## P1-B｜面试路线 / 复盘 / 模式化 roadmap

> **导入现状（截至 2026-07-28）**：下表来源在当前 seed 中均**尚未真正内置为完整题单**——seanprashad / doocs / CS 完全缺席；labuladong 仅作结构参考、贡献 0 refs；代码随想录仍只有部分参考后重建内容。本批次的目标是把通过授权和结构评估的路线补齐。

| templateId | 标题 | 周期 | intent | 来源与策略 |
| --- | --- | ---: | --- | --- |
| `leetcode_patterns_beginner_roadmap` | 模式化入门路线 | 8–10 周 | `LONG_TERM_LEARNING` | 直接内置 seanprashad `questions.json`（179 题）+ Beginner roadmap（11 阶段），note 作推荐理由素材 |
| `leetcode_patterns_experienced_roadmap` | 模式化进阶路线 | 10–14 周 | `INTERVIEW_SPRINT` | 直接内置 seanprashad Experienced roadmap（15 阶段） |
| `carl_algorithm_roadmap_full` | 代码随想录完整刷题路线 | 16 周 | `LONG_TERM_LEARNING` | 直接内置 README 完整模块顺序 + 每模块题单（数组→链表→哈希→字符串→双指针→栈队列→树→回溯→贪心→DP→单调栈→图论）；把现有 25 refs 的部分重建升级为完整题单，作为 `cn_algorithm_foundation_12weeks` 的完整版 |
| `labuladong_algo_thinking` | labuladong 算法思维训练 | 6–8 周 | `LONG_TERM_LEARNING` | 直接内置其“快速/完整学习规划” + DP/数据结构设计专题题单（当前 0 refs，需从结构参考升级为真正内置） |
| `leetcode_75_core_sprint` | LeetCode 75 核心冲刺 | 6 周 | `INTERVIEW_SPRINT` | 官方 LeetCode 75 结构 + 本地题库，区别于 Blind 75 |
| `tih_algorithm_essentials` | 面试主题 essential 专项 | 6 周 | `TOPIC_BREAKTHROUGH` | 直接内置 TIH `algorithms/*.md` essential/recommended（21 主题 / 126 唯一 slug）→ phase.objectives |
| `sword_offer_classic` | 剑指 Offer 经典路线 | 6–8 周 | `INTERVIEW_SPRINT` | 直接内置 doocs 剑指 Offer 题单，面向中文经典面试题 |
| `cracking_coding_interview_150`（可选） | 面试金典路线 | 8 周 | `INTERVIEW_SPRINT` | 直接内置 doocs 面试金典题单 |

> NeetCode pattern track 与上述专项主题高度重叠，只在需要补 P1-A 未覆盖模式（如 Math & Geometry、Advanced Graph）时按需追加，不重复造模板。

---

## P2｜长周期 CS 与非算法

| templateId | 方向 | 来源 | 前置依赖 |
| --- | --- | --- | --- |
| `leetcode_sql_50` / `advanced_sql_50` | SQL 练习 / 进阶 | 官方 SQL Study Plan + 本地 `database`（94 题） | 题库已就绪，**P2 中可提前**；需前端/练习入口支持 SQL 题域 |
| `cs_interview_university_longterm` | 长周期 CS 综合 | jwasham/coding-interview-university | 需“非题目任务 + 外链学习材料”数据模型 |
| `cs_notes_comprehensive` | CS 基础补强 | CyC2018/CS-Notes | 同上，外链为主 |
| `cracking_coding_interview_150`（可选） | 面试金典辅助 | doocs | 中文覆盖补充，优先级低于专项 |
| `js_30_days` / `pandas_plans` | JS / 数据分析 | 官方 JS30 / Pandas | 偏离算法主线，需题库域扩展，最低优先 |

---

## 缺失题处理

- 当前 20 缺失均为外部源自带的 premium/锁题（`encode-and-decode-strings`、`meeting-rooms(-ii)`、`walls-and-gates`、`alien-dictionary`、`graph-valid-tree`、`number-of-connected-components-in-an-undirected-graph`）。
- P1 专项走本地 tag 重组，基本不产生新缺失。
- 短期维持现状：缺失题保留在 refs + metadata、不进草稿、前端轻提示。
- 可选中期动作：若要把 NeetCode 150 匹配率补满，单独补这几道锁题的本地题库 seed 后重生成；**不阻塞 P1**。

## 生成与验证流程（复用 P0）

对每个批次：

1. 主 agent 派发输入包：templateId、参考来源 + 固定 commit、schema、当前题库索引、现有 seed、验收条件。
2. 子 agent 按来源/批次产候选片段（不碰四个固定文件），交付 `sourceAudit / candidateOutput / stats / validation / handoffNotes`。
3. 主 agent 合并到模板源目录；只有新增外部解析能力时才调整 `prepare_template_seed.py` adapter，然后一次性重生成四个固定 seed 文件 + manifest + metadata。
4. 三类回归：
   - `python3 -m unittest discover -s tools -p '*_test.py'`
   - 后端 `LearningPlanTemplateSeedImportServiceTest` / `LearningPlanTemplateDraftServiceTest` / `LearningPlanControllerTest`
   - 新周期（如 3 周专项）在草稿测试补断言：阶段周数合计 = 总周期、本地匹配 refs 全进草稿、缺失 refs 只进 metadata。
5. 同步更新：`docs/learning-plan-template-internalization-plan.md` 待执行表与转换清单勾选、manifest `sources` 数组、metadata markdown 的 matched/missing/缺失题示例。

**P1 顺带的两项工程增强**：

- manifest / 导入审计增强：`sources` 数组补每来源版本、路径、匹配率；import run metadata 能定位每来源缺失题。
- 前端模板入口：按 `INTERVIEW_SPRINT / LONG_TERM_LEARNING / TOPIC_BREAKTHROUGH / MISTAKE_REVIEW / PRACTICE_GOAL` 分组 + 标签筛选 + 缺失题轻提示。

## 排期建议

1. **P1-A 批次 1**（tree / backtracking / heap / greedy / stack / bit）——已完成。
2. **P1-A 批次 2**（linked-list / union-find+advanced-graph / prefix-sum / trie / intervals / design）——已完成；其后可补 `topic_dp_advanced`。
3. **P1-B**（seanprashad 两条 roadmap、代码随想录完整版、leetcode_75、tih_algorithm_essentials、可选 doocs 剑指 Offer）——下一执行批次；同期做 manifest 审计增强 + 前端入口。
4. **P2**（先做 SQL 50；CS/JS/Pandas 待数据模型扩展）。

完成后模板池预计从 10 → 约 26–30 个，覆盖：系统面试 / 短期冲刺 / 中文入门 / 全套主题专项 / 模式化 roadmap / 复盘 / SQL。
