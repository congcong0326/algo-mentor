# Spring Security 中的 SecurityContext、Authentication 与 JSESSIONID

更新时间：2026-07-22

## 核心结论

在基于 Session 的 Spring Security 应用中，`Authentication`、`SecurityContext` 和 `JSESSIONID` 分别解决三个不同问题：

- `Authentication`：描述当前用户是谁、是否已认证、拥有哪些角色或权限。
- `SecurityContext`：保存当前请求的 `Authentication`。
- `JSESSIONID`：浏览器持有的 Session 标识，服务端用它找到跨请求保存的 `SecurityContext`。

三者的关系可以概括为：

```text
浏览器 Cookie: JSESSIONID
        ↓ 定位
服务端 Session
        ↓ 保存
SecurityContext
        ↓ 包含
Authentication
        ↓ 描述
当前用户、角色和认证状态
```

`algo-mentor` 当前使用的是 Spring Security + Spring Session JDBC + HttpOnly Cookie，而不是前端保存 JWT 的登录方案。

## Authentication：当前用户的身份表示

### Authentication 保存什么

`Authentication` 是 Spring Security 对“当前身份”的统一抽象，主要包含以下信息：

| 属性 | 作用 |
|---|---|
| `principal` | 当前用户主体，例如用户对象、用户名或 OAuth2 用户 |
| `credentials` | 认证凭据，登录前通常是密码，认证成功后应尽快清理 |
| `authorities` | 角色或权限，例如 `ROLE_USER`、`ROLE_ADMIN` |
| `authenticated` | 当前对象是否已经通过可信认证流程 |
| `details` | IP、Session ID 等附加请求信息，是否存在取决于具体实现 |
| `name` | 当前主体的统一名称 |

它既可以表示“等待认证的登录请求”，也可以表示“已经认证成功的用户”。例如：

```java
// 登录前：只携带用户提交的邮箱和密码，尚未认证。
Authentication requestToken =
    UsernamePasswordAuthenticationToken.unauthenticated(email, password);

// 认证成功后：AuthenticationManager 返回受信任的 Authentication。
Authentication authentication =
    authenticationManager.authenticate(requestToken);
```

不要把用户自己提交的字段直接包装成“已认证”的 `Authentication`。通常应由 `AuthenticationManager` 和 `AuthenticationProvider` 校验凭据后，创建认证成功的对象。

### algo-mentor 中的 Authentication

项目的密码登录通过 `AuthenticationManager` 进入 `AuthenticatedDaoAuthenticationProvider`，由 `PasswordUserDetailsService` 加载账号和角色，并使用 BCrypt 校验密码。

认证成功后，`principal` 通常是项目自己的 `AuthenticatedUserPrincipal`，其中包含：

- `userId`
- 邮箱和显示名称
- 头像地址
- `USER`、`ADMIN` 等角色
- 账号状态
- 是否必须修改临时密码

Google OAuth2/OIDC 登录也会转换到同一套项目用户主体，使业务代码不必分别处理密码用户和 Google 用户。

项目将本地角色转换为 Spring Security authority：

```text
USER  -> ROLE_USER
ADMIN -> ROLE_ADMIN
```

因此配置中的 `hasRole("ADMIN")` 实际检查的是 `ROLE_ADMIN`。

相关代码：

- [`PasswordAuthController`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/controller/PasswordAuthController.java)
- [`AuthenticatedDaoAuthenticationProvider`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedDaoAuthenticationProvider.java)
- [`PasswordUserDetailsService`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/PasswordUserDetailsService.java)
- [`AuthenticatedUserPrincipal`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedUserPrincipal.java)
- [`AuthAuthorities`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthAuthorities.java)

## SecurityContext：当前请求的安全上下文

### SecurityContext 的职责

`SecurityContext` 是一个很薄的容器，核心职责就是保存当前 `Authentication`。可以近似理解为：

```java
class SecurityContext {
    Authentication authentication;
}
```

业务代码通常通过 `SecurityContextHolder` 访问它：

```java
Authentication authentication =
    SecurityContextHolder.getContext().getAuthentication();
```

默认情况下，`SecurityContextHolder` 使用与当前线程关联的存储策略。因此，同一个 HTTP 请求处理链中的过滤器、Controller 和 Service 可以访问同一份安全上下文。

请求结束后，Spring Security 会清理当前线程上的上下文，避免线程池复用线程时把前一个请求的用户身份泄漏给后一个请求。

### SecurityContext 如何跨请求保存

仅把 `SecurityContext` 放进 `SecurityContextHolder`，只能满足当前请求。为了让下一次请求仍然保持登录状态，还要通过 `SecurityContextRepository` 保存它。

项目登录成功后的核心逻辑是：

```java
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
securityContextRepository.saveContext(context, request, response);
```

项目使用 `HttpSessionSecurityContextRepository`，因此 `SecurityContext` 会进入 HTTP Session。后续请求到来时，Spring Security 再从 Session 恢复上下文。

### 项目为什么又封装 CurrentUserIdProvider

业务模块没有到处直接调用 `SecurityContextHolder`，而是通过 `CurrentUserIdProvider` 获取当前用户。它主要带来以下好处：

- 隔离业务代码与 Spring Security API。
- 统一兼容密码登录和 OAuth2 登录的 principal 类型。
- 便于单元测试中替换或模拟当前用户。
- 避免让 Controller 信任客户端传入的 `userId`。

例如能力画像、学习计划、练习记录和错题复盘接口，会从安全上下文取得可信 `userId`，再查询该用户的数据。

相关代码：

- [`SecurityContextCurrentUserIdProvider`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/SecurityContextCurrentUserIdProvider.java)
- [`AuthApiAutoConfiguration`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java)
- [`AbilityProfileController`](../../backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/ability/AbilityProfileController.java)

## JSESSIONID：浏览器持有的 Session 标识

### JSESSIONID 是什么

`JSESSIONID` 通常是服务器生成的一段随机 Session ID，浏览器通过 Cookie 保存并在后续请求中自动携带：

```http
Cookie: JSESSIONID=8F4A9C3D2B...
```

`JSESSIONID` 本身通常不直接保存用户 ID、邮箱、角色或密码。它只是服务端 Session 的索引：

```text
JSESSIONID
    ↓
服务端查找对应 Session
    ↓
从 Session 恢复 SecurityContext
    ↓
取得 Authentication
```

这与 JWT 不同。JWT 往往把声明和签名放进 Token 自身，服务端可以验证 Token 后读取其中的声明；`JSESSIONID` 主要负责定位服务端保存的状态。

### algo-mentor 如何保存 Session

项目使用 Spring Session JDBC，将 Session 存储到 PostgreSQL，而不是只保存在某个 Java 进程的内存中。这意味着：

- 应用重启后，未过期 Session 可以继续存在。
- 多个应用实例可以共享 Session 数据。
- 可以按用户查找并集中撤销 Session。

当前默认 Session 有效期是 7 天。Session Cookie 配置包括：

- `HttpOnly=true`：浏览器 JavaScript 不能直接读取 `JSESSIONID`。
- `Secure`：生产环境可以限制为只通过 HTTPS 发送。
- `SameSite=Lax`：降低部分跨站请求风险。

用户退出时，Spring Security 会使 Session 失效并删除 `JSESSIONID` Cookie。用户被禁用或删除时，项目也会按用户 ID 查找并撤销其所有 Session。

相关代码和配置：

- [`AuthSecurityAutoConfiguration`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthSecurityAutoConfiguration.java)
- [`SpringSessionAuthSessionRevocationService`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/session/SpringSessionAuthSessionRevocationService.java)
- [`application.yml`](../../backend/mentor-api/src/main/resources/application.yml)

## 一次完整的登录与请求过程

### 首次登录

```text
1. 浏览器提交邮箱和密码
2. PasswordAuthController 创建未认证 Authentication
3. AuthenticationManager 调用 AuthenticationProvider
4. UserDetailsService 加载账号、密码摘要和角色
5. PasswordEncoder 校验密码
6. 认证成功，返回已认证 Authentication
7. Authentication 放入 SecurityContext
8. SecurityContext 保存到服务端 Session
9. 响应向浏览器写入 JSESSIONID Cookie
```

### 后续访问业务接口

```text
1. 浏览器自动携带 JSESSIONID
2. Spring Session 根据 JSESSIONID 找到 Session
3. Spring Security 从 Session 恢复 SecurityContext
4. 从 Authentication 判断用户是否已登录
5. 根据 authorities 检查接口角色要求
6. 自定义过滤器检查账号状态和临时密码状态
7. Controller 从 CurrentUserIdProvider 取得可信 userId
8. 业务 Service 按 userId 查询和修改数据
```

### 退出或账号被禁用

退出登录时，Spring Security 清理认证信息、使当前 Session 失效，并删除 Cookie。

账号状态变为 `DISABLED` 或 `DELETED` 时，项目会主动撤销该用户的所有 Session。即使某个旧浏览器仍保存原来的 `JSESSIONID`，对应的服务端 Session 已经不存在，也不能继续恢复登录身份。

## JSESSIONID 与 CSRF 的关系

浏览器发送同站请求时会自动携带 Cookie，包括 `JSESSIONID`。便利的同时也产生了 CSRF 风险：恶意网站可能诱导浏览器向已登录系统发送修改请求，而浏览器仍会自动带上登录 Cookie。

因此，`HttpOnly` 和 `SameSite` 不能完全替代 CSRF Token。项目还使用：

- `XSRF-TOKEN` Cookie 向前端提供 CSRF Token。
- `X-XSRF-TOKEN` 请求头提交 Token。
- Spring Security 在修改类请求中校验 Token。

`JSESSIONID` 证明浏览器持有某个登录 Session，CSRF Token 则进一步证明这个修改请求是由了解页面上下文的合法前端发出的。

相关代码：

- [`CsrfTokenCookieFilter`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/CsrfTokenCookieFilter.java)
- [`SpaCsrfTokenRequestHandler`](../../backend/auth/src/main/java/org/congcong/algomentor/auth/security/SpaCsrfTokenRequestHandler.java)

## 三者对比

| 概念 | 核心问题 | 典型内容 | 保存位置 |
|---|---|---|---|
| `Authentication` | 当前用户是谁 | principal、authorities、认证状态 | 服务端 SecurityContext 中 |
| `SecurityContext` | 当前请求使用哪一个身份 | 一个 Authentication | 当前请求线程，并可持久化到 Session |
| `JSESSIONID` | 如何找到跨请求保存的 Session | 随机 Session ID | 浏览器 Cookie |

一句话记忆：

```text
Authentication 是身份，SecurityContext 装身份，JSESSIONID 帮浏览器找回装有身份的 Session。
```

## 常见误区

### 误区一：JSESSIONID 里保存了用户信息

通常没有。它主要是一个随机索引，真正的登录状态保存在服务端 Session 中。

### 误区二：拿到 JSESSIONID 也没有关系，因为它不是密码

错误。有效的 `JSESSIONID` 相当于当前登录 Session 的持有凭证。攻击者如果窃取它，可能直接冒用用户身份，这类攻击称为 Session Hijacking。

因此必须使用 HTTPS，并合理配置 `HttpOnly`、`Secure`、`SameSite`、Session 过期时间和退出失效机制。

### 误区三：SecurityContext 会永久保存在 SecurityContextHolder 中

不会。`SecurityContextHolder` 主要承载当前请求线程的上下文。跨请求登录状态依赖 `SecurityContextRepository` 和 Session；请求结束后线程上下文应被清理。

### 误区四：isAuthenticated() 为 true 就一定是项目用户

不一定。Spring Security 可能存在匿名 Authentication，其他认证方式也可能使用不同 principal 类型。项目的 `CurrentUserIdProvider` 除了检查认证状态，还会检查 principal 是否是受支持的项目用户类型。

### 误区五：接入 Spring Security 就自动解决了全部权限问题

Spring Security 可以统一处理登录校验、角色校验和方法权限，但不会自动理解业务数据归属。

例如用户只能修改自己的学习计划，仍需要业务层使用安全上下文中的 `userId` 查询和校验数据。不能只判断“已登录”，更不能信任请求体中的 `userId`。

### 误区六：前端拿到 permission 字符串就构成了安全控制

前端权限适合控制菜单、按钮和路由展示，但前端代码可以被绕过。真正的安全边界必须由后端过滤链、方法权限和业务数据校验共同执行。

## 面试表达

可以这样回答三者之间的关系：

> `Authentication` 是 Spring Security 对当前身份的抽象，保存 principal、authorities 和认证状态；`SecurityContext` 是保存当前 Authentication 的容器，在请求处理期间通常由 `SecurityContextHolder` 暴露；对于 Session 登录，SecurityContext 会保存到服务端 HttpSession，浏览器只持有用于定位该 Session 的 `JSESSIONID` Cookie。后续请求携带 JSESSIONID，服务端恢复 SecurityContext，再完成认证和授权判断。

结合 `algo-mentor` 可以继续补充：

> 项目使用 Spring Session JDBC 把 Session 保存到 PostgreSQL，并通过 HttpOnly Cookie 维护登录状态。业务接口不会信任客户端传入的 userId，而是从 SecurityContext 中解析当前用户。管理员接口由 `ROLE_ADMIN` 控制，同时业务层继续负责学习计划、练习记录等数据的归属校验。

## 可继续深入的方向

- `SecurityFilterChain` 中各过滤器的顺序和职责。
- `AuthenticationManager`、`ProviderManager`、`AuthenticationProvider` 的协作过程。
- `UserDetailsService` 与 `PasswordEncoder` 的密码认证流程。
- Session 登录与 JWT 登录的安全模型和适用场景。
- Session Fixation、Session Hijacking 与登录后 Session ID 轮换。
- CSRF、CORS、Cookie SameSite 三者的区别。
- 异步任务和线程池中的 `SecurityContext` 传播问题。
- URL 级、方法级和数据级授权的职责划分。
