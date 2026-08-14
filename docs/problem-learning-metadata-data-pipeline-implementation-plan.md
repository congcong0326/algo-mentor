# LeetCode 题目学习元数据数据管线实施计划

## 文档信息

- 状态：已完成（2026-08-14）
- 创建日期：2026-08-14
- 适用范围：LeetCode `similarQuestions`、`hints`、`categoryTitle` 与全量 `codeSnippets` 的抓取、seed 生成与审核、数据库 DDL、幂等数据导入
- 关联现状：`tools/problem_seed/leetcode_api.py`、`tools/problem_seed/prepare_seed.py`、`ProblemSeedImporter`、`ProblemSeedRunner`、`data/seed/problems.jsonl`

## 一、目标与阶段边界

本计划的目标是把 LeetCode 题目学习元数据建设成一条可重跑、可审核、可追溯、可幂等导入的数据管线。最终数据库中应拥有：

1. 题目间的官方相似题关联。
2. 题目的官方分级提示。
3. 题目的高层分类。
4. 题目的全量语言 starter code 模板。

本阶段只完成数据层闭环，不启动任何业务消费改造。明确不包含：

- 不修改 Practice Chat Prompt，不注入跨题会话总结。
- 不新增 Agent Tool、Controller、前端页面或前端 API 调用。
- 不把 `LEETCODE_SIMILAR` 解释为前置依赖、掌握关系或推荐排序。
- 不抓取或导入官方题解、讨论区、用户提交、用户状态、点赞/点踩、实时统计或付费公司标签。

现有题目筛选接口已经读取 `problem_category`。导入分类数据后，该既有接口可能开始返回非空分类；这是数据填充带来的既有能力生效，不额外调整 API 或前端交互。

## 二、已确认的输入事实

1. 当前 `QUESTION_DATA_QUERY` 只请求题面、标签、难度、题目模板等字段，未请求 `similarQuestions`、`hints`、`categoryTitle`。
2. 当前原始缓存 `data/sources/leetcode-api/` 与 `data/seed/problems.jsonl` 已保存题目主数据；前者被 `.gitignore` 排除，后者是可提交 seed。
3. 现有 GraphQL 响应中的 `similarQuestions` 是一层 JSON 字符串，需要二次解析；记录包含目标题 slug、标题和难度，中文站通常还包含译名与付费标记。
4. `codeSnippets` 已在旧原始缓存中出现，但当前 seed 生成器只持久化 Python 3 模板。为保证本批数据同一快照可审计，本计划仍在新的元数据抓取响应中统一请求它。
5. `.com` 与 `.cn` 均可返回 `categoryTitle`、`hints`、`similarQuestions` 和 `codeSnippets`。中文站返回的 hint 可能仍为英文，因此请求站点不能被当作内容自然语言的断言。
6. 数据库已有 `problem_category` 和 `problem_category_item`，但现有题目 seed 中分类文件为空，主题库导入器未导入分类。

## 三、固定数据契约

### 3.1 原始缓存

新增独立目录，不覆盖已有题面缓存：

```text
data/sources/leetcode-api-metadata/
  {slug}.com.json
  {slug}.cn.json
```

该目录继续由 `.gitignore` 排除。每个文件保存一次元数据专用 GraphQL 请求的完整原始响应；请求版本、站点、拉取时间、输入索引哈希写入 manifest，而不依赖文件名推断版本。

元数据 GraphQL 查询固定为：

```graphql
query questionLearningMetadata($titleSlug: String!) {
  question(titleSlug: $titleSlug) {
    titleSlug
    categoryTitle
    hints
    similarQuestions
    codeSnippets { lang langSlug code }
  }
}
```

抓取只针对当前 `data/seed/problems.jsonl` 中的题目。脚本根据题目索引的站点可用性选择 `.com`、`.cn`；每个站点独立限速、重试并支持中断后续跑。未成功的请求不得被静默当作“没有元数据”。

### 3.2 审核后 seed

新增可提交目录：

```text
data/problem-metadata-seed/
  problem_relations.jsonl
  problem_hints.jsonl
  problem_code_templates.jsonl
  problem_categories.jsonl
  problem_category_items.jsonl
  problem_metadata_seed_manifest.json
  problem_metadata_audit_report.json
```

`manifest` 记录查询版本、原始缓存统计、输入 `problems.jsonl` SHA-256、每个输出文件 SHA-256、生成时间和 source snapshot。`audit_report` 记录校验结果与统计；只有 `outcome=PASSED` 且各文件哈希与 manifest 一致的 seed 可被导入器接受。

#### 题目关联：`problem_relations.jsonl`

```json
{"sourceSlug":"two-sum","targetSlug":"3sum","relationType":"LEETCODE_SIMILAR","source":"LEETCODE","sourceSnapshot":"leetcode-question-learning-metadata.v1@...","metadata":{"title":"3Sum","translatedTitle":"三数之和","difficulty":"MEDIUM","paidOnly":false,"sourceSites":["LEETCODE_COM","LEETCODE_CN"]}}
```

- 唯一键：`sourceSlug + targetSlug + relationType + source`。
- `sourceSlug`、`targetSlug` 是 LeetCode `titleSlug`，并且必须是规范化后的非空 slug。
- `LEETCODE_SIMILAR` 仅表示“来源题页面列出了目标题为相似题”。记录保持有方向；业务侧未来可按正反任一方向判断相关性，但导入器不得补写反向边。
- `metadata` 仅保存来源快照中的辅助审计信息，不作为本地题目事实的权威来源。

#### 官方提示：`problem_hints.jsonl`

```json
{"problemSlug":"two-sum","sourceSite":"LEETCODE_COM","ordinal":1,"contentMarkdown":"先考虑暴力解法的复杂度。","sourceSnapshot":"leetcode-question-learning-metadata.v1@..."}
```

- `ordinal` 从 1 连续编号，保持来源给出的顺序。
- 来源 hint 中的 HTML 必须转换并清洗为 Markdown；不得直接将来源 HTML 作为可渲染内容入库。
- `sourceSite` 是数据来源，不是内容语言保证。未来消费层自行决定站点偏好或翻译策略。

#### Starter code：`problem_code_templates.jsonl`

```json
{"problemSlug":"two-sum","languageSlug":"java","languageLabel":"Java","code":"class Solution { ... }","sourceSite":"LEETCODE_COM","sourceSnapshot":"leetcode-question-learning-metadata.v1@..."}
```

- 唯一键：`problemSlug + languageSlug`。
- 同一题同一 `languageSlug` 优先使用 `.com`，`.com` 缺失时使用 `.cn`；保留最终来源站点。
- Python 和 Python3 是不同的 `languageSlug`，不得在 seed 构建阶段合并。

#### 分类：`problem_categories.jsonl` 与 `problem_category_items.jsonl`

```json
{"slug":"algorithms","nameEn":"Algorithms","nameZh":"算法","source":"LEETCODE","sourceSnapshot":"leetcode-question-learning-metadata.v1@..."}
{"problemSlug":"two-sum","categorySlug":"algorithms","source":"LEETCODE","sourceSnapshot":"leetcode-question-learning-metadata.v1@..."}
```

- `categoryTitle` 先通过受控映射转成稳定 slug 与双语名称。
- 每一个实际出现的来源分类都必须命中映射；未知分类是审核失败，不允许以英文原文静默兜底为中文名称。
- 第一批预期支持 `Algorithms`、`Database`、`Shell`、`Concurrency`；真实抓取结果是最终集合，不依赖主观假设。

## 四、目标数据库模型

本计划不向 `problem` 主表堆叠 JSONB。需要排序、来源和独立刷新能力的数据使用子表。

### 4.1 迁移

新增下一全局 Flyway 版本（实施前复核基线为 V63，实际使用 `V64__problem_learning_metadata.sql`）。迁移包含以下内容：

1. `problem_relation`
   - `source_problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE`
   - `target_problem_slug VARCHAR(220) NOT NULL`
   - `target_problem_id BIGINT NULL REFERENCES problem(id) ON DELETE SET NULL`
   - `relation_type VARCHAR(64) NOT NULL`
   - `source VARCHAR(64) NOT NULL`
   - `source_snapshot VARCHAR(200) NOT NULL`
   - `metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb`
   - 时间戳与唯一约束 `(source_problem_id, target_problem_slug, relation_type, source)`。
   - 约束来源题不能等于已解析的目标题；为 `target_problem_id`、`relation_type + source` 建索引。

2. `problem_hint`
   - `problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE`
   - `source_site VARCHAR(32) NOT NULL`
   - `ordinal SMALLINT NOT NULL CHECK (ordinal > 0)`
   - `content_markdown TEXT NOT NULL`
   - `source_snapshot VARCHAR(200) NOT NULL`
   - 时间戳与唯一约束 `(problem_id, source_site, ordinal)`。

3. `problem_code_template`
   - `problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE`
   - `language_slug VARCHAR(80) NOT NULL`
   - `language_label VARCHAR(120) NOT NULL`
   - `code TEXT NOT NULL`
   - `source_site VARCHAR(32) NOT NULL`
   - `source_snapshot VARCHAR(200) NOT NULL`
   - 时间戳与唯一约束 `(problem_id, language_slug)`。

4. 扩展既有分类表
   - `problem_category` 增加 `name_en`、`name_zh`、`source`、`source_snapshot`；迁移既有 `name` 到双语字段后保留 `name` 以保证现有 mapper 和 API 兼容。
   - `problem_category_item` 增加 `source`、`source_snapshot`；主键从 `(category_id, problem_id)` 扩展为 `(category_id, problem_id, source)`，既有记录标记为 `LEGACY`。
   - 查询保持“题目属于任一来源分类即命中”的原语义。

5. `problem_metadata_import_run`
   - 保存 manifest 路径、manifest SHA-256、source snapshot、读取/匹配/跳过/失败计数、审计报告摘要和导入时间。

迁移必须保留已有题目、标签、分类和筛选行为；不得删除或重建 `problem` 主表。

### 4.2 应用层与导入边界

新增数据层组件：

```text
api/problem/model/                 # 四类 metadata seed record 与导入结果
api/problem/repository/            # ProblemLearningMetadataRepository
api/problem/service/               # Reader、Importer、校验与运行配置
api/problem/mapper/                # MyBatis Mapper 与 row model
resources/mapper/problem/          # 元数据 SQL
```

元数据读取端口未来可放入 `mentor-application`，但本阶段不创建任何业务读取端口或消费逻辑。当前只实现导入所需 repository，避免没有消费者的抽象提前外溢。

`ProblemSeedRunner` 增加独立配置：

```text
algo-mentor.problem.metadata-seed.enabled=false
algo-mentor.problem.metadata-seed.path=data/problem-metadata-seed
```

它必须支持在 `problem.seed.enabled=false` 时单独运行元数据导入；前提是数据库已有 `problem` 主数据。正常顺序为：主题库 seed → 公司信号（可选）→ 元数据 seed → 学习计划模板（可选）。

### 4.3 幂等与刷新语义

每次元数据 seed 导入都以 manifest 的 `sourceSnapshot` 为单位，在单一数据库事务中完成：

- `LEETCODE` 相似题边按来源进行精确替换；不删除未来人工 `CURATED` 等其他来源的边。
- hint 按 `problem_id + source_site` 精确替换。
- code template 按 `problem_id + language_slug` upsert，采用 seed 已固定的站点优先级。
- `LEETCODE` 分类关联精确替换；`LEGACY` 或未来其他来源分类保留。
- 本地不存在的关联目标仍保存 `target_problem_slug`，但 `target_problem_id` 为 `NULL`；将来目标题被导入后，下一次元数据导入自动解析。

导入器在写入前必须验证 `manifest` 和 `audit_report` 的成功状态与 SHA-256。校验不通过时不写入任何业务表，也不创建成功 import run。

## 五、执行任务与 Goal 级验收

每个任务完成后必须在本计划“执行记录”补充：修改文件、验证命令、实际统计、偏离项和下一任务输入。未达到本任务验收条件时不得推进到下一任务。

### PMD-00：冻结契约与基线

工作内容：

- 记录 `git status --short`，区分本任务前已有改动。
- 确认全仓库当前 Flyway 最大版本号，冻结 seed 目录、JSONL 字段、表名、来源值和站点优先级。
- 为 GraphQL 查询、JSONL 字段、source 值、relation type、文件名建立集中常量，禁止散落字符串。

验收：

- 文档中的四类 seed 契约无待定字段。
- 现有主题库 seed 数量、SHA-256 与 source cache 可用性已记录。
- 未产生任何抓取、seed 或数据库写入。

### PMD-01：可恢复的元数据抓取脚本

工作内容：

- 在 `tools/problem_seed/` 或职责清晰的相邻子目录新增元数据抓取器与 GraphQL 常量。
- 以 `problems.jsonl` 为目标集合，以题目索引确定站点可用性；支持 `--slug`、`--limit`、`--force`、`--interval`、`--retries`。
- 使用独立缓存目录，成功响应原样保存；失败保存到结构化运行报告而非伪造空响应。
- 新增 `make problem-metadata-fetch`，默认不执行全量网络抓取，必须显式调用。

验收：

- 两站点 fixture 测试覆盖成功、HTTP 失败、重试、跳过已有缓存、指定 slug 与恢复执行。
- 对 `two-sum` 的真实只读 smoke 查询能取得四种字段，且不写入 `data/seed` 与数据库。
- 缓存文件名、站点、请求版本和统计可追溯；日志不输出完整题面或大段 code。

### PMD-02：确定性 seed 生成器

工作内容：

- 读取原始元数据缓存与主题库 seed，生成四类 JSONL、manifest 和初始统计。
- 严格二次解析 `similarQuestions`；将来源 hint 清洗为 Markdown；对 code template 去重并按站点优先级选择。
- 将 `categoryTitle` 映射为受控双语分类；未知值显式加入错误集合。
- 输出必须稳定排序：题目 slug、目标 slug、source site、ordinal、language slug 均使用确定性顺序。
- 新增 `make problem-metadata-seed`。

验收：

- 相同输入重复运行，五个 JSONL/manifest 的 SHA-256 完全一致（生成时间字段除外；其位置不得参与内容校验）。
- fixture 覆盖：相似题双重 JSON、双站点合并、CN 优先译名、空 hints、重复 code language、未知分类、目标不在本地题库。
- code template 中保留的 Python3 模板与旧主题库 seed 中同题的 `python3Template` 完全一致；不一致必须失败并列出 slug。

### PMD-03：静态审核器与人工审核包

工作内容：

- 新增校验器和 `make problem-metadata-validate`，只读 seed，生成 `problem_metadata_audit_report.json`。
- 校验 manifest / 输出哈希、JSON Schema、唯一键、自环、引用、Markdown、分类映射、模板合法性和统计守恒。
- 输出便于人工抽样的报告：每类数据计数、覆盖率、未匹配目标、抓取失败、分类分布、hint 数量分布、语言分布、高出度题目。

验收：

- `failedFetchCount=0`；若存在站点明确不支持或题目下架，必须作为 `notAvailable` 逐条列出，不能与失败混淆。
- 每个源题状态都有且仅有一个终态：`fetched`、`notAvailable` 或 `failed`；可导入 seed 要求 `failed=0`。
- 关联边：解析错误、空 slug、重复唯一键、自环均为 0；`matchedTargetCount + unmatchedTargetCount = totalRelationCount`。
- hints：每个 `(problemSlug, sourceSite)` 的 ordinal 从 1 连续；内容为空或未清洗 HTML 为 0。
- templates：每个 `(problemSlug, languageSlug)` 唯一且 code 非空；与旧 Python3 模板的差异为 0。
- categories：未知映射为 0；分类关联中的每个 category slug 都有目录记录。
- 人工至少抽样 20 个源题，覆盖有/无 hints、双站点、关联目标缺失、多语言模板和每一种分类；抽样结论写入报告。

### PMD-04：数据库迁移与 MyBatis 基础设施

工作内容：

- 编写并验证经版本复核后的下一版本迁移（实际为 `V64__problem_learning_metadata.sql`）。
- 新增四类表/分类扩展/import run 的 mapper、row model、repository 接口和 MyBatis 实现。
- 新增 migration resource、mapper XML 和 repository 单元测试。

验收：

- Flyway 在空数据库和已执行旧迁移的数据库均可成功升级。
- 旧 `problem_category` / `problem_category_item` 记录迁移后保留，且标记 `LEGACY`；不丢失任何题目或标签行。
- 关系表允许未匹配 target slug，禁止自引用和重复来源边；所有 FK、唯一约束、索引和 JSONB 默认值通过 SQL 断言。
- 不修改任何 Controller、Agent、Prompt、前端文件；`git diff` 仅包含数据管线、迁移、导入配置、测试和文档。

### PMD-05：受审 seed 的幂等导入器

工作内容：

- 新增 `ProblemLearningMetadataSeedReader`、`ProblemLearningMetadataSeedImportService` 和导入结果模型。
- 导入前复核 manifest、audit report、输出文件校验和与结果状态。
- 按本计划 4.3 的来源隔离和精确替换语义实现批量 upsert / 删除。
- 扩展 `ProblemSeedRunner` 和 Makefile，新增 `make db-seed-metadata`；不改变既有 `make db-seed` 的默认元数据导入行为。

验收：

- 同一个合法 seed 导入两次后，四类业务数据行数与内容哈希不变；`problem_metadata_import_run` 有可追溯的两次运行记录。
- 篡改任一 seed 文件、manifest 哈希或 audit outcome 后，导入失败且四类业务表无变化。
- fixture 更新删除某一 LeetCode 边/hint/template/category assignment 后，第二次导入准确移除该 LeetCode 数据；同 fixture 中的 `CURATED` 或 `LEGACY` 行不受影响。
- 未匹配目标 slug 被保留且 FK 为 `NULL`；匹配目标可正确写入 FK。
- 元数据导入可在 `problem.seed.enabled=false` 且题库已存在的数据库单独成功运行。

### PMD-06：真实数据抓取、审核与入库

工作内容：

- 按限速策略完成可恢复的真实元数据抓取。
- 生成并提交审核后的 `data/problem-metadata-seed/`；原始缓存保持忽略。
- 运行自动审核并完成 20 个以上人工抽样。
- 对本地 PostgreSQL 执行 Flyway 与 `make db-seed-metadata`。

验收：

- 审核报告 `outcome=PASSED`，且满足 PMD-03 全部计数门禁。
- 真实 seed 的 manifest、audit report、四类 JSONL 均已提交并相互校验。
- 数据库中四类表的行数、源快照、未匹配 target 数与 audit report 一致。
- 再次执行 `make db-seed-metadata` 结果幂等。
- 不启动 Vite；不修改 Practice Chat、学习计划生成、复习、Agent 或前端业务逻辑。

### PMD-07：交付验证与阶段封板

工作内容：

- 运行与改动相关的 Java、Python、Flyway/MyBatis 集成测试。
- 审阅 git diff，确认无未授权的业务改造、真实密钥、原始缓存或大段无关数据进入提交。
- 更新本计划执行记录和 `docs/code-index.md`。

验收：

- `make backend-test` 通过；新增 Python 脚本测试通过。
- 迁移/导入集成测试覆盖空库、旧库、合法 seed、非法 seed、重复导入和来源隔离。
- 阶段交付物仅包含数据抓取脚本、审核 seed、DDL、导入器、配置/Makefile、测试和中文文档。
- 后续业务改造拥有独立设计与任务，不在本 Goal 中夹带实现。

## 六、运行与发布顺序

```text
PMD-00
  -> PMD-01 抓取能力
  -> PMD-02 生成能力
  -> PMD-03 自动 + 人工审核
  -> PMD-04 DDL
  -> PMD-05 导入器
  -> PMD-06 真实抓取、审核、导入
  -> PMD-07 封板
```

真实抓取和真实数据库导入必须在 PMD-03、PMD-04、PMD-05 的代码与测试完成后执行。数据导入的停止条件如下：

- 任何抓取失败、未映射分类、seed 哈希不一致、解析错误或人工抽样发现错误时，停止在审核阶段。
- 数据库导入失败时，修复导入器或 seed 后重新执行；不得手工修改表来伪造成功状态。
- 本阶段不因未来跨题注入功能而提前接入任何 Prompt 或 Agent 代码。

## 七、最终阶段验收摘要

本阶段完成的必要且充分条件：

1. 现有题库所有可用来源题均有可追溯的元数据抓取终态，真实可导入批次没有抓取失败。
2. 关系、提示、分类、全量代码模板四类 seed 均通过确定性生成和自动审核，且完成规定人工抽样。
3. DDL 经 Flyway 在空库和旧库验证，旧数据不丢失。
4. 受审 seed 能独立、可重复导入，能精确刷新 LeetCode 来源数据而不影响其他来源。
5. 真实数据库记录与审核报告完全一致。
6. 不包含任何业务消费改造；跨题会话总结、官方提示展示、starter code 使用和分类 UI 均留给后续独立目标。

## 八、执行记录

| 任务 | 状态 | 修改/产物 | 验证与实际统计 | 偏离与后续输入 |
| --- | --- | --- | --- | --- |
| PMD-00 | 已完成 | 冻结 V63 基线、V64 版本、五类 JSONL 契约、来源/站点/关系常量；保留既有工作区改动。 | `git status --short`、全仓库迁移扫描；题目 seed 3,591 行，输入哈希写入 manifest。 | 实际基线高于计划示例中的 V62，改用 V64；未改动业务消费边界。 |
| PMD-01 | 已完成 | 新增独立元数据 GraphQL 抓取器、可恢复缓存、终态报告和 Make 入口。 | metadata pipeline tests 8 个通过，覆盖成功/不可用/失败/重试参数传递/缓存复用/指定 slug/limit；真实 3,591 个来源题、6,794 个站点响应，failed=0、notAvailable=0。 | 原始缓存保持 `.gitignore`；未启动前端服务。 |
| PMD-02 | 已完成 | 新增确定性关系、hint、模板、分类 seed 构建器和集中契约。 | metadata pipeline tests 8 个通过；真实输出 relations=4,424、hints=11,841、templates=68,153、categories=7、category items=3,591、buildErrors=0。 | 旧 seed 与当前 API 的 `painting-the-walls` 模板已按当前来源更新；模板外层空白统一清洗；上游 8 条自引用记录为 buildWarnings 并排除。 |
| PMD-03 | 已完成 | 新增静态审核器、哈希/契约/统计门禁和稳定人工抽样候选。 | audit `outcome=PASSED`；fetched=3,591、notAvailable=0、failed=0；matched target=3,920、unmatched target=504；已人工核对 20 个样本，覆盖 hints、双站点、未匹配目标、多语言和 7 种分类。 | 人工结论写入 `problem_metadata_audit_report.json`；自引用仅作为来源警告记录。 |
| PMD-04 | 已完成 | 新增 `V64` DDL、MyBatis mapper/row model/repository 及迁移集成测试。 | 空库/旧库 Flyway 集成测试通过；旧分类保留并标记 `LEGACY`，约束、FK、索引和未匹配 target 行为均有断言。 | 无 schema 重建或业务表删除。 |
| PMD-05 | 已完成 | 新增 seed reader/validator/import service，扩展 `ProblemSeedRunner` 和 `db-seed-metadata`。 | 幂等、精确刷新、来源隔离、篡改前置失败和 `problem.seed.enabled=false` 独立导入测试通过；Java 定向 Failsafe 2/2 通过。 | manifest 中抓取报告路径固定为相对 seed 目录，兼容 Maven 模块工作目录。 |
| PMD-06 | 已完成 | 真实审核 seed 已生成并导入本地 PostgreSQL。 | 本地 v64：relations=4,424（target NULL=504）、hints=11,841、templates=68,153、category items=3,591；两次导入均 read=88,016/matched=3,591/skipped=0/failed=0，内容哈希不变。 | 首次默认端口 18080 被占用，改用 `API_PORT=18081`；迁移和导入均成功。 |
| PMD-07 | 已完成 | 更新本计划与 `docs/code-index.md`，完成交付审阅。 | metadata pipeline 8 个测试、tools 全量 78 个 Python 测试、Java 编译和 2 个元数据集成测试通过；`make backend-test` 全部成功，mentor-api 374 个 Surefire 测试通过。 | 未修改 Controller、Prompt、Agent、前端或 Practice Chat 消费逻辑。 |
