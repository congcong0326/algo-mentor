# Agent 运行态模块拆分分阶段实施计划

## 背景

当前 agent 会话、run、turn/message、context snapshot、trace、上下文组装与压缩等能力已经开始在 `mentor-application` 和 `mentor-api` 中成形。其中：

- `mentor-application` 包含 `ConversationRepository`、`ContextAssembler`、`AgentConversationService` 等运行态抽象。
- `mentor-api` 包含 JDBC repository、持久化 observer、trace observer 和 Flyway migration。

这些能力本质上属于通用 agent 运行态，而不是算法学习业务或 Web/API 层。继续放在 `mentor-api` 会导致 API 模块承担 SQL、运行轨迹、上下文快照和持久化事务语义；继续放在 `mentor-application` 会让算法学习应用层变成通用 agent framework 的归属地。

本计划采用渐进迁移：先稳定边界和端口，再迁移实现，最后再引入 MyBatis、上下文压缩和长期记忆，避免一次性过度抽象。

## 目标边界

### agent-core

`agent-core` 保持业务无关，负责通用 agent 执行模型、生命周期、端口和策略抽象。

应包含：

- Agent loop、tool、observer、interceptor、lifecycle。
- `AgentRequest`、`AgentRunResult`、`AgentStepResult` 等执行契约。
- 通用运行态模型：task、turn、message、run、run step、context snapshot、artifact。
- 通用 repository/store 端口：run、conversation、context snapshot、artifact、memory。
- 上下文组装与压缩的通用接口：context assembler、compression policy、token budget、context source ref。
- 与 LLM 请求快照相关的通用观测点和脱敏接口。

不应包含：

- Spring、MyBatis、PostgreSQL、Flyway。
- `JdbcOperations`、mapper XML、JSONB type handler。
- SSE、HTTP controller、Web DTO。
- 算法学习 prompt、题目、学习计划、知识点、错题复盘等业务语义。
- OpenAI SDK 或 provider 具体实现。

### agent-persistence-postgres

新增 `agent-persistence-postgres` 模块，负责 PostgreSQL/MyBatis 持久化实现。

应包含：

- agent-core repository/store 端口的 PostgreSQL 实现。
- MyBatis mapper interface。
- MyBatis mapper XML。
- PostgreSQL JSONB type handler。
- Flyway migration。
- `PersistentAgentRunObserver`、`PersistentAgentTraceObserver` 等持久化 observer。
- 必要的 Spring/MyBatis 装配类，但不包含 Web controller。

### mentor-application

`mentor-application` 只保留算法学习应用逻辑。

应包含：

- 算法学习 use case。
- mentor 场景 prompt 组装。
- 题目、学习计划、知识点、练习状态等业务上下文到 `AgentRequest` 的映射。
- mentor 场景下选择哪些通用 agent 能力和策略。

不应直接包含：

- SQL、JDBC、MyBatis mapper。
- PostgreSQL/Flyway 细节。
- 通用 agent run/trace/schema 的实现逻辑。

### mentor-api

`mentor-api` 只负责 Web/API/SSE 和 Spring Bean 装配。

应包含：

- Controller。
- SSE adapter。
- 配置 properties。
- Spring Bean wiring。
- 引入 `agent-persistence-postgres` 的实现 bean。

不应直接包含：

- agent 运行态 SQL。
- MyBatis XML。
- Flyway schema 所有权。
- 持久化 observer 的具体 SQL 实现。

## 推荐最终模块依赖

```text
common
domain

llm-core
llm-openai -> llm-core

agent-core -> llm-core

agent-persistence-postgres
  -> agent-core
  -> llm-core
  -> mybatis
  -> flyway
  -> postgresql

mentor-application
  -> domain
  -> agent-core
  -> llm-core

mentor-api
  -> common
  -> mentor-application
  -> agent-core
  -> agent-persistence-postgres
  -> llm-openai
  -> spring web/sse/actuator
```

禁止形成以下依赖：

```text
agent-core -> mentor-application
agent-core -> mentor-api
mentor-application -> agent-persistence-postgres
agent-persistence-postgres -> mentor-application
```

## 推荐目录结构

```text
backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/
  runtime/
    model/
      AgentTask.java
      AgentTurn.java
      AgentMessage.java
      AgentRun.java
      AgentRunStep.java
      AgentContextSnapshot.java
      AgentArtifact.java
    repository/
      AgentConversationRepository.java
      AgentRunRepository.java
      AgentContextSnapshotRepository.java
      AgentArtifactRepository.java
      AgentMemoryRepository.java
    context/
      ContextAssembler.java
      ContextAssemblyPolicy.java
      ContextCompressionPolicy.java
      TokenBudget.java
      ContextSourceRef.java
    trace/
      AgentTraceRedactor.java
      RedactionPolicy.java

backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/
  config/
    AgentPostgresPersistenceConfiguration.java
  mapper/
    AgentConversationMapper.java
    AgentRunMapper.java
    AgentContextSnapshotMapper.java
    AgentArtifactMapper.java
  repository/
    PostgresAgentConversationRepository.java
    PostgresAgentRunRepository.java
    PostgresAgentContextSnapshotRepository.java
    PostgresAgentArtifactRepository.java
  observer/
    PersistentAgentRunObserver.java
    PersistentAgentTraceObserver.java
  json/
    JsonbTypeHandler.java
    AgentTraceRedactorSupport.java

backend/agent-persistence-postgres/src/main/resources/
  mapper/agent/
    AgentConversationMapper.xml
    AgentRunMapper.xml
    AgentContextSnapshotMapper.xml
    AgentArtifactMapper.xml
  db/migration/agent/
    V2__agent_conversation_context.sql
```

目录命名可以在落地时微调，但原则是：core 放端口和模型，postgres 模块放实现和资源，API 不直接写 SQL。

## 分阶段实施

### 阶段 0：建立重构基线

目标：确认现有行为和未提交改动边界，避免重构时混入功能变化。

改动范围：

- 不移动代码。
- 梳理当前 agent conversation、run observer、trace observer、migration 的入口和测试覆盖。
- 补充或确认最小回归测试：
  - agent loop lifecycle 事件顺序。
  - conversation prepare run。
  - run 成功后写入 assistant message。
  - trace observer 保存 final request snapshot。

验收标准：

- `make backend-test` 可通过，或记录当前失败项和原因。
- 明确当前 `mentor-api` 中哪些类会迁移到 persistence 模块。
- 不引入 schema 变更。

风险控制：

- 不修改已经应用过的 Flyway 脚本内容。
- 不重命名数据库表。

### 阶段 1：在 agent-core 稳定运行态端口和模型

目标：先把通用模型和端口放到 `agent-core`，让上层依赖 core 契约，而不是依赖 mentor 包名。

改动范围：

- 在 `agent-core` 新增运行态模型和 repository 端口。
- 将现有 `ConversationRepository`、`ConversationMessage`、`ConversationDraft`、`ContextAssembler`、`ContextAssemblyPolicy` 的通用部分迁到 `agent-core`。
- 保留 mentor 侧兼容 adapter，减少一次性改动。
- 定义 metadata key 常量，例如 `taskId`、`turnId`、`runDbId`、`tokenBudget`，避免跨模块使用散落字符串。

建议命名：

- `ConversationRepository` 改为 `AgentConversationRepository`。
- `ConversationMessage` 改为 `AgentMessage`。
- `ConversationDraft` 可改为 `AgentRunDraft` 或 `PreparedAgentRun`。
- mentor 场景命令保留在 application，例如 `MentorConversationCommand`。

验收标准：

- `agent-core` 不依赖 Spring、MyBatis、PostgreSQL、Flyway。
- `mentor-application` 只依赖 `agent-core` 的端口，不再定义通用 conversation repository。
- 现有 API 行为不变。

风险控制：

- 不在本阶段引入长期记忆、向量检索或复杂 artifact scope。
- 只迁移当前已被使用的模型，未来概念先保留在文档中。

### 阶段 2：新增 agent-persistence-postgres 模块，先迁移现有实现

目标：把 API 模块里的 agent 持久化实现移到独立模块。第一步可以先保留 JDBC 实现，降低迁移风险。

改动范围：

- 在 `backend/pom.xml` 添加 `agent-persistence-postgres` 模块。
- 新模块依赖 `agent-core`、`llm-core`、Spring JDBC 或 MyBatis 基础依赖。
- 将以下类从 `mentor-api` 移入新模块：
  - JDBC conversation repository。
  - `PersistentAgentRunObserver`。
  - `PersistentAgentTraceObserver`。
- 在 `mentor-api` 配置类中引入 persistence 模块提供的 bean。
- API controller 和 SSE 代码只依赖 application/core，不直接依赖 SQL 实现细节。

验收标准：

- `mentor-api` 中不再出现 agent runtime SQL。
- `mentor-api` 中不再有 `repository/JdbcConversationRepository` 这类持久化实现。
- 原有接口仍可创建 task、turn、message、run，并通过 observer 更新 run 状态和 assistant message。

风险控制：

- 这一阶段不强制切 MyBatis。
- 保持表结构和 SQL 语义不变。
- 保持 package rename 之外的行为最小化。

### 阶段 3：迁移 Flyway schema 所有权

目标：让 agent runtime schema 跟随 `agent-persistence-postgres`，而不是由 `mentor-api` 拥有。

改动范围：

- 将 agent runtime migration 放到：

```text
backend/agent-persistence-postgres/src/main/resources/db/migration/agent/
```

- `mentor-api` 配置 Flyway locations：

```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration
      - classpath:db/migration/agent
```

- mentor 业务自己的 migration 仍放在 `mentor-api` 或未来独立业务 persistence 模块中。

验收标准：

- 全新数据库启动时可自动创建 agent runtime 表。
- `mentor-api` 不再保存 agent runtime schema 文件。
- 已存在数据库不会因为脚本路径移动产生 checksum 问题。

风险控制：

- 如果 `V2__agent_conversation_context.sql` 已经在共享环境执行过，不修改其内容。
- 后续 schema 调整新增 `V3__...sql`，不要改历史 V2。
- 若需要拆分大脚本，优先在新库验证；对已运行库使用追加 migration。

### 阶段 4：将 JDBC 实现替换为 MyBatis XML Mapper

目标：完成你倾向的 PostgreSQL + MyBatis XML Mapper 实现，同时保持 core 端口不变。

改动范围：

- 新增 mapper interface：
  - `AgentConversationMapper`
  - `AgentRunMapper`
  - `AgentContextSnapshotMapper`
  - `AgentArtifactMapper`
- 新增 mapper XML：
  - `mapper/agent/AgentConversationMapper.xml`
  - `mapper/agent/AgentRunMapper.xml`
  - `mapper/agent/AgentContextSnapshotMapper.xml`
  - `mapper/agent/AgentArtifactMapper.xml`
- 新增 JSONB type handler。
- repository 实现只组合 mapper，不写大段 SQL 字符串。
- 替换当前 `MAX(sequence_no) + 1` 的并发风险，优先使用数据库锁、唯一约束重试或独立序列策略。

验收标准：

- repository 单元测试或集成测试覆盖：
  - 创建新 task。
  - 复用 idempotency key。
  - 创建 turn/user message/run。
  - run 成功写 assistant message。
  - run 失败写 error。
  - 保存 context snapshot。
- mapper XML 不出现在 `mentor-api`。
- repository 端口不因 JDBC -> MyBatis 切换而变化。

风险控制：

- MyBatis 切换应单独提交，避免与模型迁移混在一起。
- JSONB 序列化、时间字段、枚举大小写要有测试。
- 幂等键唯一约束冲突要转成明确的重试或查询已有 run。

### 阶段 5：清理 mentor-application 的业务边界

目标：让 `mentor-application` 只表达算法学习应用逻辑。

改动范围：

- 将默认 mentor prompt 从持久化实现移到 application 层。
- 将 mentor 场景命令建模为业务输入，例如：

```text
MentorConversationCommand
  taskId
  userId
  userMessage
  idempotencyKey
  learningTopicId
  problemId
  planId
```

- application 负责把题目、学习计划、学习状态转换成：
  - system prompt。
  - initial messages。
  - metadata。
  - context policy。
- application 调用 `agent-core` 端口准备 run，不关心 PostgreSQL/MyBatis。

验收标准：

- `mentor-application` 无 JDBC/MyBatis/PostgreSQL/Flyway 依赖。
- `mentor-application` 中保留的 `conversation` 类都具有 mentor 业务语义。
- 通用 runtime 类型都来自 `agent-core`。

风险控制：

- metadata 中可以继续保存 `problemId`、`planId` 等业务信息，但 core 只把它们视为普通 metadata。
- 不让 core 反向理解学习业务字段。

### 阶段 6：收敛 mentor-api 为装配层

目标：让 API 层只负责入口、传输和 bean wiring。

改动范围：

- controller 调用 mentor use case。
- SSE adapter 只订阅 `AgentStreamEvent` 并映射为 Web 事件。
- Spring configuration 装配：
  - LLM provider。
  - agent runner。
  - application use case。
  - persistence repository。
  - persistent observers。
- 删除 API 模块中的 agent SQL、repository 实现和持久化 observer 实现。

验收标准：

- `mentor-api` 中搜索 `SELECT agent_`、`INSERT INTO agent_`、`UPDATE agent_` 没有结果。
- `mentor-api` 中不存在 MyBatis mapper XML。
- API 层测试通过。

风险控制：

- SSE 生命周期仍留在 API 层，不下沉到 core。
- `agent-core` 仍只暴露 publisher/event，不感知 HTTP 连接。

### 阶段 7：引入 artifact、上下文压缩和记忆能力

目标：在基础边界稳定后，再实现更复杂的上下文维护能力。

改动范围：

- 增加 `agent_artifact` 表及 repository。
- 支持 active summary artifact。
- 支持 message range summary、tool result summary、context compression result。
- 增加 compression policy 接口和默认 sliding window + active summary 策略。
- 保存 candidate context 选择记录和 final request snapshot 的关联。
- 可选增加异步摘要任务。

验收标准：

- 每次模型调用仍保存 final request snapshot。
- 摘要产物记录来源范围、策略版本、模型、token 估算和 metadata。
- 原始 message/run/trace 不因压缩被覆盖。

风险控制：

- 不把工具 trace 当成用户可见 message。
- 压缩产物必须可重建或废弃。
- 明文 trace 和大工具结果需要脱敏、加密或 retention 策略。

### 阶段 8：删除兼容层和更新文档

目标：完成收口，避免长期保留双重模型。

改动范围：

- 删除 mentor 包下已迁移的通用 conversation 类型。
- 删除 API 包下已迁移的 JDBC/persistence 类。
- 更新 `docs/code-index.md`。
- 更新 agent conversation/context 设计文档中的模块归属描述。
- 补充迁移后的包结构说明。

验收标准：

- 文档与代码模块边界一致。
- 没有旧包名的无效引用。
- `make backend-test` 通过。

## 推荐提交顺序

建议按阶段拆成多个提交或 PR：

1. `docs: add agent runtime refactoring plan`
2. `refactor: add agent runtime ports to agent-core`
3. `refactor: move agent persistence to postgres module`
4. `chore: move agent runtime flyway migrations`
5. `refactor: replace jdbc agent persistence with mybatis`
6. `refactor: keep mentor application business-only`
7. `feat: add agent context artifacts and compression`
8. `docs: update agent runtime module index`

如果当前未提交改动较多，优先先提交或暂存已有功能，再启动模块拆分，避免迁移过程中难以区分功能变更和结构变更。

## 关键风险点

### Flyway checksum

已执行过的 migration 不要修改内容。迁移资源路径通常不是问题，但修改脚本内容会导致 checksum 不一致。后续 schema 调整使用新版本 migration。

### 并发序号

当前基于 `MAX(sequence_no) + 1` 的方式在并发请求下有重复风险。迁移到 MyBatis 时应同时考虑行级锁、唯一约束重试或数据库序列。

### 幂等与重试

`idempotency_key` 应保持全局唯一或明确限定作用域。如果后续支持同一用户不同任务下复用 key，需要调整唯一约束和查询条件。

### observer 失败语义

持久化 observer 默认不应因为非关键写入失败影响主流程；但 run 状态、assistant message、context snapshot 是否强一致，需要明确策略。若必须强一致，应作为 mandatory observer 或 interceptor 建模。

### metadata 污染

`taskId`、`turnId`、`runDbId` 等运行态字段可以作为 core metadata，但学习业务字段只能作为不透明 metadata，不允许 core 依赖其语义。

### trace 脱敏

request snapshot、tool 参数、tool result、错误信息都可能包含敏感数据。持久化前必须执行字段级 redaction，并记录 redaction policy version。

### 过度抽象

第一轮只迁移已经真实存在的运行态能力。`AgentMemoryRepository`、向量检索、异步压缩、长期画像等能力可以先不实现，避免 core 变成未验证概念集合。

## 完成定义

本轮重构完成后应满足：

- `agent-core` 只包含业务无关 agent 核心抽象、运行态端口和策略。
- `agent-persistence-postgres` 拥有 PostgreSQL/MyBatis/Flyway 实现。
- `mentor-application` 不依赖任何具体持久化技术。
- `mentor-api` 不直接写 agent runtime SQL。
- final LLM request snapshot 仍在 agent loop 生命周期中保存。
- 多轮会话、run attempt、assistant message、错误状态和 trace 行为保持兼容。
