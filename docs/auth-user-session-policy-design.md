# 用户会话策略技术设计

> 设计日期：2026-07-23  
> 适用范围：`backend/auth`、`backend/policy`、Spring Session JDBC  
> 技术基线：Java 17、Spring Security、Spring Session JDBC、PostgreSQL、通用策略底座

---

## 0. 已定决策

1. 新增用户会话策略类型 `auth.user-session.v1`，由 `auth` 模块注册为 `GenericPolicyType<UserSessionPolicy>`。
2. 策略内容包含单账号最大有效会话数和会话绝对超时；策略未命中时使用代码默认值：最多 `2` 个会话、绝对超时 `1` 天。
3. 同一用户命中多条策略时，完全遵循通用策略底座的全局优先级，数字越小越优先；不按“指定用户、用户组、全体用户”自动提高优先级，也不合并多条策略。
4. 在登录成功阶段执行会话数控制。新会话计入上限，超过上限时按会话创建时间升序删除最早创建的其他会话，当前刚登录的会话不删除。
5. 第一版不保证并发登录下的严格会话上限。多个请求同时检查和删除时可能短暂超过阈值；后续一次登录会重新按策略收敛。
6. 策略在会话创建时快照。策略内容、优先级、用户组成员关系后续变化均只影响之后的新登录，不扫描或主动删除既有会话。
7. 绝对超时是“登录成功时刻加固定时长”的硬截止，不是 Spring Session 的滑动空闲超时。每个已认证请求都必须校验固定截止时刻。
8. 通用策略查询未命中时才使用默认值；策略缓存、数据库、编译或内容校验失败时拒绝本次登录，不得静默回退为默认值。
9. 第一版不新增会话槽位表、分布式锁、普通用户会话管理 API 或新的 Spring Session 表迁移。
10. 会话到期或被策略踢下线只保证后续请求无法认证；不主动中断已经建立的 SSE、正在处理的 HTTP 请求或后台任务。

## 1. 背景

当前认证使用 Spring Session JDBC 保存认证会话。`PRINCIPAL_NAME` 使用用户 ID 字符串，可通过 `FindByIndexNameSessionRepository.findByPrincipalName` 找到同一账号的 Session；已有 `AuthSessionRevocationService` 可以删除单个会话和按用户删除全部会话。

现有 `spring.session.timeout` 与 `algo-mentor.auth.session-timeout` 都是七天，表达的是最后访问后的空闲超时。它不能满足“用户持续操作也必须在登录一天后失效”的绝对超时要求，也不能根据用户、用户组配置不同的会话数限制。

通用策略底座已经提供按用户、用户组、全部用户匹配、同类策略全局排序，以及运行时返回至多一条强类型策略的能力。用户会话策略只定义其内容和消费逻辑，不重建范围、优先级或缓存机制。

## 2. 目标与非目标

### 2.1 目标

- 允许管理员为全部用户、用户组或指定用户配置会话数量与绝对超时。
- 未配置任何命中策略时，稳定使用 `2` 个会话和 `1` 天绝对超时。
- 每次成功登录后保留新会话，按创建时间踢出必要数量的最早会话。
- 在每个已认证请求上可靠执行会话绝对到期，避免持续访问延长硬截止。
- 复用 Spring Session 的用户索引和删除能力，不暴露或记录 Cookie Session ID。
- 保持用户禁用、删除、移出内测白名单、重置密码时的按用户全部下线语义不变。

### 2.2 非目标

- 不保证两个或更多并发登录请求期间始终不超过会话上限。
- 不在策略降级、修改、删除、用户组成员变化后主动清理既有会话。
- 不提供设备名、IP、地理位置、User-Agent、会话备注或普通用户侧设备管理。
- 不把多条命中策略做字段合并、取最小值、取最大值或范围特异性覆盖。
- 不把会话绝对到期扩展为长连接终止、AI 任务取消或即时 Presence 系统。
- 不修改 Spring Session JDBC 表结构，也不将 Session ID 保存到新的业务表。

## 3. 策略契约

### 3.1 类型与内容

`auth` 模块新增会话策略常量和强类型内容：

```java
public final class AuthSessionPolicyConstants {

  public static final String TYPE_CODE = "auth.user-session.v1";
  public static final int DEFAULT_MAX_SESSIONS = 2;
  public static final long DEFAULT_ABSOLUTE_TIMEOUT_SECONDS = 86_400L;

  private AuthSessionPolicyConstants() {
  }
}

public record UserSessionPolicy(
    int maxSessions,
    long absoluteTimeoutSeconds
) {
}
```

管理员通过通用策略管理 API 写入的 `content` 使用以下 JSON：

```json
{
  "maxSessions": 2,
  "absoluteTimeoutSeconds": 86400
}
```

`UserSessionPolicy` 的业务校验由 `auth` 模块负责，不能依赖通用策略底座的 JSON 反序列化成功：

- `maxSessions` 必须为正整数；
- `absoluteTimeoutSeconds` 必须为正整数，并可安全转换为 `Duration` 和 Servlet Session 的秒数；
- 不接受未知业务字段的语义扩展；新增字段时使用新的 `typeCode` 版本或明确的兼容规则。

首版实施时，数值上下限、字段名和策略类型字符串必须收敛到 `AuthSessionPolicyConstants` 或 `UserSessionPolicyConstraints`，不得在 DTO、服务、过滤器和测试中散落字面量。

### 3.2 匹配与默认值

会话策略解析器调用：

```java
genericPolicyQueryService.resolve(USER_SESSION_POLICY_TYPE, userId)
```

行为如下：

| 解析结果 | 生效配置 |
| --- | --- |
| 命中一条启用策略 | 使用该策略的内容、策略 ID 与版本 |
| `Optional.empty()` | 使用 `2` 个会话、`86_400` 秒；策略 ID 与版本为空 |
| 读取、缓存加载、编译、反序列化或语义校验异常 | 登录失败，返回会话策略不可用错误 |

同一个用户可能同时属于多个组，也可能被指定用户策略和全体用户策略同时命中。通用策略只返回全局优先级最高的一条。例如要让全体用户使用默认的可配置基线、某个用户组使用更宽松的限制，管理员必须将用户组策略排在全体用户策略之前。

建议的排序示例：

| 优先级 | 范围 | `maxSessions` | 绝对超时 |
| --- | --- | --- | --- |
| 1 | 指定用户：42 | 1 | 2 小时 |
| 2 | 用户组：内部管理员 | 5 | 7 天 |
| 3 | 全部用户 | 2 | 1 天 |

如果没有第 3 条策略，未命中用户仍使用代码默认值，而不是产生一条隐式数据库策略。

### 3.3 策略快照

每次成功登录使用当时解析的策略创建会话快照。会话中保存最小必要属性：

| Session 属性常量 | 作用 |
| --- | --- |
| `AUTH_SESSION_ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS` | 绝对到期的 UTC epoch milliseconds，过滤器的权威判断依据 |
| `AUTH_SESSION_POLICY_ID` | 命中的通用策略 ID；默认值场景为空 |
| `AUTH_SESSION_POLICY_VERSION` | 命中的策略版本；默认值场景为空 |

属性名由 `AuthSessionAttributeNames` 统一定义。属性中不保存策略原始 JSON、密码、令牌、IP、完整 Session ID 或用户隐私内容。

快照意味着策略后续更新、禁用、删除、重排，以及用户组成员关系变化，均不会修改已创建会话。管理员降低上限后，也不会立即清理超额会话；该账号下一次成功登录才触发新的上限检查。

## 4. 会话时间语义

### 4.1 空闲超时与绝对超时

当前全局 `AUTH_SESSION_TIMEOUT` 保留为 Session 空闲超时，默认七天。新策略增加的是每个会话不可延长的绝对截止：

```text
absoluteExpiresAt = 登录成功时刻 + policy.absoluteTimeout

有效截止 = min(
  最后访问时刻 + AUTH_SESSION_TIMEOUT,
  absoluteExpiresAt
)
```

因此默认策略下，即使浏览器持续发送请求，一个会话最多在登录成功后存活一天；用户长时间不访问时，仍可能因全局空闲超时更早失效。

### 4.2 登录时初始化

认证成功并创建当前 HTTP Session 后：

1. 使用统一 `Clock` 获取 `now`。
2. 解析用户会话策略或取得代码默认值。
3. 计算 `absoluteExpiresAt = now + absoluteTimeout`。
4. 写入策略快照属性。
5. 将当前 Session 的 `maxInactiveInterval` 设为 `min(globalIdleTimeout, absoluteExpiresAt - now)`。

第 5 步使没有后续访问的 Session 会在绝对截止前自然过期，也保证 Spring Session JDBC 的 `EXPIRY_TIME` 不晚于绝对超时。

### 4.3 每请求硬截止

新增 `AuthSessionAbsoluteTimeoutFilter`，放在认证上下文已恢复之后、业务 Controller 之前。它只消费 Session 快照，不在每个请求重新查询通用策略。

对带有 `AUTH_SESSION_ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS` 的已认证请求：

```text
now >= absoluteExpiresAt
  -> 删除当前 Session
  -> 清空 SecurityContext
  -> 按现有认证失败协议返回未认证响应

now < absoluteExpiresAt
  -> session.maxInactiveInterval = min(globalIdleTimeout, absoluteExpiresAt - now)
  -> 放行请求
```

第二个分支很重要：Spring Session 会在访问后按最后访问时间重新计算过期时间。每次把 `maxInactiveInterval` 收紧为剩余时间，才能避免频繁访问将 JDBC 会话续到绝对截止之后。

首版不主动中断已经建立的 SSE。连接建立时会经过该过滤器；连接持续到绝对截止之后时，只有其后的新请求会被拒绝。这与现有管理员会话下线的连接语义保持一致。

### 4.4 历史会话兼容

发布前已经存在的会话没有绝对到期属性。首版将其视为历史会话：继续遵循现有七天空闲超时，不在发布时批量下线，也不在第一次访问时补写新策略快照。用户重新登录后，新创建的会话才受用户会话策略控制。

## 5. 登录阶段的会话数控制

### 5.1 统一调用位置

密码注册、密码登录和 OAuth2/OIDC 登录都必须使用同一个 `AuthSessionPolicyLoginService`：

```text
认证凭据或 OAuth2 身份验证成功
  -> 保存当前 SecurityContext，获得当前 Session
  -> AuthSessionPolicyLoginService.apply(userId, currentSession)
      -> 解析会话策略并写入快照
      -> 查询用户的有效其他 Session
      -> 按创建时间淘汰最早会话
  -> 返回登录成功或跳转
```

密码登录在 `PasswordAuthController.saveAuthentication` 之后调用该服务。OAuth2/OIDC 成功处理器在委托跳转前调用同一服务。这样两条认证链路不会出现不同的会话上限或超时语义。

### 5.2 有效会话与淘汰顺序

通过 `FindByIndexNameSessionRepository.findByPrincipalName(Long.toString(userId))` 查询会话，候选集合满足：

```text
sessionId != currentSessionId
AND session 未过期
```

候选会话仅在服务内部按以下顺序排序：

```text
creationTime ASC, sessionId ASC
```

第二排序键只用于创建时间相同时的稳定性，Session ID 不进入响应、日志、指标或审计。

设有效其他会话数为 `existingCount`，策略上限为 `maxSessions`，当前新会话始终计入总数：

```text
revokeCount = max(0, existingCount + 1 - maxSessions)
```

服务删除排序最前的 `revokeCount` 个候选会话。删除后，串行登录场景满足：

```text
当前会话 + 保留的其他有效会话 <= maxSessions
```

服务不删除当前会话，即使当前 Session 因框架保存时机尚未出现在按用户查询的结果中，也按上式显式计入一次。

### 5.3 并发语义

第一版不引入用户级 PostgreSQL 锁、会话槽位表或分布式锁。两个请求可同时读取同一批旧会话，再各自创建一个新会话，因而可能超过阈值。这是已接受的边界，不得在实现中宣称严格并发安全。

正常串行登录会再次按创建时间清理多余会话。未来若安全等级要求严格上限，应单独设计用户会话槽位或将 Session 创建、索引查询和删除纳入同一用户级事务锁，不在本设计内补丁式加入。

### 5.4 失败处理

| 失败场景 | 行为 |
| --- | --- |
| 策略未命中 | 使用代码默认值，继续登录 |
| 策略解析或校验失败 | 拒绝登录；不回退默认值 |
| Session 查询失败 | 拒绝登录 |
| 淘汰 Session 失败 | 拒绝登录，并删除当前刚创建的 Session；已删除的旧会话不尝试恢复 |
| 当前 Session 写入快照失败 | 拒绝登录，并删除当前 Session |

密码登录使用统一 API 错误响应，建议错误码为 `AUTH_SESSION_POLICY_UNAVAILABLE`，HTTP `503`。OAuth2/OIDC 登录无法返回同样的 JSON 时，使用明确失败页或登录页错误状态；不得在策略失败时继续完成登录跳转。

## 6. 模块与代码组织

### 6.1 依赖方向

`backend/auth` 新增对 `backend/policy` 的 Maven 依赖：

```text
common       cache
   ^           ^
   |           |
identity ------+
   ^
   |
policy
   ^
   |
auth
```

`policy` 不依赖 `auth`。会话策略的 DTO、类型常量、解析器、登录服务和过滤器都归属 `auth`，避免通用底座理解 Spring Session 细节。

### 6.2 建议包结构

```text
backend/auth/src/main/java/org/congcong/algomentor/auth/session/policy
  AuthSessionPolicyConstants.java
  AuthSessionAttributeNames.java
  UserSessionPolicy.java
  UserSessionPolicyConstraints.java
  ResolvedUserSessionPolicy.java
  AuthSessionPolicyResolver.java
  AuthSessionPolicyLoginService.java
  AuthSessionAbsoluteTimeoutFilter.java
  AuthSessionPolicyMetrics.java
  MicrometerAuthSessionPolicyMetrics.java
  NoopAuthSessionPolicyMetrics.java
```

会话枚举、删除和 Spring Session 适配仍放在现有 `auth.session` 包。若枚举查询与删除能力增长明显，可新增面向内部的 `AuthSessionRepository` 端口与 Spring Session 实现；Controller、登录服务和过滤器不直接散落 `FindByIndexNameSessionRepository` 调用。

### 6.3 自动配置与注册

`AuthApiAutoConfiguration` 注册：

- `GenericPolicyType<UserSessionPolicy>` Bean；
- `AuthSessionPolicyResolver`；
- `AuthSessionPolicyLoginService`；
- 会话策略指标实现；
- 必要时的 Spring Session 查询适配器。

`AuthSecurityAutoConfiguration` 将绝对超时过滤器加入现有认证过滤器链，并把登录成功处理器替换为注入 `AuthSessionPolicyLoginService` 的实现。所有构造器依赖保持可替换，缺少策略或 Session Repository Bean 时不应悄悄禁用生产控制；完整认证应用中缺少必要依赖应在启动或登录时明确失败。

## 7. 可观测性与安全

新增低基数 Micrometer 指标：

| 指标 | 标签 | 说明 |
| --- | --- | --- |
| `algo_mentor_auth_session_policy_resolutions_total` | `source=policy/default`、`outcome=success/failure` | 策略解析与默认回退 |
| `algo_mentor_auth_session_policy_evictions_total` | 无或有限原因标签 | 登录阶段被淘汰的会话数量 |
| `algo_mentor_auth_session_absolute_expirations_total` | 无 | 被硬截止拒绝的会话数量 |
| `algo_mentor_auth_session_policy_failures_total` | `operation=resolve/query/revoke/snapshot` | 控制链路失败 |

日志仅记录策略类型、策略 ID、策略版本、淘汰数量、失败操作和异常堆栈；不记录完整策略 JSON、完整 Session ID、Cookie、Authorization 头、密码或 IP。通用策略写入继续使用其既有管理员审计；自动淘汰会话不逐条写管理员审计，避免高频自动操作产生噪声。

## 8. 测试计划

### 8.1 单元测试

- `AuthSessionPolicyResolver`：命中策略、未命中默认、语义非法、通用策略异常不回退。
- `AuthSessionPolicyLoginService`：零个/刚好上限/超过上限、当前会话不在查询结果中、过期会话不计数、按创建时间淘汰、同时间稳定决胜、删除失败后当前会话清理。
- `AuthSessionAbsoluteTimeoutFilter`：未标记历史会话放行、未到期放行并收紧空闲超时、刚好到期删除并拒绝、已经超过截止删除并拒绝。
- `UserSessionPolicy`：正数与可安全转换的边界校验。

### 8.2 集成与链路测试

- 密码注册和密码登录都会写入快照并实施会话上限。
- OAuth2/OIDC 成功处理器在跳转前实施相同策略；使用测试认证对象，不依赖真实第三方。
- 指定用户、用户组、全体用户同时命中时，验证通用策略全局优先级结果。
- 策略更新后，新登录使用新值，已有标记 Session 的截止时间不变化。
- 策略降低会话上限后，不触发后台清理；下一次登录才按新上限淘汰。
- Spring Session JDBC 中 `EXPIRY_TIME` 不晚于快照的绝对截止。

不编写“并发登录绝不超过上限”的验收测试；可保留一个显式测试或设计说明，证明该场景属于首版接受的最终收敛行为，而非遗漏。

## 9. 发布与回滚

1. 先确保通用策略模块及其已有迁移已部署，且策略管理 API 可识别 `auth.user-session.v1`。
2. 发布 `auth` 对 `policy` 的依赖、类型注册、登录控制和绝对超时过滤器。
3. 初始不必写入全体用户策略，未命中用户自动使用 `2` 个会话与 `1` 天默认值。
4. 观察策略解析失败、登录失败、会话淘汰和绝对到期指标；特别关注 OAuth2 登录失败路径。
5. 回滚应用版本即可停止对新 Session 写入策略快照。已写入的属性对旧版本无害，历史 Session 和未过期 Spring Session 不需要数据回滚。

回滚不恢复已被会话上限淘汰的 Session；它们已被安全删除，用户需要重新登录。

