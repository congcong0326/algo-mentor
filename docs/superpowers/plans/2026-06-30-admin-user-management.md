# Admin User Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `backend/identity` 模块并实现管理员用户管理闭环：分页搜索、查看详情、禁用/恢复、软删除，以及禁用/删除后的认证会话下线。

**Architecture:** `identity` 拥有用户身份模型、角色、用户/角色 MyBatis mapper、用户管理 API、身份状态事件发布和软删除迁移；`auth` 依赖 `identity`，继续拥有认证、安全、密码凭证、OAuth 账号和 Spring Session 处理。状态变更后由 `identity` 发布身份事实事件，`auth` 订阅事件并删除目标用户会话，事件消费失败不回滚身份状态。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC, Maven 多模块, MyBatis, PostgreSQL/Flyway, Spring Security, Spring Session JDBC, React 19, TypeScript 6, Vite/Vitest, Testing Library.

---

## Reference Specs

- `docs/superpowers/specs/2026-06-30-admin-user-management-design.md`
- `docs/code-index.md`
- `backend/pom.xml`
- `backend/auth/pom.xml`
- `backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java`
- `backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthSecurityAutoConfiguration.java`
- `backend/auth/src/main/java/org/congcong/algomentor/auth/config/AuthSecurityPaths.java`
- `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/AuthUserRepository.java`
- `backend/auth/src/main/resources/db/migration/auth/V8__auth_schema.sql`
- `backend/auth/src/main/resources/mapper/auth/AuthUserMapper.xml`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- `backend/mentor-api/src/main/resources/application.yml`
- `frontend/src/App.tsx`
- `frontend/src/app/AppShell.tsx`
- `frontend/src/app/navigation.ts`
- `frontend/src/services/api.ts`
- `frontend/src/types/api.ts`
- `frontend/src/i18n/locales.ts`
- `frontend/src/styles.css`

## Scope Boundaries

- 做：新增 `backend/identity` Maven 模块。
- 做：`identity` 允许依赖 MyBatis，并拥有 `auth_users`、`auth_user_roles` 身份本体访问能力。
- 做：`identity` 提供 `/api/admin/users` 管理员 API，复用现有 `/api/admin/**` 安全规则。
- 做：`auth` 依赖 `identity`，登录、注册、OAuth 同步、当前用户解析通过 identity 用户模型和角色查询。
- 做：`auth` 保留密码凭证表、OAuth 账号表、认证 controller、安全配置和 Spring Session JDBC。
- 做：禁用和软删除发布身份事件，`auth` 订阅后删除目标用户会话。
- 做：前端新增“用户管理”导航项和用户管理页面，仅 `user:manage` 权限可见可访问。
- 不做：物理删除用户。
- 不做：恢复已软删除用户。
- 不做：完整操作审计历史表。
- 不做：新增前端路由库。
- 不做：`identity-core` 模块。
- 不做：重构全部认证、安全和 Session 机制。

## Execution Setup

建议执行前创建隔离 worktree：

```bash
git worktree add .worktrees/admin-user-management -b feat/admin-user-management
cd .worktrees/admin-user-management
```

基线验证：

```bash
make backend-test
make frontend-test
```

如果基线失败，记录失败项和失败命令，不在本计划中修复无关问题。

## Shared Contracts

新增和迁移时使用这些固定契约，避免路径、错误码、字段名散落：

```java
package org.congcong.algomentor.identity.controller;

public final class AdminUserApiContractConstants {

  public static final String ADMIN_USERS_BASE_PATH = "/api/admin/users";
  public static final String USER_ID_PATH = "/{userId}";
  public static final String STATUS_PATH = "/{userId}/status";

  public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
  public static final String USER_STATUS_CONFLICT = "USER_STATUS_CONFLICT";
  public static final String USER_SELF_OPERATION_FORBIDDEN = "USER_SELF_OPERATION_FORBIDDEN";
  public static final String USER_STATUS_INVALID = "USER_STATUS_INVALID";

  private AdminUserApiContractConstants() {
  }
}
```

身份状态枚举迁移到 `identity` 后包含：

```java
package org.congcong.algomentor.identity.model;

/**
 * 本地身份用户状态；认证只允许 ACTIVE 登录。
 */
public enum AuthUserStatus {
  ACTIVE,
  DISABLED,
  DELETED
}
```

角色枚举迁移到 `identity` 后保持：

```java
package org.congcong.algomentor.identity.model;

/**
 * 本地用户角色，映射到 Spring Security ROLE_* authority。
 */
public enum AuthRole {
  USER,
  ADMIN
}
```

身份事件固定为：

```java
package org.congcong.algomentor.identity.event;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record IdentityUserStatusChangedEvent(
    long userId,
    AuthUserStatus previousStatus,
    AuthUserStatus currentStatus,
    long operatorUserId,
    Instant occurredAt
) {
}
```

## File Structure

### Maven

- Modify: `backend/pom.xml`  
  在 `auth` 之前新增 module `identity`，保证 `auth` 可以依赖它。
- Create: `backend/identity/pom.xml`  
  依赖 `common`、`spring-web`、`spring-context`、`spring-tx`、`spring-boot-autoconfigure`、`mybatis`、`mybatis-spring`、`slf4j-api`、`micrometer-core` 和 test 依赖。
- Modify: `backend/auth/pom.xml`  
  新增 `identity` 依赖；保留 Spring Security、Session、JDBC、MyBatis 依赖用于认证侧凭证/OAuth mapper。
- Modify: `backend/mentor-api/pom.xml`  
  不需要直接依赖 `identity`；通过 `auth -> identity` 传递引入。若执行时发现 Spring Boot 打包未包含传递依赖，才显式增加 `identity` 依赖。

### Identity Module

- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthUser.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthUserStatus.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthRole.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/IdentityUserPage.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/IdentityUserSearchQuery.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/IdentityUserRepository.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/IdentityUserMapper.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/MyBatisIdentityUserRepository.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/model/AuthUserRow.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/model/AdminUserSearchRow.java`
- Create: `backend/identity/src/main/resources/mapper/identity/IdentityUserMapper.xml`
- Create: `backend/identity/src/main/resources/db/migration/identity/V16__identity_user_soft_delete.sql`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/IdentityUserStatusChangedEvent.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/IdentityEventPublisher.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/SpringIdentityEventPublisher.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/NoopIdentityEventPublisher.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/service/IdentityUserService.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/service/IdentityUserManagementException.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/service/IdentityUserErrorCode.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/AdminUserApiContractConstants.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/AdminUserController.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/AdminUserExceptionHandler.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserDetailResponse.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserListQuery.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserPageResponse.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserResponseMapper.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserStatusUpdateRequest.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserSummaryResponse.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/autoconfigure/IdentityAutoConfiguration.java`
- Create: `backend/identity/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Auth Migration and Session Revocation

- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/CurrentUserResponse.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedUserPrincipal.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedUserDetails.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthAuthorities.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/AuthPermissionService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/AdminEmailRoleService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/PasswordUserDetailsService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/PasswordUserService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/OAuth2LoginUserService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/AuthUserRepository.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/AuthUserMapper.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/MyBatisAuthUserRepository.java`
- Modify: `backend/auth/src/main/resources/mapper/auth/AuthUserMapper.xml`
- Delete after green tests: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthUser.java`
- Delete after green tests: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthUserStatus.java`
- Delete after green tests: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthRole.java`
- Delete after green tests: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/model/AuthUserRow.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/AuthSessionRevocationService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/SpringSessionAuthSessionRevocationService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/IdentityUserStatusChangedEventListener.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/AuthSessionMetrics.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/NoopAuthSessionMetrics.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/MicrometerAuthSessionMetrics.java`

### Frontend

- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`
- Create: `frontend/src/admin/UserManagementPage.tsx`
- Create: `frontend/src/admin/UserManagementPage.test.tsx`

### Tests

- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/model/AuthUserStatusTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/IdentityMigrationResourceTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/IdentityUserMapperXmlTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/MyBatisIdentityUserRepositoryTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/service/IdentityUserServiceTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/controller/AdminUserControllerTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/autoconfigure/IdentityAutoConfigurationTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/service/OAuth2LoginUserServiceTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/service/PasswordUserServiceTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/security/AuthenticatedOAuth2UserServiceTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/controller/PasswordAuthControllerTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/repository/mybatis/AuthMigrationResourceTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/repository/mybatis/AuthUserMapperXmlTest.java`
- Modify: `backend/auth/src/test/java/org/congcong/algomentor/auth/repository/mybatis/MyBatisAuthUserRepositoryTest.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/session/IdentityUserStatusChangedEventListenerTest.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/session/SpringSessionAuthSessionRevocationServiceTest.java`
- Modify: `frontend/src/app/navigation.test.ts`
- Modify: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/services/api.test.ts`

---

### Task 1: Identity Module Skeleton and Shared Identity Model

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/identity/pom.xml`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthUser.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthUserStatus.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthRole.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/model/AuthUserStatusTest.java`

- [ ] **Step 1: Write the failing model test**

Create `backend/identity/src/test/java/org/congcong/algomentor/identity/model/AuthUserStatusTest.java`:

```java
package org.congcong.algomentor.identity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthUserStatusTest {

  @Test
  void statusIncludesDeletedAndDefaultsToActive() {
    AuthUser user = new AuthUser(
        1L,
        "user@example.com",
        "user@example.com",
        "User Name",
        null,
        null,
        Instant.parse("2026-06-30T00:00:00Z"),
        Instant.parse("2026-06-30T00:00:00Z"),
        null,
        null,
        null);

    assertThat(AuthUserStatus.valueOf("DELETED")).isEqualTo(AuthUserStatus.DELETED);
    assertThat(user.status()).isEqualTo(AuthUserStatus.ACTIVE);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=AuthUserStatusTest
```

Expected: FAIL because Maven module `identity` does not exist.

- [ ] **Step 3: Add the Maven module**

Modify `backend/pom.xml` and insert `identity` before `auth`:

```xml
    <module>agent-persistence-postgres</module>
    <module>identity</module>
    <module>auth</module>
```

Create `backend/identity/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.congcong.algomentor</groupId>
    <artifactId>algo-mentor-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>

  <artifactId>identity</artifactId>
  <name>algo-mentor-identity</name>

  <dependencies>
    <dependency>
      <groupId>org.congcong.algomentor</groupId>
      <artifactId>common</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-tx</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>org.mybatis</groupId>
      <artifactId>mybatis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.mybatis</groupId>
      <artifactId>mybatis-spring</artifactId>
    </dependency>
    <dependency>
      <groupId>io.micrometer</groupId>
      <artifactId>micrometer-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-webmvc</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: Add the identity model**

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthUserStatus.java`:

```java
package org.congcong.algomentor.identity.model;

/**
 * 本地身份用户状态；认证只允许 ACTIVE 登录。
 */
public enum AuthUserStatus {
  ACTIVE,
  DISABLED,
  DELETED
}
```

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthRole.java`:

```java
package org.congcong.algomentor.identity.model;

/**
 * 本地用户角色，映射到 Spring Security ROLE_* authority。
 */
public enum AuthRole {
  USER,
  ADMIN
}
```

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/model/AuthUser.java`:

```java
package org.congcong.algomentor.identity.model;

import java.time.Instant;

public record AuthUser(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    Instant deletedAt,
    Long deletedBy
) {

  public AuthUser {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("id must be positive when present.");
    }
    if (deletedBy != null && deletedBy < 1) {
      throw new IllegalArgumentException("deletedBy must be positive when present.");
    }
    status = status == null ? AuthUserStatus.ACTIVE : status;
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=AuthUserStatusTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/identity/pom.xml backend/identity/src/main/java/org/congcong/algomentor/identity/model backend/identity/src/test/java/org/congcong/algomentor/identity/model/AuthUserStatusTest.java
git commit -m "feat: add identity module model"
```

### Task 2: Identity Repository, Mapper, and Soft Delete Migration

**Files:**
- Create: `backend/identity/src/main/resources/db/migration/identity/V16__identity_user_soft_delete.sql`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/IdentityMigrationResourceTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/IdentityUserMapperXmlTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/MyBatisIdentityUserRepositoryTest.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/IdentityUserPage.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/model/IdentityUserSearchQuery.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/IdentityUserRepository.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/IdentityUserMapper.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/MyBatisIdentityUserRepository.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/model/AuthUserRow.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/model/AdminUserSearchRow.java`
- Create: `backend/identity/src/main/resources/mapper/identity/IdentityUserMapper.xml`

- [ ] **Step 1: Write failing resource tests**

Create `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/IdentityMigrationResourceTest.java`:

```java
package org.congcong.algomentor.identity.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IdentityMigrationResourceTest {

  @Test
  void identityMigrationAddsSoftDeleteColumnsAndIndexes() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/identity/V16__identity_user_soft_delete.sql");

    assertThat(resource.exists()).isTrue();

    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("alter table auth_users")
        .contains("deleted_at timestamptz")
        .contains("deleted_by bigint")
        .contains("idx_auth_users_status")
        .contains("idx_auth_users_deleted_at")
        .contains("idx_auth_users_email_display_name");
  }
}
```

Create `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/IdentityUserMapperXmlTest.java`:

```java
package org.congcong.algomentor.identity.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IdentityUserMapperXmlTest {

  @Test
  void mapperDefinesAdminSearchStatusUpdatesAndSoftDelete() throws Exception {
    ClassPathResource resource = new ClassPathResource("mapper/identity/IdentityUserMapper.xml");

    assertThat(resource.exists()).isTrue();

    String xml = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(xml)
        .contains("id=\"searchUsers\"")
        .contains("id=\"countUsers\"")
        .contains("id=\"findUserById\"")
        .contains("id=\"findUserByEmailNormalized\"")
        .contains("id=\"insertUser\"")
        .contains("id=\"insertUserRole\"")
        .contains("id=\"findRoles\"")
        .contains("id=\"updateUserStatus\"")
        .contains("id=\"softDeleteUser\"")
        .contains("deleted_at")
        .contains("deleted_by");
  }
}
```

- [ ] **Step 2: Run resource tests to verify they fail**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=IdentityMigrationResourceTest,IdentityUserMapperXmlTest
```

Expected: FAIL because migration and mapper XML are missing.

- [ ] **Step 3: Add the Flyway migration**

Create `backend/identity/src/main/resources/db/migration/identity/V16__identity_user_soft_delete.sql`:

```sql
alter table auth_users
  add column if not exists deleted_at timestamptz,
  add column if not exists deleted_by bigint;

alter table auth_users
  add constraint fk_auth_users_deleted_by
    foreign key (deleted_by) references auth_users (id);

create index if not exists idx_auth_users_status
  on auth_users (status);

create index if not exists idx_auth_users_deleted_at
  on auth_users (deleted_at);

create index if not exists idx_auth_users_email_display_name
  on auth_users (email_normalized, display_name);
```

- [ ] **Step 4: Add repository contracts and row models**

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/model/IdentityUserSearchQuery.java`:

```java
package org.congcong.algomentor.identity.model;

import java.util.List;

public record IdentityUserSearchQuery(
    int page,
    int pageSize,
    String keyword,
    AuthUserStatus status
) {

  public IdentityUserSearchQuery {
    page = Math.max(page, 1);
    pageSize = Math.min(Math.max(pageSize, 1), 100);
    keyword = keyword == null ? "" : keyword.trim();
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  public List<AuthUserStatus> effectiveStatuses() {
    return status == null
        ? List.of(AuthUserStatus.ACTIVE, AuthUserStatus.DISABLED)
        : List.of(status);
  }
}
```

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/model/IdentityUserPage.java`:

```java
package org.congcong.algomentor.identity.model;

import java.util.List;

public record IdentityUserPage(
    List<AuthUser> items,
    long total,
    int page,
    int pageSize
) {

  public IdentityUserPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
```

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/IdentityUserRepository.java`:

```java
package org.congcong.algomentor.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;

public interface IdentityUserRepository {

  Optional<AuthUser> findUserById(long userId);

  Optional<AuthUser> findUserByEmailNormalized(String emailNormalized);

  AuthUser createUser(
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      AuthUserStatus status,
      Instant now);

  void addRole(long userId, AuthRole role);

  List<AuthRole> findRoles(long userId);

  AuthUser updateLastLoginAt(long userId, Instant lastLoginAt);

  IdentityUserPage searchUsers(IdentityUserSearchQuery query);

  boolean updateUserStatus(long userId, AuthUserStatus expectedStatus, AuthUserStatus status, Instant updatedAt);

  boolean softDeleteUser(long userId, long operatorUserId, AuthUserStatus expectedStatus, Instant deletedAt);
}
```

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/model/AuthUserRow.java`:

```java
package org.congcong.algomentor.identity.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public final class AuthUserRow {

  private Long id;
  private final String email;
  private final String emailNormalized;
  private final String displayName;
  private final String avatarUrl;
  private final String status;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant lastLoginAt;
  private final Instant deletedAt;
  private final Long deletedBy;

  public AuthUserRow(
      Long id,
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      String status,
      Instant createdAt,
      Instant updatedAt,
      Instant lastLoginAt,
      Instant deletedAt,
      Long deletedBy
  ) {
    this.id = id;
    this.email = email;
    this.emailNormalized = emailNormalized;
    this.displayName = displayName;
    this.avatarUrl = avatarUrl;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.lastLoginAt = lastLoginAt;
    this.deletedAt = deletedAt;
    this.deletedBy = deletedBy;
  }

  public Long id() { return id; }
  public void setId(Long id) { this.id = id; }
  public String email() { return email; }
  public String emailNormalized() { return emailNormalized; }
  public String displayName() { return displayName; }
  public String avatarUrl() { return avatarUrl; }
  public String status() { return status; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
  public Instant lastLoginAt() { return lastLoginAt; }
  public Instant deletedAt() { return deletedAt; }
  public Long deletedBy() { return deletedBy; }

  public AuthUser toDomain() {
    return new AuthUser(
        id,
        email,
        emailNormalized,
        displayName,
        avatarUrl,
        AuthUserStatus.valueOf(status),
        createdAt,
        updatedAt,
        lastLoginAt,
        deletedAt,
        deletedBy);
  }
}
```

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/model/AdminUserSearchRow.java`:

```java
package org.congcong.algomentor.identity.repository.mybatis.model;

import java.time.Instant;

public record AdminUserSearchRow(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    Instant deletedAt,
    Long deletedBy
) {

  public AuthUserRow toUserRow() {
    return new AuthUserRow(
        id,
        email,
        emailNormalized,
        displayName,
        avatarUrl,
        status,
        createdAt,
        updatedAt,
        lastLoginAt,
        deletedAt,
        deletedBy);
  }
}
```

- [ ] **Step 5: Add mapper interface and XML**

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/IdentityUserMapper.java`:

```java
package org.congcong.algomentor.identity.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow;

public interface IdentityUserMapper {

  AuthUserRow findUserById(@Param("userId") long userId);

  AuthUserRow findUserByEmailNormalized(@Param("emailNormalized") String emailNormalized);

  int insertUser(AuthUserRow user);

  int insertUserRole(@Param("userId") long userId, @Param("role") String role, @Param("createdAt") Instant createdAt);

  List<String> findRoles(@Param("userId") long userId);

  int updateLastLoginAt(@Param("userId") long userId, @Param("lastLoginAt") Instant lastLoginAt);

  List<AuthUserRow> searchUsers(
      @Param("keyword") String keyword,
      @Param("statuses") List<String> statuses,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countUsers(@Param("keyword") String keyword, @Param("statuses") List<String> statuses);

  int updateUserStatus(
      @Param("userId") long userId,
      @Param("expectedStatus") String expectedStatus,
      @Param("status") String status,
      @Param("updatedAt") Instant updatedAt);

  int softDeleteUser(
      @Param("userId") long userId,
      @Param("operatorUserId") long operatorUserId,
      @Param("expectedStatus") String expectedStatus,
      @Param("deletedAt") Instant deletedAt);
}
```

Create `backend/identity/src/main/resources/mapper/identity/IdentityUserMapper.xml` with this result map:

```xml
<resultMap id="AuthUserRowMap" type="org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow">
  <constructor>
    <idArg column="id" javaType="java.lang.Long"/>
    <arg column="email" javaType="java.lang.String"/>
    <arg column="email_normalized" javaType="java.lang.String"/>
    <arg column="display_name" javaType="java.lang.String"/>
    <arg column="avatar_url" javaType="java.lang.String"/>
    <arg column="status" javaType="java.lang.String"/>
    <arg column="created_at" javaType="java.time.Instant"/>
    <arg column="updated_at" javaType="java.time.Instant"/>
    <arg column="last_login_at" javaType="java.time.Instant"/>
    <arg column="deleted_at" javaType="java.time.Instant"/>
    <arg column="deleted_by" javaType="java.lang.Long"/>
  </constructor>
</resultMap>
```

The XML must include `findUserById`, `findUserByEmailNormalized`, `insertUser`, `insertUserRole`, `findRoles`, `updateLastLoginAt`, `countUsers`, `searchUsers`, `updateUserStatus`, and `softDeleteUser`. The `searchUsers` SQL must be:

```xml
<select id="searchUsers" resultMap="AuthUserRowMap">
  select id, email, email_normalized, display_name, avatar_url, status,
         created_at, updated_at, last_login_at, deleted_at, deleted_by
  from auth_users
  where status in
  <foreach collection="statuses" item="status" open="(" separator="," close=")">
    #{status}
  </foreach>
  <if test="keyword != null and keyword != ''">
    and (
      lower(coalesce(email, '')) like concat('%', lower(#{keyword}), '%')
      or lower(coalesce(display_name, '')) like concat('%', lower(#{keyword}), '%')
    )
  </if>
  order by created_at desc, id desc
  limit #{limit}
  offset #{offset}
</select>
```

and:

```xml
<update id="updateUserStatus">
  update auth_users
  set status = #{status},
      updated_at = #{updatedAt}
  where id = #{userId}
    and status = #{expectedStatus}
    and status != 'DELETED'
</update>

<update id="softDeleteUser">
  update auth_users
  set status = 'DELETED',
      deleted_at = #{deletedAt},
      deleted_by = #{operatorUserId},
      updated_at = #{deletedAt}
  where id = #{userId}
    and status = #{expectedStatus}
    and status != 'DELETED'
</update>
```

- [ ] **Step 6: Write repository behavior test**

Create `backend/identity/src/test/java/org/congcong/algomentor/identity/repository/mybatis/MyBatisIdentityUserRepositoryTest.java` with a fake `IdentityUserMapper` that verifies:

```java
@Test
void searchUsersDefaultsToActiveAndDisabledStatuses() {
  IdentityUserPage page = repository.searchUsers(new IdentityUserSearchQuery(1, 20, "user", null));

  assertThat(mapper.lastStatuses).containsExactly("ACTIVE", "DISABLED");
  assertThat(mapper.lastKeyword).isEqualTo("user");
  assertThat(page.items()).hasSize(1);
  assertThat(page.total()).isEqualTo(1);
}

@Test
void softDeleteReturnsFalseWhenMapperDoesNotUpdateAnyRow() {
  mapper.updatedRows = 0;

  assertThat(repository.softDeleteUser(
      42L,
      1L,
      AuthUserStatus.ACTIVE,
      Instant.parse("2026-06-30T00:00:00Z"))).isFalse();
}
```

- [ ] **Step 7: Implement repository**

Create `backend/identity/src/main/java/org/congcong/algomentor/identity/repository/mybatis/MyBatisIdentityUserRepository.java` so it maps rows to `AuthUser`, defaults blank email lookups to `Optional.empty()`, converts roles with `AuthRole.valueOf`, and returns `mapper.update...(...) == 1` for status mutations.

- [ ] **Step 8: Run identity repository tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=IdentityMigrationResourceTest,IdentityUserMapperXmlTest,MyBatisIdentityUserRepositoryTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/identity/src/main/java/org/congcong/algomentor/identity backend/identity/src/main/resources backend/identity/src/test/java/org/congcong/algomentor/identity
git commit -m "feat: add identity user repository"
```

### Task 3: Identity Service, Status Rules, and Events

**Files:**
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/IdentityUserStatusChangedEvent.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/IdentityEventPublisher.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/NoopIdentityEventPublisher.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/event/SpringIdentityEventPublisher.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/service/IdentityUserErrorCode.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/service/IdentityUserManagementException.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/service/IdentityUserService.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/service/IdentityUserServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/identity/src/test/java/org/congcong/algomentor/identity/service/IdentityUserServiceTest.java` with tests named:

```java
@Test
void disableChangesActiveUserToDisabledAndPublishesEvent()

@Test
void restoreChangesDisabledUserToActiveAndPublishesEvent()

@Test
void softDeleteActiveUserMarksDeletedAndPublishesEvent()

@Test
void defaultSearchExcludesDeletedUsers()

@Test
void selfOperationIsRejected()

@Test
void deletedUserCannotBeDisabledRestoredOrDeletedAgain()

@Test
void missingUserReturnsNotFound()

@Test
void invalidRequestedStatusIsRejected()
```

The first assertion block for disable must be:

```java
AuthUser updated = service.updateStatus(42L, 7L, AuthUserStatus.DISABLED);

assertThat(updated.status()).isEqualTo(AuthUserStatus.DISABLED);
assertThat(publisher.events).containsExactly(new IdentityUserStatusChangedEvent(
    42L,
    AuthUserStatus.ACTIVE,
    AuthUserStatus.DISABLED,
    7L,
    NOW));
```

The self-operation assertion must be:

```java
assertThatThrownBy(() -> service.updateStatus(42L, 42L, AuthUserStatus.DISABLED))
    .isInstanceOf(IdentityUserManagementException.class)
    .extracting("code")
    .isEqualTo(IdentityUserErrorCode.USER_SELF_OPERATION_FORBIDDEN);
```

- [ ] **Step 2: Run service tests to verify they fail**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=IdentityUserServiceTest
```

Expected: FAIL because service/event classes are missing.

- [ ] **Step 3: Add events and exceptions**

Create `IdentityEventPublisher`:

```java
package org.congcong.algomentor.identity.event;

public interface IdentityEventPublisher {
  void publish(IdentityUserStatusChangedEvent event);
}
```

Create `NoopIdentityEventPublisher`:

```java
package org.congcong.algomentor.identity.event;

public class NoopIdentityEventPublisher implements IdentityEventPublisher {
  @Override
  public void publish(IdentityUserStatusChangedEvent event) {
  }
}
```

Create `SpringIdentityEventPublisher`:

```java
package org.congcong.algomentor.identity.event;

import org.springframework.context.ApplicationEventPublisher;

public class SpringIdentityEventPublisher implements IdentityEventPublisher {

  private final ApplicationEventPublisher publisher;

  public SpringIdentityEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(IdentityUserStatusChangedEvent event) {
    publisher.publishEvent(event);
  }
}
```

Create `IdentityUserErrorCode`:

```java
package org.congcong.algomentor.identity.service;

public enum IdentityUserErrorCode {
  USER_NOT_FOUND,
  USER_STATUS_CONFLICT,
  USER_SELF_OPERATION_FORBIDDEN,
  USER_STATUS_INVALID
}
```

Create `IdentityUserManagementException` with a `code()` accessor:

```java
package org.congcong.algomentor.identity.service;

public class IdentityUserManagementException extends RuntimeException {

  private final IdentityUserErrorCode code;

  public IdentityUserManagementException(IdentityUserErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public IdentityUserErrorCode code() {
    return code;
  }
}
```

- [ ] **Step 4: Implement service**

Create `IdentityUserService` with constructor:

```java
public IdentityUserService(
    IdentityUserRepository repository,
    IdentityEventPublisher eventPublisher,
    Clock clock
)
```

Implement:

- `searchUsers(IdentityUserSearchQuery query)`
- `getUser(long userId)`
- `updateStatus(long userId, long operatorUserId, AuthUserStatus requestedStatus)`
- `softDelete(long userId, long operatorUserId)`

Rules:

- self-operation rejects with `USER_SELF_OPERATION_FORBIDDEN`.
- missing user rejects with `USER_NOT_FOUND`.
- `DELETED` target rejects with `USER_STATUS_CONFLICT`.
- requested status must be only `ACTIVE` or `DISABLED`.
- `ACTIVE -> DISABLED`, `DISABLED -> ACTIVE` are allowed.
- no-op status changes reject with `USER_STATUS_CONFLICT`.
- soft delete accepts `ACTIVE` or `DISABLED`, sets `DELETED`, `deletedAt`, `deletedBy`.
- event is published only after repository mutation succeeds.
- failed mutation after reload rejects with `USER_STATUS_CONFLICT`.

- [ ] **Step 5: Run service tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=IdentityUserServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/identity/src/main/java/org/congcong/algomentor/identity/event backend/identity/src/main/java/org/congcong/algomentor/identity/service backend/identity/src/test/java/org/congcong/algomentor/identity/service/IdentityUserServiceTest.java
git commit -m "feat: add identity user management service"
```

### Task 4: Identity Auto-Configuration and Admin Users API

**Files:**
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/AdminUserApiContractConstants.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/AdminUserController.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/AdminUserExceptionHandler.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserDetailResponse.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserListQuery.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserPageResponse.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserResponseMapper.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserStatusUpdateRequest.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/controller/model/AdminUserSummaryResponse.java`
- Create: `backend/identity/src/main/java/org/congcong/algomentor/identity/autoconfigure/IdentityAutoConfiguration.java`
- Create: `backend/identity/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/controller/AdminUserControllerTest.java`
- Create: `backend/identity/src/test/java/org/congcong/algomentor/identity/autoconfigure/IdentityAutoConfigurationTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `AdminUserControllerTest` using standalone MockMvc. Cover:

```java
@Test
void listUsersReturnsPagedResponse()

@Test
void detailReturnsRolesAndDeleteFields()

@Test
void updateStatusDisablesUser()

@Test
void deleteSoftDeletesUser()

@Test
void serviceConflictMapsTo409()

@Test
void invalidStatusMapsTo400()
```

Required list response assertions:

```java
mockMvc.perform(get("/api/admin/users")
        .param("page", "2")
        .param("pageSize", "10")
        .param("keyword", "alice")
        .param("status", "ACTIVE"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.data.items[0].id").value(42))
    .andExpect(jsonPath("$.data.items[0].roles[0]").value("USER"))
    .andExpect(jsonPath("$.data.total").value(1))
    .andExpect(jsonPath("$.data.page").value(2))
    .andExpect(jsonPath("$.data.pageSize").value(10));
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=AdminUserControllerTest
```

Expected: FAIL because controller classes are missing.

- [ ] **Step 3: Add response DTOs and mapper**

Create DTOs with these fields:

```java
public record AdminUserSummaryResponse(
    Long id,
    String email,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    List<AuthRole> roles,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt
) {}
```

```java
public record AdminUserDetailResponse(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    List<AuthRole> roles,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    Instant deletedAt,
    Long deletedBy
) {}
```

```java
public record AdminUserPageResponse(
    List<AdminUserSummaryResponse> items,
    long total,
    int page,
    int pageSize
) {}
```

```java
public record AdminUserStatusUpdateRequest(AuthUserStatus status) {}
```

`AdminUserResponseMapper` must call `IdentityUserRepository.findRoles(user.id())` for each returned user.

- [ ] **Step 4: Add controller and exception handler**

`AdminUserController` endpoints:

- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- `PATCH /api/admin/users/{userId}/status`
- `DELETE /api/admin/users/{userId}`

Use Spring Security `Authentication.getName()` as the operator source. This avoids an `identity -> auth` dependency cycle because `CurrentUserIdProvider` and `AuthenticatedUserPrincipal` live in `auth`.

```java
private long requireOperatorId(Authentication authentication) {
  if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
    throw new IdentityUserManagementException(
        IdentityUserErrorCode.USER_NOT_FOUND,
        "当前请求未登录或无法解析当前用户。");
  }
  try {
    return Long.parseLong(authentication.getName());
  } catch (NumberFormatException exception) {
    throw new IdentityUserManagementException(
        IdentityUserErrorCode.USER_NOT_FOUND,
        "当前请求未登录或无法解析当前用户。");
  }
}
```

`AdminUserExceptionHandler` maps:

- `USER_NOT_FOUND` -> 404
- `USER_STATUS_CONFLICT` -> 409
- `USER_SELF_OPERATION_FORBIDDEN` -> 409
- `USER_STATUS_INVALID` -> 400

Use `ApiErrorResponseFactory` when present, otherwise use `ApiResponse.failure(code, message)`.

- [ ] **Step 5: Add identity auto-configuration**

Create `IdentityAutoConfiguration`:

- `@AutoConfiguration`
- conditionally register `IdentityUserMapper` from `SqlSessionTemplate`
- conditionally register `IdentityUserRepository`
- conditionally register `Clock`
- conditionally register `IdentityEventPublisher`
- conditionally register `IdentityUserService`
- conditionally register `AdminUserController`

Create imports file:

```text
org.congcong.algomentor.identity.autoconfigure.IdentityAutoConfiguration
```

- [ ] **Step 6: Write and run auto-configuration test**

`IdentityAutoConfigurationTest` must use `ApplicationContextRunner` and assert that with a mocked `SqlSessionTemplate`, `IdentityUserMapper`, `IdentityUserRepository`, `IdentityUserService`, and `AdminUserController` beans can be created.

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test -Dtest=AdminUserControllerTest,IdentityAutoConfigurationTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/identity/src/main/java/org/congcong/algomentor/identity/controller backend/identity/src/main/java/org/congcong/algomentor/identity/autoconfigure backend/identity/src/main/resources/META-INF backend/identity/src/test/java/org/congcong/algomentor/identity/controller backend/identity/src/test/java/org/congcong/algomentor/identity/autoconfigure
git commit -m "feat: add admin user management api"
```

### Task 5: Move User Identity Usage from Auth to Identity

**Files:**
- Modify: `backend/auth/pom.xml`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/CurrentUserResponse.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedUserPrincipal.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthenticatedUserDetails.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/AuthAuthorities.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/AuthPermissionService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/AdminEmailRoleService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/PasswordUserDetailsService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/PasswordUserService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/OAuth2LoginUserService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java`
- Modify: auth tests listed in File Structure.

- [ ] **Step 1: Write failing auth behavior tests**

Update `PasswordAuthControllerTest` to add:

```java
@Test
void disabledUserReceivesUnifiedLoginFailure()

@Test
void deletedUserReceivesUnifiedLoginFailure()
```

Both must register or seed a user, set status to `DISABLED` or `DELETED`, then assert:

```java
.andExpect(status().isUnauthorized())
.andExpect(jsonPath("$.success").value(false))
.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
```

Update `OAuth2LoginUserServiceTest` to add:

```java
@Test
void deletedUserCannotLogin()
```

Assert an `OAuth2AuthenticationException` and code `auth_user_disabled`.

- [ ] **Step 2: Run auth tests to verify they fail**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=PasswordAuthControllerTest,OAuth2LoginUserServiceTest
```

Expected: FAIL because `auth` still imports its local identity model and lacks `DELETED`.

- [ ] **Step 3: Add identity dependency to auth**

Modify `backend/auth/pom.xml`:

```xml
    <dependency>
      <groupId>org.congcong.algomentor</groupId>
      <artifactId>identity</artifactId>
      <version>${project.version}</version>
    </dependency>
```

- [ ] **Step 4: Change auth imports to identity model**

For these files, replace imports from `org.congcong.algomentor.auth.model.AuthRole`, `AuthUser`, `AuthUserStatus` with `org.congcong.algomentor.identity.model.*`:

- `CurrentUserResponse.java`
- `AuthenticatedUserPrincipal.java`
- `AuthenticatedUserDetails.java`
- `AuthAuthorities.java`
- `AuthPermissionService.java`
- `AdminEmailRoleService.java`
- `PasswordUserDetailsService.java`
- `PasswordUserService.java`
- `OAuth2LoginUserService.java`
- auth tests using roles/status/users

Update active checks:

```java
if (user.status() != AuthUserStatus.ACTIVE) {
  throw new DisabledException("Bad credentials.");
}
```

and OAuth:

```java
if (user.status() != AuthUserStatus.ACTIVE) {
  throw authenticationException(AUTH_USER_DISABLED_CODE, "User is disabled.");
}
```

- [ ] **Step 5: Split auth repository responsibilities**

Change `AuthUserRepository` so it keeps only auth-owned data:

```java
public interface AuthUserRepository {
  Optional<OAuthAccount> findOAuthAccount(OAuthProvider provider, String providerSubject);
  PasswordCredential createPasswordCredential(long userId, String passwordHash, Instant now);
  Optional<PasswordCredential> findPasswordCredentialByEmailNormalized(String emailNormalized);
  OAuthAccount createOAuthAccount(OAuthAccount account);
  void updateOAuthAccountProfile(long accountId, String emailAtProvider, String displayNameAtProvider, String avatarUrlAtProvider, Instant updatedAt);
}
```

Change services to inject both:

- `AuthUserRepository` for credentials/OAuth accounts.
- `IdentityUserRepository` for users, roles, last-login updates.

For example `PasswordUserService` constructor becomes:

```java
public PasswordUserService(
    AuthUserRepository authRepository,
    IdentityUserRepository identityRepository,
    PasswordEncoder passwordEncoder,
    Clock clock,
    AdminEmailRoleService adminEmailRoleService
)
```

`AdminEmailRoleService` constructor becomes:

```java
public AdminEmailRoleService(IdentityUserRepository identityRepository, Collection<String> adminEmails)
```

- [ ] **Step 6: Narrow auth MyBatis mapper**

Remove user and role SQL from auth mapper after identity mapper covers it:

- Remove `findUserById`
- Remove `findUserByEmailNormalized`
- Remove `insertUser`
- Remove `insertUserRole`
- Remove `findRoles`
- Remove `updateLastLoginAt`
- Remove `AuthUserRowMap`

Keep:

- OAuth account SQL.
- Password credential SQL.

`findPasswordCredentialByEmailNormalized` may continue joining `auth_users`, because credentials are auth-owned but lookup is by email.

- [ ] **Step 7: Update `AuthApiAutoConfiguration` wiring**

Change beans:

- `AdminEmailRoleService` depends on `IdentityUserRepository`.
- `PasswordUserDetailsService` depends on `AuthUserRepository` and `IdentityUserRepository`.
- `PasswordUserService` depends on `AuthUserRepository` and `IdentityUserRepository`.
- `OAuth2LoginUserService` depends on `AuthUserRepository` and `IdentityUserRepository`.
- Remove `AuthUserMapper` user/role ownership assumptions.

- [ ] **Step 8: Delete migrated auth identity files**

Delete only after compile errors are resolved:

```bash
rm backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthUser.java
rm backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthUserStatus.java
rm backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthRole.java
rm backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/model/AuthUserRow.java
```

- [ ] **Step 9: Run auth tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=PasswordAuthControllerTest,PasswordUserServiceTest,OAuth2LoginUserServiceTest,AuthenticatedOAuth2UserServiceTest,AuthMigrationResourceTest,AuthUserMapperXmlTest,MyBatisAuthUserRepositoryTest
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/auth backend/identity backend/pom.xml
git commit -m "refactor: move user identity model to identity module"
```

### Task 6: Auth Session Revocation on Identity Events

**Files:**
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/AuthSessionRevocationService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/SpringSessionAuthSessionRevocationService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/IdentityUserStatusChangedEventListener.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/AuthSessionMetrics.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/NoopAuthSessionMetrics.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/session/MicrometerAuthSessionMetrics.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/session/IdentityUserStatusChangedEventListenerTest.java`
- Create: `backend/auth/src/test/java/org/congcong/algomentor/auth/session/SpringSessionAuthSessionRevocationServiceTest.java`

- [ ] **Step 1: Write failing listener tests**

Create `IdentityUserStatusChangedEventListenerTest`:

```java
@Test
void disabledEventRevokesTargetUserSessions()

@Test
void deletedEventRevokesTargetUserSessions()

@Test
void activeEventDoesNotRevokeSessions()

@Test
void revocationFailureIsRecordedAndDoesNotRethrow()
```

The disabled assertion must be:

```java
listener.onStatusChanged(new IdentityUserStatusChangedEvent(
    42L,
    AuthUserStatus.ACTIVE,
    AuthUserStatus.DISABLED,
    7L,
    NOW));

assertThat(revocationService.revokedUserIds).containsExactly(42L);
assertThat(metrics.successCount).isEqualTo(1);
```

- [ ] **Step 2: Write failing revocation service test**

Create `SpringSessionAuthSessionRevocationServiceTest` using a fake `FindByIndexNameSessionRepository` and fake `Session` objects. Assert:

```java
service.revokeSessionsForUser(42L);

assertThat(repository.deletedSessionIds).containsExactlyInAnyOrder("session-1", "session-2");
```

The fake repository must return sessions when queried with principal name `"42"`, because `AuthenticatedUserPrincipal.getName()` returns user id as string.

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=IdentityUserStatusChangedEventListenerTest,SpringSessionAuthSessionRevocationServiceTest
```

Expected: FAIL because session classes are missing.

- [ ] **Step 4: Implement session revocation contracts**

Create:

```java
package org.congcong.algomentor.auth.session;

public interface AuthSessionRevocationService {
  int revokeSessionsForUser(long userId);
}
```

Create `SpringSessionAuthSessionRevocationService` backed by:

```java
FindByIndexNameSessionRepository<? extends Session> sessionRepository
```

Query:

```java
sessionRepository.findByPrincipalName(Long.toString(userId))
```

Delete every returned session id and return the count.

- [ ] **Step 5: Implement event listener and metrics**

Listener logic:

```java
if (event.currentStatus() != AuthUserStatus.DISABLED && event.currentStatus() != AuthUserStatus.DELETED) {
  return;
}
try {
  int revoked = revocationService.revokeSessionsForUser(event.userId());
  metrics.recordSuccess(event.currentStatus(), revoked);
} catch (RuntimeException exception) {
  metrics.recordFailure(event.currentStatus());
  log.error("Failed to revoke sessions for identity status change. userId={} currentStatus={}",
      event.userId(),
      event.currentStatus(),
      exception);
}
```

Metric names:

- `algo_mentor_auth_session_revocations_total`
- `algo_mentor_auth_session_revocation_failures_total`

Tags:

- `status=DISABLED|DELETED`

- [ ] **Step 6: Register auth beans**

In `AuthApiAutoConfiguration`, conditionally create:

- `AuthSessionMetrics`: Micrometer when `MeterRegistry` present, otherwise noop.
- `AuthSessionRevocationService`: when `FindByIndexNameSessionRepository` present.
- `IdentityUserStatusChangedEventListener`: when `AuthSessionRevocationService` present.

- [ ] **Step 7: Run auth session tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test -Dtest=IdentityUserStatusChangedEventListenerTest,SpringSessionAuthSessionRevocationServiceTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/auth/src/main/java/org/congcong/algomentor/auth/session backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java backend/auth/src/test/java/org/congcong/algomentor/auth/session
git commit -m "feat: revoke sessions on identity status changes"
```

### Task 7: Mentor API Integration and Security Verification

**Files:**
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/MentorApiApplicationTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AdminUserEndpointSecurityTest.java`

- [ ] **Step 1: Write failing integration tests**

Add or create tests that assert:

```java
@Test
void applicationContextLoadsAdminUserControllerFromIdentity()
```

and:

```java
@Test
void nonAdminCannotAccessAdminUsersEndpoint()
```

`AdminUserEndpointSecurityTest` should use Spring Security test support and assert `/api/admin/users` returns 403 for an authenticated non-admin user because `AuthSecurityPaths.ADMIN_API_PATTERN` already requires `ROLE_ADMIN`.

- [ ] **Step 2: Run mentor-api tests to verify identity auto-configuration is visible**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=MentorApiApplicationTest,AdminUserEndpointSecurityTest
```

Expected: FAIL until `auth` depends on `identity`, `IdentityAutoConfiguration` is imported from the identity jar, and `AdminUserController` can be loaded in the application context.

- [ ] **Step 3: Keep existing MyBatis and Flyway scanning contracts**

Do not add duplicate identity mapper or repository beans in `mentor-api`. `MentorApiMyBatisConfiguration` already loads `classpath*:mapper/**/*.xml`; `IdentityAutoConfiguration` must create `IdentityUserMapper` from the shared `SqlSessionTemplate`.

Keep `application.yml` Flyway locations as:

```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration
```

because it recursively scans module migration subdirectories.

- [ ] **Step 4: Run integration tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=MentorApiApplicationTest,AdminUserEndpointSecurityTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/mentor-api backend/identity backend/auth
git commit -m "test: verify admin user api integration"
```

### Task 8: Frontend API Types and Client

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/services/api.test.ts`

- [ ] **Step 1: Write failing API client tests**

Add tests in `frontend/src/services/api.test.ts`:

```ts
it('loads admin users with pagination filters and locale headers', async () => {
  setApiLocale('zh-CN');
  vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x51, 0x52, 0x53, 0x54, 0x55, 0x56]) });
  const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
    success: true,
    data: {
      items: [],
      total: 0,
      page: 2,
      pageSize: 10,
    },
    timestamp: '2026-06-30T00:00:00Z',
  })));
  vi.stubGlobal('fetch', fetchMock);

  await getAdminUsers({
    page: 2,
    pageSize: 10,
    keyword: 'alice',
    status: 'ACTIVE',
  });

  expect(fetchMock).toHaveBeenCalledWith(
    '/api/admin/users?page=2&pageSize=10&keyword=alice&status=ACTIVE',
    expect.objectContaining({
      credentials: 'same-origin',
      headers: expect.any(Headers),
    }),
  );
  expect(requestHeaders(fetchMock).get('Accept-Language')).toBe('zh-CN');
});
```

Add tests for:

- `getAdminUserDetail(42)` calls `/api/admin/users/42`.
- `updateAdminUserStatus(42, { status: 'DISABLED' })` uses `PATCH`, JSON body, CSRF.
- `deleteAdminUser(42)` uses `DELETE`, CSRF.

- [ ] **Step 2: Run API tests to verify they fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- services/api.test.ts
```

Expected: FAIL because types and functions are missing.

- [ ] **Step 3: Add frontend types**

In `frontend/src/types/api.ts`, change:

```ts
export type AuthUserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';
```

Add:

```ts
export interface AdminUserSummary {
  id: number;
  email?: string;
  displayName?: string;
  avatarUrl?: string;
  status: AuthUserStatus;
  roles: AuthRole[];
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string | null;
}

export interface AdminUserDetail extends AdminUserSummary {
  emailNormalized?: string;
  deletedAt?: string | null;
  deletedBy?: number | null;
}

export interface AdminUserPage {
  items: AdminUserSummary[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminUserListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  status?: AuthUserStatus | '';
}

export interface AdminUserStatusUpdateRequest {
  status: Extract<AuthUserStatus, 'ACTIVE' | 'DISABLED'>;
}
```

- [ ] **Step 4: Add API client functions**

In `frontend/src/services/api.ts`, import the new types and add:

```ts
export async function getAdminUsers(
  query: AdminUserListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminUserPage>> {
  const response = await apiFetch(`/api/admin/users${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin users request failed');
  }

  return response.json();
}

export async function getAdminUserDetail(userId: number, signal?: AbortSignal): Promise<ApiResponse<AdminUserDetail>> {
  const response = await apiFetch(`/api/admin/users/${userId}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user detail request failed');
  }

  return response.json();
}

export async function updateAdminUserStatus(
  userId: number,
  request: AdminUserStatusUpdateRequest,
): Promise<ApiResponse<AdminUserDetail>> {
  const response = await apiFetch(`/api/admin/users/${userId}/status`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user status update request failed');
  }

  return response.json();
}

export async function deleteAdminUser(userId: number): Promise<ApiResponse<AdminUserDetail>> {
  const response = await apiFetch(`/api/admin/users/${userId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user delete request failed');
  }

  return response.json();
}
```

- [ ] **Step 5: Run API tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- services/api.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/services/api.ts frontend/src/services/api.test.ts
git commit -m "feat: add admin user api client"
```

### Task 9: Frontend Navigation and Route Guard

**Files:**
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/app/navigation.test.ts`
- Modify: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/i18n/locales.ts`

- [ ] **Step 1: Write failing navigation tests**

Add to `navigation.test.ts`:

```ts
it('maps the admin users route', () => {
  expect(viewFromPath('/admin/users')).toBe('adminUsers');
  expect(pathForView('adminUsers')).toBe('/admin/users');
});
```

Add to `AppShell.test.tsx`:

```ts
it('shows user management navigation only with user manage permission', () => {
  render(
    <AppShell
      activeView="adminUsers"
      currentUser={{ ...user, roles: ['ADMIN'], permissions: ['user:manage'] }}
      onLogout={vi.fn()}
      onNavigate={vi.fn()}
      onToggleTheme={vi.fn()}
      theme="light"
    >
      <div>Users page</div>
    </AppShell>,
  );

  expect(screen.getByRole('button', { name: '用户管理' })).toHaveAttribute('aria-pressed', 'true');
});
```

Add to `App.test.tsx`:

```ts
it('redirects admin users route to home without user manage permission', async () => {
  vi.stubGlobal('fetch', mockAuthenticatedUserWithoutUserManageFetch());
  window.history.replaceState({}, '', '/admin/users');

  render(<App />);

  expect(await screen.findByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');
  expect(window.location.pathname).toBe('/');
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- app/navigation.test.ts app/AppShell.test.tsx App.test.tsx
```

Expected: FAIL because route/view/navigation item are missing.

- [ ] **Step 3: Add route and navigation item**

In `frontend/src/app/navigation.ts`:

- Import `UsersRound` from `lucide-react`.
- Add `adminUsers: '/admin/users'` to `APP_ROUTES`.
- Change `AppView` to include `'adminUsers'`.
- Add navigation item:

```ts
{
  view: 'adminUsers',
  labelKey: 'adminUsers',
  path: APP_ROUTES.adminUsers,
  icon: UsersRound,
  permission: 'user:manage',
}
```

- Add `viewFromPath('/admin/users') -> 'adminUsers'`.

- [ ] **Step 4: Add i18n nav labels**

In `LocaleResources.nav`, add:

```ts
adminUsers: string;
```

Chinese:

```ts
adminUsers: '用户管理',
```

English:

```ts
adminUsers: 'Users',
```

- [ ] **Step 5: Add route guard in App**

Update `normalizeAuthenticatedPath` and `navigateToView`:

```ts
if (view === 'adminUsers' && !hasPermission(user, 'user:manage')) {
  return DEFAULT_AUTHENTICATED_ROUTE;
}
```

and:

```ts
if (view === 'adminUsers' && !hasPermission(currentUser, 'user:manage')) {
  view = 'home';
}
```

For loading shell nav, include admin users only if a neutral loading placeholder is acceptable; otherwise leave loading shell unchanged and do not show permission-only nav before auth is known.

- [ ] **Step 6: Run navigation tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- app/navigation.test.ts app/AppShell.test.tsx App.test.tsx
```

Expected: PASS for navigation/guard tests. Page rendering can still fall back until Task 10 adds the page.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/navigation.ts frontend/src/app/navigation.test.ts frontend/src/app/AppShell.test.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/i18n/locales.ts
git commit -m "feat: add admin users route"
```

### Task 10: Frontend User Management Page

**Files:**
- Create: `frontend/src/admin/UserManagementPage.tsx`
- Create: `frontend/src/admin/UserManagementPage.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing page tests**

Create `UserManagementPage.test.tsx` with tests:

```ts
it('loads users with keyword status and pagination controls')

it('opens a user detail panel')

it('confirms and disables an active user then refreshes the current page')

it('confirms and restores a disabled user then refreshes the current page')

it('confirms soft delete and falls back to previous page when current page becomes empty')

it('shows a permission error for 401 or 403 responses')
```

The disable test should assert:

```ts
fireEvent.click(await screen.findByRole('button', { name: '禁用' }));
expect(screen.getByRole('dialog')).toHaveTextContent('确认禁用该用户');
fireEvent.click(screen.getByRole('button', { name: '确认' }));

await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
  '/api/admin/users/42/status',
  expect.objectContaining({
    method: 'PATCH',
    body: JSON.stringify({ status: 'DISABLED' }),
  }),
));
expect(fetchMock).toHaveBeenCalledWith('/api/admin/users?page=1&pageSize=20', expect.any(Object));
```

- [ ] **Step 2: Run page tests to verify they fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- admin/UserManagementPage.test.tsx
```

Expected: FAIL because page is missing.

- [ ] **Step 3: Add i18n page resources**

Add `adminUsers` section to `LocaleResources`:

```ts
adminUsers: {
  ariaLabel: string;
  title: string;
  searchPlaceholder: string;
  statusAll: string;
  statusActive: string;
  statusDisabled: string;
  statusDeleted: string;
  refresh: string;
  loading: string;
  empty: string;
  loadFailed: string;
  forbidden: string;
  id: string;
  email: string;
  displayName: string;
  roles: string;
  status: string;
  createdAt: string;
  lastLoginAt: string;
  updatedAt: string;
  deletedAt: string;
  deletedBy: string;
  actions: string;
  disable: string;
  restore: string;
  delete: string;
  confirm: string;
  confirmDisableTitle: string;
  confirmRestoreTitle: string;
  confirmDeleteTitle: string;
  confirmDeleteDescription: string;
  operationFailed: string;
  operationSucceeded: string;
};
```

- [ ] **Step 4: Implement page component**

`UserManagementPage.tsx` must:

- Use `getAdminUsers`, `getAdminUserDetail`, `updateAdminUserStatus`, `deleteAdminUser`.
- Keep state: `page`, `pageSize`, `keywordInput`, `keyword`, `status`, `selectedUser`, `loading`, `error`, `operation`.
- Use stable table layout with loading skeleton rows.
- Show empty state when `items.length === 0`.
- Show `查看`, `禁用` for ACTIVE, `恢复` for DISABLED, `删除` for ACTIVE/DISABLED, no mutation buttons for DELETED.
- Use native `window.confirm` or a simple accessible in-component dialog. Prefer in-component dialog because tests can query `role="dialog"`.
- On operation success, reload current page.
- If delete reload returns empty and `page > 1`, decrement page and reload.
- For 401/403, show forbidden text and a button that calls `onNavigateHome`.

Component props:

```ts
interface UserManagementPageProps {
  onNavigateHome: () => void;
}
```

- [ ] **Step 5: Wire page into App**

Import:

```ts
import UserManagementPage from './admin/UserManagementPage';
```

Render branch:

```tsx
: activeView === 'adminUsers' && hasPermission(currentUser, 'user:manage')
  ? <UserManagementPage onNavigateHome={() => navigateToView('home')} />
```

- [ ] **Step 6: Add focused styles**

In `styles.css`, add admin page classes with restrained dashboard styling:

- `.admin-users-page`
- `.admin-users-toolbar`
- `.admin-users-table-wrap`
- `.admin-users-table`
- `.admin-user-status`
- `.admin-user-actions`
- `.admin-user-detail`
- `.admin-confirm-dialog`
- `.admin-users-pagination`

Keep cards only for the detail panel/dialog/table wrapper, no nested cards.

- [ ] **Step 7: Run page tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- admin/UserManagementPage.test.tsx App.test.tsx
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/admin frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/i18n/locales.ts frontend/src/styles.css
git commit -m "feat: add admin user management page"
```

### Task 11: End-to-End Verification and Cleanup

**Files:**
- Modify: `docs/code-index.md`

- [ ] **Step 1: Run identity module tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl identity test
```

Expected: PASS.

- [ ] **Step 2: Run auth tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test
```

Expected: PASS.

- [ ] **Step 3: Run mentor-api focused tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=MentorApiApplicationTest,AdminUserEndpointSecurityTest
```

Expected: PASS.

- [ ] **Step 4: Run full backend tests**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 5: Run focused frontend tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- services/api.test.ts app/navigation.test.ts app/AppShell.test.tsx admin/UserManagementPage.test.tsx App.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Run full frontend tests**

Run:

```bash
make frontend-test
```

Expected: PASS.

- [ ] **Step 7: Run build if tests pass**

Run:

```bash
make build
```

Expected: PASS.

- [ ] **Step 8: Update code index**

Add to `docs/code-index.md` backend section:

```markdown
- `backend/identity`：身份本体模块，拥有用户/角色模型、用户与角色 MyBatis mapper、管理员用户管理 API、身份状态事件和用户软删除迁移；`auth` 依赖该模块完成认证流程中的用户创建、查询、角色和状态校验。
```

- [ ] **Step 9: Check git status**

Run:

```bash
git status --short
```

Expected: only files from this feature are modified.

- [ ] **Step 10: Final commit**

```bash
git add docs/code-index.md
git commit -m "docs: update identity module index"
```

## Manual Acceptance Checklist

- [ ] 管理员登录后能看到“用户管理”导航。
- [ ] 普通用户看不到“用户管理”导航。
- [ ] 普通用户访问 `/admin/users` 被前端带回首页。
- [ ] 非管理员请求 `/api/admin/users` 返回 403。
- [ ] 管理员可以按邮箱/昵称搜索用户。
- [ ] 管理员可以按 `ACTIVE`、`DISABLED`、`DELETED` 过滤。
- [ ] 不传状态时默认只返回 `ACTIVE` 和 `DISABLED`。
- [ ] 管理员可以查看用户详情，包含角色、最后登录、删除信息。
- [ ] 管理员不能禁用、恢复或删除自己。
- [ ] `ACTIVE -> DISABLED` 成功后发布事件并删除目标用户会话。
- [ ] `DISABLED -> ACTIVE` 成功后发布事件但不删除会话。
- [ ] `ACTIVE/DISABLED -> DELETED` 成功后发布事件并删除目标用户会话。
- [ ] `DELETED` 用户不能禁用、恢复或再次删除。
- [ ] `DISABLED` 和 `DELETED` 用户密码登录返回统一登录失败。
- [ ] `DISABLED` 和 `DELETED` 用户 OAuth 登录失败，不泄露状态原因给用户。

## Risk Notes

- Flyway 版本空间是全后端共享的，当前最高版本是 `V15__user_ai_preference_schema.sql`，本计划使用 `V16__identity_user_soft_delete.sql`。
- `auth_users` 和 `auth_user_roles` 迁移文件历史上仍在 `auth/V8__auth_schema.sql`，本计划不重写历史迁移，只让新增身份迁移归属 `identity`。
- `auth` 删除用户会话失败不回滚 `identity` 状态变更；监听器必须记录 error 日志和失败 metric。
- `identity` 的 admin controller 必须从 Spring Security `Authentication.getName()` 解析操作者用户 ID，不依赖 `auth` 的 `CurrentUserIdProvider`，避免 `identity -> auth -> identity` 模块循环。
