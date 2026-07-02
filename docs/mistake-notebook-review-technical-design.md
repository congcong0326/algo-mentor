# 错题本 + 间隔重复复习 —— 技术详细设计

> ⚠️ 后续演进：本文的「只有 Review 未达标才入库」已被修正。做对的题同样遵循遗忘曲线、且达标≠真懂，因此入口放宽到「练过就进」。改动方案见 `docs/review-queue-cover-all-practiced-design.md`（新增 `REVIEW_PASSED` 来源 + 初始定档 seed + 队列负载控制）。本文 §5 的入库判定应以该文为准。
> 上游产品设计：`docs/product-planning/p0-mistake-notebook-spaced-repetition-design.md`（本文严格实现其已定决策，不重复论证产品动机）。
> 本文目标：给出**可直接落地**的模块划分、数据模型、领域模型、服务边界、LLM 契约、API 契约、配置、可观测性、测试与分步任务，供执行方（人或模型）照此实现。
> 语言/风格约束遵循 `AGENTS.md`：JDK 17、Spring MVC、PostgreSQL + Flyway + MyBatis、`org.congcong.algomentor.*`、SSE、Jackson、Logback(@Slf4j)、Micrometer；跨模块字面量进常量类/枚举。

---

## 0. 已定产品决策（实现基线，来自上游 §12）

1. 评级由 **AI 判定为主**（自评仅辅助信号，不入库驱动调度）。
2. 默认复习形态 = **思路复述（RECALL）**，重做（RESOLVE）兜底。
3. 调度 = **SM-2 简化版**；毕业阈值 `interval_days ≥ 30 且 repetitions ≥ 3`；冷启动固定梯度 `1/3/7/15/30`，本文实现直接落 EF 版（固定梯度作为 `repetitions≤1` 的初值）。
4. 卡片 = **C 档 AI 生成为全量默认** + **后台异步预生成 + signature 缓存**；B 档规则模板 / A 档静态模板为兜底。
5. 预生成触发（MVP）= **入队即生成** + **会话内 prefetch**；每日批处理 worker 二期。
6. 单用户预生成配额 = **每天 20 张 C 卡**（可配置）；超限走兜底档；judge 与缓存命中不计数。
7. 备注 = 一次性卡片备注 + 题目长期备注两级，纯文本，默认不入 judge。
8. 测试阶段单模型、全功能放开、不设会员墙（分层仅埋接口，不启用）。

**MVP 不做**：AI 判定薄弱自动入库、每日批处理 worker、D 档对话式复习、移动端 swipe、备注富文本、会员分层启用。

---

## 1. 架构总览

### 1.1 模块落位

沿用现有分层，不新增 Maven 模块（MVP 内联异步，二期再抽 `复习卡预生成` worker）：

```
backend/mentor-application/.../mentor/application/review/        ← 新增子包（领域 + 应用服务 + 端口 + LLM 契约）
backend/mentor-api/.../api/controller/review/                    ← 新增（Controller + 异常处理）
backend/mentor-api/.../api/review/model/                         ← 新增（DTO + Mapper）
backend/mentor-api/.../api/review/repository/                    ← 新增（MyBatis 实现）
backend/mentor-api/.../resources/mapper/review/                  ← 新增（Mapper XML）
backend/mentor-api/.../resources/db/migration/V18__mistake_review_schema.sql  ← 新增迁移
frontend/src/{types,services,mistakes}/                          ← 新增类型/服务/页面
```

> 新建独立子包 `mentor.application.review`，与 `practice` 平级，避免继续在 practice 平铺（遵循 AGENTS.md「按职责分类组织」）。

### 1.2 核心数据流

```
① 入库
  PracticeCodeReviewService.save() 成功
    └─(观察者) PracticeCodeReviewObserver.onReviewSaved(review)
         └─ MistakeNoteService.ingestFromReview(review)   // passed=false → upsert 错题, 触发首次调度
              └─ ReviewCardPregenerationService.enqueue(noteId)   // 入队即生成下一张卡

② 用户手动标记
  POST /api/mistake-notes  → MistakeNoteService.mark(userId, slug, source=USER_MARKED)
         └─ enqueue 预生成

③ 复习会话
  GET  /api/review-sessions/queue        → 今日待复习队列（due_at<=now）
  GET  /api/mistake-notes/{id}/card      → ReviewCardService.getOrFallback(note)  // 缓存命中→C卡；否则即时B卡+后台补生成
  POST /api/mistake-notes/{id}/recall    → ReviewSessionService.submitRecall(...)
         ├─ RecallJudgeService.judge(note, recallText)   // LLM 结构化 → grade + 命中/遗漏
         ├─ ReviewSchedulerService.apply(state, grade)   // 纯函数 → 新调度状态
         ├─ MistakeNoteRepository.updateScheduling(...)
         ├─ ReviewLogRepository.append(logEntry)
         └─ ReviewCardPregenerationService.enqueue(noteId)  // 为下一次复习预生成

④ 预生成
  ReviewCardPregenerationService（内联 @Async 执行器）
    ├─ 配额：AiDailyUsageStore.tryConsumeRequest(userId, today, "REVIEW_CARD_GEN", limit=20)
    │     false → 跳过（下次打开走兜底 B 档）
    ├─ signature 命中且未过期 → 跳过（复用旧卡）
    └─ ReviewCardService.generate(note)  // C 档 LLM 结构化 → 写 pending_card_json + signature
```

### 1.3 复述判定时序（是否走 SSE）

- **复述判定默认走同步 REST**（`POST .../recall` 返回结构化结果），非 SSE：judge 是一次结构化输出、无需逐字流式，前端「提交→转圈→出评级卡」即可。这样避免为复习单独拉一套 SSE 通道。
- 若产品希望 judge 讲评逐字流式，可二期复用 `PracticeMessageStreamService` + `LlmStreamSseMapper`，本文 MVP 不做。

---

## 2. 数据模型与迁移

新增 `backend/mentor-api/src/main/resources/db/migration/V18__mistake_review_schema.sql`。
（Flyway 版本空间跨模块共享，当前最大 V17，取 **V18**。）

```sql
-- 错题本主表：每 (user, problem) 一条，聚合掌握度与调度状态
CREATE TABLE IF NOT EXISTS mistake_note (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  problem_slug VARCHAR(220) NOT NULL,              -- 对齐 problem.slug
  source VARCHAR(32) NOT NULL,                     -- 首次入库来源
  source_detail_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  origin_plan_id BIGINT NULL,
  origin_phase_index INT NULL,
  origin_practice_session_id BIGINT NULL,
  mastery_state VARCHAR(16) NOT NULL DEFAULT 'NEW',
  repetitions INT NOT NULL DEFAULT 0,
  ease_factor NUMERIC(4,2) NOT NULL DEFAULT 2.50,
  interval_days INT NOT NULL DEFAULT 0,
  due_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),        -- NEW 立即到期，进首日队列
  lapses INT NOT NULL DEFAULT 0,
  last_reviewed_at TIMESTAMPTZ NULL,
  last_grade SMALLINT NULL,                         -- 2..5
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  user_note_persistent TEXT NULL,                   -- 题目长期备注
  -- 预生成卡片缓存（MVP 内联；量大可拆 review_card 表）
  pending_card_json JSONB NULL,
  pending_card_variant VARCHAR(16) NULL,            -- STATIC/RULE_BASED/AI_GENERATED
  pending_card_signature VARCHAR(64) NULL,          -- 提交历史指纹
  pending_card_generated_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_mistake_note_user_problem UNIQUE (user_id, problem_slug),
  CONSTRAINT ck_mistake_note_source
    CHECK (source IN ('REVIEW_FAILED','USER_MARKED','AI_WEAK')),
  CONSTRAINT ck_mistake_note_state
    CHECK (mastery_state IN ('NEW','LEARNING','MASTERED','LAPSED')),
  CONSTRAINT ck_mistake_note_grade CHECK (last_grade IS NULL OR last_grade BETWEEN 2 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_mistake_note_due
  ON mistake_note (user_id, due_at) WHERE archived = FALSE;
CREATE INDEX IF NOT EXISTS idx_mistake_note_state
  ON mistake_note (user_id, mastery_state);

-- 复习流水：每次复习一条，审计 + 效果分析 + 卡片档位对比
CREATE TABLE IF NOT EXISTS review_log (
  id BIGSERIAL PRIMARY KEY,
  mistake_note_id BIGINT NOT NULL REFERENCES mistake_note(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  review_mode VARCHAR(16) NOT NULL,                 -- RECALL / RESOLVE
  card_variant VARCHAR(16) NULL,                    -- 本次展示卡片档位
  card_prompt_json JSONB NULL,                      -- 本次卡片结构（效果对比）
  user_recall_text TEXT NULL,                       -- RECALL 复述全文
  user_note_transient TEXT NULL,                    -- 本次一次性备注
  grade SMALLINT NOT NULL,                          -- 2..5
  grade_source VARCHAR(16) NOT NULL,                -- AI_RECALL / CODE_REVIEW / SELF
  ai_judgment_json JSONB NULL,                      -- 命中/遗漏/误区
  practice_code_review_id BIGINT NULL,              -- RESOLVE 关联
  recall_message_id BIGINT NULL,                    -- 预留：judge 会话 message
  interval_before INT NOT NULL,
  interval_after INT NOT NULL,
  ease_factor_before NUMERIC(4,2) NOT NULL,
  ease_factor_after NUMERIC(4,2) NOT NULL,
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_review_log_mode CHECK (review_mode IN ('RECALL','RESOLVE')),
  CONSTRAINT ck_review_log_grade CHECK (grade BETWEEN 2 AND 5),
  CONSTRAINT ck_review_log_grade_source
    CHECK (grade_source IN ('AI_RECALL','CODE_REVIEW','SELF'))
);

CREATE INDEX IF NOT EXISTS idx_review_log_note ON review_log (mistake_note_id, reviewed_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_log_user ON review_log (user_id, reviewed_at DESC);
```

> 复习卡配额复用治理层 `ai_daily_usage`（V10 已建，`(user_id, quota_date, scope)`），**无需新表**，scope 取常量 `REVIEW_CARD_GEN`。

---

## 3. 领域模型（`mentor.application.review`）

全部用不可变 record / enum；字段命名对齐表列。

```java
public enum MasteryState { NEW, LEARNING, MASTERED, LAPSED }
public enum ReviewMode { RECALL, RESOLVE }
public enum MistakeSource { REVIEW_FAILED, USER_MARKED, AI_WEAK }
public enum GradeSource { AI_RECALL, CODE_REVIEW, SELF }
public enum CardVariant { STATIC, RULE_BASED, AI_GENERATED }

/** 评级 4 档，携带 SM-2 q 值。 */
public enum ReviewGrade {
  FORGOT(2), BARELY(3), MASTERED(4), FLUENT(5);
  private final int q;
  ReviewGrade(int q) { this.q = q; }
  public int q() { return q; }
  public static ReviewGrade ofQ(int q) { /* 2..5 → 枚举，越界 clamp */ }
}

/** 调度状态（纯数据，供 SchedulerService 输入/输出）。 */
public record SchedulingState(int repetitions, BigDecimal easeFactor,
                              int intervalDays, MasteryState masteryState, int lapses) {}

/** 错题聚合根（读模型）。 */
public record MistakeNote(long id, long userId, String problemSlug, MistakeSource source,
    Map<String,Object> sourceDetail, Long originPlanId, Integer originPhaseIndex,
    Long originPracticeSessionId, SchedulingState scheduling, Instant dueAt,
    Instant lastReviewedAt, ReviewGrade lastGrade, boolean archived,
    String userNotePersistent, ReviewCardCache pendingCard,
    Instant createdAt, Instant updatedAt) {}

public record ReviewCardCache(JsonNode cardJson, CardVariant variant,
                              String signature, Instant generatedAt) {}

/** 复习卡结构（§6.4 产品文档同构，既是规则输出也是 LLM 结构化输出目标）。 */
public record ReviewCard(CardVariant cardVariant, ProblemRef problemRef, String contextSummary,
    List<ReviewCardPrompt> prompts, ReviewCardScaffold scaffold,
    String revealPolicy, String expectedEffort) {}
public record ReviewCardPrompt(String key, String label, String hint) {}
public record ReviewCardScaffold(String templateMarkdown, int maxInputChars) {}
public record ProblemRef(String slug, String titleCn, String difficulty) {}

/** RECALL judge 结果。 */
public record RecallJudgment(ReviewGrade grade, List<String> hitPoints,
                             List<String> missedPoints, String gapSummary) {}
```

---

## 4. 调度引擎 `ReviewSchedulerService`

**纯函数、无 IO、无 LLM、无时间依赖（`now` 由调用方传入）** —— 最容易单测、最需覆盖边界。

```java
public final class ReviewSchedulerService {
  private static final BigDecimal MIN_EF = new BigDecimal("1.30");
  private static final int GRADUATION_INTERVAL = 30;
  private static final int GRADUATION_REPS = 3;

  /** 输入当前状态 + 本次评级，输出新状态与新的到期时间。 */
  public Scheduled apply(SchedulingState s, ReviewGrade grade, Instant now) {
    int q = grade.q();
    int reps; int interval; MasteryState state; int lapses = s.lapses();
    BigDecimal ef = updateEase(s.easeFactor(), q);          // EF 每次都更新
    if (q < 3) {                                            // 忘了：重置
      reps = 0; interval = 1; lapses = s.lapses() + 1; state = MasteryState.LAPSED;
    } else {
      reps = s.repetitions() + 1;
      interval = switch (reps) {
        case 1 -> 1;
        case 2 -> 3;
        default -> Math.max(1, round(s.intervalDays() * ef.doubleValue()));
      };
      state = (interval >= GRADUATION_INTERVAL && reps >= GRADUATION_REPS)
          ? MasteryState.MASTERED : MasteryState.LEARNING;
    }
    Instant dueAt = now.plus(Duration.ofDays(interval));
    return new Scheduled(new SchedulingState(reps, ef, interval, state, lapses), dueAt);
  }

  private BigDecimal updateEase(BigDecimal ef, int q) {
    double delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02);
    return ef.add(BigDecimal.valueOf(delta)).max(MIN_EF).setScale(2, RoundingMode.HALF_UP);
  }
  public record Scheduled(SchedulingState state, Instant dueAt) {}
}
```

**必须覆盖的单测用例**：
- 首次 NEW + FLUENT → reps=1, interval=1, LEARNING（reps=1 固定 1 天，不因 q 高跳级）。
- 连续 4 次 MASTERED：interval 走 1→3→~7→~18，reps=4，EF 微升，state 在 interval≥30 后转 MASTERED。
- LEARNING 中 FORGOT → reps=0, interval=1, lapses+1, LAPSED。
- EF 下限：连续 FORGOT/BARELY 使 EF clamp 到 1.30 不再下探。
- `now` 注入决定 `dueAt`，不调用 `Instant.now()`（可测）。

---

## 5. 错题入库（事件驱动，幂等）

### 5.1 观察者接入（不改 Review 核心签名，镜像 metrics 注入方式）

在 `PracticeCodeReviewService` 增加可选观察者（默认 NOOP，与 `PracticeCodeReviewMetrics` 同款可选依赖）：

```java
public interface PracticeCodeReviewObserver {
  void onReviewSaved(PracticeCodeReview review);       // save 成功后回调
  PracticeCodeReviewObserver NOOP = review -> {};
}
```

`saveReviewedDraft(...)` 保存成功后调用 `observer.onReviewSaved(saved)`；异常吞掉并 `log.warn`，**绝不影响 Review 主流程**。

### 5.2 入库服务

```java
public class MistakeNoteService {
  // Review 未达标 → 入库；达标 → 若已在错题本则不动（复习流程自会推进/毕业）
  public void ingestFromReview(PracticeCodeReview r) {
    if (r.passed() && r.score().total().compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0) return;
    MistakeNote note = repo.upsertForReviewFailure(/* user/plan/phase/slug/sessionId + 扣分点写 sourceDetail */);
    pregeneration.enqueue(note.id());
  }
  public MistakeNote mark(long userId, String slug, MistakeSource source) { ... }   // 手动标记
  public void archive(long userId, long noteId) { ... }                             // 归档/移除
  public void updatePersistentNote(long userId, long noteId, String text) { ... }   // 长期备注
}
```

**幂等**：`upsert...` 依赖唯一键 `(user_id, problem_slug)`；重复未达标只更新 `source_detail_json` 与 `updated_at`，已存在的调度状态**不重置**（除非用户显式「重新学习」）。

---

## 6. 卡片生成 `ReviewCardService`

### 6.1 取卡（打开卡片时，零等待）

```java
public ReviewCard getOrFallback(MistakeNote note) {
  String sig = signature(note);                       // 见 6.4
  ReviewCardCache c = note.pendingCard();
  if (c != null && c.variant() == AI_GENERATED
      && sig.equals(c.signature())
      && !expired(c.generatedAt())) {                 // 命中且未过期
    return deserialize(c.cardJson());                 // C 卡，零等待
  }
  pregeneration.enqueue(note.id());                   // 后台补生成，供下次
  return ruleBasedComposer.compose(note);             // 即时兜底 B 档（无 LLM）
}
```

### 6.2 C 档生成（LLM 结构化输出，复用现有 gateway 模式）

严格对齐 `PracticeCodeReviewService.request(...)` 的写法：
- `LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT))`
- `LlmResponseFormat.JsonSchema(ReviewCardConstants.CARD_SCHEMA_NAME, ReviewCardJsonSchema.schema(), true)`
- prompt 注入题面摘要 + 标签 + **上次 Review 扣分点/分数/代码摘要**（来自 `mistake_note.source_detail_json` 与最近 `practice_code_review`），强约束「问关键点、给支架、限制 `max_input_chars`、不泄题解」。
- 失败/超时 → 返回 null，由预生成流程降级，不落缓存。

`ReviewCardJsonSchema.schema()` 手写 Jackson `ObjectNode`（镜像 `PracticeCodeReviewJsonSchema`），字段 = §3 `ReviewCard`（`card_variant` 固定 `AI_GENERATED`、`prompts[]`、`scaffold`、`reveal_policy`、`expected_effort`）。

### 6.3 B/A 档规则引擎 `RuleBasedCardComposer`（无 LLM）

按 `(difficulty, mastery_state, lapses, last_grade)` 查表决定 prompts 与 scaffold：
- EASY 且 last_grade≥MASTERED → 仅 `key_step + complexity`（回算关键点）。
- HARD 或 lapses≥2 → 全量四问 + `scaffold`（结构化模板，`max_input_chars=400`）。
- 其余 → 三问，无 scaffold。
A 档 = 忽略信号的固定四问，作为 B 档不可用时的最终兜底。

### 6.4 signature（缓存复用的关键）

`signature = sha256(problemSlug + latestReviewId + latestReviewScore + masteryState + lapses)` 截断 64 hex。
含义：**提交历史/掌握度未变 → signature 命中 → 复用旧卡不重生成**（省成本，尤其 MASTERED 巡检题）。

---

## 7. 异步预生成 `ReviewCardPregenerationService`

```java
public class ReviewCardPregenerationService {
  private final Executor executor;                    // MVP：有界线程池（应用内 @Async）
  private final AiDailyUsageStore usageStore;         // 治理层已有
  private final MistakeNoteRepository repo;
  private final ReviewCardService cardService;
  private final ReviewCardProperties props;

  public void enqueue(long noteId) {
    executor.execute(() -> safelyGenerate(noteId));   // 不阻塞调用线程/事务
  }

  private void safelyGenerate(long noteId) {
    MistakeNote note = repo.findById(noteId).orElse(null);
    if (note == null || note.archived()) return;
    String sig = cardService.signature(note);
    if (cardService.cacheHit(note, sig)) return;                          // 命中不生成、不计数
    if (!usageStore.tryConsumeRequest(note.userId(), today(),
            ReviewCardConstants.QUOTA_SCOPE, props.dailyLimit())) {       // 配额：20/天
      metrics.recordCardGen(CardGenOutcome.QUOTA_EXCEEDED); return;       // 超限：不生成，打开时走 B 档
    }
    ReviewCard card = cardService.generate(note);                        // C 档 LLM
    if (card == null) { metrics.recordCardGen(FAILED); return; }         // 失败：不落缓存，下次走 B 档
    repo.savePendingCard(noteId, serialize(card), AI_GENERATED, sig, now());
    metrics.recordCardGen(GENERATED);
  }
}
```

- **触发点（MVP）**：`MistakeNoteService.ingestFromReview/mark` 之后、`ReviewSessionService.submitRecall` 之后各 `enqueue(noteId)`（入队即生成）；`ReviewSessionController.queue` 返回时对队列前若干条 `enqueue`（会话内 prefetch）。
- **配额计数口径**：只有真正发起 LLM 生成才 `tryConsumeRequest`；命中缓存 / 降级 B 档 / 生成失败前的短路都不计数。
- **`today()`/`now()`** 用注入的 `Clock`，便于测试。
- **二期**：把 `executor` 换成独立 `复习卡预生成` worker + 每日批处理扫 `due_at ∈ [今日,明日]`。

---

## 8. 复述判定 `RecallJudgeService`

LLM 结构化输出（同 §6.2 gateway 写法），schema = `RecallJudgment`：

```jsonc
{ "grade": "FORGOT|BARELY|MASTERED|FLUENT",
  "hitPoints": ["..."], "missedPoints": ["..."], "gapSummary": "一句差在哪" }
```

- prompt：注入题面摘要 + 标签 + 用户复述文本；**rubric 分项**（算法选型/关键步骤/复杂度/边界）逐项判断后给总档；明确「无官方题解，基于通用算法知识判断，拿不准就偏保守（BARELY）」。
- **不把 `user_note_transient` 喂给 judge**（默认，防用户把答案写进备注刷分）。
- 失败降级：LLM 失败 → 返回 `RecallJudgment(BARELY, [], [], "本次判定异常，按保守处理")`，仍推进调度但打 `metrics FAILED`；绝不因 judge 失败卡住用户。

---

## 9. 复习会话编排 `ReviewSessionService`

```java
public List<MistakeNote> dueQueue(long userId, int limit) {
  return repo.findDue(userId, Instant.now(clock), limit);   // due_at<=now, archived=false, 按 due_at 升序
}

public RecallReviewResult submitRecall(long userId, long noteId, String recallText, String transientNote) {
  MistakeNote note = repo.findForUser(noteId, userId).orElseThrow();
  RecallJudgment j = recallJudge.judge(note, recallText);
  var scheduled = scheduler.apply(note.scheduling(), j.grade(), Instant.now(clock));
  repo.updateScheduling(noteId, scheduled.state(), scheduled.dueAt(), j.grade(), Instant.now(clock));
  reviewLog.append(/* mode=RECALL, card_variant(本次), grade, grade_source=AI_RECALL,
                      ai_judgment_json=j, recallText, transientNote, before/after interval&ef */);
  pregeneration.enqueue(noteId);                            // 为下一次预生成
  return new RecallReviewResult(j, scheduled.dueAt(), scheduled.state());
}
```

- **RESOLVE 模式**：不新建做题栈，复用现有 practice 流程——前端跳到题目训练工作台重做；重做产生的 `practice_code_review` 落库后，由 §5.1 观察者回调 `MistakeNoteService.onResolveReviewed(review)`，以 `grade_source=CODE_REVIEW` 写 `review_log` 并按「达标↑/未达标↓」映射 grade（达标→MASTERED(4)，未达标→FORGOT(2)），再 `scheduler.apply`。
- **备注**：`user_note_transient` 存入本次 `review_log`；前端「置顶到题目备注」→ `PATCH /api/mistake-notes/{id}/note` 写 `user_note_persistent`。

---

## 10. API 契约

新增路径常量进 `ApiContractConstants`：

```java
public static final String MISTAKE_NOTES_BASE_PATH = "/api/mistake-notes";
public static final String REVIEW_SESSIONS_BASE_PATH = "/api/review-sessions";
```

| 方法 & 路径 | 用途 | 请求 | 响应 |
|---|---|---|---|
| `GET  /api/mistake-notes` | 错题列表（筛选：state/source/tag/planId，分页） | query params | `List<MistakeNoteResponse>` |
| `POST /api/mistake-notes` | 手动加入错题本 | `{ problemSlug }` | `MistakeNoteResponse` |
| `PATCH /api/mistake-notes/{id}/archive` | 归档/移除 | `{ archived }` | `MistakeNoteResponse` |
| `PATCH /api/mistake-notes/{id}/note` | 更新长期备注 | `{ text }` | `MistakeNoteResponse` |
| `GET  /api/mistake-notes/{id}/card` | 取本次复习卡（缓存命中 C / 兜底 B） | — | `ReviewCardResponse` |
| `POST /api/mistake-notes/{id}/recall` | 提交思路复述 → 判定+调度 | `{ recallText, transientNote? }` | `RecallReviewResponse`（grade/命中/遗漏/nextDueAt/masteryState） |
| `GET  /api/review-sessions/queue` | 今日待复习队列 + 计数 | `?limit=20` | `ReviewQueueResponse` |
| `GET  /api/review-sessions/summary` | 复习小结（今日通过 x/N、掌握度变化） | — | `ReviewSummaryResponse` |

约定：全部 `ApiResponse<T>` 包裹；`userId` 来自 `CurrentUserIdProvider`，**不接收前端声明的 userId**（对齐权限设计）；DTO 放 `api/review/model/`，Mapper 同目录（镜像 `PracticeCodeReviewResponseMapper`）。

> 首页「待复习 N 道」（产品 §7）在 3.3 学习台聚合 API 内调用 `review-sessions/queue` 的计数，本文只保证该计数可查。

---

## 11. 常量与枚举登记（AGENTS.md 硬约束）

新增 `mentor.application.review.MistakeReviewConstants`：

```java
public final class MistakeReviewConstants {
  public static final String QUOTA_SCOPE = "REVIEW_CARD_GEN";     // ai_daily_usage.scope
  public static final String CARD_SCHEMA_NAME = "review_card_v1";
  public static final String JUDGE_SCHEMA_NAME = "recall_judgment_v1";
  public static final String REVEAL_HIDE_PREVIOUS = "HIDE_PREVIOUS_CODE_AND_SOLUTION";
  public static final String METADATA_MISTAKE_NOTE_ID = "mistakeNoteId";
  private MistakeReviewConstants() {}
}
```

状态值 / 来源 / 档位 / grade 一律走 §3 枚举，不散字面量；DB CHECK 与枚举 `name()` 严格一致。

---

## 12. 前端契约

- `frontend/src/types/api.ts`：`MistakeNote`, `ReviewCard`(+prompts/scaffold), `RecallReviewResult`, `ReviewQueueResponse`, `MasteryState`/`ReviewGrade`/`CardVariant` 联合类型，与后端 DTO 同步。
- `frontend/src/services/api.ts`：`listMistakeNotes`, `markMistake`, `archiveMistake`, `updateMistakeNote`, `getReviewCard`, `submitRecall`, `getReviewQueue`。
- 页面（`frontend/src/mistakes/`）：`MistakeNotebookPage.tsx`（列表+筛选+掌握度概览）、`ReviewSessionPage.tsx`（卡片流：遮蔽题解→复述输入`maxInputChars`约束→提交→评级卡→下一张/快捷键）、题目工作台内「加入错题本」角标按钮。
- 遵循 AGENTS.md：2 空格缩进、PascalCase 组件、`use` 前缀 hooks；默认不启动 Vite。

---

## 13. 配置项（`application.yml`）

```yaml
algo-mentor:
  review:
    card-gen:
      daily-limit: 20            # 单用户每日 C 卡预生成上限
      cache-ttl-hours: 168       # signature 未命中前的卡片有效期（7 天）
      prefetch-count: 3          # 会话内向后预取张数
    scheduler:
      graduation-interval-days: 30
      graduation-repetitions: 3
```

绑定为 `ReviewCardProperties` / `ReviewSchedulerProperties`（`@ConfigurationProperties`），默认值即上表。

---

## 14. 可观测性（Micrometer）

- `review.card.generate`（tag: outcome=GENERATED|CACHE_HIT|QUOTA_EXCEEDED|FAILED|FALLBACK_RULE）计数 + 生成耗时 timer。
- `review.recall.judge`（tag: grade, outcome）计数 + 耗时。
- `review.session.submit` 计数；`review.note.ingest`（tag: source）计数。
- `review.card.gen.tokens`：预生成 LLM token（并入 5.4 单用户成本，按 `card_variant` 拆）。
- 日志：入库、生成降级、配额耗尽、judge 失败均 `log.info/warn`；**不打印复述全文中的敏感内容**（遵循日志红线，复述本身非敏感但避免超长，截断记录长度）。

---

## 15. 测试计划

**单元测试（Surefire）**
- `ReviewSchedulerServiceTest`：§4 全部边界用例（注入 `now`/`Clock`）。
- `RuleBasedCardComposerTest`：各 `(difficulty×state×lapses)` 组合的 prompts/scaffold。
- `MistakeNoteServiceTest`：passed=true 不入库、passed=false upsert、幂等不重置调度、手动标记。
- `ReviewCardServiceTest`：缓存命中复用 / signature 变化触发重生 / 生成失败降级 B。
- `ReviewCardPregenerationServiceTest`：配额耗尽跳过、命中不计数、失败不落缓存（`AiDailyUsageStore` 用 stub）。
- `RecallJudgeServiceTest`：结构化输出映射、LLM 失败降级 BARELY（gateway stub）。
- Schema 测试：`ReviewCardJsonSchema` / `RecallJudgeJsonSchema` 结构断言（镜像现有 `PracticeCodeReviewJsonSchema` 测试）。

**集成测试（Failsafe，`*IT.java`，需 PostgreSQL）**
- 迁移 V18 生效；`mistake_note`/`review_log` CRUD + 唯一键幂等 + `findDue` 排序。
- `PracticeCodeReviewObserver` → 入库端到端（未达标 Review 落库触发错题）。
- REST：`recall` 提交后 `due_at` 推进、`review_log` 落一条、下一张卡入队。

---

## 16. 落地任务拆解（按依赖排序，可直接分派）

| # | 任务 | 产出 | 依赖 |
|---|---|---|---|
| T1 | V18 迁移 + 领域枚举/record（§2、§3） | 迁移脚本、domain 包 | — |
| T2 | `MistakeNoteRepository` 接口 + MyBatis 实现 + XML + `findDue`/`upsert`/`updateScheduling`/`savePendingCard` | 端口 + `api/review/repository` + mapper | T1 |
| T3 | `ReviewLogRepository` 接口 + 实现 | 同上 | T1 |
| T4 | `ReviewSchedulerService` + 单测（纯函数，先行可并行） | service + test | T1 |
| T5 | `RuleBasedCardComposer`（B/A 档）+ 单测 | service + test | T1 |
| T6 | `ReviewCardJsonSchema` + `RecallJudgeJsonSchema` + `ReviewCardService.generate`（C 档 LLM） | LLM 契约 | T1,T5 |
| T7 | `RecallJudgeService`（LLM + 降级） | service | T1 |
| T8 | 配额接线（`AiDailyUsageStore` scope=REVIEW_CARD_GEN）+ `ReviewCardPregenerationService` | service + 配置 | T2,T6 |
| T9 | `MistakeNoteService`（入库/标记/归档/备注）+ `PracticeCodeReviewObserver` 接入 Review 保存路径 | service + 改 `PracticeCodeReviewService` | T2,T8 |
| T10 | `ReviewSessionService`（队列/取卡/提交复述编排） | service | T2,T3,T4,T6,T7,T8 |
| T11 | Controller + DTO + Mapper + `ApiContractConstants` 路径 + 异常处理 | `api/controller/review`、`api/review/model` | T9,T10 |
| T12 | 配置属性 + autoconfiguration/bean 装配（NOOP 默认、`getIfAvailable` 兜底，镜像 `AgentConversationApiAutoConfiguration`） | config | T2–T11 |
| T13 | 可观测指标接线（§14） | metrics adapter | T8,T10 |
| T14 | 前端类型/服务/页面（§12） | frontend | T11 |
| T15 | 单测 + IT（§15），`make backend-test` 通过 | tests | 各服务 |

---

## 17. 风险与回滚

- **回滚**：功能纯增量（新表 V18、新包、新 API、Review 保存路径加 NOOP 观察者）。关闭方式：不装配 review beans / 观察者置 NOOP，即回到无错题本状态，不影响 practice 主链路；`mistake_note`/`review_log` 表保留不删。
- **成本失控**：预生成配额 `daily-limit` 兜底；signature 缓存复用；生成失败/超限一律降级 B 档；按 `card_variant` 拆成本可观测，异常可临时把 `daily-limit` 调 0（全走 B 档）。
- **判定信度**：judge rubric + 保守降级；`review_log.ai_judgment_json` 全量留痕，供后续 eval（挂 `pyproject` eval runner）与 prompt 迭代。
- **合规**：`mistake_note`/`review_log` 只存用户自己的复述/代码摘要与事实性元数据，**不缓存受版权保护的题面/他人题解原文**（遵循上游 4.0 合规红线）。
- **迁移版本冲突**：V18 跨模块唯一；提交前 `grep -rE 'V18__'` 确认无他人占用。
```
