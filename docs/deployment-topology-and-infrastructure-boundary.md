# 部署拓扑与基础设施边界

更新时间：2026-08-13

状态：已定基线

## 一、部署原则

`algo-mentor` 的本地开发环境默认不容器化：应用软件进程、PostgreSQL、Redis 以及需要本地运行的配套服务都直接部署在开发机上，`make up` 直接启动应用进程。测试环境和生产环境不沿用本地开发进程管理：测试环境 Redis 直接安装并外部提供，生产环境 PostgreSQL、Redis 等有状态基础设施全部外置。

基础设施与应用进程的边界固定如下：

| 环境 | 应用进程 | PostgreSQL | Redis | 说明 |
| --- | --- | --- | --- | --- |
| 本地开发 | 直接运行进程 | 直接安装运行 | 直接安装运行 | 默认不容器化，`make up` 直接启动 Java 应用进程 |
| 当前测试环境 | 按测试环境方式运行应用进程 | 外部或环境已有服务 | 直接安装在测试环境，不容器化 | 测试环境 Redis 不纳入应用 Compose |
| 生产环境 | 以容器或其他进程交付方式运行应用 | 外置托管/独立部署 | 外置托管/独立部署 | 应用容器只连接外部基础设施 |

测试和生产环境不得把数据库或 Redis 数据目录挂载到应用容器，也不得依赖本地开发机上的服务名作为连接地址。所有连接地址、端口、账号、密码、TLS 和超时配置均通过环境变量或外部配置注入。

## 二、Redis 边界

Redis 至少按用途划分独立实例：

- Shared TTL 缓存实例：允许缓存丢失，使用缓存专用淘汰和持久化策略；
- Redis Streams 队列实例：消息需要确认、重试、积压和恢复，禁止使用会淘汰未消费消息的缓存策略。

Redis 实例由测试或生产基础设施负责安装、升级、备份、监控、访问控制和容量管理。应用只负责通过客户端访问已注入的 Redis endpoint，不负责启动 Redis 服务。

Redis Streams 与当前 PostgreSQL 持久队列的接入仍需遵守消息可靠性边界：业务事务产生的可靠消息先落 PostgreSQL outbox/queue，再由 relay 投递到 Stream；消费者采用至少一次语义、成功后 ACK、超时回收和 DLQ。Redis Stream 不作为缓存淘汰域，也不替代 PostgreSQL 业务事实来源，除非后续单独完成可靠性设计和迁移评审。

## 三、应用交付约束

- Dockerfile 只构建和运行 `mentor-api` 应用镜像，不改变本地默认直接运行进程的开发方式。
- 生产部署不依赖 `docker-compose.yml` 启动 PostgreSQL 或 Redis。
- Compose 中的 PostgreSQL 服务和应用服务只作为可选的本地便利/可重复测试方式；修改正式部署文档时不得将其描述为默认本地开发或生产拓扑。
- 当前 Compose 尚未包含 Redis；Redis 接入完成后，本地默认仍直接安装运行 Redis，是否增加可选 Redis Compose 服务需另行决定。
- 应用健康检查必须能区分应用进程可用、PostgreSQL 不可用和 Redis 不可用，不能用启动本地基础设施掩盖连接配置错误。
- 测试和生产环境的基础设施版本、容量、淘汰、持久化和备份策略由部署环境单独记录，应用仓库只维护所需的连接契约和最低兼容版本。

## 四、相关文档

- Redis 缓存 provider：`docs/cache-module-v1-design.md`
- 业务缓存参数：`docs/cache-business-data-decisions.md`
- PostgreSQL 持久队列与成功确认：`docs/persistent-queue-success-confirmation-design.md`
- 本地直接运行与反向代理：`docs/reverse-proxy.md`；可选 Docker 观测栈：`deploy/docker/observability/README.md`
