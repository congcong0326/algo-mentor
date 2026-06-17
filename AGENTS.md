# Repository Guidelines

## 项目定位
- `algo-mentor` 是一个 AI 个人学习项目，后端以 Java 为主，面向算法学习、刷题训练、知识沉淀、AI 辅助讲解与学习进度管理。
- 项目采用前后端分离的工程结构，通过根目录 `Makefile` 统一封装构建、测试、本地运行和打包命令。
- 前端新建时优先选择 React + TypeScript + Vite 体系；后端保持 Java/Maven 多模块结构。

## 目标项目结构
- `backend/` Maven 多模块根目录，建议包含：
  - `common/`：通用 DTO、异常、工具、AI 请求模型、题目/学习域模型。
  - `mentor-api/`：Spring MVC API、认证、题库、学习计划、AI 对话与讲解接口。
  - 按需增加独立 worker 模块，例如异步评测、题目导入、向量索引、内容同步等。
- `frontend/` React + TypeScript 单页应用，源码放在 `frontend/src`，构建产物输出到 `frontend/dist` 或 `frontend/build`，并通过 Makefile 同步到后端静态目录。
- `docs/` 存放研发文档和设计记录，建议中文书写；重要功能先补充设计说明或接口草案。
- `deploy/docker/` 存放 Dockerfile、Docker Compose 和本地依赖服务配置。

## 渐进式披露
- 第一层：先读本文件，掌握项目定位、目标结构、构建命令、编码风格和安全约束。
- 第二层：项目形成后，优先读 `docs/code-index.md` 定位模块职责、入口类、关键调用链和常见改动位置。
- 第三层：只打开当前任务涉及的 controller/service/page/DTO/迁移脚本/测试文件；避免一次性通读 `target`、`dist`、`build`、`node_modules`、`.m2`、`.npm` 等生成目录。
- 涉及跨模块契约时，同步检查后端 DTO、API、前端类型、数据库迁移和文档是否需要一起更新。

## 构建、测试与本地运行
- 使用根目录 `Makefile` 作为统一入口；Maven 缓存使用 `./.m2/repository`，npm 缓存使用 `./.npm`，二者都不应提交。
- 建议统一命令：
  - `make build`：后端打包 + 前端构建。
  - `make package`：前端构建并同步到后端静态目录后再打后端包。
  - `make backend-build`：后端打包。
  - `make backend-test`：后端测试。
  - `make backend-dev`：启动后端 API。
  - `make frontend-install`：安装前端依赖。
  - `make frontend-build`：构建前端。
  - `make frontend-test`：运行前端测试。
  - `make frontend-dev`：启动前端开发服务器。
- 后端 Maven 命令示例：`mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository ...`。
- 前端 npm 命令示例：`npm --cache ./.npm --prefix frontend ...`。

## 后端技术约定
- JDK 17，Spring MVC，SSE，Maven 多模块；包名使用 `org.congcong.algomentor.*`。
- 流式 AI 回复和学习过程事件使用 SSE（Server-Sent Events），优先通过 Spring MVC 的 `SseEmitter` 或项目统一封装实现。
- 数据库选择 PostgreSQL；数据库迁移使用 Flyway，迁移脚本放在 API 模块的 `src/main/resources/db/migration`。
- 数据访问层在 PostgreSQL 基础上选择一套统一方案后保持一致，避免同一模块混用多套持久化风格。
- AI SDK 暂时选择 `openai-java`；AI 能力应封装在清晰的 service/client 边界内，避免 controller 直接拼接模型请求；密钥、base URL、模型名、超时和重试策略必须走配置。
- 日志实现使用 Logback，经 SLF4J/Lombok `@Slf4j` 输出；日志中不得输出 API key、访问令牌、完整 Authorization 头或用户隐私内容。
- JSON 序列化和反序列化使用 Jackson，统一配置时间、枚举、空值和错误响应格式。
- 监控指标集成 Micrometer，核心接口、AI 调用、SSE 连接、数据库访问和后台任务应暴露必要的计数、耗时和错误指标。

## 前端技术约定
- 新建前端时优先使用 React + TypeScript + Vite；UI 库可根据实际产品形态选择 Ant Design 或更轻量方案。
- TypeScript/React 使用 2 空格缩进；组件与文件使用 PascalCase，hooks 以 `use` 前缀；保持命名导出一致。
- 学习工具类页面应优先服务高频操作：题目列表、练习状态、AI 讲解、错题复盘、学习计划和进度看板应信息密度适中、操作路径短。
- 前端 API 类型应与后端 DTO 保持同步；复杂请求/响应建议在 `frontend/src/types` 和 `frontend/src/services` 中集中维护。

## 测试指引
- 后端单元测试使用 Maven Surefire；跨模块或依赖真实外部组件的测试使用 Failsafe，并以 `*IT.java` 命名。
- AI client、题目解析、学习计划生成、权限校验、数据迁移等逻辑需要重点覆盖边界条件。
- 前端测试优先覆盖核心交互和状态流转；使用 Vitest/React Testing Library 或项目实际选型，命令通过 `make frontend-test` 暴露。
- 修改行为后尽量运行最小相关测试；提交或交付前说明已运行的验证命令和结果。

## Commit 与 Pull Request
- Git 提交信息遵循类 Conventional Commits，例如 `feat: add practice session API`、`fix: handle empty AI explanation`、`chore: initialize backend skeleton`。
- PR/变更说明应包含：变更摘要、测试结果、配置变更、数据库迁移影响、前端截图或录屏，以及风险与回滚方式。

## 安全与配置提示
- 不要提交密钥、模型 token、真实用户数据、数据库密码或本地 `.env`。
- AI 提供商配置、数据库连接、JWT 密钥、CORS 白名单等均应通过环境变量或本地配置注入。
- 依赖外部 AI 服务的功能需要设置超时、错误降级和可观测日志；不要让模型调用阻塞关键事务或持有数据库事务。

## 实施规则
- 研发设计文档默认使用中文编写。除非用户明确要求英文，新增或更新的设计文档、实施计划、代码注释、架构说明和开发流程文档应优先使用中文。
- 代码文件不要全部平铺在同一目录下。请按职责分类组织，例如 controller、service、domain/model、repository、config、util 等，保持目录结构清晰、模块边界明确。
- 写代码时如果字符串、数字或其他字面量属于跨类/跨模块复用的公共契约（例如 API 路径、请求头、配置 key、SSE 事件名、JSON 字段、metadata key、状态值、provider 标识等），应抽象到所属模块已有或新建的常量类/枚举中统一管理，并用简短注释或 Javadoc 说明字段作用；局部错误消息、测试样例和只在单个方法内使用的一次性文案可保留字面量。
- 除非用户要求，默认不使用 superpowers 插件。
