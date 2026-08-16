# 复习中心 Anki 对齐与 FSRS 分层调度设计

## 1. 背景与目标

预发布环境中，一张刚进入复习的题目展示如下间隔：

| 评级 | 当前结果 |
|---|---:|
| 重来（Again） | 1 分钟 |
| 困难（Hard） | 5 分 30 秒 |
| 良好（Good） | 10 分钟 |
| 简单（Easy） | 约 19 天 |

`19 天`不是此前“通过后失败”状态转换缺陷导致的异常大间隔；它是当前 FSRS 库对新卡 `Easy` 的正常预测结果。但它不符合复习中心首轮复习应当提供及时校验的产品体验，也偏离了项目原有的首间隔设计。

本设计的目标是：

1. 新题和学习中的题目采用 Anki 默认学习步骤的交互语义。
2. 题目毕业后仍使用 FSRS 计算长期复习间隔，保留个性化遗忘曲线能力。
3. 自动 Code Review 入队的首间隔恢复为显式业务策略，不把一次代码评分直接等同于用户点了 `Easy`。
4. 预览和实际提交始终使用同一套调度结果，避免按钮文案与落库结果不一致。

本设计不把所有复习都改回 SM-2，也不通过调整全局目标记忆率或最大间隔来压缩首轮间隔。

## 2. 调研结论

Anki 的默认牌组配置为：学习步骤 `1m 10m`、毕业 Good 间隔 `1d`、Easy 间隔 `4d`、重学步骤 `10m`。

在第一个学习步骤：

- `Again` 回到第一个步骤，间隔为 `1m`。
- `Hard` 是前两个步骤的均值，即 `(1m + 10m) / 2 = 5m30s`。Anki 手册和 UI 通常将其展示为 `6m`。
- `Good` 前进到下一学习步骤，间隔为 `10m`。
- `Easy` 无论当前处于哪个学习步骤都会直接毕业，使用 `4d`。

在第二个学习步骤：

- `Again` 回到 `1m`。
- `Hard` 重复当前步骤，间隔为 `10m`。
- `Good` 毕业，使用 `1d`。
- `Easy` 直接毕业，使用 `4d`。

需要区分两种 Anki 策略：传统调度使用上述固定毕业间隔；启用 FSRS 后，Anki 会隐藏固定的 Graduating/Easy interval 配置，并让毕业后的 Good/Easy 使用 FSRS 预测。因此“严格复刻 Anki FSRS”并不保证 Easy 是 4 天。

本项目当前通过 [FsrsReviewSchedulerService](../backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/review/schedule/FsrsReviewSchedulerService.java) 将 `1m/10m` 步骤直接交给 `io.github.open-spaced-repetition:fsrs`。该库没有 Graduating interval 或 Easy interval 参数。其默认 FSRS 参数把新卡 Easy 的初始稳定度设为约 `16.1507` 天，叠加 interval fuzz 后，预览可能显示约 `19 天`。

## 3. 产品决策

采用“Anki 式短期学习 + FSRS 长期复习”的分层策略。

这不是严格的 Anki FSRS 模式，而是为了满足算法题学习的首轮反馈需求所作的明确产品选择：用户刚完成或刚错过一道题时，需要在当天和接下来几天内验证回忆，而不是因为一次 Easy 直接离开近三周。

### 3.1 学习阶段固定间隔

默认配置如下。后续如需做用户自定义，应保留同一语义，不允许直接暴露 FSRS 库内部参数替代这些字段。

| 卡片阶段 | 重来 | 困难 | 良好 | 简单 |
|---|---:|---:|---:|---:|
| 第 1 学习步骤 | 1 分钟 | 5 分 30 秒，展示为 6 分钟 | 10 分钟 | 4 天，直接毕业 |
| 第 2 学习步骤 | 1 分钟 | 10 分钟 | 1 天，毕业 | 4 天，直接毕业 |
| 已毕业的复习卡 | FSRS 重学语义 | FSRS | FSRS | FSRS |

日级间隔应使用用户的复习日边界，而不是简单的 `now + 24h`。本期若尚未保存用户复习日边界，可先使用用户时区的次日零点；不能继续把日级间隔伪装成分钟级学习步骤。

`5m30s` 是实际到期时间，前端只在展示层向上取整为 `6 分钟`。后端 API、审计记录和测试应继续使用精确时长。

### 3.2 长期阶段交由 FSRS

卡片在第二学习步骤点击 Good，或任一学习步骤点击 Easy 后，状态转换为 `REVIEW`。此后的 `Again/Hard/Good/Easy`，以及成熟卡进入 `RELEARNING` 后的处理，继续由 FSRS 决定。

不应修改以下全局参数来解决首卡 Easy 过长：

- `desiredRetention`：它影响所有成熟卡的复习量和间隔。
- `maximumIntervalDays`：它是长时间成熟卡的上限，不是首轮学习控制器。
- FSRS 默认权重：没有足够的个人复习历史时，手工改权重既不能稳定产生 4 天，也会破坏后续预测。

## 4. 调度实现边界

### 4.1 单一调度入口

保留 `FsrsReviewSchedulerService` 作为 `preview()` 和 `apply()` 的唯一入口，避免前端或 Controller 另行计算按钮间隔。其内部按卡片状态路由：

1. `LEARNING`：先应用 `AnkiLearningPolicy`，得到学习步骤或固定毕业间隔。
2. `REVIEW`、`RELEARNING`：调用现有 FSRS 库。
3. `preview()` 只调用该入口且不持久化；`apply()` 调用同一入口并持久化返回的 `SchedulingState`、`dueAt` 和 FSRS memory state。

`SchedulingState.fsrsState` 与 `fsrsStep` 已足以记录两步学习状态；本期不为这一目标引入平行的卡片状态机或第二张调度表。

### 4.2 毕业时的 FSRS memory state

不得采用“先让 FSRS 生成 Easy 的 19 天状态，再只把 `dueAt` 改为 4 天”的做法。这样卡片会保存约 16 天的稳定度，却在第 4 天出现；下一次 FSRS 会认为用户远早于预计时间复习，后续间隔会失真。

毕业转换应执行以下步骤：

1. 调用 FSRS 获取此次评分对应的下一张卡片，用其计算后的 difficulty 和 `lastReview` 作为基础。
2. 由 `AnkiLearningPolicy` 确定目标首间隔：最终 Good 为 `1d`，Easy 为 `4d`。
3. 根据当前 `desiredRetention` 反解使 FSRS `nextInterval(stability)` 等于目标天数的 stability，并将该 calibrated stability 与上述 difficulty 一起保存。
4. 使用用户复习日边界生成 `dueAt`，状态写为 `REVIEW`、步骤清空。

FSRS 的遗忘曲线可写为 `R(t,S) = (1 + FACTOR * t / S)^DECAY`。目标保留率为 `r`、目标间隔为 `t` 时，校准值为：

```text
S = FACTOR * t / (r ^ (1 / DECAY) - 1)
```

在默认 `r = 0.90` 下，目标间隔天数恰好近似等于稳定度天数。实现必须通过单元测试将该计算与当前 FSRS 库的 `nextInterval` 行为交叉校验；不得假定所有版本的 FSRS 常数恒定不变。

### 4.3 重学不做固定首间隔覆盖

成熟卡点击 Again 后，仍使用现有 `10m` 重学步骤和 FSRS memory state。这样可以保留成熟卡的历史记忆信息，避免一次遗忘将其退化成全新的题目。重学单步的 Hard 语义为 `15m`，Good 完成重学后由 FSRS 计算后续长期时间。

## 5. Code Review 入队策略

Code Review 的自动评分是“入队置信度信号”，不是一次真实的自评 Easy。因此 [ReviewSeedPolicy](../backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/review/schedule/ReviewSeedPolicy.java) 不再通过空学习步骤调用 FSRS 并传入 `EASY/HARD/GOOD` 建卡。

首间隔恢复为显式配置：

| 自动入队结果 | 首间隔 |
|---|---:|
| 未通过 / 用户主动标记 | 立即到期，进入第 1 学习步骤 |
| 低置信通过 | 1 天 |
| 普通通过 | 3 天 |
| 高分通过且题目非 HARD | 4 天 |

上述 `1d/3d/4d` 通过同一个 FSRS bootstrap helper 写入校准后的 `REVIEW` state，不能留下空的 stability/difficulty。现有设计文档已有这一产品规则，见 [复习队列覆盖全部已练题设计](review-queue-cover-all-practiced-design.md)。

建议增加并统一管理以下配置，不在用户偏好 API 中开放：

```yaml
algo-mentor:
  review:
    scheduler:
      graduating-interval-days: 1
      easy-interval-days: 4
    seed:
      low-confidence-first-interval-days: 1
      passed-first-interval-days: 3
      passed-high-score-interval-days: 4
```

学习步骤 `1m/10m` 与重学步骤 `10m` 继续作为调度器默认配置；如未来允许修改，应成组校验，保证 Easy interval 不小于 Graduating interval。

## 6. 存量与发布

此前“已通过卡片重新失败而保留 Easy stability”造成的超大间隔修复，属于独立的数据正确性补丁，继续由对应 Flyway 迁移处理。

本设计的上线规则如下：

1. 所有新评分立即使用新策略。
2. 尚未有用户复习尝试的 `LEARNING` 卡片在下次打开时按新学习步骤预览，不需要迁移。
3. 不批量重排已有、已经产生真实复习历史的 `REVIEW/RELEARNING` 卡片，避免破坏其 FSRS 历史。
4. 对“自动 Code Review seed 且从未有真实复习尝试”的历史 `REVIEW_PASSED` 卡片，单独确认可识别条件后再提供一次性迁移；无法可靠区分时宁可不迁移，也不能重排成熟卡。
5. 预发布先观察 7 天：首轮 Easy 间隔分布、首次到期后的 Again 比例、每日队列长度和成熟卡间隔分布。

## 7. 改动清单

- `ReviewSchedulerProperties`：增加毕业 Good、Easy 和 Code Review seed 的显式首间隔配置。
- `AnkiLearningPolicy`：新增纯领域策略，负责两步学习状态、精确分钟间隔和固定毕业目标。
- `FsrsReviewSchedulerService`：保留对外 API，内部组合学习策略、FSRS 调用和 calibrated bootstrap。
- `ReviewSeedPolicy`：改为基于业务首间隔创建校准后的 FSRS review state，不再使用空步骤 + FSRS rating 直接 seed。
- `ReviewQueueService`：保持调用 `schedulerService.preview()`；新增日边界依赖后将用户时区传入调度入口。
- 前端时间格式化：学习阶段 Hard 的 330 秒展示为“6 分钟”，其余展示不得改变实际后端到期时间。
- 配置文档与部署环境变量：新增配置均提供默认值，保证旧环境不设变量时采用本文默认策略。

## 8. 测试与验收

### 8.1 单元测试矩阵

禁用 fuzz、固定时钟和固定用户时区，至少覆盖：

| 前置状态 | 评分 | 期望 |
|---|---|---|
| 第 1 学习步骤 | Again | 1 分钟，仍为第 1 步 |
| 第 1 学习步骤 | Hard | 330 秒，仍为第 1 步 |
| 第 1 学习步骤 | Good | 10 分钟，进入第 2 步 |
| 第 1 学习步骤 | Easy | 4 天，进入 REVIEW，稳定度已校准 |
| 第 2 学习步骤 | Again | 1 分钟，回到第 1 步 |
| 第 2 学习步骤 | Hard | 10 分钟，留在第 2 步 |
| 第 2 学习步骤 | Good | 1 天，进入 REVIEW，稳定度已校准 |
| 成熟 REVIEW | Again | 进入 10 分钟 RELEARNING，保留 FSRS history |
| Code Review 高分通过 | seed | 4 天，而非约 19 天 |

还必须断言：

- 同一输入下 `preview()` 与 `apply()` 返回的 `dueAt`、state、stability 和 difficulty 完全一致。
- calibrated stability 代回当前 FSRS 算法后得到配置目标天数。
- 目标保留率不为 `0.90` 时仍能生成正确的首间隔。
- 已修复的 passed-to-failed 路径不会重新复用 Easy stability。
- 用户时区跨日、夏令时边界和重复提交幂等性均符合预期。

### 8.2 完成标准

1. 新的失败题打开复习页时，按钮显示 `1分 / 6分 / 10分 / 4天`。
2. 第一次 Good 后，第二步显示 `1分 / 10分 / 1天 / 4天`。
3. 新卡或刚进入学习的卡片不再出现 19 天的 Easy 预览。
4. 已毕业的成熟卡仍可能出现超过 19 天的间隔，这是 FSRS 的正常结果，不应被首轮策略强行截断。
5. 高分 Code Review 自动入队的首间隔为 4 天，符合既有产品设计。

## 9. 参考资料

- [Anki Deck Options: Learning Steps, Graduating Interval, Easy Interval](https://docs.ankiweb.net/deck-options.html)
- [Anki LearningSteps 源码](https://github.com/ankitects/anki/blob/main/rslib/src/scheduler/states/steps.rs)
- [Anki LearnState 源码](https://github.com/ankitects/anki/blob/main/rslib/src/scheduler/states/learning.rs)
- [项目既有首间隔设计](review-queue-cover-all-practiced-design.md)
