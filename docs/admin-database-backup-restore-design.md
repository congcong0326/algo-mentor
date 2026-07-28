# 管理员全表数据备份与覆盖恢复技术设计

> 设计日期：2026-07-28
> 适用范围：Algo Mentor 管理后台、单实例 PostgreSQL 部署
> 技术基线：Java 17、Spring MVC、PostgreSQL 16、Flyway、React + TypeScript

---

## 0. 已定决策

本文以以下讨论结论作为第一版实现基线：

1. 备份只导出当前应用数据库中全部表的数据和序列值，不导出数据库结构。
2. 恢复固定清空全部表，再导入备份中的全部表数据，不提供合并、追加、跳过或选择表。
3. 备份与目标必须使用相同应用版本、相同 Flyway 迁移记录和相同表集合，不一致直接拒绝。
4. 备份文件不加密、不设置密码。
5. 管理员每天手动下载备份，服务端不建设定时备份、备份历史或远程存储。
6. 恢复前由运维断开外部流量，并确认没有运行中的 Agent、SSE、队列消费或其他写任务。
7. 应用不自动断流、不停止或重启应用，也不重启容器。
8. 恢复不执行 `dropdb`、`createdb` 或 Flyway migration。
9. 清空表、导入数据和恢复序列必须位于同一个 PostgreSQL 事务中，失败时整体回滚。
10. 恢复成功后清空应用缓存，当前管理员重新登录，再由运维恢复外部流量。

## 1. 背景

Algo Mentor 当前需要恢复的数据集中在一个 PostgreSQL 数据库中，包括：

- 用户、角色、密码凭据、OAuth 账号和 Spring Session；
- 题库、标签、学习计划模板和导入记录；
- 用户学习计划、训练进度、练习会话、代码 Review、复习卡和题目笔记；
- Agent 会话、消息、运行记录、工具调用、上下文快照和内容 blob；
- 学习者画像、AI provider、模型、路由、价格、用量和策略；
- 用户组、内测白名单、反馈、管理员审计；
- 持久队列消息和缓存一致性事件。

当前没有业务上传目录、对象存储或独立向量数据库，因此第一版只处理 PostgreSQL 表数据即可满足日常手工备份、服务器重建和同版本数据迁移。

本设计通过明确的运维约束保持实现简单：版本一致由程序校验，断流和后台任务空闲由运维保证。

## 2. 目标与非目标

### 2.1 目标

- 管理员可以下载全部表数据的单个备份文件。
- 管理员可以上传备份并覆盖当前全部表数据。
- 新增表后自动进入备份范围，不维护业务表白名单。
- 恢复前完成精确版本校验。
- 恢复过程具备事务原子性，失败不留下空库或半份数据。
- 恢复后序列值正确，新增数据不会与已恢复主键冲突。
- 恢复后缓存不再返回恢复前数据。

### 2.2 非目标

- 不备份 schema、索引、约束、函数、扩展、数据库角色和权限。
- 不备份 `.env`、数据库密码、OAuth client secret、代码、JAR 或 Docker 配置。
- 不备份 Prometheus、Loki、Grafana 等可观测性数据。
- 不提供增量备份、定时备份、备份保留、云端上传和按时间点恢复。
- 不提供选择性导出、选择性恢复和跨版本数据转换。
- 不自动控制反向代理、负载均衡器、应用进程、容器或后台 worker。
- 不支持多实例同时运行时恢复。

## 3. 数据范围

备份动态包含 PostgreSQL `public` schema 中的全部普通表和分区表，同时保存序列当前值。

第一版不排除运行基础表，因此以下数据也会被原样备份和恢复：

```text
flyway_schema_history
SPRING_SESSION
SPRING_SESSION_ATTRIBUTES
queue_message
cache_invalidation_event
admin_operation_audit
```

不包含以下内容：

```text
数据库和 schema 定义
数据库角色、密码和 ACL
表、索引、约束和触发器定义
Docker volume 物理目录
文件系统、环境变量、日志和指标
```

目标服务器必须先部署相同版本应用，让 Flyway 创建好完全相同的数据库结构，再执行表数据恢复。

## 4. 运维流程

### 4.1 日常备份

备份使用 PostgreSQL 一致性快照，不要求断流：

```text
管理员登录
  -> 打开“数据备份”
  -> 点击“下载全表备份”
  -> 浏览器下载单个备份文件
  -> 管理员保存到受控位置
```

### 4.2 覆盖恢复

```text
确认目标应用版本与备份版本相同
  -> 运维断开外部流量
  -> 保留管理员可访问的内网或管理端口
  -> 等待现有请求结束并确认无后台写任务
  -> 管理员上传备份并确认覆盖
  -> 应用在单事务内清空并导入全部表数据
  -> 应用清空缓存
  -> 管理员重新登录并检查数据
  -> 运维恢复外部流量
```

运维需要确认：

- 没有运行中的 Agent run 和 SSE；
- 没有队列消费者正在处理消息；
- 没有 seed 导入或其他后台写任务；
- 数据库和临时目录有足够空间；
- 管理端口不会被外部用户访问。

第一版不在应用内判断这些运维条件是否满足。

## 5. 备份文件

### 5.1 文件名与内容

文件名使用 UTC 时间：

```text
algo-mentor-data-20260728T120000Z.ambak
```

`.ambak` 是不加密的单个 ZIP 文件：

```text
manifest.json
data.dump
```

- `manifest.json` 保存版本和数据范围信息。
- `data.dump` 是 PostgreSQL custom format 的 data-only dump。

### 5.2 Manifest

```json
{
  "formatVersion": 1,
  "application": "algo-mentor",
  "applicationVersion": "0.1.0-SNAPSHOT",
  "createdAt": "2026-07-28T12:00:00Z",
  "postgresMajorVersion": 16,
  "schema": "public",
  "flywayFingerprint": "sha256:...",
  "tables": [
    "admin_operation_audit",
    "agent_message",
    "auth_users",
    "flyway_schema_history"
  ],
  "tableCount": 58,
  "dumpSizeBytes": 12345678,
  "dumpSha256": "..."
}
```

`tables` 按字典序保存。`flywayFingerprint` 根据 `flyway_schema_history` 中全部成功记录的版本、脚本名和 checksum 计算。

恢复前要求以下内容与当前环境完全一致：

```text
applicationVersion
postgresMajorVersion
flywayFingerprint
tables
```

## 6. 备份实现

后端使用 `ProcessBuilder` 调用 PostgreSQL client，不通过 shell 拼接用户输入。

备份命令语义：

```bash
pg_dump \
  --format=custom \
  --data-only \
  --no-owner \
  --no-privileges \
  --schema=public \
  --file=data.dump \
  "$DATABASE_URL"
```

流程：

1. 查询应用版本、PostgreSQL major version、Flyway 指纹和全部表名。
2. 在临时目录执行 data-only dump。
3. 计算 dump SHA-256。
4. 写入 Manifest。
5. 打包并流式返回浏览器。
6. 请求结束后删除临时文件。

`pg_dump` 使用 MVCC 一致性快照，备份期间不锁住业务写入。

数据库密码通过受控进程环境或临时 passfile 传递，不进入命令参数、响应或日志。

## 7. 恢复实现

### 7.1 恢复前校验

执行数据库修改前必须完成：

1. 校验 ZIP 中只包含 `manifest.json` 和 `data.dump`。
2. 校验 Manifest 格式版本和应用标识。
3. 校验 dump SHA-256。
4. 校验应用版本、PostgreSQL major version、Flyway 指纹和表集合完全一致。
5. 使用 `pg_restore --list` 确认 dump 可读取且只包含 data-only 条目。
6. 校验数据库账号具备恢复所需权限。

任一校验失败都不得执行 `TRUNCATE`。

### 7.2 单事务覆盖

数据库存在循环外键关系，不能依赖普通表插入顺序完成 data-only 恢复。恢复使用独立 `psql` 连接，在一个事务中完成：

```sql
BEGIN;
SET LOCAL session_replication_role = replica;
TRUNCATE TABLE <全部 public 表> RESTART IDENTITY CASCADE;
-- 执行 pg_restore 生成的全部表数据和 sequence setval
SET LOCAL session_replication_role = origin;
COMMIT;
```

实现要求：

- 表名只来自 PostgreSQL catalog，并进行 identifier quoting。
- `pg_restore` 只输出 data-only SQL，不执行 schema DDL。
- `psql` 开启 `ON_ERROR_STOP=1`。
- 任一步骤失败时连接退出，整个事务回滚。
- `session_replication_role = replica` 所需权限在恢复前验证。
- 序列值使用 dump 中的 `setval` 恢复。

### 7.3 Session 与缓存

Spring Session 表会被覆盖。恢复开始后当前管理员 Session 不应在响应结束时重新写回恢复后的数据库；恢复完成后前端清理登录状态并跳转登录页。

恢复事务提交成功后，应用清空当前实例的全部业务缓存。第一版只支持单实例，因此不建设跨节点缓存清理流程。

## 8. API 契约

公共路径常量：

```text
ADMIN_DATABASE_BASE_PATH = /api/admin/database
BACKUP_PATH = /backup
RESTORE_PATH = /restore
```

新增前端能力标识：

```text
database-backup:manage
```

当前 `ADMIN` 角色拥有该能力，后端仍以 `/api/admin/**` 的 `ROLE_ADMIN` 作为最终权限边界。

### 8.1 下载备份

```http
GET /api/admin/database/backup
```

```http
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="algo-mentor-data-20260728T120000Z.ambak"
Cache-Control: no-store
```

响应流式输出，不将完整备份加载到 JVM heap。

### 8.2 覆盖恢复

```http
POST /api/admin/database/restore
Content-Type: multipart/form-data
```

请求字段：

```text
file          备份文件
confirmation  固定值 OVERWRITE_ALL_DATA
```

成功响应：

```json
{
  "success": true,
  "data": {
    "restoredAt": "2026-07-28T12:10:00Z",
    "tableCount": 58,
    "dumpSizeBytes": 12345678,
    "loginRequired": true
  }
}
```

同一实例同一时间只允许一个备份或恢复请求，重复请求返回 `409 Conflict`。

主要错误码：

```text
DATABASE_BACKUP_BUSY
DATABASE_BACKUP_FAILED
DATABASE_RESTORE_ARCHIVE_INVALID
DATABASE_RESTORE_CHECKSUM_MISMATCH
DATABASE_RESTORE_VERSION_MISMATCH
DATABASE_RESTORE_TABLE_SET_MISMATCH
DATABASE_RESTORE_PRIVILEGE_REQUIRED
DATABASE_RESTORE_FAILED
```

## 9. 管理员页面

在“运维管理”模块增加：

```text
数据备份    /admin/database-backup
```

页面只保留两个操作区：

```text
全表备份
[下载全表备份]

覆盖恢复
[选择备份文件]
[覆盖全部数据]
```

交互规则：

- 下载期间禁用重复点击。
- 恢复只允许选择一个文件。
- 点击恢复时弹出覆盖确认对话框。
- 恢复期间显示进行中状态，不展示虚假百分比。
- 恢复成功后跳转登录页。
- 版本不一致或恢复失败时保留页面并显示后端错误。

## 10. 代码边界与配置

第一版不新增 Maven 模块，后端代码放在：

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/databasebackup
  controller/
  model/
  service/
  postgres/
  config/
```

前端代码放在：

```text
frontend/src/admin/database-backup/
```

建议配置：

```yaml
algo-mentor:
  database-backup:
    enabled: ${DATABASE_BACKUP_ENABLED:false}
    pg-dump-path: ${PG_DUMP_PATH:pg_dump}
    pg-restore-path: ${PG_RESTORE_PATH:pg_restore}
    psql-path: ${PSQL_PATH:psql}
    command-timeout: ${DATABASE_BACKUP_COMMAND_TIMEOUT:10m}
    max-upload-size: ${DATABASE_BACKUP_MAX_UPLOAD_SIZE:2GB}
```

Docker runtime 镜像需要安装与数据库 major version一致的 PostgreSQL client。上传大小还需要同步配置 Spring multipart 和反向代理限制。

所有 API 路径、Manifest 字段、确认文本、格式版本和错误码必须通过常量类或枚举统一管理。

本功能不新增业务表，因此不需要 Flyway migration。

## 11. 安全与审计

备份文件不加密，包含 API Key、密码哈希、Session、用户对话、代码和学习数据。运维必须通过 HTTPS 下载，并将文件保存在可信设备和受控目录中。

实现要求：

- 响应使用 `Cache-Control: no-store`。
- 服务端不长期保存备份文件。
- 上传文件使用服务端随机临时文件名。
- 临时文件在请求结束后删除。
- 日志不得记录 dump 内容、数据库密码、完整 JDBC URL 或用户数据。
- 禁止将 `.ambak` 提交到 Git 或公开存储。

备份和恢复复用现有管理员审计能力，增加：

```text
AdminAuditAction.DATABASE_BACKUP_EXPORT
AdminAuditAction.DATABASE_RESTORE_OVERWRITE
```

审计只记录结果、版本、表数量、文件大小和受控错误码，不记录备份内容或本地路径。

## 12. 测试方案

### 12.1 后端测试

- Manifest 序列化、dump checksum 和版本匹配测试。
- 表集合自动发现和差异检测测试。
- 并发备份恢复互斥测试。
- 非法 ZIP、缺失文件和非 data-only archive 拒绝测试。
- 数据库凭据不进入命令参数和日志测试。

### 12.2 PostgreSQL 集成测试

1. 写入字符串、时间、JSONB、`bytea`、UUID、外键和循环引用代表数据。
2. 创建全表备份并记录全部表数据摘要和序列状态。
3. 修改、删除和新增目标库数据。
4. 执行覆盖恢复。
5. 验证全部表数据和序列与备份时一致。
6. 在导入中间注入错误，验证清空和已导入数据全部回滚。
7. 验证应用版本、Flyway 指纹或表集合不一致时在清空前拒绝。
8. 验证 Spring Session、队列、缓存事件和 Flyway 历史也被覆盖。

### 12.3 前端测试

- 管理员权限和导航可见性。
- 下载期间按钮状态。
- 恢复确认和取消行为。
- 版本不匹配和恢复失败提示。
- 恢复成功后跳转登录页。

## 13. 验收标准

- 管理员可以下载包含 `public` 全部表数据的单个 `.ambak` 文件。
- 备份文件不要求密码，服务端不保留长期副本。
- 相同应用版本、PostgreSQL major version、Flyway 指纹和表集合可以成功覆盖恢复。
- 版本或表集合不一致时，在执行 `TRUNCATE` 前拒绝。
- 恢复成功后全部表数据和序列值与备份时一致。
- 恢复中途失败时恢复前数据完整保留。
- 恢复不停止或重启应用、容器，也不执行 Flyway migration。
- 恢复成功后当前管理员重新登录，缓存不返回恢复前数据。
- 运维能够按文档完成断流、恢复验证和重新接流量。
