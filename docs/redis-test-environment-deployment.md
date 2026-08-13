# 测试环境 Redis 部署记录

部署日期：2026-08-13

## 部署范围

本次只部署 Redis 基础设施，不修改应用代码、Redis client、缓存 provider 或队列业务协议。

本机测试环境的完整连接台账（包含 PostgreSQL 和本机凭据）位于被 Git 忽略的 `.local/test-environment-deployment.md`，不在公开设计文档中重复明文密码。

| 实例 | 版本 | 端口 | 用途 | 内存上限 | 淘汰 | 持久化 |
| --- | --- | ---: | --- | ---: | --- | --- |
| `redis-cache` | 7.4.10 | 6379 | Shared TTL 缓存 | 384 MiB | `allkeys-lfu` | 关闭 RDB/AOF |
| `redis-stream` | 7.4.10 | 6380 | Redis Streams | 768 MiB | `noeviction` | RDB + AOF `everysec` |

## 安装位置

- 二进制：`/opt/redis/7.4.10/bin/`
- 缓存配置：`/etc/redis/cache.conf`
- Streams 配置：`/etc/redis/stream.conf`
- 缓存数据：`/var/lib/redis-cache`
- Streams 数据：`/var/lib/redis-stream`
- 缓存日志：`/var/log/redis-cache/redis.log`
- Streams 日志：`/var/log/redis-stream/redis.log`
- 可迁移 systemd unit：`/etc/systemd/system/redis-cache.service`、`/etc/systemd/system/redis-stream.service`

当前执行环境的 PID 1 不是 systemd，因此本次实例由对应 Redis 系统用户直接启动并以 daemon 进程运行；具备 systemd 的测试机可直接使用同名 unit 接管。

## 验证结果

- 两个实例均返回 `PONG`。
- 版本均为 `7.4.10`。
- 缓存实例确认 `maxmemory=384mb`、`maxmemory-policy=allkeys-lfu`、`appendonly=no`。
- Streams 实例确认 `maxmemory=768mb`、`maxmemory-policy=noeviction`、`appendonly=yes`、`appendfsync=everysec`。
- Streams 已完成 `XADD -> XGROUP CREATE -> XREADGROUP -> XACK`，ACK 后 Pending 数为 `0`。

源码包 SHA-256：

```text
669ab6689b5e7d0c479e8d526ccbadae36b69a11370742ffe23822b9df8d85ba
```

`vm.overcommit_memory=1` 已写入 `/etc/sysctl.d/99-redis.conf`。当前执行环境的 `/proc` 为只读挂载，立即应用失败，当前值仍为 `0`；不影响本次实例启动和功能验证。
