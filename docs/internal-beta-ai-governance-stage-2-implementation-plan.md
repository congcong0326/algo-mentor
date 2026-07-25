# 内测管理员能力阶段二：AI 止损与成本观测详细实施计划

> 上位设计：`docs/internal-beta-admin-capabilities-design.md`
>
> 总体计划：`docs/internal-beta-admin-capabilities-implementation-plan.md`
>
> 本文细化总体计划中的 Task 6-8；如两份计划在阶段二的任务粒度或文件落位上不一致，以本文为准。
>
> 基线日期：2026-07-14；基线提交：`7c8aad9`。

---

## 1. 交付目标与固定决策

### 1.1 阶段二交付结果

阶段二完成后，管理员应能在不重启服务的情况下完成以下闭环：

1. 查看并切换全局 AI 状态。
2. 修改全局默认每日 AI 入口请求上限。
3. 暂停单个用户的 AI 使用，或覆盖该用户的每日入口请求上限。
4. 查看按用户、模型和业务场景聚合的调用数、Token 与估算成本。
5. 为实际出现的模型配置非缓存输入、缓存输入、输出三类单价和成本倍率。
6. 识别未定价模型，且未定价调用不会以 `$0` 混入已定价成本。
7. 确认 Agent step 和代码 Review 都进入调用级 Token 台账；题目复习不调用模型。
8. 在全局或用户级关闭 AI 后，下一次相应准入或后台生成不再调用模型。

### 1.2 已确认的产品边界

阶段二采用“AI 治理集中管理，用户管理只承载单用户操作”的信息架构：

| 能力 | 主界面 | 用户管理中的表现 |
| --- | --- | --- |
| 全局 AI 开关 | `/admin/ai` 页面顶部 | 只显示单用户最终生效状态 |
| 全局默认每日额度 | `/admin/ai` 页面顶部 | 只显示该用户继承或覆盖后的有效额度 |
| 全体用户 Token/成本 | `/admin/ai` 的“用量与成本” | 不复制完整分析表 |
| 模型价格 | `/admin/ai` 的“模型定价” | 不提供价格编辑入口 |
| 单用户暂停和额度覆盖 | `/admin/users` 用户详情 | 作为唯一编辑入口 |
| 单用户用量 | `/admin/ai?userId={id}` | 用户详情只展示摘要和跳转链接 |

进一步固定以下交互原则：

- `/admin/ai` 是阶段二的主工作区，不新增通用“系统设置”页面。
- 管理员概览在阶段四只读展示全局 AI 状态和摘要，不复制全局开关的写入口。
- 用户列表不增加 Token、成本等时间相关列，也不让 `identity` 模块依赖 `ai-governance`。
- 用户详情通过独立 AI API 并行加载策略与用量，不能把跨模块字段塞入 `AdminUserDetailResponse`。
- 用户详情支持 `?userId={id}` 深链接，便于从 AI 用量表回到单用户控制面。

### 1.3 术语口径

前后端文案和 API 字段必须明确区分以下概念：

- **AI 入口请求**：用户主动发起的一次受治理业务请求；每日硬额度按此计数。
- **实际模型调用**：一次真实 provider dispatch；一个 Agent run 可能产生多次模型调用。
- **Token 用量**：模型返回的 input、cached、output、reasoning 和 total usage；只用于观测。
- **估算成本**：按当前启用价格实时回算；不是 provider 实际账单。
- **用户暂停**：阻止该用户后续受治理 AI 调用；不能绕过全局关闭。
- **额度覆盖**：覆盖全局默认的每日 AI 入口请求上限；不修改历史 request count。

页面不得把“实际模型调用数”写成“请求额度使用量”，也不得把 Token 或美元金额表现为硬预算。

### 1.4 本阶段不做

- 不做 Token、美元或单模型的硬预算。
- 不做按用户允许/禁止指定模型的路由策略。
- 不做模型价格版本、历史账单快照或汇率换算。
- 不做用户今日用量清零。
- 不做管理员手工取消、重试或解锁 run。
- 不做阶段三的 run 列表和完整 trace 页面。
- 不做阶段四的管理员概览、反馈信箱和主动告警。

---

## 2. 当前代码基线

### 2.1 已经存在的阶段二基座

以下内容已经提交，不应重复创建：

- `V29__ai_runtime_policy_and_model_price.sql` 已创建：
  - `ai_runtime_settings`
  - `ai_user_policy`
  - `ai_model_price`
  - `ai_llm_call_usage`
- `AuthPermission` 和前端 `AuthPermission` 已包含 `ai-governance:manage`。
- `/api/admin/**` 已由 Spring Security 统一限制为 `ROLE_ADMIN`。
- `AdminAuditAction` 已包含全局设置、用户策略和模型价格操作类型。
- `AiRunAdmissionService` 已实现静态 purpose 策略、共享每日请求额度和用户级运行锁。
- `AiRunGovernanceObserver` 已聚合 Agent run 的 provider、model 和 Token 到 `ai_run_admissions`。
- `AiDailyUsageStore` 已通过条件 `UPDATE` 原子消费每日请求额度。
- 前端已存在用户管理、权限导航、API 封装、i18n 和管理员确认弹窗模式。

### 2.2 当前缺口

阶段二实现尚缺少：

- V29 表对应的 Java model、repository、mapper XML 和自动配置。
- 数据库动态策略读取及 admission 接入。
- 全局设置、用户策略、价格和用量的管理员 API。
- 调用级 Token 写入逻辑。
- Agent step 的稳定 `stepIndex` 记账 metadata。
- 三个直接 `LlmGateway.complete(...)` 调用入口的准入和记账包装。
- 历史 `ai_run_admissions` Token 到调用级台账的回填。
- 当前价格成本计算、未定价拆分和聚合查询。
- `/admin/ai` 页面及用户详情的 AI 控制区域。

### 2.3 迁移规则

`V29` 已经提交并可能在环境中执行，阶段二禁止修改其内容。所有补充约束、索引和历史回填统一新增：

```text
backend/ai-governance/src/main/resources/db/migration/ai/
  V32__ai_usage_accounting_hardening.sql
```

开始编码前必须再次扫描全仓 Flyway 版本；如果 `V32` 已被占用，只顺延新迁移，不修改 V29-V31。

---

## 3. 最终产品形态

### 3.1 `/admin/ai` 页面结构

页面采用一个治理工作区，不拆成多个一级导航：

```text
AI 治理
├── 运行策略区
│   ├── 全局 AI 状态 + toggle
│   ├── 默认每日 AI 入口请求上限 + 编辑操作
│   └── 最近更新时间/更新人
├── Tab：用量与成本（默认）
│   ├── 日期、用户、模型、场景筛选
│   ├── 汇总指标带
│   ├── 维度切换：按用户 / 按模型 / 按场景
│   └── 聚合表格
└── Tab：模型定价
    ├── 未定价模型警告
    ├── 当前价格表
    └── 创建/编辑价格对话框
```

运行策略区始终位于页面顶部，不放入 Tab，保证止损入口可见。

### 3.2 运行策略区

全局 AI 开关使用 toggle，但点击后先显示确认对话框：

- 关闭文案明确说明：下一次用户 AI 准入将被拒绝。
- 开启文案明确说明：仍会继续执行用户暂停、每日额度和静态 purpose 策略。
- 保存期间禁用 toggle；失败时保留原状态并显示 API 错误。
- 不做前端乐观更新，后端成功后再刷新状态。

默认每日额度采用“展示值 + 编辑按钮”，进入编辑态后使用数字输入：

- 字段名称固定为“默认每日 AI 入口请求上限”。
- 第一版允许范围为 `1-10000`，前后端共享同一业务约束。
- 调低额度不修改当天已用次数；如果已用次数超过新上限，下一次请求直接拒绝。

### 3.3 用量与成本 Tab

默认查询当天，快捷范围为今天、近 7 天、近 30 天，并支持最多 90 天的自定义区间。

汇总指标至少包含：

- 已准入的用户 AI 入口请求数。
- 实际模型调用数。
- 输入 Token。
- 缓存输入 Token。
- 输出 Token。
- 总 Token。
- 已定价估算成本。
- 未定价调用数和未定价 Token。

维度表行为：

- 默认“按用户”，便于内测管理员直接发现高消耗或接近额度的用户。
- “按模型”展示 provider、精确 model ID、调用数、Token、估算成本和是否已定价。
- “按场景”按 `AiRunSource` 聚合，不以宽泛的 `AiPurpose` 替代业务场景。
- 按用户行提供“查看用户”和“仅看该用户”操作。
- “查看用户”进入 `/admin/users?userId={id}`。
- “仅看该用户”保留在 `/admin/ai` 并更新 URL 查询参数。
- 表格必须区分已定价与未定价，不能把两者相加为一个看似完整的金额。

### 3.4 模型定价 Tab

价格表字段：

- provider 规范化 ID。
- provider 返回的精确 model ID。
- 非缓存输入价格，单位 `USD / 1M tokens`。
- 缓存输入价格，单位 `USD / 1M tokens`。
- 输出价格，单位 `USD / 1M tokens`。
- 成本倍率。
- 启用状态。
- 最近更新时间和更新人。

交互规则：

- 未定价但已经出现调用的模型置顶显示。
- “配置价格”会预填 provider 和 model，避免手工拼写错误。
- provider 保存前转为小写；model 只 trim，不改变大小写。
- 单价允许为 0，倍率必须大于 0。
- 不提供物理删除，只允许停用。
- 更新价格后提示“历史区间会按当前价格重新估算”。
- 金额和单价输入、API 传输都使用十进制字符串，避免 JavaScript 浮点误差。

### 3.5 `/admin/users` 用户详情

阶段二不在用户列表增加 Token/成本列。点击用户后，详情区域增加“AI 使用与控制”：

- 有效 AI 状态。
- 状态来源：全局关闭、用户暂停或正常启用。
- `aiEnabledOverride` 当前值：继承或暂停。
- 默认额度、用户覆盖值和最终有效额度。
- 今日入口请求数，例如 `6 / 20`。
- 今日 Token 和估算成本。
- 近 7 天、近 30 天估算成本。
- 用户暂停 toggle。
- 每日额度覆盖输入。
- “恢复继承”操作。
- “查看完整用量”链接到 `/admin/ai?userId={id}`。

现有表格下方的内联详情改为页面级右侧抽屉；移动端使用占满内容区的详情层。抽屉通过 `?userId={id}` 驱动，刷新和浏览器前进/后退后仍能恢复选中用户。

用户基础信息、AI 策略和用量摘要独立加载。某个 AI 请求失败时，不影响用户基础信息与密码运维操作。

---

## 4. 技术架构

### 4.1 模块职责

| 模块 | 阶段二职责 |
| --- | --- |
| `common` | 管理员审计动作、目标类型和受控 metadata key |
| `llm-core` | 保持 provider 无感；只补充必要的通用 metadata/stream 包装支持，不依赖治理模块 |
| `agent-core` | 把稳定 step index 放入最终 LLM request metadata |
| `ai-governance` | 动态策略、调用级记账、价格、成本计算和管理员查询核心逻辑 |
| `mentor-application` | 使用治理 completion 包装接入代码 Review；题目复习保持纯 FSRS 路径 |
| `mentor-api` | 组装记账网关、暴露管理员 API、处理认证操作者和错误响应 |
| `identity` | 继续拥有用户基础信息；不依赖 `ai-governance` |
| `frontend` | `/admin/ai` 工作区和用户详情 AI 区域 |

不新增 Maven 模块，也不让 `llm-core`、`identity` 反向依赖 `ai-governance`。

### 4.2 动态策略解析

每次用户入口 admission 按以下顺序处理：

1. 解析现有静态 `AiPurposePolicy`。
2. 检查部署级 `algo-mentor.ai-governance.enabled` 硬开关。
3. 查询 `ai_runtime_settings`。
4. 检查数据库全局 AI 开关。
5. 校验登录态并取得 user ID。
6. 查询 `ai_user_policy`。
7. 检查用户暂停状态。
8. 执行 adminOnly、请求大小等静态 purpose 校验。
9. 计算有效每日额度。
10. 使用现有 `tryConsumeRequest(...)` 原子消费一次共享入口额度。
11. 获取用户级 run lock，写入 admission 并继续原流程。

有效策略公式：

```text
effectiveAiEnabled = deployment.enabled
                     AND global.aiEnabled
                     AND purpose.enabled
                     AND user.aiEnabledOverride != false

effectiveDailyLimit = user.dailyRequestLimitOverride
                      ?? global.defaultDailyRequestLimit
```

约束：

- 用户覆盖只允许 `NULL` 或 `FALSE`；第一版不需要保存 `TRUE`，因为它不能绕过全局关闭。
- PATCH 收到 `aiEnabledOverride=true` 时规范化为 `NULL`，前端也只提供“继承/暂停”两态。
- 用户两项覆盖均为 `NULL` 时删除 `ai_user_policy` 行，避免保存无意义记录。
- 数据库设置行缺失时使用当前静态 purpose 的 daily limit 作为兼容兜底并记录 error 日志。
- 数据库查询异常时 AI admission 失败关闭，不回退为“默认启用”，避免数据库故障期间失去止损能力。
- 第一版每次 admission 直接查数据库，不加本地缓存。

### 4.3 调用级记账架构

调用级记账不能继续只依赖 `AiRunGovernanceObserver`，因为 observer 看不到业务服务直接发起的 provider 调用。阶段二采用两层结构：

```text
业务准入/上下文包装
        │
        ▼
AiAccountingLlmGateway（统一包裹真实 LlmGateway）
        │
        ├── 写 RUNNING 调用记录
        ├── 调用 provider
        ├── 捕获 provider/model/usage/error/cancel
        └── 写终态并累计 ai_daily_usage Token
```

职责拆分：

- `AiGovernedCompletionService`：决定是否消费入口额度、是否只检查开关、如何关联父 run，并补齐受信 metadata。
- `AiAccountingLlmGateway`：对每次真实 `complete` 或 `stream` dispatch 生成唯一 `callId` 并写调用级台账。
- `AiRunGovernanceObserver`：继续维护 run 状态和 run 聚合字段，供阶段三排障；不作为成本查询来源。
- `AiLlmCallAccountingService`：持久化调用终态、更新调用级指标，并把 Token 累计到对应 `ai_daily_usage` scope。

### 4.4 调用入口映射

| 调用入口 | Purpose | Source | Call kind | 入口额度 | 开关检查 | run 关联 |
| --- | --- | --- | --- | --- | --- | --- |
| Agent 每个模型 step | 继承 admission | 继承 admission | `AGENT_STEP` | run 入口只消费一次 | admission 时 | 当前 runId + stepIndex |
| 代码 Review 工具子调用 | 继承父 run，默认 `LEARNING_CHAT` | `PRACTICE_CODE_REVIEW` | `DIRECT` | 不重复消费 | 子调用前重新检查全局/用户开关 | 父 runId + 当前 stepIndex |

实现细节：

- `AiRunSource` 增加 `PRACTICE_CODE_REVIEW`。
- `AgentLlmRequestFactory` 在最终 request metadata 写入通用 `AgentRuntimeMetadataKeys.STEP_INDEX`。
- 代码 Review 工具从 `AgentExecutionContext` 取得父 `runId`、`stepIndex` 和 admission metadata，不接受模型提供的用户或 run 标识。
- 题目复习不创建 AI run；用户直接评级是 FSRS 的唯一调度输入。
- provider 在 usage 返回前失败仍保存 FAILED 调用，Token 为 0，provider/model 尽可能从 `LlmException` 获取。

### 4.5 `ai_daily_usage` 的唯一写入职责

当前 `AiRunLifecycleService` 在 run 结束时把聚合 Token 写入 `ai_daily_usage`。阶段二必须调整为：

- `AiRunLifecycleService` 只更新 `ai_run_admissions` 聚合和释放 run lock。
- 每个真实 provider 调用终止时，由 `AiLlmCallAccountingService` 调用一次 `AiDailyUsageStore.addUsage(...)`。
- Agent step 使用 `ALL` scope。
- 代码 Review 子调用使用父 admission 的 `ALL` scope，但不增加 request count。

这样可以同时避免 Agent run 聚合重复累计，并覆盖父 run observer 看不到的代码 Review 子调用。

### 4.6 complete 与 stream 记账规则

非流式 `complete(...)`：

1. 从 request metadata 提取受信调用上下文。
2. 插入 RUNNING 行。
3. 调用 delegate gateway。
4. 成功时使用 result 的 provider、model 和 usage 更新为 COMPLETED。
5. 抛出异常时映射 provider、model、errorCode，更新为 FAILED 后原样抛出。

流式 `stream(...)`：

1. 返回包装后的 `Flow.Publisher`，真正订阅时才创建一次调用记录。
2. `MessageStart` 捕获 provider/model。
3. `Usage` 保存最后一次 provider usage 快照；若 provider 发出增量 usage，先在 provider adapter 层确认语义，禁止盲目重复相加。
4. `onComplete` 更新 COMPLETED。
5. `onError` 或 `LlmStreamEvent.Error` 更新 FAILED。
6. subscriber 调用 `cancel()` 时更新 CANCELLED。
7. 使用原子终态标记，保证 complete/error/cancel 竞争时只结算一次。

调用记录失败不应吞掉模型响应，但必须记录 error 日志和 `ai_accounting_persist_failures_total`。管理员设置、用户策略和价格写入失败则正常失败返回，不能 best effort。

### 4.7 缺少记账上下文

`AiAccountingLlmGateway` 不得因为调用方漏传 metadata 而静默丢失调用：

- 仍记录 provider、model 和 Token。
- `userId`、`runId` 可为 `NULL`。
- `purpose`、`source` 记录稳定字面值 `UNKNOWN`。
- `callKind` 在存在 stepIndex 时回退为 `AGENT_STEP`，否则回退为 `DIRECT`，保证仍满足 V29 CHECK。
- 增加 `ai_accounting_missing_context_total` 计数和 warning 日志。
- 阶段二交付前使用源码扫描和测试确保生产代码中的现有 `LlmGateway` 调用都有受信上下文；`UNKNOWN` 只作为最后防线。

### 4.8 成本计算

```text
uncachedInputTokens = max(inputTokens - cachedTokens, 0)

estimatedCostUsd = (
    uncachedInputTokens * inputPricePerMillion
  + cachedTokens * cachedInputPricePerMillion
  + outputTokens * outputPricePerMillion
) / 1_000_000 * costMultiplier
```

固定规则：

- Java 使用 `BigDecimal`，数据库使用 Numeric，不使用 `double`。
- Java 计算中间过程不提前舍入，最终成本使用 scale 8、`HALF_UP`。
- 成本公式只在 `AiCostCalculator` 维护一份；SQL 负责按展示维度和匹配到的价格元组聚合 Token，service 再调用 calculator 合并成本，禁止在多个 mapper 中复制公式。
- `reasoningTokens` 默认已包含在 `outputTokens` 中，不重复计费。
- cached 大于 input 时，非缓存输入按 0 计算。
- 只有匹配到启用价格的调用才进入 `pricedCostUsd`。
- 缺少 provider/model、价格缺失或价格停用均进入未定价统计。
- 未定价调用数和未定价 Token 单独返回，不把成本写成 0 后混入总额。
- 价格匹配使用规范化小写 provider 和精确 model ID。

---

## 5. API 契约

### 5.1 路径常量

新增统一常量类：

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/ai/
  AdminAiApiContractConstants.java
```

固定路径：

```text
GET   /api/admin/ai/settings
PATCH /api/admin/ai/settings

GET   /api/admin/users/{userId}/ai-policy
PATCH /api/admin/users/{userId}/ai-policy

GET   /api/admin/ai/model-prices
POST  /api/admin/ai/model-prices
PATCH /api/admin/ai/model-prices/{priceId}

GET   /api/admin/ai/usage/summary
GET   /api/admin/ai/usage/by-user
GET   /api/admin/ai/usage/by-model
GET   /api/admin/ai/usage/by-source
```

总体设计中的 `by-purpose` 在阶段二页面中细化为 `by-source`，因为产品维度是业务场景；`purpose` 仍保留为查询过滤字段和响应字段。

### 5.2 全局设置

响应示例：

```json
{
  "aiEnabled": true,
  "defaultDailyRequestLimit": 50,
  "updatedBy": 1,
  "updatedByDisplayName": "Admin",
  "updatedAt": "2026-07-14T10:00:00Z"
}
```

PATCH 请求每次提交完整可编辑状态：

```json
{
  "aiEnabled": false,
  "defaultDailyRequestLimit": 50
}
```

校验：

- `aiEnabled` 必填。
- `defaultDailyRequestLimit` 必填且范围 `1-10000`。
- operator ID 只从 `Authentication` 解析。
- 成功和失败都按现有审计模式记录低敏事件。

### 5.3 用户策略

GET 响应：

```json
{
  "userId": 42,
  "globalAiEnabled": true,
  "aiEnabledOverride": null,
  "effectiveAiEnabled": true,
  "effectiveDisabledReason": null,
  "globalDefaultDailyRequestLimit": 50,
  "dailyRequestLimitOverride": 20,
  "effectiveDailyRequestLimit": 20,
  "updatedBy": 1,
  "updatedByDisplayName": "Admin",
  "updatedAt": "2026-07-14T10:00:00Z"
}
```

PATCH 请求提交完整覆盖状态，两个字段都允许 JSON `null`：

```json
{
  "aiEnabledOverride": false,
  "dailyRequestLimitOverride": 20
}
```

规则：

- `aiEnabledOverride=true` 规范化为 `null`。
- `dailyRequestLimitOverride` 为 `null` 表示继承，非空范围 `1-10000`。
- 两项均为 `null` 时删除覆盖行。
- 目标用户不存在或已软删除时拒绝更新。
- 禁用用户可以预先修改策略，但只有账号恢复后才会进入 AI admission。

### 5.4 模型价格

所有价格和成本字段通过十进制字符串传输：

```json
{
  "id": 7,
  "provider": "openai",
  "model": "gpt-5.2",
  "currency": "USD",
  "inputPricePerMillion": "1.25000000",
  "cachedInputPricePerMillion": "0.12500000",
  "outputPricePerMillion": "10.00000000",
  "costMultiplier": "1.000000",
  "enabled": true,
  "updatedBy": 1,
  "updatedByDisplayName": "Admin",
  "createdAt": "2026-07-14T10:00:00Z",
  "updatedAt": "2026-07-14T10:00:00Z"
}
```

`GET /model-prices` 响应同时包含：

- `items`：已配置价格。
- `unpricedModels`：查询区间内实际出现但没有启用价格的 provider/model、调用数、Token 和最后出现时间。

创建和更新校验：

- provider、model 非空并满足 V29 长度限制。
- provider 统一转小写。
- 三类价格大于等于 0，scale 不超过 8。
- multiplier 大于 0，scale 不超过 6。
- currency 固定为 USD，不接受客户端修改为其他值。
- POST 遇到已存在 `(provider, model)` 返回 409，前端引导编辑原记录。
- PATCH 不允许修改记录 ID；provider/model 如允许编辑，仍受唯一键约束。

### 5.5 用量查询

通用查询参数：

```text
from=2026-07-14
to=2026-07-14
userId=42
provider=openai
model=gpt-5.2
purpose=LEARNING_CHAT
source=PRACTICE_CHAT
```

日期为闭区间，后端按 `AiGovernanceProperties.quotaZone` 转换为：

```text
[from.atStartOfDay, to.plusDays(1).atStartOfDay)
```

规则：

- 不传日期时默认当天。
- `from` 不得晚于 `to`。
- 最大区间 90 天。
- provider 过滤转小写；model 精确匹配。
- `by-user` 支持 `page`、`pageSize`，默认 20，最大 100。
- `by-model`、`by-source` 第一版最多返回 200 行，并按总 Token 降序。

汇总响应至少包含：

```json
{
  "from": "2026-07-14",
  "to": "2026-07-14",
  "quotaZone": "UTC",
  "admittedEntryRequestCount": 8,
  "modelCallCount": 21,
  "inputTokens": 120000,
  "cachedTokens": 40000,
  "outputTokens": 16000,
  "reasoningTokens": 8000,
  "totalTokens": 136000,
  "pricedCallCount": 19,
  "pricedTokenCount": 130000,
  "estimatedCostUsd": "0.84250000",
  "unpricedCallCount": 2,
  "unpricedTokenCount": 6000
}
```

按用户响应额外包含：

- user ID、email、display name、账号状态。
- 区间内模型调用与 Token/成本。
- 今日入口请求数。
- 当前有效每日额度。
- 当前有效 AI 状态。
- 未定价调用和 Token。

`admittedEntryRequestCount` 来自 admission/每日额度数据，模型调用与 Token/成本只来自 `ai_llm_call_usage`，两者不能使用同一张表猜测。

### 5.6 错误码

阶段二至少补齐并本地化：

```text
AI_GLOBALLY_DISABLED
AI_USER_DISABLED
AI_RUNTIME_SETTINGS_INVALID
AI_USER_POLICY_INVALID
AI_MODEL_PRICE_NOT_FOUND
AI_MODEL_PRICE_ALREADY_EXISTS
AI_MODEL_PRICE_INVALID
AI_USAGE_DATE_RANGE_INVALID
AI_USAGE_QUERY_INVALID
```

前端只能按错误码决定特殊交互，不解析错误消息文本。

HTTP 状态固定为：

- `AI_GLOBALLY_DISABLED` -> 503。
- `AI_USER_DISABLED` -> 403。
- `AI_MODEL_PRICE_NOT_FOUND` -> 404。
- `AI_MODEL_PRICE_ALREADY_EXISTS` -> 409。
- 其余阶段二输入和查询校验错误 -> 400。

---

## 6. V32 数据迁移

### 6.1 迁移内容

`V32__ai_usage_accounting_hardening.sql` 完成以下工作：

- 为五类 Token 字段增加非负 CHECK。
- 为 `step_index` 增加正数或 NULL 的 CHECK。
- 规范化已有 `ai_model_price.provider` 为小写；迁移前检测规范化后是否冲突。
- 为 `ai_model_price.provider = lower(provider)` 增加 CHECK。
- 增加 `(source, started_at DESC)` 索引。
- 增加 `(started_at DESC)` 索引，支持全局日期区间查询。
- 为历史 `ai_run_admissions` 中 Token 非零且尚未回填的记录插入一条 `LEGACY_RUN_AGGREGATE`。

历史 call ID 使用确定性格式：

```text
legacy-run-{ai_run_admissions.id}
```

回填字段：

- `run_id`、`user_id`、`purpose`、`source`、provider/model 和 Token 原样复制。
- `step_index=NULL`。
- `started_at=coalesce(started_at, created_at)`。
- `completed_at=coalesce(completed_at, updated_at, created_at)`。
- COMPLETED 映射 COMPLETED，CANCELLED 映射 CANCELLED，其余含 Token 的终态映射 FAILED。
- 使用 `ON CONFLICT (call_id) DO NOTHING` 保证幂等意图清晰。

### 6.2 迁移验收

- V29 文件 checksum 不变。
- V32 可以在仅有 V10/V29 的数据库上执行。
- 同一个 admission 不会生成多条 legacy 记录。
- 新版本成本查询不读取 `ai_run_admissions` Token，避免 legacy 与新调用重复。
- 回滚应用版本时 V32 新增约束和索引不影响旧代码写入。

---

## 7. 详细实施任务

### Task 0：固定阶段二契约并追加 V32

**目标：** 先固定公共枚举、metadata、API 路径和不可修改的数据库补丁，使后续实现并行时不反复改契约。

**主要文件：**

- Create: `backend/ai-governance/src/main/resources/db/migration/ai/V32__ai_usage_accounting_hardening.sql`
- Modify: `backend/ai-governance/.../model/AiGovernanceErrorCode.java`
- Modify: `backend/ai-governance/.../model/AiGovernanceMetadataKeys.java`
- Modify: `backend/ai-governance/.../model/AiRunSource.java`
- Create: `backend/ai-governance/.../accounting/AiLlmCallKind.java`
- Create: `backend/ai-governance/.../accounting/AiLlmCallStatus.java`
- Modify: `backend/agent-core/.../runtime/model/AgentRuntimeMetadataKeys.java`
- Modify: `backend/common/.../admin/audit/AdminAuditMetadataKey.java`
- Create: `backend/mentor-api/.../controller/admin/ai/AdminAiApiContractConstants.java`
- Modify: 中英文 API error resources
- Modify: migration resource tests

**实施步骤：**

- [ ] 扫描全仓迁移版本并确认 V32 可用。
- [ ] 添加 V32 约束、索引和 legacy backfill。
- [ ] 增加三个稳定业务 source。
- [ ] 增加 call kind、call status 和调用 metadata key。
- [ ] 增加 Agent 通用 step index metadata key。
- [ ] 增加全局/用户关闭和管理员 API 错误码及中英文消息。
- [ ] 增加审计 metadata key：全局状态、默认额度、用户覆盖、provider、model、价格启停。
- [ ] 固定 API 路径，禁止 controller 内散落字符串。

**测试：**

- `AiGovernanceMigrationResourceTest` 验证 V32 存在、包含 backfill 和非负约束。
- `InternalBetaMigrationResourceTest` 验证 V28-V32 版本不冲突。
- enum/model 单测验证新增稳定值。

**完成标准：** 后续任务所需数据库字段、路径、状态和错误码不再变化。

### Task 1：实现数据库动态策略与管理员写服务

**目标：** 全局开关、默认额度和用户覆盖可以持久化、查询、审计并计算有效策略。

**主要文件：**

- Create: `backend/ai-governance/.../policy/runtime/AiRuntimeSettings.java`
- Create: `backend/ai-governance/.../policy/runtime/AiUserPolicy.java`
- Create: `backend/ai-governance/.../policy/runtime/EffectiveAiRuntimePolicy.java`
- Create: `backend/ai-governance/.../policy/runtime/AiRuntimePolicyService.java`
- Create: `backend/ai-governance/.../policy/runtime/AiRuntimeAdminService.java`
- Create: `backend/ai-governance/.../repository/mybatis/AiRuntimeSettingsMapper.java`
- Create: `backend/ai-governance/.../repository/mybatis/AiUserPolicyMapper.java`
- Create: corresponding row records and mapper XML
- Modify: `AiGovernanceAutoConfiguration.java`
- Modify: `AdminAuditMetadataKey.java`

**实施步骤：**

- [ ] 实现 singleton settings 查询和更新。
- [ ] 实现 user policy 查询、upsert 和两项为空时删除。
- [ ] 使用 identity repository 校验目标用户存在且未删除。
- [ ] 实现有效状态、有效额度和 disabled reason 解析。
- [ ] 数据库行缺失走静态兜底；数据库异常失败关闭。
- [ ] 全局和用户更新写管理员审计，审计失败沿用现有 best-effort 语义。
- [ ] 为 mapper 和 service 注册条件 bean，保持无数据库测试上下文可启动。

**关键测试：**

- 全局启用 + 用户继承 -> 启用。
- 全局关闭 + 用户任意覆盖 -> 关闭，reason 为 GLOBAL。
- 全局启用 + 用户暂停 -> 关闭，reason 为 USER。
- 用户额度为空 -> 使用全局默认。
- 用户额度覆盖 -> 使用覆盖值。
- 两项恢复继承 -> 删除覆盖行。
- 设置行缺失 -> 静态兜底并记录日志路径。
- mapper XML 可解析，自动配置在依赖存在/缺失时均符合预期。

**完成标准：** service 层可以不依赖 HTTP 返回任意用户的有效动态策略，并能完成三类管理员写操作审计。

### Task 2：把动态策略接入 admission

**目标：** 数据库开关和额度在下一次用户入口准入时立即生效。

**主要文件：**

- Modify: `AiRunAdmissionService.java`
- Modify: `AiRunAdmissionException.java`（如需补充 metadata）
- Modify: `AiRunLifecycleService.java`
- Modify: `AiGovernanceAutoConfiguration.java`
- Modify: `AiRunAdmissionServiceTest.java`
- Modify: API exception mapping tests

**实施步骤：**

- [ ] 注入 `AiRuntimePolicyService`。
- [ ] 按 4.2 顺序区分部署关闭、全局关闭、用户暂停和 purpose 关闭。
- [ ] 用 effective daily limit 调用现有原子 `tryConsumeRequest`。
- [ ] metadata 中的 `aiDailyLimit` 写最终有效额度。
- [ ] 保留 `properties.enabled` 作为部署级硬熔断。
- [ ] 新增 `AI_GLOBALLY_DISABLED`、`AI_USER_DISABLED` 的 HTTP 与本地化映射。
- [ ] 从 `AiRunLifecycleService` 移除 run 结束时的 `addUsage`，为调用级唯一累计做准备。

**关键测试：**

- 修改全局开关后下一次 admission 被拒绝/恢复。
- 用户暂停只影响目标用户。
- 用户不能绕过全局关闭。
- 调低额度后历史 request count 不变，下一次调用按新上限拒绝。
- 提高额度后无需重启即可继续。
- 并发消费仍只有 limit 数量成功。
- run 完成只更新 admission，不再重复累计 daily Token。

**完成标准：** 所有现有 Agent 入口都自动受动态开关和有效额度控制，且原有锁与并发语义不回归。

### Task 3：实现调用级台账与记账网关

**目标：** 每次真实 provider dispatch 只写一条调用记录，并准确处理同步、流式、失败和取消。

**主要文件：**

- Create: `backend/ai-governance/.../accounting/AiLlmCallContext.java`
- Create: `backend/ai-governance/.../accounting/AiLlmCallUsage.java`
- Create: `backend/ai-governance/.../accounting/AiLlmCallAccountingService.java`
- Create: `backend/ai-governance/.../accounting/AiAccountingLlmGateway.java`
- Create: `backend/ai-governance/.../repository/mybatis/AiLlmCallUsageMapper.java`
- Create: corresponding row/update records
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiLlmCallUsageMapper.xml`
- Modify: `AiGovernanceAutoConfiguration.java`
- Modify: `MentorAiConfiguration.java`
- Modify: `AgentLlmRequestFactory.java`
- Modify: `AiGovernanceMapperXmlTest.java`

**实施步骤：**

- [ ] 定义从 request metadata 提取受信调用上下文的单一解析器。
- [ ] 插入 RUNNING，使用条件终态 UPDATE 防止重复结算。
- [ ] 实现 complete success/failure 记账。
- [ ] 实现 stream subscribe、usage、complete、error、cancel 记账。
- [ ] 终态成功后按 call context 的 user/date/scope 累计一次 daily Token。
- [ ] 持久化失败不影响 provider 响应，但输出 error 日志和低基数指标。
- [ ] 缺少上下文时记录 UNKNOWN，而非静默跳过。
- [ ] 在 `MentorAiConfiguration` 中先创建真实 gateway delegate，再包装为应用注入的 `LlmGateway`。
- [ ] Agent request 写入 stepIndex，使每个 step 可单独追踪。
- [ ] 保留现有 run observer 聚合，但成本查询永远不读取其 Token。

**关键测试：**

- complete 成功记录一次 COMPLETED 和完整 usage。
- complete 失败记录一次 FAILED，原异常继续抛出。
- stream 完成、错误、主动取消各只结算一次。
- MessageStart 前失败仍产生零 Token 失败记录。
- 多个 Agent step 产生多个 `AGENT_STEP` 行和不同 stepIndex。
- daily Token 每个调用只累计一次，run end 不重复累计。
- 记账数据库异常不吞掉模型结果。
- 缺少 metadata 产生 UNKNOWN 记录和计数。

**完成标准：** 使用 fake provider 的单元测试可以证明 provider 调用数与 `ai_llm_call_usage` 新增行数一一对应。

### Task 4：实现受治理的直接 completion 并迁移代码 Review 入口

**目标：** 直接调用不再裸用 `LlmGateway`，并按入口语义正确处理额度、开关和父 run。

**主要文件：**

- Create: `backend/ai-governance/.../completion/AiGovernedCompletionService.java`
- Create: `backend/ai-governance/.../completion/AiCompletionContext.java`
- Create: `backend/ai-governance/.../completion/AiCompletionMode.java`
- Modify: `PracticeCodeReviewService.java`
- Modify: `PracticeCodeReviewAgentTool.java`
- Modify: `PracticeTurnContext.java`
- Modify: `AgentConversationApiAutoConfiguration.java`
- Modify: corresponding unit tests

**实施步骤：**

- [ ] completion service 提供独立用户入口和父 run 子调用两种明确模式。
- [ ] 独立用户入口创建 admission、标记生命周期并消费一次 `ALL` 请求额度。
- [ ] 独立用户入口在 provider 调用前 markRunning，成功/失败后更新 run 聚合并保证释放 lock；daily Token 仍只由记账 service 累计。
- [ ] 父 run 子调用不创建新 admission、不获取第二把锁、不增加 request count，但重新检查全局/用户开关。
- [ ] 代码 Review 从 trusted tool context 传父 runId、stepIndex、userId，不从模型参数读取。
- [ ] 代码 Review request metadata 写入明确 source、call kind、quota scope。
- [ ] 题目复习保持用户直接评级 + FSRS 路径，不接入 completion service。
- [ ] 删除生产代码中的对应裸 `llmGateway.complete(...)`。

**关键测试：**

- 代码 Review 子调用关联父 run，调用入账但共享 request count 不增加。
- 父 run 已准入后全局关闭，后续代码 Review 子调用不再 dispatch。
- 代码 Review 成功、provider 失败、结构化输出失败均有正确调用终态。
- 题目复习评级不会产生 AI 调用或用量记录。

**源码门禁：**

```bash
rg -n "llmGateway\.(complete|stream)" \
  backend/mentor-application/src/main/java \
  --glob '!**/target/**'
```

结果中不得再出现代码 Review 业务服务的裸调用。

**完成标准：** Agent 和代码 Review 调用均有可区分的 source，且额度不会重复消费；题目复习不产生 AI 调用。

### Task 5：实现价格、成本和管理员查询 API

**目标：** 管理员 API 可以管理当前模型价格，并返回前端所需的精确用量与成本数据。

**主要文件：**

- Create: `backend/ai-governance/.../pricing/AiModelPrice.java`
- Create: `backend/ai-governance/.../pricing/AiCostEstimate.java`
- Create: `backend/ai-governance/.../pricing/AiCostCalculator.java`
- Create: `backend/ai-governance/.../pricing/AiModelPriceAdminService.java`
- Create: `backend/ai-governance/.../adminquery/AiUsageQuery.java`
- Create: `backend/ai-governance/.../adminquery/AiUsageSummary.java`
- Create: `backend/ai-governance/.../adminquery/AiUsageByUserRow.java`
- Create: `backend/ai-governance/.../adminquery/AiUsageByModelRow.java`
- Create: `backend/ai-governance/.../adminquery/AiUsageBySourceRow.java`
- Create: `backend/ai-governance/.../adminquery/AiAdminUsageQueryService.java`
- Create: `AiModelPriceMapper.java`、`AiAdminUsageMapper.java` and XML
- Create: `AdminAiSettingsController.java`
- Create: `AdminUserAiPolicyController.java`
- Create: `AdminAiModelPriceController.java`
- Create: `AdminAiUsageController.java`
- Create: request/response DTO and admin AI exception handler
- Modify: mapper registration and auto-configuration

**实施步骤：**

- [ ] 实现 BigDecimal 成本计算器和固定舍入。
- [ ] 实现模型价格 create/update/enable/disable 和审计。
- [ ] 价格查询同时返回 observed unpriced models。
- [ ] 用量 SQL 只从 `ai_llm_call_usage` 聚合调用和 Token。
- [ ] 使用 `LEFT JOIN ai_model_price ... AND enabled=true` 拆分 priced/unpriced，并按展示维度 + 价格元组聚合 Token。
- [ ] service 使用唯一的 `AiCostCalculator` 计算各价格元组成本并汇总，mapper 不复制成本公式。
- [ ] 按用户查询补充当前有效策略、今日 request count 和用户基础摘要。
- [ ] summary 单独统计 admitted entry request，不用 call count 代替。
- [ ] 实现日期区间、用户、provider、model、purpose、source 校验。
- [ ] 金额和价格 DTO 使用 decimal string；响应不得出现“实际账单”字段名。
- [ ] controller 只处理认证和 DTO 映射，不直接拼 SQL 或成本公式。

**关键测试：**

- 纯输入、缓存输入、输出、倍率和混合成本。
- cached 大于 input 时不产生负成本。
- reasoning 不重复计费。
- 大 Token 数保持 Decimal 精度。
- 调价后历史查询金额变化。
- 价格停用后调用进入未定价统计。
- 未定价调用不进入 priced cost。
- provider 规范化、model 精确匹配。
- 90 天边界、反向日期、超大 pageSize 被正确处理。
- controller 的管理员、非管理员、CSRF 和本地化错误响应。

**完成标准：** 不启动前端也能通过 API 完成设置、单用户策略、价格和四类用量查询闭环。

### Task 6：实现 `/admin/ai` 治理工作区

**目标：** 管理员可以在独立页面完成全局止损、用量分析和模型定价。

**主要文件：**

- Create: `frontend/src/admin/ai/AiGovernancePage.tsx`
- Create: `frontend/src/admin/ai/AiRuntimePolicyBar.tsx`
- Create: `frontend/src/admin/ai/AiUsagePanel.tsx`
- Create: `frontend/src/admin/ai/AiModelPricingPanel.tsx`
- Create: `frontend/src/admin/ai/AiModelPriceDialog.tsx`
- Create: corresponding tests
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 新增 `/admin/ai`、`adminAi` view 和 `ai-governance:manage` 路由门禁。
- [ ] 管理员导航加入“AI 治理”，位置在“用户管理”之后、“AI Debug”之前。
- [ ] 顶部实现全局状态、默认额度和更新时间。
- [ ] toggle 关闭/开启均使用确认对话框。
- [ ] 用量 Tab 默认按用户和当天加载。
- [ ] 日期快捷项、维度 segmented control 和筛选项同步 URL。
- [ ] 汇总指标明确分开入口请求与模型调用。
- [ ] 未定价调用使用警告状态，不显示为 `$0`。
- [ ] 模型价格使用 number input 的交互外观，但内部保存原始 decimal string。
- [ ] 创建、编辑、停用操作完成后刷新价格和当前用量成本。
- [ ] 使用 lucide 图标表示刷新、编辑、警告和跳转；不手绘 SVG。
- [ ] 表格使用稳定列宽和横向滚动，加载状态不引发布局跳动。
- [ ] 所有新增文案同步中英文。

**关键前端测试：**

- 无权限时路由归一化，不渲染页面和导航项。
- 全局开关确认前不发 PATCH；成功后刷新状态。
- 默认额度范围校验和失败回滚。
- 用量默认区间、筛选 URL 和过期响应防护。
- 入口请求数与模型调用数使用不同标签。
- 未定价 warning、价格预填和停用确认。
- 价格 decimal string 不经浮点运算后再提交。
- 金额区域始终出现“按当前价格估算”。

**完成标准：** `/admin/ai` 可以独立完成阶段二全局治理，不需要进入用户管理页配置全局能力或价格。

### Task 7：实现用户详情 AI 控制与跨页面联动

**目标：** 用户管理只承担单用户控制和摘要，并能与 AI 治理页双向跳转。

**主要文件：**

- Modify: `frontend/src/admin/UserManagementPage.tsx`
- Create: `frontend/src/admin/users/AdminUserDetailDrawer.tsx`
- Create: `frontend/src/admin/users/AdminUserAiSection.tsx`
- Modify/Create: corresponding tests
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/App.tsx` navigation callback plumbing
- Modify: i18n and styles

**实施步骤：**

- [ ] 用 `?userId=` 驱动用户详情选择和浏览器历史。
- [ ] 将现有内联详情改为右侧抽屉，不建设通用抽屉框架。
- [ ] 打开抽屉后并行加载 identity detail、AI policy、今日/7日/30日用量。
- [ ] 基础信息和 AI 区域分别展示 loading/error，不互相阻塞。
- [ ] 用户暂停使用 toggle + 确认弹窗。
- [ ] 额度覆盖支持数字输入和“恢复继承”。
- [ ] 始终展示全局默认、覆盖值和最终有效值，避免管理员误判。
- [ ] 今日用量使用“入口请求 x / effective limit”，Token/成本作为次要摘要。
- [ ] “查看完整用量”进入 `/admin/ai?userId={id}`。
- [ ] AI 治理按用户表的“查看用户”进入 `/admin/users?userId={id}`。
- [ ] 用户列表本身不新增 Token/成本列，不发逐行策略请求。

**关键前端测试：**

- URL 中有 userId 时自动打开正确用户。
- 快速切换用户时旧响应不能覆盖新详情。
- 用户详情基础请求成功、AI 请求失败时仍可执行密码运维。
- 暂停确认、额度覆盖、恢复继承的请求体正确。
- 全局关闭时用户 toggle 不表现为可绕过全局。
- 两个页面的深链接保留 userId。
- 抽屉在窄视口不遮挡关闭按钮和操作区。

**完成标准：** 用户管理与 AI 治理解耦，但管理员从任一页面都能用一次跳转到另一侧的相关上下文。

### Task 8：集成验证、smoke、文档和发布门禁

**目标：** 用真实认证、PostgreSQL 和可控模型响应验证阶段二端到端语义。

**主要文件：**

- Create: `tests/smoke/suites/admin/ai_governance.hurl`
- Modify: smoke suite documentation/config if required
- Modify: `docs/code-index.md`
- Modify: API/运维文档
- Modify: `.env.example`，仅在新增必要 fallback 时更新

**自动验证：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance,mentor-application,mentor-api -am test

make frontend-test
make build
```

**smoke 场景：**

- [ ] 非管理员访问所有新增 `/api/admin/**` 返回 403。
- [ ] 管理员关闭全局 AI，下一次用户入口返回 `AI_GLOBALLY_DISABLED`。
- [ ] 管理员重新开启后，无需重启即可恢复。
- [ ] 用户达到额度后被拒绝；管理员提高覆盖额度后下一次请求恢复。
- [ ] 用户暂停只影响目标用户。
- [ ] 配置价格前已有调用显示未定价。
- [ ] 配置价格后历史 Token 得到当前价格估算成本。
- [ ] 停用价格后重新显示未定价，而不是 `$0`。
- [ ] 多步 Agent run 产生多条 `AGENT_STEP`。
- [ ] 代码 Review 子调用关联父 run 且不增加入口 request count。
- [ ] 题目复习路径不会写入调用级 Token 台账。
- [ ] 全局关闭后复习卡不再调用 provider，也不先消费后台额度。

依赖真实 provider 的 smoke 只在测试 key/本地 fake provider 可用时运行；设置、策略、价格和查询 API 的 smoke 不应依赖外部 AI。

**安全复核：**

- [ ] 日志、异常、审计不包含 prompt、response、Authorization、Cookie 或 API key。
- [ ] Token、价格和金额不作为 Micrometer tag。
- [ ] operator ID 不接受客户端输入。
- [ ] 所有管理员写操作有 CSRF 测试。
- [ ] 用户策略目标用户由路径参数决定。
- [ ] 价格和成本使用 Decimal，前端不做浮点成本计算。
- [ ] 调用记账失败日志只包含低敏 ID 和错误类型。

**完成标准：** 阶段二功能、数据准确性、安全和页面交互全部满足第 10 节 Definition of Done。

---

## 8. 推荐提交顺序

按以下顺序提交，每个提交包含对应最小测试：

```text
feat: harden AI usage accounting schema and contracts
feat: add dynamic AI runtime policies
feat: account every LLM provider call
feat: govern direct AI completion flows
feat: add AI pricing and usage admin APIs
feat: add AI governance admin workspace
feat: add per-user AI controls
test: add AI governance smoke coverage
docs: document internal beta AI governance operations
```

硬依赖关系：

```text
Task 0
  -> Task 1 -> Task 2
  -> Task 3 -> Task 4
  -> Task 5
  -> Task 6 -> Task 7
  -> Task 8
```

Task 1/2 与 Task 3 的内部实现可以在契约固定后分别开发，但 Task 4 必须等动态策略和记账网关都稳定后再接入。

---

## 9. 发布与回滚

### 9.1 发布前数据检查

- `ai_runtime_settings(id=1)` 存在，初始 `ai_enabled=true`。
- `default_daily_request_limit` 已按内测预期确认。
- 当前真实 provider/model 已录入价格，或管理员明确接受未定价状态。
- V32 legacy backfill 数量与历史非零 Token admission 数量一致。
- 不存在规范化后重复的 provider/model 价格。

### 9.2 发布顺序

1. 执行 V32 和后端兼容代码。
2. 验证动态策略 GET/PATCH API。
3. 验证 fake provider 下调用级记账与未定价查询。
4. 发布 `/admin/ai` 和用户详情前端。
5. 配置实际模型价格。
6. 使用测试用户验证全局关闭、用户暂停和额度提高。
7. 完成阶段二 smoke 后再邀请新增测试者。

### 9.3 回滚

- 新表、约束和索引保留，不做 down migration。
- 前端故障可隐藏 `adminAi` 导航，不影响普通学习页面。
- 价格错误可停用记录；不会影响 admission。
- 动态策略实现故障时，先通过数据库关闭全局 AI，再回滚应用。
- 回滚到不读取数据库开关的旧应用前，必须同时设置部署级 `algo-mentor.ai-governance.enabled=false` 或关闭 provider，防止旧版本忽略数据库止损状态后重新发起调用。
- 记账装饰器故障回滚后，已写调用记录保留；成本查询仍只读取调用级表。

---

## 10. Definition of Done

### 10.1 功能

- [ ] 管理员可以实时开关全局 AI。
- [ ] 管理员可以修改全局默认每日入口额度。
- [ ] 管理员可以暂停单用户或覆盖其额度。
- [ ] 用户不能通过覆盖绕过全局关闭。
- [ ] 管理员可以按用户、模型和场景查看 Token 与估算成本。
- [ ] 管理员可以创建、编辑和停用模型价格。
- [ ] 未定价调用有明确警告且不混入成本总额。

### 10.2 数据准确性

- [ ] 每次 provider dispatch 恰好对应一条调用记录。
- [ ] Agent 多 step 不合并成单条成本记录。
- [ ] 代码 Review 子调用不重复消费入口额度。
- [ ] 题目复习评级不消费 AI 入口额度。
- [ ] 复习卡保留专用额度并受动态开关控制。
- [ ] daily Token 不再被 run aggregate 重复累计。
- [ ] reasoning Token 不重复计费。
- [ ] 历史 legacy 回填与新调用不会重复计费。

### 10.3 产品与交互

- [ ] 全局治理和价格只在 `/admin/ai` 编辑。
- [ ] 用户管理只显示单用户摘要和控制。
- [ ] 两个页面通过 userId 深链接互通。
- [ ] 页面明确区分入口请求和模型调用。
- [ ] 所有金额标注“按当前价格估算”。
- [ ] 桌面和移动视口不存在文本、表格操作和抽屉控件重叠。

### 10.4 安全与运维

- [ ] 所有新增管理员 API 只允许 `ROLE_ADMIN`。
- [ ] 所有写操作受 CSRF 保护并记录低敏审计。
- [ ] 日志和审计不包含模型输入输出或凭据。
- [ ] 动态策略数据库异常时 AI 失败关闭。
- [ ] 记账持久化异常有日志和指标，但不篡改 provider 响应。
- [ ] `make frontend-test`、相关 Maven 测试和 `make build` 全部通过。
