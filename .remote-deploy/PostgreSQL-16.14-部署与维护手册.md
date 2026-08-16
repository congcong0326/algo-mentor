# PostgreSQL 16 开发环境部署说明

本文只覆盖 `pass-dev` 上为 `algo-mentor` 首次部署 PostgreSQL 的最小步骤。当前是受控内网开发环境，不按生产标准建设高可用、PITR、监控或完整运维体系；这些事项在需要进入生产前另行设计和补充。

## 1. 已确认的部署约定

| 项目 | 约定 |
| --- | --- |
| 数据库主机 | `pass-dev`，`192.168.10.121` |
| 应用主机 | `leetmentor-dev`，`192.168.10.118` |
| PostgreSQL | Ubuntu 官方仓库的 `postgresql-16` 和 `postgresql-client-16`；保持在 16.x 版本线 |
| 数据目录 | `/data/postgresql/16/main` |
| 端口 | `5432` |
| 数据库 / 角色 | `algo_mentor` / `algo_mentor` |
| 应用配置 | `leetmentor-dev:/etc/algo-mentor/application-local.yml`，权限 `0600` |
| 网络 | 只接受本机及 `leetmentor-dev` 的内网连接；开发环境不启用 TLS，使用 SCRAM 口令认证 |
| 初始容量 | 数据库主机为 `4 vCPU / 9.7 GiB RAM`；数据库总连接上限为 `200`，单个应用实例的 Hikari 连接池上限为 `40` |

密码、私钥和令牌不得写入此仓库、SQL 文件或 Shell 历史。开始任何状态变更前，确认目标确为 `pass-dev`、变更范围仅为开发环境，并确认当前虚拟机备份状态。

## 2. 安装 PostgreSQL 16

PASS 的数据库文件应放在 `/data`，不要使用默认的根分区数据目录。先关闭 Ubuntu 自动创建默认集群的行为：

```bash
sudo apt update
sudo apt install postgresql-common
sudoedit /etc/postgresql-common/createcluster.conf
```

在该文件中设置：

```conf
create_main_cluster = false
```

安装软件包并创建集群：

```bash
sudo install -d -o postgres -g postgres -m 0750 /data/postgresql/16
sudo apt install postgresql-16 postgresql-client-16
sudo pg_createcluster --datadir /data/postgresql/16/main 16 main
sudo systemctl enable --now postgresql
sudo pg_lsclusters
```

确认版本、数据目录和服务状态：

```bash
sudo -u postgres psql -c 'SHOW server_version;'
sudo -u postgres psql -c 'SHOW data_directory;'
sudo systemctl status postgresql@16-main
```

## 3. 最小连接配置

配置文件位于：

```text
/etc/postgresql/16/main/postgresql.conf
/etc/postgresql/16/main/pg_hba.conf
```

在 `postgresql.conf` 设置：

```conf
listen_addresses = '127.0.0.1,192.168.10.121'
port = 5432
password_encryption = 'scram-sha-256'
ssl = off
max_connections = 200
shared_buffers = 3GB
effective_cache_size = 7GB
work_mem = 4MB
maintenance_work_mem = 512MB
autovacuum_work_mem = 256MB
```

`ssl = off` 是本开发环境的简化约定，只适用于受控内网。生产环境或接入范围扩大时，再启用并验证 TLS。

这组参数适用于当前专用的 10G 开发数据库主机。`shared_buffers` 固定分配 3GB；其余内存由操作系统页缓存、数据库连接和查询工作区按需使用，并非闲置。`effective_cache_size` 仅供优化器估算，不实际分配内存。`work_mem` 按每个排序或哈希操作计算，不能因主机内存较大而直接大幅提高。

在 `pg_hba.conf` 保留本机 `peer` 管理规则，并为应用添加以下规则。删除或注释其他宽泛的外部 `host` 规则；规则按首次匹配生效。

```conf
host    algo_mentor    algo_mentor    127.0.0.1/32       scram-sha-256
host    algo_mentor    algo_mentor    192.168.10.118/32  scram-sha-256
```

本机防火墙使用 iptables 时，仅为 PostgreSQL 端口增加以下规则。先确认 INPUT 链中没有更早的、适用于 `5432` 的宽泛 ACCEPT 规则，并将规则纳入主机现有的持久化方式：

```bash
sudo iptables -I INPUT 1 -i lo -p tcp --dport 5432 -j ACCEPT
sudo iptables -I INPUT 2 -s 192.168.10.118/32 -p tcp --dport 5432 -j ACCEPT
sudo iptables -I INPUT 3 -p tcp --dport 5432 -j DROP
sudo iptables -S INPUT
```

重启服务使监听地址生效，并检查结果：

```bash
sudo systemctl restart postgresql@16-main
sudo -u postgres psql -c 'SHOW hba_file;'
sudo ss -ltnp | grep 5432
```

`max_connections` 和 `shared_buffers` 的变更需要重启 PostgreSQL；其他内存参数可重载。实际运行后，根据连接等待、慢查询和内存使用情况再调整，不直接将 `shared_buffers` 提高到 `8GB`。

## 4. 创建应用数据库

应用通过同一个非超级用户角色执行 Flyway 和运行时读写，因此开发环境中该角色拥有数据库和 `public` schema。不要使用 `postgres` 作为应用账号。

以本地 `postgres` 管理员身份执行：

```psql
CREATE ROLE algo_mentor LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE DATABASE algo_mentor OWNER algo_mentor ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE algo_mentor FROM PUBLIC;
\connect algo_mentor
ALTER SCHEMA public OWNER TO algo_mentor;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
```

随后在交互式 `psql` 中设置密码，避免将密码写入命令历史：

```psql
\password algo_mentor
```

## 5. 交接给 algo-mentor

在 `leetmentor-dev` 创建 `/etc/algo-mentor/application-local.yml`，由应用运行账号拥有并设为 `0600`。该文件不进入 `/root/code/algo-mentor` 仓库。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://192.168.10.121:5432/algo_mentor
    username: algo_mentor
    password: <only-in-leetmentor-dev-config>
    hikari:
      maximum-pool-size: 40
      minimum-idle: 2
  flyway:
    enabled: true
```

应用以 `SPRING_PROFILES_ACTIVE=local` 启动，并通过 `--spring.config.additional-location=file:/etc/algo-mentor/` 加载该配置。`max_connections=200` 是整台数据库的总上限，Hikari 的 `40` 是该应用实例最多占用的 JDBC 连接数；两者不应设为相同值。

## 6. 首次部署检查

- [ ] `pass-dev` 上 PostgreSQL 为受支持的 16.x 版本，数据目录为 `/data/postgresql/16/main`。
- [ ] `postgresql@16-main` 正常运行，`5432` 只监听本机和 PASS 内网地址。
- [ ] `algo_mentor` 不具备超级用户、建库或建角色权限。
- [ ] `leetmentor-dev` 可以用应用配置连接数据库；其他来源无法连接。
- [ ] 应用启动后 Flyway 迁移、日志和健康检查正常。
- [ ] 已更新 `README.md` 中的实际 PostgreSQL 版本、服务状态和应用部署状态。

## 7. 后续再补的事项

以下内容不阻塞当前开发环境首次部署：WAL 归档与 PITR、独立物理备份、恢复演练、Prometheus/exporter、完整告警与值班流程、角色细分、性能调优、主备高可用和大版本升级方案。

在生产发布或接入不受控网络前，需要单独补齐备份和恢复方案、监控告警、TLS、权限审计、容量规划及生产变更流程。
