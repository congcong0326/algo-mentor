# 知识大纲与知识卡片用户侧接口设计

日期：2026-09-15  
状态：当前接口实施基线，待实现

## 1. 目标与范围

围绕 [首期产品设计](knowledge-outline-card-v1-design.md) 完整支持：

知识库首页 → 技术分支大纲 → 节点卡片列表 → 问题与答案 → 可选四档评价 → 八股文复习中心 → 到期复习；文章作为可选阅读入口。

本次以普通用户接口为重点。共享大纲、卡片和文章由维护者直接执行数据库导入，不建设管理员 CRUD、发布后台或导入 API。此前的 [管理员接口设计](knowledge-outline-card-v1-admin-api-design.md) 已撤回，不作为实现任务。

保留 V72 五张表，V73 seed 已删除；当前不执行初始化或导入。未来导入事务负责建立唯一虚拟 root 及共享内容，用户 GET 和应用启动均不自动创建 root。空库可以正常访问并展示空态。

本次交付接口设计，不实施 Java/API、前端或数据库导入。

## 2. 公共契约

- 新接口基础路径为 `/api/knowledge`，要求登录，不要求 ADMIN。操作用户从现有 CurrentUserIdProvider 获取，请求不接受 userId。
- 响应复用 `ApiResponse<T>`：`success/data/error/timestamp`。下文描述 data 内字段；错误使用现有错误响应工厂。
- ID 为 bigint 对应的 JSON 数值，字段 camelCase，时间为 UTC ISO 8601。slug 只辅助定位，路由使用 ID，不能把同级唯一 slug 当作全局唯一标识。
- 分页使用 `page=1&pageSize=20`，pageSize 范围 1–100。返回 `items/total/page/pageSize`；超出末页返回空 items，非法分页返回 400。
- 所有包含到期状态的响应返回 `asOf`，同一次请求的到期判断以该服务端时间为准，不接受客户端 now/dueAt。
- 所有 GET 都不创建用户状态、流水或 root，不推进调度。当前不提供手动入队、移出、暂停、掌握状态修改或共享正文写接口。
- 内容与个人状态一起返回的响应不得使用跨用户共享缓存；首期设置 `Cache-Control: private, no-store`，避免数据库直接导入后应用内容缓存不刷新。
- 前端显示 Markdown 时按现有安全渲染约定处理，不执行正文中的 HTML 脚本。

### 可见性

可见节点必须从唯一 ROOT 连通，且自身及整条祖先链均为 PUBLISHED；ROOT 本身也必须已发布。可见卡片/文章还要求自身 PUBLISHED 且所属节点可见。产品不返回虚拟 root，技术节点的 parentId 返回 null，面包屑也不包含 root。

列表、详情、计数、间隔预览、评价、复习中心必须复用同一可见性规则。对不存在、隐藏、归档或 root 资源的按 ID 请求统一返回 404；不暴露隐藏内容的标题、路径、数量或正文。空库或无可见 root 时，首页及个人复习列表返回空，汇总为零。

递归 CTE 记录已访问 ID 并拒绝带环路径；未能连通 ROOT 的内容不可见。树结构的完整性仍由导入校验负责。

### 公共响应对象

| 对象 | 字段与语义 |
| --- | --- |
| Breadcrumb | `id/title/slug`；数组从技术节点到当前节点 |
| LearningState | `enrolled/phase/dueAt/isDue/lastRating/lastReviewedAt`；无状态行为 false/null/null/false/null/null；phase 为 LEARNING、REVIEW、RELEARNING 或 null |
| NodeSummary | `id/parentId/slug/title/summary/sortOrder`；`directCardCount/directArticleCount/hasChildren`；`subtreeCardCount/subtreeEnrolledCardCount` |
| CardSummary | `id/outlineNodeId/question/sortOrder/updatedAt/learningState`，不含答案和补充正文 |
| ArticleSummary | `id/outlineNodeId/title/publishedAt/updatedAt`，不含正文 |

节点数量仅统计可见内容。direct 表示当前节点直属，subtree 包括本节点和全部可见后代；入队数量只统计当前用户已经存在状态的可见卡片，不等同于掌握数量。文章入口使用 directArticleCount，卡片入口使用 directCardCount，不把后代内容误认为直属内容。

## 3. 接口总览

以下是需要新增的 12 个用户接口。全部位于 /api/knowledge 下。

| 方法与路径 | 用户动作与作用 |
| --- | --- |
| GET /topics | 知识库首页：读取技术入口及各分支卡片数、个人已入队数 |
| GET /outline-nodes/{nodeId}/tree | 进入技术大纲：读取该分支的嵌套树、内容数量和个人进度 |
| GET /outline-nodes/{nodeId} | 进入节点详情或刷新页面：获取面包屑、直属内容数量和子节点入口 |
| GET /outline-nodes/{nodeId}/cards | 进入卡片列表：分页读取本节点的问题和个人学习状态 |
| GET /cards/{cardId} | 打开卡片：获取问题、答案、补充说明及个人状态 |
| GET /outline-nodes/{nodeId}/articles | 点击文章入口：分页读取该节点的可选文章列表 |
| GET /articles/{articleId} | 打开文章：读取完整文章及返回所属节点所需信息 |
| GET /cards/{cardId}/review-preview | 显示答案后：获得四档评价的间隔预览，不入队 |
| POST /cards/{cardId}/review-attempts | 提交四档评价：首次原子入队，后续更新同一用户状态 |
| GET /review/summary | 打开复习中心：获得已入队数、到期数和下一到期时间 |
| GET /review/cards | 复习中心列表：分页读取当前用户全部已入队或已到期卡片 |
| GET /review/next | 开始或继续复习：选择当前应复习的一张卡片 |

另复用已有 `GET/PATCH /api/me/review-preferences` 读取和修改调度偏好，不新建知识卡专用偏好接口。

## 4. 知识库与大纲

### GET /topics

无业务查询参数。data 为 `{items: NodeSummary[], asOf}`，按 sortOrder、id 升序返回 root 的全部可见直接子节点，不分页。首页只返回技术入口摘要，不传递其后代树或正文。分支卡片总数和已入队数由聚合查询计算，避免逐技术节点查询数据库。

### GET /outline-nodes/{nodeId}/tree

nodeId 可以是技术节点或普通子节点。data 为 `{node: NodeTree, breadcrumbs: Breadcrumb[], asOf}`，NodeTree 为 NodeSummary 加 `children: NodeTree[]`，叶子 children 为空数组。

只读取请求分支，按各层 sortOrder、id 排列，完整返回可见节点，不分页、不静默截断、不携带卡片正文或文章正文。前端默认展开，展开/收起仅保存在浏览器。

### GET /outline-nodes/{nodeId}

data 为 `{node: NodeSummary, breadcrumbs: Breadcrumb[], children: NodeSummary[], asOf}`。children 仅包含直属可见子节点并按 sortOrder、id 排序。用于直接访问节点链接、自身有卡片又有子节点的中间节点，以及独立卡片/文章列表页面的上下文加载。

该接口与 tree 的区别是只读取一层，避免刷新节点内容页时再次下载整个技术分支。

## 5. 卡片与文章阅读

### GET /outline-nodes/{nodeId}/cards

入参 page、pageSize。data 为分页对象加 asOf，items 为 CardSummary，仅包含直属可见卡片，按 sortOrder、id 排序。可见节点没有卡片时返回 total=0；隐藏节点返回 404。

“开始学习”从第一页第一张进入。“下一张”使用当前分页列表中的顺序，跨页时获取下一页；详情直达时，可返回所属节点重新进入列表，不增加单独的下一张学习卡接口。

### GET /cards/{cardId}

data 包含：

- `id/outlineNodeId/question/answerMarkdown/explanationMarkdown/exampleMarkdown/sourceMarkdown/sortOrder/updatedAt`；
- `breadcrumbs: Breadcrumb[]`、`learningState: LearningState`、`asOf`。

问题与答案一次返回，前端初始仅展示问题，点击“显示答案”才展开答案及存在的补充字段。该按钮属于练习交互，不是答案访问权限，因此不另建“显示答案”写接口，不保存用户回答或浏览流水。

返回的归属信息用于面包屑；返回来源（知识库列表或复习中心）、分页与滚动位置由前端路由状态维护。每次切换卡片恢复答案折叠状态，不自动评价。

### GET /outline-nodes/{nodeId}/articles

入参 page、pageSize。data 为分页对象，items 为 ArticleSummary。只查询本节点直属已发布文章，按 publishedAt DESC、id DESC 排序。没有文章返回空列表，前端不显示文章入口；文章不是进入卡片的前提。

### GET /articles/{articleId}

data 为 `{id, outlineNodeId, title, bodyMarkdown, publishedAt, createdAt, updatedAt, breadcrumbs}`。纯阅读，不影响节点进度、用户状态或复习队列。

## 6. 间隔预览与评价

### GET /cards/{cardId}/review-preview?timezone=Asia/Shanghai

timezone 使用 IANA 时区，省略按现有 ReviewZoneId 默认 UTC，非法值返回 400。预览与提交必须传相同的用户时区。客户端优先使用浏览器时区，不能硬编码新卡“1 分钟、6 分钟、10 分钟、4 天”。

data 为：

```json
{
  "cardId": 123,
  "enrolled": false,
  "timezone": "Asia/Shanghai",
  "asOf": "2026-09-15T08:00:00Z",
  "options": [
    {"rating": "AGAIN", "dueAt": "2026-09-15T08:01:00Z", "intervalDays": 0},
    {"rating": "HARD", "dueAt": "2026-09-15T08:05:30Z", "intervalDays": 0},
    {"rating": "GOOD", "dueAt": "2026-09-15T08:10:00Z", "intervalDays": 0},
    {"rating": "EASY", "dueAt": "2026-09-19T08:00:00Z", "intervalDays": 4}
  ]
}
```

以上仅展示响应形状和默认间隔示例，实际 dueAt 由当前调度器、用户时区日界线与偏好生成。options 固定 AGAIN、HARD、GOOD、EASY 顺序，中文分别为重来、困难、良好、简单。分钟间隔按 dueAt 与 asOf 的差值显示；intervalDays 不能代替短期分钟值。

未入队时使用 SchedulingState.initial() 的内存状态，不写用户状态。复用 FsrsReviewSchedulerService、AnkiLearningPolicy、ReviewDayBoundary；相同输入、时间、偏好、随机配置下预览与应用一致。提交以当时服务端时间和最新状态重新计算，不保证稍后提交仍得到预览中的绝对时间。

### POST /cards/{cardId}/review-attempts

请求：

```json
{
  "clientAttemptId": "ccba2a84-df1f-4c7d-b2c4-bf8b9c718dea",
  "rating": "HARD",
  "timezone": "Asia/Shanghai"
}
```

clientAttemptId 为必填 UUID，rating 必须为四档之一；timezone 与预览规则一致。不接收用户 ID、客户端评价时间、调度状态、自由回答或入队标志。

data 为：

| 字段 | 语义 |
| --- | --- |
| id / cardId / userStateId | 评价流水 ID、共享卡片 ID、当前用户调度状态 ID |
| clientAttemptId / rating / reviewedAt | 原始成功评价信息，评价时间取服务端 |
| firstReview | 该流水是否为首次评价；重试返回原值 |
| duplicate | 本次是否命中已成功处理的幂等键 |
| schedulingBefore / schedulingAfter | 复用 ReviewSchedulingSnapshot 结构和语义，保存当次评价前后状态 |

快照字段为 repetitions、intervalDays、lapses、fsrsState、fsrsStep、fsrsStability、fsrsDifficulty、dueAt、lastReviewedAt、lastRating。首次前态使用内存初始状态，dueAt、lastReviewedAt、lastRating 为 null；后态是实际持久化结果。firstReview 可由首次前态 lastReviewedAt=null 判定，不新增数据库字段。

四种评价都入队，包括 EASY。知识库允许对未到期的已入队卡主动评价；“开始复习”只从到期卡选取。首次成功创建一行状态和一行流水；已有状态评价仍更新同一 (user_id, card_id)。

### 事务与幂等

1. 验证登录、参数和卡片当前可见性；从当前用户的流水检查 clientAttemptId。同一键对应相同 cardId/rating 则返回原流水和快照，duplicate=true；不同卡片或评级返回 409。
2. timezone 只影响第一次成功调度，重试不重新计算。数据库没有保存请求时区，首期不对“同键改时区”单独报冲突；客户端重试仍须保持原请求。
3. 新评价事务锁定卡片及祖先链，防止受控内容导入同时移动或归档；固定按节点 ID 升序取共享行锁，再锁卡片，并在锁后重新验证链路。已有个人状态加排他行锁。
4. 首次在内存计算评价后状态，再使用唯一约束配合 INSERT ON CONFLICT 尝试插入；冲突则读取并锁定已有状态，重新检查幂等键并重新计算。不能把 PostgreSQL 唯一冲突异常吞掉后继续使用已失败事务。
5. 状态和流水在同一事务提交；流水幂等冲突时回滚本次全部调度变更和临时状态，再在新事务读取原流水。不能遗留没有首次流水的入队状态。
6. 每次评价成功后刷新列表/汇总。幂等重放返回历史 schedulingAfter，若之后已有其他评价，不能把历史快照当作最新状态覆盖页面，应重新 GET 卡片。

可见性优先：卡片在首次成功后被归档，旧请求重试返回 404，不再暴露其内容或调度快照，也不会重复调度。该规则是内容隐藏后的明确例外；内容仍可见时返回原成功结果。

客户端在同一轮提交期间禁用四档按钮。网络超时或结果不确定时，使用同一 UUID 和原请求重试；成功后本轮不可重复评价。之后新一轮评价生成新 UUID。事务失败可使用原 UUID 重试。

## 7. 八股文复习中心

### GET /review/summary

无业务查询参数。data 为 `{enrolledCount, dueCount, nextDueAt, asOf}`。

enrolledCount 为当前用户全部可见且已有状态的卡片数；dueCount 为其中 dueAt <= asOf 的数量；nextDueAt 为当前可见已入队卡片中严格晚于 asOf 的最早到期时间，没有则为 null。两项数量都不包含未评价卡片或隐藏分支。

有 dueCount 时启用“开始复习”；没有到期卡片时显示 nextDueAt；enrolledCount=0 时引导去知识库。知识卡不使用算法题的每日新卡配额或跨中心任务池。

### GET /review/cards?filter=ALL&page=1&pageSize=20

filter 为 ALL（默认）或 DUE，非法值返回 400。返回分页对象加 asOf，items 为 CardSummary 加 `breadcrumbs`，按 dueAt、id 升序排列，这里的 id 固定为用户状态主键作为排序兜底，响应另带 `userStateId`。

ALL 显示全部可见已入队卡，包括未到期卡；DUE 仅显示 dueAt <= asOf。每行 LearningState 提供阶段和到期标记，不使用 NEW、MASTERED 或独立掌握状态。

不接受其他用户 ID，不提供修改用户状态的通用 PATCH。分页为实时视图，评价后数据可能换页；前端评价成功后重新拉取，不把旧分页视为固定复习队列。

### GET /review/next

无业务查询参数。data 为 `{card: CardSummaryWithBreadcrumbs | null, dueCount, nextDueAt, asOf}`，候选附带 userStateId，计数和时间定义同 summary。

仅从当前用户可见、已入队且到期的卡片中取一张，排序为：

1. LEARNING/RELEARNING 优先于 REVIEW；
2. 同优先级按 dueAt、用户状态 id 升序。

没有到期卡片时返回 200 和 card=null，供页面显示“本轮已完成”与下次到期时间。该接口不领取、不锁定永久任务、不标记完成、不推进调度，不建会话表。

客户端取到候选后调用卡片详情、预览和统一评价接口。每次评价成功后重新调用 next；重来后的卡片到新到期时间前不会再次选中，到期后可重新参与。退出、刷新和恢复页面都不改变调度。

多标签页可能读到同一候选，next 不承诺排他领取。不同 UUID 的有效评价按数据库锁串行处理；相同 UUID 只处理一次。首期不引入会话占用或“一个卡片只能有一个打开页面”的约束。

## 8. 既有复习能力复用

复用 `GET/PATCH /api/me/review-preferences` 及已有请求字段：desiredRetention、dailyNewLimit、dailyLearningLimit、dailyReviewLimit、maximumIntervalDays、enableFuzzing。这些是共享偏好，修改也会影响算法题；知识卡首期只使用调度相关参数，不据每日配额截断到期队列。

当前 FsrsReviewSchedulerService 输入依赖 ProblemReviewCard，实施时提取调度状态、到期时间、最近评价时间的轻量输入，给算法题保留适配入口。知识卡独立维护状态/流水和查询，不伪造 problemSlug，不复制算法，不使用 Code Review 自动入队策略。

## 9. 页面调用顺序

| 页面/动作 | 调用顺序 |
| --- | --- |
| 知识库首页 | topics |
| 技术大纲 | outline-nodes/{id}/tree；展开收起在浏览器完成 |
| 节点卡片列表 | 节点详情 + 节点 cards；并行读取 |
| 卡片学习 | cards/{id} → 本地显示答案 + review-preview → 可选 review-attempts |
| 下一张学习卡 | 从当前节点分页列表选择下一项，再读详情；不评价也能继续 |
| 节点文章阅读 | 节点 articles → articles/{id} |
| 复习中心 | review/summary + review/cards，默认 ALL；切换 DUE 重新查询 |
| 开始/继续复习 | review/next → 卡片详情 → 显示答案与预览 → 评价 → 再次 next |
| 返回列表或页面恢复 | 重取原来源列表与汇总；不固定返回算法题复习中心 |

## 10. 错误、导入与实现边界

| HTTP | 建议错误码 | 场景 |
| --- | --- | --- |
| 400 | KNOWLEDGE_INVALID_REQUEST | ID、分页、筛选、UUID、评级或时区非法 |
| 401 | 复用现有认证错误 | 未登录或会话失效 |
| 404 | KNOWLEDGE_NOT_FOUND | 不存在、不可见或 root 资源 |
| 409 | KNOWLEDGE_ATTEMPT_CONFLICT | 同用户同 clientAttemptId 用于不同卡片或评级 |
| 503 | KNOWLEDGE_SERVICE_UNAVAILABLE | 数据库/调度服务不可用，或短暂并发冲突在有限重试后仍未完成 |

错误不包含 SQL、隐藏内容或其他用户数据。评价异常统一回滚；锁死锁/序列化失败可在服务端有限重试，超限后客户端仍使用原幂等键。

数据库导入负责 root 唯一、无环、普通节点归属、同级 slug、正文长度、发布状态及 publishedAt 等约束；在单个事务提交内容，保持共享卡片 ID 稳定，不删除再重建已有学习历史的卡片。已有历史采用归档，导入不得清空或生成用户学习状态/流水。

在线导入涉及节点移动、发布/归档时，应按与评价相同的节点 ID 顺序锁定受影响祖先及节点，再更新卡片；节点结构变更须锁定被移动节点与新旧父链，并在锁后复核。离线维护也可在暂停业务写入期间完成。DDL 的唯一/外键不能代替无环校验或业务可见性检查。本次不新增导入脚本或导入接口。

拟按职责组织 controller/knowledge、knowledge/service、knowledge/model、knowledge/repository，持久化沿用 MyBatis/PostgreSQL；跨类路径、评级、状态、错误码和字段限值统一使用模块常量/枚举。用户响应 DTO 与数据库实体分离，不直接返回其他用户字段或内部 JSON 流水。

## 11. 实施顺序与验收

先实现公共可见性查询与知识库 7 个阅读接口，再实现调度适配、预览与评价，最后实现复习中心 3 个接口和页面联调。管理员接口、内容编辑页和自动导入均不列入任务。

实现阶段通过根 Makefile 的 backend-test/backend-it/frontend-test 入口验证：

- 空库各页面可用，所有 GET 无写入；仅浏览、显示答案、预览均不入队。
- 多层节点和隐藏祖先在列表、计数、详情、预览、评价与复习中心具有一致可见性；root 不可直接读取。
- 无文章节点、无卡片节点、分页边界、同级排序与子树聚合正确。
- 新卡四种评价各创建一条状态和一条流水，EASY 未到期也立即出现在 ALL。
- 并发首次评价、重复 UUID、跨卡/跨评级冲突及事务失败不会重复调度或遗留孤立状态。
- 固定时钟/时区/偏好/随机配置校验预览与应用一致；算法题原有调度回归通过。
- 多用户状态隔离，未到期卡不进入 next；重来卡到期后可再次出现，空队列正常结束。
- 归档保留历史但从个人可见数量与队列隐藏；幂等历史响应不覆盖后续最新调度。
- 页面保留返回来源，不评价退出不写库；网络重试复用 UUID，成功后刷新列表与汇总。
