# 从「错题本」到「复习队列」：覆盖做对的题 —— 研发设计

> 上游：`docs/product-planning/p0-mistake-notebook-spaced-repetition-design.md`、`docs/mistake-notebook-review-technical-design.md`（现有实现基线）。
> 本文目标：修正现有「只有错题进复习」的结构性缺陷，把间隔重复的覆盖面从「做错的题」扩展到「练过的题」，并说明落地改动、迁移、兼容与分期。
> 语言/风格遵循 `AGENTS.md`：JDK 17、Spring MVC、PostgreSQL + Flyway + MyBatis、`org.congcong.algomentor.*`、跨模块字面量进枚举/常量类。

---

## 1. 问题：当前设计把「做对」排除在遗忘曲线之外

现有实现只有三个入库来源（`REVIEW_FAILED / USER_MARKED / AI_WEAK`），且**做对的题永远进不来**。从数据层到服务层，这个假设被写死在三处：

1. **服务层早退** —— `MistakeNoteService.ingestFromReview()`（`MistakeNoteService.java:41`）：
   ```java
   if (review.passed() && review.score().total().compareTo(PASS_SCORE) >= 0) {
     return;   // 达标直接返回，做对的题不入库
   }
   ```
2. **数据层约束** —— `V19__mistake_review_schema.sql:28`：
   ```sql
   CONSTRAINT ck_mistake_note_source CHECK (source IN ('REVIEW_FAILED','USER_MARKED','AI_WEAK'))
   ```
3. **入库 SQL** —— `MistakeNoteMapper.xml` 的 `upsertForReviewFailure` 只服务「失败」语义，且靠表默认值（`interval_days=0 / due_at=NOW / mastery_state=NEW`）让错题**立即进当日队列**。

### 1.1 为什么这是缺陷

- **遗忘曲线与对错无关**：记忆强度只取决于「距上次成功回忆的时间」。只复习错题，等于只保护了一部分记忆，做对的题过两周照样忘 —— 命中的正是产品要解决的痛点 P6「学了就忘」。
- **做对 ≠ 真会**：达标可能来自借鉴/参考答案/看题解后 AC/蒙对。`passed=true` 只证明「这次交上去过了」，不证明掌握。这类「假通过」现在完全没有复查机制。

### 1.2 关键洞察：底座已经就绪，缺的是入口

`mistake_note` 表本质上**已经是一张 SM-2 间隔重复记录表**（`repetitions / ease_factor / interval_days / due_at / mastery_state / lapses` 齐全），`ReviewSchedulerService` 已是完备的 grade 驱动调度器。真正缺的只有两点：

1. **入库闸门太窄** —— 只让失败进。
2. **缺少「初始定档（seed）」** —— 失败题靠表默认值立即到期即可；但做对的题若也用 `due=NOW`，会瞬间灌爆当日队列，必须按「本次表现」给一个**更长的首个间隔**。

因此这不是新功能，而是**放开入口 + 补一个 seed 策略 + 概念更名**。

---

## 2. 目标与非目标

**目标**
- 练过的题（无论对错）都纳入间隔重复，按遗忘曲线安排复习。
- 做对的题按「掌握置信度」给差异化的首个间隔，不冲垮当日复习负载。
- 「假通过」（借鉴/抄答案）通过首次复习的 recall 判定被自然识别。
- 概念从「错题本」升级为「复习队列 / 复习中心」，「错题」降级为其中一个筛选视图，前端与 API 平滑兼容。

**非目标（本期不做）**
- 不改调度算法本身（SM-2 简化版不动）。
- 不新增卡片档位、不改 judge 契约。
- 不做「AI 判定薄弱自动入库」（仍二期）。
- 不启用会员分层。
- 不做每日批处理 worker。

---

## 3. 概念重构：复习队列 = 练过的题的统一调度

| 旧概念 | 新概念 | 说明 |
|---|---|---|
| 错题本（mistake notebook） | **复习队列 / 复习中心** | 底层是同一张 `mistake_note` 表、同一套调度 |
| 「进错题本」 | 「进复习队列」 | 入口从「做错」放宽到「练过」 |
| 错题本页 | 复习中心页 + **「错题」筛选视图** | 「错题」= `source=REVIEW_FAILED` 或 `lapses>0` 的过滤，不再是全部 |

**不改表名/类名**：`mistake_note` / `MistakeNote` / `mentor.application.review` 保持不变（改名的迁移成本 > 收益）。仅在**产品文案与前端 UI 措辞**上从「错题本」切到「复习中心」，用 `source` 字段区分「错题子集」。

---

## 4. 入库规则变更

### 4.1 新的来源枚举

`MistakeSource` 增加一档，语义 = 「达标通过、但仍需按遗忘曲线复习」：

```java
public enum MistakeSource {
  REVIEW_FAILED,   // Review 未达标（原「错题」）
  REVIEW_PASSED,   // 新增：Review 达标通过，纳入遗忘曲线复习
  USER_MARKED,
  AI_WEAK
}
```

### 4.2 新的入库判定

`MistakeNoteService.ingestFromReview()` 去掉「达标早退」，改为**按结果分流入库**：

```java
public void ingestFromReview(PracticeCodeReview review) {
  boolean passed = review.passed()
      && review.score().total().compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0;
  MistakeSource source = passed ? MistakeSource.REVIEW_PASSED : MistakeSource.REVIEW_FAILED;
  ReviewSeed seed = seedPolicy.forReview(review, passed);   // §5 初始定档
  MistakeNote note = repository.upsertForReview(review, source, sourceDetail(review), seed);
  metrics.recordNoteIngest(source);
  pregenerationService.enqueue(note.id());
}
```

**幂等规则不变**：`(user_id, problem_slug)` 唯一。已在队列中的题再次练习，**不重置调度状态**（只更新 `source_detail_json / updated_at`）；仅一个例外——若旧记录是 `REVIEW_PASSED` 且本次 `REVIEW_FAILED`（做对后又做错），视为 lapse 信号，允许把 `due_at` 提前到近期（见 §5.3）。

### 4.3 更新后的来源表

| 来源 | 触发 | 首个间隔 | MVP |
|---|---|---|---|
| `REVIEW_FAILED` | Review 未达标 | 立即到期（当日复习） | ✅ |
| `REVIEW_PASSED` | Review 达标 | 由 seed 策略给较长首间隔（§5） | ✅ 本期新增 |
| `USER_MARKED` | 用户手动标记 | 立即到期 | ✅ |
| `AI_WEAK` | AI 判定薄弱 | 立即到期 | 二期 |

---

## 5. 初始定档（Seed）：做对的题该几天后复习

这是本次改动的核心新增逻辑。失败题沿用「立即到期」；**达标题**需要按掌握置信度给首个间隔，避免灌爆队列，也避免把「真熟」的题和「刚会」的题一视同仁。

### 5.1 定档信号

从本次 `PracticeCodeReview` 与其会话可得的信号：

- **Review 分数**：越高越接近 PASS 阈值上限 → 掌握越稳。
- **难度**（`problem.difficulty`）：难题遗忘更快，首间隔更短。
- **借鉴/低置信信号**（见 §5.4）：提示次数、是否粘贴大段代码、AI 介入程度 → 命中则**当作低置信通过**，首间隔压缩。

### 5.2 定档函数（新增 `ReviewSeedPolicy`，纯函数、可单测）

产出一个初始 `SchedulingState` + `dueAt`，作为入库时写入的初值：

```
未达标(REVIEW_FAILED) / USER_MARKED:
    reps=0, interval=0, state=NEW, due=now           // 立即进当日队列（同现状）

达标(REVIEW_PASSED):
    低置信通过(见 §5.4)        → reps=0, interval=1,  state=LEARNING, due=now+1d
    普通达标                   → reps=1, interval=3,  state=LEARNING, due=now+3d
    高分达标(接近满分且非难题)   → reps=1, interval=4,  state=LEARNING, due=now+4d
    ease_factor 一律沿用默认 2.50
```

- 起点选 `interval∈{1,3,4}` 是为了对齐大纲的 `1/3/7/15` 冷启动梯度：达标题从第 3~4 天起步，之后由既有 `ReviewSchedulerService.apply()` 的 EF 自然推进，**无需改调度器**。
- 阈值（高分线、首间隔天数）全部走 `ReviewSchedulerProperties`，见 §9。

### 5.3 已在队列 + 结果变化的处理

- 旧 `REVIEW_PASSED` → 本次 `REVIEW_FAILED`：调 `scheduler.apply(state, FORGOT)` 语义的 lapse，`due` 提前、`lapses+1`、转 `LAPSED`。等价于「做对后又做错」，符合遗忘曲线。
- 旧 `REVIEW_FAILED` → 本次 `REVIEW_PASSED`：**不**因为一次达标就拉长间隔（达标≠通过 recall 验真）。保持现有调度状态不变，等其到期时走正常 recall 复习验证。

### 5.4 借鉴 / 低置信通过信号（防「假通过」）

**主要机制靠 recall-first 复习本身**：即使借鉴通过而入库，这题到期后的第一张复习卡要求用户「遮住代码复述思路」，讲不出就被 judge 判 `FORGOT`、重置间隔。**放宽入口 + recall 验真两者叠加，天然堵住抄答案幻觉**——这正是原设计防抄答案理念的自然延伸。

**辅助机制（可 MVP 轻量、可二期增强）**：入库时若命中「低置信」信号（本次练习提示次数 ≥ 阈值 / 粘贴大段外部代码 / 会话中 AI 大幅介入），走 §5.2 的「低置信通过」分支（首间隔压到 1 天，尽早验真），并在 `source_detail_json` 标 `lowConfidence=true` 供埋点与卡片生成参考。MVP 若暂无成熟信号，可先只用「分数 + 难度」定档，低置信分支预留但默认不触发。

---

## 6. 负载控制：别让「今天待复习」吓到人

放开入口后队列体量会显著增长，必须配三道闸：

1. **达标题首间隔更长**（§5.2）：做对的题从第 3~4 天起才首次到期，天然错峰。
2. **队列优先级排序**：`review-sessions/queue` 与列表默认排序改为
   `ORDER BY (source=REVIEW_FAILED OR lapses>0) DESC, due_at ASC, id ASC`
   —— 错题/顽固题优先，做对的复习题排后。
3. **每日复习量上限（软上限）**：`review-sessions/queue` 支持 `dailyCap`（默认可配，如 20），超出部分不计入「今日待复习 N 道」的醒目提示，但用户手动「继续复习」仍可拉取。上限只影响**呈现与提醒**，不影响 `due_at` 本身，避免制造「永远清不完」的焦虑。

> 首页「今日待复习 N 道」（3.3 学习台）取 `countDue` 时应用同一 `dailyCap`，口径统一。

---

## 7. 数据与迁移（V20）

新增 `backend/mentor-api/src/main/resources/db/migration/V20__review_queue_cover_passed.sql`（当前最大 V19）：

```sql
-- 扩展入库来源，纳入「达标通过」
ALTER TABLE mistake_note DROP CONSTRAINT IF EXISTS ck_mistake_note_source;
ALTER TABLE mistake_note ADD CONSTRAINT ck_mistake_note_source
  CHECK (source IN ('REVIEW_FAILED','REVIEW_PASSED','USER_MARKED','AI_WEAK'));
```

- **无新增列**：seed 写入的是已存在的 `mastery_state / repetitions / interval_days / due_at`；低置信标记进 `source_detail_json`（JSONB，无需 DDL）。
- **存量数据不迁移**：历史只有错题，语义不变；新规则只对新入库生效。
- `review_log` 无需变更（`grade_source` 已含 `CODE_REVIEW`）。

---

## 8. 代码改动清单（按模块）

**backend/mentor-application/.../review/**
- `MistakeSource`：加 `REVIEW_PASSED`。
- `ReviewSeedPolicy`（新增，纯函数）：`ReviewSeed forReview(PracticeCodeReview, boolean passed)`，产出初始 `SchedulingState + dueAt + lowConfidence`。
- `MistakeNoteService.ingestFromReview()`：去早退，改按结果分流 + 调 seed（§4.2）。
- `MistakeNoteRepository`：`upsertForReviewFailure` → 泛化为 `upsertForReview(review, source, sourceDetail, seed)`（seed 可空 = 沿用表默认，兼容失败路径）。

**backend/mentor-api/.../review/**
- `MistakeNoteMapper` + `MistakeNoteMapper.xml`：`upsertForReview` 支持写入初始调度列；`ON CONFLICT` 分支保持「不重置已有调度」（seed 仅用于首次 INSERT，`DO UPDATE` 不覆盖 `interval_days/due_at/mastery_state`）。
- `findDue` / `list` 的 `ORDER BY` 加入 §6.2 的优先级；`countDue` 与 queue 支持 `dailyCap`。

**backend/mentor-api/.../config/**
- `ReviewSchedulerProperties`（或新增 `ReviewSeedProperties`）：seed 阈值与首间隔天数、`dailyCap`。

**测试**
- `ReviewSeedPolicyTest`：失败→立即到期；普通达标→+3d；高分达标→+4d；低置信→+1d；难题不吃高分档。
- `MistakeNoteServiceTest`：passed 现在**入库**（`REVIEW_PASSED`）、幂等不重置、pass→fail 触发 lapse。
- `MistakeNoteMapper` XML 测试 / IT：`upsertForReview` 首次写入 seed、冲突不覆盖调度、queue 排序与 dailyCap。

**前端（frontend/src/mistakes/ 或改名 review/）**
- 文案：「错题本」→「复习中心」；来源标签区分「错题 / 复习」。
- 列表筛选加「仅看错题」（`source=REVIEW_FAILED` 或 `lapses>0`）。
- 类型 `MistakeSource` 联合类型加 `REVIEW_PASSED`，与后端同步。

---

## 9. 配置

```yaml
algo-mentor:
  review:
    seed:
      passed-first-interval-days: 3       # 普通达标首间隔
      passed-high-score-interval-days: 4  # 高分达标首间隔
      low-confidence-first-interval-days: 1 # 低置信通过首间隔
      high-score-ratio: 0.9               # 分数/满分 ≥ 此比例且非 HARD 视为高分
    queue:
      daily-cap: 20                       # 首页/队列提醒的每日软上限
```

---

## 10. 可观测性

- 复用 `review.note.ingest`（tag: `source`）—— 新增 `REVIEW_PASSED` 维度即可观察「做对入库」占比。
- 新增/复用埋点：`review.seed`（tag: `bucket=FAILED|NORMAL|HIGH_SCORE|LOW_CONFIDENCE`）看定档分布。
- 关注指标：`REVIEW_PASSED` 题的**首次复习 recall 通过率** —— 若显著低于失败题的后续通过率，说明「假通过」普遍，验证了做这件事的价值；反之说明做对确实大多真会，可调长首间隔省成本。

---

## 11. 兼容与回滚

- **纯增量、可回滚**：新入口默认开启；若要退回「只收错题」，把 `ingestFromReview` 的达标分支关掉（或加开关 `algo-mentor.review.ingest-passed=false`）即回到现状，存量数据不受影响。
- V20 仅放宽 CHECK 约束，不删列不改存量，回滚时收回约束前需确认无 `REVIEW_PASSED` 存量（或保留放宽的约束、仅停用写入）。
- 建议 `ingest-passed` 做成配置开关，灰度时可先对部分用户开启、观察队列体量与 recall 通过率再放量。

---

## 12. 分期

**本期（P0 补丁）**
- `REVIEW_PASSED` 入口 + `ReviewSeedPolicy`（分数 + 难度定档）+ V20 迁移。
- 队列优先级排序 + `dailyCap` 软上限。
- 前端文案切「复习中心」+「仅看错题」筛选。
- 埋点：ingest source 分布、seed 分布、passed 首次 recall 通过率。

**二期**
- 低置信信号接入（提示次数/粘贴代码/AI 介入）驱动 seed 与卡片。
- `ingest-passed` 灰度开关下线（默认全量）。
- 复习负载的个性化上限（按用户活跃度动态调 `dailyCap`）。
- 与能力画像/动态建议联动：用「做对但复习没过」的题作为薄弱信号。
