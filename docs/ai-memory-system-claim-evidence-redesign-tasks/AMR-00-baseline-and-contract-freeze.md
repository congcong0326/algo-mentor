# AMR-00：基线、版本与契约冻结

> 波次：A
>
> 状态：PENDING
>
> 直接依赖：无
>
> 建议首轮文件上限：8

## 1. 目标与完成标准

在修改业务代码前，重新验证当前仓库、测试、Flyway 版本和功能开关，冻结后续任务使用的实现契约。

完成后必须能够回答：当前基线是否通过、下一个可用迁移版本是什么、哪些旧类/SQL/topic 必须在最终清零、是否存在会改变任务主链的代码冲突。

本任务不修改业务行为，不创建迁移，不顺手修复无关问题。

## 2. 必须读取

- `CURRENT.md`、`README.md`、`CONTRACTS.md`。
- `docs/ai-memory-system-claim-evidence-redesign.md`，这是唯一默认允许完整读取设计原文的任务。
- `docs/ai-memory-system-current-state-audit.md`。
- `docs/code-index.md` 的记忆系统、Agent Runtime、持久化队列和前端条目。
- `backend/mentor-api/src/main/resources/application.yml`、`application-local.yml` 的相关配置片段。

先用 `rg` 定位，禁止通读生成目录：

```bash
rg -n 'learner_profile_entry|learner-profile\.code-review\.v1|LearnerProfile|CodeReviewProfile' \
  backend frontend docs/code-index.md
find backend -path '*/src/main/resources/db/migration/*.sql' -type f | sort -V
```

## 3. 基线检查

1. 记录 `git status --short` 和 `git diff --stat`，区分既有用户改动与后续实现改动。
2. 扫描全仓 Flyway 版本，记录当前最大版本和为 `AMR-01` 预留的候选版本；每次真正创建迁移前仍需重扫。
3. 执行现状审计中的后端相关测试和前端相关测试。
4. 记录旧表、旧 topic、旧 DTO、旧 Prompt/Schema、旧配置前缀的源码命中数量，不复制命中全文。
5. 核对 `CONTRACTS.md` 与代码事实；只修正文档中的事实错误，不重新讨论已经冻结的产品方向。

## 4. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,persistent-queue,mentor-api -am \
  -Dtest='*LearnerProfile*,*DeclaredProfile*,*CodeReviewProfile*,Queue*,PersistentQueue*,AgentConversationServiceTest,PracticeChatAgentDefinitionTest' test

npm --cache ./.npm --prefix frontend test -- \
  src/MyPage.test.tsx \
  src/services/api.test.ts \
  src/learning-plans/PracticeChatWorkbench.test.tsx \
  src/App.test.tsx \
  src/app/AppShell.test.tsx

git diff --check
```

若某条测试因非本任务已有改动失败，记录测试名、错误类别和最小证据，不修改无关代码。

## 5. 出口与停止条件

- 基线结果、候选迁移版本和旧实现命中计数已写入 `CURRENT.md`。
- `CONTRACTS.md` 没有字段数量、状态或依赖关系错误。
- 没有需要用户补充的产品决策；实现细节冲突已经分配到具体后续任务。
- 基线无法编译、数据库迁移版本冲突或关键设计输入缺失时不得开始 `AMR-01`。

## 6. 上下文交接

交接只记录测试结果摘要、当前最大迁移版本、候选新版本、旧引用计数和已有用户改动路径。不要带入设计原文摘录。

## 7. 完成备注

完成时间：2026-07-30T09:26:28Z

状态：DONE

主要改动：

- 扫描全仓迁移，当前最大版本为 `V45`，为 `AMR-01` 预留候选 `V46`，未创建迁移。
- 冻结五张新表、两段式切换和旧数据不回填契约；未发现改变主链的代码冲突。
- 记录旧实现基线：表 47、v1 topic 1、旧 DTO/视图 264、Review 画像符号 270、Prompt/Schema 77、配置 4（匹配行）。

验证：

- `mvn -f backend/pom.xml ... -Dtest='*LearnerProfile*,...' test`：PASS（74 tests）。
- `npm --cache ./.npm --prefix frontend test -- ...`：PASS（5 files，161 tests）。
- `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无；创建迁移前按任务要求重新扫描版本。

下一任务：`AMR-01`
