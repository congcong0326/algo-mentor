# 管理员会话监控技术设计

> 设计日期：2026-07-23  
> 适用范围：Algo Mentor 管理后台 `/admin/*`  
> 技术基线：Java 17、Spring MVC、Spring Session JDBC、PostgreSQL、MyBatis、React + TypeScript

---

## 0. 已定决策

本文以以下讨论结论作为第一版实现基线：

1. 在管理后台“系统监控”业务域下新增“会话监控”独立页面，路由为 `/admin/sessions`。
2. 页面展示 Spring Session 中仍未过期的认证会话，不把数据库中尚未清理的过期记录计入在线会话。
3. “在线”在产品文案中拆分为“有效会话”和“活跃/空闲”状态，避免把七天内未过期的 Session 等同于用户此刻正在使用系统。
4. 默认最近 5 分钟有访问的有效会话为“活跃”，其余有效会话为“空闲”；活跃窗口通过配置注入。
5. 管理员可以手动下线其他用户的会话，也可以下线自己的其他会话。
6. 当前正在操作管理台的会话显示“当前会话”标记，前端禁用下线按钮，后端再次校验并拒绝下线。
7. 第一版不展示 IP、地理位置、浏览器、操作系统或设备名称，不新增会话元数据表。
8. 第一版不强制中断已经建立的 SSE、正在执行的 HTTP 请求或后台 AI 任务；下线只保证目标 Session 的后续请求无法继续通过认证。
9. 会话列表和单会话下线能力归属 `backend/auth`，不放入 `identity` 或 `mentor-api` 业务包。
10. 第一版直接查询现有 `SPRING_SESSION` 表，不新增 Flyway 迁移。
11. API 不返回浏览器 Cookie 使用的完整 Session ID；对外只返回不可作为登录凭证使用的管理引用 `sessionRef`。
12. 手动下线写入管理员操作审计，并记录低基数 Micrometer 指标；日志不得输出完整 Session ID。

## 1. 背景

当前项目已经具备以下基础：

- Spring Session JDBC 使用 PostgreSQL 持久化认证 Session。
- `SPRING_SESSION` 已保存创建时间、最后访问时间、最大空闲时间、过期时间和 `PRINCIPAL_NAME`。
- 当前认证主体的 `getName()` 返回用户 ID 字符串，因此 `PRINCIPAL_NAME` 可以稳定关联 `auth_users.id`。
- `AuthSessionRevocationService` 已支持按用户 ID 删除该用户全部 Session。
- 用户禁用、软删除、移出内测白名单和管理员重置密码已经复用 Session 吊销能力。
- 管理前端已经按“业务域 + 局部页签”组织页面，“系统监控”当前包含“运行状态”和“AI 治理”。

当前缺口是管理员无法回答以下问题：

- 系统中当前有多少仍有效的认证 Session；
- 同一用户是否在多个位置登录；
- 某个 Session 最近何时访问、何时过期；
- 出现账号风险或排障需要时，能否只下线一个 Session，而不是禁用用户或吊销其全部 Session。

## 2. 目标与非目标

### 2.1 目标

- 提供可分页、可搜索、可筛选的有效认证会话列表。
- 区分最近仍有访问的活跃会话和较长时间无访问的空闲会话。
- 展示会话对应用户、用户状态、创建时间、最后访问时间和预计过期时间。
- 支持管理员精确下线单个会话。
- 识别当前管理会话，并在前后端防止误下线。
- 保持现有按用户吊销全部 Session 的行为和调用方兼容。
- 不暴露 Cookie Session ID，不在日志或审计中保存认证凭证。
- 对手动下线操作提供审计、指标和明确的错误响应。

### 2.2 非目标

- 不实现真正的 WebSocket/SSE 在线 Presence 系统。
- 不保证用户关闭浏览器后立即从列表消失。
- 不主动关闭已建立的 SSE 连接。
- 不取消已经开始执行的 AI run、学习计划生成或代码 Review。
- 不采集客户端 IP、代理链、User-Agent、设备指纹或地理位置。
- 不提供批量下线、按用户一键下线全部会话或全站强制下线按钮；按用户吊销能力继续由现有用户状态和密码运维流程使用。
- 不提供普通用户侧的“登录设备管理”页面。
- 不建设新的管理员角色或动态权限配置页面。
- 不修改 Spring Session 表结构和清理策略。

## 3. 会话与在线语义

### 3.1 有效会话

数据库中的 Session 满足以下条件时进入会话监控列表：

```text
PRINCIPAL_NAME 非空且符合当前用户 ID 字符串约定
AND EXPIRY_TIME > 查询时刻 epoch milliseconds
```

过期 Session 可能在 Spring Session 清理任务执行前继续保留在表中，但不得出现在列表、汇总或分页总数中。

### 3.2 活跃与空闲

在同一次查询使用的统一 `now` 快照下：

```text
ACTIVE: LAST_ACCESS_TIME >= now - activeWindow
IDLE:   LAST_ACCESS_TIME <  now - activeWindow
```

默认配置：

```yaml
algo-mentor:
  auth:
    session-monitoring-active-window: ${AUTH_SESSION_MONITORING_ACTIVE_WINDOW:5m}
```

配置 key 应加入 auth 模块现有配置常量类，属性校验要求值为正数。

“活跃”只表示最近有受 Session 认证的 HTTP 请求，不表示浏览器页面当前可见，也不表示存在长连接。

### 3.3 当前会话

管理员请求会话列表时，通过 `HttpServletRequest.getSession(false).getId()` 获得当前请求使用的 Cookie Session ID。后端将该值与查询结果内部携带的 `SESSION_ID` 比较，并只向响应暴露：

```json
{
  "current": true
}
```

完整 `SESSION_ID` 不进入响应 DTO、前端状态、日志、指标或管理员审计。

## 4. 信息架构与页面设计

### 4.1 路由与导航

“系统监控”业务域调整为：

```text
系统监控
  运行状态     /admin/monitoring
  会话监控     /admin/sessions
  AI 治理      /admin/ai
```

新增前端能力标识：

```text
session:manage
```

当前 `ADMIN` 角色拥有该能力。前端使用该能力控制路由和页签可见性；后端继续以 `/api/admin/**` 的 `ROLE_ADMIN` 校验作为最终安全边界。当前权限字符串是前端能力契约，不能宣称已经形成可动态配置的后端细粒度授权。

### 4.2 页面结构

页面使用管理台现有紧凑数据工作区，不增加营销式说明区或嵌套卡片：

```text
┌─────────────────────────────────────────────────────────────────┐
│ 会话监控                                    最后刷新  刷新按钮  │
├─────────────────────────────────────────────────────────────────┤
│ 有效会话 12        活跃会话 4        涉及用户 7                 │
├─────────────────────────────────────────────────────────────────┤
│ 搜索用户                          状态：全部 / 活跃 / 空闲       │
├─────────────────────────────────────────────────────────────────┤
│ 用户 │ 用户状态 │ 会话状态 │ 创建时间 │ 最后访问 │ 过期时间 │操作│
│ ...                                                             │
├─────────────────────────────────────────────────────────────────┤
│ 共 12 项                                             分页        │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 汇总指标

汇总指标固定为全局有效会话范围，不随列表搜索和活动状态筛选变化：

- `validSessionCount`：全部未过期认证会话数。
- `activeSessionCount`：活跃窗口内的有效会话数。
- `validUserCount`：至少拥有一个有效会话的去重用户数。

列表响应中的 `total` 表示当前搜索和筛选条件下的记录数。

### 4.4 搜索与筛选

支持单一关键词搜索：

- 用户 ID 精确匹配；
- 邮箱规范化后的模糊匹配；
- 昵称不区分大小写的模糊匹配。

活动状态筛选：

```text
ALL
ACTIVE
IDLE
```

默认分页大小为 `20`。`page` 小于 1 时归一为 1，`pageSize` 按 `1-100` 夹取，与现有管理员列表查询保持一致。默认排序固定为：

```text
LAST_ACCESS_TIME DESC, PRIMARY_ID ASC
```

第二排序键用于保证最后访问时间相同时分页顺序稳定。

### 4.5 表格字段

| 字段 | 展示规则 |
| --- | --- |
| 用户 | 昵称优先，附邮箱和用户 ID；缺失昵称时使用邮箱 |
| 用户状态 | `ACTIVE`、`DISABLED`、`DELETED` |
| 会话状态 | `ACTIVE` 显示“活跃”，`IDLE` 显示“空闲” |
| 创建时间 | 本地化日期时间 |
| 最后访问 | 本地化日期时间，必要时附相对时间 |
| 过期时间 | 本地化日期时间，不展示容易漂移的永久倒计时 |
| 当前会话 | `current=true` 时显示稳定状态标记 |
| 操作 | 非当前会话显示“下线”；当前会话按钮禁用并提供说明 tooltip |

### 4.6 刷新行为

- 页面首次进入立即加载。
- 每 60 秒自动刷新一次。
- `document.hidden=true` 时暂停自动刷新。
- 手动刷新使用图标按钮和 tooltip。
- 新请求开始前取消旧请求，避免慢响应覆盖新筛选结果。
- 下线成功或目标已经离线后立即重新加载当前页。
- 当前页删除到空且页码大于 1 时，回退一页重新查询。

## 5. API 契约

### 5.1 公共路径常量

auth 模块新增 `AdminAuthSessionApiContractConstants`，集中管理：

```text
ADMIN_AUTH_SESSIONS_BASE_PATH = /api/admin/auth-sessions
SESSION_REF_PATH = /{sessionRef}
```

错误码、筛选状态和公共字段不得在 controller、service 和测试中重复散落字面量。

### 5.2 查询会话

```http
GET /api/admin/auth-sessions?page=1&pageSize=20&keyword=&activity=ALL
```

响应：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "sessionRef": "0f5cdb19-97f8-4e52-9ca8-218bfa8b3d44",
        "userId": 42,
        "email": "user@example.com",
        "displayName": "示例用户",
        "userStatus": "ACTIVE",
        "createdAt": "2026-07-23T08:00:00Z",
        "lastAccessedAt": "2026-07-23T09:25:00Z",
        "expiresAt": "2026-07-30T09:25:00Z",
        "activity": "ACTIVE",
        "current": false
      }
    ],
    "total": 12,
    "page": 1,
    "pageSize": 20,
    "summary": {
      "validSessionCount": 12,
      "activeSessionCount": 4,
      "validUserCount": 7
    },
    "checkedAt": "2026-07-23T09:26:00Z"
  },
  "timestamp": "2026-07-23T09:26:00Z"
}
```

`checkedAt` 是本次会话有效性和活跃状态计算使用的统一时刻，前端不自行重新判定 `activity`。

### 5.3 手动下线单个会话

```http
DELETE /api/admin/auth-sessions/{sessionRef}
```

成功删除：

```json
{
  "success": true,
  "data": {
    "sessionRef": "0f5cdb19-97f8-4e52-9ca8-218bfa8b3d44",
    "userId": 42,
    "revoked": true,
    "alreadyOffline": false
  },
  "timestamp": "2026-07-23T09:30:00Z"
}
```

目标在查询后自然过期、已注销或被其他管理员删除时采用幂等成功：

```json
{
  "success": true,
  "data": {
    "sessionRef": "0f5cdb19-97f8-4e52-9ca8-218bfa8b3d44",
    "userId": null,
    "revoked": false,
    "alreadyOffline": true
  },
  "timestamp": "2026-07-23T09:30:00Z"
}
```

### 5.4 当前会话拒绝响应

当前请求 Session 与目标 Session 相同时返回：

```http
409 Conflict
```

```json
{
  "success": false,
  "error": {
    "code": "AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN",
    "messageKey": "error.auth.session.currentRevokeForbidden",
    "message": "不能在会话监控中下线当前会话，请使用退出登录。"
  },
  "timestamp": "2026-07-23T09:30:00Z"
}
```

前端禁用只是防误操作，后端校验是必须保留的安全和一致性边界。

### 5.5 其他错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `AUTH_SESSION_QUERY_INVALID` | 400 | 活动状态或搜索条件非法 |
| `AUTH_SESSION_REF_INVALID` | 400 | `sessionRef` 格式非法 |
| `AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN` | 409 | 尝试下线当前请求使用的 Session |
| `AUTH_SESSION_MANAGEMENT_UNAVAILABLE` | 503 | Session repository 或查询 repository 不可用 |
| `AUTH_SESSION_REVOKE_FAILED` | 500 | 删除 Session 时发生未预期错误 |

合法格式但已经不存在的 `sessionRef` 不返回 404，按幂等成功处理。

## 6. 后端模块设计

### 6.1 模块落位

不新增 Maven 模块，新增代码均位于 `backend/auth`：

```text
backend/auth/src/main/java/org/congcong/algomentor/auth/session/admin
  controller/
    AdminAuthSessionController
    AdminAuthSessionExceptionHandler
    AdminAuthSessionApiContractConstants
    model/
      AdminAuthSessionListQuery
      AdminAuthSessionResponse
      AdminAuthSessionPageResponse
      AdminAuthSessionSummaryResponse
      AdminAuthSessionRevocationResponse
  model/
    AuthSessionActivity
    AuthSessionAdminQuery
    AuthSessionAdminPage
    AuthSessionAdminRecord
    AuthSessionAdminSummary
  repository/
    AuthSessionAdminRepository
    mybatis/
      AuthSessionAdminMapper
      MyBatisAuthSessionAdminRepository
      model/AuthSessionAdminRow
  service/
    AuthSessionAdminService
    AuthSessionAdminException
    AuthSessionAdminErrorCode
    AuthSessionAdminMetrics
    MicrometerAuthSessionAdminMetrics
    NoopAuthSessionAdminMetrics

backend/auth/src/main/resources/mapper/auth
  AuthSessionAdminMapper.xml
```

`sessionRef` 到真实 Session ID 的解析、当前会话比较和删除编排全部留在 auth 模块内部。

### 6.2 核心职责

`AuthSessionAdminRepository`：

- 分页查询有效会话；
- 查询全局有效会话汇总；
- 按 `sessionRef` 查询目标会话的内部记录；
- 返回 repository/domain 模型，不返回 controller DTO。

`AuthSessionAdminService`：

- 固定一次请求使用的 `Clock.instant()`；
- 计算活跃窗口起点；
- 校验分页和筛选条件；
- 将内部 `SESSION_ID` 与当前请求 Session ID 比较；
- 编排单会话撤销；
- 写管理员审计和 Micrometer 指标；
- 不直接依赖 `HttpServletRequest`。

`AdminAuthSessionController`：

- 从 `Authentication.getName()` 解析管理员用户 ID；
- 从 `HttpServletRequest.getSession(false)` 提取当前 Session ID；
- 调用 service 并映射统一 `ApiResponse`；
- 不包含 SQL、Session 删除和审计逻辑。

### 6.3 现有撤销服务扩展

现有接口：

```java
int revokeSessionsForUser(long userId);
```

建议兼容性扩展为：

```java
boolean revokeSession(String sessionId);

int revokeSessionsForUser(long userId);
```

`SpringSessionAuthSessionRevocationService.revokeSession` 语义：

1. `findById(sessionId)` 返回空时返回 `false`；
2. 存在时调用 `deleteById(sessionId)` 并返回 `true`；
3. 不记录或抛出包含完整 Session ID 的消息；
4. 不改变现有按用户吊销全部 Session 的行为。

这样可以保持 Session 删除统一通过 Spring Session repository 完成，避免管理员能力直接执行独立 SQL 删除，形成第二套吊销实现。

### 6.4 自动配置

在 `AuthApiAutoConfiguration` 中按现有条件 Bean 风格增加：

- `AuthSessionAdminMapper`：依赖 `SqlSessionTemplate`；
- `AuthSessionAdminRepository`：依赖 mapper；
- `AuthSessionAdminMetrics`：有 `MeterRegistry` 时使用 Micrometer 实现，否则 no-op；
- `AuthSessionAdminService`：依赖查询 repository、撤销 service、审计 recorder、metrics 和 `Clock`；
- controller 与 exception handler：依赖 service。

当 `FindByIndexNameSessionRepository` 或查询 repository 不存在时，不创建管理 service 和 controller。生产应用的自动配置集成测试必须断言这些 Bean 以及 `/api/admin/auth-sessions` 已注册，避免部署后静默缺失接口。Bean 已正常创建但运行期 repository 查询或删除不可用时，API 返回明确的 503，而不是空列表冒充系统没有会话。

## 7. 数据查询设计

### 7.1 `sessionRef` 选择

现有表包含：

```text
PRIMARY_ID       Spring Session 数据库内部主键
SESSION_ID       浏览器 Cookie 使用的 Session ID
PRINCIPAL_NAME   当前项目中的用户 ID 字符串
```

API 使用 `PRIMARY_ID` 作为 `sessionRef`，原因如下：

- 它不是浏览器认证 Cookie 使用的值；
- 可以稳定定位数据库记录；
- 管理 service 可以先通过它读取内部 `SESSION_ID`，再调用 Spring Session repository 删除；
- 不需要增加映射表、签名 token 或缓存。

`sessionRef` 仍属于敏感管理标识，不进入普通用户 API，也不应无必要写入常规业务日志。

### 7.2 列表查询

SQL 由 MyBatis XML 管理，核心条件示意：

```sql
SELECT
  s.PRIMARY_ID,
  s.SESSION_ID,
  CAST(s.PRINCIPAL_NAME AS BIGINT) AS user_id,
  s.CREATION_TIME,
  s.LAST_ACCESS_TIME,
  s.EXPIRY_TIME,
  u.email,
  u.display_name,
  u.status AS user_status
FROM SPRING_SESSION s
LEFT JOIN auth_users u
  ON u.id = CAST(s.PRINCIPAL_NAME AS BIGINT)
WHERE s.PRINCIPAL_NAME ~ '^[0-9]+$'
  AND s.EXPIRY_TIME > #{nowEpochMillis}
ORDER BY s.LAST_ACCESS_TIME DESC, s.PRIMARY_ID ASC
LIMIT #{limit}
OFFSET #{offset}
```

动态条件追加规则：

- 用户 ID 只做精确匹配；
- 邮箱优先匹配 `email_normalized`；
- 昵称使用 `lower(display_name)` 模糊匹配；
- `ACTIVE` 和 `IDLE` 使用 `activeSinceEpochMillis` 比较 `LAST_ACCESS_TIME`；
- count 查询必须复用相同的过滤条件定义，防止列表和总数漂移。

不得反序列化 `SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES` 来生成列表字段。列表所需数据均来自结构化列和 `auth_users`。

### 7.3 汇总查询

汇总使用单条聚合 SQL：

```sql
SELECT
  COUNT(*) AS valid_session_count,
  COUNT(*) FILTER (
    WHERE LAST_ACCESS_TIME >= #{activeSinceEpochMillis}
  ) AS active_session_count,
  COUNT(DISTINCT PRINCIPAL_NAME) AS valid_user_count
FROM SPRING_SESSION
WHERE PRINCIPAL_NAME ~ '^[0-9]+$'
  AND EXPIRY_TIME > #{nowEpochMillis}
```

列表、count 和 summary 必须使用同一个 `nowEpochMillis`，避免一次请求内会话状态跨边界后出现明显不一致。

### 7.4 索引策略

第一版面向当前内测规模，复用现有索引：

- `SESSION_ID` 唯一索引；
- `EXPIRY_TIME` 索引；
- `PRINCIPAL_NAME` 索引。

第一版不新增迁移。只有在实际监控表明列表查询成为数据库热点，并且 Session 数量显著增长时，再评估 `LAST_ACCESS_TIME` 或复合索引。不得仅为当前 5-20 人规模提前增加索引。

## 8. 单会话下线流程

### 8.1 正常流程

```text
管理员点击“下线”
  -> 前端显示确认对话框
  -> DELETE /api/admin/auth-sessions/{sessionRef}
  -> controller 解析 operatorUserId 和 currentSessionId
  -> service 按 sessionRef 查询目标内部记录
  -> 目标不存在或已过期：返回 alreadyOffline=true
  -> 目标 SESSION_ID == currentSessionId：拒绝并返回 409
  -> 调用 AuthSessionRevocationService.revokeSession(SESSION_ID)
  -> 写审计和指标
  -> 返回 revoked 结果
  -> 前端关闭对话框并刷新列表
```

### 8.2 并发与幂等

以下竞态都按可预期结果处理：

- 列表查询后用户主动退出：DELETE 返回 `alreadyOffline=true`。
- 列表查询后 Session 自然过期：DELETE 返回 `alreadyOffline=true`。
- 两名管理员同时下线同一 Session：一方 `revoked=true`，另一方 `alreadyOffline=true`。
- Session 在 repository 查询和删除之间消失：最终返回 `revoked=false`，按已经离线处理。

单会话下线不要求数据库事务包裹查询、Spring Session 删除和审计。Session 状态是易变运行态，强行扩大事务不能消除跨请求竞态，反而会增加锁和耦合。

### 8.3 当前会话保护

保护采用双层实现：

前端：

- `current=true` 显示“当前会话”；
- 下线按钮禁用；
- tooltip 提示“请使用退出登录结束当前会话”。

后端：

- 每次 DELETE 都重新比较目标 `SESSION_ID` 和当前请求 Session ID；
- 相同则返回 `AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN`；
- 不调用 repository 删除；
- 记录拒绝指标，但不将其视为系统故障。

不能只依赖列表中的 `current` 字段，因为列表响应可能过期，也可能被绕过直接调用 API。

## 9. 安全、权限与隐私

### 9.1 权限边界

- 前端页面和导航要求 `session:manage`。
- 后端 API 位于 `/api/admin/**`，由 Spring Security 强制要求 `ROLE_ADMIN`。
- controller 不接受请求体传入的管理员 ID，操作人只能来自当前认证主体。
- 普通用户、未登录请求和非管理员请求不得获得会话数量、用户登录状态或 `sessionRef`。

### 9.2 Session ID 保护

以下位置禁止出现完整 `SESSION_ID`：

- API 响应；
- controller DTO；
- 前端 TypeScript 类型和状态；
- info、warn、error 日志；
- Micrometer tag；
- 管理员审计 metadata；
- 异常 message。

内部 repository row 可以短暂携带 `SESSION_ID`，但只能在 auth 模块内传递给当前会话比较和 Spring Session 删除逻辑。

### 9.3 隐私边界

第一版只展示管理员用户管理页面已经可见的身份字段：

- 用户 ID；
- 邮箱；
- 昵称；
- 用户状态。

不新增更高敏感度的客户端环境采集。未来若增加 IP 或设备信息，必须单独设计代理信任、保留周期、脱敏展示、隐私说明和数据删除语义。

### 9.4 CSRF

DELETE 请求继续通过现有 `apiFetch` 注入 `X-XSRF-TOKEN`。不得为会话管理接口关闭 CSRF，也不得使用 GET 执行下线操作。

## 10. 管理员审计

公共审计契约增加：

```text
AdminAuditAction.AUTH_SESSION_REVOKE
AdminAuditTargetType.AUTH_SESSION
```

允许的 metadata：

```text
USER_ID
SESSION_REVOKED
SESSION_ALREADY_OFFLINE
ERROR_CODE
```

成功下线：

```text
operatorUserId = 当前管理员 ID
action         = AUTH_SESSION_REVOKE
targetType     = AUTH_SESSION
targetRef      = sessionRef
outcome        = SUCCESS
metadata       = userId, sessionRevoked=true, sessionAlreadyOffline=false
```

已离线的幂等请求可以记录为 `SUCCESS`，其中 `sessionRevoked=false`、`sessionAlreadyOffline=true`。当前会话拒绝和未预期删除失败记录为 `FAILURE`。

审计失败不得回滚已经完成的 Session 删除，但必须记录低敏 error 日志。日志只包含管理员 ID、目标用户 ID、结果和错误码，不包含真实 Session ID。

## 11. 可观测性

新增低基数计数指标：

```text
algo_mentor_auth_session_admin_revocations_total{outcome}
```

允许的 `outcome`：

```text
revoked
already_offline
rejected_current
failed
```

可选增加查询计时器：

```text
algo_mentor_auth_session_admin_query_seconds
```

禁止使用用户 ID、管理员 ID、`sessionRef` 或 Session ID 作为 metric tag。

列表当前值不注册为每次请求执行数据库查询的 Gauge。页面汇总通过管理 API 按需读取，系统级 Session 指标若未来需要，应另行设计低成本采集方式。

## 12. 前端实现设计

### 12.1 文件落位

建议新增：

```text
frontend/src/admin/sessions/
  SessionMonitoringPage.tsx
  SessionMonitoringPage.test.tsx
  SessionRevocationDialog.tsx
```

同步修改：

```text
frontend/src/app/navigation.ts
frontend/src/admin/shell/adminNavigation.ts
frontend/src/App.tsx
frontend/src/types/api.ts
frontend/src/services/api.ts
frontend/src/services/api.test.ts
frontend/src/i18n/locales.ts
frontend/src/styles.css
```

### 12.2 前端类型

新增集中类型：

```ts
export type AuthSessionActivity = 'ACTIVE' | 'IDLE';

export interface AdminAuthSession {
  sessionRef: string;
  userId: number;
  email?: string;
  displayName?: string;
  userStatus: AuthUserStatus;
  createdAt: string;
  lastAccessedAt: string;
  expiresAt: string;
  activity: AuthSessionActivity;
  current: boolean;
}
```

前端不得增加 `sessionId` 字段或自行从 `sessionRef` 推导 Cookie 值。

### 12.3 下线交互

下线按钮使用 `LogOut` 图标和文字，点击后打开确认对话框。确认内容至少包含：

- 目标用户；
- 最后访问时间；
- “下线后，该会话的后续请求需要重新登录”；
- “已经建立的请求或流式连接可能继续到当前操作结束”。

确认期间禁用重复提交。成功后关闭对话框并刷新；`alreadyOffline=true` 使用轻量提示说明目标已经离线，不显示错误。

当前会话行不打开确认对话框，下线按钮禁用并提供 tooltip。

### 12.4 响应式布局

- 桌面使用完整表格，保持稳定列宽和横向滚动容器。
- 窄屏优先保留用户、状态、最后访问和操作，创建时间与过期时间可以进入行详情区。
- 操作按钮不得因状态文字变化导致表格列宽跳动。
- 当前会话、活跃和空闲状态均使用文字加颜色，不只依赖颜色表达。

## 13. 异常与降级

| 场景 | 行为 |
| --- | --- |
| Session 查询 repository 不可用 | 页面显示明确加载错误，不显示零会话 |
| 汇总查询失败 | 整个请求失败，避免列表与汇总来源不一致 |
| 自动刷新失败 | 保留上一次成功数据并显示非阻塞错误；手动刷新可重试 |
| 手动下线失败 | 保留对话框和目标信息，允许重试 |
| 目标已自然过期 | 按幂等成功处理并刷新列表 |
| 当前会话保护被后端触发 | 显示明确提示，不清空管理员当前登录态 |
| 审计写入失败 | 不恢复已经删除的 Session，记录低敏错误日志 |
| 用户记录缺失 | 按数据异常记录指标和日志；第一版查询仍依赖当前数值 principal 不变量 |

## 14. 测试设计

### 14.1 后端单元测试

`AuthSessionAdminServiceTest` 至少覆盖：

- 使用统一 `Clock` 计算有效和活跃边界；
- 当前 Session 正确标记；
- 非当前 Session 成功删除；
- 当前 Session 返回指定错误码且不调用删除；
- 目标不存在返回 `alreadyOffline=true`；
- repository 删除竞态返回幂等结果；
- 审计成功、幂等、拒绝和失败事件；
- 日志、异常和响应模型不包含完整 Session ID。

`SpringSessionAuthSessionRevocationServiceTest` 增加：

- 单 Session 存在时删除并返回 `true`；
- 单 Session 不存在时不删除并返回 `false`；
- 现有按用户批量吊销测试继续通过。

`AdminAuthSessionControllerTest` 至少覆盖：

- 管理员 ID 从认证主体解析；
- 当前请求 Session ID 正确传入 service；
- 分页查询参数映射；
- 当前会话冲突返回 409；
- 非管理员由安全过滤链拒绝。

### 14.2 MyBatis 与 PostgreSQL 集成测试

至少覆盖：

- 只查询 `EXPIRY_TIME > now` 的记录；
- 活跃边界恰好等于窗口起点时判为 `ACTIVE`；
- 邮箱、昵称、用户 ID 搜索；
- 活跃/空闲筛选；
- 稳定排序和分页总数；
- 汇总不受列表筛选影响；
- `sessionRef` 能解析到内部 `SESSION_ID`；
- 非数字 `PRINCIPAL_NAME` 不进入当前项目用户会话列表。

### 14.3 前端测试

`SessionMonitoringPage.test.tsx` 至少覆盖：

- 首次加载并展示汇总和会话表格；
- 搜索、活动状态筛选和分页触发正确请求；
- 手动刷新；
- 页面隐藏时不自动刷新；
- 当前会话显示标记且下线按钮禁用；
- 其他会话打开确认对话框并成功下线；
- `alreadyOffline=true` 按成功处理；
- 409 当前会话冲突显示明确错误；
- 加载失败保留上一次成功数据；
- 中英文资源完整。

`api.test.ts` 增加 DELETE 请求的 CSRF header 断言，并验证 API 类型中不存在 Cookie Session ID 字段。

### 14.4 建议验证命令

```bash
make backend-test
make frontend-test
make build
```

实现阶段优先运行 auth 模块和会话页面的最小相关测试，交付前再运行完整构建。遵循项目约定，不主动启动 Vite 开发服务器。

## 15. 实施拆分

### 阶段一：后端查询与单会话吊销

1. 增加 `session:manage` 权限契约和配置属性。
2. 增加会话管理领域模型、查询参数和 repository。
3. 增加 MyBatis mapper、列表、count 和 summary 查询。
4. 扩展 `AuthSessionRevocationService` 的单会话删除能力。
5. 实现当前会话保护、幂等删除和统一异常。
6. 增加 controller、审计和指标。
7. 完成单元测试与 PostgreSQL 集成测试。

### 阶段二：管理前端

1. 增加 `/admin/sessions` 路由和系统监控局部页签。
2. 增加 API 类型和 service 请求。
3. 实现汇总、搜索、筛选、表格、分页和刷新。
4. 实现当前会话标记和下线确认对话框。
5. 增加中英文资源、响应式样式和前端测试。

### 阶段三：联调与交付

1. 验证同一用户多浏览器 Session 展示。
2. 验证下线其他会话后，该浏览器下一次 API 请求返回未登录。
3. 验证当前管理会话无法通过 UI 或直接 API 下线。
4. 验证过期 Session 不进入列表。
5. 验证操作审计和 Micrometer 指标不包含 Session ID。
6. 运行完整后端、前端测试和构建。

## 16. 验收标准

- “系统监控”下出现“会话监控”页签，权限不足时不展示。
- 页面只展示未过期的认证 Session。
- 活跃与空闲状态使用服务端统一时刻和可配置窗口计算。
- 支持按用户 ID、邮箱和昵称搜索，支持活跃状态筛选和分页。
- 同一用户的多个 Session 分别展示并可单独下线。
- 当前管理 Session 显示“当前会话”，前端禁用操作，直接调用 API 也返回 409。
- 下线其他 Session 后，其后续认证请求失败，不影响该用户的其他 Session。
- API、前端、日志、指标和审计均不暴露 Cookie Session ID。
- 目标已退出或过期时，下线请求幂等成功。
- 已建立 SSE 不承诺被立即中断，页面确认文案明确该限制。
- 不新增数据库迁移，不改变现有按用户吊销全部 Session 的行为。
- 后端、前端最小相关测试和完整构建通过。

## 17. 风险与后续演进

### 17.1 已知限制

- 默认 Session 有效期较长，空闲会话仍会留在有效会话列表。
- HTTP 最后访问时间不是用户视觉上的“在线心跳”。
- 删除 Session 不会主动取消已开始的 AI 任务。
- 已建立 SSE 连接可能持续到流结束、超时或客户端断开。
- 第一版无法帮助管理员判断具体设备或网络来源。

### 17.2 后续可选能力

只有出现明确需求时再分别设计：

- 普通用户侧登录设备管理；
- 受控采集 IP 和 User-Agent 的独立会话元数据表；
- 按 Session 追踪并关闭 SSE 连接；
- 按用户批量下线的管理入口；
- 异常登录提醒和新设备通知；
- 动态管理员权限与双人审批。

这些能力不应阻塞第一版会话查询和单会话下线闭环。

## 18. 回滚策略

第一版不增加数据库结构，回滚只涉及应用代码：

1. 移除 `/admin/sessions` 路由和导航项；
2. 移除会话管理 controller、service、mapper 和自动配置 Bean；
3. 保留现有 `revokeSessionsForUser` 行为；新增单会话方法若无其他调用方可一并移除；
4. 已写入的管理员审计记录和 Micrometer 历史时序无需删除；
5. Spring Session 表和认证流程不需要回滚。
