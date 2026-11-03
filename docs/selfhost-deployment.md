# 个人自部署（Docker Compose 一键启动）

面向“自己部署、自己使用”的场景：一台装有 Docker 的机器，一条命令启动完整服务。
本方案只新增启动编排，**不修改任何业务代码和现有配置文件**，所有差异都通过环境变量和启动参数注入。
原有的本地开发（`make up`）、`deploy/docker` 与预发布部署流程保持不变。

## 组成

| 服务 | 镜像 | 说明 | 内存上限 |
| --- | --- | --- | --- |
| `postgres` | `postgres:16-alpine` | 业务数据库，数据保存在 `postgres-data` 卷 | 384m |
| `redis` | `redis:7.4-alpine` | 单实例；db0 缓存、db1 题目聊天事件流、db2 学习计划事件流；不持久化 | 128m |
| `init` | `algo-mentor:selfhost` | 一次性任务：Flyway 迁移 + 导入种子数据和知识库，完成后退出 | — |
| `app` | `algo-mentor:selfhost` | Spring Boot 应用（含前端静态资源），使用 `local` profile | 900m |

镜像由 `deploy/selfhost/Dockerfile` 多阶段构建：Node 构建前端 → Maven 打包后端 → JRE 运行镜像，
本机无需安装 JDK、Maven 或 Node。

## 快速开始

```bash
make selfhost-env   # 生成 deploy/selfhost/.env，自动填充数据库密码和 HMAC 密钥
vim deploy/selfhost/.env   # 至少把 AUTH_ADMIN_EMAILS 改成自己的邮箱
make selfhost-up    # 构建镜像并启动；首次会导入数据，耗时数分钟
make selfhost-logs  # 查看初始化与应用日志
```

没有 make 时也可以：

```bash
cd deploy/selfhost
cp .env.example .env   # 手动把两个 change-me 改成随机长字符串
docker compose up -d --build
```

启动后访问 `http://<主机>:8080`：

1. 使用 `AUTH_ADMIN_EMAILS` 中的邮箱注册账号，该账号即为管理员。
2. 进入管理后台的 AI 设置：
   - 新增 Provider（类型、Base URL、API Key），并在其下添加模型；
   - 在模型路由中为各业务场景（题目聊天、代码 Review、学习计划等）选择该模型。
     路由没有隐式兜底，未配置路由的场景无法调用 AI。
   AI Key 只保存在你自己的数据库中。
3. 自己注册完成后，可在管理后台「登录设置」中关闭注册。

> 登录开关（`AUTH_PASSWORD_*`）只在首次初始化登录设置时写入数据库，之后以管理后台为准。

## 首次数据导入

`init` 服务每次 `up` 都会运行，逻辑见 `deploy/selfhost/init-data.sh`：

- 若 `problem`、`learning_plan_template`、`knowledge_outline_node` 均已有数据，直接跳过（秒级）。
- 否则依次调用应用已有入口：题库/公司标签/题目元数据/学习计划模板种子导入，然后 `--knowledge-import` 导入知识库。
- 题目元数据（分类、提示、代码模板、相关题）导入会校验 manifest 引用的
  `data/sources/leetcode-api-metadata/problem_metadata_fetch_report.json` 的 SHA256；该文件已纳入 git 并随镜像提供。
  如需跳过元数据导入，可在 `.env` 设置 `SEED_PROBLEM_METADATA=false`。
- `app` 依赖 `init` 成功完成后才启动；导入失败时 `app` 不会启动，可通过 `make selfhost-logs` 排查。

更新代码或种子数据后，强制重新导入（均为幂等 upsert，保留用户学习记录）：

```bash
make selfhost-reseed
make selfhost-up
```

## 常用操作

| 命令 | 作用 |
| --- | --- |
| `make selfhost-up` | 构建并启动（升级代码后同样执行） |
| `make selfhost-down` | 停止并移除容器，保留数据卷 |
| `make selfhost-ps` | 查看各服务状态（`init` 显示 Exited (0) 属于正常） |
| `make selfhost-logs` | 跟踪 `init` 与 `app` 日志 |
| `make selfhost-reseed` | 强制重新导入种子数据和知识库 |

彻底清空数据：`docker compose -f deploy/selfhost/docker-compose.yml --env-file deploy/selfhost/.env down -v`（会删除数据库卷，不可恢复）。

## 资源与安全

- 资源参数可在 `.env` 中调整：`JAVA_OPTS`（默认 `-Xmx512m`）、`REDIS_MAXMEMORY`（默认 64mb）；
  PostgreSQL 使用 `shared_buffers=64MB`、`max_connections=40` 的小规格参数。
- Redis 中缓存和实时事件流的 key 均带 TTL，使用 `volatile-ttl` 优先淘汰短 TTL 的缓存；
  Redis 不持久化，重启后实时流丢失由应用回读 PostgreSQL 终态兜底。
- PostgreSQL 和 Redis 不对宿主机暴露端口，只有应用端口对外。
- 公网访问请在前面加 HTTPS 反向代理（Caddy/Nginx），并在 `.env` 设置 `AUTH_COOKIE_SECURE=true`；
  仅本机使用可设置 `APP_BIND=127.0.0.1`。
- `deploy/selfhost/.env` 已被 `.gitignore` 忽略，不要提交。

## 备份

```bash
docker compose -f deploy/selfhost/docker-compose.yml --env-file deploy/selfhost/.env \
  exec postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > algo-mentor.dump
```

管理后台的数据库备份功能同样可用（运行镜像已内置 `postgresql-client`）。
