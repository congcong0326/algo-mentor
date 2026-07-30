# UAF-05：Runtime 治理模式

> 波次：B
>
> 状态：DONE
>
> 直接依赖：UAF-01、UAF-04
>
> 建议上下文上限：11 个生产/测试文件

## 1. 目标与完成标准

为 Runtime 提供 `USER_ENTRY / CHILD / BACKGROUND` 三类治理执行租约，统一模型路由、metadata、成功/失败收尾和资源清理，但本任务不接入业务 Definition。

## 2. 必须读取

- `AiRunAdmissionService.java`、`AiRunLifecycleService.java` 及测试。
- `AiGovernedCompletionService.java`、`AiCompletionContext.java`、`AiCompletionMode.java` 及测试。
- `AiRunInvocationTargetStore`、`AiModelRouteResolver`。
- `AiAccountingLlmGateway` 和治理 metadata key。
- UAF-01 Invocation context 与 UAF-04 Prepared run 契约。

## 3. 目标治理接口

在 `agent-runtime` 或 `ai-governance` 提取一个可被 Runtime 调用的治理协调边界。具体名称可调整，但返回值需要表达一次执行租约：

```text
begin(invocation, run identity)
  -> trusted metadata
  -> invocation target / route snapshot
  -> optional admission and lock token
  -> complete(...)
  -> fail(...)
```

不得把 `AiGovernedCompletionService.complete` 作为 Runtime 的底层执行器。可以抽取它与 Runtime 共用的治理逻辑，迁移期保留旧 gateway 外观。

## 4. 模式语义

### USER_ENTRY

- 调用完整 `AiRunAdmissionService.admit`。
- 继续使用共享 `ALL` 额度。
- 获取用户级运行锁。
- 绑定模型 invocation target。
- run 开始、成功、失败、取消都更新治理生命周期并释放资源。

### CHILD

- 必须有 parentRunId 和 parentStepIndex。
- 不消费额度，不获取用户锁。
- 检查全局、用户和 purpose 开关。
- 按 child 自己的 Agent key/AiBusinessScenario 解析模型。
- 通过 metadata 进入独立调用级 Token 记账。

### BACKGROUND

- 不允许 parent run 字段。
- 不消费额度，不获取用户锁。
- 检查动态开关并按后台场景路由、记账。

## 5. 场景目录

建立唯一代码映射：

```text
AgentKey.value
  -> AiBusinessScenario.fromCode
  -> AiRunSource
  -> AiPurpose
```

当前九个场景必须一一可解析，但本任务测试可以只注册测试 key。调用方不能在 Invocation 中覆盖 source、purpose 或 scenario。

## 6. 实施步骤

1. 抽取或新增治理协调服务，消除与 direct completion 的重复逻辑。
2. 实现三种模式的 begin/complete/fail/cancel。
3. 固定路由 metadata 和调用记账关联。
4. 确保 begin 后任意异常都有幂等清理路径。
5. 增加额度 store、用户锁、route resolver、lifecycle 和 invocation target 的交互测试。
6. 保持现有 `AiGovernedCompletionServiceTest` 全部通过。

## 7. 非目标

- 不执行 Agent loop。
- 不创建 Definition Bean。
- 不迁移 controller。
- 不新增场景级额度或 fallback 路由。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance,agent-runtime -am test

git diff --check
```

## 9. 上下文交接

记录治理租约接口、三种模式的副作用矩阵、场景映射位置和失败清理入口。不要复制策略或 route 对象完整结构。

## 10. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增共享治理租约，覆盖 USER_ENTRY、CHILD、BACKGROUND 的 begin/complete/fail/cancel。
- 建立 Agent key 到九个业务场景、AiRunSource、AiPurpose 的唯一目录，并固定 AGENT_STEP 调用台账 metadata。
- 兼容直接调用网关改为复用租约；自动配置暴露共享治理协调服务。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance,agent-runtime -am test`：通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-06`
