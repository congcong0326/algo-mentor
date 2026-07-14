# 内测管理员业务能力实施计划

> 对应设计：`docs/internal-beta-admin-capabilities-design.md`
> 目标：在 5-20 人封闭内测前，完成准入、AI 止损与成本观测、AI run 排障、反馈信箱、管理员概览和密码恢复闭环。

---

## 1. 实施原则

- 按纵向业务闭环交付，每个任务完成后都能独立测试和回滚。
- 先完成准入与止损，再建设观测和排障页面。
- 不新增 Maven 模块，沿用 `auth`、`identity`、`ai-governance`、`agent-persistence-postgres`、`mentor-api` 和 `frontend`。
- 所有管理员写操作都走现有 CSRF、请求 ID 和 `ROLE_ADMIN` 安全边界。
- Flyway 版本在全仓共享。本文暂用 `V28`-`V31`，开始编码前必须重新扫描版本并顺延冲突。
- 不在本计划中顺手实现 Prometheus 告警、内容管理、邮箱验证、价格版本和多管理员角色。
- 每个任务优先运行最小相关测试；阶段门禁再运行模块和全量测试。

## 2. 阶段划分

| 阶段 | 结果 | 内测门禁 |
| --- | --- | --- |
| 阶段一 | 白名单、审计底座、临时密码 | 能受控添加和移除测试者，账号可恢复 |
| 阶段二 | 动态 AI 开关、额度、价格和成本 | 能看到消耗并立即止损 |
| 阶段三 | run 查询、完整 trace、30 天保留 | 能定位业务层 AI 故障 |
| 阶段四 | 反馈信箱、管理员概览、最终联调 | 能形成用户反馈和管理员处理闭环 |

所有阶段均属于内测 P0。阶段划分用于降低一次性改动风险，不代表阶段三、四可以无限期延后。

## 3. 基线检查

开始实施前执行：

```bash
git status --short
find backend -path '*/src/main/resources/db/migration/*' -type f -printf '%f %p\n' | sort -V
make backend-test
make frontend-test
```

若基线测试失败，记录失败项，不在本计划中修复无关问题。

---

## 阶段一：准入与账号运维

### Task 1：建立数据库迁移和共享契约

**目标：** 先固定表结构、权限字符串、错误码和 API 路径，后续任务只填实现。

**主要文件：**

- Create: `backend/auth/src/main/resources/db/migration/auth/V28__beta_access_and_password_reset.sql`
- Create: `backend/ai-governance/src/main/resources/db/migration/ai/V29__ai_runtime_policy_and_model_price.sql`
- Create: `backend/mentor-api/src/main/resources/db/migration/V30__admin_audit_and_user_feedback.sql`
- Create: `backend/agent-persistence-postgres/src/main/resources/db/migration/agent/V31__agent_diagnostic_retention.sql`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/AuthPermission.java`
- Modify: `frontend/src/types/api.ts`
- Create/Modify: 各模块 API contract constants、错误码 enum
- Modify: 各模块 migration resource tests

**实施步骤：**

- [ ] 再次确认最大 Flyway 版本，必要时整体顺延 `V28`-`V31`。
- [ ] 创建设计文档规定的表、列、唯一键、CHECK 和索引。
- [ ] 为新增管理员页面增加权限枚举，不创建新角色。
- [ ] 为白名单、临时密码、AI 动态策略、价格、run 和反馈增加稳定错误码。
- [ ] 为所有共享 API 路径、JSON 字段和状态值建立常量类或 enum。
- [ ] 增加迁移文件存在性和 mapper XML 注册测试骨架。

**验收：**

- Flyway 版本全仓唯一。
- 迁移只新增表和列，不删除旧字段。
- 后端与前端权限字符串完全一致。

**验证：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl auth,ai-governance,agent-persistence-postgres,mentor-api -am test
```

### Task 2：实现管理员审计底座

**目标：** 在开始敏感功能前提供统一、低敏、可降级的持久化审计。

**主要文件：**

- Create: `backend/common/src/main/java/org/congcong/algomentor/common/admin/audit/AdminOperationAuditRecorder.java`
- Create: `backend/common/src/main/java/org/congcong/algomentor/common/admin/audit/AdminOperationAuditEvent.java`
- Create: `backend/common/src/main/java/org/congcong/algomentor/common/admin/audit/AdminAuditAction.java`
- Create: `backend/common/src/main/java/org/congcong/algomentor/common/admin/audit/AdminAuditTargetType.java`
- Create: `backend/common/src/main/java/org/congcong/algomentor/common/admin/audit/AdminAuditOutcome.java`
- Create: `backend/common/src/main/java/org/congcong/algomentor/common/admin/audit/NoopAdminOperationAuditRecorder.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/audit/PostgresAdminOperationAuditRecorder.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/audit/AdminOperationAuditMapper.java`
- Create: `backend/mentor-api/src/main/resources/mapper/admin/AdminOperationAuditMapper.xml`
- Modify: `backend/mentor-api` MyBatis/config wiring

**实施步骤：**

- [ ] 定义强类型审计事件，metadata 仅允许低敏键值。
- [ ] 使用 `RequestTraceContext` 自动附带 request ID。
- [ ] 实现 MyBatis insert 和 no-op fallback。
- [ ] 审计写入失败只记录 error 和指标，不回滚止损或准入操作。
- [ ] 增加敏感字段拒绝或脱敏测试，确保不能写入密码、Authorization、完整 prompt 和代码。

**验收：**

- 底层模块可以只依赖 `common` 中的 recorder 端口。
- 数据库不可用或测试上下文没有 recorder 时仍可启动。
- 审计表没有临时密码和完整 trace 内容。

### Task 3：实现白名单后端闭环

**目标：** 管理员可在线维护白名单，所有认证方式和存量 Session 统一执行策略。

**主要文件：**

- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/model/*`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/repository/*`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/service/BetaAccessPolicy.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/betaaccess/service/BetaAccessAdminService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/controller/admin/BetaAccessController.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/controller/admin/model/*`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/PasswordUserService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/PasswordUserDetailsService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/service/OAuth2LoginUserService.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/ActiveIdentityUserFilter.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/autoconfigure/AuthApiAutoConfiguration.java`
- Modify: `backend/auth/src/main/resources/mapper/auth/AuthUserMapper.xml`

**实施步骤：**

- [ ] 实现邮箱 `trim + lowercase(Locale.ROOT)` 规范化工具并集中复用。
- [ ] 实现开关读取、单邮箱判定、分页查询和批量幂等添加。
- [ ] 管理员绕过只能来自受信角色或配置中的管理员邮箱。
- [ ] 在密码注册前检查白名单。
- [ ] 在密码登录加载用户时检查白名单。
- [ ] 在 OAuth 创建或同步账号前检查 provider 邮箱。
- [ ] 在 `ActiveIdentityUserFilter` 中校验已登录 API 请求。
- [ ] 移除邮箱后查找关联用户并吊销全部 Session；失败不回滚删除。
- [ ] 所有开关、添加和移除操作写入管理员审计。
- [ ] 增加 403 本地化错误响应和 OAuth failure 映射。

**关键测试：**

- 开关关闭时所有现有认证行为不变。
- 管理员邮箱不在白名单时仍可登录。
- 非白名单密码注册、密码登录和 OAuth 登录均被拒绝。
- 重复批量添加幂等。
- 移除后旧 Session 下一次 API 请求失败。
- Session 吊销异常时白名单删除仍然提交。

**验证：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl auth -am test
```

### Task 4：实现白名单管理员页面

**目标：** 管理员不通过数据库或部署操作即可完成内测成员维护。

**主要文件：**

- Create: `frontend/src/admin/BetaAccessPage.tsx`
- Create: `frontend/src/admin/BetaAccessPage.test.tsx`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/services/api.test.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/app/navigation.test.ts`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 增加 `/admin/beta-access` 路由和 `beta-access:manage` 权限门禁。
- [ ] 页面提供白名单开关、关键词搜索、批量邮箱输入、列表和移除操作。
- [ ] 列表展示已注册状态、关联用户状态、添加人和添加时间。
- [ ] 开启空白名单时展示强提醒，但保留管理员应急绕过。
- [ ] 移除已注册邮箱时明确提示会立即下线但不会删除数据。
- [ ] 对新增、重复和无效邮箱显示结构化结果，不依赖解析错误字符串。
- [ ] 中英文文案和移动端布局同步完成。

**验收：**

- 管理员可以在一个页面完成开关、添加、搜索和移除。
- 非管理员不能看到导航，也不能直接访问路由。

### Task 5：实现一次性临时密码和强制改密

**目标：** 为密码用户提供可审计且不会泄露旧密码的恢复路径。

**主要文件：**

- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/PasswordCredential.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/AuthUserRepository.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/repository/mybatis/*`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/passwordreset/PasswordResetService.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/passwordreset/TemporaryPasswordGenerator.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/security/PasswordChangeRequiredFilter.java`
- Create: `backend/auth/src/main/java/org/congcong/algomentor/auth/controller/admin/AdminPasswordResetController.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/controller/PasswordAuthController.java`
- Modify: `backend/auth/src/main/java/org/congcong/algomentor/auth/model/CurrentUserResponse.java`
- Modify: authenticated principal/details classes
- Create: `frontend/src/app/PasswordChangeRequiredPage.tsx`
- Modify: `frontend/src/admin/UserManagementPage.tsx`

**实施步骤：**

- [ ] 使用 `SecureRandom` 生成临时密码，默认有效期 24 小时。
- [ ] 管理员重置时更新 hash、重置状态并吊销全部 Session。
- [ ] 临时密码只在本次成功响应中返回，不写日志、审计 metadata 或数据库明文。
- [ ] 临时密码认证使用条件更新实现单次消费。
- [ ] 受限 Session 只能访问当前用户、完成改密和退出接口。
- [ ] 完成改密后清空重置状态并恢复正常 Session。
- [ ] OAuth-only 用户返回明确 409，管理员不能重置自己。
- [ ] 用户管理页使用确认弹窗，并只展示一次临时密码。

**关键测试：**

- 旧密码和旧 Session 在重置后失效。
- 临时密码过期和二次使用失败。
- 丢失已消费临时密码的 Session 后必须再次重置。
- 受限 Session 不能调用学习计划或 AI API。
- 新密码完成后可以正常登录。

**阶段一门禁：**

```bash
make backend-test
make frontend-test
```

手工验证管理员应急账号不依赖白名单，随后再允许开启白名单开关。

---

## 阶段二：AI 止损与成本观测

> 详细执行计划见 `docs/internal-beta-ai-governance-stage-2-implementation-plan.md`。该文档结合当前已落地的 V29 基座、实际 LLM 调用链和已确认的前端信息架构，细化并覆盖本节 Task 6-8 的执行顺序与验收门禁。

### Task 6：实现数据库动态 AI 策略

**目标：** 全局 AI 开关、默认每日额度和用户覆盖无需重启即可生效。

**主要文件：**

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/runtime/*`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiRuntimeSettingsMapper.java`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiRuntimeSettingsMapper.xml`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionService.java`
- Modify: `backend/ai-governance` auto-configuration
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminAiSettingsController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminUserAiPolicyController.java`

**实施步骤：**

- [ ] 实现全局设置和用户覆盖 repository/service。
- [ ] 按设计公式解析有效开关和有效每日额度。
- [ ] admission 每次直接查询数据库，不增加缓存。
- [ ] 全局关闭优先于用户覆盖；用户 `TRUE` 不能绕过全局关闭。
- [ ] 使用有效额度继续调用现有原子 `tryConsumeRequest`。
- [ ] 调低额度后不修改历史 request count，下一次调用按新上限判定。
- [ ] 不提供清零用量 API。
- [ ] 全局和用户修改均写管理员审计。

**关键测试：**

- 全局开关立即影响下一次 admission。
- 用户暂停只影响该用户。
- 用户覆盖为空时继承全局额度。
- 当前用量超过调低后的额度时下一次请求被拒绝。
- 并发额度消费仍然只有允许数量成功。

### Task 7：实现模型价格和成本查询

**目标：** 建立调用级 Token 台账，并按当前价格计算用户、模型、场景和日期维度的估算成本。

**主要文件：**

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/pricing/*`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/accounting/*`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/adminquery/*`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiModelPriceMapper.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiLlmCallUsageMapper.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiAdminUsageMapper.java`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiModelPriceMapper.xml`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiLlmCallUsageMapper.xml`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiAdminUsageMapper.xml`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserver.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/review/ReviewCardService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/review/RecallJudgeService.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminAiPriceController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminAiUsageController.java`

**实施步骤：**

- [ ] 用 `BigDecimal` 实现成本计算器和固定舍入规则。
- [ ] 建立 `ai_llm_call_usage` 调用级台账，并为已有非零 run usage 生成一次性 legacy 聚合记录。
- [ ] Agent loop 每个 LLM step 单独记录实际 provider、model 和 usage。
- [ ] 提供统一 direct completion accounting 包装，禁止业务服务裸调用后不记账。
- [ ] 代码 Review 子调用关联父 run，不重复消费用户入口额度。
- [ ] 复述判定走共享 admission；复习卡预生成保留专用后台配额，但受全局和用户 AI 开关控制。
- [ ] 对 input、cached input、output 和 multiplier 分别校验非负/正数。
- [ ] 使用 `max(input-cached, 0)` 防御 provider usage 异常。
- [ ] `reasoningTokens` 不重复计费。
- [ ] 未定价调用进入独立计数，不按零成本混入总额。
- [ ] 价格只 upsert/停用，不物理删除。
- [ ] 用量查询支持日期、用户、模型和 purpose，最大区间 90 天。
- [ ] 查询以 `ai_llm_call_usage` 为成本来源，`ai_run_admissions` 只用于入口和 run 聚合。
- [ ] 价格变更写管理员审计。

**关键测试：**

- 纯输入、缓存输入、输出、倍率和混合用量公式。
- cached tokens 大于 input tokens 时不会产生负成本。
- 调价后历史查询金额变化。
- 停用或缺少价格时显示未定价。
- 大 Token 数仍保持 Decimal 精度。
- Agent step、代码 Review、复述判定和复习卡预生成全部入账。
- 父 run 聚合与子调用不会重复计费。

### Task 8：实现 AI 治理管理员页面和用户级控制

**目标：** 管理员可以在页面中观测成本并执行全局或单用户止损。

**主要文件：**

- Create: `frontend/src/admin/AiGovernancePage.tsx`
- Create: `frontend/src/admin/AiGovernancePage.test.tsx`
- Modify: `frontend/src/admin/UserManagementPage.tsx`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 增加 `/admin/ai` 和 `ai-governance:manage` 权限门禁。
- [ ] 页面顶部展示全局 AI 开关和默认每日请求额度。
- [ ] 模型价格表支持创建、编辑和停用，明确三个价格单位。
- [ ] 成本区域支持日期、用户、模型和场景筛选。
- [ ] 显示输入、缓存、输出、总 Token、估算金额和未定价警告。
- [ ] 明确区分“用户 AI 入口请求数”和“实际模型调用数”。
- [ ] 所有金额区域显示“按当前价格估算”。
- [ ] 用户详情增加 AI 继承状态、暂停开关和每日额度覆盖。
- [ ] 不提供清零用量和美元预算输入。

**阶段二门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance,mentor-api -am test
make frontend-test
```

手工验证：关闭全局 AI 后下一次用户调用被拒绝；提高单用户额度后无需重启即可恢复。

---

## 阶段三：AI run 排障与 30 天诊断保留

### Task 9：实现 AI run 管理员列表

**目标：** 管理员能够从用户、状态或错误码定位具体 run。

**主要文件：**

- Extend: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/adminquery/*`
- Extend: `backend/ai-governance/src/main/resources/mapper/ai/AiRunAdmissionMapper.xml`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminAiRunController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/model/AiRun*Response.java`

**实施步骤：**

- [ ] 增加分页筛选 DTO，限制页大小和时间区间。
- [ ] 支持用户、purpose、source、provider、model、status 和错误码筛选。
- [ ] 列表关联当前价格并计算估算成本。
- [ ] 根据 run 时间和 30 天策略返回 `traceAvailable`、`traceExpiresAt`。
- [ ] RUNNING 项展示预计锁过期时间，但不提供管理动作。
- [ ] 列表不得返回完整 message、prompt、代码和工具参数。

### Task 10：实现完整 trace 读取与保留清理

**目标：** 30 天内可安全排障，过期后立即禁止读取并清理诊断副本。

**主要文件：**

- Create: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/admintrace/*`
- Create: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/retention/*`
- Create: `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentAdminTraceMapper.xml`
- Modify: Agent persistence auto-configuration
- Extend: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessPolicy.java`
- Extend: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminAiRunController.java`
- Add: scheduled cleanup wiring in `mentor-api`

**实施步骤：**

- [ ] 通过 `run_uuid` 将 `ai_run_admissions.run_id` 与 `agent_run` 关联。
- [ ] Agent persistence 暴露只读聚合，返回 run、step、snapshot、tool call 和 blob preview。
- [ ] 完整内容读取先校验管理员权限，再校验 30 天期限。
- [ ] 每次成功或拒绝读取都写 `AI_RUN_TRACE_VIEW` 审计。
- [ ] 过期 API 只返回 summary 和 `AI_RUN_TRACE_EXPIRED` 状态。
- [ ] 清理任务小批量处理过期 run，脱敏 snapshot、工具参数和结果并删除 blob。
- [ ] 清理任务标记 `diagnostic_redacted_at`，可重复执行。
- [ ] 明确排除 `agent_message.content` 等用户业务历史，不因诊断保留策略删除。
- [ ] 增加清理成功、失败和处理数量指标，标签保持低基数。

**关键测试：**

- 非管理员无法读取。
- 30 天边界前可读，边界后不可读。
- 清理任务尚未运行时 API 仍拒绝过期内容。
- 清理两次结果一致。
- 用户业务消息和学习记录不被删除。
- trace 内容继续执行凭据脱敏。

### Task 11：实现 AI run 管理员页面

**目标：** 用稳定、可扫描的页面完成失败定位和完整 trace 阅读。

**主要文件：**

- Create: `frontend/src/admin/AiRunPage.tsx`
- Create: `frontend/src/admin/AiRunDetailPage.tsx`
- Create: corresponding tests
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 增加 `/admin/ai/runs` 和详情路由。
- [ ] 列表提供稳定筛选栏、分页、状态和错误码展示。
- [ ] 成本列区分已定价和未定价。
- [ ] 详情用 tabs 展示概览、消息、步骤、工具和上下文。
- [ ] 大 JSON 默认折叠，支持复制但不支持批量导出。
- [ ] 过期 trace 只显示 summary 和清晰的过期状态。
- [ ] 用户、反馈和 run 之间提供返回和跳转路径。

**阶段三门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-persistence-postgres,ai-governance,mentor-api -am test
make frontend-test
```

---

## 阶段四：反馈与管理员概览

### Task 12：实现用户反馈信箱闭环

**目标：** 用户可以提交故障、建议和意见，管理员可以回复和关闭，双方有未读状态。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/model/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/service/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/feedback/repository/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/FeedbackController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminFeedbackController.java`
- Create: `backend/mentor-api/src/main/resources/mapper/feedback/FeedbackMapper.xml`
- Create: `frontend/src/feedback/UserFeedbackPage.tsx`
- Create: `frontend/src/admin/FeedbackManagementPage.tsx`
- Create: corresponding backend/frontend tests

**实施步骤：**

- [ ] 实现 thread 和 message MyBatis repository。
- [ ] 普通用户所有查询和回复都校验 thread ownership。
- [ ] 管理员 API 不提供创建 thread 的方法。
- [ ] 用户对 CLOSED thread 回复时自动重开。
- [ ] GET 不隐式修改已读状态，使用显式 read API。
- [ ] 创建反馈时验证可选 `sourceRunId` 属于当前用户。
- [ ] 前端自动附带当前路径和已知 request/run ID，不增加必填项。
- [ ] 用户和管理员页面显示未读徽标、时间线、关闭和重开。
- [ ] 不实现附件、删除消息、优先级、负责人和外部通知。

**关键测试：**

- 用户不能读取或回复他人 thread。
- 管理员能回复但不能主动创建。
- 未读状态按发送方正确计算。
- 关闭后用户回复会重开。
- 非法 run 关联不能越权。

### Task 13：实现管理员概览和跨页面联动

**目标：** 管理员登录后立即看到需要处理的事项，并能跳转到对应详情。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/admin/AdminOverviewController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/admin/overview/AdminOverviewService.java`
- Create: overview DTO and tests
- Create: `frontend/src/admin/AdminOverviewPage.tsx`
- Create: `frontend/src/admin/AdminOverviewPage.test.tsx`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

**实施步骤：**

- [ ] 聚合白名单、用户、AI、反馈和最近失败 run 摘要。
- [ ] 分开展示用户入口请求数和实际 provider 调用数。
- [ ] “接近额度”按有效额度的 80% 计算。
- [ ] 同时展示已定价成本和未定价调用/Token。
- [ ] 默认管理员登录落点从 `/admin/users` 改为 `/admin`。
- [ ] 所有异常项可跳转到用户、AI run、反馈或治理设置。
- [ ] 保持页面信息密度，不做嵌套卡片和营销式图表。
- [ ] 用户详情增加白名单、AI 策略、用量、失败 run 和反馈快捷入口。

**验收：**

- 管理员无需逐页检查即可知道今日异常和待回复事项。
- 概览查询失败时各区块独立降级，不导致整个管理员后台不可用。

### Task 14：补齐 smoke、文档和最终安全复核

**目标：** 以真实认证和数据库流程验证所有 P0 闭环。

**主要文件：**

- Create/Modify: `tests/smoke/suites/admin/*.hurl`
- Modify: `tests/smoke/run.py` 或 suite 注册
- Modify: `docs/code-index.md`
- Modify: `.env.example`（仅保留必要 fallback，不放密钥）
- Modify: relevant API/error documentation

**Smoke 场景：**

- [ ] 管理员开启白名单，非白名单密码注册失败。
- [ ] 管理员添加邮箱后用户注册成功。
- [ ] 管理员移除邮箱后用户旧 Session 被拒绝。
- [ ] 重新添加邮箱后原账号可登录且数据保留。
- [ ] 全局 AI 关闭后用户请求被拒绝，开启后恢复。
- [ ] 用户达到额度后返回 429，管理员提高覆盖额度后恢复。
- [ ] 模型价格配置后历史 Token 得到估算成本。
- [ ] Agent、多步工具调用和直接 LLM 调用都进入调用级 Token 台账。
- [ ] 未定价模型显示警告，不按零成本混入。
- [ ] 管理员能查看 30 天内 trace，过期 trace 被拒绝。
- [ ] 用户发起反馈，管理员回复，用户读取并继续回复。
- [ ] 管理员重置密码，旧 Session 失效，临时密码只能消费一次，完成改密后正常访问。

**安全复核：**

- [ ] `rg` 检查日志和审计中不存在临时密码、Authorization、Cookie、API key 和完整 trace。
- [ ] 所有新增 `/api/admin/**` 非管理员测试返回 403。
- [ ] 所有写接口有 CSRF 测试。
- [ ] 所有用户资源有 ownership 测试。
- [ ] 前后端 API 字段和权限字符串一致。
- [ ] trace 清理不会删除业务消息。

**最终验证：**

```bash
make backend-test
make frontend-test
make build
```

数据库和外部 AI 可用时再执行：

```bash
python tests/smoke/run.py
```

## 4. 发布门禁

正式邀请测试者前必须满足：

- [ ] 管理员应急账号在白名单为空时仍能登录。
- [ ] 白名单默认关闭，录入测试邮箱后再人工开启。
- [ ] 全局 AI 开关和默认额度已有明确初始值。
- [ ] 所有实际使用模型都已配置价格，或管理员明确接受未定价警告。
- [ ] 用户级 AI 暂停和额度覆盖已通过真实数据库验证。
- [ ] 完整 trace 查看已写审计，30 天过期限制已生效。
- [ ] 临时密码不会出现在日志和审计中。
- [ ] 反馈信箱双方未读和回复流程已验证。
- [ ] 管理员概览可以跳转到失败 run、用户和反馈。
- [ ] `make backend-test`、`make frontend-test`、`make build` 全部通过。

## 5. 建议提交拆分

建议按以下粒度提交，避免一个提交跨越全部后台能力：

```text
feat: add beta access allowlist management
feat: add admin temporary password reset
feat: add dynamic AI usage controls
feat: add model pricing and cost estimates
feat: add admin AI run diagnostics
feat: enforce AI diagnostic retention
feat: add user feedback inbox
feat: add admin beta overview
test: add internal beta admin smoke coverage
docs: document internal beta admin operations
```

每个提交应包含对应最小测试，不在最后一个提交集中补全部测试。
