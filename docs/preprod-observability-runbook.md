# 预发布外置观测手册

## 目标与边界

本手册记录 `algo-mentor` 预发布环境由外置 Prometheus 拉取指标、由 Grafana 展示指标的已验证操作流程。Prometheus 采用 pull 模式，应用只暴露 Spring Boot Actuator 的 `/actuator/prometheus`；应用不主动向监控服务推送数据。

本手册覆盖预发布观测配置、验证、回滚和新增业务指标后的面板接入，不包含防火墙、网络策略、Prometheus/Grafana 安装、告警通知路由或生产环境发布。

## 已验证拓扑

| 角色 | 地址与端口 | 说明 |
| --- | --- | --- |
| 预发布应用 | `192.168.10.118:18080` | `algo-mentor` Docker 容器，暴露 `/actuator/prometheus`。 |
| Prometheus/Grafana 主机 | `192.168.10.85` | Prometheus `9090`，Grafana `3001`。 |
| Prometheus 容器内数据源 | `http://prometheus:9090` | Grafana 使用 Docker 网络访问该地址。 |

预发布应用的 `mentor-api` 已依赖 Actuator 和 `micrometer-registry-prometheus`，并在 `application.yml` 中公开 `health,info,prometheus`。现有 Micrometer、JVM、HTTP、数据库连接池和业务 Recorder 指标会统一出现在该端点。

## 监控主机目录与约定

以下路径位于 `192.168.10.85`，不是本仓库内容：

```text
/root/docker-nas/prometheus/
  docker-compose.yml
  config/prometheus.yml
  file_sd/java/targets.yml
  grafana/provisioning/datasources/prometheus.yml
  grafana/provisioning/dashboards/algomentor-preprod.yml
  grafana/provisioning/dashboards/json/algomentor-preprod.json
  backups/
```

从当前部署工作机维护观测主机时，统一使用直接 root 入口：

```bash
ssh prometheus-root
```

该别名使用独立私钥 `/root/.ssh/id_ed25519_prometheus_ops`，并已固定目标 IP、`IdentitiesOnly yes` 和严格主机键校验；不要改用 IP 地址加密码登录，也不要复用预发布应用部署密钥。

`config/prometheus.yml` 已定义 `job_name: java`，通过 `/etc/prometheus/file_sd/java/*.yml` 读取 Java 服务发现文件。因此新增或修改本项目目标时，应修改 `file_sd/java/targets.yml`，不要另建静态 scrape job，也不要调整全局抓取周期。

当前目标定义：

```yaml
- targets:
    - 192.168.10.118:18080
  labels:
    __metrics_path__: /actuator/prometheus
    environment: preprod
    service: algo-mentor-api
```

约定查询标签如下：

| 标签 | 固定值或来源 | 用途 |
| --- | --- | --- |
| `job` | `java` | Prometheus 已有的 Java file-SD job。 |
| `environment` | `preprod` | 环境筛选。 |
| `service` | `algo-mentor-api` | 服务筛选。 |
| `instance` | `192.168.10.118:18080` | 由 Prometheus 自动生成。 |
| `application` | `algo-mentor-api` | 由 Micrometer 公共标签写入。 |

不要将用户 ID、会话 ID、run ID、题目内容、Prompt、响应正文、异常消息或其他高基数字段加入 Prometheus 标签。

### Redis Exporter

预发布 Redis 运行在 PaaS 主机 `192.168.10.121`，缓存与 Streams 为独立 Redis 7.4.10
实例。PaaS 主机通过两个 systemd 服务运行固定版本的 `redis_exporter`，而不是由应用容器或
Prometheus 主机代理采集：

| Redis 用途 | Redis 端口 | Exporter 服务 | Exporter 端口 | 关键配置 |
| --- | ---: | --- | ---: | --- |
| Shared TTL 缓存 | `6379` | `redis-exporter-cache.service` | `9121` | `384 MiB`、`allkeys-lfu`、无 RDB/AOF |
| Practice realtime Streams | `6380` | `redis-exporter-stream.service` | `9122` | `768 MiB`、`noeviction`、RDB+AOF |

两个 Redis ACL 文件各自定义同名 `prometheus_exporter` 用户，但使用独立随机密码。该用户只拥有
`PING` 与 `INFO`；exporter 固定启用 `--config-command=-`、`--set-client-name=false` 与
`--disable-scrape-endpoint`，因此不需要 `CONFIG`、`CLIENT`、`SCAN`、`KEYS` 或读写业务 key 的权限。
密钥仅保存在 PaaS 主机 `/etc/redis-exporter/*.env`，权限为 `root:redis-exporter 0640`，不得复制到
仓库、应用环境文件或 Prometheus 配置。

`config/prometheus.yml` 另有 `job_name: redis`，通过
`/etc/prometheus/file_sd/redis/*.yml` 加载以下两个 target；不得将它们加入 Java job：

```yaml
- targets:
    - 192.168.10.121:9121
  labels:
    environment: preprod
    service: redis-cache
    redis_purpose: cache
- targets:
    - 192.168.10.121:9122
  labels:
    environment: preprod
    service: redis-stream
    redis_purpose: stream
```

所有 Redis 查询必须至少包含 `job="redis"`、`environment="preprod"` 与
`redis_purpose`。标准采集保留可用性、内存、连接、命中/淘汰、RDB/AOF 与复制状态；禁止启用
`--check-keys`、`--check-streams` 或 `--export-client-list`，以避免 key、会话 Stream 或客户端信息造成
高基数与隐私暴露。Streams 使用会话级 Stream，积压和消费者语义应由应用侧聚合指标表达，不按 Stream
名称导出 Prometheus 标签。

## 新增或修改抓取目标

在监控主机使用受控运维访问进入 `/root/docker-nas/prometheus`。真实凭据不进入仓库、shell 历史、配置文件或本文档。

1. 只读确认当前运行状态和端点可达性：

   ```bash
   cd /root/docker-nas/prometheus
   docker compose ps
   docker exec prometheus sh -c \
     'wget -qO- --timeout=5 http://192.168.10.118:18080/actuator/prometheus | head'
   ```

2. 备份现有 Java target 文件：

   ```bash
   mkdir -p backups
   cp file_sd/java/targets.yml \
     "backups/java-targets.yml.$(date -u +%Y%m%dT%H%M%SZ)"
   ```

3. 按上述 YAML 更新 `file_sd/java/targets.yml`。需要增加新的 Java 服务时，追加新的 target group，并为每个服务固定 `environment` 与 `service` 标签；同一 target group 中不要混入不同指标路径的服务。

4. 校验 Prometheus 主配置和服务发现结果：

   ```bash
   docker exec prometheus promtool check config /etc/prometheus/prometheus.yml
   docker exec prometheus promtool check service-discovery \
     /etc/prometheus/prometheus.yml java
   ```

   预期服务发现结果包含目标地址、`__metrics_path__=/actuator/prometheus`、`environment=preprod` 与 `service=algo-mentor-api`。

5. 仅在校验通过后 reload Prometheus。本环境已启用 `--web.enable-lifecycle`：

   ```bash
   curl -fsS -X POST http://127.0.0.1:9090/-/reload
   curl -fsS http://127.0.0.1:9090/-/ready
   ```

6. 等待一个 15 秒抓取周期后验收：

   ```bash
   curl -fsS -G http://127.0.0.1:9090/api/v1/query \
     --data-urlencode \
     'query=up{job="java",environment="preprod",service="algo-mentor-api"}'
   ```

   返回值必须为 `1`。也可以在 Prometheus Targets 页面确认目标 URL 为 `http://192.168.10.118:18080/actuator/prometheus` 且状态为 `UP`。

若从 Prometheus 容器无法连接应用端点，停止后续配置修改并记录错误。该问题属于网络可达性，不在本手册范围内改变网络或防火墙。

## Grafana Dashboard

Grafana 已通过 provisioning 预置默认数据源：

```yaml
name: Prometheus
type: prometheus
access: proxy
url: http://prometheus:9090
isDefault: true
```

预发布 dashboard 地址：

```text
http://192.168.10.85:3001/d/algo-mentor-preprod
```

`Algo Mentor - Preprod` 默认展示最近 6 小时、每 30 秒刷新。当前 dashboard 已按运行链路覆盖以下指标族：

- 应用存活、进程启动时间、HTTP 请求速率、5xx 比率和 P95 延迟；
- JVM/进程资源、线程、GC 和 Hikari 连接池压力；
- Agent executor 活跃数、队列、拒绝数、执行组余量，以及 Agent run、工具执行和权限决策；
- SSE 活跃/新建/失败/超时连接；
- AI provider 调用、活跃请求、模型路由、AI run 请求/错误/Token 和 active gauge；
- Caffeine/Redis cache 命中率、加载延迟、Redis 失败和 cache coherence；
- Learner Memory 队列、worker、更新/投影/工具调用结果；
- Practice realtime Redis Stream、学习计划、认证会话和反馈状态变更。

认证会话面板覆盖以下已由应用暴露的低基数指标：策略解析/淘汰/绝对过期/失败、管理员查询延迟与吊销结果、身份状态联动吊销、改密联动吊销和 beta 准入联动吊销。查询只使用 `source`、`outcome`、`operation`、`status` 等固定标签，不使用用户 ID 或 Session ID。

最近窗口内没有 5xx 时，5xx 面板无时间序列是正常现象，不代表 scrape 失败。判断采集状态应使用 `up` 面板。

Redis 使用 provisioned dashboard `Algo Mentor - Redis Preprod`，UID 为
`algo-mentor-redis-preprod`。它基于 Grafana 社区 Dashboard `763`（Redis Dashboard for Prometheus
Redis Exporter 1.x），已将原 Kubernetes `namespace` 变量替换为 `redis_purpose`，并固定查询范围为
预发布 `redis` job；JSON 源文件为
`deploy/docker/observability/grafana/dashboards/redis-preprod.json`。

### 新增或调整面板

Dashboard 必须通过 provisioning 文件维护，不只在 Grafana UI 中保存。操作顺序：

1. 先在 Prometheus expression browser 或 API 验证 PromQL，确认返回结果与标签集合符合预期。
2. 备份当前 provisioning：

   ```bash
   tar -C grafana/provisioning -czf \
     "backups/grafana-provisioning.$(date -u +%Y%m%dT%H%M%SZ).tar.gz" .
   ```

3. 修改 `grafana/provisioning/dashboards/json/algomentor-preprod.json`，所有本项目查询必须包含：

   ```promql
   job="java", environment="preprod", service="algo-mentor-api"
   ```

4. 验证 JSON 格式后重启 Grafana 以立即加载 provider：

   ```bash
   docker compose restart grafana
   curl -fsS http://127.0.0.1:3001/api/health
   docker logs --since 2m grafana
   ```

   健康检查应返回 `database: ok`，日志中不应包含 dashboard provisioning error。

5. 在浏览器打开 dashboard，确认新增面板的查询、单位、阈值和图例均正确。

## 预发布告警

告警规则文件为 `deploy/docker/observability/preprod-alert-rules.yml`，部署到监控主机的
`rules/algo-mentor-preprod.yml`。当前规则覆盖：

- API scrape down；
- HTTP 5xx 比率和聚合 P95 延迟；
- SSE 失败/超时；
- Agent executor 队列持续堆积；
- AI provider 失败率；
- Hikari pending 连接；
- cache coherence 长时间未成功轮询；
- Learner Memory 最老消息积压；
- Practice realtime Redis 非成功操作。
- Redis exporter / Redis 不可用、缓存内存与淘汰、Streams 内存、AOF 写入和 RDB 保存失败。
- 认证会话策略、身份状态/管理员/beta 准入联动吊销失败，以及会话监控查询平均延迟过高。

规则只负责 Prometheus 侧判断，通知路由仍由监控主机现有 Alertmanager/Grafana 配置管理。
加载前备份并执行：

```bash
cp rules/algo-mentor-preprod.yml \
  "backups/algo-mentor-preprod.yml.$(date -u +%Y%m%dT%H%M%SZ)"
scp deploy/docker/observability/preprod-alert-rules.yml \
  prometheus-root:/root/docker-nas/prometheus/rules/algo-mentor-preprod.yml
ssh prometheus-root 'cd /root/docker-nas/prometheus && docker exec prometheus promtool check rules /etc/prometheus/rules/algo-mentor-preprod.yml'
```

校验通过后 reload Prometheus，并检查 `/api/v1/rules` 中的 `algo-mentor-preprod` 组状态为
`loaded`。

## 新增业务指标后的接入流程

新增指标先遵循代码约定，再接入观测面板：

1. 在所属模块的常量类或枚举中定义稳定的指标名、事件名和允许标签；`ops-observability` 模块优先复用 `OpsMetricNames`、`OpsMetricTags` 与对应 Recorder。
2. 仅使用低基数、无隐私的标签；新增 tag 前评估其最大取值数量与是否会随用户输入增长。
3. 使用 Micrometer `Counter`、`Timer`、`DistributionSummary` 或 `Gauge` 中与语义匹配的类型，避免用 Gauge 表示累计事件。
4. 在模块测试中验证指标名称、tag 和计数/耗时语义；运行最小相关 Maven 测试。
5. 发布到预发布后，从 `192.168.10.118:18080/actuator/prometheus` 确认原始指标存在，再在 Prometheus 中添加本手册约定的三项筛选标签查询。
6. 仅当 PromQL 已验证时才向 provisioned dashboard 添加面板；同时设置恰当的单位、阈值和时间窗口。
7. 记录指标用途、标签契约、面板名称和验证查询；若指标影响故障判断，再单独评审告警规则与通知路由。

## 回滚

### Prometheus target 回滚

1. 恢复对应 `backups/java-targets.yml.<UTC 时间戳>`。
2. 运行 `promtool check config` 与 `promtool check service-discovery`。
3. 调用 `POST /-/reload`。
4. 确认其他 Targets 仍为 `UP`。

### Grafana dashboard 回滚

1. 解压对应 `backups/grafana-provisioning.<UTC 时间戳>.tar.gz` 到 `grafana/provisioning/`。
2. 重启 Grafana。
3. 调用 `/api/health`，并检查 provisioning 日志。

上述回滚只影响监控展示与采集配置，不重启或修改预发布应用。

## 2026-08-19 接入记录

- Prometheus `v3.14.0` 已开始抓取预发布 Actuator 指标；目标 `UP`，抓取耗时约 25ms。
- Grafana `13.2.0` 已 provision `Algo Mentor - Preprod` dashboard；本次补齐应用已暴露的业务指标族和对应 PromQL 面板。
- 新增 `preprod-alert-rules.yml`，覆盖 API、HTTP、SSE、Agent、AI provider、连接池、缓存一致性、Learner Memory 和 realtime Redis。
- `AiRunMetricsObserver` 的 `ai.run.active` 在应用启动阶段可能早于 Prometheus MeterRegistry 初始化，导致 `ai_run_active` 未注册；`AiGovernanceAutoConfiguration` 已声明在 Prometheus metrics 自动配置之后运行，并增加回归测试。
- 发布后必须验证 `ai_run_active{job="java",environment="preprod",service="algo-mentor-api"}` 存在；没有运行中的 AI 请求时值为 `0` 仍属于正常结果。
- 2026-08-19 11:20 UTC 已将 dashboard 和告警规则同步到观测主机；Prometheus readiness 正常、target `up=1`、告警组 10 条规则 health 为 `ok`，Grafana API 返回 `Algo Mentor - Preprod`。
- 验收时 `AlgoMentorPreprodLearnerQueueStale` 为 `pending/warning`：`learner_profile_queue_oldest_pending_age` 约 5,250 秒、pending 2 条；这是当前预发布队列积压，需业务侧处理，不属于本次指标接入失败。

## 2026-08-21 会话监控补齐

- 核实认证模块已暴露 10 个会话相关指标族，包含策略控制、管理员会话监控、身份状态吊销、密码修改吊销和 beta 准入吊销。
- `Algo Mentor - Preprod` dashboard 从 19 个面板扩展为 22 个面板、63 个 PromQL 查询，新增会话策略、会话吊销和会话监控查询延迟图表，并将反馈状态变更保留为独立面板。
- `preprod-alert-rules.yml` 从 18 条扩展为 20 条，新增认证会话控制失败和管理员查询平均延迟超过 1 秒的告警；当前计时器实际导出 `_sum/_count`，未假设尚未发布的 histogram bucket。
- 本地 Compose Prometheus 同时挂载预发布规则文件，便于配置语法和规则回归校验；预发布规则仍由外置 Prometheus 按本手册同步。

## 2026-08-20 Redis 接入记录

- PaaS 主机已安装经上游 `sha256sums.txt` 校验的 `redis_exporter v1.89.0`，并启用
  `redis-exporter-cache.service` 与 `redis-exporter-stream.service`。
- 缓存与 Streams Redis 均使用专属 `prometheus_exporter` ACL，业务账号保持原有权限；两个 exporter
  本地与 Prometheus 抓取均返回 `redis_up=1`。
- Prometheus 新增 `redis` file-SD job，两个 target 的 `up=1`；
  `preprod-alert-rules.yml` 已扩展为 18 条规则并通过 `promtool` 校验。
- Grafana 已 provision `Algo Mentor - Redis Preprod`；模板不依赖 Kubernetes 标签，支持按缓存/Streams
  用途与 exporter instance 筛选。
