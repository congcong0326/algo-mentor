# 内测管理员能力阶段四：反馈信箱与管理员概览详细实施计划

> 上位设计：`docs/internal-beta-admin-capabilities-design.md`
>
> 总体计划：`docs/internal-beta-admin-capabilities-implementation-plan.md`
>
> 本文只细化总体计划中的 Task 12 和 Task 13；不包含 Task 14，不安排 Hurl smoke、文档补齐或最终安全复核任务。
>
> 基线日期：2026-07-14；基线提交：`6867a36`。

---

## 1. 交付目标与固定决策

### 1.1 阶段四交付结果

阶段四完成后，应形成两个可独立使用、又能互相跳转的业务闭环：

1. 用户可以从普通用户界面发起反馈，查看自己的历史反馈，读取管理员回复并继续回复。
2. 管理员可以筛选待处理反馈、读取上下文、回复、关闭或重开会话。
3. 用户在已关闭会话中再次回复时，会话自动重开。
4. 用户和管理员都能看到对方未读消息数量，读取动作通过显式 API 完成。
5. 管理员登录后默认进入 `/admin`，首先看到准入、AI 消耗、额度风险、反馈待办和最近失败 run。
6. 概览中的异常项可以进入白名单、用户、AI 治理、AI run 或反馈详情。
7. 用户详情继续保持跨模块并行查询，不把 AI、反馈或白名单字段塞入 `identity` 的用户 DTO。

### 1.2 已确认的产品边界

| 能力 | 本阶段实现 | 本阶段不实现 |
| --- | --- | --- |
| 会话发起 | 只能由普通用户创建 thread | 管理员主动私信、群发 |
| 消息能力 | 纯文本双向回复，单条 1-4000 字符 | 附件、富文本、编辑、删除 |
| 处理状态 | `OPEN`、`CLOSED`，用户回复自动重开 | 优先级、负责人、SLA、工单流转 |
| 上下文 | 当前页面路径、最近 request ID、当前已知 run ID | 上传日志、自动收集完整请求体、浏览器环境画像 |
| 通知 | 页面内未读徽标和列表未读状态 | 邮件、短信、Webhook、外部告警 |
| 管理员概览 | 可行动的当天状态和待办 | DAU、留存、漏斗、收入预测、复杂图表 |
| AI run 联动 | 复用阶段三的 run 查询与详情路由 | 在阶段四重复实现 trace、清理、run 管理动作 |
| 最终联调 | 各任务内的单元、接口、Mapper 和前端测试 | 总体计划 Task 14 的 smoke、文档和最终安全复核 |

### 1.3 术语与统计口径

- **thread**：由用户创建的一条反馈会话，对应 `user_feedback_thread`。
- **message**：thread 内的一条用户或管理员消息，对应 `user_feedback_message`。
- **对方未读**：当前查看者收到、且 `read_at IS NULL` 的对方消息；自己的消息不计入自己的未读数。
- **读取 thread**：先读取详情，再显式调用 `/read`；GET 请求自身不修改数据库。
- **关闭**：管理员把 thread 置为 `CLOSED`，记录 `closed_at` 和 `closed_by`。
- **自动重开**：用户向 `CLOSED` thread 回复时，在同一事务内改回 `OPEN` 并清空关闭字段。
- **入口请求**：`ai_run_admissions` 中当天创建且 `user_id IS NOT NULL` 的用户 AI 入口尝试，包含成功、失败、取消和拒绝。
- **模型调用**：`ai_llm_call_usage` 中实际 dispatch 到 provider 的调用，不与入口请求混用。
- **接近额度**：`requestCount / effectiveDailyRequestLimit >= 0.8`；阈值由后端常量统一管理。
- **概览区块不可用**：某个数据源查询失败。API 返回该区块 `available=false`，不能伪装成零值。

### 1.4 本阶段不做

- 不包含总体计划 Task 14。
- 不新增反馈表或修改已提交的 `V30__admin_audit_and_user_feedback.sql`。
- 不建设管理员主动创建 thread 的 API 或 UI。
- 不实现附件、截图上传、消息编辑、消息删除、批量关闭或批量回复。
- 不实现外部通知、服务端后台轮询任务或客服分配。
- 不为每条反馈消息写管理员操作审计；消息本身就是持久化业务记录。
- 不在管理员概览展示完整反馈正文、完整 AI trace、用户代码或工具参数。
- 不在阶段四实现阶段三的 run 列表、trace 详情和 30 天清理逻辑。
- 不新增通用事件总线、通用工单框架、通用 dashboard 框架或通用抽屉框架。

---

## 2. 当前代码基线

### 2.1 已经存在的阶段四基座

当前 `6867a36` 已具备以下前置能力：

- `V30__admin_audit_and_user_feedback.sql` 已创建 `user_feedback_thread` 和 `user_feedback_message`。
- 后端 `AuthPermission` 与前端 `AuthPermission` 已包含 `admin-overview:read` 和 `feedback:manage`。
- `/api/admin/**` 已由 `ROLE_ADMIN` 作为最终安全边界，写请求沿用现有 CSRF 机制。
- `CurrentUserIdProvider` 可以从认证上下文解析普通用户 ID。
- `RequestTraceFilter` 会回显 `X-Request-Id`，前端 `apiFetch` 也会为请求生成 request ID。
- 白名单、临时密码、动态 AI 策略、调用级 Token、当前价格成本查询和用户 AI 控制已经落地。
- `BetaAccessAdminService`、`AiRuntimeAdminService`、`AiAdminUsageQueryService` 已提供可复用的模块内服务。
- `AdminUserDetailDrawer` 已按基础身份信息与 AI 区域分块加载，适合继续追加独立的支持信息区。
- 前端采用自有轻量路由，路由、搜索参数和权限归一集中在 `App.tsx` 与 `app/navigation.ts`。

### 2.2 当前缺口

- `mentor-api` 尚无反馈领域模型、Mapper、repository、事务服务、Controller 和错误处理。
- 前端尚无 `/feedback`、`/admin/feedback`、`/admin` 路由和对应页面。
- `apiFetch` 生成 request ID 后没有保存可供反馈表单复用的最近请求上下文。
- 普通用户和管理员导航都没有反馈未读徽标。
- 白名单模块缺少面向概览的总人数与已注册人数聚合读模型。
- AI 治理模块缺少当天入口状态分组和接近额度用户查询。
- 管理员概览没有区块级降级响应模型。
- 用户详情还没有白名单状态、最近失败 run 和 OPEN 反馈入口。

### 2.3 阶段三依赖门禁

当前基线尚未实现总体计划阶段三。阶段四有两处必须依赖阶段三的公开查询能力：

| 使用方 | 所需阶段三能力 | 阶段四约束 |
| --- | --- | --- |
| 创建反馈 | 按 `runId` 查询所属用户 | 只调用 `ai-governance` 暴露的 run 查询服务，不直接读取其 Mapper |
| 管理员概览 | 查询最近失败 run 摘要 | 只复用阶段三的分页/摘要查询，不复制 run SQL |
| 反馈详情 | 跳转 `/admin/ai/runs/{runId}` | 路由和权限由阶段三提供 |
| 用户详情 | 查询该用户最近失败 run | 复用阶段三的 `userId + status` 筛选 |

阶段四开始编码时，应确认阶段三至少暴露等价于以下能力的稳定端口：

```java
Optional<AiRunSummary> findRun(String runId);

AiRunPage findRuns(AiRunAdminQuery query);
```

`AiRunSummary` 至少需要 `runId`、`userId`、`status`、`purpose`、`source`、`errorCode`、时间和 `traceAvailable`。实际类名可以服从阶段三实现，但阶段四不得直接依赖 `AiRunAdmissionMapper` 或 Agent persistence SQL。

在阶段三合入前，可以先完成不带 `sourceRunId` 的反馈主链路、反馈 UI 和概览其他区块；以下能力不能标记完成：

- `sourceRunId` ownership 校验。
- 反馈到 AI run 的详情跳转。
- 管理员概览最近失败 run 区块。
- 用户详情最近失败 run 区块。

### 2.4 数据迁移规则

阶段四默认不新增 Flyway 迁移：

```text
V30 已有 thread/message 表
V31 已有诊断保留字段
V32 已有调用级用量强化
阶段四直接实现代码与查询
```

禁止修改已经提交并可能执行过的 `V30`。如果开发中发现必须增加索引或约束，只能新建全仓唯一的 `V33+` 迁移，并先重新扫描所有模块版本；该情况不属于本文默认任务。

---

## 3. 最终产品形态

### 3.1 普通用户反馈弹窗

普通用户不再有独立的“反馈”导航项或 `/feedback` 页面。右上角通用操作区提供带 Tooltip 的反馈图标；有未读管理员回复时显示红点，不显示数字。图标打开由 `App` 控制的单层弹窗，桌面端采用固定宽度会话栏加自适应详情区，移动端在列表、详情和新建表单之间切换：

```text
┌──────────────────────────────────────────────────────────────┐
│ 反馈信箱                              [新建反馈] [刷新] [关闭] │
├──────────────────┬───────────────────────────────────────────┤
│ 全部 / OPEN      │ 主题、分类、状态、更新时间                 │
│ ● 未读 反馈 A    ├───────────────────────────────────────────┤
│   已读 反馈 B    │ 用户消息                                   │
│   CLOSED 反馈 C  │                         管理员消息          │
│                  │                                           │
│                  ├───────────────────────────────────────────┤
│                  │ [回复内容........................] [发送]   │
└──────────────────┴───────────────────────────────────────────┘
```

交互约束：

- “新建反馈”在同一弹窗中切换到表单，只展示分类、可选主题和正文；当前页面、最近 request ID 和当前已知 run ID 作为可选诊断上下文随请求静默附带，不向普通用户展示，也不提供移除控件。
- thread 列表按 `updatedAt DESC, id DESC` 排序。
- 打开弹窗只加载列表，不写 `read_at`；用户选择详情后，存在管理员未读消息时才调用显式 read API。
- 用户可以回复 `CLOSED` thread，提交按钮显示“回复并重新打开”的结果语义。
- 用户只能看到自己的 thread；前端不接收或发送 userId。
- 不使用聊天气泡装饰堆叠页面，消息按清晰的发送方、时间和正文时间线展示。
- 支持关闭按钮、Escape、遮罩关闭和焦点恢复；新建后返回详情并选中新建 thread。

### 3.2 管理员 `/admin/feedback`

管理员页面以扫描和处理为主：

```text
┌──────────────────────────────────────────────────────────────┐
│ 反馈管理                                      [刷新]           │
│ [OPEN/CLOSED] [分类] [用户 ID] [仅未读]                       │
├──────────────────────────────────────────────────────────────┤
│ 未读 │ 状态 │ 分类 │ 用户 │ 主题 │ 更新时间 │ 关联 run │ 操作 │
├──────────────────────────────────────────────────────────────┤
│  2   │ OPEN │ BUG  │ ...  │ ...  │ ...      │ run-...  │ 查看 │
└──────────────────────────────────────────────────────────────┘
                                  详情抽屉 / 窄屏独立详情区域
```

交互约束：

- 筛选和选中 thread 同步到 URL，支持刷新、返回和从概览深链接进入。
- 管理员详情显示用户摘要、来源上下文、消息时间线、回复框和关闭/重开动作。
- `sourceRunId` 存在时提供进入阶段三 run 详情的链接。
- 关闭、重开、回复和 read 完成后，只刷新受影响的 thread 和顶部未读计数。
- 管理员页面没有“新建会话”入口。

### 3.3 管理员 `/admin`

概览页不做营销式 hero 或复杂图表，首屏直接呈现状态和待办：

```text
┌──────────────────────────────────────────────────────────────┐
│ 管理员概览                         数据时间 ...       [刷新]   │
├──────────────────────────────────────────────────────────────┤
│ 准入：已开启  12 人 / 已注册 9 人     AI：已开启  默认 50 次  │
├──────────────────────────────────────────────────────────────┤
│ 今日入口 34  成功 29  失败 2  取消 1  额度拒绝 2              │
│ 模型调用 61  Token ...  估算 $...  未定价调用 ...             │
├──────────────────────────────┬───────────────────────────────┤
│ 额度风险用户                 │ 反馈待办                      │
│ 用户 / 42 of 50 / 84%        │ OPEN 5 / 管理员未读 8         │
├──────────────────────────────┴───────────────────────────────┤
│ 最近失败 run：用户 / 场景 / 错误 / 时间 / [查看]             │
└──────────────────────────────────────────────────────────────┘
```

交互约束：

- 每个区块独立展示 loading、available 或 unavailable，不用零值掩盖失败。
- 金额明确标注“按当前价格估算”。
- 入口请求和实际模型调用分开展示。
- 未定价调用和 Token 使用警告样式，不显示为 `$0`。
- 所有计数、用户和 run 项都提供明确深链接。

### 3.4 用户详情扩展

现有 `AdminUserDetailDrawer` 保持以下独立区域：

```text
基础身份信息
AI 状态与额度（阶段二已有）
内测准入与支持信息（阶段四新增）
```

新增区域展示：

- 当前用户是否在白名单，以及白名单记录 ID。
- OPEN 反馈数量和进入该用户反馈筛选的链接。
- 最近失败 run 摘要和进入 run 详情/筛选列表的链接。
- AI 用量仍使用阶段二现有区域，不复制完整表格。

### 3.5 路由与导航顺序

最终路由：

```text
/admin                          管理员概览
/admin/beta-access              内测准入
/admin/users                    用户管理
/admin/ai                       AI 治理
/admin/ai/runs                  AI 调用列表（阶段三）
/admin/ai/runs/:runId           AI 调用详情（阶段三）
/admin/feedback                 反馈管理
```

管理员导航顺序：

```text
概览 -> 内测准入 -> 用户管理 -> AI 治理 -> AI 调用 -> 反馈 -> 题库 -> AI Debug
```

普通用户通过右上角反馈图标进入弹窗，未读管理员回复只以红点提示；管理员保留“反馈”导航，并显示用户消息未读数量。旧 `/feedback` 书签对普通用户规范化到首页，不自动打开弹窗。

---

## 4. 反馈领域与数据语义

### 4.1 强类型契约

后端必须使用 enum 管理数据库和 API 状态，前端使用对应联合类型：

```text
FeedbackCategory = BUG | SUGGESTION | OTHER
FeedbackStatus = OPEN | CLOSED
FeedbackSenderType = USER | ADMIN
```

共享限制集中在 `FeedbackConstraints`：

```text
subject 最大 200 字符，可空
content 1-4000 字符
sourcePath 最大 500 字符，可空
sourceRequestId 最大 128 字符，可空
sourceRunId 最大 80 字符，可空
默认 pageSize 20，最大 pageSize 100
```

正文只做以下规范化：

- 把 CRLF 统一为 LF。
- 使用 `trim()` 判断是否为空。
- 保留正文内部缩进和换行，不压缩代码片段格式。
- 超长或空白正文返回 `FEEDBACK_MESSAGE_INVALID`。

主题执行 `trim`，空字符串转为 `null`。来源路径只接受以 `/` 开头的同站路径，不接受协议、域名、hash 或请求体内容。

### 4.2 未读模型

`read_at` 表示消息接收方首次确认读取的时间。插入消息时保持 `NULL`。

| 当前查看者 | 计入未读的消息 | `/read` 更新的消息 |
| --- | --- | --- |
| 普通用户 | `sender_type='ADMIN' AND read_at IS NULL` | 当前 thread 内未读 ADMIN 消息 |
| 管理员 | `sender_type='USER' AND read_at IS NULL` | 当前 thread 内未读 USER 消息 |

约束：

- GET 列表和详情都不写 `read_at`。
- read API 幂等，多次调用返回本次实际更新数量。
- read 不更新 thread 的 `updated_at`，避免读取行为改变会话排序。
- thread 列表响应同时返回每条 thread 未读数和当前查看者全局未读总数。

### 4.3 创建 thread 事务

用户创建反馈时，在一个事务内执行：

```text
校验用户身份和输入
  -> 校验可选 sourceRunId 所属用户
  -> INSERT user_feedback_thread
  -> INSERT 第一条 USER message
  -> 返回完整 thread detail
```

任何一步失败都回滚 thread 和首条消息。Controller 不直接执行多条 Mapper 写入。

### 4.4 回复事务

用户回复：

```text
SELECT thread FOR UPDATE
  -> 校验 ownership
  -> INSERT USER message
  -> UPDATE thread.updated_at
  -> 若原状态 CLOSED：status=OPEN，closed_at=NULL，closed_by=NULL
```

管理员回复：

```text
SELECT thread FOR UPDATE
  -> INSERT ADMIN message
  -> UPDATE thread.updated_at
  -> 保持当前 OPEN/CLOSED 状态
```

管理员可以在 CLOSED thread 中补充最后一条说明；只有显式状态更新或用户新回复才改变状态。

### 4.5 状态更新事务

管理员 `PATCH status` 使用行锁并保持幂等：

- `OPEN -> CLOSED`：写入 `closed_at=now`、`closed_by=operatorUserId`、`updated_at=now`。
- `CLOSED -> OPEN`：清空 `closed_at`、`closed_by`，写入 `updated_at=now`。
- 请求状态与当前状态相同：直接返回当前状态，不重复改变时间。

状态变化不自动插入系统消息，避免引入未设计的第三种 sender type。

### 4.6 ownership 与来源 run 校验

普通用户的 thread 访问必须同时使用 `threadId` 和认证 userId 校验。Controller 不接受 body 或 query 中的 userId。

`sourceRunId` 校验规则：

1. 校验字符串长度和基础格式。
2. 通过阶段三公开查询服务读取 run summary。
3. run 不存在或 `run.userId != currentUserId` 时统一返回 `FEEDBACK_SOURCE_RUN_INVALID`。
4. 不把其他用户、provider 或 trace 内容返回给请求方。
5. thread 保存 run ID 引用，不建立数据库外键。

### 4.7 列表查询

普通用户列表支持：

```text
page, pageSize, status
```

管理员列表支持：

```text
page, pageSize, status, category, userId, unreadOnly
```

所有列表统一按 `thread.updated_at DESC, thread.id DESC` 排序。列表只返回 subject、状态、发送方、未读数、来源标识和时间，不返回完整消息正文。

---

## 5. API 契约

### 5.1 路径常量

后端路径集中到常量类，前端集中到 service 函数，禁止页面散落字符串：

```text
POST /api/feedback
GET  /api/feedback
GET  /api/feedback/{threadId}
POST /api/feedback/{threadId}/messages
POST /api/feedback/{threadId}/read

GET   /api/admin/feedback
GET   /api/admin/feedback/{threadId}
POST  /api/admin/feedback/{threadId}/messages
PATCH /api/admin/feedback/{threadId}/status
POST  /api/admin/feedback/{threadId}/read

GET /api/admin/overview
GET /api/admin/beta-access/users/{userId}
```

建议常量类：

```text
FeedbackApiContractConstants
AdminFeedbackApiContractConstants
AdminOverviewApiContractConstants
```

### 5.2 创建反馈

```json
{
  "category": "BUG",
  "subject": "练习聊天中断",
  "content": "发送代码后页面提示连接中断。",
  "sourcePath": "/learning-plans/12/phases/1/problems/two-sum/chat",
  "sourceRequestId": "a1b2c3d4e5f6",
  "sourceRunId": "run-uuid"
}
```

响应返回新建 thread 详情。`category`、`content` 必填；其他字段可空。用户 ID 只取认证上下文。

### 5.3 thread 列表

```json
{
  "items": [
    {
      "id": 41,
      "category": "BUG",
      "status": "OPEN",
      "subject": "练习聊天中断",
      "lastSenderType": "ADMIN",
      "unreadMessageCount": 1,
      "sourceRunId": "run-uuid",
      "createdAt": "2026-07-14T10:00:00Z",
      "updatedAt": "2026-07-14T10:10:00Z",
      "closedAt": null
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20,
  "unreadMessageCount": 1
}
```

管理员列表项额外包含低敏用户摘要：

```json
{
  "user": {
    "id": 12,
    "email": "tester@example.com",
    "displayName": "Tester",
    "status": "ACTIVE"
  }
}
```

### 5.4 thread 详情

```json
{
  "id": 41,
  "category": "BUG",
  "status": "OPEN",
  "subject": "练习聊天中断",
  "sourcePath": "/learning-plans/12/phases/1/problems/two-sum/chat",
  "sourceRequestId": "a1b2c3d4e5f6",
  "sourceRunId": "run-uuid",
  "createdAt": "2026-07-14T10:00:00Z",
  "updatedAt": "2026-07-14T10:10:00Z",
  "closedAt": null,
  "closedBy": null,
  "unreadMessageCount": 1,
  "messages": [
    {
      "id": 101,
      "senderType": "USER",
      "senderUserId": 12,
      "content": "发送代码后页面提示连接中断。",
      "readAt": "2026-07-14T10:04:00Z",
      "createdAt": "2026-07-14T10:00:00Z"
    }
  ]
}
```

消息按 `created_at ASC, id ASC` 返回。内测阶段先返回完整 thread 时间线，不额外建设消息分页；列表接口绝不返回正文。

### 5.5 回复、读取与状态更新

回复请求：

```json
{
  "content": "已收到，我们正在检查这次调用。"
}
```

read 响应：

```json
{
  "threadId": 41,
  "markedReadCount": 2,
  "unreadMessageCount": 0
}
```

管理员状态请求：

```json
{
  "status": "CLOSED"
}
```

回复和状态更新返回最新 thread detail，前端不需要猜测自动重开或关闭字段。

### 5.6 管理员概览响应

概览使用统一区块包装，单区块失败仍返回 HTTP 200：

```json
{
  "generatedAt": "2026-07-14T11:00:00Z",
  "quotaDate": "2026-07-14",
  "quotaZone": "UTC",
  "betaAccess": {
    "available": true,
    "data": {
      "emailAllowlistEnabled": true,
      "allowedEmailCount": 12,
      "registeredAllowedEmailCount": 9
    },
    "errorCode": null
  },
  "aiRuntime": {
    "available": true,
    "data": {
      "aiEnabled": true,
      "defaultDailyRequestLimit": 50,
      "updatedAt": "2026-07-14T09:00:00Z"
    },
    "errorCode": null
  },
  "aiToday": {
    "available": true,
    "data": {
      "entryRequests": {
        "total": 34,
        "completed": 29,
        "failed": 2,
        "cancelled": 1,
        "quotaRejected": 2,
        "inProgress": 0,
        "otherRejected": 0
      },
      "modelCallCount": 61,
      "inputTokens": 120000,
      "cachedTokens": 30000,
      "outputTokens": 24000,
      "totalTokens": 144000,
      "estimatedCostUsd": "1.23456789",
      "unpricedCallCount": 3,
      "unpricedTokenCount": 9000
    },
    "errorCode": null
  },
  "quotaRisks": {
    "available": true,
    "data": {
      "thresholdPercent": 80,
      "items": [
        {
          "userId": 12,
          "email": "tester@example.com",
          "displayName": "Tester",
          "requestCount": 42,
          "effectiveDailyRequestLimit": 50,
          "usagePercent": 84,
          "atLimit": false,
          "effectiveAiEnabled": true
        }
      ]
    },
    "errorCode": null
  },
  "feedback": {
    "available": true,
    "data": {
      "openThreadCount": 5,
      "adminUnreadMessageCount": 8
    },
    "errorCode": null
  },
  "recentFailedRuns": {
    "available": false,
    "data": null,
    "errorCode": "ADMIN_OVERVIEW_SECTION_UNAVAILABLE"
  }
}
```

区块失败时禁止返回异常消息、SQL、表名或堆栈。`estimatedCostUsd` 使用 decimal string，前端不做成本计算。

入口状态分组固定如下：

| 响应字段 | `ai_run_admissions.status` |
| --- | --- |
| `completed` | `COMPLETED` |
| `failed` | `FAILED`, `EXPIRED` |
| `cancelled` | `CANCELLED` |
| `quotaRejected` | `REJECTED_QUOTA` |
| `inProgress` | `ADMITTED`, `RUNNING` |
| `otherRejected` | 其他 `REJECTED_*` |

`total` 等于以上分组之和，避免指标无法对账。

### 5.7 错误码与 HTTP 状态

新增稳定错误码：

```text
FEEDBACK_THREAD_NOT_FOUND
FEEDBACK_THREAD_FORBIDDEN
FEEDBACK_CATEGORY_INVALID
FEEDBACK_STATUS_INVALID
FEEDBACK_MESSAGE_INVALID
FEEDBACK_SOURCE_PATH_INVALID
FEEDBACK_SOURCE_RUN_INVALID
FEEDBACK_PAGE_INVALID
ADMIN_OVERVIEW_SECTION_UNAVAILABLE
ADMIN_OVERVIEW_UNAVAILABLE
```

映射建议：

| 错误 | HTTP |
| --- | --- |
| thread 不存在 | 404 |
| thread 不属于当前用户 | 403 |
| category/status/message/source/page 不合法 | 400 |
| 管理员身份不可解析 | 403 |
| 概览整体无法构造 | 500 |

概览某个区块失败不使用 HTTP 错误，而是该区块 `available=false`。中英文错误消息加入现有 `api-errors_zh_CN.properties` 和 `api-errors_en_US.properties`。

---

## 6. 技术架构

### 6.1 模块职责

| 模块 | 阶段四职责 |
| --- | --- |
| `auth` | 白名单概览聚合、用户白名单 membership 查询 |
| `identity` | 用户基础信息查询，不接收反馈或 AI 字段 |
| `ai-governance` | 当天入口状态、模型调用/成本、额度风险、run 摘要查询 |
| `agent-persistence-postgres` | 仅由阶段三继续拥有 trace SQL；阶段四不新增直接依赖 |
| `mentor-api` | 反馈领域、反馈 SQL、管理员概览聚合、Controller 和区块降级 |
| `frontend` | 用户反馈、管理员反馈、概览、未读徽标和跨页面跳转 |

### 6.2 后端包结构

建议落位：

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback
  model/                       thread、message、query、page、enum
  repository/                  FeedbackRepository
  repository/mybatis/          Mapper、repository、row
  service/                     用户服务、管理员服务、事务执行器、错误码
  metrics/                     no-op 与 Micrometer adapter

backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/feedback
  FeedbackController
  FeedbackApiContractConstants
  FeedbackExceptionHandler
  model/                       用户 API request/response

backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/feedback
  AdminFeedbackController
  AdminFeedbackApiContractConstants
  model/                       管理员 API request/response

backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/overview
  AdminOverviewService
  AdminOverviewSection

backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/overview
  AdminOverviewController
  AdminOverviewApiContractConstants
  model/                       overview response DTO
```

反馈配置独立放在 `FeedbackConfiguration`，通过 `SqlSessionTemplate` 注册 Mapper、repository、service、Clock 和 metrics，避免继续扩大 `MentorApiMyBatisConfiguration`。

### 6.3 前端结构

```text
frontend/src/feedback
  UserFeedbackDialog.tsx
  UserFeedbackDialog.test.tsx
  FeedbackThreadList.tsx
  FeedbackThreadTimeline.tsx
  FeedbackComposer.tsx
  feedbackSourceContext.ts

frontend/src/admin/feedback
  FeedbackManagementPage.tsx
  FeedbackManagementPage.test.tsx

frontend/src/admin/overview
  AdminOverviewPage.tsx
  AdminOverviewPage.test.tsx
  AdminOverviewSection.tsx

frontend/src/app
  navigation.ts
  AppShell.tsx
```

只在组件确实被用户和管理员页面共同复用时提取共享组件；不要预先建设通用 inbox 或 dashboard 组件库。

### 6.4 概览聚合与区块降级

`AdminOverviewService` 直接依赖模块公开服务，不通过 HTTP 调用本应用其他 Controller：

```text
BetaAccessAdminService / overview query
AiRuntimeAdminService
AiAdminUsageQueryService / overview query
AdminFeedbackService / overview stats
阶段三 AiRunAdminQueryService
```

聚合规则：

- 不给整个 `getOverview()` 加单一数据库事务。
- 每个区块独立执行并捕获运行时异常。
- 区块失败记录 `section`、request ID 和异常类型，不记录反馈正文、邮箱列表或 trace。
- 不使用异步 common pool；内测规模下顺序查询更容易控制连接和错误边界。
- 只有认证解析或响应构造本身失败时返回整体 500。

### 6.5 指标

阶段四只增加低基数指标：

```text
feedback.thread.created{category}
feedback.message.sent{senderType}
feedback.thread.status.changed{status}
feedback.message.read{viewerType}
admin.overview.section.failure{section}
```

禁止把 userId、threadId、runId、邮箱、subject 或 requestId 放入 tag。

---

## 7. 详细实施任务

### Task 12：实现用户反馈信箱闭环

#### Task 12.0：固定反馈契约与 Mapper 骨架

**目标：** 在编写业务逻辑前固定 enum、限制、API 路径、DTO 和 MyBatis statement 名称。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/model/FeedbackCategory.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/model/FeedbackStatus.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/model/FeedbackSenderType.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/FeedbackConstraints.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/FeedbackErrorCode.java`
- Create: 用户与管理员 API contract constants 和 request/response DTO
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/repository/mybatis/FeedbackMapper.java`
- Create: `backend/mentor-api/src/main/resources/mapper/feedback/FeedbackMapper.xml`
- Modify: `backend/common/src/main/resources/i18n/api-errors_zh_CN.properties`
- Modify: `backend/common/src/main/resources/i18n/api-errors_en_US.properties`

**实施步骤：**

- [ ] 定义三个 enum，数据库值和 JSON 值保持完全一致。
- [ ] 定义字段长度、分页上限和正文规范化常量。
- [ ] 固定普通用户与管理员 API 路径。
- [ ] 固定创建、回复、状态更新、列表、详情和 read DTO。
- [ ] 列表 DTO 不包含完整正文。
- [ ] 增加本文 5.7 的错误码和本地化消息。
- [ ] Mapper XML 先建立 resultMap 与 statement 骨架，不修改 V30。

**关键测试：**

- enum 序列化值与数据库 CHECK 值一致。
- DTO 的 decimal、时间和 nullable 字段符合契约。
- Mapper XML namespace、statement 和 resultMap 可以注册。
- V30 资源仍是唯一反馈表迁移，文件内容未被改写。

#### Task 12.1：实现反馈 repository、事务和未读查询

**目标：** 建立单一 SQL 所有权和原子写入边界。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/repository/FeedbackRepository.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/repository/mybatis/MyBatisFeedbackRepository.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/repository/mybatis/model/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/FeedbackMutationExecutor.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/metrics/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/FeedbackConfiguration.java`
- Extend: `backend/mentor-api/src/main/resources/mapper/feedback/FeedbackMapper.xml`

**Repository 能力：**

```text
insertThread
insertMessage
findThreadById
findThreadByIdForUpdate
findUserThreadPage
findAdminThreadPage
countUserThreads / countAdminThreads
findMessagesByThreadId
countUnreadMessagesForUser / countUnreadMessagesForAdmin
markAdminMessagesReadByUser
markUserMessagesReadByAdmin
updateThreadActivity
closeThread
reopenThread
feedbackOverviewStats
```

**实施步骤：**

- [ ] 为 thread、message、列表摘要和 overview stats 建立明确 row model。
- [ ] 创建 thread + 首消息使用一个 `TransactionTemplate`。
- [ ] 用户/管理员回复先 `SELECT ... FOR UPDATE`，再插入消息并更新 thread。
- [ ] 用户回复 CLOSED thread 时同事务重开。
- [ ] close/reopen 使用行锁并保持幂等。
- [ ] read 使用 `sender_type + read_at IS NULL` 条件更新，返回实际更新行数。
- [ ] read 不更新 `thread.updated_at`。
- [ ] 列表未读统计按查看者区分 sender type。
- [ ] 所有 SQL 使用参数绑定；分页只接受 service 校验后的 limit/offset。
- [ ] 注册 no-op/Micrometer metrics，tag 只使用固定 enum。

**关键测试：**

- 创建 thread 任一写入失败时不会保留半条会话。
- 用户回复 CLOSED thread 后状态、关闭字段和更新时间一致。
- 管理员回复 CLOSED thread 不自动改变状态。
- 同一 read 请求重复执行，第二次更新数为 0。
- 用户未读不计 USER 消息，管理员未读不计 ADMIN 消息。
- thread 排序不受 read 操作影响。

#### Task 12.2：实现普通用户反馈 service 与 API

**目标：** 普通用户可以安全创建、读取和回复自己的反馈。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/UserFeedbackService.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/FeedbackException.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/feedback/FeedbackController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/feedback/FeedbackExceptionHandler.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/feedback/model/*`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/FeedbackConfiguration.java`
- Depend on: 阶段三 run summary 查询端口

**实施步骤：**

- [ ] 从 `CurrentUserIdProvider` 获取 userId，不接受客户端 userId。
- [ ] 校验 category、subject、content、sourcePath、requestId 和 runId。
- [ ] `sourceRunId` 存在时验证 run 属于当前用户。
- [ ] 创建 thread 并返回首条消息在内的 detail。
- [ ] list 和 detail 始终带当前用户 ownership 条件。
- [ ] 回复前再次锁定并校验 ownership。
- [ ] CLOSED thread 的用户回复自动重开。
- [ ] read 只标记 ADMIN 消息。
- [ ] Controller 只做认证上下文、DTO 映射和 `ApiResponse` 包装。
- [ ] 对不存在与越权分别返回稳定错误码，不回显目标 thread 数据。

**关键测试：**

- 未登录请求返回 401。
- 用户不能列出、读取、回复或标记他人 thread。
- 用户创建反馈时不能伪造 userId。
- 合法 sourceRunId 可以保存。
- 不存在或属于他人的 sourceRunId 返回 `FEEDBACK_SOURCE_RUN_INVALID`。
- GET detail 不改变 readAt。
- POST read 只标记管理员消息。
- 无效分页、空白正文和超长正文返回 400。

**验证：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest='*Feedback*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

#### Task 12.3：实现管理员反馈 service 与 API

**目标：** 管理员可以筛选、读取、回复和更新状态，但不能主动创建 thread。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/AdminFeedbackService.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/feedback/AdminFeedbackController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/feedback/AdminFeedbackApiContractConstants.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/feedback/model/*`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/FeedbackConfiguration.java`

**实施步骤：**

- [ ] list 支持 status、category、userId 和 unreadOnly。
- [ ] 使用 `IdentityUserRepository` 补充当前页用户低敏摘要。
- [ ] pageSize 保持最大 100，默认 20。
- [ ] detail 返回来源路径、requestId、runId 和消息时间线。
- [ ] 管理员回复时 senderUserId 取认证管理员 ID。
- [ ] 管理员 read 只标记 USER 消息。
- [ ] PATCH status 支持幂等 close/reopen。
- [ ] Controller 不提供 `POST /api/admin/feedback`。
- [ ] 反馈回复和状态变化不写额外管理员审计。
- [ ] 为 overview 暴露 `openThreadCount + adminUnreadMessageCount` 的只读方法。

**关键测试：**

- 非管理员访问所有 `/api/admin/feedback/**` 返回 403。
- 管理员能读取所有用户 thread，但不能通过 API 创建 thread。
- unreadOnly 只返回存在未读 USER 消息的 thread。
- 管理员 read 不会标记自己的 ADMIN 消息。
- close 写入 operator ID 和时间，reopen 清空关闭字段。
- 同状态 PATCH 幂等。

#### Task 12.4：实现前端反馈类型、API 与来源上下文

**目标：** 集中维护反馈契约，并在不收集业务正文的前提下自动附带最近页面上下文。

**主要文件：**

- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/services/api.test.ts`
- Create: `frontend/src/feedback/feedbackSourceContext.ts`
- Create: `frontend/src/feedback/feedbackSourceContext.test.ts`

**实施步骤：**

- [ ] 增加 feedback enum、request、response、page 和 overview TypeScript 类型。
- [ ] 增加普通用户与管理员 feedback service 函数。
- [ ] `apiFetch` 保存服务端回显或本地生成的 `X-Request-Id`。
- [ ] 只记录同站 pathname、requestId、可选 runId 和时间，不记录 query、hash、body、Authorization 或响应正文。
- [ ] 排除 `/api/feedback`、`/api/admin/feedback`、登录和健康检查，防止反馈自身覆盖来源上下文。
- [ ] SSE 解析到受信 `runId` 字段时更新当前 pathname 的反馈上下文。
- [ ] 上下文只保留最近一条，优先写入 sessionStorage，并在不可用时回退到内存，最大有效期 30 分钟。
- [ ] 打开反馈弹窗前记录当前 pathname；创建表单允许用户移除上下文。
- [ ] 所有前端 API 继续通过 `requireApiData` 和结构化错误码处理错误。

**关键测试：**

- requestId 优先使用响应头回显值。
- feedback API 不覆盖先前业务请求上下文。
- 不保存 URL query 和请求正文。
- 过期上下文不会附带。
- SSE runId 只更新当前页面上下文。
- 用户移除上下文后创建请求不包含对应字段。

#### Task 12.5：实现普通用户反馈弹窗

**目标：** 完成普通用户创建、历史列表、时间线、未读和回复闭环。

**主要文件：**

- Create: `frontend/src/feedback/UserFeedbackDialog.tsx`
- Create: `frontend/src/feedback/UserFeedbackDialog.test.tsx`
- Create: `frontend/src/feedback/FeedbackThreadList.tsx`
- Create: `frontend/src/feedback/FeedbackThreadTimeline.tsx`
- Create: `frontend/src/feedback/FeedbackComposer.tsx`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 在 `AppShell` 右上角增加普通用户反馈图标；管理员不显示该图标。
- [ ] 移除普通用户 `feedback` view、导航项和页面路由；旧 `/feedback` 统一回到首页。
- [ ] 列表支持全部/OPEN/CLOSED 筛选、分页和刷新。
- [ ] 弹窗状态在内存中维护；选中 thread 不写 URL。
- [ ] 新建反馈在同一弹窗内切换表单，分类使用明确选项，正文使用 textarea。
- [ ] 上下文以可移除的路径/request/run 摘要展示，不显示请求正文。
- [ ] 详情加载成功且有未读时调用 read API，并同步列表和右上角红点。
- [ ] 回复完成后追加/刷新 detail，并把 thread 移到列表顶部。
- [ ] CLOSED thread 回复按钮表达“回复并重新打开”。
- [ ] 桌面双栏尺寸稳定；移动端详情提供返回列表按钮。
- [ ] loading、empty、forbidden、not found 和操作失败状态完整。
- [ ] 所有新增文案同步中英文。

**关键测试：**

- 创建 BUG/SUGGESTION/OTHER thread。
- 可选 subject 为空时正常创建。
- 详情 GET 后只有存在未读才调用 read。
- CLOSED thread 回复后 UI 变为 OPEN。
- 选中未读 thread 后才调用 read API；打开弹窗本身不调用 read API。
- 旧 `/feedback` 直接访问规范化到首页，且不自动打开弹窗。
- 用户看不到管理员管理动作。
- 移动视口没有列表、正文和操作按钮重叠。

#### Task 12.6：实现管理员反馈页面与显式未读同步

**目标：** 管理员能高效处理反馈；普通用户通过图标红点、管理员通过导航数字看到各自视角的未读状态。

**主要文件：**

- Create: `frontend/src/admin/feedback/FeedbackManagementPage.tsx`
- Create: `frontend/src/admin/feedback/FeedbackManagementPage.test.tsx`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/app/navigation.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 增加 `/admin/feedback`、`adminFeedback` view 和 `feedback:manage` 门禁。
- [ ] 管理员列表提供 status、category、userId、unreadOnly 和分页。
- [ ] 筛选与 `threadId` 全部同步 URL，并由 `normalizeAuthenticatedSearch` 白名单化。
- [ ] 详情显示用户跳转、来源上下文和 run 详情跳转。
- [ ] 回复、close、reopen 和 read 后局部更新列表。
- [ ] 页面不渲染“新建反馈”按钮。
- [ ] `App` 持有当前角色视角的 feedback unread count。
- [ ] 未读数通过 list endpoint 的 `unreadMessageCount` 获取，不新增 count endpoint。
- [ ] 仅在登录后、打开或刷新反馈弹窗、读取消息、发送消息或创建反馈后同步；不使用页面可见性事件或 60 秒轮询。
- [ ] 未读查询失败不阻塞 AppShell，也不显示全局错误。
- [ ] `AppShell` 为普通用户反馈图标渲染无数字红点，为管理员反馈导航保留 `99+` 数字徽标。

**关键测试：**

- 非管理员不显示管理员反馈导航。
- 管理员页面没有创建 thread 命令。
- unreadOnly、userId 和 threadId 深链接可恢复。
- close/reopen 后状态与筛选结果同步。
- 普通用户红点只统计 ADMIN 消息，管理员导航徽标只统计 USER 消息。
- 未读查询失败时其他导航和页面仍可用。

**Task 12 完成门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am test
make frontend-test
```

### Task 13：实现管理员概览和跨页面联动

#### Task 13.0：补齐白名单与 AI 概览读模型

**目标：** 各数据所有者提供小而稳定的只读聚合，`mentor-api` 不跨模块拼 SQL。

**主要文件：**

- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/model/BetaAccessOverviewSummary.java`
- Extend: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/repository/BetaAccessRepository.java`
- Extend: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/repository/mybatis/BetaAccessMapper.java`
- Extend: `backend/auth/src/main/resources/mapper/auth/BetaAccessMapper.xml`
- Extend: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/service/BetaAccessAdminService.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/adminquery/AiOverviewSnapshot.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/adminquery/AiEntryRequestMetrics.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/adminquery/AiQuotaRiskUser.java`
- Extend: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/adminquery/AiAdminUsageQueryService.java`
- Extend: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiAdminUsageMapper.java`
- Extend: `backend/ai-governance/src/main/resources/mapper/ai/AiAdminUsageMapper.xml`
- Extend: `backend/ai-governance` daily usage Mapper as needed

**白名单查询：**

- [ ] 一次聚合返回 allowlist 开关、总白名单数和已注册白名单数。
- [ ] 已注册定义为 `auth_users.email_normalized` 存在匹配记录，不按用户状态排除。
- [ ] 不返回白名单邮箱正文。
- [ ] 为用户详情增加 `GET /api/admin/beta-access/users/{userId}`；不通过模糊邮箱搜索推断。

**AI 查询：**

- [ ] 按 quota zone 计算当天 `[fromAt, toExclusive)`。
- [ ] 从 `ai_run_admissions` 一次聚合 `user_id IS NOT NULL` 的入口状态分组。
- [ ] 从现有 `ai_llm_call_usage` 聚合模型调用、Token、成本和未定价部分。
- [ ] 从 `ai_daily_usage(scope='ALL')` 读取当天有请求的用户。
- [ ] 对候选用户调用现有 runtime policy 解析最终额度和状态。
- [ ] 使用整数比较实现 80% 阈值，避免浮点边界误差。
- [ ] 额度风险按 usage ratio 降序、requestCount 降序、userId 升序，最多返回 20 人。
- [ ] 金额继续由唯一 `AiCostCalculator` 计算。

**关键测试：**

- 白名单开关关闭时人数仍正常统计。
- 已注册白名单包括 ACTIVE/DISABLED/DELETED 已存在账号。
- 入口状态分组之和等于 total。
- `4/5` 命中 80%，`3/5` 不命中。
- 用户覆盖额度优先于全局默认额度。
- 已定价和未定价 Token 不混算。
- 当天边界使用配置 quota zone，而不是 JVM 默认时区。

#### Task 13.1：实现管理员概览聚合 API

**目标：** 用一个 API 返回可行动信息，并保证区块故障隔离。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/overview/AdminOverviewService.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/overview/AdminOverviewSection.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/overview/AdminOverviewController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/overview/AdminOverviewApiContractConstants.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/overview/model/*`
- Create: corresponding service/controller tests
- Depend on: 阶段三 run query service

**实施步骤：**

- [ ] 聚合 betaAccess、aiRuntime、aiToday、quotaRisks、feedback、recentFailedRuns 六个区块。
- [ ] generatedAt 使用注入 Clock，quotaDate/zone 使用 AI governance 配置口径。
- [ ] 最近失败 run 固定最多 10 条，只返回 summary。
- [ ] 每个区块通过统一 helper 转为 available/unavailable。
- [ ] 区块失败日志只包含固定 section、request ID 和异常类型。
- [ ] 区块失败指标只使用固定 section tag。
- [ ] 不给整个方法增加单一事务。
- [ ] 整体响应不包含完整 feedback message、trace 或用户代码。
- [ ] 非管理员访问返回 403；前端权限仍使用 `admin-overview:read`。

**关键测试：**

- 六个区块全部成功时字段和统计口径正确。
- 任一区块抛异常时其他区块仍返回 available。
- 失败区块 data 为 null，不返回零值。
- cost 使用 decimal string。
- recent failed runs 不包含完整 prompt、response 或工具参数。
- 非管理员访问 403。

#### Task 13.2：实现管理员概览页面和默认落点

**目标：** 管理员登录后无需逐页检查即可看到当天异常和待办。

**主要文件：**

- Create: `frontend/src/admin/overview/AdminOverviewPage.tsx`
- Create: `frontend/src/admin/overview/AdminOverviewPage.test.tsx`
- Create: `frontend/src/admin/overview/AdminOverviewSection.tsx`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/services/api.test.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/app/navigation.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 增加 `/admin`、`adminOverview` view 和 `admin-overview:read` 门禁。
- [ ] 管理员默认登录落点从 `/admin/users` 改为 `/admin`。
- [ ] 管理员身份判断优先使用 `admin-overview:read`，不再把 `user:manage` 当作唯一标识。
- [ ] 导航顺序按 3.5 固定，概览置顶。
- [ ] 页面顶部显示生成时间和刷新图标按钮。
- [ ] 白名单与 AI runtime 状态使用紧凑状态行并提供设置跳转。
- [ ] 今日入口状态和模型调用/Token/成本明确分区。
- [ ] 未定价调用显示 warning，并跳转 `/admin/ai?tab=pricing`。
- [ ] 额度风险用户提供用户详情和按用户用量跳转。
- [ ] 反馈计数跳转 `/admin/feedback?status=OPEN` 或 `unreadOnly=true`。
- [ ] 最近失败 run 跳转阶段三详情页。
- [ ] unavailable 区块显示局部错误，不替换整个页面。
- [ ] 页面在可见时每 60 秒刷新，手动刷新立即生效；旧请求用 AbortController 取消。
- [ ] 不做折线图、饼图、增长卡片或嵌套卡片。
- [ ] 所有新增文案同步中英文。

**关键测试：**

- 管理员登录与未知管理员路径默认落到 `/admin`。
- 缺少 overview 权限不能进入页面或看到导航。
- 各区块数字和 unavailable 状态正确。
- 入口请求与模型调用使用不同标签。
- 未定价部分不显示 `$0`。
- 所有深链接包含正确 query/path。
- 移动端数字、表格和操作按钮不重叠。

#### Task 13.3：完成反馈、run、用户详情和白名单跨页面联动

**目标：** 从概览、反馈和用户详情进入同一业务对象时，筛选和返回路径保持一致。

**主要文件：**

- Extend: `backend/auth` beta access user membership API/DTO/tests
- Modify: `frontend/src/admin/users/AdminUserDetailDrawer.tsx`
- Create: `frontend/src/admin/users/AdminUserSupportSection.tsx`
- Create: `frontend/src/admin/users/AdminUserSupportSection.test.tsx`
- Modify: `frontend/src/admin/UserManagementPage.tsx`
- Modify: `frontend/src/admin/UserManagementPage.test.tsx`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 打开用户抽屉后，支持信息区独立加载白名单 membership、OPEN 反馈和最近失败 run。
- [ ] 该区域 loading/error 与身份信息、AI 区域互不阻塞。
- [ ] 白名单状态跳转 `/admin/beta-access`，必要时携带可恢复的筛选条件。
- [ ] OPEN 反馈跳转 `/admin/feedback?userId={id}&status=OPEN`。
- [ ] 最近失败 run 跳转详情；“查看全部”进入 `runs?userId={id}&status=FAILED`。
- [ ] 管理员反馈详情中的用户跳转 `/admin/users?userId={id}`。
- [ ] 管理员反馈详情中的 run 跳转阶段三详情页。
- [ ] run 页面返回反馈时使用 `threadId` 深链接；具体返回入口服从阶段三组件结构。
- [ ] 所有 search 参数由 `normalizeAuthenticatedSearch` 明确允许，未知参数丢弃。
- [ ] 不修改 `AdminUserDetailResponse` 添加跨模块字段。

**关键测试：**

- 用户 support 区某一查询失败不影响身份和 AI 区域。
- 白名单存在/不存在状态正确。
- userId、threadId、status 和 unreadOnly 深链接可以刷新恢复。
- 从反馈进入用户、从用户进入反馈的目标一致。
- runId 不存在或 trace 过期时仍可显示 run summary/错误状态，不破坏反馈详情。

**Task 13 完成门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl auth,ai-governance,mentor-api -am test
make frontend-test
```

---

## 8. 实施顺序与依赖

推荐顺序：

```text
Task 12.0
  -> Task 12.1
    -> Task 12.2 + Task 12.3
      -> Task 12.4
        -> Task 12.5 + Task 12.6

阶段三公开 run 查询端口
  -> Task 12.2 的 sourceRunId 校验
  -> Task 13.1 的 recentFailedRuns
  -> Task 13.3 的 run 深链接

Task 13.0
  -> Task 13.1
    -> Task 13.2 + Task 13.3
```

关键依赖说明：

- 反馈 repository 和 API 可以先于概览完成。
- 普通反馈主链路不应被阶段三阻塞，但 run 关联能力必须在阶段四完成前补齐。
- 概览前端只依赖一个聚合 API，不应在浏览器并行拼六个模块接口。
- 用户详情继续前端并行查询，避免形成 `identity -> auth/ai/feedback` 的反向依赖。

---

## 9. 测试矩阵

### 9.1 后端 service

- 创建 thread 与首消息原子性。
- 用户 ownership 和管理员访问边界。
- 用户回复自动重开。
- 管理员回复保持 CLOSED 状态。
- close/reopen 幂等与并发锁语义。
- 双方未读和 read 幂等。
- sourceRunId ownership。
- 白名单概览计数。
- AI 入口状态分组、额度 80% 边界和成本口径。
- overview 区块独立降级。

### 9.2 Controller 与安全

- 普通反馈 API 需要认证。
- 普通用户不能访问他人 thread。
- 非管理员访问 admin feedback/overview 返回 403。
- 管理员 API 没有 create thread mapping。
- 所有 POST/PATCH 使用现有 CSRF 行为。
- 错误码和中英文消息稳定。

### 9.3 Mapper

- Mapper XML 注册与 resultMap 构造。
- 列表分页、排序和筛选 SQL。
- `FOR UPDATE` 读取 statement。
- 未读统计按 sender type 区分。
- read 条件更新不重复计数。
- overview 聚合不返回正文。
- V30 不修改，迁移版本无新增冲突。

### 9.4 前端

- 用户创建、列表、详情、回复、关闭后重开和未读。
- 管理员筛选、回复、close/reopen 和用户/run 跳转。
- 普通用户与管理员徽标视角正确。
- request/run 来源上下文不记录 query/body。
- 管理员默认路由与权限门禁。
- overview 每个区块成功、失败和深链接。
- 用户抽屉多个区域独立 loading/error。
- 桌面和移动视口无文本与控件重叠。

本文不增加 Hurl smoke 任务；上述验证通过各任务的 Maven/Vitest 测试完成。

---

## 10. 推荐提交拆分

```text
feat: add feedback domain and persistence
feat: add user feedback inbox API
feat: add admin feedback management API
feat: add feedback inbox pages and unread badges
feat: add admin overview query aggregation
feat: add admin overview and support cross-links
```

每个提交保持对应模块测试通过。不要把阶段三 trace 实现或总体计划 Task 14 混入这些提交。

---

## 11. 发布与回滚

### 11.1 发布顺序

1. 发布反馈 repository、service 和 API；V30 已存在，无新迁移。
2. 发布普通用户和管理员反馈页面。
3. 发布各模块 overview read model 与聚合 API。
4. 发布管理员概览默认落点和用户详情联动。

后端先于前端发布，保证新路由加载时 API 已可用。

### 11.2 回滚

- 反馈入口故障：隐藏普通用户右上角图标和 `/admin/feedback` 导航，核心学习流程不受影响。
- 概览故障：管理员默认落点临时恢复 `/admin/users`，其他管理页继续可用。
- 单区块查询故障：保留 overview 页面，其余区块继续展示 unavailable 状态。
- 未读状态故障：关闭图标红点同步，用户仍可手动打开弹窗或管理员工作台刷新。
- 数据表保留，不删除用户已提交反馈；回滚应用版本不需要回滚 V30。

---

## 12. Definition of Done

### 12.1 反馈闭环

- [ ] 用户可以创建 BUG、SUGGESTION、OTHER 三类反馈。
- [ ] 用户只能访问自己的 thread。
- [ ] 管理员可以筛选、读取、回复、关闭和重开。
- [ ] 管理员不能主动创建 thread。
- [ ] 用户回复 CLOSED thread 会原子重开。
- [ ] 双方未读统计和显式 read API 正确且幂等。
- [ ] 来源路径、requestId 和合法 runId 可以附带，非法 runId 不能越权。
- [ ] 列表不返回完整反馈正文。

### 12.2 管理员概览

- [ ] `/admin` 成为管理员默认落点。
- [ ] 白名单开关、人数和已注册人数可见。
- [ ] 全局 AI 开关和默认额度可见。
- [ ] 今日入口状态与实际模型调用分开显示。
- [ ] Token、按当前价格估算成本和未定价部分可见。
- [ ] 80% 额度风险用户可进入用户详情或完整用量。
- [ ] OPEN 反馈与管理员未读可进入对应筛选。
- [ ] 最近失败 run 可进入阶段三详情。
- [ ] 任一区块失败不会使整个概览不可用，也不会显示误导性零值。

### 12.3 跨页面与交互

- [ ] 普通用户图标和管理员导航显示各自视角的反馈未读状态。
- [ ] overview、users、runs 和管理员反馈工作台的深链接刷新后可恢复；旧普通用户 `/feedback` 规范化到首页。
- [ ] 用户详情的身份、AI、支持区域独立加载和降级。
- [ ] 所有新增中英文文案完整。
- [ ] 桌面和移动视口不存在列表、消息正文、抽屉和操作按钮重叠。

### 12.4 工程质量

- [ ] 不修改已提交 V30，不产生 Flyway 版本冲突。
- [ ] 共享路径、状态、限制和错误码使用常量或 enum。
- [ ] `mentor-api` 不直接读取 auth、AI governance 或 Agent persistence 私有 Mapper。
- [ ] overview 金额使用 decimal string，前端不做成本计算。
- [ ] 指标 tag 不包含 userId、threadId、runId、邮箱或正文。
- [ ] `auth`、`ai-governance`、`mentor-api` 相关 Maven 测试通过。
- [ ] `make frontend-test` 通过。
- [ ] 本阶段未引入总体计划 Task 14 的工作项。
