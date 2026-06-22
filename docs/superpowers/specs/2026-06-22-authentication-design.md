# 认证体系设计

## 背景

`algo-mentor` 当前以后端 Java/Maven 多模块为主，`mentor-api` 承担 Spring MVC API、SSE adapter、配置装配和 Flyway 迁移扫描职责。项目已有 PostgreSQL、Flyway、MyBatis、Micrometer、Logback 等基础设施，但尚未形成认证与用户身份体系。

本设计目标是在不把认证逻辑散落到业务 API 的前提下，引入相对成熟的认证方案。当前阶段只支持普通用户通过 Google 账号登录，系统管理员账号、更多用户角色和管理后台只做模型与权限预留。

## 已确认决策

- 新建独立 Maven 模块 `backend/auth`，认证账号、角色、OAuth2 绑定、Spring Security 配置和认证 API 不直接放入业务代码。
- 使用 Spring Security OAuth2 Login 接入 Google 账号登录。
- 使用 HttpOnly Cookie Session 作为登录态，不使用前端保存 JWT。
- 使用 Spring Session JDBC + PostgreSQL 持久化 Session。
- 不实现邮箱密码注册、邮箱密码登录、找回密码和密码 hash 存储。
- 任意 Google 账号首次登录后自动创建本地普通用户。
- 生产部署按前后端同域设计，后端可托管前端静态资源。
- 默认保护 `/api/**`，除健康检查、OAuth2 登录入口/回调、登出、静态资源和必要健康探针外，业务 API 均要求登录。

## 推荐架构

采用一个独立的 `auth` 模块：

- 包名：`org.congcong.algomentor.auth.*`
- Maven 模块：`backend/auth`
- API 应用依赖：`mentor-api` 引入 `auth` 模块，由 `auth` 提供自动装配或显式配置类。
- 认证边界：`auth` 负责登录态、账号同步、角色映射、当前用户解析、认证 API 和数据库迁移；业务模块只消费当前登录用户身份，不直接处理 OAuth2 细节。

暂不拆分 `auth-core` 和 `auth-spring-security`。当前规模下一个独立模块可以保持边界清晰，同时避免过早增加模块数量。后续如果认证领域显著扩大，再按领域模型和框架适配拆分。

## 模块职责

`auth` 模块建议包含以下子包：

- `config`：Spring Security、OAuth2 Client、Spring Session、CSRF、认证属性配置。
- `controller`：`/api/auth/me`、`/api/auth/logout` 等认证相关 HTTP 接口。
- `model`：用户、角色、状态、OAuth2 provider 标识等领域模型，延续 `mentor-api` 现有 `problem/model` 命名习惯。
- `repository`：用户、角色、OAuth2 账号绑定的持久化接口与 MyBatis 实现。
- `service`：Google OAuth2 登录后的用户查找、创建、更新、角色装载、登录时间更新。
- `security`：`Authentication` 到当前用户上下文的适配、authorities 映射、登录成功/失败处理器。

跨模块复用的路径、请求头、角色名、provider 标识、配置 key 和 JSON 字段应抽象为常量类或枚举，避免在 controller、service 和测试中重复字面量。

## 依赖

`auth` 模块建议引入：

- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-client`
- `spring-session-jdbc`
- `spring-boot-starter-jdbc`
- `spring-boot-starter-validation`
- `mybatis`
- `mybatis-spring`
- `postgresql`
- `flyway-core` 由应用层已有依赖提供时可不重复声明
- `lombok` 可选
- `spring-boot-starter-test`、`spring-security-test` 用于测试

`mentor-api` 继续作为最终 Spring Boot 应用，依赖 `auth` 后获得安全过滤链、认证 API 和迁移资源。

## 登录流程

1. 未登录用户访问受保护的 `/api/**` 时，Spring Security 返回未认证响应。前端可跳转到 `/oauth2/authorization/google`。
2. 用户访问 `/oauth2/authorization/google`，Spring Security 启动 Google OAuth2 Login。
3. Google 回调 `/login/oauth2/code/google`，Spring Security 完成授权码交换并获得 OIDC/OAuth2 用户信息。
4. `auth` 模块使用 Google 稳定 subject，即 `sub`，按 `(provider, provider_subject)` 查找 `auth_oauth_accounts`。
5. 找到绑定时，加载本地用户、角色和状态，并更新 provider 侧展示信息与 `last_login_at`。
6. 未找到绑定时，创建 `auth_users` 本地用户，默认 `status=ACTIVE`，默认角色 `USER`，再创建 Google OAuth2 绑定。
7. 登录成功后创建 Spring Session JDBC 记录，并向浏览器写入 HttpOnly Session Cookie。
8. 登录成功跳转到配置的前端地址，默认 `/`。

Google OAuth2 scope 使用 `openid profile email`。本地用户身份以 provider subject 作为绑定依据，email 主要作为展示和联系字段，不作为第三方身份绑定的唯一依据。

## 对外 API

`auth` 模块暴露最小认证 API：

- `GET /api/auth/me`：返回当前登录用户信息。
- `POST /api/auth/logout`：登出，清理服务端 Session 和浏览器 Cookie。
- `/oauth2/authorization/google`：Spring Security 提供的 Google 登录入口。
- `/login/oauth2/code/google`：Spring Security 提供的 Google 登录回调。

`GET /api/auth/me` 响应建议包含：

```json
{
  "id": 1,
  "email": "user@example.com",
  "displayName": "User Name",
  "avatarUrl": "https://example.com/avatar.png",
  "roles": ["USER"],
  "status": "ACTIVE"
}
```

普通未登录状态应返回统一错误响应，避免前端依赖 Spring Security 默认 HTML 登录页。认证异常和权限不足应映射为项目统一 `ApiResponse` / `ApiError` 风格。

## API 保护策略

默认保护 `/api/**`：

- 放行 `/api/health`
- 放行 OAuth2 登录入口 `/oauth2/authorization/**`
- 放行 OAuth2 回调 `/login/oauth2/code/**`
- 放行 `POST /api/auth/logout`，使重复登出或 session 已失效时也能清理浏览器侧状态
- 放行静态资源
- 放行必要 actuator 健康探针，例如 `/actuator/health`、`/actuator/health/**`
- 其他 `/api/**` 均要求认证

业务接口不得信任客户端传入的 `userId`。已有接口如 Agent conversation 的 `userId` 请求字段，应在后续实施中迁移为从 `Authentication` 或 `CurrentUser` 中读取。迁移期间可以先在认证上下文和请求字段并存时做一致性校验，但最终应移除请求体中的用户 ID。

管理员能力当前只预留：

- 角色枚举包含 `USER` 和 `ADMIN`
- authorities 映射为 `ROLE_USER`、`ROLE_ADMIN`
- 后续后台管理接口可使用 `hasRole("ADMIN")`

当前不实现管理员创建、管理员审批、用户封禁后台和权限管理页面。

## Session 与 Cookie

登录态采用 Spring Session JDBC：

- Session 数据存储在 PostgreSQL。
- 浏览器只保存 HttpOnly Session Cookie。
- 服务重启后登录态不丢失。
- 单实例和后续多实例都可复用同一 PostgreSQL Session 存储。

Cookie 建议：

- `HttpOnly=true`
- 生产环境 `Secure=true`
- 同域部署下 `SameSite=Lax`
- Session timeout 通过配置控制，默认可从 7 天起步，后续按安全需求收紧。

不在前端 localStorage/sessionStorage 中保存 access token。

## CSRF

建议启用 CSRF，采用同域 SPA 友好的 Cookie token 策略：

- Session Cookie 保持 HttpOnly。
- CSRF token 通过非 HttpOnly 的 `XSRF-TOKEN` cookie 下发。
- 前端对 `POST`、`PUT`、`PATCH`、`DELETE` 请求带 `X-XSRF-TOKEN` 请求头。
- `GET` 请求不要求 CSRF token。
- OAuth2 登录入口和回调按 Spring Security 默认机制处理。

如果前端尚未统一封装 CSRF header，可在认证功能落地时同步更新 `frontend/src/services/api.ts`。

## 数据库设计

认证模块迁移放在：

`backend/auth/src/main/resources/db/migration/auth`

项目现有 Flyway 会递归扫描 `classpath:db/migration`，新增 `V` 版本号需要和其他模块全局唯一。
当前已存在 `V1` 至 `V7`，认证模块首批迁移从 `V8__auth_schema.sql` 开始。

### `auth_users`

本地用户主表：

- `id`：主键，建议 `bigserial`
- `email`：邮箱，用于展示和联系
- `email_normalized`：小写规范化邮箱，用于唯一约束和查重
- `display_name`：展示名
- `avatar_url`：头像地址
- `status`：用户状态，当前 `ACTIVE`，预留 `DISABLED`
- `created_at`
- `updated_at`
- `last_login_at`

唯一约束：`email_normalized`。当前阶段只接入 Google，按“一个邮箱对应一个本地用户”处理；第三方身份绑定仍以 `(provider, provider_subject)` 为准。

### `auth_user_roles`

用户角色表：

- `user_id`
- `role`
- `created_at`

唯一约束：`(user_id, role)`。

当前角色：

- `USER`
- `ADMIN`

### `auth_oauth_accounts`

第三方账号绑定表：

- `id`
- `user_id`
- `provider`，当前为 `google`
- `provider_subject`，Google 的稳定 `sub`
- `email_at_provider`
- `display_name_at_provider`
- `avatar_url_at_provider`
- `created_at`
- `updated_at`

唯一约束：

- `(provider, provider_subject)`

`provider_subject` 是第三方账号绑定主依据。即使 Google 侧 email 变化，只要 subject 不变，仍应映射到同一本地用户。

### Spring Session 表

使用 Spring Session JDBC 标准表：

- `SPRING_SESSION`
- `SPRING_SESSION_ATTRIBUTES`

为保持当前项目“迁移脚本可审查、可版本化”的风格，Spring Session 表结构通过 Flyway 管理，不依赖运行时自动初始化脚本。

## 配置

建议新增配置前缀：

`algo-mentor.auth`

关键配置：

- `algo-mentor.auth.login-success-url`
- `algo-mentor.auth.logout-success-url`
- `algo-mentor.auth.session-timeout`
- `algo-mentor.auth.cookie-secure`
- `algo-mentor.auth.cookie-same-site`

Spring OAuth2 Client 配置通过标准 Spring Boot 属性接入，并用环境变量注入：

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

示例：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:}
            client-secret: ${GOOGLE_CLIENT_SECRET:}
            scope:
              - openid
              - profile
              - email
```

不要把 Google client secret 写入仓库。`.env.example` 可以只放变量名和说明。

## 前端协作

前端启动时调用 `GET /api/auth/me` 判断登录状态：

- 成功：保存当前用户到前端状态。
- `401`：展示登录入口，点击后跳转 `/oauth2/authorization/google`。

所有 mutating API 请求带 CSRF header。前端已有 `frontend/src/services/api.ts`，认证落地时应在该封装中统一处理：

- 同域请求带 `credentials: "same-origin"`。
- 从 `XSRF-TOKEN` cookie 读取 token。
- 对非 GET 请求设置 `X-XSRF-TOKEN`。

登出使用 `POST /api/auth/logout`，成功后清理前端用户状态并跳转到登录页或首页。

## 可观测性与安全

日志不得输出：

- Google client secret
- Session ID
- 完整 Cookie
- Authorization header
- 用户隐私正文

建议暴露 Micrometer 指标：

- 登录成功次数
- 登录失败次数
- OAuth2 账号首次创建次数
- 登出次数
- 当前活跃 session 数，可后续接入

安全异常应避免泄露具体内部原因。被禁用用户登录时可返回通用认证失败或受限提示，并在服务端记录脱敏原因。

## 测试策略

后端测试重点：

- `SecurityFilterChain` 路径放行与保护规则。
- 未登录访问受保护 `/api/**` 返回 `401` 或统一未认证响应。
- `GET /api/auth/me` 在登录和未登录状态下的响应。
- Google OAuth2 首次登录创建用户、角色和绑定。
- 已绑定 Google 账号再次登录更新 provider 展示信息和 `last_login_at`。
- `DISABLED` 用户不能登录或不能访问受保护 API。
- CSRF 对 mutating API 生效。
- Spring Session JDBC 相关配置和 Flyway 迁移资源存在。

前端测试重点：

- 未登录时展示登录入口。
- 已登录时展示当前用户信息。
- 登出后清理用户状态。
- 非 GET 请求带 CSRF header。

## 实施阶段

第一阶段：

- 新建 `backend/auth` Maven 模块。
- 加入 Spring Security OAuth2 Login、Spring Session JDBC、MyBatis repository 和 Flyway 迁移。
- 暴露 `/api/auth/me` 和 `/api/auth/logout`。
- 配置 `/api/**` 默认认证，放行健康检查和 OAuth2 必要路径。
- 前端接入登录状态、Google 登录跳转、登出和 CSRF header。

第二阶段：

- 将现有业务 API 中的 `userId` 请求字段迁移为认证上下文。
- 为用户学习计划、会话、错题等数据补充用户隔离。
- 根据需要增加 `@AuthenticationPrincipal` 或统一 `CurrentUser` 参数解析。

第三阶段：

- 增加管理员后台或命令行初始化管理员角色。
- 增加用户禁用、角色调整、登录审计等管理能力。
- 如出现移动端或开放 API 需求，再评估短期 access token 或 JWT issuance。

## 非目标

当前设计不包含：

- 邮箱密码注册和登录。
- 找回密码。
- 多 OAuth2 provider。
- 管理员后台页面。
- JWT 登录态。
- 多租户。
- 用户审批流。

## 风险与应对

- Google OAuth2 配置错误会导致登录失败：通过本地 `application-local.yml` 和 `.env.example` 明确必需变量。
- 默认保护 `/api/**` 可能影响现有前端开发：实施时需要同步前端登录入口和测试，必要时临时放行特定只读 API，但最终以默认保护为准。
- CSRF 接入可能使现有 POST/SSE 请求失败：前端 API 封装必须统一加 CSRF header，后端测试覆盖 mutating API。
- Flyway 版本号跨模块冲突：新增迁移前检查所有 `db/migration` 路径的最高版本号。
- Google email 变化：第三方绑定以 provider subject 为准，email 只作展示字段。
