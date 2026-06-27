# 主页能力雷达图设计

## 背景

用户希望在登录后的默认主页看到一个类似 JOJO 替身能力分析的能力图，用题目 tag 作为能力方向，并结合练习代码 Review 分数生成用户能力画像。

当前题库数据已经在 `problem.tag_values` 中保存 tag。当前本地题库共 1828 题、38 个去重 tag，其中题数不少于 20 的常见 tag 有 23 个。代码 Review 分数已经保存在 `practice_code_review.total_score`，范围为 0 到 10。

## 目标

- 登录用户默认进入主页。
- 主页右侧展示用户能力雷达图。
- 雷达图展示题库中题数不少于 20 的常见 tag，当前为 23 个。
- 每个方向满分 10 分，能力分保留一位小数。
- 雷达图刻度展示 `2 / 4 / 6 / 8 / 10`。
- 能力分采用保守诊断算法，未练习或没有 Review 的 tag 显示 `0.0`。
- 第一版只展示雷达图，不提供点击、跳转、悬浮说明、低分摘要或 tag 详情。

## 非目标

- 不实现 tag 下提交记录详情页。
- 不实现 Review 历史版本展开。
- 不实现雷达图点击跳转。
- 不实现 hover tooltip。
- 不展示低分摘要。
- 不新增能力快照表或后台定时任务。
- 不把低频专项 tag 放进第一版雷达图。

## 主页体验

登录成功后，应用默认路由调整为主页 `/`。主页采用工作台布局，右侧为能力雷达图模块，左侧和中间预留后续模块位置，例如最近练习、学习计划、待复盘和推荐任务。

能力雷达图模块只负责展示当前用户的常见 tag 能力画像：

- 标题：能力雷达图。
- 副信息：常见 tag、保守诊断、满分 10 分。
- 雷达图轴：题数不少于 20 的 tag，按题库题数从高到低排序。
- 轴标签：使用中文标签，英文 tag 只作为后端契约值，不直接作为主要展示文案。
- 分数显示：图形点位或临近文本使用一位小数。
- 空数据：用户没有任何 Review 时，雷达图仍展示 23 个 tag，所有能力分为 `0.0`。

主页其他区域第一版可以使用结构化空态占位，但不做营销式大段介绍。

## 评分算法

第一版采用实时聚合，不保存快照。

每个 tag 的统计规则：

1. 只统计当前登录用户的 `practice_code_review`。
2. 同一道题只取最新一次 Review，按 `created_at DESC, id DESC` 判定。
3. 一道题如果有多个 tag，则该题最新 Review 分数会贡献给每个对应 tag。
4. 没有 Review 的 tag，能力分为 `0.0`。

保守诊断公式：

```text
rawAverage = 该 tag 下最新 Review total_score 的平均值
confidence = reviewedProblemCount / (reviewedProblemCount + 4)
abilityScore = rawAverage * confidence
```

示例：

- 0 道：`0.0`
- 1 道满分：`10.0 * 1 / 5 = 2.0`
- 3 道均分 8：`8.0 * 3 / 7 = 3.4`
- 8 道均分 8：`8.0 * 8 / 12 = 5.3`

`abilityScore` 和 `rawAverageScore` 返回时保留一位小数。`confidence` 可作为后端内部计算值，第一版不需要前端展示。

## 后端 API

新增只读 API：

```text
GET /api/abilities/profile
```

认证要求：

- 必须登录。
- 只返回当前登录用户的数据。
- 不接受前端传入 `userId`。

响应示例：

```json
{
  "tags": [
    {
      "tag": "dynamic-programming",
      "label": "动态规划",
      "problemCount": 240,
      "reviewedProblemCount": 3,
      "rawAverageScore": 8.0,
      "abilityScore": 3.4
    }
  ],
  "scope": {
    "minProblemCount": 20,
    "scorePrecision": 1,
    "latestReviewOnly": true,
    "conservativeWeight": 4
  }
}
```

字段说明：

- `tag`：题库 tag 契约值。
- `label`：按当前 locale 返回中文或英文标签，默认中文。
- `problemCount`：题库中挂载该 tag 的题目数。
- `reviewedProblemCount`：当前用户在该 tag 下有最新 Review 的去重题目数。
- `rawAverageScore`：同 tag 下最新 Review 的原始均分；无 Review 时为 `0.0`。
- `abilityScore`：保守加权后的能力分；无 Review 时为 `0.0`。
- `scope.minProblemCount`：第一版主视图筛选阈值。
- `scope.latestReviewOnly`：表示同题只取最新 Review。

## 后端实现边界

新增 `ability` 相关包，避免把聚合逻辑塞进已有 practice 或 problem controller：

- `controller/ability`：能力画像 API controller。
- `ability/model`：响应 DTO。
- `ability/service`：能力画像聚合服务。
- `ability/repository` 或 mapper：MyBatis 查询实现。

SQL 聚合建议：

1. 从 `problem` 展开 `tag_values` 和对应 label。
2. 先按 tag 统计题库题数，筛出 `COUNT(*) >= 20` 的 tag。
3. 从 `practice_code_review` 中按当前用户和 `problem_slug` 取最新 Review。
4. 将最新 Review 与 `problem` 的 tag 展开结果 JOIN。
5. 按 tag 计算 `reviewedProblemCount` 和 `rawAverageScore`。
6. 应用保守公式生成 `abilityScore`。
7. 按 `problemCount DESC, tag ASC` 返回。

建议把常量抽出：

- API 路径，例如 `/api/abilities/profile`。
- `MIN_PROBLEM_COUNT = 20`。
- `CONSERVATIVE_WEIGHT = 4`。
- 分数精度 `SCORE_SCALE = 1`。

## 前端实现边界

新增能力画像相关类型和 API client：

- `AbilityProfileResponse`
- `AbilityTagScore`
- `AbilityProfileScope`
- `getAbilityProfile(...)`

主页改造：

- 登录后默认路由改为 `/`。
- 导航中可以保留现有功能入口，但主页应成为认证态默认视图。
- 区分公开首页和认证态主页；未登录公开首页可以保持现有展示，登录后主页使用工作台布局。
- `HomeDashboard` 可拆分出认证态主页组件，右侧渲染能力雷达图。
- 能力雷达图封装为独立组件，例如 `AbilityRadarChart`。
- 图表可以优先使用轻量 SVG 实现，避免为 V1 引入大型图表库。

雷达图展示约束：

- 不使用 hover tooltip。
- 不绑定点击事件。
- 不展示低分摘要。
- 不展示 tag 详情。
- 标签文本必须在桌面和移动宽度下不互相遮挡到不可读；移动端可让雷达图模块降到页面下方或横向压缩为单列布局。

## 数据流

```text
登录用户打开 /
  -> HomeDashboard 挂载
  -> 调用 GET /api/abilities/profile
  -> 后端按当前用户实时聚合 Review 和题库 tag
  -> 前端渲染主页右侧能力雷达图
```

加载状态：

- 请求中展示雷达图骨架或加载态。
- 请求失败展示紧凑错误态和重试按钮。
- 无 Review 返回正常雷达图，所有分数为 `0.0`。

## 测试策略

后端：

- 单元测试评分公式，覆盖 0、1、3、8 道题等样本数。
- MyBatis mapper 测试：同题多 Review 只取最新版本。
- MyBatis mapper 测试：同一题多个 tag 时分别贡献到多个 tag。
- API controller 测试：未登录返回 401。
- API controller 测试：响应不接受前端 userId，始终使用当前登录用户。

前端：

- API client 测试 `GET /api/abilities/profile`。
- 主页认证态默认路由测试。
- 能力雷达图渲染测试：23 个 tag、分数一位小数、无 tooltip、无点击行为。
- 加载、失败、空 Review 三种状态测试。

## 后续演进

- 增加 tag 详情页或主页详情抽屉。
- 支持点击 tag 查看已做题目和 Review 历史。
- 增加低分摘要、推荐补练题目和近期变化趋势。
- 在 Review 数量变大后，将实时聚合迁移为写入时快照或后台重算快照。
