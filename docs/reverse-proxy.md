# 本地反向代理

本地开发入口统一保留为 `http://localhost:8080`，由 nginx 监听。Java Spring Boot 进程改为监听 `18080`，避免业务进程直接占用公共入口端口。

## 端口

- `PROXY_PORT=8080`：nginx 对外入口。
- `API_PORT=18080`：Java Spring Boot 实际监听端口。
- `PROMETHEUS_PORT=9090`：Prometheus Web/UI 端口，通过 nginx 的 `/prometheus/` 前缀代理。

`SERVER_PORT` 保留给旧脚本兼容；新配置优先使用 `API_PORT`。

## 无 Docker 开发环境

安装 nginx 后使用 Makefile 启动代理：

```bash
make proxy-up
make proxy-status
make proxy-down
```

`make up` 在 Docker 不可用时会打包前后端、启动本地 nginx 代理，然后以 `API_PORT` 启动 Java jar。代理健康检查地址：

```bash
curl http://localhost:8080/nginx-health
```

Java API 和前端仍访问 `http://localhost:8080`。如果只运行 `make backend-dev`，需要另开一个终端运行 `make proxy-up`，或者直接访问 `http://localhost:18080`。

## 浏览器静态资源缓存

浏览器缓存策略由 Spring Boot 静态资源响应头统一提供，nginx 仅透传，不应覆盖：

- `/assets/**`：仅放置 Vite 构建的内容哈希文件，返回 `Cache-Control: public, max-age=31536000, immutable`。内容变化会生成新 URL。
- `index.html`、favicon 等固定 URL 静态资源：返回 `Cache-Control: no-cache`，浏览器每次使用时协商校验，未改变时复用本地响应。

发布前端时必须使用 `make package`，使新的 `index.html` 与其引用的 `/assets/**` 文件一同进入后端制品。不要把可变文件直接放入 `frontend/public/assets/`，否则它会绕过 Vite 内容哈希而错误获得长期缓存。

## Prometheus

nginx 预留 `/prometheus/` 到 `http://localhost:9090` 的代理：

```bash
curl http://localhost:8080/prometheus/-/ready
```

如果需要完整 Prometheus UI 在路径前缀下工作，建议 Prometheus 启动时设置：

```bash
prometheus --web.external-url=http://localhost:8080/prometheus --web.route-prefix=/
```

Spring Boot 自身的指标端点仍由 Java 暴露，并通过 nginx 访问：

```bash
curl http://localhost:8080/actuator/prometheus
```

## Docker 形态

当前 Docker compose 先把 Java 容器内端口改为 `18080`，宿主机仍通过 `PROXY_PORT` 暴露到 `8080`。把 nginx 和 Java 放入同一个 Docker 镜像或同一部署单元的工作后续再做。
