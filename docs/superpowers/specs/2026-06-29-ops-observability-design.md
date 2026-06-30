# 运维观测接入与业务埋点一期设计

## 背景

`algo-mentor` 当前仍处于测试阶段，需要在日常开发环境中能够快速拉起一套本地观测栈，判断服务是否稳定运行，尤其是 HTTP API、SSE 流式连接、Agent 执行和学习计划生成等高频路径是否出现异常。

本需求不建设自研监控后台。通用监控、日志检索、告警通知和 Dashboard 展示交给成熟观测系统承担，项目只补外部系统无法自动理解的业务语义信号。

## 目标

- 接入 Prometheus、Grafana、Loki、Promtail，形成开发/测试环境本地可启动的观测栈。
- 保持后端 Actuator `/actuator/health` 和 `/actuator/prometheus` 可用。
- 补齐业务指标和结构化日志，让 Grafana/Loki 能直接分析服务可用性。
- 提供 Grafana dashboard JSON 和 Prometheus 告警规则示例。
- 提供 Makefile 或脚本入口，在探测 Docker 可用后拉起本地观测栈。
- 默认不增加前端页面，不新增 `/api/admin/ops/overview`，不在应用内存储历史错误。

## 非目标

- 不做前端管理员运维页面。
- 不做应用内日志分析、错误环形缓冲或历史查询接口。
- 不做自研告警通知、静默、升级策略。
- 不接入 Sentry。
- 不接入 Elasticsearch 或 OpenSearch。
- 不设计多租户监控权限模型。
- 不把 Prometheus、Loki、Grafana SDK 或查询逻辑写入业务模块。
- 不实现生产级外置监控接入。后续分布式架构需要外置监控时，复用本期暴露的标准指标和结构化日志。

## 总体方案

一期使用项目自带的本地观测栈承载监控能力：

```text
mentor-api
  -> /actuator/prometheus        -> Prometheus -> Grafana Dashboard/Alert
  -> structured console logs     -> Promtail   -> Loki -> Grafana Logs
```

后端只负责输出稳定、低敏的指标和日志。管理员通过 Grafana 查看服务健康、HTTP 指标、SSE 稳定性、Agent/学习计划失败和错误日志。

本期 Prometheus 通过 scrape 方式采集程序暴露的 `/actuator/prometheus` 指标；Loki 通过 Promtail 收集容器 stdout 日志。后续切换到外置 Prometheus/Grafana/Loki 时，应用侧仍然只暴露同样的指标和日志，不需要把业务代码改成主动推送或调用外部监控系统。

## 模块边界

### 后端

可新增 `backend/ops-observability` 模块，或先在 `mentor-api` 中建立 `ops` 边界包。推荐优先新增独立模块，避免监控接口散落在业务代码中。

`ops-observability` 职责：

- 定义观测事件枚举、指标名、标签名和日志字段常量。
- 提供窄接口，例如 `OpsMetricsRecorder`、`SseOpsRecorder`、`AgentOpsRecorder`。
- 提供 Micrometer 实现和 no-op 实现。
- 提供结构化日志辅助工具，统一字段名和脱敏策略。

`mentor-api` 职责：

- 装配 `ops-observability`。
- 在 HTTP/SSE 边界调用观测接口。
- 保持 Actuator 和 Prometheus endpoint 暴露。
- 不实现运维聚合 API。

`mentor-application` 职责：

- 保持业务逻辑纯净。
- 必要时通过已有应用事件、返回结果或异常让 API 层记录业务观测信号。
- 不依赖 Prometheus、Loki、Grafana 或外部监控概念。

`agent-core` 职责：

- 继续通过 lifecycle/observer 暴露 Agent 运行事件。
- 不感知 HTTP、SSE、Prometheus 或 Loki。
- 需要记录 Agent 指标时，优先在 observer 或 API 编排层适配。

## 业务指标

指标命名使用 Micrometer，Prometheus 输出时会转换为下划线格式。标签必须低基数，禁止把 userId、sessionId、runId、requestId 放入指标标签。

### SSE 指标

建议指标：

- `algo.mentor.sse.connections.active`
  - Gauge
  - 标签：`stream_type`
  - 含义：当前活跃 SSE 连接数。
- `algo.mentor.sse.connections.opened`
  - Counter
  - 标签：`stream_type`
  - 含义：SSE 连接打开次数。
- `algo.mentor.sse.connections.completed`
  - Counter
  - 标签：`stream_type`
  - 含义：SSE 正常完成次数。
- `algo.mentor.sse.connections.failed`
  - Counter
  - 标签：`stream_type`、`failure_type`
  - 含义：SSE 写出失败、上游异常等失败次数。
- `algo.mentor.sse.connections.timeout`
  - Counter
  - 标签：`stream_type`
  - 含义：SSE 超时次数。
- `algo.mentor.sse.connections.client_disconnected`
  - Counter
  - 标签：`stream_type`
  - 含义：客户端断开次数。

`stream_type` 固定枚举：

- `ai_explanation`
- `learning_plan_draft`
- `practice_message`
- `agent_conversation`

`failure_type` 固定枚举：

- `send_failure`
- `upstream_error`
- `timeout`
- `unknown`

### Agent 指标

建议指标：

- `algo.mentor.agent.runs`
  - Counter
  - 标签：`source`、`status`
  - 含义：Agent run 启动、完成、失败计数。
- `algo.mentor.agent.tool.permission.decisions`
  - Counter
  - 标签：`decision`
  - 含义：工具权限允许、拒绝、超时计数。
- `algo.mentor.agent.tool.executions`
  - Counter
  - 标签：`tool_name`、`status`
  - 含义：工具执行成功和失败计数。`tool_name` 必须来自固定工具注册名。

`source` 固定枚举：

- `ai_explanation`
- `learning_plan_draft`
- `practice_message`
- `agent_conversation`

`status` 固定枚举：

- `started`
- `completed`
- `failed`

### 学习计划和练习指标

建议指标：

- `algo.mentor.learning_plan.draft.generations`
  - Counter
  - 标签：`status`
  - 含义：学习计划草案生成成功、失败计数。
- `algo.mentor.practice.message.streams`
  - Counter
  - 标签：`status`
  - 含义：题目聊天流成功、失败计数。
- `algo.mentor.practice.code_reviews`
  - Counter
  - 标签：`status`
  - 含义：代码 Review 成功、失败、不可评审计数。

## 结构化日志

现有 `logback-spring.xml` 已在控制台日志中输出 `requestId` MDC。一期继续使用控制台日志，由 Promtail 收集容器 stdout 并发送到 Loki。

新增运维日志事件时使用稳定字段，字段通过 key=value 或 JSON 形式输出。第一期可先使用 key=value，后续如需更强查询能力再引入 JSON encoder。

通用字段：

- `eventType`
- `requestId`
- `method`
- `pathTemplate`
- `status`
- `errorCode`
- `exceptionType`
- `durationMs`
- `sseStreamType`
- `agentRunId`
- `agentSource`
- `toolName`
- `failureType`

日志事件类型：

- `http_request_failed`
- `sse_connection_opened`
- `sse_connection_completed`
- `sse_connection_failed`
- `sse_connection_timeout`
- `agent_run_failed`
- `agent_tool_permission_timeout`
- `learning_plan_draft_failed`
- `practice_message_stream_failed`

安全约束：

- 不记录请求体。
- 不记录用户原始输入。
- 不记录 AI 原文输出。
- 不记录 API key、Authorization、Cookie、token、完整 session id。
- 不把 userId、email、sessionId、runId 作为 Loki label；这些只能作为日志字段，且需要评估是否脱敏。

## 部署形态

### 一期：开发/测试本地观测栈

项目提供本地部署入口，类似当前 PostgreSQL 的 Docker Compose 方式，在开发/测试环境中由项目自己拉起 Prometheus、Grafana、Loki、Promtail。

建议 Makefile 入口：

- `make observability-up`：探测 Docker 可用后启动本地观测栈。
- `make observability-down`：停止本地观测栈。
- `make observability-status`：查看本地观测栈容器状态和关键访问地址。
- `make observability-logs`：查看观测栈关键服务日志。

Docker 探测逻辑：

- 检查 `docker --version`。
- 检查 `docker compose version`。
- 检查 Docker daemon 是否可用。
- Docker 不可用时输出明确提示和手动说明，不尝试安装 Docker，不静默失败。

默认本地开发不强制启动观测栈；只有用户显式执行 `make observability-up` 或等价脚本时启动。

### 未来：外置监控

第二种部署形态先不进入一期实现。后续分布式架构需要外置监控时，由外部 Prometheus/Grafana/Loki 或云厂商观测平台采集同一套 `/actuator/prometheus` 指标和结构化日志。

一期要保证未来切换成本低：

- 指标使用标准 Prometheus/Micrometer 暴露方式。
- 日志输出到 stdout，便于容器平台或日志 agent 收集。
- 业务代码不依赖 Grafana、Loki、Prometheus 查询 API。
- 不把本地 Docker Compose 地址写入业务配置。

## Docker Compose 观测栈

使用 Docker Compose profile，避免默认本地开发启动完整观测栈。

示例命令：

```bash
docker compose -f deploy/docker/docker-compose.yml --profile observability up -d
```

新增配置建议：

```text
deploy/docker/observability/
  prometheus.yml
  prometheus-alert-rules.yml
  loki.yml
  promtail.yml
  grafana/
    provisioning/
      datasources/
        datasources.yml
      dashboards/
        dashboards.yml
    dashboards/
      algo-mentor-overview.json
```

Compose 服务：

- `prometheus`
  - 抓取 `mentor-api:8080/actuator/prometheus`。
  - 加载 `prometheus-alert-rules.yml`。
  - 容器内使用默认 `9090` 端口，宿主机监听端口固定为 `18080`，避免与本机已有 Prometheus 或其他服务冲突。
- `grafana`
  - 自动配置 Prometheus 和 Loki datasource。
  - Grafana 访问 Prometheus 使用 Docker 网络内地址 `http://prometheus:9090`，不使用宿主机 `18080`。
  - 自动加载 dashboard JSON。
- `loki`
  - 接收 Promtail 推送的容器日志。
- `promtail`
  - 收集 Docker 容器 stdout。
  - 按容器名和服务名打低基数 label。

## Grafana Dashboard

Dashboard 一期包含这些面板：

- 服务健康状态
  - `up{job="mentor-api"}`
  - Actuator health 可通过 Prometheus scrape 状态间接判断。
- HTTP 请求量
  - 基于 Spring Boot `http_server_requests_seconds_count`。
- HTTP 5xx 错误率
  - 按窗口计算 `5xx / total`。
- HTTP P95 延迟
  - 如果 histogram 已启用，使用 bucket 计算；否则展示可用的 max/avg。
- JVM 和进程基础指标
  - 内存、线程、GC、CPU。
- SSE 活跃连接和失败
  - 基于 `algo_mentor_sse_*` 指标。
- Agent run 失败
  - 基于 `algo_mentor_agent_runs_total`。
- 学习计划生成失败
  - 基于 `algo_mentor_learning_plan_draft_generations_total`。
- Loki 错误日志
  - 查询 `eventType` 为失败类事件的日志。

## Prometheus 告警规则示例

一期提供示例规则，不在应用内做告警。

建议规则：

- `AlgoMentorApiDown`
  - `up{job="mentor-api"} == 0`
  - 持续 1 分钟触发 critical。
- `AlgoMentorHighHttp5xxRate`
  - 最近 5 分钟 5xx 错误率超过 5%，且请求数超过 20。
  - warning 或 critical 可按阈值拆分。
- `AlgoMentorHighSseFailureRate`
  - 最近 10 分钟 SSE failed/timeout 过高。
- `AlgoMentorAgentRunFailures`
  - 最近 10 分钟 Agent run failed 过高。
- `AlgoMentorPostgresUnavailable`
  - mentor-api health 或连接池指标显示数据库不可用。

## 配置

保留现有 Actuator 配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

如需支持 P95 延迟，增加 histogram 配置：

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

Prometheus/Grafana/Loki 地址通过 Docker Compose 内部网络连接，不写入业务配置。未来生产或分布式环境是否启用外部 Prometheus 抓取，由部署平台配置，不属于一期实现。

## 测试策略

后端单元测试：

- `ops-observability` 指标 recorder 测试：
  - 记录 SSE opened/completed/failed 后，Counter/Gauge 值正确。
  - 不允许动态高基数字段进入 tag。
- SSE subscriber 测试：
  - 正常完成记录 completed。
  - 上游 error 记录 failed。
  - send failure 记录 client disconnect 或 send failure。
  - timeout callback 记录 timeout。
- Agent observer 或 API 编排层测试：
  - Agent run failed 记录固定标签。
  - 工具权限 timeout 记录固定标签。

配置测试：

- Prometheus 配置能解析。
- Grafana datasource/dashboard provisioning 文件路径正确。
- Promtail 能匹配 `mentor-api` 容器日志。

手动验证：

```bash
make observability-up
curl http://localhost:8080/actuator/prometheus
curl http://localhost:18080/-/ready
curl http://localhost:3000/api/health
```

`make observability-up` 底层可封装 `docker compose -f deploy/docker/docker-compose.yml --profile observability up -d`，但日常使用优先通过 Makefile 入口执行 Docker 探测和提示逻辑。

验证 Grafana 中 dashboard 有数据，Loki 能查到 `eventType` 相关日志。

## 风险与取舍

- 不做前端页面意味着管理员需要使用 Grafana；这是有意取舍，避免重复建设监控系统。
- Loki 日志字段如果用 key=value，查询体验不如 JSON。第一期优先简单可用，后续可引入 JSON encoder。
- Prometheus 指标标签必须严格控制低基数，否则会带来存储和查询压力。
- Docker Compose 观测栈只定位开发/测试环境。未来生产或分布式部署需要外置监控时，复用本期标准指标和结构化日志。
- `agentRunId`、`requestId` 等不应作为指标标签，也不应作为 Loki label；需要查单次问题时通过日志全文字段查询。

## 验收标准

- `make observability-up` 能在 Docker 可用时启动 Prometheus、Grafana、Loki、Promtail。
- Docker 不可用时，`make observability-up` 输出明确错误和手动说明。
- 使用 Compose profile 能启动 Prometheus、Grafana、Loki、Promtail。
- Prometheus 在宿主机监听 `18080`，容器内和 Docker 网络内仍使用默认 `9090`。
- Prometheus 能抓取 mentor-api 指标。
- Grafana 自动加载 datasource 和 `algo-mentor` dashboard。
- Loki 能查询 mentor-api 结构化运维日志。
- SSE、Agent、学习计划等核心业务路径有低基数业务指标。
- 日志和指标不泄露密钥、token、用户输入或 AI 输出。
- 一期没有新增前端页面和管理员运维 API。
