# 项目框架初始化设计

## 目标

本次初始化建立可独立构建、测试和本地运行的前后端分离骨架，为后续算法题库、学习计划、AI 讲解、错题复盘和进度管理功能提供稳定边界。

## 后端

- 使用 `backend/pom.xml` 作为 Maven 多模块根，包含 `common` 与 `mentor-api`。
- `common` 放置跨模块 DTO、AI 请求模型和题目领域模型，不依赖 Spring Web。
- `mentor-api` 使用 Spring Boot 3.5、Spring MVC、SSE、Actuator、Micrometer、Flyway、PostgreSQL 驱动和 `openai-java`。
- 默认配置通过 `spring.autoconfigure.exclude` 不强制启动 PostgreSQL，`application-local.yml` 覆盖该配置并打开 datasource 与 Flyway。
- AI 配置通过 `algo-mentor.ai.openai.*` 绑定，密钥、base URL、模型、超时和重试次数均走环境变量。

## 前端

- `frontend` 使用 React 19 + TypeScript 6 + Vite 8。
- 首屏直接是学习工作台，不做营销页；界面聚焦题目队列、今日训练、AI 讲解和复盘入口。
- API 类型与调用集中在 `frontend/src/types` 和 `frontend/src/services`。

## 构建与本地依赖

- 根目录 `Makefile` 暴露 `make backend-test`、`make frontend-test`、`make build`、`make package` 等统一入口。
- Maven 缓存固定到 `./.m2/repository`，npm 缓存固定到 `./.npm`，两者均被 `.gitignore` 忽略。
- `deploy/docker/docker-compose.yml` 提供本地 PostgreSQL。
