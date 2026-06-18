# 代码索引

## 根目录

- `Makefile`：统一构建、测试、本地运行和前端静态资源同步入口。
- `pom.xml`：仓库级 Maven 聚合入口，引入 `backend` 多模块工程。
- `.env.example`：本地开发环境变量样例，不包含真实密钥。
- `deploy/docker/docker-compose.yml`：本地 PostgreSQL 服务。
- `docs/agent-loop-lifecycle-design.md`：Agent loop 生命周期扩展设计，说明 observer、interceptor、lifecycle 与 SSE 边界。
- `docs/agent-conversation-context-recall-design.md`：Agent 多轮上下文召回与压缩研发设计，说明会话存储、运行轨迹、压缩策略和上下文快照。
- `docs/agent-run-tool-result-compaction-design.md`：Agent run 内工具结果压缩设计，说明大结果预览、blob 引用、范围读取工具和 run-local 上下文预算。
- `docs/agent-runtime-refactoring-implementation-plan.md`：Agent 运行态模块拆分分阶段实施计划，说明模块边界、迁移步骤、验收标准和风险点。
- `docs/problem-agent-tools-design.md`：题目 Agent 工具体系设计，说明过滤项发现、查题、读取题面的用途、边界、返回内容和后续演进。

## 后端

- `backend/pom.xml`：Maven 多模块根，统一 Java 17、Spring Boot 与 `openai-java` 版本。
- `backend/common`：跨模块公共模型、DTO 和工具。
- `backend/domain`：业务领域模型，例如算法学习主题、题目、学习计划、会话等。
- `backend/llm-core`：项目内 LLM 抽象契约，按职责拆分为 `gateway`、`provider`、`model`、`request`、`response`、`stream`、`tool`、`exception` 子包。
- `backend/llm-openai`：OpenAI provider 适配模块，隔离 `openai-java` SDK、OpenAI 配置、provider 能力描述和后续请求/响应映射。
- `backend/agent-core`：Agent 核心编排模型，面向 `LlmGateway` 组织模型调用和后续工具执行流程；`runtime` 子包提供通用会话模型、上下文组装策略和 repository 端口。
- `backend/agent-persistence-postgres`：Agent 运行态 PostgreSQL/MyBatis 持久化模块，包含 MyBatis mapper interface/XML、JSONB type handler、repository、持久化 observer、trace snapshot observer 和 agent runtime Flyway migration。
- `backend/mentor-application`：算法学习业务应用层，用 use case 组织 Agent 调用和领域对象；conversation 包只保留 mentor 场景命令、运行结果和业务编排服务。
- `backend/mentor-api`：Spring MVC API 应用，负责 controller、SSE adapter、配置属性和 bean wiring，不直接拥有 agent runtime SQL。
- `backend/mentor-api/src/main/resources/application.yml`：默认应用配置，默认不强制连接数据库。
- `backend/mentor-api/src/main/resources/application-local.yml`：本地 PostgreSQL 与 Flyway 配置。
- `backend/mentor-api/src/main/resources/db/migration`：mentor API 自有 Flyway 迁移脚本目录。
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/config`：PostgreSQL persistence auto-configuration，装配 MyBatis `SqlSessionFactory`、mapper 和持久化 bean。
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper`：agent runtime MyBatis mapper interface 和 mapper 参数/结果模型。
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/json`：PostgreSQL JSONB 与 agent message role 的 MyBatis type handler。
- `backend/agent-persistence-postgres/src/main/resources/mapper/agent`：agent runtime MyBatis XML mapper 目录，SQL 只保存在 persistence 模块。
- `backend/agent-persistence-postgres/src/main/resources/db/migration/agent`：agent runtime Flyway 迁移脚本目录，随 `classpath:db/migration` 被 API 应用递归扫描；这些目录共享同一个 Flyway 版本空间，新增 `V` 版本号需要跨模块唯一；`V3__agent_runtime_sequence_counters.sql` 使用数据库计数器/触发器分配 turn、message sequence 和 run attempt。

## 前端

- `frontend/src/App.tsx`：学习工作台首屏。
- `frontend/src/services/api.ts`：前端 API 调用封装。
- `frontend/src/types/api.ts`：前后端共享契约的 TypeScript 表示。
- `frontend/package.json`：React 19、TypeScript 6、Vite 8、Vitest 4 依赖与脚本。
- `frontend/vite.config.ts`：Vite、React 插件和 Vitest 配置。

## 常用命令

- `make backend-test`：运行后端单元测试。
- `make frontend-install`：安装前端依赖。
- `make frontend-test`：运行前端测试。
- `make build`：后端打包并构建前端。
- `make package`：构建前端、同步到后端静态目录并打包后端。
