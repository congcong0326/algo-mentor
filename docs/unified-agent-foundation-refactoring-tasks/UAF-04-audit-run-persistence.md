# UAF-04：审计运行准备与持久化

> 波次：B
>
> 状态：DONE
>
> 直接依赖：UAF-01
>
> 建议上下文上限：12 个生产/测试文件

## 1. 目标与完成标准

扩展现有 task/turn/run 持久化，使会话、单次、子调用和后台调用都能创建统一审计记录，并支持父 run/step 与同 turn retry。完成后尚不执行模型。

## 2. 必须读取

- `AgentRunPreparationRequest.java`、`PreparedAgentRun.java`、`AgentConversationRepository.java`。
- `PostgresAgentConversationRepository.java` 及测试。
- `AgentConversationMapper.java/xml`、`AgentRunMapper.java/xml`。
- `V2__agent_conversation_context.sql`、`V3__agent_runtime_sequence_counters.sql`、`V31__agent_diagnostic_retention.sql`。
- 所有模块 migration 文件名列表，用于选择新版本。

## 3. 数据模型决策

保留非空 task/turn 外键，并扩展 run 审计字段：

- `agent_key`：新 Runtime 创建的 run 必填，历史 row 可空。
- `parent_step_index`：仅 child run 使用，与 `parent_run_id` 同时存在。
- `trigger_type`：复用并写入 `USER_ENTRY`、`CHILD`、`BACKGROUND` 的稳定数据库值。
- `retry_of_run_id`：继续表达 stale retry 或显式重试链。

新增 Flyway migration 前重新扫描版本；不得修改历史脚本。约束至少覆盖 parent step 为正数，以及 parent run/step 成对出现。

## 4. Repository 契约

优先扩展通用准备请求，而不是在 Runtime 里拼 SQL。需要支持：

1. 会话调用：传已有 taskId，创建 turn、user message 和首个 run。
2. 单次审计：taskId 为空，使用受信 userId 创建独立 task、turn、message 和 run。
3. child/background：与单次审计相同，但写入 mode 和父关联。
4. replay：同一幂等键返回既有准备结果，不重复创建。
5. retry：复用既有 task/turn，创建新的 run attempt 和 `retry_of_run_id`；新 attempt 使用新的唯一运行幂等键，不能被普通 replay 吞掉。

如果现有 `AgentConversationRepository` 命名已明显妨碍通用语义，可以新增更窄的 `AgentRunPreparationRepository` 端口并让 PostgreSQL 实现组合现有 mapper；不要在本任务大范围重命名整个 conversation 包。

## 5. 实施步骤

1. 确认最终列、约束和索引，创建唯一新 migration。
2. 扩展 core 请求/结果模型和 metadata 常量。
3. 扩展 mapper interface/XML 与行模型。
4. 实现首 run、replay 和 retry 三条 repository 路径。
5. 保证新 task 的 `user_id` 使用受信用户，title 和 metadata 不泄露完整 Prompt 或代码。
6. 增加 mapper XML、repository、幂等、parent、retry 和历史兼容测试。
7. 不改变现有会话 recentMessages 查询语义。

## 6. 非目标

- 不调用治理或 LLM。
- 不把 task/turn 外键改为可空。
- 不清理历史 run。
- 不实现前端审计查询。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core,agent-persistence-postgres -am test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -DskipTests package

git diff --check
```

有可用 PostgreSQL 集成测试基线时，必须补充 migration 升级和 parent/retry 写入验证。

## 8. 停止条件

发现最新 migration 已占用计划版本时重新分配版本号，不询问用户。只有需要破坏历史数据或修改已应用脚本时才暂停。

## 9. 上下文交接

记录 migration 文件、最终 repository 入口、字段语义和 retry 规则。下一任务只需读取这些新契约与治理服务，不重读全部 mapper SQL。

## 10. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增 `V45__agent_runtime_run_audit.sql`，写入 `agent_key`、父 run step、稳定 trigger type 与关联约束/索引。
- 准备请求和结果扩展 agent key、模式、父关联、retry 来源与 max steps，旧构造器保留默认值。
- PostgreSQL repository 支持初始 run、幂等 replay 与同 task/turn 的新 retry attempt；retry 继承来源的 agent key、模式和父关联。
- 新建审计 task 使用固定标题且不写入请求 metadata，避免把完整 Prompt 或代码复制到审计 metadata。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core,agent-persistence-postgres -am test`：通过（agent-core 149 tests，持久化模块 34 tests）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -DskipTests package`：通过。
- `git diff --check`：通过。

偏离计划：无。

遗留事项：本地没有可用 PostgreSQL 集成测试基线；migration 的列、约束和 SQL 由资源/XML 测试覆盖。

下一任务：`UAF-05`
