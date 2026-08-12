# 学习计划 AI 修订灰度访问策略设计

## 1. 文档信息

- 设计日期：2026-08-12
- 状态：已实施
- 依赖：通用策略底座、用户组关系缓存、学习计划草案/扩展提案链路
- 关联文档：
  - `docs/generic-policy-foundation-design.md`
  - `docs/learning-plan-creation-governance-design.md`
  - `docs/learning-plan-revision-plan-compiler-design.md`

## 2. 背景与目标

学习计划目前有三条会实际发起 AI 修订或计划变更提案的链路：

| 业务链路 | 当前入口 | 当前实现语义 |
| --- | --- | --- |
| 模板草案 AI 修订 | `POST /api/learning-plans/drafts/{draftId}/revisions/stream` | 对从模板生成、尚未确认的草案生成完整修订草案 |
| 已保存计划 AI 修订 | `POST /api/learning-plans/{planId}/extension-proposals/stream` 及其 revisions 路径 | 生成并修订扩展提案；当前只追加新阶段，不直接改写既有阶段 |
| AI 个性化草案 AI 修订 | `POST /api/learning-plans/drafts/{draftId}/revisions/stream` | 对通过“AI 个性化生成”入口创建、尚未确认的草案生成完整修订草案 |

前两条链路仍处于稳定性观察期，需要按全部用户、指定用户或用户组逐步开放。目标是复用现有通用策略底座，提供一套由前端和后端共同消费的访问决策：

1. 前端只向获准用户展示对应 AI 操作入口。
2. 后端在每次 AI 调用前重新校验，不信任前端隐藏结果。
3. 三项能力可在一条策略中组合配置，并沿用通用策略的全局优先级、用户组和缓存失效语义。
4. 关闭能力只阻止新的 AI 工作；不破坏用户已经得到的草案、扩展提案及其确认/应用/丢弃操作。

## 3. 范围与非目标

### 3.1 本次范围

- 新增学习计划 AI 修订访问策略类型、强类型内容、运行时解析器和管理页面。
- 为学习计划草案增加不可变、显式持久化的创建来源。
- 新增当前登录用户的学习计划 AI 修订能力快照接口。
- 对草案修订、扩展提案首次生成和扩展提案再次修订三个 AI 发起入口实施后端门禁。
- 在学习计划创建页和详情页按能力快照隐藏 AI 操作。
- 增加策略、来源迁移、后端门禁与前端显示的测试和观测。

### 3.2 非目标

- 不灰度“AI 个性化生成”草案本身；该入口和表单中的 `personalizationEnabled` 开关保持原有行为。
- 不引入按百分比、时间窗、设备、标签或表达式计算的灰度规则；复用通用策略已有的全部用户、用户和用户组范围。
- 不把策略内容直接暴露给普通用户，也不让前端直接调用通用策略原始 JSON 接口。
- 不改变草案修订、扩展提案的 Agent、SSE、提案版本或 Plan Compiler 业务语义。
- 不在策略关闭时撤销、删除或隐藏已存在的草案、扩展提案或正式计划。

## 4. 已定决策

### 4.1 一个策略类型，三项完整布尔能力

新增稳定 `typeCode`：

```text
learning-plan.ai-revision-access.v1
```

策略内容必须完整包含三个布尔字段：

```json
{
  "templateDraftRevisionEnabled": false,
  "savedPlanRevisionEnabled": false,
  "personalizedDraftRevisionEnabled": true
}
```

字段定义：

| 字段 | 控制范围 |
| --- | --- |
| `templateDraftRevisionEnabled` | 模板草案的 AI 修订 |
| `savedPlanRevisionEnabled` | 正式计划的扩展提案首次生成和已存在扩展提案的 AI 再修订 |
| `personalizedDraftRevisionEnabled` | 从“AI 个性化生成”入口产生的草案 AI 修订 |

`savedPlanRevisionEnabled` 沿用产品层“已保存计划 AI 修订”的称谓，但其当前后端实现仍是“扩展提案”，不会修改正式计划已有阶段。该能力关闭时，已存在扩展提案仍可应用或丢弃，只是不允许再生成或 AI 修订提案。

通用策略只命中优先级最高的一条策略，**不会按字段合并多条策略**。因此管理页每次新建或编辑都必须提交三项完整布尔值；例如，一个指定用户策略若只想开启模板修订，也仍须显式填写其余两项为 `false` 或 `true`。

新建策略及未命中策略时，`personalizedDraftRevisionEnabled` 默认值为 `true`，模板草案和已保存计划修订仍默认关闭。策略解析失败仍按失败关闭规则将三个能力处理为 `false`。

### 4.2 草案来源是数据库事实，不依赖 metadata 推断

新增领域枚举 `LearningPlanDraftSource`：

```text
TEMPLATE
AI_PERSONALIZED
```

- `TEMPLATE`：通过模板草案创建接口创建。
- `AI_PERSONALIZED`：通过当前“AI 个性化生成”入口创建。即使用户在表单中关闭 `personalizationEnabled`，来源仍为 `AI_PERSONALIZED`；后者只控制该次 AI 运行是否读取学习者聚合数据，不能用来判断创建链路。

新增 `learning_plan_draft.draft_source` 列，且在迁移完成后为 `NOT NULL`。该列是访问判定的唯一权威来源。已有 `draftPlan.metadata.draftSource` 继续作为计划内容的兼容性元数据保留，但不得用于运行时授权判断。

### 4.3 前端能力快照与后端访问控制分离

新增当前用户专用接口：

```text
GET /api/learning-plans/ai-revision-capabilities
```

成功响应只包含可展示能力，不返回命中的策略 ID、优先级、范围或原始策略内容：

```json
{
  "templateDraftRevisionEnabled": false,
  "savedPlanRevisionEnabled": false,
  "personalizedDraftRevisionEnabled": true
}
```

该接口通过同一应用层解析器读取强类型策略；响应设置 `Cache-Control: no-store`，前端不写入本地持久化缓存。接口读取失败返回 `503`，前端按三个能力均为 `false` 处理，不阻断非 AI 的学习计划浏览、模板创建、草案确认、提案应用和提案丢弃。

能力快照只服务展示。所有 AI 发起接口必须在后端再次使用同一解析器校验，且禁用或策略解析失败时在创建 Agent run、提案组和提案修订记录之前停止。

### 4.4 失败关闭与现有数据可继续收尾

| 情况 | 前端 | 后端 |
| --- | --- | --- |
| 未命中策略 | 仅显示 AI 个性化草案修订入口 | 仅允许 AI 个性化草案修订 |
| 命中但对应字段为 `false` | 隐藏该条链路的入口 | 拒绝新的 AI 调用 |
| 策略缓存、关系查询、反序列化或校验失败 | 关闭全部入口 | 返回 `503`，不执行 AI |
| 用户已得到草案或扩展提案后策略关闭 | 隐藏后续 AI 修订/生成入口 | 允许确认草案、应用提案和丢弃提案；拒绝后续 AI 调用 |

新增稳定错误码：

| 错误码 | HTTP 状态 | 含义 |
| --- | --- | --- |
| `LEARNING_PLAN_AI_REVISION_NOT_ENABLED` | `403 Forbidden` | 当前用户未获准使用所请求的 AI 修订能力 |
| `LEARNING_PLAN_AI_REVISION_POLICY_UNAVAILABLE` | `503 Service Unavailable` | 策略无法可靠解析，采用失败关闭 |

SSE 接口在创建 `SseEmitter` 前完成首次校验，使常规拒绝表现为 HTTP `403/503`。应用服务在创建提案组或修订记录的事务内再次校验，防止其他调用方绕过 controller，也缩小前后两次判定间策略变化的窗口。

## 5. 架构与调用链

### 5.1 策略解析

```text
管理员策略写入
  -> GenericPolicyManagementService
  -> 通用策略缓存失效事件

当前用户能力快照 / AI 请求
  -> LearningPlanAiRevisionAccessService
  -> GenericPolicyQueryService.resolve(learning-plan.ai-revision-access.v1, userId)
  -> 命中策略内容或代码默认值（模板/已保存计划关闭，个性化草案开启）
  -> LearningPlanAiRevisionCapabilities
```

`LearningPlanAiRevisionAccessService` 放在 `mentor-application` 的 `learningplan.policy` 包中，只理解三个能力和业务动作。其策略适配器放在 `mentor-api`，按现有 `PolicyBackedLearningPlanCreationPolicyResolver` 的模式调用通用策略底座。策略类型注册为 `INTERNAL_ONLY`，普通用户不得通过 `/api/policies/{typeCode}/effective` 获取原始 JSON。

推荐领域结构：

```text
LearningPlanAiRevisionPolicy
LearningPlanAiRevisionCapabilities
LearningPlanAiRevisionAction
LearningPlanAiRevisionPolicyResolver
LearningPlanAiRevisionAccessService
LearningPlanAiRevisionPolicyConstants
```

其中 `LearningPlanAiRevisionAction` 固定为：

```text
TEMPLATE_DRAFT_REVISION
SAVED_PLAN_REVISION
PERSONALIZED_DRAFT_REVISION
```

### 5.2 后端门禁位置

| HTTP 入口 | 判定动作 | 事务内二次校验位置 |
| --- | --- | --- |
| `POST /drafts/{draftId}/revisions/stream` | 根据锁定草案 `draft_source` 判定模板或 AI 个性化草案修订 | `LearningPlanDraftRevisionStreamService.createSubscriptionRevision`，在创建提案组前 |
| `POST /{planId}/extension-proposals/stream` | 已保存计划 AI 修订 | `LearningPlanExtensionProposalStreamService.createFirstRevision`，在创建提案组前 |
| `POST /{planId}/extension-proposals/{proposalGroupId}/revisions/stream` | 已保存计划 AI 修订 | `LearningPlanExtensionProposalStreamService.createNextRevision`，在创建修订记录前 |

草案来源查询必须与草案锁定读取使用同一行事实。控制器的预检只用于产生正确 HTTP 状态；事务内检查才是阻止副作用的最终边界。

### 5.3 前端消费链

```text
LearningPlans 路由进入或切换
  -> GET ai-revision-capabilities
  -> 失败或加载中：三项能力均按 false
  -> LearningPlanCreatePage / LearningPlanDetail
       -> 仅渲染允许的 AI 控件
  -> 用户操作仍由后端重新授权
```

能力快照由 `LearningPlans` 统一加载并向创建页、详情页传递，不在每个按钮各自请求。路由在学习计划列表、创建、详情之间切换时重新读取；不要求实时推送管理员策略变更。即使浏览器仍持有上一份快照，后端重新校验仍是最终事实。

## 6. 数据、API 与前端契约

### 6.1 数据库迁移

新增下一版本 Flyway 迁移，按以下顺序执行：

1. 为 `learning_plan_draft` 新增可空 `draft_source VARCHAR(32)`。
2. 回填已有数据：当 `draft_plan_json #>> '{metadata,draftSource}' = 'TEMPLATE'` 时写入 `TEMPLATE`；其余已有草案写入 `AI_PERSONALIZED`。
3. 将该列改为 `NOT NULL`，增加只允许 `TEMPLATE`、`AI_PERSONALIZED` 的 `CHECK` 约束。本次授权读取均按草案主键完成，不预置无消费索引；未来出现按来源批量查询需求时再评估索引。
4. 不修改已有草案、草案修订、正式计划或扩展提案内容，也不重放任何 AI 运行。

回填采用保守分类：现有模板服务已写入 `metadata.draftSource=TEMPLATE`，其余历史草案全部归为 AI 个性化草案，避免将未确认来源的数据误放入模板链路。

### 6.2 草案 DTO 与响应

`LearningPlanDraft`、MyBatis 行模型、mapper 和 `LearningPlanDraftResult` 增加 `source`。两个创建路径显式写入来源：

- `LearningPlanTemplateDraftService` 写入 `TEMPLATE`。
- `LearningPlanDraftStreamService` 写入 `AI_PERSONALIZED`，并同步写入既有 metadata 的 `draftSource` 以维持内容投影一致。

`LearningPlanDraftResponse` 和前端 `LearningPlanDraftResponse` 增加：

```json
{
  "source": "TEMPLATE"
}
```

草案修订 SSE 的 `draft_ready`、`draft_revision_ready` 继续复用此响应，因此无需新增事件名。前端不得根据 `assistantMessage`、模板 metadata 或 `personalizationEnabled` 推断来源。

### 6.3 策略管理 API 与页面

后端继续复用通用策略管理 API：

```text
GET/POST/PATCH/DELETE /api/admin/policies
POST /api/admin/policy-types/{typeCode}/order
```

新增管理员路径：

```text
/admin/learning-plan-ai-revision-policies
```

页面复用现有学习计划创建治理策略页的范围选择、状态、优先级移动、删除确认和写入冲突处理，改为管理 `learning-plan.ai-revision-access.v1`。编辑表单使用三个语义明确的开关，不使用自由 JSON 编辑器：

- 模板草案 AI 修订
- 已保存计划 AI 修订（扩展提案）
- AI 个性化草案 AI 修订

管理员仍需 `policy:manage` 权限。通用策略已有的管理员审计记录 `typeCode`、动作和结果；本功能不额外记录用户的修订文本或计划正文。

## 7. 页面行为

### 7.1 创建页

“从模板创建”和“AI 个性化生成”两个创建模式始终保留，策略不影响初次创建。

当草案处于 `GENERATED`：

| 草案来源 | 所需能力 | UI 行为 |
| --- | --- | --- |
| `TEMPLATE` | `templateDraftRevisionEnabled` | 允许时显示“AI 修订”输入和发送按钮；否则整个修订区域不渲染，确认保存保留 |
| `AI_PERSONALIZED` | `personalizedDraftRevisionEnabled` | 允许时显示“AI 修订”输入和发送按钮；否则整个修订区域不渲染，确认保存保留 |

草案收集追问、重试创建和确认不是本策略管辖范围，保持原样。

### 7.2 已保存计划详情页

`savedPlanRevisionEnabled=true` 时保持现有扩展面板：输入扩展要求、生成扩展、对 pending proposal 再修订、应用和丢弃。

该能力为 `false` 时：

- 没有 pending proposal：不渲染扩展 AI 面板。
- 有 pending proposal：不渲染“生成扩展”与“修订扩展”输入/按钮；保留“应用”和“丢弃”。

这样策略收紧不会使用户无法处理既有业务结果。

### 7.3 加载和错误状态

能力尚未加载、请求失败或返回无效数据时使用全关闭快照。页面不展示“无权限”错误或占位文案，避免暴露内部灰度配置；只有用户手工构造请求时才会获得标准 `403/503` API 错误。

## 8. 校验、缓存、并发与观测

### 8.1 内容校验

`LearningPlanAiRevisionPolicyContentValidator` 必须拒绝：

- 非对象 JSON；
- 缺少任一字段或包含未知字段；
- 非 JSON 布尔值、Jackson 宽松类型转换后的值；
- `null`。

这与创建治理策略的“完整字段、无宽松转换”约束保持一致。

### 8.2 缓存与一致性

策略命中、用户组关系和失效传播完全复用通用策略底座，不新增业务缓存。管理员写入、启停、删除或排序成功后按现有机制失效对应 `typeCode` 的缓存；跨节点最终一致性和 TTL 上限沿用底座定义。

前端快照可能在管理员改策略后短暂陈旧，因此它只能降低无效点击，不能承担安全责任。服务端每次请求和事务内二次检查均按当前可解析策略做出决定。

### 8.3 指标与日志

新增低基数 Micrometer 计数器：

```text
algo.mentor.learning_plan.ai_revision.access_checks
```

标签固定为：

- `action`：三个 `LearningPlanAiRevisionAction` 枚举值；
- `outcome`：`ALLOWED`、`DENIED`、`POLICY_UNAVAILABLE`。

不使用用户 ID、草案 ID、计划 ID、输入文本或策略内容作为指标标签。策略解析失败记录包含 `typeCode` 和异常堆栈、但不打印完整 JSON 的 `ERROR` 日志；正常拒绝只记录计数器，不增加高噪声日志。

AI 门禁拒绝发生在 Agent run 之前，因此不会产生伪造的 LLM 调用、Token 或 Agent run 成功/失败记录。

## 9. 实施拆分

1. 在应用层新增策略内容、能力、动作、resolver、访问服务、错误码和指标端口；在 API 模块注册强类型 `GenericPolicyType` 与策略适配器。
2. 编写 Flyway 迁移，扩展草案领域模型、MyBatis 映射、repository、创建服务和草案响应/SSE 映射，完成来源的显式写入与历史回填。
3. 新增当前用户能力 controller 与前端 service/type，接入 `LearningPlans` 的能力加载和失败关闭状态。
4. 将 controller 预检和事务内二次校验接入三条 AI 发起链路，补齐 `403/503` 映射。
5. 实现管理员策略页面、路由、导航、i18n 和三个开关的表单校验。
6. 接入创建页、草案面板和计划详情扩展面板，确保现有结果的确认、应用、丢弃不受策略关闭影响。
7. 补齐测试、运行针对性构建门禁，并在内测环境先创建仅覆盖内部测试账号的高优先级策略。

## 10. 测试与验收

### 10.1 后端

- 策略内容仅接受三个完整布尔字段；未命中得到模板/已保存计划为 `false`、AI 个性化草案为 `true`。
- 用户、用户组、全部用户和优先级覆盖由通用策略集成验证；策略解析失败映射为 `POLICY_UNAVAILABLE`。
- 模板草案与 AI 个性化草案在两个创建路径中写入正确来源；历史回填和 `CHECK` 约束通过迁移资源测试。
- 当前用户能力接口不返回原始策略内容，未命中全关，策略失败为 `503`。
- 模板草案修订被拒绝时不创建提案组、提案修订或 Agent run；允许时保持既有路径。
- AI 个性化草案修订同样覆盖允许和拒绝分支。
- 扩展提案首次生成与再次修订均被 `savedPlanRevisionEnabled` 控制；关闭后已有 pending proposal 仍可 apply/discard。
- controller 预检返回 `403/503`，应用服务二次校验能拦截直接调用服务或策略在预检后变更的情况。
- 指标不含高基数或隐私标签。

### 10.2 前端

- 能力加载中和请求失败时三个 AI 修订入口均不出现。
- 模板草案、AI 个性化草案分别只响应自己的布尔能力；初次 AI 生成入口不受影响。
- 已保存计划在允许时显示完整扩展面板；关闭后 pending proposal 仍显示 apply/discard，且不显示新的 AI 生成或修订控件。
- 能力接口和草案 `source` 的 TypeScript 解析、请求失败处理及现有 SSE 草案更新覆盖测试。
- 管理页能创建、编辑、启停、排序和删除完整三布尔策略，并复用用户/用户组范围选择。

### 10.3 验收标准

- 没有策略时，普通用户仅看到并能使用 AI 个性化草案修订入口；模板草案和已保存计划修订直接调用得到 `403`，且没有新的 AI 副作用。
- 为单个用户开启其中一项能力后，仅该用户仅看到并能执行对应链路；其余两项保持关闭。
- 高优先级策略可以完整覆盖低优先级策略，行为符合通用策略单条命中语义。
- 管理员关闭一项能力后，后端在策略失效传播完成后拒绝新的相关 AI 调用；历史草案和 pending proposal 可以正常收尾。

## 11. 发布与回滚

初始代码默认全关闭，数据库中不需要预置全员策略。内测发布顺序：

1. 部署迁移和应用代码，确认无策略时所有 AI 修订入口隐藏。
2. 通过管理员页面为内部测试账号或测试组创建高优先级、最小范围策略。
3. 按链路逐项打开，观察 AI 运行、业务错误和新增访问检查指标。
4. 发现质量、稳定性或成本异常时，在管理页关闭对应字段或禁用策略；后端拒绝后续 AI 调用，已有结果仍可收尾。

回滚以策略关闭为首选，不需要删除历史提案或回滚数据库迁移。`draft_source` 是向前兼容的事实字段，发布后保留；代码回退前必须确认旧版本能够忽略该新增列。
