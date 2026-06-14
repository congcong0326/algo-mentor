# Algo Mentor

`algo-mentor` 是一个 AI 个人学习项目，目标是把算法学习、刷题训练、错题复盘、学习计划和 AI 辅助讲解整合到一个可本地运行、可持续迭代的系统中。

## 已初始化技术栈

- 后端：JDK 17 + Spring Boot 3.5 + Spring MVC + SSE + Maven 多模块。
- 数据库：PostgreSQL + Flyway，本地 profile 通过 `application-local.yml` 启用。
- AI SDK：`openai-java`，配置通过环境变量注入。
- 基础组件：Logback 日志、Jackson 序列化、Micrometer 监控指标。
- 前端：React 19 + TypeScript 6 + Vite 8 + Vitest。
- 构建入口：根目录 `Makefile` 统一封装构建、测试、本地运行和打包命令。
- 文档：重要设计与模块索引放在 `docs/`，默认中文书写。

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

本地数据库可通过以下命令启动：

```bash
docker compose -f deploy/docker/docker-compose.yml up -d
```

详细协作约定见 [AGENTS.md](AGENTS.md)。
