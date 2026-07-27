# AI 提供商配置与模型路由实施台账

> 来源：[AI 提供商配置与模型路由研发设计](ai-provider-and-model-routing-design.md)
>
> 工作流：按阶段串行交付；同一阶段内仅并行只读盘点或互不重叠的测试工作。每个阶段合入前执行对应验证闸门，并更新本台账、`docs/code-index.md` 和设计文档的状态。

## 执行约束

- 数据库、模型路由和 Client 版本是运行时唯一配置来源；不得引入文件配置或默认模型回退。
- Provider 配置中的 API Key、完整 `config` 和 Authorization 不得进入日志、异常 metadata、Agent trace、调用台账或 SSE。
- 所有业务场景、策略 type code、受信 metadata key、API 路径和错误码使用集中常量或枚举；前端从后端目录发现 provider type 与场景。
- 每次 AI 业务执行在额度消耗与运行锁之前解析一次路由；Agent run 固定该快照，独立子调用重新按自身场景解析。
- 每一项实现均保留不依赖真实 OpenAI Key 的单元或集成测试；真实连接只进入人工发布验收。

## 阶段与验收

| 状态 | 任务 | 依赖 | 验收证据 |
| --- | --- | --- | --- |
| 已完成 | 0.1 扫描现有调用链、策略底座、迁移版本和管理端契约 | 无 | 本台账、代码索引和最小文件清单 |
| 已完成 | 1.1 新增统一 `AiBusinessScenario` 目录及常量 | 0.1 | 场景 descriptor 完整、code 唯一、无 `AI_DEBUG` |
| 已完成 | 1.2 系统提示词 definition 关联场景，并补齐 `TOPIC_EXPLANATION` | 1.1 | 所有 active definition 可映射到场景；主题讲解带受管理系统提示词 |
| 已完成 | 1.3 新增 provider/model/usage ID 的 Flyway 迁移 | 0.1 | 全仓 Flyway 版本唯一；待最终 PostgreSQL 集成闸门复核 |
| 已完成 | 1.4 provider/model 领域模型、MyBatis mapper、repository 与管理服务 | 1.3 | 多实例、唯一约束、全量编辑和启停语义已实现 |
| 已完成 | 2.1 在 `llm-core` 定义 adapter、client、instance spec、invocation target 契约 | 1.1 | 无 PostgreSQL/策略依赖，registry 重复类型拒绝测试通过 |
| 已完成 | 2.2 改造 OpenAI 为动态 adapter 与强类型 config 校验 | 2.1 | config 边界校验、同步与流式同一 Client 测试通过 |
| 已完成 | 2.3 实现版本化 `ProviderClientRegistry` | 1.4, 2.1 | 相同版本复用、版本变化换新、创建失败不污染 holder |
| 已完成 | 2.4 改造 gateway 按调用目标动态分发并保留能力检查 | 2.2, 2.3 | 无默认模型/文件回退，provider/model 响应文本语义不变 |
| 已完成 | 3.1 为每个场景注册内部模型路由策略类型 | 1.1, 1.4 | 与场景一一对应，策略内容仅为正数 `modelId` |
| 已完成 | 3.2 实现路由解析、稳定错误和不可变执行快照 | 2.3, 3.1 | 无命中、停用、未知 type 与无效 config 均无 fallback |
| 已完成 | 3.3 将快照接入 AI 准入、Agent、direct completion 和后台任务 | 2.4, 3.2 | 准入在扣额度/加锁前解析；Agent 使用 run 内进程存储的固定 target |
| 已完成 | 3.4 扩展受信 metadata、指标和调用台账 ID 关联 | 1.3, 3.3 | 调用台账记录 provider instance/model ID；metadata 不含 config/client；路由解析和 provider 调用具备低基数指标 |
| 已完成 | 4.1 实现 provider type、实例和模型管理员 API | 1.4, 2.2 | 管理员 CRUD、类型不可变、详情完整 config、无 DELETE；列表不返回完整 config 的控制器契约测试通过 |
| 已完成 | 4.2 实现场景目录、配置状态和有效命中模拟 API | 3.1, 3.2 | 模拟仅查询策略/模型/实例，不创建 Client、不扣额度；未命中和不可用目标的控制器契约测试通过 |
| 已完成 | 4.3 在 `/admin/ai` 增加提供商与模型、模型路由页签 | 4.1, 4.2 | 已有目录驱动列表、编辑、模型维护、路由规则编辑和用户命中模拟 |
| 已完成 | 5.1 移除文件配置主链路、默认模型和环境变量说明 | 3.3 | `OPENAI_*`/`AI_GATEWAY_*` 不再由应用运行时绑定或使用 |
| 已完成 | 5.2 完成全量回归、迁移验证与文档交付 | 4.3, 5.1 | 前后端测试、V41 PostgreSQL 迁移验证和代码索引均已完成 |

## 每阶段工作流

1. 主任务先确认上游契约和已有用户改动，不跨越阶段写入下游模块。
2. 可委派的只读盘点、测试缺口审查和前端/后端独立实现由子任务完成；共享文件由主任务串行合并，避免覆盖。
3. 编码前列出本阶段的受影响 API、数据库和 metadata 契约；编码后立即运行最小模块测试。
4. 测试通过后，复查设计中的非目标，特别是不得新增 fallback、连接测试、自动同步、模型参数或 provider 限流。
5. 阶段验收通过后将本台账条目更新为“已完成”，记录命令和证据，再开始下一个阶段。

## 发布人工验收

1. 创建两个 OpenAI provider instance，并分别创建多个模型。
2. 为 Pro 用户组、指定用户和全部用户配置不同场景及显式优先级的规则。
3. 用有效命中模拟验证 Pro、普通用户和后台画像场景；确认未覆盖用户得到 `AI_MODEL_ROUTE_NOT_CONFIGURED`。
4. 发起最小实际业务调用，确认台账记录 provider instance/configured model ID，以及 provider type/upstream model 文本快照。
5. 在运行中修改路由或 provider config，确认旧 run 保持原快照，下一次运行使用新版本。

## 自动化验证记录

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=../.m2/repository -pl ai-governance -am test`：通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=../.m2/repository -pl mentor-api -am test`：通过。
- `mvn -f backend/pom.xml -B -ntp -q -Dmaven.repo.local=../.m2/repository -pl mentor-api -am -Dtest=AiProviderModelMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test`：通过。本机 PostgreSQL 16 的独立 schema 已完整执行 V1 至 V41，验证 `ai_provider_instance`、`ai_model` 和历史台账 nullable 关联。
- `npm --cache ./.npm --prefix frontend run build`：通过。
- `npm --cache ./.npm --prefix frontend run test`：通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=../.m2/repository -pl ai-governance -am -Dtest=DefaultAiModelRouteResolverTest,AiProviderCallMetricsLlmGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。覆盖路由成功/拒绝指标、provider 同步成功失败和流式取消后的活跃调用归零。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=../.m2/repository -pl mentor-api -am -Dtest=AdminAiProviderControllerTest,AdminAiModelRoutingControllerTest,MentorAiConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。覆盖 provider 列表/详情的 config 边界、动态场景目录、未命中与不可用目标模拟，以及动态 gateway 的指标装配。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=../.m2/repository -pl ai-governance -am test`：通过。AI 治理模块及上游依赖共 63 个治理测试通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=../.m2/repository -pl mentor-api -am test`：通过。API 模块及全部上游单元测试通过。
- `npm --cache ../.npm run test`（在 `frontend/`）：通过，89 个测试文件、335 个测试全部通过。
- `npm --cache ../.npm run build`（在 `frontend/`）：通过。
