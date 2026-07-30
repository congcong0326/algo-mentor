# UAF-01：核心契约与模块骨架

> 波次：A
>
> 状态：DONE
>
> 直接依赖：UAF-00
>
> 建议上下文上限：10 个生产/测试文件

## 1. 目标与完成标准

新增 `agent-runtime` Maven 模块，并在 `agent-core` 稳定类型化 Runtime 契约。完成后可以注册和解析 Definition，但没有生产业务场景切换到新 Runtime。

## 2. 必须读取

- `backend/pom.xml`、`backend/agent-core/pom.xml`、`backend/ai-governance/pom.xml`、`backend/mentor-api/pom.xml`。
- `AgentRequest.java`、`AgentExecutionOptions.java`、`AgentRunResult.java`。
- `AiBusinessScenario.java`、`AiRunSource.java`。
- UAF-00 完成备注和 `CURRENT.md`。

## 3. 核心契约

在 `agent-core` 按职责分包，避免继续平铺：

```text
agent/core/runtime/api/
  AgentRuntime.java
  AgentInvocation.java
  AgentInvocationContext.java
  AgentInvocationMode.java

agent/core/runtime/definition/
  AgentKey.java
  AgentDefinition.java
  AgentLoopPolicy.java
  AgentOutputContract.java
```

允许根据现有包结构微调，但必须满足：

- `AgentKey<I>` 同时保存稳定 value 和 `Class<I>`。
- `AgentInvocation<I>` 让 key 与 input 在编译期关联。
- Invocation context 保存受信 userId、模式、幂等键、父 run/step、requestSize 和 streaming，不接收调用方覆盖的治理场景。
- `AgentRuntime.execute` 返回 `AgentRunResult`，`stream` 返回 `Flow.Publisher<AgentStreamEvent>`。
- `AgentDefinition<I>` 不保存单次调用状态，不解析领域输出。
- 第一阶段 `AgentLoopPolicy` 只保存 `maxSteps`。
- `AgentOutputContract` 复用现有 `AgentExecutionOptions`/structured output 能力，不建立新的 Schema DSL。

## 4. agent-runtime 模块

新增模块骨架：

```text
backend/agent-runtime/
  pom.xml
  src/main/java/.../agent/runtime/
  src/test/java/.../agent/runtime/
```

依赖保持：

```text
agent-runtime -> agent-core + llm-core + ai-governance
```

本任务实现 `AgentDefinitionRegistry`，但不实现完整 `DefaultAgentRuntime`。Registry 必须：

- 拒绝空 key、重复 key 和空 input type；
- 保存 `AgentDefinition<?>`，将唯一受控类型转换封装在 Registry 内；
- 不使用业务字符串 switch；
- 支持当前 Spring 条件装配传入的 Definition 集合为空或不完整；
- 暂不要求九个场景全部注册，完整性门禁在 UAF-13。

## 5. 实施步骤

1. 新增 Maven 模块并加入 backend 聚合顺序，确保依赖无环。
2. 添加核心类型和构造校验。
3. 添加 Registry 及类型安全解析方法。
4. 增加契约、重复 key、错误 input type 和不可变集合测试。
5. 在 `mentor-api` 增加模块依赖，但不注册生产 Runtime Bean。
6. 确认 `mentor-application` 只依赖 core 契约，不依赖 runtime 实现模块。

## 6. 非目标

- 不拆 `AgentLoopRunner`。
- 不修改工具集合。
- 不调用治理或数据库。
- 不注册九个空壳 Definition。
- 不迁移 Topic 或其他业务场景。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core,agent-runtime -am test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,mentor-api -am -DskipTests package

git diff --check
```

## 8. 上下文交接

在 `CURRENT.md` 只记录最终包名、公开接口签名、Maven 依赖和未实现部分。不要复制全部类型代码。

## 9. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 在 `agent-core.runtime.api` 与 `agent-core.runtime.definition` 新增 Runtime、Invocation、Context、Definition、Key、Policy 和输出契约。
- 新增 `agent-runtime` 与不可变 `AgentDefinitionRegistry`，封装受控类型转换并覆盖空集、重复 key、类型错误和不可变集合。
- `mentor-api` 已声明新模块依赖；`mentor-application` 保持仅依赖 core 契约。

验证：

- `-pl agent-core,agent-runtime -am test`：PASS。
- `-pl mentor-application,mentor-api -am -DskipTests package`：PASS。
- `git diff --check`：PASS。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-02`
