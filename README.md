# Leet Mentor

`algo-mentor` 是一个 AI 个人学习项目，目标是把算法学习、刷题训练、错题复盘、学习计划和 AI 辅助讲解整合到一个可本地运行、可持续迭代的系统中。

## 已初始化技术栈

- 后端：JDK 17 + Spring Boot 3.5 + Spring MVC + SSE + Maven 多模块。
- 数据库：PostgreSQL + Flyway，本地 profile 通过 `application-local.yml` 启用。
- AI SDK：`openai-java`，配置通过环境变量注入。
- 基础组件：Logback 日志、Jackson 序列化、Micrometer 监控指标。
- 前端：React 19 + TypeScript 6 + Vite 8 + Vitest。
- 构建入口：根目录 `Makefile` 统一封装构建、测试、本地运行和打包命令。
- 文档：重要设计与模块索引放在 `docs/`，默认中文书写。

部署边界：本地开发默认不容器化，应用软件、PostgreSQL、Redis 等直接部署运行，`make up` 直接启动应用进程；当前测试环境 Redis 直接安装并外部提供；生产环境 PostgreSQL、Redis 等有状态基础设施全部外置，容器化交付只包含应用软件进程。详见[部署拓扑与基础设施边界](docs/deployment-topology-and-infrastructure-boundary.md)。

## 项目结构

```text
backend/
  common/
  mentor-api/
frontend/
docs/
deploy/docker/
```

## 常用命令

```bash
make frontend-install
make backend-test
make frontend-test
make build
make package
```

本地开发默认直接构建并启动应用进程：

```bash
make up
```

Docker Compose 仅作为可选的本地便利和可重复测试方式，不是默认开发启动方式。

详细协作约定见 [AGENTS.md](AGENTS.md)。
