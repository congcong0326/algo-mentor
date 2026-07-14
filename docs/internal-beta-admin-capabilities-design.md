# 内测管理员业务能力研发设计

> 适用阶段：5-20 人封闭内测。
> 本文只设计业务后台能力；Prometheus、Grafana、外部告警和基础设施运维另行讨论。
> 技术约束遵循 `AGENTS.md`：Java 17、Spring MVC、PostgreSQL、Flyway、MyBatis、React + TypeScript，跨模块契约使用常量类或枚举管理。

---

## 0. 已定决策

本设计以以下讨论结论为实现基线，不在开发阶段重新扩大范围：

1. 内测规模为 5-20 人，采用数据库邮箱白名单控制注册和登录。
2. 白名单开关、邮箱列表均由管理员页面维护，不依赖修改环境变量或重启服务。
3. 从白名单移除已注册邮箱后，立即吊销该用户全部 Session；重新加入后保留原账号和学习数据。
4. 密码注册暂不验证邮箱所有权。系统只声明“邮箱在内测白名单中”，不得声明“邮箱已验证”。
5. AI 成本的硬控制仍是单用户每日请求次数；Token 和金额只用于观测。用户主动发起的 AI 入口共享每日额度，同一 Agent run 内的子模型调用不重复扣减入口额度。
6. 模型价格按当前配置实时回算。调价后历史估算成本允许变化，不做价格版本和历史成本快照。
7. 模型价格至少包含非缓存输入、缓存输入、输出三种单价，单位统一为 `USD / 1M tokens`，并支持成本倍率。
8. 管理员可实时关闭全局 AI、调整全局默认每日额度，并为单个用户暂停 AI 或覆盖每日额度。
9. 管理员可查看最近 30 天的完整 AI 输入、输出、工具调用、用户代码和关联业务上下文；每次查看必须记录审计。
10. 用户与管理员通过反馈信箱交流。只能由用户发起会话，管理员不能主动创建私信。
11. 内测只有一名管理员，不建设管理员账号管理、角色分级和审计查询页面。
12. 保留现有用户禁用、恢复和软删除能力，不新增已删除用户恢复。
13. 保留现有 30 分钟 AI 运行锁 TTL，不提供管理员强制解锁、重试或取消运行。
14. 不建设题目或学习计划模板的在线编辑、启停和 CMS 能力。

## 1. 背景

当前项目已经具备以下基础：

- `identity` 模块提供管理员用户搜索、详情、禁用、恢复和软删除。
- `auth` 模块提供密码登录、Google OAuth、Spring Session 和用户状态变化后的 Session 吊销。
- `ai-governance` 已保存每次 AI run 的用户、场景、状态、模型和 Token，并执行共享每日请求次数配额。
- `agent-persistence-postgres` 已保存 Agent run、step、context snapshot、message、tool call 和大结果 blob。
- 前端已有管理员用户页、题库只读页和 AI Debug 页面。

内测前的缺口不在于再增加通用后台菜单，而在于形成四个闭环：

```text
谁能进入
  -> 每个用户能消耗多少 AI
    -> 出现异常时管理员能否定位
      -> 用户能否把故障和建议反馈回来
```

## 2. 目标与非目标

### 2.1 目标

- 管理员可以在线控制内测准入，不需要部署操作。
- 管理员可以看到 Token 和按当前价格估算的成本，并能立即止损。
- 管理员可以从用户、反馈或失败记录定位到具体 AI run。
- 管理员可以在合规边界内查看完整诊断内容，并对访问行为留痕。
- 用户可以提交故障、建议和一般意见，并与管理员继续回复。
- 管理员首页优先展示需要处理的异常和待办，而不是增长分析。
- 密码用户忘记密码时，管理员可以提供一次性临时密码恢复访问。

### 2.2 非目标

- 不做基础设施监控、告警通知、日志检索或 Grafana 嵌入。
- 不做邀请码、邀请邮件、申请审批和邀请过期。
- 不做邮箱验证码或 OAuth-only 强制策略。
- 不做按美元或 Token 的硬预算。
- 不做按 AI purpose 分配独立额度；所有用户主动发起的 AI 入口继续共享每日额度。
- 不因为成本统计而改变现有后台预生成任务的专用安全配额；后台任务仍需受全局和用户 AI 开关控制。
- 不做模型价格历史版本、账单和财务审计。
- 不做管理员主动私信、群发公告、附件、客服分配、优先级和 SLA。
- 不做 Agent run 的人工重试、取消和锁释放。
- 不做内容管理和业务数据重置。
- 不做多管理员权限配置页面。

## 3. 总体架构

### 3.1 模块落位

本阶段不新增 Maven 模块，沿用现有所有权边界：

```text
backend/common
  admin/audit/             管理员审计端口、动作和目标类型契约

backend/auth
  betaaccess/              白名单设置、邮箱列表、认证准入策略
  passwordreset/           临时密码、强制改密和 Session 吊销
  controller/admin/        白名单与密码重置管理员 API

backend/identity
  继续拥有用户本体、状态、角色和现有管理员用户 API

backend/ai-governance
  policy/runtime/          数据库动态全局策略与用户覆盖策略
  pricing/                 模型当前价格与估算成本
  adminquery/              用量、成本和 run 列表查询端口

backend/agent-persistence-postgres
  admintrace/              管理员 trace 读取端口
  retention/               诊断内容 30 天脱敏清理

backend/mentor-api
  api/controller/admin/    管理员概览、AI 治理、run 详情、反馈 API
  api/feedback/            用户反馈应用服务、MyBatis repository、DTO
  api/admin/audit/         管理员审计持久化实现

frontend/src/admin
  AdminOverviewPage
  BetaAccessPage
  AiGovernancePage
  AiRunPage
  FeedbackManagementPage

frontend/src/feedback
  UserFeedbackPage
```

### 3.2 依赖原则

- `auth` 不直接查询 `ai-governance` 表。
- `ai-governance` 不直接读取 Agent trace 表。
- `mentor-api` 只通过各模块暴露的 service/repository 端口聚合管理员响应。
- Agent trace SQL 继续保存在 `agent-persistence-postgres`，不能搬到 `mentor-api`。
- AI 用量、额度和价格 SQL 继续保存在 `ai-governance`。
- 管理员审计使用 `common` 中的端口，具体 PostgreSQL 实现在 `mentor-api`，避免 `auth` 反向依赖 API 模块。

### 3.3 管理员业务流

```text
管理员登录 /admin
  -> 查看异常优先概览
  -> 从失败 run / 用户 / 反馈进入详情
  -> 必要时调整用户 AI 状态或额度
  -> 成本异常时调整全局额度或关闭全局 AI
  -> 所有敏感查看和配置修改写入审计
```

## 4. 权限与路由

### 4.1 后端权限能力

保留 `ROLE_ADMIN` 作为 `/api/admin/**` 的最终安全边界，同时在 `AuthPermission` 增加前端能力标识：

```text
admin-overview:read
beta-access:manage
ai-governance:manage
ai-run:read
feedback:manage
```

当前 `ADMIN` 角色拥有全部新增权限；本阶段不提供角色编辑，也不增加第二种管理员角色。

新增权限字符串应加入后端 `AuthPermission` 枚举和前端 `AuthPermission` 联合类型，禁止在页面中散落字符串。

### 4.2 前端路由

```text
/admin                         管理员概览，管理员默认登录落点
/admin/beta-access             内测准入
/admin/users                   现有用户管理，增加 AI 与密码运维信息
/admin/ai                      AI 治理、价格和用量
/admin/ai/runs                 AI 调用列表
/admin/ai/runs/:runId          AI 调用详情
/admin/feedback                反馈管理
/feedback                      普通用户反馈信箱
```

现有 `/admin/problems` 和 `/debug` 保留，但不属于本期新增范围。

## 5. 数据模型与迁移

Flyway 版本在所有模块共享。当前最大版本为 `V27`，以下版本号为暂定值；实施前必须再次扫描全仓迁移并顺延冲突版本。

### 5.1 V28：内测准入与临时密码

建议迁移位置：

```text
backend/auth/src/main/resources/db/migration/auth/V28__beta_access_and_password_reset.sql
```

#### `auth_beta_access_settings`

单行全局设置：

```sql
CREATE TABLE auth_beta_access_settings (
  id SMALLINT PRIMARY KEY,
  email_allowlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_by BIGINT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_auth_beta_access_settings_singleton CHECK (id = 1)
);

INSERT INTO auth_beta_access_settings (id, email_allowlist_enabled)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;
```

字段语义：

- `FALSE`：不执行邮箱白名单限制，仍执行用户状态和正常认证校验。
- `TRUE`：密码注册、密码登录、OAuth 登录和已登录 API 请求都执行白名单策略。
- 管理员邮箱或 `ROLE_ADMIN` 用户始终放行，防止后台自锁。

#### `auth_beta_allowed_email`

```sql
CREATE TABLE auth_beta_allowed_email (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(320) NOT NULL,
  email_normalized VARCHAR(320) NOT NULL,
  created_by BIGINT NOT NULL REFERENCES auth_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_auth_beta_allowed_email_normalized UNIQUE (email_normalized)
);

CREATE INDEX idx_auth_beta_allowed_email_created_at
  ON auth_beta_allowed_email(created_at DESC);
```

约束：

- 统一使用 `trim + lowercase(Locale.ROOT)` 生成 `email_normalized`。
- 原始邮箱只用于管理员展示，准入只比较规范化字段。
- 白名单不记录“已验证”状态，因为密码注册没有所有权验证。
- 批量添加在 service 层去重，重复邮箱按幂等成功处理。

#### `auth_password_credentials` 扩展

```sql
ALTER TABLE auth_password_credentials
  ADD COLUMN reset_required BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN temporary_password_expires_at TIMESTAMPTZ NULL,
  ADD COLUMN temporary_password_consumed_at TIMESTAMPTZ NULL,
  ADD COLUMN password_changed_at TIMESTAMPTZ NULL,
  ADD COLUMN reset_by BIGINT NULL REFERENCES auth_users(id);
```

约束由 service 保证：

- `reset_required = FALSE` 时临时密码字段全部为空。
- `reset_required = TRUE` 时必须存在未过期的 `temporary_password_expires_at`。
- 临时密码成功登录后原子写入 `temporary_password_consumed_at`，不能再次创建新 Session。
- 完成改密后清空临时密码状态并更新 `password_changed_at`。

### 5.2 V29：动态 AI 策略与当前价格

建议迁移位置：

```text
backend/ai-governance/src/main/resources/db/migration/ai/V29__ai_runtime_policy_and_model_price.sql
```

#### `ai_runtime_settings`

```sql
CREATE TABLE ai_runtime_settings (
  id SMALLINT PRIMARY KEY,
  ai_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  default_daily_request_limit INTEGER NOT NULL DEFAULT 50,
  updated_by BIGINT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ai_runtime_settings_singleton CHECK (id = 1),
  CONSTRAINT ck_ai_runtime_settings_daily_limit CHECK (default_daily_request_limit > 0)
);

INSERT INTO ai_runtime_settings (id, ai_enabled, default_daily_request_limit)
VALUES (1, TRUE, 50)
ON CONFLICT (id) DO NOTHING;
```

第一版只动态化两个真正需要止损的字段。以下 purpose 级能力仍由现有配置管理：

- `maxRequestBytes`
- `maxOutputTokens`
- `maxSteps`
- `streamingAllowed`
- `toolsAllowed`
- `structuredOutputRequired`
- `adminOnly`

#### `ai_user_policy`

```sql
CREATE TABLE ai_user_policy (
  user_id BIGINT PRIMARY KEY REFERENCES auth_users(id),
  ai_enabled_override BOOLEAN NULL,
  daily_request_limit_override INTEGER NULL,
  updated_by BIGINT NOT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ai_user_policy_daily_limit
    CHECK (daily_request_limit_override IS NULL OR daily_request_limit_override > 0)
);
```

有效策略：

```text
effectiveAiEnabled = global.aiEnabled
                     AND purpose.enabled
                     AND user.aiEnabledOverride != false

effectiveDailyLimit = user.dailyRequestLimitOverride
                      ?? global.defaultDailyRequestLimit
```

`ai_enabled_override = NULL` 表示继承全局；本期不允许用户级 `TRUE` 绕过全局关闭。

#### `ai_model_price`

```sql
CREATE TABLE ai_model_price (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(80) NOT NULL,
  model VARCHAR(160) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  input_price_per_million NUMERIC(20, 8) NOT NULL,
  cached_input_price_per_million NUMERIC(20, 8) NOT NULL,
  output_price_per_million NUMERIC(20, 8) NOT NULL,
  cost_multiplier NUMERIC(12, 6) NOT NULL DEFAULT 1,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT NOT NULL REFERENCES auth_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_model_price_provider_model UNIQUE (provider, model),
  CONSTRAINT ck_ai_model_price_currency CHECK (currency = 'USD'),
  CONSTRAINT ck_ai_model_price_non_negative CHECK (
    input_price_per_million >= 0
    AND cached_input_price_per_million >= 0
    AND output_price_per_million >= 0
    AND cost_multiplier > 0
  )
);
```

本表只保存当前价格。更新同一 `(provider, model)` 行会改变所有历史区间的估算成本。

#### `ai_llm_call_usage`

`ai_run_admissions` 是用户入口准入和 run 聚合表，不能准确表达一个 Agent run 内多个模型调用，也无法自动覆盖直接调用 `LlmGateway` 的业务服务。模型成本必须使用调用级 Token 台账：

```sql
CREATE TABLE ai_llm_call_usage (
  id BIGSERIAL PRIMARY KEY,
  call_id VARCHAR(100) NOT NULL,
  run_id VARCHAR(80) NULL,
  user_id BIGINT NULL REFERENCES auth_users(id),
  purpose VARCHAR(64) NOT NULL,
  source VARCHAR(64) NOT NULL,
  call_kind VARCHAR(32) NOT NULL,
  step_index INTEGER NULL,
  provider VARCHAR(80) NULL,
  model VARCHAR(160) NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(80) NULL,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  cached_tokens BIGINT NOT NULL DEFAULT 0,
  reasoning_tokens BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_llm_call_usage_call_id UNIQUE (call_id),
  CONSTRAINT ck_ai_llm_call_usage_kind
    CHECK (call_kind IN ('AGENT_STEP', 'DIRECT', 'BACKGROUND', 'LEGACY_RUN_AGGREGATE')),
  CONSTRAINT ck_ai_llm_call_usage_status
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_ai_llm_call_usage_user_started
  ON ai_llm_call_usage(user_id, started_at DESC);
CREATE INDEX idx_ai_llm_call_usage_model_started
  ON ai_llm_call_usage(provider, model, started_at DESC);
CREATE INDEX idx_ai_llm_call_usage_run
  ON ai_llm_call_usage(run_id, started_at);
```

迁移时可为已有且 Token 非零的 `ai_run_admissions` 插入一条 `LEGACY_RUN_AGGREGATE` 记录。新版本上线后，成本查询只读取 `ai_llm_call_usage`，避免同时读取 run 聚合字段造成重复计费。

### 5.3 V30：管理员审计与反馈信箱

建议迁移位置：

```text
backend/mentor-api/src/main/resources/db/migration/V30__admin_audit_and_user_feedback.sql
```

#### `admin_operation_audit`

```sql
CREATE TABLE admin_operation_audit (
  id BIGSERIAL PRIMARY KEY,
  operator_user_id BIGINT NOT NULL REFERENCES auth_users(id),
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_ref VARCHAR(160) NULL,
  outcome VARCHAR(24) NOT NULL,
  request_id VARCHAR(128) NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_admin_operation_audit_outcome
    CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_admin_operation_audit_operator_created
  ON admin_operation_audit(operator_user_id, created_at DESC);
CREATE INDEX idx_admin_operation_audit_target
  ON admin_operation_audit(target_type, target_ref, created_at DESC);
```

禁止写入：

- 密码和临时密码。
- 完整 prompt、response、代码或工具结果。
- Cookie、Authorization、API key 和 access token。
- 白名单邮箱正文；审计只记录白名单记录 ID。

#### `user_feedback_thread`

```sql
CREATE TABLE user_feedback_thread (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id),
  category VARCHAR(24) NOT NULL DEFAULT 'OTHER',
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  subject VARCHAR(200) NULL,
  source_path VARCHAR(500) NULL,
  source_request_id VARCHAR(128) NULL,
  source_run_id VARCHAR(80) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ NULL,
  closed_by BIGINT NULL REFERENCES auth_users(id),
  CONSTRAINT ck_user_feedback_thread_category
    CHECK (category IN ('BUG', 'SUGGESTION', 'OTHER')),
  CONSTRAINT ck_user_feedback_thread_status
    CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_user_feedback_thread_user_updated
  ON user_feedback_thread(user_id, updated_at DESC);
CREATE INDEX idx_user_feedback_thread_status_updated
  ON user_feedback_thread(status, updated_at DESC);
```

`source_run_id` 不建立数据库外键，由 service 校验该 run 属于当前用户，避免反馈模块依赖 AI 表的生命周期。

#### `user_feedback_message`

```sql
CREATE TABLE user_feedback_message (
  id BIGSERIAL PRIMARY KEY,
  thread_id BIGINT NOT NULL REFERENCES user_feedback_thread(id) ON DELETE CASCADE,
  sender_type VARCHAR(16) NOT NULL,
  sender_user_id BIGINT NOT NULL REFERENCES auth_users(id),
  content TEXT NOT NULL,
  read_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_user_feedback_message_sender_type
    CHECK (sender_type IN ('USER', 'ADMIN')),
  CONSTRAINT ck_user_feedback_message_content
    CHECK (char_length(content) BETWEEN 1 AND 4000)
);

CREATE INDEX idx_user_feedback_message_thread_created
  ON user_feedback_message(thread_id, created_at);
```

### 5.4 V31：诊断 trace 保留状态

建议迁移位置：

```text
backend/agent-persistence-postgres/src/main/resources/db/migration/agent/V31__agent_diagnostic_retention.sql
```

```sql
ALTER TABLE agent_run
  ADD COLUMN diagnostic_retention_expires_at TIMESTAMPTZ NULL,
  ADD COLUMN diagnostic_redacted_at TIMESTAMPTZ NULL;

CREATE INDEX idx_agent_run_diagnostic_retention
  ON agent_run(diagnostic_retention_expires_at)
  WHERE diagnostic_redacted_at IS NULL;
```

新增 run 默认设置 `diagnostic_retention_expires_at = started_at + 30 days`。已有 run 可按 `COALESCE(ended_at, started_at) + 30 days` 回填。

## 6. 内测准入设计

### 6.1 核心服务

`auth.betaaccess` 建议包含：

```text
BetaAccessSettings
BetaAllowedEmail
BetaAccessDecision
BetaAccessRepository
BetaAccessPolicy
BetaAccessAdminService
BetaAccessErrorCode
BetaAccessApiContractConstants
```

公共错误码：

```text
AUTH_BETA_ACCESS_DENIED
BETA_ACCESS_EMAIL_INVALID
BETA_ACCESS_EMAIL_ALREADY_EXISTS
BETA_ACCESS_EMAIL_NOT_FOUND
BETA_ACCESS_SETTINGS_CONFLICT
```

### 6.2 准入判定

`BetaAccessPolicy.evaluate(emailNormalized, admin)` 固定顺序：

1. 全局白名单开关关闭，允许。
2. 当前身份是管理员或邮箱命中配置的管理员邮箱，允许。
3. 邮箱为空或格式无效，拒绝。
4. 规范化邮箱存在于白名单，允许。
5. 其他情况拒绝。

不得由前端传入 `admin=true`。管理员身份只能来自受信认证上下文或服务端 `adminEmails` 配置。

### 6.3 接入点

必须统一接入：

- `PasswordUserService.register(...)`：创建 `auth_users` 前检查。
- `PasswordUserDetailsService` 或认证 provider：密码认证成功前检查。
- `OAuth2LoginUserService.syncGoogleUser(...)`：创建或同步账号前检查 provider 邮箱。
- `ActiveIdentityUserFilter`：对已登录 API 请求再次检查，保证白名单移除后即使 Session 吊销失败也会被拒绝。

### 6.4 移除邮箱

管理员移除白名单邮箱：

```text
删除 auth_beta_allowed_email
  -> 按 email_normalized 查询已注册用户
  -> 存在用户则 AuthSessionRevocationService.revokeAll(userId)
  -> 写管理员审计
```

Session 吊销失败不回滚白名单删除。后续 API 请求仍会被 `ActiveIdentityUserFilter` 拒绝；同时记录错误日志和失败计数。

移除白名单不改变 `auth_users.status`，不删除学习数据。重新加入后用户可以继续登录。

### 6.5 管理员 API

```text
GET    /api/admin/beta-access
PATCH  /api/admin/beta-access/settings
POST   /api/admin/beta-access/emails
DELETE /api/admin/beta-access/emails/{allowedEmailId}
```

`GET` 返回：

- 白名单开关。
- 分页邮箱列表。
- 每个邮箱是否已注册、关联用户 ID 和用户状态。
- 更新时间和操作人。

`POST` 请求体支持一次提交多个邮箱，建议上限 100；响应返回新增、已存在和无效项数量。

## 7. 管理员密码重置

### 7.1 管理员操作

```text
POST /api/admin/users/{userId}/password-reset
```

执行规则：

1. 目标用户必须存在且不是 `DELETED`。
2. 管理员不能重置自己的密码。
3. 目标用户必须已有密码凭据；OAuth-only 用户返回 409。
4. 服务端使用 `SecureRandom` 生成高强度临时密码。
5. 只保存 BCrypt hash，不保存明文。
6. 临时密码有效期默认 24 小时。
7. 吊销目标用户全部 Session。
8. 明文临时密码只在本次成功响应中返回一次，禁止日志记录。
9. 写管理员审计，但审计 metadata 不包含临时密码。

建议错误码：

```text
AUTH_PASSWORD_CREDENTIAL_NOT_FOUND
AUTH_PASSWORD_RESET_SELF_FORBIDDEN
AUTH_TEMPORARY_PASSWORD_EXPIRED
AUTH_TEMPORARY_PASSWORD_CONSUMED
AUTH_PASSWORD_CHANGE_REQUIRED
```

### 7.2 临时密码登录

临时密码认证成功后：

- 原子写入 `temporary_password_consumed_at`。
- 创建带 `passwordChangeRequired=true` 的受限 Session。
- `CurrentUserResponse` 增加 `passwordChangeRequired`。
- 前端强制进入 `/password/change-required`。
- 除当前用户、修改密码和退出接口外，其他业务 API 返回 403 `AUTH_PASSWORD_CHANGE_REQUIRED`。

如果用户在完成改密前丢失该 Session，临时密码已经消费，必须联系管理员再次重置。

### 7.3 完成改密

```text
POST /api/auth/password/complete-reset
```

请求只包含新密码和确认密码。服务端从受限 Session 解析用户 ID，不接收客户端声明的 userId。

完成后：

- 更新密码 hash。
- 清空 `reset_required`、过期时间、消费时间和 `reset_by`。
- 更新 `password_changed_at`。
- 将当前 Session 更新为正常认证状态。

## 8. 动态 AI 治理

### 8.1 策略读取

新增 `AiRuntimePolicyService`，在每次 admission 时读取：

- 数据库全局 `ai_runtime_settings`。
- 当前用户 `ai_user_policy`。
- 现有 `AiPurposePolicy` 静态限制。

内测规模很小，第一版直接查数据库，不增加本地缓存和失效广播。这样管理员修改后下一次请求立即生效。

数据库没有设置行时使用现有配置默认值，并记录错误日志；迁移应预插入单行设置，正常环境不应走此兜底。

### 8.2 admission 顺序

在现有 `AiRunAdmissionService` 中调整为：

1. 解析静态 purpose 策略。
2. 读取全局动态设置和用户覆盖。
3. 检查全局 AI 开关。
4. 检查用户 AI 开关。
5. 执行认证、管理员限定、请求大小等现有校验。
6. 以有效每日额度调用 `tryConsumeRequest(...)`。
7. 获取现有用户级运行锁并继续执行。

新增拒绝码：

```text
AI_GLOBALLY_DISABLED
AI_USER_DISABLED
```

用户界面可以统一展示“AI 功能暂不可用”，管理员 run 列表必须保留具体拒绝原因。

### 8.3 管理员 API

```text
GET   /api/admin/ai/settings
PATCH /api/admin/ai/settings

GET   /api/admin/users/{userId}/ai-policy
PATCH /api/admin/users/{userId}/ai-policy
```

全局设置只允许修改：

- `aiEnabled`
- `defaultDailyRequestLimit`

用户覆盖只允许修改：

- `aiEnabledOverride`
- `dailyRequestLimitOverride`

不提供“清零今日使用量”。提高额度通过覆盖 limit 完成，历史 request count 保持真实。

### 8.4 模型调用覆盖

动态开关和成本观测必须覆盖两类调用：

1. **Agent 调用**：`AgentLoopRunner` 每个 step 的 provider 调用写入一条 `AGENT_STEP` 用量记录；run 入口只消费一次共享每日额度。
2. **直接调用**：业务服务直接调用 `LlmGateway.complete/stream` 时，通过 `AiGovernedCompletionService` 或等价统一包装记录 provider、model 和 usage，禁止继续裸调用后不记账。

当前至少需要迁移以下直接调用：

- `PracticeCodeReviewService`
- `ReviewCardService`
- `RecallJudgeService`

为调用级统计补充稳定 source：

```text
PRACTICE_CODE_REVIEW
REVIEW_CARD_GENERATION
RECALL_JUDGE
```

第一版复用现有 `AiPurpose`，通过更细的 `AiRunSource` 区分这些调用，不为成本展示新增 purpose 策略。

规则：

- 代码 Review 如果是 Agent tool 的子调用，关联父 `runId`，不再次消费用户入口额度，但必须单独记录实际模型和 Token。
- 复述判定属于用户主动发起的 AI 入口，应走正常共享 admission。
- 复习卡后台预生成保留现有 `REVIEW_CARD_GEN` 专用安全配额，不重复消费用户入口额度；全局 AI 关闭或用户 AI 暂停时必须停止生成，并记录为 `BACKGROUND` 调用。
- provider 在返回 usage 前失败时仍记录失败调用；没有 usage 时 Token 为零，错误码保留。
- 禁止同时把同一调用写入 `LEGACY_RUN_AGGREGATE` 和新调用级记录。

## 9. 模型价格与成本估算

### 9.1 估算公式

```text
uncachedInputTokens = max(inputTokens - cachedTokens, 0)

estimatedCostUsd = (
    uncachedInputTokens * inputPricePerMillion
  + cachedTokens * cachedInputPricePerMillion
  + outputTokens * outputPricePerMillion
) / 1_000_000 * costMultiplier
```

规则：

- `reasoningTokens` 只用于分析，默认已包含在 `outputTokens` 中，不重复计费。
- 某次模型调用缺少 provider/model 或价格未配置时，标记 `priced=false`。
- 未定价调用的 Token 和调用数仍进入“未定价”统计，但不按 `$0` 混入已定价成本。
- 所有金额使用 `BigDecimal`，数据库和 Java 计算禁止使用 `double`。
- 页面统一显示“按当前价格估算”，不得使用“实际账单”字样。
- provider 使用规范化小写 ID，model 使用 provider 返回的精确模型 ID，价格匹配不得依赖展示名称。

### 9.2 价格 API

```text
GET   /api/admin/ai/model-prices
POST  /api/admin/ai/model-prices
PATCH /api/admin/ai/model-prices/{priceId}
```

不提供物理删除。管理员可以停用价格；停用后相关 run 进入未定价统计。

### 9.3 用量与成本 API

```text
GET /api/admin/ai/usage/summary?from=&to=
GET /api/admin/ai/usage/by-user?from=&to=&page=&pageSize=
GET /api/admin/ai/usage/by-model?from=&to=
GET /api/admin/ai/usage/by-purpose?from=&to=
```

成本和 Token 查询来源以 `ai_llm_call_usage` 为准，因为它能表达每次真实 provider 调用和实际模型。`ai_run_admissions` 继续负责入口准入、run 状态和聚合排障；`ai_daily_usage` 继续用于原子额度消费和当天 request count。

默认查询当天，最大区间建议限制为 90 天。管理员概览只查询当天和最近失败记录。

## 10. AI 调用排障

### 10.1 列表

```text
GET /api/admin/ai/runs
```

支持筛选：

- 时间范围。
- 用户 ID 或邮箱关键词。
- purpose、source。
- provider、model。
- status、errorCode、rejectionCode。

列表字段：

- `runId`、用户、场景、状态。
- provider、model。
- 输入、缓存、输出、总 Token。
- 当前价格估算成本或未定价标识。
- 开始、完成、耗时。
- 错误码或拒绝码。
- `traceAvailable` 和诊断过期时间。

### 10.2 详情

```text
GET /api/admin/ai/runs/{runId}
```

详情由 `mentor-api` 聚合：

- `ai-governance`：admission、状态、usage、错误和成本。
- `identity`：用户基本信息。
- `agent-persistence-postgres`：run、step、context snapshot、message、tool call 和 blob preview。
- 业务 repository：可选的学习计划、练习会话和代码 Review 引用。

完整内容返回前必须调用已有 `AiTraceAccessPolicy`，并增加 30 天期限判定。每次成功或拒绝查看都记录审计。

禁止：

- 批量导出完整 trace。
- 在列表 API 返回完整 prompt 或代码。
- 从详情页修改用户业务数据。
- 人工重试、取消或释放 run 锁。

### 10.3 30 天保留与业务数据边界

需要区分两类数据：

1. **业务所有数据**：用户可见的聊天消息、学习计划、练习记录和代码 Review，继续遵循各业务自身生命周期。本设计不能为了清理管理员 trace 而删除这些数据。
2. **诊断副本**：context snapshot、完整工具参数和结果、blob、请求快照等管理员排障数据，保留 30 天后脱敏或删除。

双重保证：

- API 层：超过 `diagnostic_retention_expires_at` 后立即返回 summary，拒绝完整内容，即使清理任务尚未执行。
- 清理任务：定期清空 snapshot 中的 message/tool/request 内容、工具参数和结果，删除关联 blob，并写入 `diagnostic_redacted_at`。

`agent_message.content` 等用户业务数据不在本任务中删除；管理员 API 在 30 天后不得通过诊断接口读取它们。

清理任务必须幂等，建议每天执行一次，每批限制数量，避免长事务。

## 11. 反馈信箱

### 11.1 用户侧

```text
POST /api/feedback
GET  /api/feedback
GET  /api/feedback/{threadId}
POST /api/feedback/{threadId}/messages
POST /api/feedback/{threadId}/read
```

用户只能访问自己的 thread。创建反馈时输入：

- 分类：故障、建议、其他。
- 可选主题。
- 正文。

前端自动附带：

- 当前路由。
- 最近一次相关 `requestId`。
- 页面当前已知的 `runId`。

后端必须验证 `sourceRunId` 属于当前用户；验证失败时忽略关联或返回 400，不能建立越权引用。

用户在已关闭 thread 中发送新消息时自动重新打开，减少额外交互。

### 11.2 管理员侧

```text
GET   /api/admin/feedback
GET   /api/admin/feedback/{threadId}
POST  /api/admin/feedback/{threadId}/messages
PATCH /api/admin/feedback/{threadId}/status
POST  /api/admin/feedback/{threadId}/read
```

管理员可以：

- 按 OPEN/CLOSED、分类、用户和未读状态筛选。
- 回复用户。
- 关闭或重新打开。
- 跳转用户详情和关联 AI run。

管理员不能：

- 创建没有用户发起的 thread。
- 上传附件。
- 删除用户消息或修改历史正文。

## 12. 管理员概览

```text
GET /api/admin/overview
```

响应只包含可行动信息：

- 白名单开关、白名单人数、已注册白名单人数。
- 当前全局 AI 开关和默认每日额度。
- 今日用户 AI 入口请求、成功、失败、取消、额度拒绝。
- 今日实际模型调用数；不得与入口请求数混为同一个指标。
- 今日输入、缓存、输出和总 Token。
- 今日已定价估算成本、未定价调用数和未定价 Token。
- 达到或接近额度的用户。
- OPEN 反馈数量和管理员未读消息数量。
- 最近失败的 AI run。

“接近额度”第一版定义为 `requestCount / effectiveLimit >= 0.8`，阈值作为后端常量集中管理。

概览不包含 DAU、留存、转化漏斗、收入预测或复杂图表。

## 13. 用户管理页扩展

现有 `AdminUserDetailResponse` 不直接塞入所有跨模块数据，避免 `identity` 依赖 `ai-governance`。前端打开用户详情后并行查询：

```text
GET /api/admin/users/{userId}
GET /api/admin/users/{userId}/ai-policy
GET /api/admin/ai/usage/summary?userId={userId}&...
GET /api/admin/ai/runs?userId={userId}&...
GET /api/admin/feedback?userId={userId}&...
```

用户详情新增区域：

- 是否在白名单。
- 有效 AI 状态和有效每日额度。
- 今日请求、Token、估算成本。
- 近 7/30 天估算成本。
- 最近失败 run。
- OPEN 反馈数量。
- 管理员临时密码重置操作。

保留现有禁用、恢复和软删除行为。

## 14. 管理员审计

### 14.1 必须记录的动作

```text
BETA_ACCESS_SETTING_UPDATE
BETA_ALLOWED_EMAIL_ADD
BETA_ALLOWED_EMAIL_REMOVE
AI_GLOBAL_SETTING_UPDATE
AI_USER_POLICY_UPDATE
AI_MODEL_PRICE_CREATE
AI_MODEL_PRICE_UPDATE
AI_RUN_TRACE_VIEW
USER_PASSWORD_RESET
```

反馈消息本身已经是持久化业务记录，不额外为每条回复写审计。

### 14.2 审计端口

`common` 中定义：

```java
public interface AdminOperationAuditRecorder {
  void record(AdminOperationAuditEvent event);
}
```

事件字段使用强类型 enum 表达 action、targetType 和 outcome。没有持久化实现时提供 no-op bean，保证底层模块单测和非数据库上下文可启动。

审计写入失败不应回滚已经完成的白名单或止损操作，但必须记录 error 日志和 Micrometer 失败计数。

## 15. 前端信息架构

管理员导航建议顺序：

```text
概览
内测准入
用户管理
AI 治理
AI 调用
反馈
题库
AI Debug
```

交互要求：

- 全局 AI 开关、白名单开关、用户 AI 暂停使用 toggle，并显示影响说明和确认弹窗。
- 价格使用数值输入，明确单位 `USD / 1M tokens`，不得用普通文本输入伪装。
- 状态筛选使用下拉或 segmented control。
- run 列表和用户列表保持稳定表格高度，加载时使用 skeleton。
- 完整 trace 详情使用分区 tabs：概览、消息、步骤、工具、上下文；不把所有 JSON 同时铺在一个页面。
- JSON 只读展示需要折叠、复制按钮和敏感字段二次脱敏。
- 反馈入口在普通用户导航或账号区域提供明确图标，并显示未读徽标。
- 临时密码响应只在确认对话框中展示一次；关闭后不能重新读取。
- 所有新增文案同步维护中英文 i18n。

## 16. 安全与隐私

- 所有管理员 API 继续受 `/api/admin/** -> ROLE_ADMIN` 保护。
- 所有写 API 使用现有 CSRF 机制和请求 ID。
- 白名单、模型价格和用户覆盖更新不接受客户端声明的 operator ID。
- 反馈用户 ID、trace user ID 和密码重置目标均由路径与认证上下文校验。
- 管理员 trace 返回前继续使用现有 redaction policy，不能因为管理员有权限就恢复凭据。
- 临时密码不得出现在日志、审计、指标 tag、异常消息和持久化明文字段中。
- 价格、Token 和金额不得作为 Micrometer 高基数 tag。
- 管理员概览和列表只返回低敏摘要；完整内容必须进入单条详情并触发审计。
- 密码白名单冒用是已接受风险；普通用户接口不得暴露白名单列表。注册限流和邮箱所有权验证留待后续安全增强。

## 17. 并发与事务

- 白名单添加依赖唯一键保证并发幂等。
- 白名单删除先提交准入状态，再执行 Session 吊销；吊销失败时由请求过滤器兜底拒绝。
- 每日额度继续使用现有条件 `UPDATE` 原子消费，不允许先查后写。
- 动态降低额度后，已用次数大于新额度不回退，下一次请求直接拒绝。
- 模型价格 upsert 使用唯一键 `(provider, model)`。
- 反馈回复和 thread 状态更新在同一事务中更新 `updated_at`。
- 临时密码消费必须使用条件更新，保证同一临时密码最多创建一个受限 Session。
- trace 清理使用小批量幂等事务，不持有业务长事务。

## 18. 错误契约

新增错误码应进入所属模块常量或枚举，至少覆盖：

```text
AUTH_BETA_ACCESS_DENIED
BETA_ACCESS_EMAIL_INVALID
BETA_ACCESS_EMAIL_NOT_FOUND
AUTH_PASSWORD_CREDENTIAL_NOT_FOUND
AUTH_TEMPORARY_PASSWORD_EXPIRED
AUTH_TEMPORARY_PASSWORD_CONSUMED
AUTH_PASSWORD_CHANGE_REQUIRED
AI_GLOBALLY_DISABLED
AI_USER_DISABLED
AI_MODEL_PRICE_NOT_FOUND
AI_MODEL_PRICE_INVALID
AI_RUN_NOT_FOUND
AI_RUN_TRACE_EXPIRED
AI_RUN_TRACE_FORBIDDEN
FEEDBACK_THREAD_NOT_FOUND
FEEDBACK_THREAD_FORBIDDEN
FEEDBACK_MESSAGE_INVALID
```

后端统一通过现有 `ApiResponse` 和本地化错误消息返回，前端不得解析错误字符串判断行为。

## 19. 测试策略

### 19.1 后端单元测试

- 邮箱规范化、管理员绕过和开关关闭行为。
- 密码注册、密码登录、OAuth 登录的白名单允许与拒绝。
- 白名单移除触发 Session 吊销；吊销失败不回滚。
- 临时密码生成、过期、单次消费、强制改密和 Session 吊销。
- 全局 AI 开关、用户暂停和额度覆盖的有效策略解析。
- 并发额度消费继续保持原子性。
- Agent step、代码 Review、复述判定和复习卡预生成的调用级 Token 全部入账且不重复。
- 成本公式、缓存 Token、倍率、未定价和高精度舍入。
- run 查询筛选和 30 天 trace 访问边界。
- trace 清理幂等且不删除业务 message。
- 反馈 ownership、管理员回复、关闭后用户回复自动重开、未读状态。
- 审计内容不包含临时密码和完整 trace。

### 19.2 Controller 与安全测试

- 非管理员访问所有 `/api/admin/**` 返回 403。
- 普通用户只能读取自己的反馈。
- 管理员不能通过反馈 API 主动创建 thread。
- 受限改密 Session 不能访问其他业务 API。
- 白名单移除后旧 Session 的下一次 API 请求被拒绝。
- 完整 trace 每次查看产生审计记录。

### 19.3 MyBatis 与迁移测试

- V28-V31 资源存在且版本不冲突。
- 所有新增 mapper statement 注册成功。
- 唯一键、CHECK、条件更新和分页 SQL 行为正确。
- 成本聚合不把未定价模型按零成本混入。

### 19.4 前端测试

- 管理员默认落到 `/admin`。
- 导航按权限显示。
- 白名单批量添加、开关确认和移除刷新。
- AI 全局开关、用户覆盖和价格校验。
- 成本页明确显示“按当前价格估算”和未定价警告。
- trace 过期时只显示 summary。
- 反馈未读、回复和关闭/重开。
- 临时密码只展示一次并强制进入改密页。

### 19.5 集成与 smoke

至少增加以下 Hurl 流程：

```text
管理员开启白名单 -> 非白名单注册失败 -> 添加邮箱 -> 注册成功
移除白名单 -> 已登录用户后续请求失败 -> 重新添加 -> 可重新登录
管理员关闭全局 AI -> 用户 AI 请求被拒绝 -> 重新开启恢复
用户额度达到上限 -> 请求 429 -> 管理员提高该用户额度 -> 请求恢复
用户提交反馈 -> 管理员回复 -> 用户读取并继续回复
管理员重置密码 -> 旧 Session 失效 -> 临时密码登录 -> 强制改密 -> 正常访问
```

## 20. 发布与回滚

### 20.1 发布顺序

1. 先执行数据库迁移和后端兼容代码，默认白名单开关关闭。
2. 发布前端管理员页面。
3. 录入管理员邮箱和测试邮箱。
4. 验证管理员应急入口可用。
5. 最后开启白名单开关。

不得在白名单为空且管理员绕过未验证时直接开启生产开关。

### 20.2 回滚

- 白名单故障：通过数据库或管理员应急入口关闭 `email_allowlist_enabled`。
- AI 动态策略故障：关闭数据库全局 AI 或回退到现有配置默认值。
- 价格配置故障：停用错误价格；金额只是估算，不影响 admission。
- 反馈和概览故障：可单独隐藏前端入口，不影响核心学习流程。
- trace 读取故障：关闭详情入口，保留 run summary 和原始持久化数据。

数据库迁移只新增表和列，不在同一发布中删除旧字段，支持应用版本回滚。

## 21. 验收标准

完成以下条件后，管理员业务能力才达到 5-20 人内测门槛：

- 管理员可在页面中开启白名单、批量添加和移除邮箱。
- 所有注册和登录方式统一执行白名单规则，移除后旧 Session 立即失效。
- 管理员可以实时关闭全局 AI，并调整全局和单用户每日请求额度。
- 管理员可以配置模型三类价格和倍率，查看 Token、估算成本及未定价调用。
- 成本统计覆盖 Agent step 和所有直接 `LlmGateway` 调用，且同一调用不会重复计入。
- 管理员可以筛选失败 run，并在 30 天内查看完整诊断信息。
- 完整 trace 查看、关键配置修改和密码重置均有低敏审计记录。
- 用户可以发起反馈并与管理员双向回复，管理员不能主动创建私信。
- 管理员概览能直接进入异常用户、失败 run 和未回复反馈。
- 管理员可以为密码用户生成一次性临时密码，并完成强制改密闭环。
- 30 天到期后诊断内容不可再由管理员 API 读取，清理任务不破坏用户业务历史。

## 22. 已接受风险

- 密码自助注册不验证邮箱所有权，知道白名单邮箱的人可能抢先注册。
- 当前价格回算会导致历史估算金额随调价变化。
- 硬额度按请求次数控制，不保证单次调用不会产生较高 Token 成本。
- 只有一名管理员，缺少职责分离；通过审计留痕而非角色体系缓解。
- 本阶段没有基础设施主动告警，管理员仍可能先从用户反馈得知服务异常。
