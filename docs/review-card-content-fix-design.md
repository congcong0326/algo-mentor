# 复习卡内容修复研发设计

> 上游：`docs/product-planning/p0-mistake-notebook-spaced-repetition-design.md`、`docs/mistake-notebook-review-technical-design.md`、`docs/review-queue-cover-all-practiced-design.md`。
> 本文修订点：复习主界面不再默认展示题面相关片段；题面仅在用户主动点击眼睛图标或"查看题面"时，通过题库接口按需查询。

## 0. TL;DR

1. 复习卡首屏只展示题目标题、难度、复习上下文、复述提示和输入区。
2. 完整题面不进入 `GET /api/mistake-notes/{id}/card` 响应，也不写入错题记录来源详情。
3. 用户主动查看题面时，前端调用 `GET /api/mistake-notes/{id}/problem-statement`，后端根据 noteId 校验归属后用 `problemSlug` 查题库。
4. `MistakeNoteService` 仍可在入库时补齐 `titleCn / difficulty`，用于列表和复习卡标题展示。
5. pass→fail 触发 lapse 的观测补齐保留：记录 `review.note.ingest{outcome=INSERTED|UPDATED|LAPSED}`。

## 1. 问题定位

复习中心此前存在两个体验问题：

- 开始复习时标题可能退化成 `slug`，例如显示 `unique-paths` 而不是中文标题。
- 旧设计把题面片段塞进复习卡首屏，容易弱化 recall-first 的训练目标，也让错题记录保存了不必要的题库快照。

设计原意是隐藏旧代码和题解，不是完全禁止查看题面。因此新方案是：默认不展示题面，用户需要时按需查看完整题面。

## 2. 决策

| # | 决策 | 采用理由 |
|---|---|---|
| D1 | 复习卡首屏不展示题面内容 | 复习首屏关注独立复述；题面是用户主动打开时才需要的信息。 |
| D2 | 完整题面通过 `GET /api/mistake-notes/{id}/problem-statement` 按需返回 | 避免扩大卡片主 payload，也避免在错题记录里保存题库内容快照。 |
| D3 | `titleCn / difficulty` 可在错题入库时由题库补齐 | 标题和难度是轻量展示元数据，能修复标题退化为 slug 的问题。 |
| D4 | `ReviewCardService.generate()` 不要求模型输出题面字段 | 模型只负责复习上下文、提示和脚手架；题面事实由题库提供。 |
| D5 | pass→fail lapse 观测在服务层补齐 | 不改 SQL 语义，只补充可观测 outcome。 |

## 3. API 契约

### 3.1 复习卡

`GET /api/mistake-notes/{id}/card` 返回复习卡内容，但不包含题面字段：

```java
public record ReviewCard(
    CardVariant cardVariant,
    ProblemRef problemRef,
    String contextSummary,
    List<ReviewCardPrompt> prompts,
    ReviewCardScaffold scaffold,
    String revealPolicy,
    String expectedEffort
) {}
```

### 3.2 完整题面

`GET /api/mistake-notes/{id}/problem-statement`

```json
{
  "slug": "unique-paths",
  "titleCn": "不同路径",
  "difficulty": "MEDIUM",
  "contentMarkdown": "..."
}
```

后端处理流程：

1. 解析当前用户。
2. 通过 `MistakeNoteService.get(userId, noteId)` 校验错题记录归属。
3. 取记录中的 `problemSlug`。
4. 通过 `ReviewProblemCatalog.findBySlug(problemSlug)` 查题库。
5. 返回完整 Markdown；题目不存在时返回业务错误。

## 4. 后端实现

### 4.1 题库端口

`ReviewProblemCatalog` 保持在 `mentor.application.review` 包中，mentor-api 提供基于 `ProblemService` 的实现。

```java
public interface ReviewProblemCatalog {
  Optional<ReviewProblemSnapshot> findBySlug(String slug);
}

public record ReviewProblemSnapshot(
    String slug,
    String titleCn,
    String difficulty,
    String fullStatementMarkdown
) {}
```

### 4.2 错题入库

`MistakeNoteService.ingestFromReview()` 与 `MistakeNoteService.mark()` 在构造 `sourceDetail` 时只补齐轻量展示元数据：

```json
{
  "titleCn": "不同路径",
  "difficulty": "MEDIUM"
}
```

题面正文不写入 `source_detail_json`。

### 4.3 复习卡生成

LLM prompt 可以继续包含题目 slug、标题、难度、掌握状态、上次扣分点等上下文，但不要求模型输出题面字段。返回值只用于复习提示与复述支架。

## 5. 前端实现

### 5.1 开始复习页

- 默认展示标题、卡片档位、复习上下文、提示列表、输入区。
- 标题附近提供"查看题面"折叠入口。
- 折叠入口首次打开时调用 `getReviewProblemStatement(noteId)`。
- 返回的 Markdown 缓存在 `Map<noteId, string>` 中，同一题重复打开不重复请求。
- 题面加载失败时展示"未找到题目原文"，不阻塞复述提交。

### 5.2 复习中心详情

- 眼睛图标仍用于进入复习卡详情。
- 详情弹窗内提供"查看题面"折叠入口。
- 用户展开后再调用同一个题面接口。

### 5.3 前端类型

`ReviewCard` 前端类型不声明题面字段。后端若临时返回额外字段，前端忽略。

## 6. 测试计划

### 6.1 后端

- `MistakeNoteServiceTest`：catalog 命中时只断言 `titleCn / difficulty` 写入；catalog 失败不阻断入库。
- `ReviewCardServiceTest`：生成卡片不依赖题面字段；LLM schema 不包含题面字段。
- `MistakeNoteControllerIT`：`GET /api/mistake-notes/{id}/problem-statement` 覆盖成功、他人 note、未登录、题目不存在。
- pass→fail lapse：先 ingest passed=true，再 ingest passed=false，断言 outcome 为 `LAPSED`。

### 6.2 前端

- `ReviewSessionPage.test.tsx`：首屏不渲染题面；点击"查看题面"后调用 `getReviewProblemStatement` 并渲染 Markdown。
- `MistakeNotebookPage.test.tsx`：眼睛图标打开详情；详情内点击"查看题面"后调用题面接口。
- 断言题面接口失败时页面仍可提交复述。

## 7. 回滚

- 若按需题面渲染出问题：隐藏"查看题面"入口，复习卡主体不受影响。
- 若题库查询不可用：接口返回题目不存在或服务不可用，错题列表与复习卡仍可使用。
- 无需数据库迁移回滚。
