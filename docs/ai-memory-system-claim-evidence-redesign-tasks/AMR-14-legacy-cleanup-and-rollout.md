# AMR-14：旧模型清理、最终迁移与发布收口

> 波次：E
>
> 状态：DONE
>
> 直接依赖：AMR-13
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

执行第二阶段破坏性迁移，删除 `learner_profile_entry`、v1 topic 和旧画像代码/配置/前端契约，完成发布顺序、关闭式回滚和仓库级最终门禁。

本任务完成后生产源码只存在 learner memory claim/evidence v2 路径；旧数据不回填、不双写、不提供兼容视图，应用可通过关闭新功能开关止损，但不能回滚到依赖旧表的二进制。

## 2. 必须读取

- `CURRENT.md`、`AMR-13` 完成备注和 `AMR-00` 记录的 Flyway 版本分配。
- `CONTRACTS.md` 第 1、5、6、12 节。
- 实施时重新执行 Flyway 全仓版本扫描，不信任文档中的旧最新版本。
- `V34__learner_profile_entry.sql`，只读且禁止修改。
- `AMR-01` 创建新表的迁移和最新 mentor-api/queue/agent migrations。
- v2 publisher/consumer、declared、recall、projection 的实际配置 properties 和默认值。
- `application.yml`、`application-local.yml` 中 learner profile/memory 片段。
- `ApiContractConstants`、MyBatis 自动配置和 profile controller 装配。
- 前端 `types/api.ts`、`services/api.ts`、`MyPage.tsx` 和画像组件的最终引用。
- `docs/code-index.md` 中旧画像索引和本任务目录说明。

首轮先运行 `rg` 得到旧符号文件列表；只打开实际命中的生产文件，不按旧计划清单逐个预读。

## 3. 最终 Flyway 迁移

使用扫描得到的下一个全局唯一版本新增迁移，不修改 V34 或 `AMR-01` 迁移：

1. 删除 `queue_message` 中 topic 为 `learner-profile.code-review.v1` 的全部状态数据；SQL 使用精确 topic 常量值，不使用模糊匹配。
2. `DROP TABLE learner_profile_entry`；不备份、不回填、不创建兼容 view。
3. 不重建五张 learner memory 新表，不改变已上线的新 revision/evidence 数据。
4. 对不存在旧表/无 v1 消息的清洁环境保持可迁移；是否使用条件语句由当前 Flyway 测试惯例决定并固定测试。

迁移测试至少覆盖：V34 旧库 + AMR-01 新表 -> 最终版本、从空库迁到最新、已有新 claim/evidence 保留、v1 queue 清零、旧表不存在。

## 4. 后端旧实现清单

基于实际 `rg` 命中删除或替换，不机械照抄文件名。范围至少包括：

- 旧 `LearnerProfileEntry/Identity/Snapshot/Repository/QueryService/UpdateService` 及 `ProfileUpdate*` 聚合正文模型。
- 旧 `LearnerProfileMapper`、row/view model、XML 的 `learner_profile_entry` statement 和 repository adapter。
- 旧分类数组 DTO、view service 和 controller 测试 fixture；保留同路径的新 document API。
- 旧 800 token `LearnerProfileRecallService/PromptSectionProvider/Policy/Resolver` 和旧 section metadata/config key。
- v1 `CodeReviewProfile*` topic、event、consumer、Prompt、Schema、mapper、metrics 和自动配置别名；保留/重命名后的 v2 learner memory 实现。
- declared 链路中只服务 `NO_CHANGE / REPLACE`、dimension 整段正文或旧 Prompt/schema 的类型。
- `LearnerProfileProperties`、`LearnerProfileRecallProperties`、`LearnerProfileAgentProperties`、`CodeReviewProfileConsumerProperties` 中已被 learner-memory 配置取代的字段和环境变量。

删除前逐个确认无新代码仍复用该类型；公共工具生命周期状态、`update_learner_declared_profile` 产品工具名和 `/api/me/learner-profile` 产品路径按固定契约保留。

## 5. 前端与配置清理

- 删除旧 `LearnerProfileEntry`、dimension/category/tab 类型和相关 i18n 文案、CSS、测试 fixture。
- `MyPage.tsx` 不残留旧分类、preview count、MarkdownView 或 entry card 分支。
- 删除仅为 v1 topic、旧 recall token budget、旧 prompt/schema version 服务的 YAML/env 配置。
- 新 learner-memory 开关默认保持关闭，数值预算与 `CONTRACTS.md` 一致；启动校验覆盖非法上限和缺少必要安全配置。
- 更新 `docs/code-index.md` 的代码路径说明为 claim/evidence、recall tools 和 projection 现状；历史设计文档可保留 v1 名称作为历史，不伪造“全仓文本零命中”。

## 6. 源码零引用门禁

对活动源码和配置执行精确扫描，允许名单只包含不可修改的历史 migration/测试基线或明确历史文档：

```bash
rg -n 'learner_profile_entry|learner-profile\.code-review\.v1|LearnerProfileEntry|CodeReviewProfile' \
  backend frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**'
```

- 每个剩余命中都必须在完成备注中说明并具有必要性；生产 Java/TypeScript/YAML/XML 不允许遗留旧读写路径。
- 再扫描旧 JSON 字段 `declaredFacts/generalObservations/tagAssessments/contentText/revisionNo`，区分其他业务同名字段后处理。
- 新 v2 topic、API path、tool name、metadata key、document format 和 query key 只从常量/枚举引用。

## 7. 发布顺序

新增中文发布 runbook，固定以下顺序：

1. 确认所有 learner-memory 写入、consumer 和 recall 开关关闭；暂停 queue worker，暂不把新前端静态产物同步到后端发布包。
2. 备份仅用于基础设施事故恢复，不承诺恢复旧画像业务数据；记录当前 v1 queue 数和新表健康检查。
3. 部署包含最终 migration 的新二进制并完成数据库升级；验证旧表/topic 清理和新表约束。
4. 先开启 declared 与 v2 Review 写入，使用测试用户验证 update run/claim/evidence，不开启 recall。
5. 开启 shadow/内部画像 API 验证，再开启 Practice Chat recall 工具。
6. 最后同步并发布包含 `/me` 新画像入口的前端静态产物，观察错误率、预算、stale、projection 和跨用户安全告警。

项目未上线的前提不取消这些检查，只允许把观察窗口缩短并记录实际执行方式。

## 8. 关闭式回滚

- 回滚动作是关闭 declared、v2 consumer 和 recall，停止新增记忆读写；已经发布的画像 API/页面保持只读，必要时通过仍兼容新 schema 的前向补丁隐藏入口。
- 保留五张新表、update run、claim 和 evidence；不删除新数据，不恢复旧表，不回填旧正文。
- 不部署依赖 `learner_profile_entry` 的旧二进制；需要代码回退时必须从仍支持新 schema 的发布提交生成修复版本。
- queue v2 中未消费消息按当前最多一次语义处理，不改造成临时重试/DLQ。
- runbook 给出开关、健康查询、观察指标、触发阈值和恢复开启顺序，不包含密钥或真实用户数据。

## 9. 最终测试与验收

- 新旧 migration 全量升级和 clean install 通过；V34 文件校验未变化。
- declared、5 条 Review、recall 工具、projector、前端文档和 Review 深链完整 E2E 通过。
- 默认关闭配置下应用可启动、普通聊天不受影响；按顺序开启后功能工作。
- 旧表、v1 queue 和活动源码引用清零；新表数据在最终 migration 后完整。
- API 不再返回旧数组字段，前端 bundle 不包含旧 tab/claim card 文案。
- 日志、指标和诊断快照安全门禁保持通过。

## 10. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dit.test='*LearnerMemory*FullUpgradeIT,*LearnerMemory*CleanInstallIT,*LearnerMemory*EndToEndIT' verify

make backend-test
make backend-build
make frontend-test
make frontend-build

rg -n 'learner_profile_entry|learner-profile\.code-review\.v1|LearnerProfileEntry|CodeReviewProfile' \
  backend frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**'

git diff --check
```

`rg` 命令预期只返回经过审核的历史 migration/test allowlist；生产源码命中视为失败。不要把完整构建日志写入任务备注。

## 11. 非目标与停止条件

- 不修改 V34，不恢复旧数据，不保留兼容视图，不新增新旧双写。
- 不把“可回滚”解释为重新创建旧表或部署旧二进制。
- 若最终 migration 未覆盖升级/空库、活动源码仍读写旧表/topic、默认关闭无法启动、最终门禁失败或 runbook 缺少关闭式回滚，不得标记整个重构完成。

## 12. 上下文交接

这是最后一个任务。记录最终 migration 版本、删除清单、允许的历史命中、runbook 路径、四个仓库级命令和 E2E 结果；不要复制构建日志、数据库行内容或配置密钥。

随后把 `AMR-14` 和状态板标记为 `DONE`，将 `CURRENT.md` 更新为“全部完成”，不再指定下一任务。

## 13. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增 `V49__remove_legacy_learner_profile_storage.sql`，精确删除 v1 queue 消息并以前向迁移删除旧表。
- 删除旧画像实体、Mapper、DTO、分类视图、v1 Review consumer/Prompt/配置及兼容读写；Practice Chat recall 改为默认关闭。
- 新增发布与关闭式止损手册 `docs/ai-memory-system-rollout-runbook.md`，并更新代码索引。
- 全功能应用上下文测试显式启用 recall；默认关闭状态由自动配置测试独立覆盖。

验证：

- LearnerMemory 最终 migration/E2E Failsafe 选择器：PASS（6 类、15 项）。
- `make backend-test`：PASS（286 项）；`make backend-build`：PASS。
- `make frontend-test`：PASS（48 files、368 tests）；`make frontend-build`：PASS。
- 旧符号/旧 JSON 字段扫描和 `git diff --check`：PASS。

偏离计划：

- 无；仅补齐全功能应用上下文测试的显式 recall 开关，使其与默认关闭契约一致。

遗留事项：

- 允许的旧符号仅在 V34/V47/V49 历史迁移，以及断言最终删除行为的 PostgreSQL 回归测试；其余 JSON 命中属于学习计划 revision 或通用 tool blob 契约。

下一任务：无，进入最终交付。
