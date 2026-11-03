# 个人自部署手册

适用场景：在一台自己的机器（云服务器、家用主机、NAS 等）上部署 algo-mentor 给自己使用。
整套服务通过 Docker Compose 启动，首次启动会自动建表并导入题库、学习计划模板和知识库，
AI Key 由部署者在管理后台自行填写。

本方案只新增启动编排，不修改业务代码和现有配置文件；原有的本地开发（`make up`）、
`deploy/docker` 与预发布部署流程保持不变。

---

## 一、快速部署（5 步）

### 0. 准备环境

| 项目 | 要求 |
| --- | --- |
| 系统 | Linux / macOS / Windows（WSL2），x86_64 或 arm64 |
| Docker | Docker Engine 24+，带 Compose v2（`docker compose version` 能输出版本） |
| 工具 | `git`；`make` 可选（没有 make 见第二节） |
| 内存 | 运行约 600MB，建议机器 ≥ 2GB（首次构建时 Maven/Node 需要额外内存） |
| 磁盘 | 预留 ≥ 10GB（镜像约 0.8GB，构建缓存与数据库另计） |
| 网络 | 首次构建需要拉取 Docker 镜像、npm 和 Maven 依赖 |

### 1. 拉取代码

```bash
git clone https://github.com/congcong0326/algo-mentor.git
cd algo-mentor
```

### 2. 生成配置

```bash
make selfhost-env
```

该命令会生成 `deploy/selfhost/.env`，并自动填入随机的数据库密码和 HMAC 密钥。
然后编辑这个文件，**至少修改管理员邮箱**：

```bash
vim deploy/selfhost/.env
```

```ini
AUTH_ADMIN_EMAILS=you@example.com   # 用这个邮箱注册的账号就是管理员
APP_PORT=8080                       # 对外端口，被占用时改掉
```

### 3. 启动

```bash
make selfhost-up
```

首次执行会依次完成以下步骤：

1. 在容器内构建前端和后端，耗时约 5–15 分钟，取决于网络和机器配置；
2. 启动 PostgreSQL 和 Redis；
3. `init` 容器执行数据库迁移并导入初始数据，耗时约 1–2 分钟；
4. 数据导入成功后启动应用。

查看进度：

```bash
make selfhost-logs   # Ctrl+C 退出日志跟踪，不影响服务
make selfhost-ps     # app 显示 (healthy) 即启动完成；init 显示 Exited (0) 属于正常
```

`init` 日志的最后一行是 `[init] 初始数据导入完成。`，说明数据已导入。

### 4. 首次使用

1. 浏览器打开 `http://<服务器IP>:8080`。
2. **注册账号**：邮箱填写 `AUTH_ADMIN_EMAILS` 中的邮箱，该账号自动拥有管理员权限。
3. **配置 AI**：进入管理后台的 AI 设置：
   - 新增 Provider，填写类型、Base URL 和 API Key（兼容 OpenAI 接口的服务均可）；
   - 在该 Provider 下添加模型，模型 ID 填写服务商的模型名；
   - 在**模型路由**中为各业务场景（题目聊天、代码 Review、学习计划等）选择该模型。
     路由没有默认兜底，未配置的场景无法使用 AI。
4. **关闭注册（推荐）**：注册完自己的账号后，在管理后台「登录设置」中关闭注册。

> AI Key 只保存在你自己的数据库中。
> `.env` 中的 `AUTH_PASSWORD_*` 登录开关只在首次初始化时生效，之后以管理后台「登录设置」为准。

部署完成。

---

## 二、没有 make 时

所有 `make selfhost-*` 命令都可以用 `docker compose` 代替，在 `deploy/selfhost` 目录下执行：

```bash
cd deploy/selfhost
cp .env.example .env
# 编辑 .env：把两处 change-me 改成随机长字符串（可用 openssl rand -hex 32 生成），并修改 AUTH_ADMIN_EMAILS
docker compose up -d --build        # 启动 / 升级
docker compose ps -a                # 状态
docker compose logs -f init app     # 日志
docker compose down                 # 停止（保留数据）
```

> 如果当前 shell 中已经 export 了 `POSTGRES_PASSWORD` 等同名变量，它们会覆盖 `.env` 中的值。
> `make selfhost-*` 会在干净的环境中执行 compose，不受这个问题影响。

---

## 三、日常运维

| 操作 | 命令 |
| --- | --- |
| 启动 / 重新构建 | `make selfhost-up` |
| 停止（保留数据） | `make selfhost-down` |
| 查看状态 | `make selfhost-ps` |
| 查看日志 | `make selfhost-logs` |
| 修改 `.env` 后生效 | `make selfhost-up`（会自动重建受影响的容器） |

### 升级到新版本

```bash
git pull
make selfhost-up
```

- 数据库迁移由应用启动时自动执行。
- 如果新版本更新了题库、学习计划模板或知识库，再执行一次强制导入：

  ```bash
  make selfhost-reseed
  ```

  导入使用 upsert，可以重复执行，不会影响你的做题记录、复习进度和笔记。

### 备份

```bash
docker compose -f deploy/selfhost/docker-compose.yml --env-file deploy/selfhost/.env \
  exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > algo-mentor-$(date +%F).dump
```

建议用 crontab 定期执行，并把备份文件拷到其他机器。管理后台也提供数据库备份功能。
Redis 只存缓存和临时事件流，不需要备份。

### 恢复（也可用于迁移到新机器）

在新机器上先完成第一节的第 1～3 步，确保服务能正常启动，然后执行：

```bash
C="docker compose -f deploy/selfhost/docker-compose.yml --env-file deploy/selfhost/.env"
$C stop app
$C exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
$C exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < algo-mentor-xxxx.dump
$C start app
```

恢复后账号、AI 配置和学习数据都与备份时一致。

### 彻底删除

```bash
docker compose -f deploy/selfhost/docker-compose.yml --env-file deploy/selfhost/.env down -v
```

`-v` 会删除数据库卷，数据无法恢复，执行前请先备份。

---

## 四、公网访问与 HTTPS

服务默认监听 `0.0.0.0:8080`，使用 HTTP。如果要放到公网，建议在前面加一层 HTTPS 反向代理：

1. 在 `.env` 中设置 `APP_BIND=127.0.0.1`（只允许本机反向代理访问）和 `AUTH_COOKIE_SECURE=true`，然后执行 `make selfhost-up`。
2. 配置反向代理。注意：AI 回复使用 SSE 流式输出，**不能开启响应缓冲**。

Caddy（自动申请证书）：

```caddy
mentor.example.com {
    reverse_proxy 127.0.0.1:8080 {
        flush_interval -1
    }
}
```

Nginx：

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_read_timeout 600s;
}
```

只在本机或内网使用时，直接通过 HTTP 访问即可。

---

## 五、常见问题

| 现象 | 处理 |
| --- | --- |
| `请在 .env 中设置 POSTGRES_PASSWORD` | 没有生成 `.env`，先执行 `make selfhost-env` |
| `port is already allocated` | 修改 `.env` 中的 `APP_PORT` 后重新执行 `make selfhost-up` |
| `service "init" didn't complete successfully` | 执行 `make selfhost-logs` 查看 `init` 的报错；排除问题后重新执行 `make selfhost-up` 即可，导入可以重复执行 |
| 构建时拉取依赖很慢或超时 | 网络问题：为 Docker 配置镜像加速；npm 和 Maven 的依赖下载同样依赖网络 |
| 页面能打开，但 AI 没有回复 | 检查管理后台的 Provider、模型是否已启用，以及**模型路由**是否已配置 |
| 注册时提示不允许注册 | 管理后台「登录设置」中已关闭注册；需要时临时打开 |
| 机器内存紧张 | 在 `.env` 中调小 `JAVA_OPTS` 的 `-Xmx`（不建议低于 384m） |

---

## 附：部署结构

| 服务 | 镜像 | 说明 | 内存上限 |
| --- | --- | --- | --- |
| `postgres` | `postgres:16-alpine` | 业务数据库，数据保存在 `postgres-data` 卷；使用小规格参数 | 384m |
| `redis` | `redis:7.4-alpine` | 单实例：db0 为缓存，db1 为题目聊天事件流，db2 为学习计划事件流；不持久化，淘汰策略为 `volatile-ttl` | 128m |
| `init` | `algo-mentor:selfhost` | 一次性任务：执行数据库迁移、导入初始数据后退出 | — |
| `app` | `algo-mentor:selfhost` | Spring Boot 应用（含前端），使用 `local` profile，`-Xmx512m` | 900m |

- 只有应用端口对外暴露，PostgreSQL 和 Redis 不暴露端口。
- 相关文件都在 `deploy/selfhost/` 下：`Dockerfile` 为多阶段构建，`docker-compose.yml` 为服务编排，
  `init-data.sh` 为首次导入逻辑，`init-env.sh` 生成 `.env`。
- `init` 每次 `up` 都会运行：如果 `problem`、`learning_plan_template`、`knowledge_outline_node`
  三张表都已有数据，就直接跳过；否则依次导入题库、公司标签、题目元数据、学习计划模板和知识库。
  设置 `SEED_FORCE=true` 可以强制重新导入（`make selfhost-reseed`），设置 `SEED_PROBLEM_METADATA=false`
  可以跳过题目元数据导入。
- 题目元数据导入会校验 `data/sources/leetcode-api-metadata/problem_metadata_fetch_report.json` 的 SHA256，
  该文件已纳入 git，并随镜像一起提供。
