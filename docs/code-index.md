# 代码索引

## 根目录

- `Makefile`：统一构建、测试、本地运行和前端静态资源同步入口。
- `.env.example`：本地开发环境变量样例，不包含真实密钥。
- `deploy/docker/docker-compose.yml`：本地 PostgreSQL 服务。

## 后端

- `backend/pom.xml`：Maven 多模块根，统一 Java 17、Spring Boot 与 `openai-java` 版本。
- `backend/common`：跨模块公共模型、DTO 和工具。
- `backend/llm-core`：项目内 LLM 抽象契约，定义 provider/gateway、请求响应、消息内容 part、工具调用、结构化输出、流式事件、能力发现、用量和统一错误模型。
- `backend/llm-openai`：OpenAI provider 适配模块，隔离 `openai-java` SDK、OpenAI 配置、provider 能力描述和后续请求/响应映射。
- `backend/agent-core`：Agent 核心编排模型，面向 `LlmGateway` 组织模型调用和后续工具执行流程。
- `backend/mentor-api`：Spring MVC API 应用。
- `backend/mentor-api/src/main/resources/application.yml`：默认应用配置，默认不强制连接数据库。
- `backend/mentor-api/src/main/resources/application-local.yml`：本地 PostgreSQL 与 Flyway 配置。
- `backend/mentor-api/src/main/resources/db/migration`：Flyway 迁移脚本目录。

## 前端

- `frontend/src/App.tsx`：学习工作台首屏。
- `frontend/src/services/api.ts`：前端 API 调用封装。
- `frontend/src/types/api.ts`：前后端共享契约的 TypeScript 表示。
- `frontend/package.json`：React 19、TypeScript 6、Vite 8、Vitest 4 依赖与脚本。
- `frontend/vite.config.ts`：Vite、React 插件和 Vitest 配置。

## 常用命令

- `make backend-test`：运行后端单元测试。
- `make frontend-install`：安装前端依赖。
- `make frontend-test`：运行前端测试。
- `make build`：后端打包并构建前端。
- `make package`：构建前端、同步到后端静态目录并打包后端。
