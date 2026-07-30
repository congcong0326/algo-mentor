# UAF-11：Declared Profile 子 Agent

> 波次：D
>
> 状态：DONE
>
> 直接依赖：UAF-10
>
> 建议上下文上限：12 个生产/测试文件

## 1. 目标与完成标准

将学习者自述画像判定从 `AiCompletionGateway` 迁移为 `AgentRuntime.execute` 的 `CHILD` 调用，保留批量全有或全无、严格维度白名单、NO_CHANGE/REPLACE 和一次 stale retry 语义。

完成后，画像工具不再自行构造治理 completion context，所有模型判定都进入统一无工具 Agent loop。

## 2. 必须读取

- `CURRENT.md` 和 UAF-10 完成备注。
- `UpdateLearnerDeclaredProfileAgentTool.java` 及测试。
- `DeclaredProfileUpdateService.java`、Prompt Builder、JSON Schema 及测试。
- `LearnerProfileQueryService`、`LearnerProfileUpdateService` 的 snapshot/applyBatch 契约。
- `LearnerProfileAgentProperties.java`。
- `AgentConversationApiAutoConfiguration.java` 的 declared profile Bean。
- UAF-04 的同 turn retry API 和 UAF-10 的 child 调用参考实现。

## 3. Child Definition

新增 `DeclaredProfileUpdateAgentInput`，保存一次模型决策所需的受信候选：

```text
userId
candidate dimensions
explicit statements and intents
current content snapshots
logical idempotency key
```

Definition 固定：

- key 使用 `AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE.code()`。
- 使用现有受管理 Prompt 和 JSON Schema。
- 工具集合为空，`maxSteps=1`。
- 不读取或写入画像 repository；snapshot 加载和 applyBatch 留在 service。
- 输出只由现有严格 parser 映射，不增加宽松字段兼容。

## 4. 工具与父关联

`UpdateLearnerDeclaredProfileAgentTool` 继续：

- 只在受信 `PRACTICE_CHAT` 上下文工作；
- 从 metadata 取得 userId 和父 run DB id；
- 从 execution context 取得父 step；
- 只接受 dimensions/statements/intents，不接受身份或版本字段；
- 对无效参数或 Runtime 失败返回普通 FAILED JSON，不中断父聊天。

child 幂等键由父 run DB id、父 step、工具名和规范化请求摘要生成。摘要使用稳定哈希，不把完整用户自述写入 task title、幂等键或日志。

## 5. Stale retry 语义

一次工具执行是一条逻辑 child turn：

```text
加载 snapshot
  -> child run attempt 1 做模型判定
  -> applyBatch 返回 STALE
  -> 重新加载全部 snapshot
  -> 同一 task/turn 下创建 child run attempt 2
  -> 再次整体判定和 applyBatch
```

固定要求：

- stale retry 复用 child task/turn 和逻辑幂等身份，创建新的 run attempt。
- 第二个 run 继续关联同一 parent run/step，并写 `retry_of_run_id`。
- 每个 attempt 都按自己的模型调用记账，但都不消耗用户交互额度、不获取用户锁。
- 最多重试次数继续由现有配置限制为 `0..1`。
- 任一批次结果数、维度或字段不匹配时整批失败，不做部分写入。
- provider/model 从对应成功 attempt 的 Runtime result 取得并写入画像来源。

## 6. 实施步骤

1. 新增 input、Definition 和严格输出映射测试。
2. 将 service 的 `decide` 改为构造 CHILD Invocation 并调用 Runtime。
3. 在 stale 分支使用 UAF-04 retry 契约，而不是创建无关新 turn。
4. 修改工具移除 `AiCompletionContext.parentRun`。
5. 更新 Spring 装配与功能开关，Definition/Runtime 缺失时不注册工具。
6. 删除 declared profile 生产路径对 `AiCompletionGateway` 的引用。

## 7. 重点测试

- 一次批量判定产生一个 child task/turn/run。
- stale 后同 turn 有两个 run attempts，parent run/step 相同。
- child 使用 declared profile 场景路由，不继承 Practice 模型。
- 两个 attempts 都不扣额度、不获取用户锁。
- 多维 REPLACE/NO_CHANGE 顺序和 applyBatch 原子性不变。
- invalid output、第二次 stale、Runtime deny/failure 返回 FAILED 且零部分写入。
- tool 参数、日志、幂等键和 task title 不暴露完整用户自述。
- 父聊天拿到 UPDATED/NO_CHANGE/FAILED 的现有稳定 JSON。

## 8. 非目标

- 不修改画像领域表或内容策略。
- 不允许模型创建新维度。
- 不在 Runtime 中实现业务 stale retry 策略。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=DeclaredProfileUpdateServiceTest,DeclaredProfileUpdateJsonSchemaTest,UpdateLearnerDeclaredProfileAgentToolTest,DeclaredProfileUpdateIT test

git diff --check
```

补充执行 child retry 持久化和治理测试。

## 10. 停止条件

stale retry 创建了第二个逻辑 turn、出现部分画像写入、或完整用户自述进入审计标题/日志时，不得进入 UAF-12。

## 11. 上下文交接

记录 Definition、child 幂等哈希输入、retry API、成功 provider/model 来源、批量原子测试和失败降级。不要复制用户自述样例正文。

## 12. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增无工具、单 step 的 Declared Profile Definition，并以 CHILD Runtime 执行模型判定。
- child 幂等键仅含父 run、step、工具名和请求摘要哈希；stale retry 复用 task/turn，记录 retry 来源。
- 自动配置仅在 Definition、Runtime 与领域依赖齐备时注册工具，并将 Definition 注册到 Runtime Registry。

验证：

- 画像 service/schema/tool/IT、Runtime retry 传播、持久化 retry 和自动配置目标测试通过；`git diff --check` 通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-12`
