# AMR-02：Repository、查询、容量与快照原语

> 波次：A
>
> 状态：PENDING
>
> 直接依赖：AMR-01
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

为新表实现 mentor-application 端口、mentor-api MyBatis 适配、稳定查询、容量统计和用户级 snapshot token。完成后后续服务不需要直接调用 Mapper，也不依赖数据库默认顺序。

本任务只提供读写原语，不编排 operation，不调用模型，不切换旧入口。

## 2. 必须读取

- `CURRENT.md`、`AMR-01` 完成备注和新建领域模型。
- `CONTRACTS.md` 第 2 至 6 节。
- 旧 `LearnerProfileRepository.java`、`LearnerProfileQueryService.java`、`MyBatisLearnerProfileRepository.java`。
- `LearnerProfileMapper.java`、`LearnerProfileMapper.xml`，只参考 MyBatis 风格。
- `MentorApiMyBatisConfiguration.java` 的 Mapper/Repository 装配片段。
- `MyBatisCodeReviewProfileFactRepository.java` 的 JSON 映射模式。
- `LearnerProfileMapperXmlTest`、`LearnerProfileConcurrencyIT` 的相关测试模式。

## 3. 端口边界

建议建立三个端口：

- `LearnerMemoryClaimRepository`：ACTIVE/current/history、scope 查询、计数、锁用户和 revision 写原语。
- `LearnerMemoryEvidenceRepository`：按 revision 查询和插入 Review/message evidence，按来源校验归属。
- `LearnerMemoryUpdateRunRepository`：创建、幂等查找、关联触发 Review、绑定 Agent run 和更新终态。

Repository 必须支持：

- 按用户读取全部或指定 scope 的 ACTIVE claim，并使用显式稳定顺序。
- 按 revision ID 批量重读当前 ACTIVE claim。
- 查询单 claim 历史和自包含 evidence。
- 用户 ACTIVE 总数、kind/dimension/tag scope 数量和 `NORMAL / SOFT_LIMIT / HARD_LIMIT`。
- 锁定 `auth_users` 用户行；首次 `ADD` 不能只依赖不存在的 claim 行锁。
- 插入 revision、把旧当前 revision 标记为 `SUPERSEDED`、插入两类 evidence。
- 创建/查询 update run 幂等键和稳定的 5 条触发 Review 顺序。

## 4. Snapshot 与 hash

- `claim_text_hash` 统一由应用层规范化后计算：trim、连续空白收敛、UTF-8、SHA-256 小写 hex。
- 用户级 snapshot token 基于有序当前 revision 的 `id + claimKey + revisionNo + status + updatedAt` 计算；空集合也有稳定 token。
- scope snapshot 保留当前完整 claim 列表，不再假设一个 scope 只有一条 ACTIVE。
- Repository 返回顺序必须显式包含 kind、dimension、tag、更新时间和 revision ID 的平局规则。
- snapshot token、文本 hash 和 document revision 使用不同类型，禁止都以裸 `String` 混用。

## 5. 目标代码区域

```text
backend/mentor-application/.../profile/claim/repository/
backend/mentor-application/.../profile/claim/service/
backend/mentor-application/.../profile/evidence/repository/
backend/mentor-application/.../profile/run/repository/
backend/mentor-api/.../profile/mapper/
backend/mentor-api/.../profile/repository/
backend/mentor-api/src/main/resources/mapper/profile/
```

新 Mapper 使用独立 `LearnerMemoryMapper` 和 XML，避免继续扩大旧 `LearnerProfileMapper`。

## 6. 实施步骤

1. 定义端口和 query model，先用内存 fake 固定排序、hash、snapshot 和容量语义。
2. 新增 Mapper、row model 和 XML，逐项映射五张新表。
3. 实现 PostgreSQL adapter 和用户行锁。
4. 增加稳定批量查询，避免逐 claim N+1 读取 evidence 和 tag。
5. 接入 MyBatis/Spring 装配，但不替换旧 Bean。
6. 增加 XML statement 完整性、Repository 单测和 PostgreSQL IT。

## 7. 重点测试

- ACTIVE 与当前终态、历史 SUPERSEDED 的过滤边界。
- 同 scope 多 ACTIVE claim 的完整读取和稳定排序。
- 500/1000 软硬状态以及 10/10/5 scope 计数。
- 用户隔离、tag 过滤、批量 revision 读取无越权。
- snapshot token 对顺序稳定，对增删改敏感。
- Review/message evidence 顺序为业务时间 + source ID，不信任插入顺序。
- 用户行锁和 Mapper 写原语加入调用方事务。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am -Dtest='*LearnerMemory*Repository*Test,*LearnerMemory*Snapshot*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='*LearnerMemory*Mapper*Test' -Dit.test='*LearnerMemory*RepositoryIT' verify

git diff --check
```

## 9. 非目标与停止条件

- 不实现 evidence pattern 语义校验、operation apply、Agent 或 API。
- Repository 出现 N+1 全量 evidence 查询、依赖默认 SQL 顺序或绕过用户锁时不得开始 `AMR-03`。

## 10. 上下文交接

记录端口名、Mapper namespace、snapshot/hash 类型、稳定排序键和容量查询方式。不要携带 Mapper XML 全文。

## 11. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增 Claim、Evidence、Update Run 三个端口的 MyBatis 适配、独立 `LearnerMemoryMapper` 与行模型。
- 接入 Spring/MyBatis 装配；提供规范化 SHA-256、稳定 snapshot 和容量查询原语，未切换旧画像 Bean。
- 覆盖 XML、用户隔离、批量读取、来源时间排序、幂等 run、触发 Review 顺序与用户行锁的测试。

验证：

- `-pl mentor-application -am -Dtest='*LearnerMemory*Repository*Test,*LearnerMemory*Snapshot*Test' test`：PASS。
- `-pl mentor-api -am -Dtest='*LearnerMemory*Mapper*Test' -Dit.test='*LearnerMemory*RepositoryIT' verify`：PASS。
- `-pl mentor-application,mentor-api -am verify`、`git diff --check`：PASS。

偏离计划：

- 首轮为完成新领域模型、旧 Mapper/Repository 范例与 PostgreSQL 集成测试接线，必要地超过建议文件上限；未扩大到后续任务实现。

遗留事项：

- 无。

下一任务：`AMR-03`
