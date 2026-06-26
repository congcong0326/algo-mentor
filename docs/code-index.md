# 代码索引

## 根目录

- `Makefile`：统一构建、测试、本地运行和前端静态资源同步入口。
- `pom.xml`：仓库级 Maven 聚合入口，引入 `backend` 多模块工程。
- `.env.example`：本地开发环境变量样例，不包含真实密钥。
- `deploy/docker/docker-compose.yml`：本地 PostgreSQL 服务。
- `docs/agent-loop-lifecycle-design.md`：Agent loop 生命周期扩展设计，说明 observer、interceptor、lifecycle 与 SSE 边界。
- `docs/agent-conversation-context-recall-design.md`：Agent 多轮上下文召回与压缩研发设计，说明会话存储、运行轨迹、压缩策略和上下文快照。
- `docs/agent-structured-output-design.md`：Agent 结构化输出与最终结果捕获设计，说明执行配置、provider-native structured output、AgentOutput 和最终输出持久化边界。
- `docs/agent-run-tool-result-compaction-design.md`：Agent run 内工具结果压缩设计，说明大结果预览、blob 引用、范围读取工具和 run-local 上下文预算。
- `docs/agent-tool-permission-phase-one-design.md`：Agent Tool 人在回路权限阶段一设计，说明工具执行前门禁、权限 hook/coordinator、决策 API、SSE 和 Review 工具确认链路。
- `docs/agent-tool-permission-phase-one-tasks/README.md`：Agent Tool 权限阶段一任务拆解与落地确认，记录 task 8-20 的完成备注、阶段一限制和验证命令。
- `docs/agent-runtime-refactoring-implementation-plan.md`：Agent 运行态模块拆分分阶段实施计划，说明模块边界、迁移步骤、验收标准和风险点。
- `docs/practice-chat-workbench-design.md`：题目聊天工作台研发设计，说明方案详情、题目聊天页、固定工具栏、题目状态、训练会话和 AI 聊天接口草案。
- `docs/practice-chat-agent-design.md`：题目聊天 Agent 研发设计，说明 prompt 组装、题面上下文注入、SSE 聊天气泡展示、后端会话/API 和测试计划。
- `docs/practice-chat-system-prompt-assembly-design.md`：题目聊天系统提示词拼装设计，说明结构化片段、分层 prompt、动态 profile、预算裁剪、metadata 追踪和测试策略。
- `docs/practice-code-review-product-design.md`：练习代码 Review 产品设计，说明自动识别完整代码提交、多版本 Review、评分规则、完成门槛和 Review 抽屉体验。
- `docs/practice-code-review-technical-design.md`：练习代码 Review 技术设计，说明基于 practice turn orchestrator 与服务端 capability 的结构化 Review、数据模型、完成 gate、API 和前端闭环。
- `docs/problem-agent-tools-design.md`：题目 Agent 工具体系设计，说明过滤项发现、查题、读取题面的用途、边界、返回内容和后续演进。

## 后端

- `backend/pom.xml`：Maven 多模块根，统一 Java 17、Spring Boot 与 `openai-java` 版本。
- `backend/common`：跨模块公共模型、DTO 和工具。
- `backend/domain`：业务领域模型，例如算法学习主题、题目、学习计划、会话等。
- `backend/llm-core`：项目内 LLM 抽象契约，按职责拆分为 `gateway`、`provider`、`model`、`request`、`response`、`stream`、`tool`、`exception` 子包。
- `backend/llm-openai`：OpenAI provider 适配模块，隔离 `openai-java` SDK、OpenAI 配置、provider 能力描述和后续请求/响应映射。
- `backend/agent-core`：Agent 核心编排模型，面向 `LlmGateway` 组织模型调用和后续工具执行流程；`runtime` 子包提供通用会话模型、上下文组装策略和 repository 端口。
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission`：Agent Tool 执行前权限核心包，包含 `AgentToolPermissionGuard`、hook chain、内存 coordinator、permission request/decision 模型、synthetic result factory 和 no-op metrics。
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopRunner.java`：Agent 主循环，在真实工具执行前调用 lifecycle 权限门禁，并支持 synthetic permission result 回填模型上下文。
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopLifecycle.java`：Agent lifecycle 门面，发布 `tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout` 事件并调用权限 guard。
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentRuntimeMetadataKeys.java`：Agent runtime 受信 metadata key，包含权限 owner 校验使用的 `USER_ID = "userId"`。
- `backend/agent-persistence-postgres`：Agent 运行态 PostgreSQL/MyBatis 持久化模块，包含 MyBatis mapper interface/XML、JSONB type handler、repository、持久化 observer、trace snapshot observer 和 agent runtime Flyway migration。
- `backend/mentor-application`：算法学习业务应用层，用 use case 组织 Agent 调用和领域对象；conversation 包只保留 mentor 场景命令、运行结果和业务编排服务。
- `backend/mentor-api`：Spring MVC API 应用，负责 controller、SSE adapter、配置属性和 bean wiring，不直接拥有 agent runtime SQL。
- `backend/mentor-api/src/main/resources/application.yml`：默认应用配置，默认不强制连接数据库。
- `backend/mentor-api/src/main/resources/application-local.yml`：本地 PostgreSQL 与 Flyway 配置。
- `backend/mentor-api/src/main/resources/db/migration`：mentor API 自有 Flyway 迁移脚本目录。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentToolPermissionController.java`：Agent Tool 权限决策 API，提供 `POST /api/agent/tool-permissions/{permissionRequestId}/decision`，通过当前认证用户提交允许或拒绝。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentToolPermissionExceptionHandler.java`：权限决策异常到 HTTP 状态的映射，覆盖未登录、越权、不存在、已决策、过期和非法请求。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/agent/model`：权限决策 API 的 request/response DTO，只接收 `decision` 和 `reason`，不接收前端声明的 userId。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/AgentToolPermissionProperties.java`：权限配置属性，绑定 `algo-mentor.agent.tool-permission.enabled/timeout/cleanup-interval`。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`：装配权限 hook chain、coordinator、guard 和 API 层 Micrometer adapter；`enabled=false` 时清空业务 hooks 并使用默认 allow coordinator。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/LlmStreamSseMapper.java`：SSE mapper，负责把核心权限事件映射为前端可消费的 `tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout`。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`：Agent conversation 自动配置，注册 practice code review tool 与权限 hook。
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/config`：PostgreSQL persistence auto-configuration，装配 MyBatis `SqlSessionFactory`、mapper 和持久化 bean。
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper`：agent runtime MyBatis mapper interface 和 mapper 参数/结果模型。
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/json`：PostgreSQL JSONB 与 agent message role 的 MyBatis type handler。
- `backend/agent-persistence-postgres/src/main/resources/mapper/agent`：agent runtime MyBatis XML mapper 目录，SQL 只保存在 persistence 模块。
- `backend/agent-persistence-postgres/src/main/resources/db/migration/agent`：agent runtime Flyway 迁移脚本目录，随 `classpath:db/migration` 被 API 应用递归扫描；这些目录共享同一个 Flyway 版本空间，新增 `V` 版本号需要跨模块唯一；`V3__agent_runtime_sequence_counters.sql` 使用数据库计数器/触发器分配 turn、message sequence 和 run attempt。
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice`：题目训练会话应用层，包含 `PracticeSessionService`、`PracticeMessageStreamService`、prompt assembly 片段 provider、题面 catalog 端口和训练进度/消息领域模型。
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentTool.java`：`submit_practice_code_review` Agent 工具，从受信 metadata、practice session repository 和 run message lookup 读取上下文，不信任模型 arguments 中的用户/session/code。
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPermissionHook.java`：Review 工具业务权限 hook，命中 `ASK`，构造低敏 preview 并脱敏 authorization、cookie、API key、JWT/bearer/token 类内容。
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolNames.java`：Review Agent 工具名、参数名、preview 字段和 tool result 字段常量。
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewToolResultMapper.java`：Review 工具结果映射，输出 `practice_code_review_submitted` 摘要给 Agent 主模型。
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`：题目聊天 prompt 片段，包含 Review 工具调用边界和拒绝/超时后的回复约束。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionController.java`：题目训练会话 API，提供创建/读取 session、更新题目进度和专用 SSE 聊天入口。
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeSessionRepository.java`：practice session 的 PostgreSQL/MyBatis repository 实现，负责会话、进度和 agent message 映射。
- `backend/mentor-api/src/main/resources/mapper/practice/PracticeSessionMapper.xml`：practice session SQL mapper，包含会话 upsert、进度更新和消息读取。
- `backend/mentor-api/src/main/resources/db/migration/V12__practice_session_schema.sql`：题目训练会话和学习计划题目进度表迁移。

## 前端

- `frontend/src/App.tsx`：学习工作台首屏。
- `frontend/src/services/api.ts`：前端 API 调用封装，包含 Agent Tool 权限决策 API `decideAgentToolPermission(...)`。
- `frontend/src/types/api.ts`：前后端共享契约的 TypeScript 表示，包含权限 SSE 事件、决策请求/响应和 Review tool result 类型。
- `frontend/src/learning-plans/PracticeChatWorkbench.tsx`：题目训练聊天工作台，使用 practice session 专用 API 渲染题面 seed、流式 AI 回复、Review 入口、LeetCode 外链和题目完成状态；监听权限 SSE 并展示轻量原生确认弹窗。
- `frontend/src/i18n/locales.ts`：前端文案资源，包含权限弹窗、拒绝、超时“本次未执行。”和英文 “This action was not run.” 文案。
- `frontend/package.json`：React 19、TypeScript 6、Vite 8、Vitest 4 依赖与脚本。
- `frontend/vite.config.ts`：Vite、React 插件和 Vitest 配置。

## 常用命令

- `make backend-test`：运行后端单元测试。
- `make frontend-install`：安装前端依赖。
- `make frontend-test`：运行前端测试。
- `make build`：后端打包并构建前端。
- `make package`：构建前端、同步到后端静态目录并打包后端。
