# 业务缓存数据与参数决策记录

更新时间：2026-07-22

状态：已实施

## 一、文档目的

本文记录 `algo-mentor` 各类业务数据是否接入缓存、采用的缓存类型、缓存边界、失效方式和配置参数，作为后续业务缓存接入的实现依据。

基础设施约束和缓存类型定义以 `docs/cache-module-v1-design.md` 为准。本文只记录逐项确认后的业务决策；尚未讨论或尚未拍板的配置不视为实施结论。

## 二、已确认决策

### 2.1 全局 AI 运行设置

#### 业务含义

全局 AI 运行设置包含 `aiEnabled` 全局止损开关和默认每日请求额度。AI 准入链路会高频读取该设置，管理员修改后需要所有应用节点及时生效。

#### 缓存决策

| 配置项 | 结论 |
|---|---|
| 缓存数据 | 数据库中的全局 AI 运行设置原始快照或记录缺失状态 |
| 缓存类型 | `SharedTtlCacheRegion` |
| cache name | `ai-runtime-settings` |
| namespace | `ai-runtime-settings` |
| schema version | `1` |
| key token | `singleton` |
| `maximum-size` | `1` |
| `ttl` | `2m` |
| 正常跨节点传播目标 | 依靠共享失效事件和 `poll-interval=1s`，目标约 1 至 2 秒 |
| 负缓存 | 允许缓存数据库记录缺失状态 |
| loader 异常 | 不缓存异常，不使用旧值掩盖异常，保持现有抛错语义 |
| 失效入口 | 管理员成功更新全局 AI 运行设置 |

#### 实现约束

- 缓存数据库原始设置快照或明确的记录缺失状态，不缓存结合具体 `AiPurposePolicy` 计算后的 fallback。
- 数据库记录缺失时，在缓存外根据当前 `AiPurposePolicy` 计算静态 fallback，避免不同 AI purpose 的默认额度互相污染。
- `ttl=2m` 表示共享失效事件发布或消费持续异常时，旧设置允许保留的最大时间；正常管理员修改不等待 TTL 到期。
- 管理员更新设置时，业务数据更新和共享失效事件必须在同一数据库事务内提交，当前节点在事务提交后立即失效。

### 2.2 用户 AI 覆盖策略

#### 业务含义

用户 AI 覆盖策略允许管理员针对单个用户暂停 AI，或覆盖其每日请求额度。每次 AI 准入都会按用户读取该策略；多数用户没有独立策略记录，表示继承全局设置。

#### 缓存决策

| 配置项 | 结论 |
|---|---|
| 缓存数据 | `AiUserPolicy`，包括表示无独立覆盖的 inherited 对象 |
| 缓存类型 | `SharedTtlCacheRegion` |
| cache name | `ai-user-policy` |
| namespace | `ai-user-policy` |
| schema version | `1` |
| key token | 十进制 `userId` |
| `maximum-size` | `500` |
| `ttl` | `2m` |
| 正常跨节点传播目标 | 依靠共享失效事件和 `poll-interval=1s`，目标约 1 至 2 秒 |
| 负缓存 | 缓存 `AiUserPolicy.inherited(userId)`，表示数据库中没有该用户的覆盖记录 |
| loader 异常 | 不缓存异常，保持 AI 准入链路现有的失败关闭语义 |
| 失效入口 | 管理员 upsert 或删除指定用户的 AI 覆盖策略 |

#### 实现约束

- 数据库中不存在用户策略记录和数据库查询异常必须严格区分；查询异常不得降级为 inherited 策略。
- 管理员更新或删除覆盖策略时，业务写入和该 `userId` 的共享失效事件必须在同一数据库事务内提交。
- `maximum-size=500` 面向当前 5 至 20 人封闭内测保留了充足余量；后续只根据实际活跃用户数、命中率和容量淘汰指标调整。
- `ttl=2m` 表示共享失效链路持续异常时，用户暂停状态或额度覆盖允许陈旧的最大时间。

### 2.3 用户访问快照

#### 业务含义

用户访问快照服务于已认证 API 请求的身份有效性校验。当前认证过滤器会按请求读取用户状态、邮箱和角色，再结合内测准入策略决定是否继续放行；缓存该最小快照可以避免每个请求重复查询用户表和角色表。

#### 缓存决策

| 配置项 | 结论 |
|---|---|
| 缓存数据 | `AuthAccessSnapshot(userId, email, status, roles)` 或明确的用户不存在状态 |
| 缓存类型 | `SharedTtlCacheRegion` |
| cache name | `auth-access-snapshot` |
| namespace | `auth-access-snapshot` |
| schema version | `1` |
| key token | 十进制 `userId` |
| `maximum-size` | `500` |
| `ttl` | `1m` |
| 正常跨节点传播目标 | 依靠共享失效事件和 `poll-interval=1s`，目标约 1 至 2 秒 |
| 负缓存 | 缓存用户不存在状态 |
| loader 异常 | 不缓存异常，认证链路保持失败关闭 |
| 失效入口 | 用户禁用、恢复、软删除、邮箱修改，以及角色增加或删除 |

#### 实现约束

- 快照只包含访问判断必需的用户 ID、邮箱、状态和角色，不包含密码、凭据、Session、头像、展示名、登录时间或其他完整用户资料。
- 缓存用于已认证请求的访问校验，不替代登录时的密码凭据读取，也不用于管理员用户列表或详情查询。
- 更新 `lastLoginAt` 不影响快照内容，因此不触发该缓存失效。
- 用户不存在与数据库查询异常必须严格区分；查询异常不得转换成负缓存。
- 所有用户状态、邮箱和角色写入入口都必须在业务事务内发布对应 `userId` 的共享失效事件。
- `ttl=1m` 表示共享失效链路持续异常时，已禁用用户或已撤销角色仍可能被旧快照放行的最长兜底时间。

### 2.4 内测准入全局设置

#### 业务含义

内测准入全局设置控制邮箱白名单是否生效。设置关闭时，有效用户不受邮箱白名单限制；设置开启后，除受信管理员外，注册、登录和已认证 API 请求都必须通过邮箱白名单校验。

#### 缓存决策

| 配置项 | 结论 |
|---|---|
| 缓存数据 | 鉴权所需的 `emailAllowlistEnabled` 或设置记录缺失状态 |
| 缓存类型 | `SharedTtlCacheRegion` |
| cache name | `auth-beta-access-settings` |
| namespace | `auth-beta-access-settings` |
| schema version | `1` |
| key token | `singleton` |
| `maximum-size` | `1` |
| `ttl` | `30s` |
| 正常跨节点传播目标 | 依靠共享失效事件和 `poll-interval=1s`，目标约 1 至 2 秒 |
| 负缓存 | 缓存设置记录缺失状态 |
| loader 异常 | 不缓存异常，认证链路保持失败关闭 |
| 失效入口 | 管理员修改邮箱白名单开关 |

#### 实现约束

- 鉴权缓存只保存白名单是否启用，不保存更新管理员、更新时间或管理员页面使用的其他展示字段。
- 设置记录缺失时保持现有业务语义：记录错误日志，并视为邮箱白名单关闭；数据库查询异常与记录缺失必须严格区分。
- 管理员更新白名单开关时，设置写入和 `singleton` 共享失效事件必须在同一数据库事务内提交。
- `ttl=30s` 表示共享失效链路持续异常时，白名单开关错误放行或错误拒绝的最长兜底时间。

### 2.5 邮箱准入结果

#### 业务含义

邮箱白名单开启后，注册、登录和已认证 API 请求需要判断规范化邮箱是否存在于 `auth_beta_allowed_email`。缓存只表达单个邮箱的白名单成员关系，不缓存组合了全局开关、管理员豁免等因素的最终准入决策。

#### 缓存决策

| 配置项 | 结论 |
|---|---|
| 缓存数据 | 规范化邮箱是否属于内测白名单的布尔结果 |
| 缓存类型 | `SharedTtlCacheRegion` |
| cache name | `auth-beta-email-membership` |
| namespace | `auth-beta-email-membership` |
| schema version | `1` |
| 业务 key | 规范化邮箱 |
| key token | `sha256(normalizedEmail)` 的完整十六进制字符串 |
| `maximum-size` | `500` |
| `ttl` | `1m` |
| 正常跨节点传播目标 | 依靠共享失效事件和 `poll-interval=1s`，目标约 1 至 2 秒 |
| 负缓存 | 缓存 `false`，表示该邮箱不在白名单中 |
| loader 异常 | 不缓存异常，认证链路保持失败关闭 |
| 失效入口 | 白名单成功添加或删除对应规范化邮箱 |

#### 实现约束

- 无效邮箱在进入缓存前直接拒绝，不为格式无效的输入生成缓存 key。
- key token、失效事件和默认日志不得包含原始邮箱；所有节点必须使用相同的邮箱规范化和 SHA-256 编码规则。
- 管理员豁免、全局白名单开关和邮箱成员关系在缓存外组合计算，禁止缓存最终 `ALLOWED/DENIED` 判断。
- 批量添加只为实际新增成功的邮箱发布失效事件；已存在或格式无效的邮箱不发布事件。
- 删除白名单记录时，先取得其规范化邮箱，再在删除事务内发布对应 key 的共享失效事件。
- `ttl=1m` 表示共享失效链路持续异常时，已移除邮箱仍被旧缓存放行，或新添加邮箱仍被旧负缓存拒绝的最长兜底时间。

### 2.6 题库静态数据

#### 业务含义

题库数据来自 LeetCode 接口和离线 seed，当前产品阶段在导入完成后按发布期不可变数据管理。题面会被题目详情、练习聊天、Agent 取题、Code Review、错题复习和学习计划等多个流程按 slug 重复读取；题库筛选快照还需要执行多组聚合查询。

#### 缓存决策

| 缓存 | 缓存类型 | cache name | key | `maximum-size` | TTL |
|---|---|---|---|---:|---|
| 题目静态快照 | `LocalBoundedCacheRegion` | `problem-static-snapshot` | 规范化 `problemSlug` | `4000` | 无 |
| 题库筛选快照 | `LocalBoundedCacheRegion` | `problem-filters` | `ProblemLocale` | `2` | 无 |

题目静态快照统一保存以下信息：

```java
public record ProblemStaticSnapshot(
    String slug,
    Integer frontendId,
    String frontendDisplayId,
    String titleEn,
    String titleZh,
    ProblemDifficulty difficulty,
    List<TrustedProblemTag> tags,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String contentStatus,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit,
    String recommendationReasonEn,
    String recommendationReasonZh
) {
}
```

#### 实现约束

- 同一个 slug 只缓存一份与 locale 无关的双语原始快照，在缓存外按 locale 投影为 API、聊天、Review、复习和学习计划需要的对象。
- `TrustedProblemTag` 必须包含稳定 `tagId`、value 和中英文标签；题目静态快照同时承载规范化题目标签关系，不再单独建设 `problem-trusted-tags` 缓存。
- 题目静态快照采用懒加载，不在普通服务启动时一次性加载全部题库。
- 允许缓存题目不存在状态；题库不可变前提下，该负缓存同样稳定。
- 题库筛选快照缓存完整 `ProblemFilters`，包括题目总数、难度、标签、分类、公司、岗位、时间范围及对应数量；当前只存在中英文两个 locale key。
- 第一版不缓存题目搜索、筛选分页结果和导入中间结果，避免组合 key 膨胀和低复用数据占用内存。
- 当前 3591 道题的双语题面、Python 模板和样例约为 9.7 MB UTF-8，推荐理由源文件约为 1.7 MB；全量 Java 对象预计为几十 MB，且懒加载下通常低于全量。
- 任意题库 seed、题目标签、分类、公司信号或推荐理由重新导入后，必须重启或滚动替换全部服务节点。未来支持在线导入或管理员编辑时，需要重新评估为 Shared TTL 或版本化 key。

### 2.7 学习计划模板目录

#### 业务含义

学习计划模板用于模板列表、模板详情、从模板创建学习计划草稿和 Today Pack 推荐。模板、阶段和题目引用由离线 seed 导入，当前产品阶段按发布期不可变数据管理。

#### 缓存决策

| 配置项 | 结论 |
|---|---|
| 缓存数据 | 包含完整模板、阶段和题目引用的 `LearningPlanTemplateCatalog` |
| 缓存类型 | `LocalBoundedCacheRegion` |
| cache name | `learning-plan-template-catalog` |
| key | `singleton` |
| `maximum-size` | `1` |
| TTL | 无 |
| 加载方式 | 首次访问时一次性加载完整模板目录 |
| loader 异常 | 不缓存异常 |
| 失效方式 | 服务进程重启 |

建议的目录结构：

```java
public record LearningPlanTemplateCatalog(
    List<LearningPlanTemplate> orderedTemplates,
    Map<String, LearningPlanTemplate> templatesById
) {
}
```

#### 实现约束

- 模板列表从 `orderedTemplates` 映射摘要，模板详情和草稿创建从 `templatesById` 读取，禁止分别维护可能不一致的列表缓存和详情缓存。
- 模板不存在直接由目录 map 判断，不建立单独的负缓存 region。
- 完整目录采用懒加载；当前 16 个模板和 631 条题目引用总体较小，不需要按模板拆分缓存。
- 全量加载应分别批量查询模板、阶段和题目引用，再在内存中组装，目标约为 3 次 SQL；避免按模板逐个查询阶段和引用形成 N+1。
- 学习计划模板 seed 重新导入后，必须重启或滚动替换全部服务节点。未来支持在线模板编辑时，需要重新评估缓存类型和失效机制。

### 2.8 代码内置 prompt 和固定映射

#### 业务含义

该类数据包括代码中的系统提示词、Prompt Builder 固定片段、工具说明、prompt 版本号、枚举标签和固定业务映射。它们随应用版本发布，不从数据库或外部模板文件动态读取。

#### 缓存决策

不接入缓存模块，不配置 cache name、容量、TTL 或失效机制。

#### 实现约束

- 代码内置 prompt 使用版本常量、静态模板或单例 Bean；不缓存包含用户消息、画像、题面和会话状态的最终渲染结果。
- 固定映射使用枚举、不可变集合或 `switch`，不通过 cache region 包装。
- 正则、JSON Schema 或其他需要预编译的固定对象使用 `static final` 或单例 Bean。
- 未来如果 prompt 改为数据库在线编辑，需要作为独立业务数据重新评估，通常采用 `SharedTtlCacheRegion`。
- 未来如果引入外部模板文件，默认在启动时加载并构造成单例 Bean；只有存在运行期更新需求时才重新讨论缓存和失效机制。

### 2.9 学习者画像只读召回

#### 业务含义

练习聊天在进入 Agent loop 前读取用户自述画像、通用观察和当前题目标签相关能力画像。当前每个 Agent run 最多执行约 3 次数据库查询，不在每个模型 step 重复读取。

#### 缓存决策

第一版不缓存学习者画像召回数据。

#### 实现约束

- `findCurrentForUpdate`、用户行锁、版本切换、抑制和删除写路径始终直接访问权威数据库。
- 不缓存 `userId + scenario + problemSlug` 组合形成的最终召回快照或最终 prompt 内容。
- 如果后续监控表明画像召回数据库耗时成为瓶颈，优先评估将多次查询合并为一次受控查询，再重新评估按 `LearnerProfileIdentity` 精确缓存。
- 后续讨论只继续覆盖建议实际接入缓存的数据，不逐项展开其他低收益或不适合缓存的候选。

## 三、Shared 缓存公共参数

### 3.1 失效事件轮询间隔

#### 业务含义

每个应用节点运行一个后台 poller，按统一 cursor 增量读取 PostgreSQL 失效事件。poller 不遍历缓存 region 或缓存 entry，也不比较每个缓存项的版本号；它只把新增事件按 `cacheName` 路由到对应 Shared region，并精确失效事件指定的 key。

#### 参数决策

```yaml
algo-mentor:
  cache:
    coherence:
      poll-interval: ${CACHE_COHERENCE_POLL_INTERVAL:1s}
```

| 配置项 | 结论 |
|---|---|
| 默认值 | `1s` |
| 建议最小值 | `250ms`，更小的配置启动失败 |
| 正常跨节点传播目标 | 约 1 至 2 秒 |
| 生效范围 | 仅 `postgres-coherent-caffeine` provider |

#### 实现约束

- 每个应用节点只运行一个单线程 poller，单次 poll 完成后再调度下一次，不允许重叠执行。
- 应用重启时本地 Caffeine 为空，poller 以事件表当前最大 ID 作为新 cursor，只消费启动后新增事件，不从头回放历史事件。
- 没有新增事件时不访问任何缓存内容；运行成本主要取决于节点数和轮询频率，不随注册 region 数量线性增加。
- 轮询失败时保留 cursor 并在后续周期重试，不清空缓存；业务缓存 TTL 继续承担最终陈旧上限。
- 事件保留缺口检查不需要每秒执行，后续实现应调整为低频检查，或在 poller 从失败状态恢复时检查。

### 3.2 单次轮询批量大小

#### 业务含义

`batch-size` 表示单次 poll 最多从事件表读取多少条新增失效事件。它不控制任何业务缓存的容量，只影响突发失效事件的消化速度和单次数据库查询规模。

#### 参数决策

```yaml
algo-mentor:
  cache:
    coherence:
      batch-size: ${CACHE_COHERENCE_BATCH_SIZE:500}
```

| 配置项 | 结论 |
|---|---|
| 默认值 | `500` |
| 合法范围 | `1` 至 `5000` |

#### 实现约束

- 每条事件只包含 cache name、namespace、schema version、key token、事件类型和创建时间等轻量字段。
- 当前邮箱白名单批量添加上限为 100，默认单批可以完整处理一次业务批量更新产生的事件。
- 某次查询返回数量等于 `batch-size` 时，poller 应立即继续拉取下一批，不等待正常 `poll-interval`。
- 只有某批返回数量小于 `batch-size` 后，才进入下一次正常轮询等待，避免事件突发时积压按每秒一批缓慢消化。
- 单批上限不宜无限放大，避免长时间占用数据库连接和 poller 单线程。

### 3.3 轮询抖动比例

#### 业务含义

`jitter-ratio` 用于分散多个应用节点的轮询时刻，避免所有节点形成固定同步节奏并在同一毫秒查询 PostgreSQL。

#### 参数决策

```yaml
algo-mentor:
  cache:
    coherence:
      jitter-ratio: ${CACHE_COHERENCE_JITTER_RATIO:0.2}
```

| 配置项 | 结论 |
|---|---|
| 默认值 | `0.2` |
| 合法范围 | `0` 至 `0.5`，包含边界 |
| `poll-interval=1s` 时的实际等待范围 | `0.8s` 至 `1.2s` |

#### 实现约束

- 每次正常轮询完成后重新计算随机等待时间，长期平均轮询频率仍接近配置的 `poll-interval`。
- `jitter-ratio=0` 表示关闭抖动。
- 满批立即继续拉取积压事件时不应用抖动，事件队列基本排空后才恢复正常抖动等待。

### 3.4 失效事件保留时间

#### 业务含义

`event-retention` 决定失效事件在 PostgreSQL 中保留多久，用于覆盖仍在运行但短时间无法消费事件的节点。进程重启后的节点本地缓存为空，不依赖历史事件回放。

#### 参数决策

```yaml
algo-mentor:
  cache:
    coherence:
      event-retention: ${CACHE_COHERENCE_EVENT_RETENTION:24h}
```

| 配置项 | 结论 |
|---|---|
| 默认值 | `24h` |
| 最小约束 | 必须大于应用中最大的 Shared 缓存 TTL |

#### 实现约束

- 当前最大的 Shared 缓存 TTL 为 `2m`，`24h` 为 poller 故障、数据库短时不可用和节点暂停保留充足恢复窗口。
- 如果仍在运行的节点 cursor 已落后于当前最小保留事件 ID，该节点必须清空所有本地 Shared region，再把 cursor 跳到当前最大事件 ID。
- 不允许在检测到事件缺口后直接跳过历史事件并继续使用已有缓存。
- 后续增加更长 TTL 的 Shared region 时，启动检查必须同步验证 retention 仍大于最大 TTL。

### 3.5 失效事件清理间隔

#### 业务含义

`cleanup-interval` 决定多久删除一次超过 `event-retention` 的失效事件，控制事件表和时间索引的增长速度。

#### 参数决策

```yaml
algo-mentor:
  cache:
    coherence:
      cleanup-interval: ${CACHE_COHERENCE_CLEANUP_INTERVAL:1h}
```

| 配置项 | 结论 |
|---|---|
| 默认值 | `1h` |

#### 实现约束

- 清理条件固定为 `created_at < now() - event-retention`，不得按 cursor 删除仍在 retention 窗口内的事件。
- 第一版允许每个节点执行幂等清理；当前事件量和节点数较小，不额外引入 leader election 或分布式锁。
- 清理失败只记录受控日志和指标，不影响 poller 消费和业务请求，下一清理周期继续重试。
- 后续事件量显著增大时，再评估分批删除、单节点清理或 PostgreSQL 分区，不在第一版提前实现。

## 四、最终配置汇总

### 4.1 Shared provider 公共配置

数据库环境建议使用：

```yaml
algo-mentor:
  cache:
    enabled: ${CACHE_ENABLED:true}
    metrics-enabled: ${CACHE_METRICS_ENABLED:true}
    shared-provider: ${CACHE_SHARED_PROVIDER:postgres-coherent-caffeine}
    coherence:
      enabled: ${CACHE_COHERENCE_ENABLED:true}
      poll-interval: ${CACHE_COHERENCE_POLL_INTERVAL:1s}
      batch-size: ${CACHE_COHERENCE_BATCH_SIZE:500}
      jitter-ratio: ${CACHE_COHERENCE_JITTER_RATIO:0.2}
      event-retention: ${CACHE_COHERENCE_EVENT_RETENTION:24h}
      cleanup-interval: ${CACHE_COHERENCE_CLEANUP_INTERVAL:1h}
```

无 DataSource 的默认启动、单元测试或明确单节点环境可以使用 `shared-provider=caffeine`。需要多节点一致性的数据库环境必须使用 `postgres-coherent-caffeine`，缺少 DataSource 时启动失败，不静默降级。

### 4.2 业务缓存参数

| cache name | 类型 | `maximum-size` | TTL | key |
|---|---|---:|---:|---|
| `ai-runtime-settings` | Shared TTL | `1` | `2m` | `singleton` |
| `ai-user-policy` | Shared TTL | `500` | `2m` | `userId` |
| `auth-access-snapshot` | Shared TTL | `500` | `1m` | `userId` |
| `auth-beta-access-settings` | Shared TTL | `1` | `30s` | `singleton` |
| `auth-beta-email-membership` | Shared TTL | `500` | `1m` | 规范化邮箱，事件 token 使用 SHA-256 |
| `problem-static-snapshot` | Local bounded | `4000` | 无 | `problemSlug` |
| `problem-filters` | Local bounded | `2` | 无 | `ProblemLocale` |
| `learning-plan-template-catalog` | Local bounded | `1` | 无 | `singleton` |

Shared region 的 namespace 与 cache name 保持相同，首版 schema version 均为代码常量 `1`，不允许通过环境变量随意修改。容量和 TTL 由所属业务模块的类型化配置属性管理。

## 五、实施顺序

1. 完成 poller 的满批连续消费、低频 gap 检查和公共参数范围校验。
2. 接入 AI 全局设置和用户覆盖策略两个 Shared region。
3. 接入认证访问快照、内测准入设置和邮箱成员关系三个 Shared region。
4. 接入题目静态快照和题库筛选两个 Local bounded region。
5. 实现学习计划模板全量批量查询和单例目录缓存。
6. 补齐各业务写路径的同事务失效事件、提交后本地失效、指标和测试。

## 六、最终结论

第一版实际接入 5 个 Shared TTL region 和 3 个 Local bounded region。Shared 缓存通过 PostgreSQL 增量失效事件实现跨节点精确失效，正常传播目标约为 1 至 2 秒；各业务 TTL 只负责共享失效链路持续异常时的最终陈旧上限。题库和学习计划模板按发布期不可变快照处理，重新导入后通过服务重启或滚动替换完成全量刷新。

学习者画像、代码内置 prompt、固定映射、搜索分页结果和其他低收益动态数据不纳入第一版缓存。后续只有在监控数据证明数据库读取成为实际瓶颈，或业务增加在线编辑能力时，再重新评估对应缓存边界。
