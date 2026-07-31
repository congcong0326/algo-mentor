# AMR-03：Operation 校验、证据分级与原子应用

> 波次：B
>
> 状态：DONE
>
> 直接依赖：AMR-02
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

实现统一的 `ADD / CONFIRM / REVISE / RETIRE` 校验和短事务原子应用内核，供 declared 与 Code Review 两条链路共同使用。

完成后，模型只产生候选 operation；服务端重新验证用户、scope、证据、容量、快照和状态，再一次性写 revision、完整 evidence 和 update run 终态。

## 2. 必须读取

- `CURRENT.md`、`AMR-02` 完成备注及其端口。
- `CONTRACTS.md` 第 2 至 8 节。
- 旧 `LearnerProfileUpdateService.java`、`ProfileUpdateCommand.java`、`LearnerProfileSnapshot.java`。
- `CodeReviewProfileStructuredOutputMapper.java`，只参考整批拒绝模式。
- `PracticeCodeReviewRepository.java` 和 `AgentTurnMessageLookupRepository.java` 的受信读取边界。
- 新 `LearnerMemory*Repository` 及 PostgreSQL adapter 测试。

## 3. 核心类型

建议新增：

- `LearnerMemoryOperation` 与 typed review/message evidence ref。
- `LearnerMemoryOperationBatch`，固定单用户、最多 12 项、目标 revision 不重复。
- `LearnerMemoryEvidenceValidator`。
- `LearnerMemoryEvidenceGradeCalculator`。
- `LearnerMemoryOperationValidator`。
- `LearnerMemoryAtomicApplyService`。
- `LearnerMemoryUpdateRunService` 或等价 run 生命周期门面。

Operation 类型必须让非法字段组合无法被静默忽略；例如 `CONFIRM` 不接受新文本，`ADD` 不接受 revision ID。

## 4. 校验与应用顺序

模型和工具调用前后只允许短事务：

1. 事务外创建或幂等取得 `RUNNING` update run。
2. 事务外执行 Agent/工具并映射为 typed operations。
3. 应用事务锁用户行，批量重读当前 ACTIVE 集合并复核用户 snapshot token。
4. 按来源重新查询 Review/message，校验用户归属、窗口/tag、pattern、role、顺序和重复。
5. 校验 claim scope、文本、hash、目标 ACTIVE 状态、scope 上限和用户软硬上限。
6. 按稳定 scope + revision 顺序应用全部 operation。
7. 为每个新 revision 写完整 evidence 集合，更新 update run 的 operation/tool 数和终态。
8. 任一失败回滚全部 revision/evidence 写入；run 失败终态通过独立短事务记录。

`NO_CHANGE` 使用空 operations，run 状态为 `NO_CHANGE`。第一次 STALE 不写 operation，由调用方最多重算一次。

## 5. Revision 语义

- `ADD`：服务端生成 claim UUID、revision 1 和 ACTIVE。
- `CONFIRM`：先 supersede 旧 ACTIVE，再插入相同文本和 hash 的下一 ACTIVE revision，evidence 为旧有效证据加本次新证据的完整集合。
- `REVISE`：scope 与 claim key 不变，只保留仍支撑新文本的旧证据并加入新证据。
- `RETIRE`：supersede 旧 ACTIVE，复制旧文本，插入 RETIRED 当前 revision，主要关联导致终止的 evidence。
- `SUPPRESSED / REJECTED` 本任务只保留通用 revision 能力，不暴露给 Agent 或 API。

## 6. 容量与失败码

- scope 达到 10/10/5 时拒绝该 scope 的 `ADD`，整批回滚。
- 用户达到 500 只记录 `SOFT_LIMIT`，不拒绝写入。
- 用户达到 1000 只拒绝 `ADD`；其他操作继续。
- 失败码使用稳定低敏枚举，例如 `STALE_SNAPSHOT`、`INVALID_EVIDENCE`、`SCOPE_LIMIT`、`HARD_LIMIT`、`DUPLICATE_ACTIVE_TEXT`。
- 异常和日志不包含 claim、代码、Review 或消息正文。

## 7. 重点测试

- 四种 operation 的完整版本链和 evidence 自包含。
- 空 operations、整批字段非法、重复目标、scope 变化和伪造来源整批拒绝。
- 所有 evidence pattern 最小结构与 grade 计算。
- `SINGLE_REVIEW` 只允许标签评价。
- 用户锁、snapshot stale、两次并发 ADD、重复文本 hash 和批量全有或全无。
- 500/1000 与 10/10/5 边界；硬上限下 REVISE/CONFIRM/RETIRE 仍成功。
- 模型调用模拟阻塞时数据库事务和连接没有被持有。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='*LearnerMemory*Operation*Test,*LearnerMemory*Evidence*Test,*LearnerMemory*Apply*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test='*LearnerMemory*ConcurrencyIT,*LearnerMemory*ApplyIT' verify

git diff --check
```

## 9. 非目标与停止条件

- 不改 declared tool、Review consumer、Prompt、召回或 API。
- 若服务端仍信任模型 grade、证据顺序或用户 ID，或者部分 operation 可以提交，不得开始写入链路迁移。

## 10. 上下文交接

记录 operation 类型、失败码、apply 事务入口、snapshot stale 返回类型和 grade 计算器名称。不要携带测试中的 claim 文本样例。

## 11. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增 sealed operation 批次、服务端证据 pattern/grade 校验和稳定低敏失败码；模型不能提供用户、状态、grade、时间或 evidence 顺序。
- 增加 `LearnerMemoryAtomicApplyService`：锁用户、复核 snapshot、校验证据/容量后原子写入 revision 和完整 evidence，并返回 `APPLIED / NO_CHANGE / STALE`。
- 增加 `REQUIRES_NEW` 的失败 run 生命周期服务；无效批次回滚后独立记录 `FAILED`，不切换旧画像链路。

验证：

- `-pl mentor-application -am -Dtest='*LearnerMemory*Operation*Test,*LearnerMemory*Evidence*Test,*LearnerMemory*Apply*Test' test`：PASS。
- `-pl mentor-api -am -Dit.test='*LearnerMemory*ConcurrencyIT,*LearnerMemory*ApplyIT' verify`：PASS。
- `git diff --check`：PASS。

偏离计划：

- 原子并发覆盖保留在 `LearnerMemoryAtomicApplyIT`，未额外创建只包装同一场景的 `*ConcurrencyIT`。

遗留事项：

- 受信 evidence context 的具体构建留给 AMR-04、AMR-05 和 AMR-06 各自从服务端消息/Review 查询接入。

下一任务：`AMR-04`
