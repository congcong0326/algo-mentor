# 管理员用户管理设计

## 背景

当前系统已有 `backend/auth` 模块，负责用户表、角色表、OAuth/密码登录、Spring Security、Session 与当前用户接口。管理员登录后已有 `user:manage` 权限能力标识，但还没有用户管理 API 和前端页面。

本次目标是在管理员界面新增用户管理功能，支持分页搜索、查看、禁用/恢复、软删除，并将用户身份本体从认证模块中拆出到底层身份模块。

## 目标

- 新增 `backend/identity` Maven 模块，作为底层身份模块。
- `auth` 依赖 `identity`，认证流程通过身份模块创建和查询用户。
- 管理员可以分页搜索用户，按状态筛选，查看详情，禁用/恢复用户，软删除用户。
- 禁用和软删除用户后，该用户应被强制下线。
- 前端新增管理员用户管理页面，仅对具备 `user:manage` 权限的管理员展示入口。

## 非目标

- 首版不做物理删除用户。
- 首版不做已软删除用户恢复。
- 首版不做完整操作审计历史表。
- 首版不引入新的前端路由库。
- 首版不重构全部认证、安全和 Session 机制。

## 模块边界

新增 `backend/identity` 模块，允许依赖 MyBatis，负责身份本体与用户管理能力：

- 用户、角色、状态等身份模型。
- 用户创建、查询、分页搜索、禁用、恢复、软删除。
- `IdentityUserRepository` 及 MyBatis mapper/XML 实现。
- `auth_users`、`auth_user_roles` 相关迁移归属。
- `/api/admin/users` 管理员用户管理 controller 与 DTO。
- 身份事件模型与发布接口。

`backend/auth` 调整为认证模块，继续负责：

- 密码登录/注册入口。
- OAuth 登录入口。
- Spring Security、CSRF、Session、当前用户解析。
- 密码凭证表 `auth_password_credentials`。
- OAuth 账号表 `auth_oauth_accounts`。
- 订阅身份事件并执行认证侧的会话下线。

认证创建用户的流程改为：`auth` 接收认证请求，完成凭证校验或第三方身份校验后，调用 `identity` 创建或查询用户。用户身份创建能力归属 `identity`，认证模块只编排认证流程。

`backend/mentor-api` 继续通过依赖 `auth` 获得认证和安全能力。由于 `auth` 依赖 `identity`，身份模块的自动配置会随应用启动注册管理员用户管理 API。管理员 API 路径统一为 `/api/admin/users`，继续受现有 `/api/admin/**` 需要 `ROLE_ADMIN` 的安全规则保护。

## 数据模型

保留现有表：

- `auth_users`
- `auth_user_roles`

新增软删除字段：

- `auth_users.deleted_at timestamptz`
- `auth_users.deleted_by bigint`

`AuthUserStatus` 扩展为：

- `ACTIVE`：可登录。
- `DISABLED`：不可登录，可由管理员恢复。
- `DELETED`：软删除，不可登录，默认不出现在普通管理列表中，首版不支持恢复。

状态规则：

- 禁用：`ACTIVE -> DISABLED`。
- 恢复：`DISABLED -> ACTIVE`。
- 软删除：`ACTIVE` 或 `DISABLED` -> `DELETED`，写入 `deleted_at` 和 `deleted_by`。
- `DELETED` 用户不能再次禁用、恢复或删除。
- 登录认证只允许 `ACTIVE` 用户通过，`DISABLED` 和 `DELETED` 使用通用登录失败文案。

## 身份事件

`identity` 不感知认证模块和会话实现。用户状态发生关键变化后，身份模块只发布身份事实事件。

建议使用统一事件：

```java
public record IdentityUserStatusChangedEvent(
    long userId,
    AuthUserStatus previousStatus,
    AuthUserStatus currentStatus,
    long operatorUserId,
    Instant occurredAt
) {
}
```

事件发布时机：

- 禁用成功后发布 `ACTIVE -> DISABLED`。
- 恢复成功后发布 `DISABLED -> ACTIVE`。
- 软删除成功后发布 `ACTIVE/DISABLED -> DELETED`。

`auth` 订阅身份事件。收到目标用户进入 `DISABLED` 或 `DELETED` 状态时，按认证模块自己的实现删除该用户当前登录会话。未来如果增加 JWT 黑名单、OAuth token revoke 或设备会话管理，也由 `auth` 内部处理。

会话下线失败不回滚身份状态变更。认证模块需要记录 error 日志和失败计数；后续请求仍会因为用户状态不是 `ACTIVE` 被拒绝。

## 后端 API

管理员用户 API：

- `GET /api/admin/users?page=&pageSize=&keyword=&status=`
  - 分页列表。
  - `keyword` 匹配邮箱和显示名。
  - `status` 可选 `ACTIVE`、`DISABLED`、`DELETED`。
  - 不传 `status` 时默认返回未删除用户，即 `ACTIVE` 和 `DISABLED`。
- `GET /api/admin/users/{userId}`
  - 查看用户详情。
  - 返回基本信息、角色、状态、创建/更新时间、最后登录时间、删除信息。
- `PATCH /api/admin/users/{userId}/status`
  - 请求体：`{ "status": "ACTIVE" | "DISABLED" }`。
  - 用于禁用和恢复。
- `DELETE /api/admin/users/{userId}`
  - 执行软删除。

保护规则：

- 管理员不能禁用、恢复或删除自己。
- 不存在用户返回 404。
- 对 `DELETED` 用户执行禁用、恢复或再次删除返回 409。
- 非管理员访问由 Spring Security 拦截，返回 403。

建议错误码：

- `USER_NOT_FOUND` -> 404。
- `USER_STATUS_CONFLICT` -> 409。
- `USER_SELF_OPERATION_FORBIDDEN` -> 409。
- `USER_STATUS_INVALID` -> 400。

## 前端设计

新增管理员导航项“用户管理”，仅当 `currentUser.permissions` 包含 `user:manage` 时显示。

路由延续现有手写路由模式：

- `APP_ROUTES.adminUsers = '/admin/users'`
- `AppView = 'adminUsers'`
- `viewFromPath()` 和 `pathForView()` 增加映射。
- 无权限访问 `/admin/users` 时回到首页。

新增页面 `frontend/src/admin/UserManagementPage.tsx`：

- 顶部工具栏：关键词搜索、状态筛选、刷新按钮。
- 主区域：用户表格，展示 ID、邮箱、显示名、角色、状态、创建时间、最后登录时间。
- 行操作：查看、禁用/恢复、删除。
- 详情展示：抽屉或页面内详情面板。
- 分页：页码、每页数量、总数。
- 操作确认：禁用、恢复、删除都需要确认；删除文案明确“软删除并强制下线”。

前端 API/types 增加：

- `AdminUserSummary`
- `AdminUserDetail`
- `AdminUserPage`
- `AdminUserListQuery`
- `AdminUserStatusUpdateRequest`
- `getAdminUsers`
- `getAdminUserDetail`
- `updateAdminUserStatus`
- `deleteAdminUser`

页面状态：

- 加载中展示稳定高度的表格占位。
- 空列表展示空态。
- 401/403 展示错误并允许返回首页。
- 操作成功后刷新当前页；如果删除导致当前页为空，回退上一页。

## 可观测性

管理员操作记录结构化日志：

- 操作类型。
- 目标用户 ID。
- 操作者用户 ID。
- 操作结果。

日志不得记录密码、token、完整 Authorization 头或用户隐私内容。

可选 Micrometer 计数：

- 用户禁用次数。
- 用户恢复次数。
- 用户软删除次数。
- 认证侧会话吊销成功次数。
- 认证侧会话吊销失败次数。

## 测试策略

后端优先按 TDD 实现：

- 身份服务测试覆盖分页筛选、禁用、恢复、软删除、自操作保护、删除后事件发布。
- MyBatis/repository 测试覆盖搜索、状态过滤、软删除字段更新。
- Controller 测试覆盖 API 状态码和 DTO。
- auth 测试覆盖 `DISABLED`/`DELETED` 拒绝登录，以及收到身份事件后删除会话。

前端测试覆盖：

- `user:manage` 权限控制导航显示。
- `/admin/users` 路由权限保护。
- 列表查询参数组装。
- 禁用、恢复、删除操作确认与刷新。
- API 错误提示。

验证命令：

- `make backend-test`
- `make frontend-test`

实现中可先运行最小相关 Maven/Vitest 测试，再在交付前运行完整命令。

## 风险与缓解

- 模块迁移风险：先迁移用户和角色身份本体，密码凭证与 OAuth 账号仍留在 `auth`，避免一次性移动整个认证体系。
- 软删除语义风险：首版不支持恢复 `DELETED`，避免与禁用/恢复混淆。
- 会话下线失败风险：状态变更不回滚，认证模块记录失败并通过后续认证状态校验兜底。
- 前端路由扩展风险：沿用现有手写路由，避免引入新路由库造成额外改造。
