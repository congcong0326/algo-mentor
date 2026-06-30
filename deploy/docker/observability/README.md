# 本地观测栈

本目录只服务开发/测试环境。默认本地开发不会启动观测栈，只有显式启用 Docker Compose 的 `observability` profile 时才会启动 Prometheus、Grafana、Loki、Promtail。

访问地址：

- mentor-api: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000
- Loki: http://localhost:3100

默认 Grafana 账号密码为 `admin` / `admin`，可通过 `GRAFANA_ADMIN_USER` 和 `GRAFANA_ADMIN_PASSWORD` 覆盖。

Prometheus 通过 Docker 网络抓取 `mentor-api:8080/actuator/prometheus`。Grafana 通过 Docker 网络访问 `http://prometheus:9090` 和 `http://loki:3100`。

配置校验：

```bash
docker compose -f deploy/docker/docker-compose.yml --profile observability config >/tmp/algo-mentor-observability-compose.yml
python3 -m json.tool deploy/docker/observability/grafana/dashboards/algo-mentor-overview.json >/tmp/algo-mentor-overview.json
```

手动启动：

```bash
docker compose -f deploy/docker/docker-compose.yml --profile observability up -d prometheus grafana loki promtail
```

## 告警规则

`prometheus-alert-rules.yml` 只提供开发/测试示例规则。项目不在应用内发送告警；生产或外置监控接入时，应由外部 Prometheus/Alertmanager 或云厂商平台读取同一套指标。
