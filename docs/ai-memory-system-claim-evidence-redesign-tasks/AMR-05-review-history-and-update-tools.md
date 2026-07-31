# AMR-05：Review 历史事实、纵向轨迹与更新工具

> 波次：B
>
> 状态：DONE
>
> 直接依赖：AMR-02
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

建立 Code Review 更新 Agent 所需的受信历史读取能力、确定性轨迹/差异服务、三项只读工具和 run-local 更新 scope。完成后工具可以独立测试，但旧 Code Review Profile Definition 仍不暴露这些工具。

## 2. 必须读取

- `CURRENT.md`、`AMR-02` 完成备注和新 claim query 端口。
- `CONTRACTS.md` 第 4、7、12 节。
- `CodeReviewProfileFact.java`、`CodeReviewProfileFactRepository.java`、`MyBatisCodeReviewProfileFactRepository.java`。
- `PracticeCodeReview.java`、`PracticeCodeReviewRepository.java`。
- `PracticeCodeReviewMapper.java`、`PracticeCodeReviewMapper.xml` 中 profile fact 和 detail 查询片段。
- `V13__practice_code_review_schema.sql`、`V36__practice_code_review_tags.sql`。
- `AgentTool.java`、`AgentExecutionContext.java`、`AgentRunResource.java`。
- `MentorAiConfiguration.java` 的全局工具注册和 Definition 校验片段。

## 3. 受信历史查询

扩展或新建独立端口，支持：

- 按用户和允许 problem slug 读取最近 5 个正式 Review，版本升序。
- 按用户和 review ID 读取 detection evidence、context summary、扣分原因、建议、评分、标签和必要定位字段。
- 只在内部读取两个 Review 的 normalized code，用于生成有界 diff；工具结果不得返回两份完整代码。
- 批量复核 Review 的用户、slug、tag、version 和创建时间，供后续 evidence validator 使用。

所有 SQL 必须同时带用户范围，不允许先按裸 review ID 读取后仅在 Java 中过滤。

## 4. 确定性派生服务

- `ReviewTrajectoryService` 计算相邻版本 score delta、持续 finding、已解决 finding 和新 finding。
- `SubmissionVersionDiffService` 使用成熟 Java diff 库生成 unified diff；在根 `backend/pom.xml` 统一管理版本，在 `mentor-application` 声明依赖，不手写 diff 算法。
- diff 只基于 normalized code，最大 8000 字符，超出时以完整 hunk 为裁剪单位并返回 `truncated=true`。
- finding 集合使用规范化字符串和稳定排序；不能依赖 JSON 数组原始顺序来判断相同项。

## 5. Run-local 更新 Scope

新增线程安全、显式释放的 `LearnerMemoryRunScopeRegistry` 或等价类型：

- `openUpdateScope` 保存 userId、允许 slug、横向 Review ID、工具预算和必要受信事实，返回随机不可枚举 `scopeRef` 与 lease。
- request metadata 只保存 `scopeRef` 和计数，不保存完整 Review、代码或 claim。
- 工具通过 `scopeRef` 查找当前 scope，不接受 user ID。
- lease 必须可与 `AgentRunResource` 组合，在成功、失败、取消、提交拒绝和 replay 路径释放。
- registry 缺失、过期或已释放时工具返回稳定失败，不回退为无界数据库查询。

## 6. 三项工具

- `get_problem_review_trajectory`：参数只有 `problemSlug`；slug 必须属于 scope；每 slug 最多一次。
- `get_code_review_evidence`：参数只有 `reviewId`；Review 必须属于当前用户和允许 slug；输出有字符上限和 `truncated`。
- `compare_submission_versions`：两个 Review 必须同题、有序且属于 scope；每 run 最多一次。

公共工具名、参数名、结果字段、metadata key 和失败码集中到 `LearnerMemoryAgentToolContracts`。

更新 scope 统一限制 3 次工具调用；超出时返回受控 `BUDGET_EXHAUSTED` 结果，不扩大查询范围，也不把异常正文抛给模型。

## 7. 实施步骤

1. 先扩展受信 query port、Mapper 和 row model，固定跨用户拒绝。
2. 实现 trajectory/diff 纯服务和确定性单测。
3. 实现 scope registry、lease 和预算计数。
4. 实现三项 AgentTool、严格 schema 和结果限长。
5. 条件注册工具 Bean；不修改任何现有 Definition 的 allowed tools。
6. 增加 Mapper、工具越权、预算、释放和并发 scope 测试。

## 8. 重点测试

- trajectory 最多 5 版、升序、相邻 delta 和 finding 变化稳定。
- 跨用户、非窗口 slug、伪造 review ID、跨题 diff、逆序版本全部拒绝。
- 工具结果不含 raw code、完整 normalized code 或 review Markdown。
- diff 以完整 hunk 裁剪并保持合法 unified diff 头。
- 同一 scope 3 次调用、trajectory 每 slug 一次、diff 一次边界。
- scope release 后不可读取；并发 run 不共享计数或事实。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='*ReviewTrajectory*Test,*SubmissionVersionDiff*Test,*LearnerMemory*Tool*Test,*RunScope*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test='*CodeReview*Memory*ToolIT' verify

git diff --check
```

## 10. 非目标与停止条件

- 不改 Agent Prompt/Schema，不创建 update run，不应用 claim。
- 若工具可通过参数传 user ID、可读取 scope 外 Review 或 scope 没有可靠释放，不得开始 `AMR-06`。

## 11. 上下文交接

记录 query 端口、三项工具名、scope metadata key、diff 库和预算结果。不要带入 Review 内容或 diff 样例。

## 实施子检查点

- 状态：通过
- 范围：`CodeReviewHistoryRepository`、用户范围 SQL、历史/详情/内部规范化代码与批量复核映射。
- 验证：`mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -DskipTests compile`：PASS。
- 后续：实现受限 diff、轨迹、run-local scope 与三项只读工具；不得在工具结果返回代码或完整 Review Markdown。

## 12. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增用户范围的 Review 历史端口、MyBatis 查询和批量复核；工具不会按裸 Review ID 回退读取。
- 使用 `java-diff-utils` 生成最多 8000 字符的完整 hunk unified diff，并计算稳定的纵向 finding/score 变化。
- 新增随机 scopeRef、lease、释放/过期和三次本地预算；三项只读工具均不接受用户 ID，且未加入现有 Definition。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest='*ReviewTrajectory*Test,*SubmissionVersionDiff*Test,*LearnerMemory*Tool*Test,*RunScope*Test' test`：PASS（9 项）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoMatchingUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='CodeReviewMemoryToolIT' verify`：PASS（PostgreSQL IT 1 项）。
- `git diff --check`：PASS。

偏离计划：

- Mapper IT 暴露 detail row 的 MyBatis 构造参数顺序问题，已在本任务内修复；为隔离 Failsafe 目标，API 验证跳过了无关 Surefire 用例。

遗留事项：

- `AMR-06` 必须在 Code Review 更新 Agent 创建时持有 scope lease，并在成功、失败、取消、拒绝和 replay 路径释放。

下一任务：`AMR-06`
