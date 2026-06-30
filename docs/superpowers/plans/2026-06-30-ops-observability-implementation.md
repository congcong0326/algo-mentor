# Ops Observability Phase One Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为开发/测试环境接入本地 Prometheus、Grafana、Loki、Promtail 观测栈，并在后端补齐低基数业务指标和低敏结构化日志。

**Architecture:** 新增 `backend/ops-observability` 作为观测边界模块，集中定义指标名、标签枚举、recorder 接口、Micrometer 实现、Agent observer 和低敏日志字段工具。`mentor-api` 只负责装配模块、在 SSE/API 边界调用 recorder，并继续通过 Actuator 暴露 `/actuator/prometheus`；Docker Compose 只提供开发/测试本地观测栈，不把 Grafana/Loki/Prometheus 查询逻辑写入业务代码。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC SSE, Maven 多模块, Micrometer/Prometheus, SLF4J/Logback, Docker Compose, Prometheus, Grafana, Loki, Promtail.

---

## Reference Specs

- `docs/superpowers/specs/2026-06-29-ops-observability-design.md`
- `docs/code-index.md`
- `Makefile`
- `deploy/docker/docker-compose.yml`
- `backend/mentor-api/src/main/resources/application.yml`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/SseLlmStreamSubscriber.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanDraftStreamSubscriber.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopObserver.java`

## Scope Boundaries

本计划只实现一期范围：

- 做开发/测试本地观测栈：Prometheus、Grafana、Loki、Promtail。
- `mentor-api` 保持监听 `8080`。
- Prometheus 抓取 `mentor-api:8080/actuator/prometheus`。
- Prometheus 自身使用默认 `9090`。
- Grafana 通过 Docker 网络访问 `http://prometheus:9090`。
- Promtail 收集容器 stdout 日志。
- 不新增前端管理员页面。
- 不新增 `/api/admin/ops/overview`。
- 不做应用内日志分析、错误环形缓冲或历史查询接口。
- 不接入 Sentry、Elasticsearch、OpenSearch。
- 不把 Prometheus、Grafana、Loki 查询 SDK 或推送逻辑写入业务代码。

## Execution Setup

建议执行前创建隔离 worktree：

```bash
git worktree add .worktrees/ops-observability -b feat/ops-observability
cd .worktrees/ops-observability
```

基线验证：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository test
```

如果基线失败，先记录失败项，不要在本计划中修复无关测试。

## File Structure

### Docker Observability

- Modify: `deploy/docker/docker-compose.yml`<br>
  增加 `observability` profile 下的 `prometheus`、`grafana`、`loki`、`promtail` 服务，并让服务加入默认 Compose 网络。
- Create: `deploy/docker/observability/prometheus.yml`<br>
  Prometheus scrape 配置，抓取 `mentor-api:8080/actuator/prometheus` 和自身 `localhost:9090`。
- Create: `deploy/docker/observability/prometheus-alert-rules.yml`<br>
  示例告警规则，不负责通知发送。
- Create: `deploy/docker/observability/loki.yml`<br>
  本地单节点 Loki 配置。
- Create: `deploy/docker/observability/promtail.yml`<br>
  收集 Docker 容器 stdout 日志，低基数 label 只使用 compose/service/container/job。
- Create: `deploy/docker/observability/grafana/provisioning/datasources/datasources.yml`<br>
  自动配置 Prometheus 和 Loki datasource。
- Create: `deploy/docker/observability/grafana/provisioning/dashboards/dashboards.yml`<br>
  自动加载 dashboard JSON。
- Create: `deploy/docker/observability/grafana/dashboards/algo-mentor-overview.json`<br>
  Grafana dashboard JSON。

### Makefile and Docker Detection

- Modify: `Makefile`<br>
  新增 `observability-up`、`observability-down`、`observability-status`、`observability-logs` 入口。
- Create: `deploy/docker/observability/check-docker.sh`<br>
  Makefile 调用的 Docker 可用性探测脚本。

### Backend Module

- Modify: `backend/pom.xml`<br>
  新增 Maven module `ops-observability`。
- Create: `backend/ops-observability/pom.xml`<br>
  依赖 `agent-core`、Micrometer、SLF4J、Spring Boot autoconfigure/test。
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/OpsMetricNames.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/OpsLogFields.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/OpsLogEventType.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/SseStreamType.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/SseFailureType.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/OpsStatus.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/AgentOpsSource.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/AgentOpsRecorder.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/SseOpsRecorder.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/LearningOpsRecorder.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/NoopOpsRecorders.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/MicrometerOpsRecorders.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/AgentOpsObserver.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/StructuredOpsLogger.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/autoconfigure/OpsObservabilityAutoConfiguration.java`
- Create: `backend/ops-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Backend Wiring and Instrumentation

- Modify: `backend/mentor-api/pom.xml`<br>
  依赖 `ops-observability`。
- Modify: `backend/mentor-api/src/main/resources/application.yml`<br>
  开启 HTTP server histogram，便于 dashboard 计算 P95。
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/SseLlmStreamSubscriber.java`<br>
  为 AI explanation、practice message、agent conversation 等 Agent SSE 流记录 opened/completed/failed/timeout/client disconnected。
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanDraftStreamSubscriber.java`<br>
  为学习计划草案 SSE 流记录指标和结构化日志。
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`<br>
  如需新增共享 path/template 常量，放在这里，不在 controller 内重复字面量。

### Tests

- Create: `backend/ops-observability/src/test/java/org/congcong/algomentor/ops/observability/MicrometerOpsRecordersTest.java`
- Create: `backend/ops-observability/src/test/java/org/congcong/algomentor/ops/observability/StructuredOpsLoggerTest.java`
- Create: `backend/ops-observability/src/test/java/org/congcong/algomentor/ops/observability/AgentOpsObserverTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/service/SseLlmStreamSubscriberOpsTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanDraftStreamSubscriberOpsTest.java`
- Create: `deploy/docker/observability/README.md`<br>
  中文说明本地访问地址、验证命令和非生产定位。

---

### Task 1: Docker Observability Stack

**Files:**
- Modify: `deploy/docker/docker-compose.yml`
- Create: `deploy/docker/observability/prometheus.yml`
- Create: `deploy/docker/observability/prometheus-alert-rules.yml`
- Create: `deploy/docker/observability/loki.yml`
- Create: `deploy/docker/observability/promtail.yml`
- Create: `deploy/docker/observability/grafana/provisioning/datasources/datasources.yml`
- Create: `deploy/docker/observability/grafana/provisioning/dashboards/dashboards.yml`
- Create: `deploy/docker/observability/grafana/dashboards/algo-mentor-overview.json`
- Create: `deploy/docker/observability/README.md`

- [ ] **Step 1: Create Prometheus config**

Create `deploy/docker/observability/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/prometheus-alert-rules.yml

scrape_configs:
  - job_name: prometheus
    static_configs:
      - targets:
          - localhost:9090

  - job_name: mentor-api
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - mentor-api:8080
```

- [ ] **Step 2: Create alert rules**

Create `deploy/docker/observability/prometheus-alert-rules.yml`:

```yaml
groups:
  - name: algo-mentor-api
    rules:
      - alert: AlgoMentorApiDown
        expr: up{job="mentor-api"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: Algo Mentor API is down
          description: Prometheus cannot scrape mentor-api:8080/actuator/prometheus.

      - alert: AlgoMentorHighHttp5xxRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{application="algo-mentor-api",status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count{application="algo-mentor-api"}[5m]))
          ) > 0.05
          and
          sum(increase(http_server_requests_seconds_count{application="algo-mentor-api"}[5m])) > 20
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: Algo Mentor API 5xx rate is high
          description: HTTP 5xx rate exceeded 5% over 5 minutes with more than 20 requests.

      - alert: AlgoMentorHighSseFailureRate
        expr: |
          (
            sum(increase(algo_mentor_sse_connections_failed_total[10m]))
            +
            sum(increase(algo_mentor_sse_connections_timeout_total[10m]))
          ) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: Algo Mentor SSE failures are elevated
          description: SSE failed or timeout count exceeded 5 over 10 minutes.

      - alert: AlgoMentorAgentRunFailures
        expr: sum(increase(algo_mentor_agent_runs_total{status="failed"}[10m])) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: Algo Mentor Agent run failures are elevated
          description: Agent run failures exceeded 3 over 10 minutes.

      - alert: AlgoMentorPostgresUnavailable
        expr: jdbc_connections_active{application="algo-mentor-api"} == 0 and up{job="mentor-api"} == 1
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: Algo Mentor PostgreSQL pool has no active signal
          description: mentor-api is scrapeable but JDBC connection metrics show no active database signal.
```

- [ ] **Step 3: Create Loki config**

Create `deploy/docker/observability/loki.yml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093
```

- [ ] **Step 4: Create Promtail config**

Create `deploy/docker/observability/promtail.yml`:

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
    relabel_configs:
      - source_labels: ['__meta_docker_container_name']
        regex: '/(.*)'
        target_label: container
      - source_labels: ['__meta_docker_container_label_com_docker_compose_service']
        target_label: compose_service
      - source_labels: ['__meta_docker_container_label_com_docker_compose_project']
        target_label: compose_project
      - source_labels: ['__meta_docker_container_label_com_docker_compose_service']
        regex: 'mentor-api|prometheus|grafana|loki'
        action: keep
      - target_label: job
        replacement: algo-mentor-docker
```

- [ ] **Step 5: Create Grafana datasource provisioning**

Create `deploy/docker/observability/grafana/provisioning/datasources/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true
```

- [ ] **Step 6: Create Grafana dashboard provisioning**

Create `deploy/docker/observability/grafana/provisioning/dashboards/dashboards.yml`:

```yaml
apiVersion: 1

providers:
  - name: algo-mentor
    orgId: 1
    folder: Algo Mentor
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
```

- [ ] **Step 7: Create initial dashboard JSON**

Create `deploy/docker/observability/grafana/dashboards/algo-mentor-overview.json` with panels for service health, HTTP request rate, HTTP 5xx rate, HTTP latency, JVM memory, JVM threads, SSE active connections, SSE failures, Agent failures, learning plan failures, and Loki error logs. Use this minimal valid dashboard:

```json
{
  "uid": "algo-mentor-overview",
  "title": "Algo Mentor Overview",
  "timezone": "browser",
  "schemaVersion": 39,
  "version": 1,
  "refresh": "15s",
  "tags": ["algo-mentor", "local"],
  "panels": [
    {
      "id": 1,
      "type": "stat",
      "title": "mentor-api up",
      "gridPos": {"x": 0, "y": 0, "w": 6, "h": 4},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "up{job=\"mentor-api\"}", "refId": "A"}]
    },
    {
      "id": 2,
      "type": "timeseries",
      "title": "HTTP requests / second",
      "gridPos": {"x": 6, "y": 0, "w": 9, "h": 4},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "sum(rate(http_server_requests_seconds_count{application=\"algo-mentor-api\"}[5m])) by (method, uri)", "refId": "A"}]
    },
    {
      "id": 3,
      "type": "timeseries",
      "title": "HTTP 5xx rate",
      "gridPos": {"x": 15, "y": 0, "w": 9, "h": 4},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "sum(rate(http_server_requests_seconds_count{application=\"algo-mentor-api\",status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count{application=\"algo-mentor-api\"}[5m]))", "refId": "A"}]
    },
    {
      "id": 4,
      "type": "timeseries",
      "title": "HTTP P95 latency",
      "gridPos": {"x": 0, "y": 4, "w": 12, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application=\"algo-mentor-api\"}[5m])) by (le, uri))", "refId": "A"}]
    },
    {
      "id": 5,
      "type": "timeseries",
      "title": "JVM memory used",
      "gridPos": {"x": 12, "y": 4, "w": 6, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "sum(jvm_memory_used_bytes{application=\"algo-mentor-api\"}) by (area)", "refId": "A"}]
    },
    {
      "id": 6,
      "type": "timeseries",
      "title": "JVM live threads",
      "gridPos": {"x": 18, "y": 4, "w": 6, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "jvm_threads_live_threads{application=\"algo-mentor-api\"}", "refId": "A"}]
    },
    {
      "id": 7,
      "type": "timeseries",
      "title": "SSE active connections",
      "gridPos": {"x": 0, "y": 10, "w": 8, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "algo_mentor_sse_connections_active", "refId": "A"}]
    },
    {
      "id": 8,
      "type": "timeseries",
      "title": "SSE failures",
      "gridPos": {"x": 8, "y": 10, "w": 8, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "sum(increase(algo_mentor_sse_connections_failed_total[5m])) by (stream_type, failure_type)", "refId": "A"}]
    },
    {
      "id": 9,
      "type": "timeseries",
      "title": "Agent run failures",
      "gridPos": {"x": 16, "y": 10, "w": 8, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "sum(increase(algo_mentor_agent_runs_total{status=\"failed\"}[5m])) by (source)", "refId": "A"}]
    },
    {
      "id": 10,
      "type": "timeseries",
      "title": "Learning plan draft failures",
      "gridPos": {"x": 0, "y": 16, "w": 12, "h": 6},
      "datasource": {"type": "prometheus", "uid": "Prometheus"},
      "targets": [{"expr": "sum(increase(algo_mentor_learning_plan_draft_generations_total{status=\"failed\"}[5m]))", "refId": "A"}]
    },
    {
      "id": 11,
      "type": "logs",
      "title": "Ops failure logs",
      "gridPos": {"x": 12, "y": 16, "w": 12, "h": 6},
      "datasource": {"type": "loki", "uid": "Loki"},
      "targets": [{"expr": "{job=\"algo-mentor-docker\", compose_service=\"mentor-api\"} |= \"eventType=\" |~ \"failed|timeout\"", "refId": "A"}]
    }
  ]
}
```

- [ ] **Step 8: Extend Compose**

Modify `deploy/docker/docker-compose.yml` by adding services:

```yaml
  prometheus:
    image: prom/prometheus:v2.55.1
    container_name: algo-mentor-prometheus
    profiles:
      - observability
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --storage.tsdb.path=/prometheus
      - --web.enable-lifecycle
    volumes:
      - ./observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./observability/prometheus-alert-rules.yml:/etc/prometheus/prometheus-alert-rules.yml:ro
      - prometheus-data:/prometheus
    ports:
      - "127.0.0.1:${PROMETHEUS_PORT:-9090}:9090"
    depends_on:
      - mentor-api

  loki:
    image: grafana/loki:3.3.2
    container_name: algo-mentor-loki
    profiles:
      - observability
    command:
      - -config.file=/etc/loki/local-config.yml
    volumes:
      - ./observability/loki.yml:/etc/loki/local-config.yml:ro
      - loki-data:/loki
    ports:
      - "127.0.0.1:${LOKI_PORT:-3100}:3100"

  promtail:
    image: grafana/promtail:3.3.2
    container_name: algo-mentor-promtail
    profiles:
      - observability
    command:
      - -config.file=/etc/promtail/config.yml
    volumes:
      - ./observability/promtail.yml:/etc/promtail/config.yml:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
    depends_on:
      - loki

  grafana:
    image: grafana/grafana:11.4.0
    container_name: algo-mentor-grafana
    profiles:
      - observability
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./observability/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./observability/grafana/dashboards:/var/lib/grafana/dashboards:ro
    ports:
      - "127.0.0.1:${GRAFANA_PORT:-3000}:3000"
    depends_on:
      - prometheus
      - loki
```

Also extend `volumes:`:

```yaml
  prometheus-data:
  loki-data:
  grafana-data:
```

- [ ] **Step 9: Add local README**

Create `deploy/docker/observability/README.md`:

```markdown
# 本地观测栈

本目录只服务开发/测试环境。默认本地开发不会启动观测栈，只有显式执行 `make observability-up` 时启动 Prometheus、Grafana、Loki、Promtail。

访问地址：

- mentor-api: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000
- Loki: http://localhost:3100

默认 Grafana 账号密码为 `admin` / `admin`，可通过 `GRAFANA_ADMIN_USER` 和 `GRAFANA_ADMIN_PASSWORD` 覆盖。

Prometheus 通过 Docker 网络抓取 `mentor-api:8080/actuator/prometheus`。Grafana 通过 Docker 网络访问 `http://prometheus:9090` 和 `http://loki:3100`。
```

- [ ] **Step 10: Validate Compose config**

Run:

```bash
docker compose -f deploy/docker/docker-compose.yml --profile observability config >/tmp/algo-mentor-observability-compose.yml
```

Expected: exit code `0`; output file contains `prometheus`, `grafana`, `loki`, `promtail`.

- [ ] **Step 11: Commit**

```bash
git add deploy/docker/docker-compose.yml deploy/docker/observability
git commit -m "chore: add local observability stack"
```

---

### Task 2: Makefile Observability Commands and Docker Detection

**Files:**
- Modify: `Makefile`
- Create: `deploy/docker/observability/check-docker.sh`

- [ ] **Step 1: Create Docker detection script**

Create `deploy/docker/observability/check-docker.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI is not installed or not in PATH." >&2
  echo "Install Docker, then retry: make observability-up" >&2
  exit 1
fi

if ! docker --version >/dev/null 2>&1; then
  echo "Docker CLI is installed but docker --version failed." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is not available. Expected: docker compose version" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not reachable." >&2
  echo "Start Docker Desktop or the Docker service, then retry: make observability-up" >&2
  exit 1
fi
```

- [ ] **Step 2: Make script executable**

Run:

```bash
chmod +x deploy/docker/observability/check-docker.sh
```

Expected: `test -x deploy/docker/observability/check-docker.sh` succeeds.

- [ ] **Step 3: Extend Makefile variables and phony targets**

Modify `Makefile`:

```make
OBSERVABILITY_PROFILE := --profile observability
OBSERVABILITY_CHECK := deploy/docker/observability/check-docker.sh
```

Extend `.PHONY` with:

```make
observability-up observability-down observability-status observability-logs observability-check
```

- [ ] **Step 4: Add Makefile targets**

Add below `down:`:

```make
observability-check:
	$(OBSERVABILITY_CHECK)

observability-up: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) up -d prometheus grafana loki promtail
	@echo "Prometheus: http://localhost:$${PROMETHEUS_PORT:-9090}"
	@echo "Grafana:    http://localhost:$${GRAFANA_PORT:-3000}"
	@echo "Loki:       http://localhost:$${LOKI_PORT:-3100}"

observability-down: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) down

observability-status: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) ps prometheus grafana loki promtail
	@echo "Prometheus readiness: curl http://localhost:$${PROMETHEUS_PORT:-9090}/-/ready"
	@echo "Grafana health:       curl http://localhost:$${GRAFANA_PORT:-3000}/api/health"
	@echo "Loki ready:           curl http://localhost:$${LOKI_PORT:-3100}/ready"

observability-logs: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) logs -f --tail=200 prometheus grafana loki promtail
```

- [ ] **Step 5: Verify Make targets resolve**

Run:

```bash
make -n observability-up
make -n observability-down
make -n observability-status
make -n observability-logs
```

Expected: each command prints the Docker check and `docker compose -f deploy/docker/docker-compose.yml --profile observability ...` command without make syntax errors.

- [ ] **Step 6: Commit**

```bash
git add Makefile deploy/docker/observability/check-docker.sh
git commit -m "chore: add observability make targets"
```

---

### Task 3: `ops-observability` Module Contracts

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/ops-observability/pom.xml`
- Create module Java files under `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability`
- Test: `backend/ops-observability/src/test/java/org/congcong/algomentor/ops/observability/MicrometerOpsRecordersTest.java`

- [ ] **Step 1: Add module to Maven root**

Modify `backend/pom.xml`:

```xml
    <module>ops-observability</module>
```

Place it before `mentor-api` so API can depend on it.

- [ ] **Step 2: Create module POM**

Create `backend/ops-observability/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.congcong.algomentor</groupId>
    <artifactId>algo-mentor-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>

  <artifactId>ops-observability</artifactId>
  <name>algo-mentor-ops-observability</name>

  <dependencies>
    <dependency>
      <groupId>org.congcong.algomentor</groupId>
      <artifactId>agent-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.micrometer</groupId>
      <artifactId>micrometer-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: Write failing recorder test**

Create `MicrometerOpsRecordersTest.java`:

```java
package org.congcong.algomentor.ops.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerOpsRecordersTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final SseOpsRecorder sse = MicrometerOpsRecorders.sse(registry);
  private final AgentOpsRecorder agent = MicrometerOpsRecorders.agent(registry);
  private final LearningOpsRecorder learning = MicrometerOpsRecorders.learning(registry);

  @Test
  void recordsSseCountersAndActiveGauge() {
    sse.opened(SseStreamType.PRACTICE_MESSAGE);
    sse.completed(SseStreamType.PRACTICE_MESSAGE);
    sse.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.SEND_FAILURE);
    sse.timeout(SseStreamType.PRACTICE_MESSAGE);
    sse.clientDisconnected(SseStreamType.PRACTICE_MESSAGE);

    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_OPENED, "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_COMPLETED, "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_FAILED, "stream_type", "practice_message", "failure_type", "send_failure")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_TIMEOUT, "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_CLIENT_DISCONNECTED, "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(registry.get(OpsMetricNames.SSE_CONNECTIONS_ACTIVE).tag("stream_type", "practice_message").gauge().value()).isEqualTo(0.0);
  }

  @Test
  void recordsAgentAndLearningCounters() {
    agent.runStarted(AgentOpsSource.AGENT_CONVERSATION);
    agent.runCompleted(AgentOpsSource.AGENT_CONVERSATION);
    agent.runFailed(AgentOpsSource.AGENT_CONVERSATION);
    agent.toolPermissionDecision("allow");
    agent.toolExecution("submit_practice_code_review", OpsStatus.FAILED);
    learning.learningPlanDraft(OpsStatus.FAILED);
    learning.practiceMessageStream(OpsStatus.COMPLETED);
    learning.practiceCodeReview(OpsStatus.UNREVIEWABLE);

    assertThat(counter(OpsMetricNames.AGENT_RUNS, "source", "agent_conversation", "status", "started")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_RUNS, "source", "agent_conversation", "status", "completed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_RUNS, "source", "agent_conversation", "status", "failed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS, "decision", "allow")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_EXECUTIONS, "tool_name", "submit_practice_code_review", "status", "failed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.LEARNING_PLAN_DRAFT_GENERATIONS, "status", "failed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_MESSAGE_STREAMS, "status", "completed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_CODE_REVIEWS, "status", "unreviewable")).isEqualTo(1.0);
  }

  private double counter(String name, String... tags) {
    return registry.get(name).tags(tags).counter().count();
  }
}
```

- [ ] **Step 4: Run RED**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ops-observability -Dtest=MicrometerOpsRecordersTest test
```

Expected: compilation fails because module classes do not exist.

- [ ] **Step 5: Implement constants and enums**

Create these files:

```java
package org.congcong.algomentor.ops.observability;

public final class OpsMetricNames {
  public static final String SSE_CONNECTIONS_ACTIVE = "algo.mentor.sse.connections.active";
  public static final String SSE_CONNECTIONS_OPENED = "algo.mentor.sse.connections.opened";
  public static final String SSE_CONNECTIONS_COMPLETED = "algo.mentor.sse.connections.completed";
  public static final String SSE_CONNECTIONS_FAILED = "algo.mentor.sse.connections.failed";
  public static final String SSE_CONNECTIONS_TIMEOUT = "algo.mentor.sse.connections.timeout";
  public static final String SSE_CONNECTIONS_CLIENT_DISCONNECTED = "algo.mentor.sse.connections.client_disconnected";
  public static final String AGENT_RUNS = "algo.mentor.agent.runs";
  public static final String AGENT_TOOL_PERMISSION_DECISIONS = "algo.mentor.agent.tool.permission.decisions";
  public static final String AGENT_TOOL_EXECUTIONS = "algo.mentor.agent.tool.executions";
  public static final String LEARNING_PLAN_DRAFT_GENERATIONS = "algo.mentor.learning_plan.draft.generations";
  public static final String PRACTICE_MESSAGE_STREAMS = "algo.mentor.practice.message.streams";
  public static final String PRACTICE_CODE_REVIEWS = "algo.mentor.practice.code_reviews";

  private OpsMetricNames() {}
}
```

```java
package org.congcong.algomentor.ops.observability;

public enum SseStreamType {
  AI_EXPLANATION("ai_explanation"),
  LEARNING_PLAN_DRAFT("learning_plan_draft"),
  PRACTICE_MESSAGE("practice_message"),
  AGENT_CONVERSATION("agent_conversation");

  private final String tagValue;

  SseStreamType(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
```

```java
package org.congcong.algomentor.ops.observability;

public enum SseFailureType {
  SEND_FAILURE("send_failure"),
  UPSTREAM_ERROR("upstream_error"),
  TIMEOUT("timeout"),
  UNKNOWN("unknown");

  private final String tagValue;

  SseFailureType(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
```

```java
package org.congcong.algomentor.ops.observability;

public enum OpsStatus {
  STARTED("started"),
  COMPLETED("completed"),
  FAILED("failed"),
  UNREVIEWABLE("unreviewable");

  private final String tagValue;

  OpsStatus(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
```

```java
package org.congcong.algomentor.ops.observability;

public enum AgentOpsSource {
  AI_EXPLANATION("ai_explanation"),
  LEARNING_PLAN_DRAFT("learning_plan_draft"),
  PRACTICE_MESSAGE("practice_message"),
  AGENT_CONVERSATION("agent_conversation");

  private final String tagValue;

  AgentOpsSource(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
```

- [ ] **Step 6: Implement recorder interfaces**

Create:

```java
package org.congcong.algomentor.ops.observability;

public interface SseOpsRecorder {
  void opened(SseStreamType streamType);
  void completed(SseStreamType streamType);
  void failed(SseStreamType streamType, SseFailureType failureType);
  void timeout(SseStreamType streamType);
  void clientDisconnected(SseStreamType streamType);
}
```

```java
package org.congcong.algomentor.ops.observability;

public interface AgentOpsRecorder {
  void runStarted(AgentOpsSource source);
  void runCompleted(AgentOpsSource source);
  void runFailed(AgentOpsSource source);
  void toolPermissionDecision(String decision);
  void toolExecution(String toolName, OpsStatus status);
}
```

```java
package org.congcong.algomentor.ops.observability;

public interface LearningOpsRecorder {
  void learningPlanDraft(OpsStatus status);
  void practiceMessageStream(OpsStatus status);
  void practiceCodeReview(OpsStatus status);
}
```

- [ ] **Step 7: Implement no-op and Micrometer recorders**

Create `NoopOpsRecorders.java` with no-op singleton implementations.

Create `MicrometerOpsRecorders.java` with `Counter.builder(...)` and `Gauge.builder(...)`; use tag keys exactly:

- `stream_type`
- `failure_type`
- `source`
- `status`
- `decision`
- `tool_name`

Use `ConcurrentHashMap<SseStreamType, AtomicInteger>` for active SSE gauges. `opened` increments the gauge, and every terminal method decrements it with `Math.max(0, value - 1)`.

- [ ] **Step 8: Run GREEN**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ops-observability -Dtest=MicrometerOpsRecordersTest test
```

Expected: test passes.

- [ ] **Step 9: Commit**

```bash
git add backend/pom.xml backend/ops-observability
git commit -m "feat: add ops observability module"
```

---

### Task 4: Auto-Configuration and Agent Metrics

**Files:**
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/AgentOpsObserver.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/autoconfigure/OpsObservabilityAutoConfiguration.java`
- Create: `backend/ops-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `backend/mentor-api/pom.xml`
- Test: `backend/ops-observability/src/test/java/org/congcong/algomentor/ops/observability/AgentOpsObserverTest.java`

- [ ] **Step 1: Write Agent observer test**

Create `AgentOpsObserverTest.java`. Use a fake `AgentOpsRecorder` and construct minimal `AgentLoopContext` from existing test builders in `agent-core`; if no builder exists, instantiate the context exactly as current `AgentLoopRunnerTest` does.

Assertions:

- `onRunStart` records `started`.
- `onRunEnd` records `completed`.
- `onError` records `failed`.
- `onToolEnd` records `completed` for the tool name.
- `onToolError` records `failed` for the tool name.
- `onToolPermissionDecision` maps allow/deny to lowercase decision tag.
- `onToolPermissionTimeout` records `timeout`.

- [ ] **Step 2: Run RED**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ops-observability -Dtest=AgentOpsObserverTest test
```

Expected: compilation fails because `AgentOpsObserver` does not exist.

- [ ] **Step 3: Implement source resolver**

In `AgentOpsObserver`, map low-cardinality source from metadata and run context:

```java
private AgentOpsSource source(AgentLoopContext context) {
  Object aiSource = context.metadata().get("aiRunSource");
  String value = aiSource == null ? "" : aiSource.toString();
  return switch (value) {
    case "LEARNING_PLAN_DRAFT", "learning_plan_draft" -> AgentOpsSource.LEARNING_PLAN_DRAFT;
    case "PRACTICE_CHAT", "practice_message" -> AgentOpsSource.PRACTICE_MESSAGE;
    case "LEARNING_CHAT", "agent_conversation" -> AgentOpsSource.AGENT_CONVERSATION;
    default -> AgentOpsSource.AGENT_CONVERSATION;
  };
}
```

Do not use `runId`, `userId`, `sessionId` as metric tags.

- [ ] **Step 4: Implement auto-configuration**

Create `OpsObservabilityAutoConfiguration.java`:

```java
package org.congcong.algomentor.ops.observability.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.ops.observability.AgentOpsObserver;
import org.congcong.algomentor.ops.observability.AgentOpsRecorder;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.MicrometerOpsRecorders;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpsObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  SseOpsRecorder sseOpsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? NoopOpsRecorders.sse() : MicrometerOpsRecorders.sse(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  AgentOpsRecorder agentOpsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? NoopOpsRecorders.agent() : MicrometerOpsRecorders.agent(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  LearningOpsRecorder learningOpsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? NoopOpsRecorders.learning() : MicrometerOpsRecorders.learning(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  StructuredOpsLogger structuredOpsLogger() {
    return new StructuredOpsLogger();
  }

  @Bean
  AgentLoopObserver agentOpsObserver(AgentOpsRecorder recorder, StructuredOpsLogger logger) {
    return new AgentOpsObserver(recorder, logger);
  }
}
```

Create `AutoConfiguration.imports`:

```text
org.congcong.algomentor.ops.observability.autoconfigure.OpsObservabilityAutoConfiguration
```

- [ ] **Step 5: Add mentor-api dependency**

Modify `backend/mentor-api/pom.xml`:

```xml
    <dependency>
      <groupId>org.congcong.algomentor</groupId>
      <artifactId>ops-observability</artifactId>
      <version>${project.version}</version>
    </dependency>
```

- [ ] **Step 6: Run GREEN**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ops-observability,mentor-api -am -Dtest=AgentOpsObserverTest test
```

Expected: test passes and `mentor-api` compiles with the new dependency.

- [ ] **Step 7: Commit**

```bash
git add backend/ops-observability backend/mentor-api/pom.xml
git commit -m "feat: wire agent observability metrics"
```

---

### Task 5: Structured Low-Sensitivity Logs

**Files:**
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/OpsLogFields.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/OpsLogEventType.java`
- Create: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/StructuredOpsLogger.java`
- Test: `backend/ops-observability/src/test/java/org/congcong/algomentor/ops/observability/StructuredOpsLoggerTest.java`

- [ ] **Step 1: Write structured logger test**

Create `StructuredOpsLoggerTest.java`:

```java
package org.congcong.algomentor.ops.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredOpsLoggerTest {

  private final StructuredOpsLogger logger = new StructuredOpsLogger();

  @Test
  void formatsStableKeyValueFieldsAndRedactsSensitiveValues() {
    String line = logger.format(
        OpsLogEventType.SSE_CONNECTION_FAILED,
        Map.of(
            OpsLogFields.REQUEST_ID, "rid-1",
            OpsLogFields.AUTHORIZATION, "Bearer secret",
            OpsLogFields.SSE_STREAM_TYPE, "practice_message",
            OpsLogFields.FAILURE_TYPE, "send_failure"));

    assertThat(line).contains("eventType=sse_connection_failed");
    assertThat(line).contains("requestId=rid-1");
    assertThat(line).contains("sseStreamType=practice_message");
    assertThat(line).contains("failureType=send_failure");
    assertThat(line).contains("authorization=[REDACTED]");
    assertThat(line).doesNotContain("Bearer secret");
  }
}
```

- [ ] **Step 2: Run RED**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ops-observability -Dtest=StructuredOpsLoggerTest test
```

Expected: compilation fails.

- [ ] **Step 3: Implement log constants**

Create `OpsLogFields.java`:

```java
package org.congcong.algomentor.ops.observability;

public final class OpsLogFields {
  public static final String EVENT_TYPE = "eventType";
  public static final String REQUEST_ID = "requestId";
  public static final String METHOD = "method";
  public static final String PATH_TEMPLATE = "pathTemplate";
  public static final String STATUS = "status";
  public static final String ERROR_CODE = "errorCode";
  public static final String EXCEPTION_TYPE = "exceptionType";
  public static final String DURATION_MS = "durationMs";
  public static final String SSE_STREAM_TYPE = "sseStreamType";
  public static final String AGENT_RUN_ID = "agentRunId";
  public static final String AGENT_SOURCE = "agentSource";
  public static final String TOOL_NAME = "toolName";
  public static final String FAILURE_TYPE = "failureType";
  public static final String AUTHORIZATION = "authorization";
  public static final String COOKIE = "cookie";
  public static final String TOKEN = "token";

  private OpsLogFields() {}
}
```

Create `OpsLogEventType.java`:

```java
package org.congcong.algomentor.ops.observability;

public enum OpsLogEventType {
  HTTP_REQUEST_FAILED("http_request_failed"),
  SSE_CONNECTION_OPENED("sse_connection_opened"),
  SSE_CONNECTION_COMPLETED("sse_connection_completed"),
  SSE_CONNECTION_FAILED("sse_connection_failed"),
  SSE_CONNECTION_TIMEOUT("sse_connection_timeout"),
  AGENT_RUN_FAILED("agent_run_failed"),
  AGENT_TOOL_PERMISSION_TIMEOUT("agent_tool_permission_timeout"),
  LEARNING_PLAN_DRAFT_FAILED("learning_plan_draft_failed"),
  PRACTICE_MESSAGE_STREAM_FAILED("practice_message_stream_failed");

  private final String value;

  OpsLogEventType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
```

- [ ] **Step 4: Implement formatter and log methods**

Create `StructuredOpsLogger.java` with:

- `format(OpsLogEventType eventType, Map<String, ?> fields)` for tests.
- `info(Logger log, OpsLogEventType eventType, Map<String, ?> fields)`.
- `warn(Logger log, OpsLogEventType eventType, Map<String, ?> fields, Throwable throwable)`.
- redact keys containing `authorization`, `cookie`, `apikey`, `api_key`, `token`, `bearer`, `jwt`, `secret`, `password`, `credential`.
- truncate string values to `256` chars.
- output `eventType=... key=value` format.

- [ ] **Step 5: Run GREEN**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ops-observability -Dtest=StructuredOpsLoggerTest test
```

Expected: test passes.

- [ ] **Step 6: Commit**

```bash
git add backend/ops-observability
git commit -m "feat: add structured ops logging helper"
```

---

### Task 6: SSE, Learning Plan, and Practice Metrics

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/SseLlmStreamSubscriber.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanDraftStreamSubscriber.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionController.java`
- Modify: `backend/mentor-api/src/main/resources/application.yml`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/service/SseLlmStreamSubscriberOpsTest.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanDraftStreamSubscriberOpsTest.java`

- [ ] **Step 1: Enable HTTP histograms**

Modify `backend/mentor-api/src/main/resources/application.yml`:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

Keep existing `management.endpoints.web.exposure.include: health,info,prometheus`.

- [ ] **Step 2: Add constructors to Agent SSE subscriber**

Extend `SseLlmStreamSubscriber` constructor to accept:

```java
SseStreamType streamType,
SseOpsRecorder sseOpsRecorder,
LearningOpsRecorder learningOpsRecorder,
StructuredOpsLogger opsLogger
```

Keep existing constructors by delegating to no-op recorders and `SseStreamType.AGENT_CONVERSATION` to avoid breaking tests.

- [ ] **Step 3: Record Agent SSE lifecycle**

In `onSubscribe`, call:

```java
sseOpsRecorder.opened(streamType);
opsLogger.info(log, OpsLogEventType.SSE_CONNECTION_OPENED, Map.of(
    OpsLogFields.SSE_STREAM_TYPE, streamType.tagValue()));
```

In successful terminal AgentRunEnd:

```java
sseOpsRecorder.completed(streamType);
if (streamType == SseStreamType.PRACTICE_MESSAGE) {
  learningOpsRecorder.practiceMessageStream(OpsStatus.COMPLETED);
}
```

In AgentError or upstream `onError`:

```java
sseOpsRecorder.failed(streamType, SseFailureType.UPSTREAM_ERROR);
if (streamType == SseStreamType.PRACTICE_MESSAGE) {
  learningOpsRecorder.practiceMessageStream(OpsStatus.FAILED);
}
```

In send failure:

```java
sseOpsRecorder.failed(streamType, SseFailureType.SEND_FAILURE);
sseOpsRecorder.clientDisconnected(streamType);
```

Ensure each connection decrements active gauge once only. Use `AtomicBoolean terminalRecorded`.

- [ ] **Step 4: Wire Agent SSE subscribers in controllers**

Inject `SseOpsRecorder`, `LearningOpsRecorder`, and `StructuredOpsLogger` into:

- `AiStreamController`: pass `SseStreamType.AI_EXPLANATION` through `AiExplanationService` if service constructs subscriber. If the service owns construction, update that service instead of the controller.
- `AgentConversationController`: pass `SseStreamType.AGENT_CONVERSATION`.
- `PracticeSessionController`: pass `SseStreamType.PRACTICE_MESSAGE`.

- [ ] **Step 5: Add learning plan subscriber instrumentation**

Extend `SseLearningPlanDraftStreamSubscriber` constructor to accept:

```java
SseOpsRecorder sseOpsRecorder,
LearningOpsRecorder learningOpsRecorder,
StructuredOpsLogger opsLogger
```

Record:

- on subscribe: `sse.opened(LEARNING_PLAN_DRAFT)`.
- `DraftReady`: `sse.completed(LEARNING_PLAN_DRAFT)` and `learning.learningPlanDraft(COMPLETED)`.
- `DraftError` or `onError`: `sse.failed(LEARNING_PLAN_DRAFT, UPSTREAM_ERROR)` and `learning.learningPlanDraft(FAILED)`.
- timeout callback in controller: call a new subscriber method `timeout()` that records `sse.timeout(LEARNING_PLAN_DRAFT)` and structured log `sse_connection_timeout`.

- [ ] **Step 6: Add timeout methods**

Add `timeout()` method to both subscribers:

```java
public void timeout() {
  if (terminalRecorded.compareAndSet(false, true)) {
    sseOpsRecorder.timeout(streamType);
    sseOpsRecorder.failed(streamType, SseFailureType.TIMEOUT);
    opsLogger.warn(log, OpsLogEventType.SSE_CONNECTION_TIMEOUT, Map.of(
        OpsLogFields.SSE_STREAM_TYPE, streamType.tagValue(),
        OpsLogFields.FAILURE_TYPE, SseFailureType.TIMEOUT.tagValue()), null);
  }
  cancel();
}
```

Use `emitter.onTimeout(subscriber::timeout)` in controllers instead of `subscriber::cancel`.

- [ ] **Step 7: Write/adjust subscriber tests**

Add tests that use fake recorders:

- `SseLlmStreamSubscriberOpsTest.recordsCompletedPracticeStream`
- `SseLlmStreamSubscriberOpsTest.recordsSendFailureAsFailedAndClientDisconnected`
- `SseLlmStreamSubscriberOpsTest.recordsTimeout`
- `SseLearningPlanDraftStreamSubscriberOpsTest.recordsDraftReadyAsCompleted`
- `SseLearningPlanDraftStreamSubscriberOpsTest.recordsDraftErrorAsFailed`
- `SseLearningPlanDraftStreamSubscriberOpsTest.recordsTimeout`

Tests should not assert sensitive request/user/model content appears in log fields.

- [ ] **Step 8: Run targeted tests**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=SseLlmStreamSubscriberOpsTest,SseLearningPlanDraftStreamSubscriberOpsTest test
```

Expected: tests pass.

- [ ] **Step 9: Verify metrics endpoint compiles**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test
```

Expected: `mentor-api` module and dependencies pass tests.

- [ ] **Step 10: Commit**

```bash
git add backend/mentor-api backend/ops-observability
git commit -m "feat: instrument sse business metrics"
```

---

### Task 7: Dashboard, Alerts, and Config Validation

**Files:**
- Modify: `deploy/docker/observability/grafana/dashboards/algo-mentor-overview.json`
- Modify: `deploy/docker/observability/prometheus-alert-rules.yml`
- Modify: `deploy/docker/observability/README.md`

- [ ] **Step 1: Confirm dashboard metric names after Micrometer conversion**

Start from Java metric names and use Prometheus converted names:

- `algo.mentor.sse.connections.active` -> `algo_mentor_sse_connections_active`
- `algo.mentor.sse.connections.opened` -> `algo_mentor_sse_connections_opened_total`
- `algo.mentor.sse.connections.completed` -> `algo_mentor_sse_connections_completed_total`
- `algo.mentor.sse.connections.failed` -> `algo_mentor_sse_connections_failed_total`
- `algo.mentor.sse.connections.timeout` -> `algo_mentor_sse_connections_timeout_total`
- `algo.mentor.agent.runs` -> `algo_mentor_agent_runs_total`
- `algo.mentor.learning_plan.draft.generations` -> `algo_mentor_learning_plan_draft_generations_total`
- `algo.mentor.practice.message.streams` -> `algo_mentor_practice_message_streams_total`
- `algo.mentor.practice.code_reviews` -> `algo_mentor_practice_code_reviews_total`

- [ ] **Step 2: Add alert examples to README**

Append:

```markdown
## 告警规则

`prometheus-alert-rules.yml` 只提供开发/测试示例规则。项目不在应用内发送告警；生产或外置监控接入时，应由外部 Prometheus/Alertmanager 或云厂商平台读取同一套指标。
```

- [ ] **Step 3: Validate JSON**

Run:

```bash
python3 -m json.tool deploy/docker/observability/grafana/dashboards/algo-mentor-overview.json >/tmp/algo-mentor-overview.json
```

Expected: exit code `0`.

- [ ] **Step 4: Validate Prometheus config with containerized promtool**

Run:

```bash
docker run --rm -v "$PWD/deploy/docker/observability:/etc/prometheus:ro" prom/prometheus:v2.55.1 promtool check config /etc/prometheus/prometheus.yml
```

Expected output includes:

```text
SUCCESS: /etc/prometheus/prometheus.yml is valid prometheus config file syntax
SUCCESS: 1 rule files found
```

- [ ] **Step 5: Commit**

```bash
git add deploy/docker/observability
git commit -m "chore: refine observability dashboards and alerts"
```

---

### Task 8: End-to-End Verification and Acceptance

**Files:**
- Modify only if previous tasks reveal missing docs or test gaps.

- [ ] **Step 1: Run backend test suite**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository test
```

Expected: all backend unit tests pass.

- [ ] **Step 2: Verify Makefile dry-run targets**

```bash
make -n observability-up
make -n observability-down
make -n observability-status
make -n observability-logs
```

Expected: all targets resolve and call `deploy/docker/observability/check-docker.sh`.

- [ ] **Step 3: Start application and observability stack**

Run:

```bash
make observability-up
```

Expected:

- Docker is checked before Compose starts.
- Prometheus, Grafana, Loki, Promtail containers start.
- URLs are printed for Prometheus, Grafana, and Loki.

- [ ] **Step 4: Verify endpoints**

Run:

```bash
curl -fsS http://localhost:8080/actuator/prometheus | head
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:3000/api/health
curl -fsS http://localhost:3100/ready
```

Expected:

- Prometheus endpoint returns metric text.
- Prometheus readiness returns success.
- Grafana health returns JSON containing `"database": "ok"`.
- Loki readiness returns success.

- [ ] **Step 5: Verify Prometheus scrape**

Run:

```bash
curl -fsS 'http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22mentor-api%22%7D'
```

Expected: JSON result contains `up{job="mentor-api"}` with value `1`.

- [ ] **Step 6: Verify custom metrics exist**

After exercising at least one SSE endpoint, run:

```bash
curl -fsS 'http://localhost:9090/api/v1/query?query=algo_mentor_sse_connections_opened_total'
curl -fsS 'http://localhost:9090/api/v1/query?query=algo_mentor_agent_runs_total'
```

Expected: Prometheus returns time series with low-cardinality tags only. Tags must not include `userId`, `sessionId`, `runId`, `requestId`, email, prompt text, or AI output.

- [ ] **Step 7: Verify Loki logs**

Run:

```bash
curl -G -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={job="algo-mentor-docker", compose_service="mentor-api"} |= "eventType="' \
  --data-urlencode 'limit=5'
```

Expected: query succeeds. When failure events have occurred, log lines include `eventType=...` and do not include API keys, Authorization headers, Cookie values, user raw input, or AI raw output.

- [ ] **Step 8: Verify Grafana provisioning**

Open:

```text
http://localhost:3000/d/algo-mentor-overview
```

Expected:

- Dashboard is loaded without manual import.
- Prometheus datasource points to `http://prometheus:9090`.
- Loki datasource points to `http://loki:3100`.

- [ ] **Step 9: Stop observability stack**

```bash
make observability-down
```

Expected: observability containers stop. PostgreSQL and `mentor-api` behavior remains consistent with existing local workflow.

- [ ] **Step 10: Final status check**

```bash
git status --short
```

Expected: clean worktree after final commit, or only intentional uncommitted documentation notes if the user requested no commits.

---

## Self-Review

### Spec Coverage

- `deploy/docker observability 配置`: Task 1 and Task 7.
- `Makefile observability-up/down/status/logs 入口和 Docker 探测`: Task 2.
- `backend/ops-observability 或等价边界模块`: Task 3 and Task 4.
- `SSE/Agent/学习计划业务指标埋点`: Task 4 and Task 6.
- `结构化低敏日志`: Task 5 and Task 6.
- `Grafana dashboard JSON`: Task 1 and Task 7.
- `Prometheus alert rules 示例`: Task 1 and Task 7.
- `测试与验收命令`: Task 1, Task 2, Task 3, Task 4, Task 5, Task 6, Task 7, Task 8.

### Placeholder Scan

本计划没有将功能留给未定义的“后续实现”。执行时如发现现有构造器或测试 builder 名称与计划示例不一致，按当前代码最小调整，但保持本计划定义的指标名、标签名、日志字段和验收命令不变。

### Type Consistency

- Java 指标名统一由 `OpsMetricNames` 定义。
- 指标低基数标签统一来自 `SseStreamType`、`SseFailureType`、`AgentOpsSource`、`OpsStatus`。
- 日志字段统一来自 `OpsLogFields`，日志事件统一来自 `OpsLogEventType`。
- Prometheus 查询使用 Micrometer 的 Prometheus 命名转换结果。
