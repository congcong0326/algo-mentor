# 学习计划模板内部化实施计划

编写日期：2026-07-06

## 背景和范围

本计划面向“把调研资料源转换为 algo-mentor 内部学习计划模板”的工程落地。资料源评估以 `docs/learning-plan-template-source-research.md` 为准，seed 最小闭环以 `docs/learning-plan-template-seed-design.md` 为准。

当前状态：

- 旧版 NeetCode 模板 seed 已移除，`neetcode_blind_75_interview_core` 和 `neetcode_150_systematic_interview` 需要按完整阶段规则重新生成。
- 当前 seed 目录固定为 `data/learning-plan-template-seed/`，包含 `learning_plan_templates.jsonl`、`learning_plan_template_problem_refs.jsonl`、`learning_plan_template_seed_manifest.json`、`learning_plan_template_seed_metadata.md` 四个文件。
- 后端导入已经要求每个模板有完整 source attribution、非空 phases、至少一个 problem ref；缺失本地题目保留在 refs 和 metadata，但不进入生成草稿。
- 从模板生成草稿必须满足：阶段周数合计等于总周期；模板阶段承载完整路线；所有本地匹配 refs 默认进入草稿推荐题；缺失 refs 只保留在模板明细和草稿 metadata。

实施边界：

- seed 只保存题单元数据、阶段目标、推荐理由和来源归因；不复制第三方题解、文章正文、图示、代码或题面。
- 模板内部化不是一比一搬运外部计划，而是把高价值路线重建为 algo-mentor 自己的阶段计划。
- 第一批目标是把内部模板池扩展到 10 个；模板市场、自动推荐排序、商业授权审计和非算法计划放到后续批次。

## 待执行列表

状态枚举：`当前已完成`、`进行中`、`待执行`。

| 批次 | 状态 | 任务 | 输出 | 验收条件 |
| --- | --- | --- | --- | --- |
| P0 | 进行中 | 按完整阶段规则重建 NeetCode seed | 2 个模板、完整 problem refs、manifest、metadata | Python seed 测试、后端导入测试、模板生成草稿测试覆盖 Blind 75 / NeetCode 150，且本地匹配题全部进入草稿 |
| P0 | 进行中 | 固化第一批 10 个内部模板的实施口径 | 本文档、templateId 列表、数据来源策略、子 agent 模式 | 每个模板都有稳定 ID、标题、周期、intent、level、来源策略和验收重点 |
| P0 | 待执行 | 把第一批剩余 8 个模板转成 seed | 8 个新增模板行、对应 problem refs、更新 manifest 和 metadata | 每个模板有完整归因、非空 phases、至少一个本地匹配题，缺失题可审计 |
| P0 | 待执行 | 将 `prepare_template_seed.py` 从 NeetCode 单源脚本演进为多 source 生成器 | source adapter、统一 schema 校验、确定性排序、统计报告 | 相同输入重复生成 hash 稳定；四个 seed 文件一次性生成 |
| P0 | 待执行 | 验证后端导入和草稿生成 | 后端测试、导入审计、草稿生成样例 | 10 个模板均可查询、可生成草稿、匹配 refs 默认全部进入草稿 |
| P1 | 待执行 | 扩展专项和模式化模板 | Trees、Backtracking、Heap、Greedy、Bit Manipulation 等专项模板 | 可由能力画像按薄弱标签命中；每个模板题量不低于 15 条 refs |
| P1 | 待执行 | 增强多来源 manifest 和导入审计 | `sources` 统计、每模板来源版本、每来源匹配率 | import run metadata 能定位每个来源的版本、路径、授权和缺失题 |
| P1 | 待执行 | 前端模板入口优化 | 模板列表分组、标签筛选、模板详情缺失题提示 | 用户能按面试冲刺、长期学习、专项突破、复盘选择模板 |
| P2 | 待执行 | 长周期 CS/综合学习模板 | Coding Interview University、CS-Notes、doocs 等参考路线 | 不影响算法刷题主线；明确外链和非题目任务的展示方式 |
| P2 | 待执行 | 非算法专项模板 | SQL、JavaScript、Pandas 等扩展计划 | 明确题库域、练习入口和现有算法题库不匹配时的降级策略 |

## 第一批 10 个内部模板

| templateId | 状态 | 用户看到的标题 | 参考来源 | 周期 | intent | level | 数据来源策略 | 验收重点 |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| `neetcode_150_systematic_interview` | 进行中 | NeetCode 150 系统面试计划 | NeetCode 150、LeetCode Top Interview 150 | 12 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 直接内置；NeetCode MIT 题单元数据固定 commit，LeetCode 官方计划只作目标参考 | 150 refs 按完整阶段规划；本地匹配和缺失统计写入 metadata；生成草稿默认包含全部本地匹配题 |
| `neetcode_blind_75_interview_core` | 进行中 | NeetCode Blind 75 面试核心计划 | Blind 75、LeetCode 75、NeetCode Blind 75 | 4 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 直接内置；NeetCode MIT 题单元数据固定 commit，官方 LeetCode 75 只作外链参考 | 75 refs 按完整阶段规划；缺失 premium/未收录题不进入草稿，保留审计 |
| `tih_best_practice_50_5weeks` | 待执行 | 5 周面试冲刺计划 | Tech Interview Handbook Best Practice 50、Grind 75 | 5 周 | `INTERVIEW_SPRINT` | `INTERMEDIATE` | 直接内置 TIH MIT 题单元数据；Grind 75 仅作外链和结构参考 | 每周主题清晰；题目顺序稳定；optional 题进入 refs metadata 但默认草稿优先 core 题 |
| `cn_algorithm_foundation_12weeks` | 待执行 | 中文系统刷题入门计划 | 代码随想录、LeetCode Programming Skills、NeetCode beginner | 12 周 | `LONG_TERM_LEARNING` | `BEGINNER` | 参考后重建；不复制代码随想录文章、图示或题解，按本地题库重建阶段和题目 | 阶段顺序符合中文学习者习惯；Easy/Medium 比例适合新手；每阶段有可操作复盘建议 |
| `topic_dynamic_programming_foundation` | 待执行 | 动态规划专项突破计划 | LeetCode Dynamic Programming、labuladong DP、halfrost DP | 4 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | halfrost MIT 可直接内置题号/标签；LeetCode 官方和 labuladong 作参考后重建 | 按一维 DP、背包、子序列、状态机/区间拆阶段；题目难度递进；验收关注状态定义和转移复盘 |
| `topic_graph_bfs_dfs` | 待执行 | 图论专项突破计划 | LeetCode Graph Theory、代码随想录图论、halfrost BFS/DFS/Union Find | 4 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | halfrost MIT 题号/标签直接内置；其他来源外链参考和阶段重建 | 覆盖 DFS/BFS、岛屿、拓扑、并查集；缺失题按 frontendId/slug 审计；草稿不推荐未匹配题 |
| `topic_binary_search_boundaries` | 待执行 | 二分与边界专项计划 | LeetCode Binary Search、halfrost Binary Search | 2 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | halfrost 可直接内置；LeetCode 官方计划外链参考 | 2 周草稿生成 2 阶段；覆盖基础二分、旋转数组、答案二分和矩阵/区间变体；边界条件验收明确 |
| `topic_sliding_window_two_pointers` | 待执行 | 滑动窗口与双指针专项计划 | NeetCode pattern、halfrost Two Pointers / Sliding Window、Tech Interview Handbook | 2 周 | `TOPIC_BREAKTHROUGH` | `INTERMEDIATE` | 多源参考后重建；只内置 slug、题号、标签和自写推荐理由 | 固定窗口、可变窗口、左右指针、排序去重组合分层；推荐题不重复过多经典 easy 题 |
| `leetcode_top_100_liked_revision` | 待执行 | Top 100 Liked 复盘计划 | LeetCode Top 100 Liked、Tech Interview Handbook essential/recommended | 6 周 | `MISTAKE_REVIEW` | `INTERMEDIATE` | 外链参考和参考后重建；不直接复制官方题单内容，按本地题库和高赞经典标签重组 | 适合二刷和查漏补缺；metadata 标记复盘导向；后续可按用户错题优先重排 |
| `programming_skills_implementation_foundation` | 待执行 | 编程基础与实现力计划 | LeetCode Programming Skills、本地 Easy/Medium 题库 | 4 周 | `PRACTICE_GOAL` | `BEGINNER` | 外链参考后用本地题库重建；官方计划只作为目标描述和入口 | 覆盖模拟、字符串、数组、矩阵、基础数据结构操作；适合换语言或实现能力薄弱用户 |

## Subagent 执行模式

主 agent 负责派发、集成和验收。子 agent 按来源或批次处理，不能同时直接改四个固定 seed 文件；统一由主 agent 做最终合并，避免 JSONL 排序、统计和 checksum 冲突。

### 主 agent 职责

- 维护第一批模板总表、templateId 命名、intent/level/difficulty 枚举和优先级。
- 为每个子任务提供输入包：目标模板、参考来源、固定 schema、当前本地题库、现有 seed 文件和验收条件。
- 汇总子 agent 输出，更新 `tools/learning_plan_template_seed/prepare_template_seed.py` 或拆分后的 source adapter。
- 一次性生成并提交 `data/learning-plan-template-seed/` 下四个固定文件。
- 运行最小相关验证：Python seed 测试、后端导入测试、模板草稿生成测试。
- 审核来源归因、授权备注、缺失题列表、matched/missing 统计和用户可见标题。

### 子任务拆分

| 子任务 | 建议处理者 | 输入 | 输出 | 写入范围 | 验收条件 |
| --- | --- | --- | --- | --- | --- |
| NeetCode 重建 | 来源子 agent | 固定 commit、目标两个模板、完整阶段规则 | 完整阶段规划、refs、matched/missing 报告 | 只提交候选片段给主 agent；如需改脚本，由主 agent 合并 | 2 个模板统计可审计；problem refs 排序、阶段归属和 matched/missing 稳定 |
| TIH 面试冲刺 | 来源子 agent | TIH 仓库固定 commit、Best Practice 50 Markdown、local problems | `tih_best_practice_50_5weeks` 候选模板、refs、license note | 临时候选目录或 PR patch；不直接覆盖 seed 目录 | core/optional 标记清楚；MIT 归因完整；5 周计划生成后为 3 或 4 个有效阶段 |
| 中文入门路线 | 来源子 agent | 代码随想录目录、Programming Skills 外链、本地题库标签 | `cn_algorithm_foundation_12weeks` 阶段草案和题目候选 | 临时候选目录；不复制第三方正文 | 阶段顺序可解释；所有推荐理由为自写；无 LICENSE 来源只作为参考后重建 |
| 专项模板包 | 批次子 agent | halfrost meta、NeetCode pattern、LeetCode 官方专题外链 | DP、图、二分、滑窗/双指针四个专项候选 | 临时候选目录；仅提交候选 JSONL 片段 | 每个模板至少 15 条 refs；2 周模板生成 2 阶段，4 周模板生成约 3 阶段 |
| 复盘和实现力模板 | 批次子 agent | Top 100 Liked 外链、TIH essential/recommended、本地题库 | Top 100 复盘和编程基础模板候选 | 临时候选目录；不直接写前端 | 复盘模板标记 `MISTAKE_REVIEW`；基础模板 Easy/Medium 结构合理 |
| 集成验收 | 主 agent | 所有候选片段、脚本、测试 | 四个 seed 文件、测试结果、变更说明 | `tools/learning_plan_template_seed/`、`data/learning-plan-template-seed/`、必要测试文件 | 所有模板可导入、可查询、可生成草稿；manifest/metadata 数字一致 |

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
