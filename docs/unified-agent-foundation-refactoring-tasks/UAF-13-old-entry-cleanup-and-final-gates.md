# UAF-13：旧入口清理与最终门禁

> 波次：E
>
> 状态：DONE
>
> 直接依赖：UAF-12
>
> 建议上下文上限：12 个生产/测试文件

## 1. 目标与完成标准

删除统一 Runtime 落地后的业务旧入口和通用层业务泄漏，建立可持续的模块与场景门禁，并完成全量构建验收。

本任务不再迁移新业务行为。若发现某场景仍依赖旧入口，应回到对应任务修复并更新完成备注，不能用例外名单掩盖。

## 2. 必须读取

- `CURRENT.md` 和 UAF-12 完成备注。
- 使用 `rg` 得到的剩余生产引用清单，不通读所有迁移任务。
- `MentorAiConfiguration.java`、`LearningPlanConfiguration.java`、两个 Agent auto-configuration。
- `PersistentAgentRunObserver.java`、`AgentOpsObserver.java` 及测试。
- backend 各相关模块 `pom.xml`。
- 完整生产装配测试和现有 Flyway resource test。
- `docs/code-index.md` 中 Agent 相关条目。

## 3. 旧入口清理

最终生产依赖必须满足：

```text
mentor-application
  不依赖 AgentLoopRunner
  不依赖 AgentRunner
  不依赖 AiCompletionGateway

正式 AI controller / SSE adapter
  不直接依赖 AiRunAdmissionService
  不直接依赖 AiRunLifecycleService

agent-runtime
  不通过 AiCompletionGateway 执行模型
```

具体处理：

- 删除已无消费者的 `AgentRunner` 业务 Bean。
- 删除 `AiCompletionGateway` 在 mentor-api 的生产装配，前提是 `rg` 证明没有其他正式调用者。
- `AgentLoopRunner` 若仍作为 agent-core 兼容 facade 或低层测试夹具可保留，但不能被业务模块注入；无引用时再删除，不为“清理感”扩大改动。
- 移除 controller/SSE subscriber 中重复的治理 begin/complete/fail 代码。
- 删除迁移期 Practice/Mentor 分支和过渡构造器。
- 清理未使用的配置 key、imports 和测试 fake。

## 4. Observer 业务泄漏清理

`agent-persistence-postgres` 和 `ops-observability` 不得硬编码 Practice、学习计划或画像场景字段。

优先采用当前实现中最窄的通用机制：

- Definition/业务适配器把 assistant message 所需业务 metadata 放入受信的通用 metadata map；
- 通用持久化 observer 只按稳定 core key 读取该 map；
- 或复用 UAF-06 已存在的通用 metadata contributor 接口。

不要同时保留 map 和 contributor 两套扩展方式。清理后：

- `PersistentAgentRunObserver` 不再判断 `PRACTICE_CHAT` 或读取 `practiceSessionId/planId/phaseIndex/problemSlug` 字面量。
- Practice assistant message 仍写入现有业务 metadata，前端会话查询不回归。
- `AgentOpsObserver` 从 Runtime 受信的 source/agent key 取得观测来源，不维护另一套场景字符串 switch。
- 新增场景不要求修改通用 persistence/ops 代码。

## 5. Definition 完整性门禁

在完整生产配置下断言九个 `AiBusinessScenario` 都有且只有一个 Definition：

```text
MENTOR_CONVERSATION
TOPIC_EXPLANATION
PRACTICE_CHAT
LEARNING_PLAN_DRAFT
LEARNING_PLAN_REVISION
LEARNING_PLAN_EXTENSION
PRACTICE_CODE_REVIEW
LEARNER_DECLARED_PROFILE_UPDATE
CODE_REVIEW_PROFILE_UPDATE
```

门禁同时固定：

- key value 与 scenario code 一致；
- input type 唯一且可解析；
- 工具白名单与各迁移任务完成备注一致；
- 三个结构化 one-shot 场景为空工具、`maxSteps=1`；
- Topic 为空工具、`maxSteps=1`；
- 当前启用的工具型场景引用的工具全部存在；
- 任一 Definition 的 `maxSteps` 不超过全局硬上限；
- 精简配置只校验已启用 Definition，不强行创建 no-op 工具。

## 6. 架构门禁

优先使用仓库已有测试方式；若没有 ArchUnit，不为本任务单独引入重型测试框架。可以使用小型源码/字节码依赖测试固定：

- `mentor-application/src/main` 不引用三个旧执行入口。
- 正式 AI controller 不引用 admission/lifecycle service。
- `agent-runtime` 不引用 `AiCompletionGateway`。
- `agent-core` 不依赖 `AiBusinessScenario` 或 Spring。
- `mentor-application` 只依赖 core Runtime 接口，不依赖 runtime 实现类。
- persistence/ops 通用代码不包含 Practice/学习计划/画像常量。

测试应扫描生产类或 Maven 依赖，不能只断言 Spring 测试上下文里“碰巧没有 Bean”。

## 7. 最终回归

至少覆盖：

- 九个 Definition 的完整装配和工具白名单快照。
- execute/stream 的文本、结构化输出、取消和错误终态。
- USER_ENTRY/CHILD/BACKGROUND 的额度、锁、路由、记账矩阵。
- 会话 task 与独立审计 task 的隔离。
- parent run/step、retry turn/attempt 和 Flyway 新列。
- Topic、Mentor、Practice、Learning Plan、Review 和画像 E2E。
- executor 饱和/拒绝、Agent 工作线程 child inline。
- SSE 对外契约和应用启动。

## 8. 文档与索引

- 更新 `docs/code-index.md` 的 `agent-runtime` 模块、Runtime 接口、Definition 目录和关键装配入口。
- 更新本实施计划状态为完成并保留最终验证命令。
- 将 `CURRENT.md` 覆盖为完成态，保留最终稳定契约、验证结果和已知残余风险。
- 不把 14 个任务完成日志汇总复制进总计划；历史仍保留在各任务文件。

## 9. 验证命令

```bash
rg -n "AgentLoopRunner|AgentRunner|AiCompletionGateway" \
  backend/mentor-application/src/main/java

rg -n "AiRunAdmissionService|AiRunLifecycleService" \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service

make backend-test
make backend-build
git diff --check
```

前两个 `rg` 命令预期无生产命中；若低层兼容类仍保留，另行限定允许目录并写入架构测试，而不是在交付说明中口头豁免。

## 10. 停止条件

- 不修改已应用 Flyway 历史脚本。
- 不为通过门禁删除仍有真实生产调用者的兼容 API。
- 若全量测试存在任务前基线失败，只能引用 UAF-00 证据；本重构引入的失败必须修复。

## 11. 上下文交接

完成时 `CURRENT.md` 只保留最终模块入口、九个 Definition 状态、全量测试/构建结果、migration 版本和残余兼容类。整体继续控制在 120 行以内。

## 12. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 删除业务层旧 completion/runner/coordinator 装配，九个正式场景统一由 `AgentRuntime` 运行。
- 清理 Learning Plan controller/SSE 的重复治理，observer 改为受信通用 metadata 与 Runtime `agentKey`。
- 新增完整 Definition 装配快照和源码架构门禁；更新 Runtime、Definition、V45 审计迁移索引。

验证：

- Definition/application 与架构门禁通过；UAF-13 API 目标集通过（56 tests）。
- `make backend-test` 通过（276 份 Surefire 报告，无 failures/errors）。
- `make backend-build` 与 `git diff --check` 通过；旧入口 `rg` 检查无生产命中。

偏离计划：无。

遗留事项：`AgentLoopRunner` 作为 agent-core 低层兼容 facade 保留，业务模块无生产引用。

下一任务：完成
