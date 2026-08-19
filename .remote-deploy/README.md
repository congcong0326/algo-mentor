# 预发布环境

本文记录当前预发布环境、已核验的部署状态及后续发布约定。基础设施最后核验时间：2026-08-13（UTC）；应用、容器与 SSH 最后核验时间：2026-08-19（UTC）。

## 环境概览

| 环境 | 主机名 | IP 地址 | 用途 | 当前核验状态 |
| --- | --- | --- | --- | --- |
| PASS | `pass` | `192.168.10.121` | PostgreSQL 与 Redis 基础设施主机 | PostgreSQL 16.14、Redis 7.4.10 的缓存与 Streams 实例均已部署、启用并运行。 |
| leetmentor | `leetmentor` | `192.168.10.118` | `algo-mentor` 预发布业务主机 | Docker 已启用；`algo-mentor` 容器正在运行并直接提供 HTTP `18080`。 |
| prometheus | `congcong` | `192.168.10.85` | 外置 Prometheus 与 Grafana 观测主机 | Prometheus `v3.14.0`、Grafana `13.2.0` 已运行并抓取预发布应用。 |

PASS 与 leetmentor 主机均已通过 SSH 登录账号 `congcong` 验证成功；预发布 root 入口 `leetmentor-root` 和观测 root 入口 `prometheus-root` 均已验证可无交互登录。本工作机的项目专用部署密钥已完成远端授权，并可无交互登录。密码属于团队开发凭据，**不得在仓库中明文保存**；请从团队约定的安全凭据渠道获取，并在首次部署前完成更换。

## 当前实际状态

### PASS 数据库主机

- 已安装 Ubuntu 官方 `postgresql-16` 与 `postgresql-client-16`，实际版本为 `16.14-0ubuntu0.24.04.1`。
- `postgresql@16-main` 已启用并运行，监听 `127.0.0.1:5432` 和 `192.168.10.121:5432`；数据目录为 `/data/postgresql/16/main`。
- 数据库为 `algo_mentor`，应用角色为同名非超级用户。SCRAM/HBA 仅允许本机和 `leetmentor-dev`（`192.168.10.118`）连接；`nftables` 对 5432 执行相同的来源限制。
- `leetmentor-root:/etc/algo-mentor/database.env` 保存随机数据库凭据，目录权限为 `0750 root:algo-mentor`，文件权限为 `0640 root:algo-mentor`。真实密码不进入仓库。
- 2026-08-19 运行提交 `a6ce4ddeda7c8acdbd5ce10694512ba09b1175ad`；`flyway_schema_history` 当前有 68 条 SQL 迁移且均成功，最新为 `V68__learner_memory_cross_problem_recovery.sql`。本次仅验证已有迁移，未执行新的 schema 变更；用户画像重放前已在业务主机创建并通过 `pg_restore --list` 校验逻辑备份 `/var/backups/algo-mentor/pre-profile-reset-20260819T033419Z.dump`（SHA-256：`25d2d891c710dd8002b5f57554d7adc59a234b401ba40c9fab195690953af901`）。WAL 归档、PITR、异机备份、恢复演练和监控告警尚未配置，因此该数据库不具备生产就绪条件。
- 2026-08-15 已完成基础 seed 数据初始化：`3591` 道题目（`3202` 双语、`389` 仅中文）、`441` 家公司和 `33138` 条公司题目信号；学习元数据导入审计为 1 次，包含 `4424` 条关联、`11841` 条提示和 `68153` 条代码模板；学习计划模板为 `35` 个、题目引用为 `1738` 条（`1699` 条已匹配本地题库）。
- 已从官方源码包（SHA-256 已核验）安装 Redis `7.4.10`，二进制位于 `/opt/redis/7.4.10/bin/`；`redis-cache.service` 与 `redis-stream.service` 均已启用并运行。
- `redis-cache` 监听 `127.0.0.1:6379` 和 `192.168.10.121:6379`，用于 Shared TTL 缓存；`maxmemory=384mb`、`allkeys-lfu`，关闭 RDB/AOF 持久化。`redis-stream` 监听对应的 `6380`，用于 Redis Streams；`maxmemory=768mb`、`noeviction`，启用 RDB 和 AOF `everysec`。
- 两个实例均关闭默认 Redis 用户，并使用独立 ACL 账号；服务、防火墙仅允许本机及 `leetmentor-dev`（`192.168.10.118`）访问 6379/6380。2026-08-15 修复 Practice Chat 实时通道时，`algo_mentor_stream` 的 key pattern 与实际 `agent:realtime:run:*` 不匹配，按预发布决策调整为 `allkeys allcommands`；它不再是最小权限账号，凭据必须严格保护。变更前 ACL 备份位于 `pass-dev:/etc/redis/stream.acl.bak.20260815T142749Z`。`leetmentor-root:/etc/algo-mentor/redis.env` 保存应用连接凭据，权限为 `0640 root:algo-mentor`，真实密码不进入仓库。
- Streams 每日 02:30 UTC 生成校验过的本机归档至 `pass-dev:/data/backups/redis-stream/`，保留 14 天；首个归档已还原到隔离的临时 Redis 实例并完成数据读取验证。该归档与服务位于同一主机，尚非异机备份；监控告警、RPO/RTO 与备份责任人仍未定义。

### leetmentor 预发布业务主机

- 已安装并启用 Ubuntu `docker.io` `29.1.3`，`docker.service` 为 enabled 和 active。
- `algo-mentor` 容器正在运行，使用非 root 容器用户 `algo-mentor`，镜像为 `algo-mentor-api:preprod-2d4545c42a18-20260819T111815Z`，重启策略为 `unless-stopped`。该版本对应提交 `2d4545c42a1827420cd8202347d607207049d99c`，发布制品 SHA-256 为 `77df7de3817f419794a150a4fe65490c506ad2710fcf24d9cfbb9f4a1cd49ace`。
- 容器将 Spring Boot HTTP 端口直接映射为 `0.0.0.0:18080` 与 `[::]:18080`；前置代理可按现有约定将流量转发至该端口。
- 运行时配置位于 `/etc/algo-mentor/`：`database.env`、`redis.env` 和由当前工作机 `.env` 迁移而来的 `runtime.env`。目录权限为 `0750 root:algo-mentor`，每个文件权限为 `0640 root:algo-mentor`；2026-08-15 已校验 `runtime.env` 与本机 `.env` 完全一致。2026-08-16 已显式加入并验证 `PRACTICE_CHAT_SUBMISSION_HISTORY_TOOL_ENABLED=true` 与 `PRACTICE_CHAT_SUBMISSION_HISTORY_CODE_DETAIL_ENABLED=true`，发布前配置备份为 `/etc/algo-mentor/runtime.env.bak.20260816T080926Z`。密码登录已禁用（`AUTH_PASSWORD_LOGIN_ENABLED=false`），真实值不进入仓库。
- 已安装 `postgresql-client-16` 用于连通性验证，应用容器已连接 PASS PostgreSQL 并完成 Flyway。缓存 Redis（6379）与 Streams Redis（6380）的连接配置继续使用既有受保护文件。
- 发布制品和 Dockerfile 位于 `/opt/algo-mentor/releases/2d4545c42a18-20260819T111815Z/`；前一版本容器以 `algo-mentor-previous-2d4545c42a18-20260819T111815Z` 停止保留，可用于容器级回滚。本次基础数据 seed 及其校验输入位于 `/opt/algo-mentor/releases/20260815T134000Z/seed/`，由 root 管理。启动前与导入前逻辑备份位于 `/var/backups/algo-mentor/`，权限为 `0600 root:root`。

实际部署、健康检查、日志和回滚边界详见 `预发布应用升级结果-2026-08-17.md`；今后这些操作统一使用 `ssh leetmentor-root`。

### 外置观测主机

- `192.168.10.85` 的 Prometheus 从 `192.168.10.118:18080/actuator/prometheus` 抓取预发布指标，Grafana 提供已 provision 的预发布 dashboard。
- 观测配置位于 `/root/docker-nas/prometheus/`；目标、Grafana dashboard、验证与回滚方式见 `docs/preprod-observability-runbook.md`。
- 2026-08-19 已同步 `Algo Mentor - Preprod` dashboard（19 个面板、55 个查询）和 `algo-mentor-preprod` 告警组（10 条规则）；Prometheus target 为 `UP`，`ai_run_active` 查询存在且当前值为 `0`，Grafana `/api/health` 返回 `database: ok`。同步前备份位于观测主机 `backups/algomentor-preprod.json.20260819T112015Z` 与 `backups/algo-mentor-preprod.yml.20260819T112015Z.empty`。
- 今后观测栈维护统一使用 `ssh prometheus-root` 直接进入 root，不使用账号密码登录或 `congcong` 后再手动切换 root。

## 访问方式

```bash
ssh congcong@192.168.10.121  # PASS
ssh leetmentor-root          # 预发布部署、预检、健康检查和容器操作
ssh prometheus-root           # Prometheus/Grafana 配置、验证和回滚
```

## SSH 密钥访问

2026-08-15 已在当前部署工作机生成专用 ED25519 私钥 `/root/.ssh/id_ed25519_remote_deploy`，私钥权限为 `0600`，本机 `.ssh` 目录权限为 `0700`。`/root/.ssh/config` 保留 `pass-dev` 与 `leetmentor-dev` 的普通账号访问，并提供 `leetmentor-root` 的 root 访问；预发布部署、预检、健康检查、日志和容器操作统一使用 `leetmentor-root`，`leetmentor-dev` 普通账号不作为发布入口。密码及键盘交互认证均被禁用。

2026-08-19 已创建观测专用 ED25519 私钥 `/root/.ssh/id_ed25519_prometheus_ops`，私钥权限为 `0600`；其公钥已仅追加至 `prometheus` 主机的 `root:/root/.ssh/authorized_keys`。`/root/.ssh/config` 的 `prometheus-root` 固定使用该私钥、root 用户、`IdentitiesOnly yes` 与严格主机键校验，且禁用密码和键盘交互认证。该密钥不得复用为 PASS 或预发布应用部署密钥，也不得复制、提交或通过聊天传输。

两台目标主机的 ED25519 主机键已以首次信任（TOFU）方式写入 `/root/.ssh/known_hosts`，之后的连接使用严格主机键校验。其指纹如下：

- `pass-dev`（`192.168.10.121`）：`SHA256:Vf4whl22d8QIJsU9tva0N911IQinax56BrPYxkJHcsc`
- `leetmentor-dev`（`192.168.10.118`）：`SHA256:B3nijoHMlNHhlAi54Ii5p8pSXGM6FYm2hAxYAQAUXJ0`

该工作机项目公钥的指纹为 `SHA256:FkyPs7kmXw0YdKh7RJ6w4O9Qj0mClYuEPPzpiY827i8`（ED25519）。2026-08-15 已在两台主机的 `congcong` 账户中备份原有 `authorized_keys` 后追加该公钥，且保留既有授权键。`pass-dev` 的备份为 `authorized_keys.backup-20260815T125506Z`，`leetmentor-dev` 的备份为 `authorized_keys.backup-20260815T125538Z`。两台主机的 `.ssh` 目录权限均为 `0700`，`authorized_keys` 权限均为 `0600`；无交互公钥登录已分别验证为 `pass`/`congcong` 和 `leetmentor`/`congcong`。2026-08-17 已将同一项目部署公钥追加至 `leetmentor-dev` 的 `root` 授权键，保留原有键并创建备份 `/root/.ssh/authorized_keys.bak.20260817T064427Z`；`PermitRootLogin without-password` 与仅公钥认证仍然生效，`ssh leetmentor-root` 已验证可直连 root。**当前 SSH 访问可用于预发布部署**。密码、私钥和其他凭据均未写入仓库。

## 发布约定

当前两台主机用于预发布验证，不可等同于生产环境。预发布部署、预检、健康检查和容器操作统一使用 `leetmentor-root`；`leetmentor-dev` 仅保留普通账号访问兼容性，不作为发布入口。快速发布不执行数据库迁移，但应用启动仍会校验既有 Flyway 迁移；它仍会运行相关测试、打包应用并构建远端镜像，因此不是增量编译。预发布完成测试、备份与恢复能力补齐后，才可上线到生产环境。

生产环境主机、访问方式和具体发布步骤尚未提供，不能据此 README 直接执行生产发布。建议生产发布至少包含：数据库迁移及备份验证、业务健康检查、配置与密钥校验、日志确认，以及明确的回滚方案。

## 核验范围

2026-08-11 已通过 SSH/root 完成 PostgreSQL 首次部署与核验：安装 PostgreSQL 16、创建 `/data/postgresql/16/main` 集群、配置监听/HBA/SCRAM/资源参数和持久化 5432 防火墙规则，并创建 `algo_mentor` 数据库、最小权限角色及受保护的应用主机连接配置。2026-08-13 已在同一基础设施主机完成 Redis 7.4.10 双实例部署：缓存与 Streams 服务启动、ACL/网络限制、AOF/RDB、首个 Streams 归档及隔离恢复、应用主机认证读写和 `XADD → XGROUP → XREADGROUP → XACK` 均已验证。2026-08-15 完成当前工作机 SSH 专用密钥、严格主机键校验和两台目标机的公钥授权；同时完成 Docker 安装、`algo-mentor` 容器部署、Flyway 迁移、基础数据 seed 导入、容器重启和 HTTP 健康检查。2026-08-19 已完成提交 `a6ce4dd` 的预发布发布和单用户画像清理重放；随后完成提交 `2d4545c` 的无迁移快速发布、容器替换、readiness 健康检查和外置 Prometheus/Grafana 指标配置同步。状态会随部署变更；每次环境调整后应重新核验并更新本文档。
