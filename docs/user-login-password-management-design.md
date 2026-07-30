# 用户登录密码管理研发设计

> 设计日期：2026-07-27
> 适用范围：`backend/auth`、`frontend/src/SettingsPage.tsx`
> 技术基线：Java 17、Spring Security、Spring Session JDBC、PostgreSQL、MyBatis、React + TypeScript

---

## 0. 已定决策

1. 在设置页提供统一的“登录密码”管理功能，不单独建设面向第三方登录用户的专用设置密码页面。
2. 当前通过邮箱密码登录时，修改密码必须提交并验证原密码。
3. 当前通过受信第三方登录（Google OIDC 或 GitHub OAuth2）时，允许不输入原密码，直接新增或覆盖当前用户的密码凭据。
4. 第三方登录免原密码能力只信任服务端 Spring Security 中的真实认证类型，不接收前端声明的登录方式。
5. 没有密码凭据时，页面按钮显示“设置密码”；已有密码凭据时显示“修改密码”。
6. 第三方登录后不强制设置密码，不弹出阻塞式引导；用户在设置页主动操作。
7. 首版不要求第三方二次认证或“最近登录”校验。未来可以在不修改核心改密 API 的前提下增加近期认证门槛。
8. 新增和修改统一使用 `PUT /api/auth/password`，请求体中的 `currentPassword` 按当前 Session 认证方式决定是否必填。
9. 修改已有密码后保留当前 Session，吊销当前用户的其他 Session；首次新增密码不主动吊销其他 Session。
10. 复用现有 `auth_password_credentials` 表及其 `user_id` 唯一约束，不新增数据库迁移。
11. 管理员临时密码重置与用户主动改密保持两套业务语义，不复用管理员临时密码生成、过期和单次消费流程。

## 1. 背景

当前项目支持两类登录方式：

- Google OIDC、GitHub OAuth2 登录；
- 邮箱密码注册和登录。

这些登录方式最终都映射到同一个 `auth_users` 用户。第三方身份保存在 `auth_oauth_accounts`，密码凭据保存在 `auth_password_credentials`。密码表已经使用 `user_id` 唯一约束，因此一个用户最多只有一份当前有效密码凭据。

现有密码注册只允许创建新用户。当第三方登录用户已经拥有相同邮箱的 `auth_users` 记录时，再走密码注册会被识别为邮箱已注册，用户无法为已有账号补充密码凭据。设置页当前只展示用户身份和退出登录，也没有普通用户修改密码的入口。

项目已经存在管理员临时密码重置和强制改密流程，但该流程面向管理员协助恢复账号，包含临时密码有效期、单次消费、强制修改和全量 Session 吊销，不适合承担普通用户主动管理登录密码的职责。

本设计增加一个统一的用户改密能力，使用户不需要理解“新增密码凭据”和“更新密码凭据”的底层差异：

```text
设置页 -> 登录密码
  |
  +-- 当前为密码 Session -> 验证原密码 -> 更新密码
  |
  +-- 当前为 OIDC/OAUTH2 Session -> 无需原密码 -> 新增或更新密码
```

## 2. 目标与非目标

### 2.1 目标

- 让第三方登录用户可以为同一账号增加邮箱密码登录方式。
- 让已有密码用户可以在设置页主动修改密码。
- 根据当前 Session 的实际认证方式决定是否要求原密码。
- 保证前端展示状态与后端密码凭据状态一致。
- 使用原子数据库写入处理重复提交和并发修改。
- 修改已有密码后使其他 Session 失效，减少旧登录状态继续存活的风险。
- 复用现有认证、CSRF、密码编码、错误响应和国际化基础设施。

### 2.2 非目标

- 不在第三方登录完成后强制用户设置密码。
- 不建设第三方二次认证、近期认证时间窗口或 MFA 挑战。
- 不建设普通用户“忘记密码”邮件找回流程。
- 不提供删除密码、关闭密码登录或解除第三方账号绑定功能。
- 不允许前端选择或伪造本次改密所使用的认证方式。
- 不修改密码注册、管理员临时密码重置和强制改密的既有业务入口。
- 不新增密码历史表、密码重复使用限制或周期性强制改密策略。

## 3. 核心业务规则

### 3.1 状态维度

改密行为由两个服务端事实共同决定：

| 当前 Session 认证方式 | 是否已有密码凭据 | 页面行为 | 后端行为 |
| --- | --- | --- | --- |
| `PASSWORD` | 是 | 显示“修改密码”，展示原密码字段 | 必须验证原密码后更新 |
| `OIDC` / `OAUTH2` | 否 | 显示“设置密码”，不展示原密码字段 | 直接新增密码凭据 |
| `OIDC` / `OAUTH2` | 是 | 显示“修改密码”，不展示原密码字段 | 直接覆盖密码凭据 |
| `PASSWORD` | 否 | 不应出现 | 拒绝请求并记录异常状态 |
| 不支持的认证类型 | 任意 | 不提供操作入口 | 拒绝请求 |

“已有第三方账号绑定”不能单独作为免原密码依据。用户即使绑定过 Google 或 GitHub，只要本次 Session 是通过密码登录建立的，就必须验证原密码。反过来，本次 Session 确实通过受信第三方登录建立时，可以把该身份视为当前改密操作的身份凭证。

首次新增密码还要求当前内部用户存在可用于密码登录的有效邮箱。第三方登录用户缺少内部邮箱时，不创建一个无法通过邮箱登录的孤立密码凭据；设置页应显示账号信息异常或暂不提供设置入口，后端同时执行最终校验。

### 3.2 密码校验

首版沿用现有密码规则：

- 新密码至少 `8` 个字符；
- `newPassword` 与 `confirmPassword` 必须一致；
- 密码 Session 必须提供 `currentPassword`；
- 原密码使用现有 `PasswordEncoder.matches` 校验；
- 新密码只保存编码后的 hash，不进入日志、错误 metadata、指标或审计内容。

最小长度应提取到认证模块统一的密码约束类，例如 `PasswordPolicyConstraints.MIN_LENGTH`，由注册、临时密码完成修改和用户主动改密共同复用，避免三个流程分别维护字面量。

### 3.3 临时密码状态

通过管理员临时密码登录且 `passwordChangeRequired=true` 的 Session，仍必须使用现有：

```text
POST /api/auth/password/complete-reset
```

完成强制改密。通用 `PUT /api/auth/password` 不作为绕过强制改密页面的备用入口。过滤器和业务服务都应拒绝 `passwordChangeRequired=true` 的通用改密请求。

OIDC/OAUTH2 Session 不携带临时密码强制状态。用户能够成功完成第三方登录时，可以通过通用改密接口覆盖已有的临时密码状态，并将凭据恢复为普通密码；这与第三方身份能够独立访问同一账号的现有语义一致。

## 4. 认证上下文

### 4.1 认证方式枚举

`auth` 模块新增内部及响应共用的稳定枚举：

```java
public enum AuthSessionAuthenticationMethod {
  PASSWORD,
  OIDC,
  OAUTH2
}
```

枚举值属于前后端公共契约，不在 Controller、service 和前端组件中散落字符串。

### 4.2 服务端解析

新增 `CurrentAuthenticationContextResolver`，从 `SecurityContextHolder` 解析：

```text
UsernamePasswordAuthenticationToken
  + principal 为 AuthenticatedUserPrincipal
  -> PASSWORD

OAuth2AuthenticationToken
  + principal 为 AuthenticatedOidcUser
  -> OIDC

OAuth2AuthenticationToken
  + principal 为 AuthenticatedOAuth2User
  -> OAUTH2

其他 Authentication 或 principal 类型
  -> unsupported
```

解析结果至少包含：

```java
public record CurrentAuthenticationContext(
    AuthenticatedUserPrincipal principal,
    AuthSessionAuthenticationMethod method
) {
}
```

改密 Controller 不接受 `userId`、`authenticationMethod`、provider subject 或邮箱作为请求参数。目标用户只能来自当前认证上下文中的受信 `principal.userId()`。

Google OIDC 和 GitHub OAuth2 登录都会把 provider subject 映射到内部用户，并分别把 `AuthenticatedOidcUser` 或 `AuthenticatedOAuth2User` 保存到 SecurityContext。改密链路不再使用请求邮箱进行账号匹配，避免把“邮箱相同”错误地当成本次操作的授权依据。

### 4.3 当前用户响应

`GET /api/auth/me` 的 `CurrentUserResponse` 增加：

```json
{
  "passwordConfigured": true,
  "sessionAuthenticationMethod": "OAUTH2"
}
```

字段语义：

| 字段 | 说明 |
| --- | --- |
| `passwordConfigured` | 当前用户是否存在 `auth_password_credentials` 记录 |
| `sessionAuthenticationMethod` | 当前浏览器 Session 是通过密码、OIDC 还是普通 OAuth2 建立 |

建议新增统一的 `CurrentUserResponseFactory` 或等价 mapper，集中组装权限、密码状态和当前认证方式，替换 `CurrentUserController` 与 `PasswordAuthController` 中重复的响应构造逻辑。

## 5. API 设计

### 5.1 更新登录密码

```http
PUT /api/auth/password
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

请求：

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```

字段规则：

| 字段 | 密码 Session | OIDC/OAUTH2 Session |
| --- | --- | --- |
| `currentPassword` | 必填并验证 | 可省略，后端不依赖该字段 |
| `newPassword` | 必填 | 必填 |
| `confirmPassword` | 必填 | 必填 |

成功响应：

```json
{
  "success": true,
  "data": {
    "passwordConfigured": true,
    "operation": "UPDATED",
    "revokedSessionCount": 1
  },
  "timestamp": "2026-07-27T00:00:00Z"
}
```

`operation` 使用固定枚举：

- `CREATED`：此前没有密码凭据，本次新增；
- `UPDATED`：此前已有密码凭据，本次覆盖。

`revokedSessionCount` 只返回数量，不返回 Session ID、设备信息或其他会话内容。

### 5.2 错误契约

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `AUTH_PASSWORD_REQUEST_INVALID` | 新密码为空、长度不足或两次输入不一致 |
| `400` | `AUTH_CURRENT_PASSWORD_REQUIRED` | 密码 Session 未提交原密码 |
| `403` | `AUTH_CURRENT_PASSWORD_INVALID` | 密码 Session 的原密码错误 |
| `403` | `AUTH_PASSWORD_CHANGE_REQUIRED` | 临时密码 Session 尝试调用通用改密接口 |
| `403` | `AUTH_PASSWORD_UPDATE_NOT_ALLOWED` | 当前 Authentication 类型不受支持 |
| `409` | `AUTH_PASSWORD_LOGIN_EMAIL_UNAVAILABLE` | 首次设置密码时当前用户没有可用登录邮箱 |
| `409` | `AUTH_PASSWORD_CHANGED_CONCURRENTLY` | 校验原密码后，凭据被其他请求并发修改 |
| `503` | `AUTH_PASSWORD_UPDATE_FAILED` | 密码存储或 Session 吊销基础设施失败 |

错误响应沿用 `ApiErrorResponseFactory` 与 `Accept-Language` 国际化机制。原密码错误不得返回密码 hash、编码算法或匹配细节。

### 5.3 安全过滤链

该接口位于 `/api/**` 下，沿用现有规则：

- 必须已认证；
- 必须通过 Cookie CSRF token 校验；
- 用户禁用、删除、白名单失效和 Session 绝对超时过滤继续生效；
- 不加入 `permitAll` 路径；
- `PasswordChangeRequiredFilter` 不放行该接口。

## 6. 后端设计

### 6.1 包结构

建议在 `backend/auth` 中新增独立的用户密码管理包，避免继续扩大管理员 `passwordreset` 包：

```text
backend/auth/src/main/java/org/congcong/algomentor/auth/password
  PasswordPolicyConstraints.java
  UserPasswordService.java
  UserPasswordMutationExecutor.java
  UserPasswordUpdateCommand.java
  UserPasswordUpdateResult.java
  UserPasswordUpdateOperation.java
  UserPasswordErrorCode.java
  UserPasswordException.java
```

Controller 与 DTO 沿用认证 API 分层：

```text
backend/auth/src/main/java/org/congcong/algomentor/auth/controller
  UserPasswordController.java

backend/auth/src/main/java/org/congcong/algomentor/auth/model
  UserPasswordUpdateRequest.java
  UserPasswordUpdateResponse.java
```

固定路径、错误码和认证方式枚举分别归入现有或新建的契约常量类，不直接在注解、前端 API 和测试中重复硬编码。

### 6.2 Service 分支

`UserPasswordService.updatePassword` 的逻辑固定为：

```text
1. 解析当前认证上下文
2. 拒绝未认证、不支持的认证类型和 passwordChangeRequired Session
3. 校验新密码与确认密码
4. 查询当前用户的密码凭据
5. 根据 Session 认证方式执行：
   PASSWORD:
     - 凭据必须存在
     - currentPassword 必填
     - PasswordEncoder.matches 校验原密码
     - 使用原 hash 作为 CAS 条件更新
   OIDC/OAUTH2:
     - 不校验 currentPassword
     - 首次新增前校验当前用户存在可用登录邮箱
     - 先尝试按 user_id 插入，唯一键冲突后再覆盖已有凭据
6. 若覆盖了已有密码，吊销除当前 Session 外的其他 Session
7. 返回 CREATED/UPDATED 与吊销数量
```

密码编码在进入数据库事务前完成，避免 BCrypt 等 CPU 操作延长数据库事务。

### 6.3 Repository 与 SQL

现有 `auth_password_credentials.user_id` 唯一约束足以支持本功能，不新增 Flyway migration。

密码 Session 更新采用 compare-and-set：

```sql
UPDATE auth_password_credentials
SET password_hash = :newPasswordHash,
    reset_required = FALSE,
    temporary_password_expires_at = NULL,
    temporary_password_consumed_at = NULL,
    password_changed_at = :changedAt,
    reset_by = NULL,
    updated_at = :changedAt
WHERE user_id = :userId
  AND password_hash = :expectedPasswordHash;
```

更新行数为 `0` 时不能静默重试，因为原密码校验所依据的凭据可能已经变化，应返回 `AUTH_PASSWORD_CHANGED_CONCURRENTLY`。

OIDC/OAUTH2 Session 在同一事务内先尝试插入：

```sql
INSERT INTO auth_password_credentials (
  user_id,
  password_hash,
  reset_required,
  temporary_password_expires_at,
  temporary_password_consumed_at,
  password_changed_at,
  reset_by,
  created_at,
  updated_at
) VALUES (
  :userId,
  :passwordHash,
  FALSE,
  NULL,
  NULL,
  :changedAt,
  NULL,
  :changedAt,
  :changedAt
)
ON CONFLICT (user_id) DO NOTHING;
```

插入行数为 `1` 时，本次操作为 `CREATED`。插入行数为 `0` 时，说明凭据已存在或被并发请求抢先创建，随后执行不校验旧 hash 的受信第三方登录覆盖更新，本次操作为 `UPDATED`。这使 `CREATED/UPDATED`、是否吊销其他 Session 和并发行为都有确定结果，不能依赖前端提交的 `passwordConfigured` 或更新前的非锁定查询。

### 6.4 Session 吊销

扩展 `AuthSessionRevocationService`：

```java
int revokeOtherSessionsForUser(long userId, String currentSessionId);
```

实现通过 Spring Session 的 principal 索引查询用户全部 Session，跳过当前 Session，删除其余 Session。完整 Session ID 只作为 repository 删除参数使用，不写入日志、响应、指标标签或审计 metadata。

Session 处理规则：

| 操作 | Session 行为 |
| --- | --- |
| 首次新增密码 | 保留全部现有 Session |
| 更新已有密码 | 保留当前 Session，吊销其他 Session |
| 数据库写入或吊销失败 | 整体失败，不返回成功 |

`UserPasswordMutationExecutor` 使用现有事务管理器统一执行凭据写入和 Session 删除。若 Spring Session 删除无法参与同一数据库事务，实现和测试必须明确实际原子性；不得在文档或日志中虚假声明强原子保证。

## 7. 前端设计

### 7.1 设置页位置

在现有设置页“账户”区增加“登录密码”行，不新增独立路由，也不在登录成功后弹窗。

展示状态：

| `passwordConfigured` | 文案 | 按钮 |
| --- | --- | --- |
| `false` | `未设置，可添加邮箱密码登录方式` | `设置密码` |
| `true` | `已设置，可使用邮箱和密码登录` | `修改密码` |

当前邮箱为空且尚未设置密码时，不显示可提交的“设置密码”按钮，改为展示账号信息异常状态。即使前端状态过期或被绕过，后端仍返回 `AUTH_PASSWORD_LOGIN_EMAIL_UNAVAILABLE`，避免创建无法通过邮箱使用的密码凭据。

### 7.2 改密弹窗

弹窗根据 `sessionAuthenticationMethod` 渲染字段：

```text
PASSWORD Session
  - 原密码
  - 新密码
  - 确认新密码

OIDC/OAUTH2 Session
  - 新密码
  - 确认新密码
```

交互要求：

- 密码字段使用浏览器合适的 `autocomplete`：原密码为 `current-password`，新密码为 `new-password`；
- 支持显示或隐藏密码，图标按钮提供可访问名称；
- 提交期间禁用重复提交；
- 前端执行长度和一致性校验，但后端始终重复校验；
- 成功后关闭弹窗，将内存中的 `currentUser.passwordConfigured` 更新为 `true`；
- 第三方登录首次设置成功提示“以后可以使用当前邮箱和此密码登录，第三方登录仍然有效”；
- 修改成功时提示其他 Session 已退出，但不展示数量以外的会话信息；
- 中英文文案统一维护在 `frontend/src/i18n/locales.ts`。

前端不发送 `authenticationMethod`。`currentPassword` 字段是否出现只影响交互，不能作为后端授权判断依据。

### 7.3 API 类型

`frontend/src/types/api.ts` 增加：

```ts
export type AuthSessionAuthenticationMethod = 'PASSWORD' | 'OIDC' | 'OAUTH2';

export interface UserPasswordUpdateRequest {
  currentPassword?: string;
  newPassword: string;
  confirmPassword: string;
}

export type UserPasswordUpdateOperation = 'CREATED' | 'UPDATED';

export interface UserPasswordUpdateResponse {
  passwordConfigured: true;
  operation: UserPasswordUpdateOperation;
  revokedSessionCount: number;
}
```

`CurrentUser` 同步增加 `passwordConfigured` 与 `sessionAuthenticationMethod`。

## 8. 安全边界与已接受风险

### 8.1 已落实的安全边界

- 只允许当前已认证用户修改自己的密码。
- 只信任服务端 Authentication 类型，不信任前端声明。
- 密码 Session 必须验证原密码。
- OIDC/OAUTH2 Session 必须是项目认证链创建的 `AuthenticatedOidcUser` 或 `AuthenticatedOAuth2User`，不能仅凭账号存在第三方绑定免校验。
- 所有写请求执行 CSRF 校验。
- 新密码使用现有 `PasswordEncoder` 编码。
- 日志、指标、错误响应和审计不保存任何密码内容或 hash。
- 修改已有密码后吊销其他 Session。

### 8.2 已接受风险

首版允许一个仍然有效但建立时间较早的第三方登录 Session 直接覆盖密码。如果该 Session 被窃取，攻击者可能借此建立长期密码登录方式。这不是跨用户授权漏洞，因为操作仍限定在当前 Session 对应的用户，但会放大被盗 Session 的持续访问能力。

当前阶段接受该风险，原因是：

- OIDC/OAUTH2 本身是受信认证方式；
- 当前项目处于小规模封闭内测；
- 强制第三方二次认证会显著增加回调状态、前端跳转和失败恢复复杂度；
- 接口设计已经保留未来增加近期认证判断的空间。

未来提高安全等级时，可以在第三方登录分支增加以下任一门槛，而不改变 `PUT /api/auth/password` 的外部契约：

- 当前第三方登录 Session 的认证时间不超过固定窗口；
- 改密前执行 provider 支持的强制重新认证；
- 要求用户输入现有密码，或在没有密码时执行第三方二次认证。

## 9. 可观测性

新增低基数 Micrometer 指标：

| 指标 | 标签 | 说明 |
| --- | --- | --- |
| `algo_mentor_auth_password_updates_total` | `method=PASSWORD/OIDC/OAUTH2`、`operation=CREATED/UPDATED`、`outcome=success/failure` | 用户密码新增或修改次数 |
| `algo_mentor_auth_password_update_failures_total` | `reason=invalid_request/current_password/concurrent/storage/session_revocation` | 失败分类 |
| `algo_mentor_auth_password_session_revocations_total` | 无 | 改密后吊销的其他 Session 数量 |

日志只记录：

- `userId`；
- Session 认证方式；
- `CREATED` 或 `UPDATED`；
- 吊销 Session 数量；
- 稳定错误码。

日志不得记录邮箱、密码、密码长度、hash、Cookie、Authorization、完整 Session ID、OAuth/OIDC token 或 provider 原始 claims。

用户主动改密不复用管理员审计表，避免把普通用户行为混入管理员操作审计。后续如建设用户安全事件中心，应使用独立事件模型。

## 10. 测试计划

### 10.1 后端单元测试

`UserPasswordServiceTest` 至少覆盖：

- 密码 Session 提交正确原密码后更新成功；
- 密码 Session 缺少原密码被拒绝；
- 密码 Session 原密码错误被拒绝；
- OIDC/OAUTH2 Session 无密码凭据时新增成功；
- OIDC/OAUTH2 Session 已有密码凭据时覆盖成功；
- 第三方登录请求即使提交 `currentPassword` 也不依赖该字段授权；
- 不支持的 Authentication 类型被拒绝；
- 临时密码强制改密 Session 被拒绝；
- 新密码过短或两次不一致被拒绝；
- CAS 更新失败返回并发错误；
- 首次新增不吊销其他 Session；
- 更新已有密码吊销其他 Session 并保留当前 Session；
- 密码编码器只接收新密码，明文不进入 repository。

### 10.2 Repository 与集成测试

- 第三方登录 insert-or-replace 在无记录时插入普通密码凭据；
- 第三方登录 insert-or-replace 在有记录时更新同一行，不创建第二条记录；
- 两条并发第三方登录请求最终仍只有一个 `user_id` 凭据；
- 第三方登录首次设置且内部邮箱缺失时被拒绝；
- 密码 CAS SQL 只在 expected hash 匹配时更新；
- 更新后清理临时密码和 `reset_by` 字段；
- `password_changed_at` 与 `updated_at` 正确写入；
- `revokeOtherSessionsForUser` 跳过当前 Session；
- `GET /api/auth/me` 返回正确的密码状态和 Session 认证方式；
- `PUT /api/auth/password` 未登录和缺失 CSRF token 时被拒绝。

### 10.3 前端测试

`SettingsPage.test.tsx` 至少覆盖：

- 无密码时显示“设置密码”；
- 有密码时显示“修改密码”；
- 密码 Session 弹窗显示原密码字段；
- OIDC/OAUTH2 Session 弹窗不显示原密码字段；
- 两次新密码不一致时不发送请求；
- 提交期间不能重复提交；
- 成功后更新 `passwordConfigured` 并关闭弹窗；
- 后端原密码错误、并发冲突和服务失败时显示对应错误；
- 中英文模式下核心按钮和错误提示可用。

建议的最小验证命令：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test
npm --cache ./.npm --prefix frontend test -- SettingsPage
```

## 11. 实施顺序

1. 提取统一密码约束，新增认证方式枚举与当前认证上下文解析器。
2. 扩展密码 Repository，完成密码 CAS 更新与第三方登录 insert-or-replace。
3. 扩展 Session 吊销端口，实现“保留当前、吊销其他”。
4. 实现 `UserPasswordService`、事务执行器、错误码和单元测试。
5. 增加 `PUT /api/auth/password` 及 Controller 安全测试。
6. 扩展 `CurrentUserResponse` 和前端 `CurrentUser` 类型。
7. 在设置页增加登录密码状态、弹窗、API 调用和国际化文案。
8. 运行认证模块与设置页最小测试，并补充 PostgreSQL 集成验证。

## 12. 发布与回滚

本功能不需要数据库迁移和新增环境变量，可以随应用版本直接发布。

发布后需要观察：

- 第三方登录新增密码成功率；
- 原密码错误比例；
- 并发更新冲突；
- Session 吊销失败；
- `GET /api/auth/me` 新字段对前端兼容性的影响。

代码回滚不会破坏已经新增或更新的密码凭据。旧版本仍会通过现有密码登录逻辑识别这些记录，因此用户通过新版本设置的密码在回滚后仍然有效。若因安全事件需要禁用该能力，应优先下线 `PUT /api/auth/password` 与设置页入口，不删除用户已有密码凭据。

## 13. 验收标准

- 密码登录用户可以使用原密码修改新密码。
- 第三方登录用户无论此前是否设置过密码，都可以不输入原密码直接设置新密码。
- 前端无法通过伪造字段让密码 Session 走第三方登录免原密码分支。
- 第三方登录首次设置后，可以使用同一用户邮箱和新密码完成密码登录，原第三方登录仍然可用。
- 修改已有密码后，旧密码无法登录，当前 Session 保持可用，其他 Session 被吊销。
- 临时密码强制改密流程不被通用改密接口绕过。
- 数据库中同一用户始终最多只有一条密码凭据。
- 日志、指标和错误响应中不出现密码、密码 hash、Token 或完整 Session ID。
- 后端认证相关测试和前端设置页测试全部通过。
