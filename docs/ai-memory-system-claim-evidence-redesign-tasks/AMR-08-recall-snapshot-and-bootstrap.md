# AMR-08：Run-local 召回快照与启动索引

> 波次：C
>
> 状态：DONE
>
> 直接依赖：AMR-02、AMR-05
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

建立 Practice Chat 的只读 run-local claim 快照、自然主题索引、直接命中排序和完整项 bootstrap 裁剪，并替换旧 800 token 整段画像 section。

本任务先接通 bootstrap；三项业务记忆工具在 `AMR-09` 接线。当前 run 内并发更新不可见，scope 在 run 所有终态释放。

## 2. 必须读取

- `CURRENT.md`、`AMR-02` 与 `AMR-05` 完成备注。
- `CONTRACTS.md` 第 9、10、12 节。
- 旧 `LearnerProfileRecallService.java`、Snapshot、PromptSectionProvider、Policy/Resolver 及测试。
- `AgentConversationService.java` 的 `assemblePracticeChatContext`。
- `PracticeChatRunAdapter.java`、`PracticeChatAgentDefinition.java`。
- `PracticeChatPromptConstants.java`、`PracticeChatPromptProfileResolver.java`。
- `LearnerProfileRecallProperties.java`、`PracticeChatPromptProperties.java`。
- 新 claim query/snapshot 服务和 run scope registry。

## 3. Recall Snapshot

`LearnerMemoryRecallService.openSnapshot(...)` 至少接收受信 user ID、当前用户消息、problem slug、受信 tag 和 locale，返回：

- 有序 ACTIVE claim revision 集合。
- `documentRevision`。
- 固定自然主题及 `sectionRef`。
- 每个 claim 的 run-local `statementRef`、来源摘要、grade、更新时间和当前题命中信息。
- 随机不可枚举 `scopeRef` 与显式 release lease。

完整 claim 不放入 request metadata；registry 只在内存中按 scopeRef 保存，metadata 只放 ref、revision、section/count 和 token 统计。

replay 不新建 scope；正常 run 的成功、失败、取消、提交拒绝和锁冲突都必须释放 scope。

## 4. 主题与排序

建立唯一的 `LearnerMemorySectionCatalog`，供 bootstrap、工具和 projector 复用：

- 学习背景与目标。
- 学习方式与条件。
- 解题与实现。
- 复盘与成长。
- 知识点表现。

排序优先级固定为：相关用户自述、当前题 tag、当前消息规范化文本匹配的通用观察、grade、最近确认/更新时间、revision ID。

第一版匹配使用当前用户最多 1000 条 ACTIVE claim 的结构化过滤和规范化 PostgreSQL/Java 文本匹配，不引入向量或中文分词基础设施。

## 5. Bootstrap Builder

- 配置改为 `algo-mentor.learner-memory.recall.practice-chat.bootstrap-token-budget`，默认 1000，setter/validator 硬拒绝超过 1500。
- 输出工具/记忆使用边界占位、自然主题索引和最多 8 条直接命中 claim；有足够内容时目标 3 至 8 条，不为凑数注入无关项。
- 索引包含 sectionRef、标题、claim 数、最近更新时间和当前题命中数。
- 每条直接命中只带完整 claim 和低成本来源摘要。
- 裁剪单位是完整索引项或完整 claim；禁止旧 provider 的 substring 半句截断。
- 相同 snapshot、locale 和预算生成字节级稳定文本与 metadata。

在 `AMR-09` 前，Prompt 不宣称不存在的工具可用；先只说明当前注入是长期记忆的有界视图。

## 6. 实施步骤

1. 新建 section catalog、snapshot model、document revision 和 direct-hit selector。
2. 扩展 run scope registry 支持 recall lease、opaque refs 和并发只读。
3. 实现 bootstrap builder 和完整项预算 planner。
4. 调整 conversation service/run adapter，把 snapshot、scopeRef 和 release 生命周期接入 Practice Chat。
5. 替换旧 profile section/config/metadata，保留读取失败降级为空且不阻断聊天。
6. 增加稳定性、预算、并发可见性、replay 和资源释放测试。

## 7. 重点测试

- 相同 ACTIVE 集合生成稳定 document revision、section order 和 direct-hit order。
- 默认不超过 1000 token，配置超过 1500 启动失败；无半句截断。
- 当前题 tag、用户自述、通用观察和 grade 优先级。
- 直接命中不超过 8；无内容时不生成推断正文。
- 并发更新不改变当前 scope，下一个 run 才看到新 revision。
- 查询异常降级为空；scope 无泄漏；replay 不重复打开。
- request metadata 和日志不包含 claim 文本。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='*LearnerMemory*Recall*Test,*LearnerMemory*Bootstrap*Test,AgentConversationServiceTest,PracticeChatRunAdapterTest' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test='*LearnerMemory*RecallIT' verify

git diff --check
```

## 9. 非目标与停止条件

- 不实现 search/section/evidence 工具，不改 `/me` API。
- 若 scope 内容进入 metadata、bootstrap 仍截半句或并发更新污染当前 run，不得开始 `AMR-09`。

## 10. 上下文交接

记录 snapshot/lease 类型、section catalog、scope metadata key、bootstrap 配置和稳定排序键。不要复制 bootstrap Prompt 文本。

## 11. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增 `LearnerMemoryRecallSnapshot`、固定 `LearnerMemorySectionCatalog`、稳定 document revision 与直接命中排序；全量 claim 仅保留在 run-local recall lease。
- `LearnerMemoryRunScopeRegistry` 增加随机 recall scope、section/statement opaque ref、显式/过期清理；Practice Chat 将 lease 交给 Runtime 资源并在 replay 路径不新建 scope。
- bootstrap 以完整索引项和完整 claim 裁剪，默认预算 1000、硬上限 1500；metadata 仅记录 scope/document ref、数量和预算统计。
- 删除旧 LearnerProfile recall 服务、Prompt provider、配置和测试，新增 V46 claim 查询的 PostgreSQL recall IT。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest='*LearnerMemory*Recall*Test,*LearnerMemory*Bootstrap*Test,AgentConversationServiceTest,PracticeChatRunAdapterTest' test`：PASS（13 项）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest='AgentConversationApiAutoConfigurationTest,LearnerMemoryRecallPropertiesTest' test`：PASS（18 项）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoMatchingUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*LearnerMemory*RecallIT' verify`：PASS（PostgreSQL IT 1 项）。

偏离计划：

- 自动配置初版依赖同配置内 registry bean 的条件顺序，导致无 Review 历史时不会创建 recall service；已收敛为只要求 claim 查询基础设施。

遗留事项：

- 无；`AMR-09` 接入三项 Practice Chat 记忆工具与其预算。

下一任务：`AMR-09`
