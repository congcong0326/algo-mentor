# RDP-04：调用台账迁移、终态快照与低基数指标

> 波次：A
>
> 状态：DONE
>
> 直接依赖：RDP-01、RDP-02
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

为调用级台账增加可空 `reasoning_effort`，让同步、流式、失败和取消统一保存调用开始时的最终 effort，并给 provider 调用指标增加低基数 effort tag。

完成后 accounting 和 metrics 与 `DynamicLlmGateway` 复用同一个生效值解析器，不会因为 wrapper 顺序、路由热更新或终态响应而记录不同值。

## 2. 必须读取

- `CURRENT.md`、`RDP-01`/`RDP-02` 完成备注和 `CONTRACTS.md` 第 6、13 节。
- `AiAccountingLlmGateway`、`AiLlmCallAccountingService`、`AiLlmCallUsage`、row/update model。
- `AiLlmCallUsageMapper.xml` 和 `AiAccountingLlmGatewayTest`。
- `AiProviderCallMetricsLlmGateway` 及其测试。
- `V29`、`V32`、`V41` AI governance migration 和 migration resource tests。
- `AiProviderModelMigrationIT` 与 PostgreSQL IT 基类。

创建迁移前重新执行：

```bash
find backend -path '*/src/main/resources/db/migration/*.sql' -type f -print | sort -V

rg -n 'AiLlmCallUsage(Row|Update)?|ai_llm_call_usage|AiProviderCallMetricsLlmGateway' \
  backend/ai-governance backend/mentor-api --glob '*.java' --glob '*.xml' --glob '*.sql'
```

## 3. Flyway 迁移

使用扫描得到的下一个全仓唯一版本新增向前迁移：

- `ALTER TABLE ai_llm_call_usage ADD COLUMN reasoning_effort VARCHAR(16) NULL`。
- 增加 check，允许七个 wire value 或 `NULL`。
- 不回填历史记录，不修改 V29/V32/V41，不增加默认值。
- clean install 和已有数据升级都必须通过。
- DeepSeek 不需要额外表结构；本列同时承载未来 `deepseek` 值。

## 4. 调用开始快照

- `AiLlmCallUsage` 或其 context/row 增加可空强类型 effort，持久化时写 wire value。
- `AiLlmCallAccountingService.start(request)` 使用 `RDP-01` 唯一解析器计算最终 effort。
- complete/fail/cancel 复用 start 返回的快照，终态 update 不覆盖 effort。
- mapper insert 写入 effort；终态 update 不需要再次更新该列。
- request、target 或 context 缺失时沿用现有降级，effort 为 `null`，不得抛出新的持久化阻断。

## 5. 指标

`AiProviderCallMetricsLlmGateway` 在开始调用时增加 `reasoning_effort` tag：

- `null -> provider_default`。
- 非空 -> wire value。
- complete、stream、失败、取消使用同一 observation 快照。
- 保留现有 `provider_type` 和 `status`；不增加实例、模型、用户或路由维度。

如果 active gauge 增加 effort 维度会产生额外固定组合，必须确认总 cardinality 有界；也可以只在 terminal counter 增加 effort，但实现与测试需明确固定。

## 6. 重点测试

- migration 升级已有历史行，旧行 effort 为 `NULL`。
- 七值插入成功，未知值被数据库约束拒绝。
- 同步成功、同步失败、流式成功、流式 error、订阅前失败和取消都保存 start effort。
- route `high`、request `none` 时台账和 metric 都记录 `none`。
- 调用开始后修改 route/target 外部状态不改变已有台账快照。
- accounting 持久化失败仍按现有策略降级，不泄露 request/config。
- metric tag 只出现 allowlist 值，空值为 `provider_default`。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance -am \
  -Dtest='AiAccountingLlmGatewayTest,AiLlmCallAccountingServiceTest,AiProviderCallMetricsLlmGatewayTest,AiGovernanceMigrationResourceTest,AiGovernanceMapperXmlTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=NoUnitTestsSpecified \
  -Dit.test='*AiReasoningEffortMigrationIT,*AiProviderModelMigrationIT' verify

git diff --check
```

## 8. 非目标与停止条件

- 不按 effort 新增管理聚合报表，不改价格计算，不重复计算 reasoning token 成本。
- 不回填历史 effort，不在终态重新解析路由。
- 若失败/取消丢失 effort、metric 与台账使用不同解析逻辑、migration 不能升级已有数据或 tag 出现高基数值，不得开始 `RDP-05`。

## 9. 上下文交接

记录 migration 版本、row 字段、start snapshot 入口、metric tag allowlist 和测试结果。不要记录数据库实际调用行或用户/模型标识。

## 10. 完成备注

完成时间：2026-08-03 10:06 UTC

状态：DONE

主要改动：

- 新增 `V54__ai_llm_call_usage_reasoning_effort.sql`，为台账增加可空列和七值 check constraint。
- 调用开始阶段使用统一 resolver 固定强类型 effort；成功、失败、取消终态均复用该快照，MyBatis 仅在 insert 写入。
- provider metrics 的 gauge/counter 增加固定 `reasoning_effort` tag；空值为 `provider_default`。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am -Dtest='AiAccountingLlmGatewayTest,AiLlmCallAccountingServiceTest,AiProviderCallMetricsLlmGatewayTest,AiGovernanceMigrationResourceTest,AiGovernanceMapperXmlTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（21 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoUnitTestsSpecified -Dit.test='*AiReasoningEffortMigrationIT,*AiProviderModelMigrationIT' verify`: PASS（2 ITs）
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-05`
