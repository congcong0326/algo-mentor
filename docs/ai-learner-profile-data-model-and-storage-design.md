# AI 学习者画像数据建模与存储研发设计

更新时间：2026-07-17

状态：研发设计，第一版已收敛为自然语言画像正文

> 上游设计：`knowledge/AI应用/记忆系统/AI学习者画像的维度设计与设计依据.md`
>
> 关联实现：题库 `problem`、代码 Review `practice_code_review`、错题复习 `mistake_note` / `review_log` / `review_recall_evaluation`、能力雷达 `GET /api/abilities/profile`。

## 一、背景与目标

上游设计已经确认 AI 学习者画像采用双轨结构：

```text
AI 学习者画像
├── 用户自述画像
└── 系统观察画像
    ├── 通用观察
    └── 标签能力评价
```

第一版只解决三个问题：

1. 用户自述、通用观察和标签评价如何分类；
2. 当前画像和历史版本如何保存；
3. 题目标签如何成为题库、能力雷达和标签评价共用的稳定身份。

本文不实现 Mem0 的通用原子记忆、向量检索和逐条记忆生命周期。只借鉴“新输入与当前结论比较、没有变化则跳过、有变化则生成新版本”的更新思想。

画像正文统一保存为简短自然语言，不再为不同条目类型设计多套 `detail_json`。需要被程序确定性读取的数据，例如正式学习时间、计划节奏和语言设置，继续放在各自的业务表中，不藏在画像正文里。

本文只讨论题目标签和画像条目本身，不再展开通用证据表、独立 Memory Summary、异步任务、重建、性能和 API 设计。

## 二、现状与约束

### 2.1 当前题目标签模型

当前 `problem` 表使用三个等长数组保存标签：

```text
tag_values
tag_labels_en
tag_labels_zh
```

题库筛选通过 `tag = ANY(tag_values)` 查询，能力雷达通过 `unnest` 展开题目标签，并将当前用户每道题最新一次代码 Review 分数聚合到标签。

这套结构能支撑当前题库规模，但不适合作为标签评价的长期身份：

- 标签只有字符串，没有稳定的数据库实体 ID；
- 标签名称散落在每道题的数组中；
- 雷达和画像都需要执行“标签到题目”的反向查询；
- 数据库无法约束标签评价只能使用题库认可的标签。

### 2.2 当前能力雷达语义

能力雷达只统计当前用户每道题最新一次 `practice_code_review.total_score`，并把一题的分数贡献给该题全部标签。它是数值聚合视图，不是 AI 标签评价的内容来源。

二者保持不同语义：

| 数据 | 归因方式 | 回答的问题 |
|---|---|---|
| 能力雷达分数 | 一道题机械贡献给全部题目标签 | 该标签的 Review 数值表现大致如何 |
| 标签 AI 评价 | 只采用本次解法实际涉及的标签 | 当前掌握情况、优势、问题和下一步是什么 |

画像不能因为某个标签出现在雷达上，就为每个用户预先创建空标签评价。

### 2.3 第一版直接使用现有业务事实

第一版形成画像时，直接从现有业务表读取必要信息：

- `practice_code_review`：正式 Review、扣分原因、改进建议和版本变化；
- `review_log`：复习评级和重做记录；
- `review_recall_evaluation`：复述命中点、遗漏点和 AI 建议；
- `learning_plan_problem_progress`：完成、跳过和重复训练状态；
- `agent_message`：用户明确自述和纠正。

这些业务表仍是权威来源。`learner_profile_entry` 只保存根据它们形成的当前自然语言画像和历史版本，不复制完整代码、完整聊天或完整 Review Markdown。

第一版不新增通用 `learner_profile_evidence` 表，也不承诺把画像正文中的每句话精确关联到某一条来源记录。需要排查时，先根据用户、条目类型、标签和时间回到原业务表查询。

## 三、核心设计结论

### 3.1 三层数据模型

```text
题库标签主数据
    problem_tag / problem_tag_assignment
              │
              ▼
现有业务事实
    practice_code_review / review_log / agent_message / ...
              │
              ▼
可版本化自然语言画像
    learner_profile_entry.content_text
```

`learner_profile_entry` 是领域画像，不是通用记忆平台。用户页面可以按 `entry_kind` 和 `dimension` 直接组织当前 `ACTIVE` 内容，第一版不再单独维护一张全局 Memory Summary 表。

### 3.2 结构化控制字段，自然语言画像正文

需要查询、约束和维护版本的内容使用普通数据库列：

- `user_id`、`entry_kind`、`dimension`、`tag_id`；
- `revision_no`、`status`、`supersedes_entry_id`；
- `origin_type`、模型和 Prompt 版本；
- 生效时间和创建时间。

真正的画像正文只使用一个 `content_text TEXT` 字段。它可以包含简短 Markdown，但不要求后端解析其中的标题、列表或具体字段。

这种边界意味着：

- 画像正文可以直接用于用户查看和 Prompt 注入；
- 不再维护 `detail_json`、`schema_version` 和版本化 JSON mapper；
- 不支持按正文中的某个事实做数据库过滤；
- 需要确定性消费的值必须进入正式业务模型。

### 3.3 当前内容和历史使用同一条版本链

同一个业务身份每次发生有意义的修改时，都插入一条新记录：

```text
revision 1  ACTIVE
      │
      └── revision 2  ACTIVE

版本切换后：
revision 1  SUPERSEDED
revision 2  ACTIVE
```

新记录通过 `supersedes_entry_id` 指向旧记录，旧记录保留原始 `content_text`。用户纠正、模型结论变化或人工重新生成都使用相同的版本切换方式。

第一版不保存候选画像。系统观察的依据不足时执行 `NO_CHANGE`，继续保留原业务记录；只有达到形成门槛时，才创建或更新 `ACTIVE` 画像。

### 3.4 第一版与 Mem0 的关系

第一版只选择性借鉴以下思想：

- 比较新输入和当前画像；
- 没有实质变化时不写新版本；
- 新结论可以取代旧结论；
- 历史版本不被静默覆盖。

第一版明确不采用：

- 通用原子记忆集合；
- 每条记忆的 `ADD / UPDATE / DELETE / NOOP` 路由；
- 向量相似度召回；
- 向量数据库和图数据库；
- 正文子项级独立生命周期和精确来源归因。

当前方案更准确的定义是“受信业务事实 + 分 dimension/tag 的自然语言 Profile 版本”。

## 四、题目标签建模

完整迁移、导入双写、读取切换和验收规格见 `docs/problem-tag-modeling-spec.md`。本章只保留数据模型摘要。

### 4.1 统一标签目录

新增规范化标签表，作为题库、雷达和标签评价共同使用的标签身份：

```sql
CREATE TABLE problem_tag (
  id BIGSERIAL PRIMARY KEY,
  value VARCHAR(120) NOT NULL,
  label_en VARCHAR(160) NOT NULL,
  label_zh VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_tag_value UNIQUE (value)
);
```

字段语义：

- `value` 是稳定契约值，例如 `dynamic-programming`；
- `label_en` / `label_zh` 是按完整 seed 名称频次收敛出的当前规范双语展示名称；
- `active=false` 表示标签不再分配给新题，但历史评价仍可引用；
- 不在 `problem_tag` 中保存需要导入链路持续维护的题目计数。

### 4.2 题目与标签多对多关系

```sql
CREATE TABLE problem_tag_assignment (
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL REFERENCES problem_tag(id) ON DELETE RESTRICT,
  ordinal SMALLINT NOT NULL,
  PRIMARY KEY (problem_id, tag_id),
  CONSTRAINT uk_problem_tag_assignment_ordinal UNIQUE (problem_id, ordinal),
  CONSTRAINT ck_problem_tag_assignment_ordinal CHECK (ordinal >= 0)
);

CREATE INDEX idx_problem_tag_assignment_tag_problem
  ON problem_tag_assignment (tag_id, problem_id);
```

`ordinal` 只用于保持题目来源中的标签顺序，不参与标签身份和能力判断。

两个方向的查询分别使用：

- `PRIMARY KEY (problem_id, tag_id)`：读取一道题的全部受信标签；
- `(tag_id, problem_id)`：按标签筛题和聚合雷达分数。

### 4.3 与现有数组字段的迁移关系

规范化关系在读取切换后成为权威来源，旧数组继续作为兼容副本。按以下顺序迁移：

1. 从当前 `problem.tag_values` 和双语 label 数组回填规范化表；
2. 校验每道题的标签数量、值和顺序；
3. 题库导入器使用同一份规范化标签列表双写关联表和旧数组；
4. 题库筛选和能力雷达切换到规范化关系；
5. 用户测试通过前持续双写并保留旧数组；停写和删列只在用户明确要求后另起变更。

迁移期间至少验证：

```text
每道题数组标签数 = assignment 数
数组中的每个 value 都能匹配 problem_tag.value
同一个 value 的名称变体能够按规格稳定收敛为规范 label
assignment 不包含数组外标签
```

## 五、自然语言画像版本建模

### 5.1 为什么使用统一版本表

用户自述、通用观察和标签评价的正文写法不同，但生命周期相同：

- 都属于一个用户；
- 都需要当前状态和历史版本；
- 都允许用户纠正、整体抑制和删除；
- 都会按当前任务选择后提供给模型。

因此第一版使用统一表 `learner_profile_entry`。`entry_kind` 和 `dimension` 负责分类，`content_text` 保存自然语言正文。

### 5.2 条目类型与维度

`entry_kind` 固定为：

```text
DECLARED_FACT         用户明确表达或纠正的自述
GENERAL_OBSERVATION   跨题目、跨标签形成的通用观察
TAG_ASSESSMENT        某个受信算法标签的综合评价
```

`dimension` 固定为：

```text
LEARNER_BACKGROUND
GOALS_AND_INTENTS
TIME_AND_RESOURCE_CONSTRAINTS
LEARNING_AND_INTERACTION_PREFERENCES
SELF_ABILITY_ASSESSMENT

PROBLEM_SOLVING_APPROACH
IMPLEMENTATION_AND_ERROR_PATTERN
LEARNING_INTERACTION_AND_INDEPENDENCE
REVIEW_AND_GROWTH_PERFORMANCE

TAG_MASTERY
```

`entry_kind` 与 `dimension` 的合法映射为：

```text
DECLARED_FACT
  -> LEARNER_BACKGROUND
  -> GOALS_AND_INTENTS
  -> TIME_AND_RESOURCE_CONSTRAINTS
  -> LEARNING_AND_INTERACTION_PREFERENCES
  -> SELF_ABILITY_ASSESSMENT

GENERAL_OBSERVATION
  -> PROBLEM_SOLVING_APPROACH
  -> IMPLEMENTATION_AND_ERROR_PATTERN
  -> LEARNING_INTERACTION_AND_INDEPENDENCE
  -> REVIEW_AND_GROWTH_PERFORMANCE

TAG_ASSESSMENT
  -> TAG_MASTERY
```

`dimension` 是服务端确定性路由和查询的维护粒度，不是模型自由生成的标签。`TAG_ASSESSMENT` 必须带 `tag_id`；用户自述和通用观察不绑定单个 `tag_id`。

各维度边界如下：

| entry_kind | dimension | 适合记录的内容 | 边界 |
|---|---|---|---|
| `DECLARED_FACT` | `LEARNER_BACKGROUND` | 学习阶段、技术背景、熟悉语言、既往刷题经历 | 不记录无关身份和敏感属性 |
| `DECLARED_FACT` | `GOALS_AND_INTENTS` | 求职场景、目标岗位、专项突破和时间点 | 正式计划目标仍以计划表为准 |
| `DECLARED_FACT` | `TIME_AND_RESOURCE_CONSTRAINTS` | 长期可投入时间和环境限制 | 当前计划节奏仍以计划表为准 |
| `DECLARED_FACT` | `LEARNING_AND_INTERACTION_PREFERENCES` | 讲解、提示和 Review 偏好 | 正式设置存在时以设置表为准 |
| `DECLARED_FACT` | `SELF_ABILITY_ASSESSMENT` | 用户对强项、弱项和信心的主观判断 | 不能自动当成系统能力评价 |
| `GENERAL_OBSERVATION` | `PROBLEM_SOLVING_APPROACH` | 跨题目重复出现的分析和建模方式 | 单次答题不能形成 |
| `GENERAL_OBSERVATION` | `IMPLEMENTATION_AND_ERROR_PATTERN` | 跨题目重复出现的编码和错误模式 | 需要多题重复表现 |
| `GENERAL_OBSERVATION` | `LEARNING_INTERACTION_AND_INDEPENDENCE` | 求助时机、提示使用和独立修正能力 | 不能根据 AI 回复质量反推 |
| `GENERAL_OBSERVATION` | `REVIEW_AND_GROWTH_PERFORMANCE` | 复习保持、多版本改善和迁移表现 | 需要时间跨度 |
| `TAG_ASSESSMENT` | `TAG_MASTERY` | 某个算法标签的当前综合评价 | 必须绑定受信 `tag_id` |

### 5.3 `content_text` 如何写

`content_text` 使用简短自然语言或 Markdown。它面向用户阅读和 Prompt 使用，不作为机器可解析的数据协议。

用户自述示例：

```markdown
- 工作日通常每天可以学习 30 分钟。
- 周末单次可以安排约 2 小时。
```

通用观察示例：

```markdown
用户在多道题中反复遗漏极端边界，但近期已经开始主动列出部分反例。

教学时继续要求其在编码前检查空输入、单元素和上下界。
```

标签评价示例：

```markdown
综合判断：能够识别二分查找场景，但循环不变量仍不稳定。

优势：能够主动选择二分查找，而不是线性扫描。

当前问题：左右边界和返回值选择反复出错。

下一步：编码前明确闭区间或半开区间不变量。

依据概况：来自 3 道题的 4 次正式 Review。
```

这些标题只是推荐写法，不是后端需要解析的固定字段。第一版只校验正文非空、总长度和敏感信息，不校验 Markdown 章节是否齐全。

### 5.4 模型更新协议

模型更新协议和数据库正文是两件事。模型只返回一个最小的 `ProfileUpdateDecision`：

```text
action   NO_CHANGE | REPLACE
content  REPLACE 时必填，表示新的完整自然语言正文
reason   NO_CHANGE 时可选，用于诊断
```

序列化格式属于模型调用的传输细节，不保存进 `content_text`。

更新流程保持简单：

1. 服务端确定 `entry_kind`、`dimension` 和可选 `tag_id`；
2. 读取该业务身份当前的 `ACTIVE content_text`；
3. 将当前正文和本次业务输入交给模型；
4. `NO_CHANGE` 时不写新版本；
5. `REPLACE` 时校验新 `content`，再切换版本。

模型不得返回或修改 `user_id`、`entry_kind`、`dimension`、`tag_id`、`revision_no` 和 `status`。这些字段都由服务端决定。

通用观察没有达到形成门槛时直接 `NO_CHANGE`。第一版不保存 candidate 正文，也不维护 candidate 到 active 的状态转换。

### 5.5 业务身份与版本切换

业务身份只有两种：

```text
TAG_ASSESSMENT                       user_id + tag_id
DECLARED_FACT / GENERAL_OBSERVATION  user_id + entry_kind + dimension
```

同一业务身份最多存在一条 `ACTIVE` 记录。首次创建时写入 `revision_no = 1`；后续 `REPLACE` 时：

1. 将旧 `ACTIVE` 记录改为 `SUPERSEDED` 并设置 `valid_to`；
2. 插入 `revision_no + 1` 的新 `ACTIVE` 记录；
3. 新记录的 `supersedes_entry_id` 指向旧记录。

例如：

| id | revision_no | status | supersedes_entry_id | content_text |
|---:|---:|---|---:|---|
| 123 | 1 | `SUPERSEDED` | NULL | 经常遗漏边界，编码前需要强制检查。 |
| 186 | 2 | `ACTIVE` | 123 | 仍会遗漏边界，但近期已开始主动列反例。 |

整个 dimension 或标签评价不应继续使用时，将当前行改为 `SUPPRESSED`。普通纠正生成新版本，不等于删除历史；用户要求真正删除时，物理删除该业务身份的当前和历史记录。

### 5.6 表结构草案

```sql
CREATE TABLE learner_profile_entry (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  entry_kind VARCHAR(32) NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  tag_id BIGINT NULL REFERENCES problem_tag(id),
  revision_no INTEGER NOT NULL,
  status VARCHAR(24) NOT NULL,
  content_text TEXT NOT NULL,
  supersedes_entry_id BIGINT NULL REFERENCES learner_profile_entry(id),
  origin_type VARCHAR(32) NOT NULL,
  model_provider VARCHAR(64) NULL,
  model_name VARCHAR(128) NULL,
  prompt_version VARCHAR(64) NULL,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  valid_to TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_learner_profile_entry_kind CHECK (
    entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION', 'TAG_ASSESSMENT')
  ),
  CONSTRAINT ck_learner_profile_entry_dimension CHECK (
    dimension IN (
      'LEARNER_BACKGROUND',
      'GOALS_AND_INTENTS',
      'TIME_AND_RESOURCE_CONSTRAINTS',
      'LEARNING_AND_INTERACTION_PREFERENCES',
      'SELF_ABILITY_ASSESSMENT',
      'PROBLEM_SOLVING_APPROACH',
      'IMPLEMENTATION_AND_ERROR_PATTERN',
      'LEARNING_INTERACTION_AND_INDEPENDENCE',
      'REVIEW_AND_GROWTH_PERFORMANCE',
      'TAG_MASTERY'
    )
  ),
  CONSTRAINT ck_learner_profile_entry_status CHECK (
    status IN ('ACTIVE', 'SUPERSEDED', 'SUPPRESSED')
  ),
  CONSTRAINT ck_learner_profile_entry_revision CHECK (revision_no > 0),
  CONSTRAINT ck_learner_profile_entry_content CHECK (
    length(btrim(content_text)) > 0
  ),
  CONSTRAINT ck_learner_profile_entry_origin CHECK (
    origin_type IN ('USER_EXPLICIT', 'USER_CORRECTION', 'SYSTEM_DERIVED')
  ),
  CONSTRAINT ck_learner_profile_entry_validity CHECK (
    (
      status = 'ACTIVE'
      AND valid_to IS NULL
    )
    OR (
      status IN ('SUPERSEDED', 'SUPPRESSED')
      AND valid_to IS NOT NULL
      AND valid_to >= valid_from
    )
  ),
  CONSTRAINT ck_learner_profile_entry_scope CHECK (
    (
      entry_kind = 'TAG_ASSESSMENT'
      AND dimension = 'TAG_MASTERY'
      AND tag_id IS NOT NULL
    )
    OR (
      entry_kind = 'GENERAL_OBSERVATION'
      AND dimension IN (
        'PROBLEM_SOLVING_APPROACH',
        'IMPLEMENTATION_AND_ERROR_PATTERN',
        'LEARNING_INTERACTION_AND_INDEPENDENCE',
        'REVIEW_AND_GROWTH_PERFORMANCE'
      )
      AND tag_id IS NULL
    )
    OR (
      entry_kind = 'DECLARED_FACT'
      AND dimension IN (
        'LEARNER_BACKGROUND',
        'GOALS_AND_INTENTS',
        'TIME_AND_RESOURCE_CONSTRAINTS',
        'LEARNING_AND_INTERACTION_PREFERENCES',
        'SELF_ABILITY_ASSESSMENT'
      )
      AND tag_id IS NULL
    )
  )
);
```

第一版只增加保证正确性所需的唯一索引：

```sql
CREATE UNIQUE INDEX uk_learner_profile_entry_active_tag
  ON learner_profile_entry (user_id, tag_id)
  WHERE status = 'ACTIVE'
    AND entry_kind = 'TAG_ASSESSMENT';

CREATE UNIQUE INDEX uk_learner_profile_entry_active_dimension
  ON learner_profile_entry (user_id, entry_kind, dimension)
  WHERE status = 'ACTIVE'
    AND entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION');

CREATE UNIQUE INDEX uk_learner_profile_entry_supersedes
  ON learner_profile_entry (supersedes_entry_id)
  WHERE supersedes_entry_id IS NOT NULL;

CREATE UNIQUE INDEX uk_learner_profile_entry_revision_tag
  ON learner_profile_entry (user_id, tag_id, revision_no)
  WHERE entry_kind = 'TAG_ASSESSMENT';

CREATE UNIQUE INDEX uk_learner_profile_entry_revision_dimension
  ON learner_profile_entry (user_id, entry_kind, dimension, revision_no)
  WHERE entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION');
```

版本切换必须在同一事务中完成。事务内重新读取当前 `ACTIVE` 记录；如果它已经变化，放弃旧模型结果并基于最新正文重新处理，不能直接覆盖。

### 5.7 第一版边界

第一版明确不做：

- 不保存 `detail_json`、`schema_version` 或多态 JSON DTO；
- 不保存 candidate 画像；
- 不新增通用证据表和画像-证据关联表；
- 不单独维护 Memory Summary 表；
- 不引入向量、图数据库和语义检索；
- 不对自然语言正文做字段级解析和查询；
- 不在本文设计异步 worker、重建、运营诊断和前端管理流程。

第一版完成标准是：能够按用户和 dimension/tag 读取当前自然语言画像，能够通过 `NO_CHANGE / REPLACE` 生成历史版本，并保证同一业务身份最多一条 `ACTIVE` 记录。
