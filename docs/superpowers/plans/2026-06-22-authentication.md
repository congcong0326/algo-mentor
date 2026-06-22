# 认证体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 按 `docs/superpowers/specs/2026-06-22-authentication-design.md` 落地 Google OAuth2 + HttpOnly Session 认证基础能力。

**Architecture:** `backend/auth` 作为独立 Maven 模块，提供认证领域模型、MyBatis repository、OAuth2 用户同步、Spring Security 过滤链、认证 API 和 Flyway 迁移。`mentor-api` 作为最终 Spring Boot 应用依赖 `auth`，前端通过 `/api/auth/me`、`/oauth2/authorization/google` 和 `POST /api/auth/logout` 协作。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring Security OAuth2 Login、Spring Session JDBC、MyBatis、Flyway、PostgreSQL、React + TypeScript + Vite、Vitest。

---

### Task 1: 后端认证领域模型与当前用户 API

**Files:**
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/CurrentUserResponse.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthUserStatus.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthRole.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/OAuthProvider.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedUserPrincipal.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/CurrentUserIdProvider.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/SecurityContextCurrentUserIdProvider.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/controller/CurrentUserController.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/controller/CurrentUserControllerTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/security/SecurityContextCurrentUserIdProviderTest.java`

- [x] **Step 1: Write failing tests**

Add tests expecting `/api/auth/me` to return `id/email/displayName/avatarUrl/roles/status` from the authenticated principal, and expecting the current-user provider to expose the full principal rather than only an ID.

- [x] **Step 2: Run tests to verify RED**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=CurrentUserControllerTest,SecurityContextCurrentUserIdProviderTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL because `CurrentUserResponse` and `AuthenticatedUserPrincipal` only contain `id`.

- [x] **Step 3: Implement minimal model and controller changes**

Add enum constants `USER`/`ADMIN`, `ACTIVE`/`DISABLED`, and `GOOGLE("google")`; update principal and response records to carry the public profile and roles; update provider/controller to use `Optional<AuthenticatedUserPrincipal>`.

- [x] **Step 4: Run tests to verify GREEN**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=CurrentUserControllerTest,SecurityContextCurrentUserIdProviderTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PASS.

### Task 2: Google OAuth2 用户同步服务

**Files:**
- Modify: `backend/auth/pom.xml`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthUser.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/OAuthAccount.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/AuthUserRepository.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/OAuth2LoginUserService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthAuthorities.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/service/OAuth2LoginUserServiceTest.java`

- [x] **Step 1: Write failing tests**

Test first Google login: when `(provider, sub)` binding is absent, create local user with role `USER`, create OAuth binding, update `last_login_at`, and return an authenticated principal. Test repeat login: when binding exists, update provider display fields and `last_login_at` without creating another user. Test disabled user: reject with OAuth2 authentication exception.

- [x] **Step 2: Run tests to verify RED**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=OAuth2LoginUserServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL because the service and repository contract do not exist.

- [x] **Step 3: Implement minimal service and repository interface**

Define a repository interface with methods for lookup by OAuth binding, create user, add role, create/update binding, load roles, and update login time. Implement `OAuth2LoginUserService` as a pure service over that interface, accepting provider attributes from Spring OAuth2/OIDC user data.

- [x] **Step 4: Run tests to verify GREEN**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=OAuth2LoginUserServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PASS.

### Task 3: Spring Security、OAuth2 Login 和 Session JDBC 自动配置

**Files:**
- Modify: `backend/auth/pom.xml`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthConfigurationKeys.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthProperties.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthSecurityPaths.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthSecurityAutoConfiguration.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/ApiAuthenticationEntryPoint.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/OAuth2AuthenticationSuccessHandler.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/OAuth2AuthenticationFailureHandler.java`
- Modify: `backend/auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/config/AuthSecurityAutoConfigurationTest.java`

- [x] **Step 1: Write failing tests**

Use Spring test context and MockMvc to verify `/api/health` is permitted, protected `/api/**` returns JSON `401`, OAuth2 authorization endpoint redirects, authenticated `/api/auth/me` returns current user, and mutating API without CSRF returns forbidden.

- [x] **Step 2: Run tests to verify RED**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=AuthSecurityAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL because no security filter chain exists.

- [x] **Step 3: Implement security auto-configuration**

Add Spring Security/OAuth2/Session dependencies; configure `/api/**` protection, permitted paths, JSON auth entry point, CSRF cookie token repository, OAuth2 login success/failure handlers, logout behavior, and session timeout/cookie properties.

- [x] **Step 4: Run tests to verify GREEN**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=AuthSecurityAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PASS.

### Task 4: MyBatis 持久化与 Flyway 迁移

**Files:**
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/AuthUserMapper.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/MyBatisAuthUserRepository.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/model/AuthUserRow.java`
- Create: `backend/auth/src/main/resources/mapper/auth/AuthUserMapper.xml`
- Create: `backend/auth/src/main/resources/db/migration/auth/V8__auth_schema.sql`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/repository/mybatis/AuthUserMapperXmlTest.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/repository/mybatis/AuthMigrationResourceTest.java`

- [x] **Step 1: Write failing tests**

Test mapper XML parses, migration resource contains auth tables and Spring Session JDBC tables, and Flyway resource discovery includes `V8__auth_schema.sql`.

- [x] **Step 2: Run tests to verify RED**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth,mentor-api -am test -Dtest=AuthUserMapperXmlTest,AuthMigrationResourceTest,FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL because mapper XML and migration do not exist.

- [x] **Step 3: Implement mapper, repository adapter, and migration**

Add auth tables `auth_users`、`auth_user_roles`、`auth_oauth_accounts` and Spring Session JDBC standard tables. Add MyBatis mapper methods used by `OAuth2LoginUserService`.

- [x] **Step 4: Run tests to verify GREEN**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth,mentor-api -am test -Dtest=AuthUserMapperXmlTest,AuthMigrationResourceTest,FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PASS.

### Task 5: 应用配置与前端认证接入

**Files:**
- Modify: `backend/mentor-api/src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

- [x] **Step 1: Write failing frontend tests**

Test app startup calls `/api/auth/me` with same-origin credentials, unauthenticated state shows Google login button, authenticated state shows user display name and logout button, logout posts to `/api/auth/logout`, and mutating fetches include `X-XSRF-TOKEN` from cookie.

- [x] **Step 2: Run frontend tests to verify RED**

Run: `npm --cache ./.npm --prefix frontend test -- --run`

Expected: FAIL because auth client functions and UI state do not exist.

- [x] **Step 3: Implement frontend auth client and UI shell**

Add `CurrentUser` type, `getCurrentUser`, `logout`, CSRF cookie reader and shared authenticated fetch helper. Update app header to show login/logout state without replacing the existing debug and problem views.

- [x] **Step 4: Run frontend tests to verify GREEN**

Run: `npm --cache ./.npm --prefix frontend test -- --run`

Expected: PASS.

### Task 6: 集成验证

**Files:**
- All modified files.

- [x] **Step 1: Run auth module tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test`

Expected: PASS.

- [x] **Step 2: Run full backend tests**

Run: `make backend-test`

Expected: PASS.

- [x] **Step 3: Run frontend tests**

Run: `make frontend-test`

Expected: PASS.

- [x] **Step 4: Inspect git diff**

Run: `git status --short && git diff --stat`

Expected: only auth implementation, application config, frontend auth integration, and the implementation plan are modified.
