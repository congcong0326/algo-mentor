# 复习中心代码 Review 时间线与反向索引研发设计

## 1. 背景

当前复习中心 `/mistakes` 以 `problem_review_card` 为题目级聚合记录，展示题目、来源、到期时间、最近评级和遗忘次数，并通过眼睛按钮打开题面、个人笔记与 FSRS 复习历史。

练习工作台已经支持正式代码 Review：

- 一次正式 Review 对应 `practice_code_review` 中的一条不可变记录；
- 同一练习会话内通过 `version_no` 形成多版本历史；
- Review 详情页支持使用 `reviewId` 定位并选中具体版本；
- `ReviewCardService` 在正式 Review 保存后按用户和题目创建或更新复习卡；
- 复习卡的 `source_detail_json` 只保存最新 Review 的轻量摘要，不能承载最近 10 次历史，也缺少稳定构造详情路径所需的完整归属信息。

因此，复习中心虽然能够汇总产生过代码 Review 的题目，但缺少从题目卡反向定位到每一次代码提交及其完整 Review 的能力。

## 2. 目标

在复习中心每个题目卡片上增加一条类似状态时间线的代码 Review 索引，使用户能够：

1. 快速扫描同一道题最近几次代码 Review 的分数变化；
2. 悬浮或键盘聚焦某个点时查看简洁摘要；
3. 点击某个彩色点进入其真实所属的代码 Review 版本；
4. 从 Review 页面返回复习中心，并恢复原筛选条件和题目位置；
5. 清楚识别由手动标记产生、尚无代码 Review 的复习卡。

## 3. 非目标

本次不包含：

- 修改代码 Review 的评分模型或 6 分完成门槛；
- 展示完整五维评分、代码快照或全部改进建议；
- 在复习中心编辑、删除或重新执行 Review；
- 展示超过最近 10 条的折叠入口、总数或分页历史；
- 将 Review 版本号改造成跨会话全局序号；
- 移除现有复习卡详情或 FSRS 复习能力；
- 新增独立的全局 Review 详情路由。

## 4. 已确认产品决策

### 4.1 点与 Review 的对应关系

- 一个彩色点严格对应一条正式 `practice_code_review` 记录；
- 模型未调用 Review 工具、用户拒绝权限、确认超时或 Review 失败时没有正式记录，因此不生成点；
- 每张题目卡只展示最近 10 条正式 Review，多余记录不展示，也不增加省略号或数量提示；
- 点列按真实创建时间从左到右升序排列，最右侧始终是最新 Review；
- 少于 10 条时整体右对齐，使不同卡片的最新状态保持在相同视觉位置。

### 4.2 跨计划、跨会话聚合

复习卡按 `user_id + problem_slug` 全局唯一，因此点列也按用户和题目聚合：

- 查询该用户在该题目下、所有学习计划和练习会话中的最近 10 条正式 Review；
- 每个点携带其自身的 `planId`、`phaseIndex`、`practiceSessionId` 和 `reviewId`；
- 点击时进入该 Review 实际所属的学习计划、阶段和题目提交历史页；
- 不使用复习卡当前来源或最新会话推断历史 Review 的归属。

`version_no` 只在单个 `practice_session_id` 内递增。跨会话聚合后可能出现多个 `V1`，因此：

- 排序以 `created_at DESC, id DESC` 为准，不以 `version_no` 判断全局新旧；
- tooltip 使用“V{versionNo}”表示原会话版本，不使用“第 N 次 Review”这类全局次数表述；
- `reviewId` 才是定位具体 Review 的稳定标识。

### 4.3 分数颜色

前端根据每条 Review 的 `totalScore` 计算展示色，不由后端返回颜色名称：

| 分数范围 | 颜色语义 | CSS 语义变量 |
| --- | --- | --- |
| `score >= 8` | 表现良好 | success/green |
| `6 <= score < 8` | 已通过但仍有明显改进空间 | warning/yellow |
| `score < 6` | 未通过 | danger/red |

边界值固定为：

- `8.0` 为绿色；
- `6.0` 为黄色；
- 小于 `6.0` 为红色。

评分带属于复习中心展示规则，应在前端集中常量或纯函数中维护并单独测试，不散落在组件条件表达式中。

### 4.4 灰色占位点

`USER_MARKED` 复习卡可能尚未产生任何正式代码 Review。该场景展示一个灰色占位点：

- 灰色点不代表 Review 记录；
- 不可点击，不执行导航；
- hover 和键盘 focus 提示：`该题由手动标记加入复习中心，尚未产生代码 Review`；
- 一旦该题产生第一条正式 Review，灰色点消失，替换为真实彩色点列。

灰色点不能作为 Review 时间线加载失败的通用降级，否则会把系统异常误导成手动标记状态。若 Review 来源卡与数据库历史不一致，应记录异常指标和日志，并使用“Review 记录暂不可用”的中性提示，不使用手动标记文案。

### 4.5 悬浮摘要

彩色点的 tooltip 保持简洁，建议结构为：

```text
V4 · 7.5 / 10
Java · 2026-08-02 21:30
主要反馈：边界条件处理不完整
```

主要反馈取值顺序：

1. 第一条非空扣分原因；
2. 第一条非空改进建议；
3. 固定兜底文案“未发现明显问题”。

tooltip 不展示五维评分、完整 Review、代码或多条建议。主要反馈响应值应规范化为单行并限制长度，建议最大 160 个字符；前端再通过固定宽度和最多两行截断保证布局稳定。

灰色点 tooltip 为：

```text
尚无代码 Review
该题由手动标记加入复习中心
```

### 4.6 点击与返回

- 彩色点在当前标签页打开 Review 历史页；
- URL 必须携带被点击的 `reviewId`，页面加载后自动选中该版本；
- Review 页返回按钮显示“返回复习中心”；
- 返回后恢复搜索词、“仅看错题”筛选，并将原题目卡滚动回可视区域；
- 灰色点不响应点击；
- 卡片空白区域不设置跳转，避免与详情、归档和点列操作冲突。

### 4.7 与现有卡片操作的分工

- 彩色点：打开对应代码 Review；
- 灰色点：说明尚无代码 Review 的原因；
- 眼睛按钮：继续打开现有复习卡详情，展示题面、个人笔记和 FSRS 复习历史；
- 归档按钮：继续执行复习卡归档或恢复；
- 本次不改变眼睛按钮、归档按钮及现有详情弹窗的业务语义。

## 5. 页面布局与交互

### 5.1 桌面端

题目卡调整为三列布局：

```text
┌─────────────────────────────────────────────────────────────────────┐
│ 题目标题与复习元信息        ● ● ● ● ● ● ● ● ● ●      查看  归档 │
└─────────────────────────────────────────────────────────────────────┘
```

- 左列继续承载标题和现有元信息；
- 中列为固定宽度 Review 时间线，放在现有操作按钮之前；
- 右列保留眼睛和归档按钮；
- 时间线固定容纳 10 个点且禁止换行；
- 视觉点建议直径 8px，交互命中区域建议不小于 18px；
- 点列整体右对齐，最新点位于最右侧；
- tooltip 使用浮层渲染，不能被卡片或元信息的 `overflow: hidden` 裁切。

建议 CSS grid 结构：

```text
minmax(0, 1fr) auto auto
```

时间线应有稳定宽度，避免不同 Review 数量导致右侧按钮横向移动。

### 5.2 移动端

窄屏下调整为两行网格：

```text
┌──────────────────────────────┐
│ 题目标题与元信息       查看 归档 │
│              ● ● ● ● ● ● ● │
└──────────────────────────────┘
```

- 第一行保留题目信息和操作按钮；
- 时间线进入第二行并右对齐；
- 10 个点保持单行，不横向滚动、不折行；
- tooltip 在触屏设备上通过点击点进入详情，不依赖 hover 承载必要操作；
- 灰色点可通过聚焦或轻触显示说明，但不得触发路由跳转。

### 5.3 键盘与可访问性

彩色点使用真实 `button`：

- `aria-label` 包含版本、分数、语言、时间和“查看 Review”；
- 支持 `Tab` 聚焦，`Enter` 或空格打开；
- focus 时展示与 hover 相同的 tooltip；
- 有清晰 focus ring，不能只依赖颜色表达当前焦点。

灰色点不是导航按钮，但仍需要可获取说明：

- 使用可聚焦的非按钮元素或项目统一 tooltip trigger；
- `aria-label` 明确“手动标记，尚无代码 Review”；
- 不使用 disabled button，因为 disabled 元素通常无法获得键盘焦点和 tooltip。

颜色不是唯一信息来源。每个点的可访问名称和 tooltip 都必须包含数值分数；测试应覆盖三种颜色边界和键盘操作。

## 6. 后端查询设计

### 6.1 设计原则

复习中心列表默认最多加载 80 张卡。如果逐卡查询最近 Review，会产生明显的 N+1 查询，因此必须采用两段式批量查询：

1. 按现有筛选条件查询当前页复习卡；
2. 收集卡片 `problemSlug`，一次批量查询这些题目的最近 10 条 Review；
3. 在应用层按 `problemSlug` 分组并组装列表响应。

不应从 `problem_review_card.source_detail_json` 还原时间线。该字段只保存最新 Review 摘要，并且历史更新会覆盖旧值。

### 6.2 应用层模型

建议新增只读模型：

```java
public record PracticeCodeReviewIndexEntry(
    long reviewId,
    long planId,
    int phaseIndex,
    String problemSlug,
    long practiceSessionId,
    int versionNo,
    String language,
    String contentLocale,
    BigDecimal totalScore,
    boolean passed,
    String primaryFeedback,
    Instant createdAt
) {}
```

建议新增窄查询端口，而不是继续扩大承担写入和会话详情的 `PracticeCodeReviewRepository`：

```java
public interface PracticeCodeReviewIndexRepository {
  List<PracticeCodeReviewIndexEntry> findRecentByProblemSlugs(
      long userId,
      List<String> problemSlugs,
      int perProblemLimit
  );
}
```

复习域新增题目卡列表组装模型，例如：

```java
public record ReviewCardOverview(
    ProblemReviewCard card,
    List<PracticeCodeReviewIndexEntry> recentCodeReviews
) {}
```

由新的列表查询服务或现有服务的明确查询方法负责：

- 调用 `ReviewCardService.list(...)` 获取卡片；
- 去重并收集题目 slug；
- 使用固定上限 10 批量查询 Review；
- 按卡片顺序组装 `ReviewCardOverview`；
- 不改变 `ProblemReviewCard` 领域对象，也不把跨域历史塞入调度实体。

最近 Review 上限应抽象为复习模块常量，例如 `RECENT_CODE_REVIEW_INDEX_LIMIT = 10`，避免 Controller、Service、SQL 和前端各自出现无说明的字面量。

### 6.3 SQL 查询

PostgreSQL 查询使用窗口函数按题目截取最近 10 条：

```sql
WITH ranked_review AS (
  SELECT
    review.id,
    review.plan_id,
    review.phase_index,
    review.problem_slug,
    review.practice_session_id,
    review.version_no,
    review.language,
    review.content_locale,
    review.total_score,
    review.passed,
    COALESCE(
      NULLIF(BTRIM(review.deduction_reasons_json ->> 0), ''),
      NULLIF(BTRIM(review.improvement_suggestions_json ->> 0), '')
    ) AS primary_feedback,
    review.created_at,
    ROW_NUMBER() OVER (
      PARTITION BY review.problem_slug
      ORDER BY review.created_at DESC, review.id DESC
    ) AS row_number
  FROM practice_code_review review
  WHERE review.user_id = #{userId}
    AND review.problem_slug IN (...)
)
SELECT ...
FROM ranked_review
WHERE row_number <= #{perProblemLimit}
ORDER BY problem_slug, created_at ASC, id ASC;
```

关键约束：

- 必须带 `user_id`，保证用户数据隔离；
- 先按倒序选最近 10 条，再按正序返回，直接满足左旧右新的展示顺序；
- 同一时间戳使用 `id` 作为确定性排序；
- 空题目列表时直接返回空集合，不生成非法 `IN ()`；
- `perProblemLimit` 在应用层固定并限制为 10，不接受前端自由传入；
- `primary_feedback` 仅取 JSONB 数组第一项，不进行字符串拼接解析。

### 6.4 数据库索引

现有索引以 `plan_id`、`phase_index` 位于 `problem_slug` 之前，不适合按用户和题目跨计划检索最近记录。需要新增 Flyway 迁移：

```sql
CREATE INDEX idx_practice_code_review_user_problem_recent
  ON practice_code_review (user_id, problem_slug, created_at DESC, id DESC);
```

本次不修改表字段、不回填数据。索引是可保留的向前兼容变更，即使回滚前端和查询代码也无需删除。

## 7. API 契约

### 7.1 列表响应

`GET /api/review-cards` 当前返回扁平 `ReviewCardResponse` 列表。为避免归档接口和详情接口被迫返回时间线，建议列表接口改为专用响应：

```json
{
  "success": true,
  "data": [
    {
      "card": {
        "id": 88,
        "problemSlug": "two-sum",
        "problemTitle": "两数之和",
        "source": "REVIEW_PASSED",
        "dueAt": "2026-08-05T00:00:00Z",
        "archived": false
      },
      "recentCodeReviews": [
        {
          "reviewId": 301,
          "planId": 20,
          "phaseIndex": 2,
          "problemSlug": "two-sum",
          "practiceSessionId": 90,
          "versionNo": 3,
          "language": "java",
          "contentLocale": "zh-CN",
          "totalScore": 7.5,
          "passed": true,
          "primaryFeedback": "边界条件处理不完整",
          "createdAt": "2026-08-02T21:30:00Z"
        }
      ]
    }
  ]
}
```

完整 `card` 字段继续复用现有 `ReviewCardResponse`。新增：

```java
public record ReviewCardOverviewResponse(
    ReviewCardResponse card,
    List<PracticeCodeReviewIndexEntryResponse> recentCodeReviews
) {}
```

归档接口仍返回 `ReviewCardResponse`，前端更新卡片本体时保留当前 `recentCodeReviews`，避免一次归档操作清空点列。

### 7.2 前端类型

新增语义类型：

```ts
export interface ReviewCardCodeReviewIndexEntry {
  reviewId: number;
  planId: number;
  phaseIndex: number;
  problemSlug: string;
  practiceSessionId: number;
  versionNo: number;
  language: string;
  contentLocale: 'zh-CN' | 'en-US';
  totalScore: number;
  passed: boolean;
  primaryFeedback?: string | null;
  createdAt: string;
}

export interface ReviewCardOverview {
  card: ReviewCard;
  recentCodeReviews: ReviewCardCodeReviewIndexEntry[];
}
```

前端不直接读取 `sourceDetail.latestReviewId` 拼接路径，所有点均以 `recentCodeReviews` 为事实来源。

## 8. 路由与返回状态

### 8.1 Review 深链

复用现有提交历史路径：

```text
/learning-plans/{planId}/phases/{phaseIndex}/problems/{problemSlug}/submissions
  ?review={reviewId}
  &from=review-center
  &returnTo={validatedInternalPath}
```

新增稳定来源常量：

```ts
export const REVIEW_CENTER_REVIEW_ORIGIN = 'review-center';
```

将提交历史来源类型从仅支持 `learner-profile` 扩展为：

```ts
type LearningPlanPracticeSubmissionsOrigin = 'learner-profile' | 'review-center';
```

现有学习画像来源和高亮行为保持不变。Review 中心来源只负责返回语义，不复用学习画像的 statement highlight 样式。

虽然点响应包含 `practiceSessionId`，当前页面仍可根据 `planId + phaseIndex + problemSlug` 复用唯一练习会话；`practiceSessionId` 用于契约完整性、异常核对和未来直接会话路由，不由前端猜测。

### 8.2 复习中心筛选 URL

将复习中心筛选状态同步到 URL，并在 `normalizeAuthenticatedSearch` 中白名单保留：

```text
/mistakes?q=two-sum&mistakeOnly=true&focusCard=88
```

- `q`：搜索词，trim 后限制合理长度；
- `mistakeOnly=true`：仅看错题；
- `focusCard`：返回时需要恢复到可视区域的复习卡 ID，只接受正整数。

输入搜索词时使用 `replaceState` 更新 URL，避免每次键入都增加浏览器历史记录。用户主动切换“仅看错题”也可使用 replace，以保持一次复习中心访问只有一个稳定历史项。

### 8.3 returnTo 校验

`returnTo` 不能作为任意 URL 使用，必须通过专用解析函数校验：

- 只允许站内 `/mistakes` 路径；
- 只保留 `q`、`mistakeOnly` 和 `focusCard` 白名单参数；
- 拒绝协议、host、`//` 开头路径和其他 pathname；
- 限制原始长度，建议不超过 512 字符；
- 校验失败时回退到 `APP_ROUTES.mistakes`。

点击点时根据当前复习中心筛选状态构造：

```text
returnTo=/mistakes?q=two-sum&mistakeOnly=true&focusCard=88
```

Review 页面返回按钮调用应用内导航进入已校验的 `returnTo`，而不是盲目执行浏览器 `back()`，避免用户通过外部深链进入时返回到无关页面。

### 8.4 返回后的定位

复习中心重新加载列表后：

1. 应用 URL 中的搜索词和错题筛选；
2. 等待列表请求完成；
3. 查找 `focusCard` 对应卡片；
4. 使用 `scrollIntoView({ block: 'center' })` 恢复题目位置；
5. 将焦点放回被点击点所属的时间线区域或题目卡容器；
6. 使用 `replaceState` 移除一次性的 `focusCard`，保留 `q` 和 `mistakeOnly`。

如果筛选、归档或数据变化导致目标卡片已不可见，不报错、不改变筛选，只回到列表顶部。

## 9. 前端组件拆分

建议新增独立组件，避免继续扩大 `MistakeNotebookPage`：

```text
frontend/src/mistakes/
  ReviewCardTimeline.tsx
  reviewCardTimeline.ts
```

职责建议：

- `ReviewCardTimeline.tsx`：渲染彩色点、灰色点、tooltip、键盘行为；
- `reviewCardTimeline.ts`：分数带计算、主要反馈兜底、ARIA 文案数据组装；
- `MistakeNotebookPage.tsx`：加载 overview、构造深链、处理返回定位；
- `app/navigation.ts`：维护来源、复习中心查询参数和 `returnTo` 校验；
- `types/api.ts` 与 `services/api.ts`：维护新的列表响应契约。

不要让时间线组件自行发起每个 Review 的详情请求。tooltip 所需摘要必须随列表批量返回，点击后再由 Review 详情页加载全量数据。

## 10. 状态与异常处理

- 复习卡列表加载中：沿用现有列表 loading 状态，不先渲染灰色占位点；
- 列表请求失败：沿用现有错误和刷新入口；
- `recentCodeReviews` 为空且来源为 `USER_MARKED`：显示手动标记灰色点；
- `recentCodeReviews` 为空但来源为 Review：显示不可交互的中性异常提示，并记录低基数指标；
- 点击后目标 Review 不存在或已不可用：沿用 Review 页“该提交不可用”状态，返回入口仍然有效；
- `primaryFeedback` 为空：前端显示“未发现明显问题”；
- 非法分数或缺失分数属于契约异常，不默认为绿色；应显示中性状态并记录日志或前端错误监控；
- 归档复习卡不删除 Review 点列；恢复后仍展示同一批历史。

## 11. 可观测性与性能

建议记录：

- 复习中心 Review 索引批量查询耗时；
- 本次查询卡片数、返回 Review 点数，使用直方图或分布摘要，避免高基数标签；
- Review 来源卡但历史为空的异常计数；
- Review 深链目标不可用计数，可复用现有 Review 详情错误指标。

日志只记录 `cardId`、`reviewId`、`problemSlug`、数量和异常类型，不输出代码、完整反馈、用户笔记或隐私内容。

性能约束：

- 单次卡片列表最多 80 张；
- 单题最多返回 10 个点，最坏不超过 800 条轻量索引记录；
- 只允许一次卡片查询和一次 Review 批量查询；
- 不加载 `raw_code`、`normalized_code`、`review_markdown`、完整维度评分或全部反馈数组；
- 新增 `(user_id, problem_slug, created_at DESC, id DESC)` 索引支撑窗口查询。

## 12. 测试范围

### 12.1 后端单元测试

- 手动标记卡且无 Review 时返回空 `recentCodeReviews`；
- 同题跨学习计划、跨会话记录能够聚合；
- 每题只保留最近 10 条；
- 相同 `createdAt` 时按 `id` 确定顺序；
- 响应顺序为左旧右新；
- 主要反馈优先扣分原因，其次改进建议，最后为空；
- 多张卡批量组装不改变原卡片排序；
- 空卡片列表不访问或安全返回空 Review 查询。

### 12.2 Mapper 与集成测试

- 只能查询当前用户的 Review；
- 题目 slug 集合过滤正确；
- 跨计划、跨会话查询正确；
- 每题窗口上限独立生效，而不是全局只返回 10 条；
- 新索引迁移资源存在且列顺序正确；
- JSONB 第一项提取不使用字符串拆分；
- 80 个题目、每题 10 条的查询结果和排序稳定。

### 12.3 Controller 测试

- 列表响应包含 `card` 和 `recentCodeReviews`；
- 手动卡返回空数组而不是 `null`；
- 未登录和用户隔离沿用现有安全行为；
- 搜索、错题筛选、limit 和 offset 仍然生效；
- 归档接口契约不被列表专用响应影响。

### 12.4 前端测试

- `8.0` 绿色、`6.0` 黄色、`5.9` 红色；
- 最近 10 条按左旧右新渲染，少于 10 条时右对齐；
- tooltip 展示版本、分数、语言、时间和一条主要反馈；
- 无反馈时展示固定兜底；
- `USER_MARKED` 空历史显示灰色点、原因提示且不导航；
- 彩色点支持鼠标点击和键盘激活；
- 点击生成包含真实 plan、phase、slug、reviewId、来源和 returnTo 的路径；
- Review 页按 reviewId 选中目标版本；
- 返回后恢复 `q`、`mistakeOnly` 并定位 `focusCard`；
- 眼睛按钮仍打开复习卡详情，归档按钮行为不变；
- 移动端 10 个点不换行、不覆盖题目文字或操作按钮。

## 13. 实施顺序

1. 新增 Review 索引查询模型、窄端口和 PostgreSQL 实现；
2. 新增 Flyway 查询索引；
3. 新增复习卡 overview 组装服务和列表专用 API 响应；
4. 更新前端 API 类型与列表加载逻辑；
5. 新增时间线组件、分数带规则、tooltip 和响应式样式；
6. 扩展提交历史来源、`returnTo` 校验和复习中心查询参数；
7. 实现返回定位与焦点恢复；
8. 补齐后端、前端和路由测试；
9. 运行最小相关测试，再执行前端完整测试和后端相关模块测试。

## 14. 发布与回滚

- 数据库只新增索引，无数据迁移和回填；
- 后端与前端由同一项目打包，可同步发布列表契约变更；
- 发布后重点观察复习卡列表查询耗时、错误率和 Review 来源卡空历史异常；
- 回滚应用代码时新增索引可保留，不影响旧逻辑；
- 若前端时间线出现布局问题，可先隐藏时间线组件，现有眼睛按钮、归档和复习流程不受影响；
- 不删除或重写 `source_detail_json`，为旧版本回滚保留兼容数据。

## 15. 验收标准

- 产生过正式代码 Review 的题目卡展示最近 1 至 10 个彩色点；
- 同一道题跨计划、跨会话的最近 Review 能按时间正确聚合；
- 绿色、黄色、红色严格遵循 `>= 8`、`>= 6 && < 8`、`< 6`；
- 手动标记且无 Review 的卡展示单个不可点击灰色点和准确原因；
- 每个彩色点 hover/focus 展示简洁摘要；
- 点击任意彩色点能够打开并选中对应 Review；
- 返回复习中心后恢复筛选并定位原题目卡；
- 现有复习卡详情、归档、FSRS 调度和今日复习入口行为不回归；
- 列表加载不存在逐卡 Review 查询，数据库有匹配的最近记录索引；
- 桌面端和移动端均无文字、点列和操作按钮重叠。
