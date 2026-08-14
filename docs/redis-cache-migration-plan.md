# Redis 缓存新增设计与迁移计划

更新时间：2026-08-13

状态：设计修订；六个业务 region 迁移到纯 Redis，保留通用策略 Shared cache

## 一、目标与范围

本计划的目标是在现有缓存模块中增加一种纯 Redis 的远程缓存能力，供需要跨节点共享 value、降低重复回源或承担二级缓存职责的业务使用。它不是对现有 `SharedTtlCacheRegion` 的替换，也不改变当前生产 Shared provider。

本机 Redis 按既定基础设施边界使用：

| 实例 | 端口 | 用途 | 关键约束 |
|---|---:|---|---|
| `redis-cache` | `6379` | 纯 Redis TTL 缓存（远程 value/L2） | `384 MiB`、`allkeys-lfu`、允许丢失 |
| `redis-stream` | `6380` | Redis Streams | `768 MiB`、`noeviction`、可靠消息，不作为缓存 |

本次不改 Redis Streams、持久化队列、数据库事实数据、登录 Session 或其他需要独立状态语义的组件。

## 二、缓存类型边界

### 2.1 现有类型继续保留

当前 `backend/cache` 已有三类缓存契约：

| 类型 | 当前 value 介质 | 跨节点语义 | 典型用途 |
|---|---|---|---|
| `LocalBoundedCacheRegion` | 本地 Caffeine | 无 | 发布期不可变目录、题目公共快照 |
| `LocalTtlCacheRegion` | 本地 Caffeine | 无 | 允许节点短时间差异的本地快照 |
| `SharedTtlCacheRegion` | 本地 Caffeine | PostgreSQL 事务失效事件 | 管理员修改后需要其他节点及时失效的设置、认证快照、用户策略 |

`SharedTtlCacheRegion` 的生产实现仍是 `postgres-coherent-caffeine`：每个 JVM 保存 value，PostgreSQL 只保存失效事件；当前节点在事务提交后失效，其他节点由 poller 精确失效。`shared-provider=caffeine` 和 `shared-provider=postgres-coherent-caffeine` 的现有选择、配置和回滚语义保持不变。

### 2.2 新增纯 Redis 类型

新增独立的 `RedisTtlCacheRegion`（名称可在实现阶段按现有命名规范落定）及 `RedisCacheRegionFactory`：

- value 只存 `redis-cache:6379`，不在 region 内隐含本地 Caffeine L1；
- 通过 `GET`、`SET PX`、`DEL` 提供 cache-aside 读写；
- Redis 是可丢失副本，miss、超时、连接失败、淘汰和坏值都回源权威数据库；
- 默认不接入 PostgreSQL Shared invalidation event/poller，不改变 Shared cache 的事务失效链路；
- 业务在数据库提交后可显式执行 Redis `DEL`，Redis 删除失败时由 TTL 兜底；
- 同一 Redis key 对所有应用节点可见，适合高读、跨节点复用、节点重启后仍希望保留热点的可重建快照。

这个类型就是远程二级缓存能力。未来若某个业务需要“本地 L1 + Redis L2”，应显式组合一个多级缓存门面；不能通过改变 `SharedTtlCacheRegion` 的 provider 隐式改变所有现有 Shared region 的性能和一致性语义。

### 2.3 如何选择

- 修改后必须沿现有数据库事务失效事件传播，选择 `SharedTtlCacheRegion`；
- 主要目标是减少多节点重复回源、共享远程 value，且可接受 TTL 或显式 `DEL` 的最终一致，选择 `RedisTtlCacheRegion`；
- 允许节点之间独立维护并且远程化收益低，继续选择 Local cache。

两种类型都不把 Redis 或 Caffeine 当作事实来源。Session、锁、权限等待、一次性 token、续期状态和安全 scope 不属于普通缓存。

## 三、迁移范围

本次后续改造明确包含 6 个 Redis-only region。它们从现有 `SharedTtlCacheRegion` 门面迁移到独立的 `RedisTtlCacheRegion` 门面，但不改变 `generic-policy-set` 的 Shared 实现，也不修改全局 `shared-provider`。

### 3.1 保留现有 Shared 的 region

以下 region 继续使用现有 `SharedTtlCacheRegion` 和 `postgres-coherent-caffeine`：

| region | 保留原因 |
|---|---|
| `generic-policy-set` | 当前已经是 Shared region；不能同时被列为 Local 或被全局切换 |

以下 region 继续使用本地 Caffeine：`problem-static-snapshot`、`problem-filters`、`learning-plan-template-catalog`。它们是发布期或低基数快照，远程化收益不足。

### 3.2 切换到 Redis-only 的 6 个 region

| region | Redis value | key | TTL | 切换原因 |
|---|---|---|---:|---|
| `ai-runtime-settings` | JSON snapshot；设置记录缺失使用显式 envelope | `singleton` | `120s` | 全局 AI 设置读取频繁，管理员更新后删除并按需回填 |
| `auth-beta-access-settings` | JSON snapshot；设置记录缺失使用显式 envelope | `singleton` | `30s` | 白名单总开关值小，删除后按需回填，避免各节点独立回源 |
| `auth-access-snapshot` | JSON snapshot；用户不存在使用显式 envelope | `userId` | `60s` | 认证高频读取，跨节点复用用户状态快照，减少用户表和角色表回源 |
| `ai-user-policy` | JSON snapshot；无覆盖记录使用 `inherited` 对象 | `userId` | `120s` | 用户策略读取频繁，策略值小且整体替换，适合远程共享 |
| `identity-user-relations` | JSON snapshot，包含成员关系时间边界 | `userId` | `1800s` | 策略解析前高频读取，避免各 JVM 重复加载用户组关系 |
| `auth-beta-email-membership` | 标量 `1`/`0`；key 使用邮箱 SHA-256 | `sha256(normalizedEmail)` | `60s` | 邮箱成员关系值小、命中率高，避免跨节点重复查询白名单表 |

这 6 个 region 迁移后使用纯 Redis value，不再由 PostgreSQL Shared invalidation event/poller 管理缓存副本。业务数据库事务提交后执行精确 Redis `DEL`；Redis 故障或删除失败时由 TTL 和 PostgreSQL 回源保证正确性。邮箱原文不得写入 Redis key、失效日志或指标 tag。

因此，本次不是把所有 Shared cache 全局切换到 Redis，而是将上述 6 个 region 按业务逐个切换到新增的 Redis-only 类型；`generic-policy-set` 及其余现有 Shared coherence 保持不变。

## 四、Redis-only provider 设计

### 4.1 API 与模块边界

在 `backend/cache` 增加 Redis 实现，但保留业务模块只依赖缓存接口。首期固定新增以下公共契约：

```java
public interface RedisTtlCacheRegion<K, V> extends CacheRegion<K, V> {
}

public interface RedisCacheRegionFactory {
  <K, V> RedisTtlCacheRegion<K, V> createTtl(
      RedisTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      RedisValueCodec<V> valueCodec);
}

public record RedisTtlCacheSpec(
    CacheRegionName name,
    String namespace,
    int schemaVersion,
    Duration ttl) {
}

public interface RedisValueCodec<V> {
  byte[] encode(V value);

  V decode(byte[] bytes);
}

public interface RedisValueCodecFactory {
  <V> RedisValueCodec<V> json(Class<V> valueType);

  RedisValueCodec<Boolean> booleanAsZeroOrOne();
}
```

`RedisTtlCacheSpec` 不包含 `maximumSize`：Redis 容量受实例的 `384 MiB` 和 `allkeys-lfu` 统一控制。它在构造时必须校验 `name`、kebab-case `namespace`、`schemaVersion >= 1`、正数 TTL，以及 `ttl.toMillis() >= 1`，保证能够安全映射为 Redis `PX`。

实现类和包结构固定如下，避免业务模块依赖 Lettuce：

```text
cache/api/RedisTtlCacheRegion.java
cache/factory/RedisCacheRegionFactory.java
cache/spec/RedisTtlCacheSpec.java
cache/codec/RedisValueCodec.java
cache/codec/RedisValueCodecFactory.java
cache/redis/LettuceRedisCacheRegionFactory.java
cache/redis/LettuceRedisTtlCacheRegion.java
cache/redis/RedisCacheKeyBuilder.java
cache/redis/RedisConnectionManager.java
cache/redis/BypassRedisCacheRegionFactory.java
cache/redis/codec/JacksonRedisValueCodecFactory.java
```

必须保持以下边界：

- Redis factory 与 `SharedCacheRegionFactory` 是两个独立依赖入口；
- Redis region 不实现 `SharedCacheRegionDescriptor`，也不自动注册 PostgreSQL 失效目标；
- value codec 由业务 region 显式提供，不能让通用 provider 猜测 `Optional`、枚举或 Java 类型；
- Lettuce、Redis command API、连接管理和序列化实现只允许位于 `backend/cache`。

`createTtl` 对相同 `CacheRegionName` 必须幂等：specification、key codec 和 value codec 不一致时启动失败，防止两个业务门面误用同一 Redis keyspace。factory 将 `RedisTtlCacheSpec` 注册为 `CacheRegionType.REDIS_TTL` 的定义，以检查 `namespace + schemaVersion` 的冲突；但不得调用 `CacheRegionRegistry.registerSharedRegion`，因此数据库恢复的 `invalidateAllRegions()` 不会产生 Redis 全量清理。

当 `algo-mentor.cache.enabled=false` 或 `algo-mentor.cache.redis.enabled=false` 时，`CacheAutoConfiguration` 仍提供 `RedisCacheRegionFactory`，但其实现为 `BypassRedisCacheRegionFactory`：`get` 直接执行 loader，`getIfPresent` 恒 miss，`put`/`invalidate` 无操作。这样业务 bean 不需要按 Redis 可用性分支，且 Shared factory 的既有装配不受影响。

### 4.2 Key、value 和 TTL

复用现有稳定 key 契约，统一格式为：

```text
algo-mentor:cache:v{schemaVersion}:{namespace}:{keyToken}
```

`namespace`、`schemaVersion` 和 `keyToken` 继续由业务 region/specification 提供。原始邮箱、Authorization、Session ID、API key 和用户隐私不得进入 key、日志或指标 tag。

首期只支持单 key 整体读写：

| 逻辑值 | Redis value |
|---|---|
| `JSON_SNAPSHOT` | Jackson UTF-8 紧凑 JSON String |
| `SCALAR` | `1`/`0` 或短字符串 |

禁止 Java 原生序列化、默认多态类型信息以及 Redis Hash/Set/List 等字段级结构。TTL 由每个 Redis region 的 `RedisTtlCacheSpec` 持有，写入使用毫秒精度 `PX`。

`RedisCacheKeyBuilder` 是唯一允许拼接物理 key 的类：

```java
String physicalKey(RedisTtlCacheSpec spec, String keyToken) {
  return "algo-mentor:cache:v" + spec.schemaVersion()
      + ":" + spec.namespace() + ":" + keyToken;
}
```

它必须先调用 `SharedCacheKeyCodec.requireValidToken`。region 的 `getIfPresent`、`get`、`put` 和 `invalidate` 都只能通过该 builder 得到 key；日志、异常、指标 tag 只记录 region 名和操作类型，不输出该 physical key 或 key token。

`JacksonRedisValueCodecFactory` 接收 Spring 已配置的 `ObjectMapper`；若应用未提供则创建专用 mapper，注册 `JavaTimeModule`，关闭时间戳写入，并禁用默认多态类型。它使用 UTF-8 byte[] 读写 JSON，反序列化时：

- `readValue` 失败向 region 抛出受控 codec 异常；region best-effort `DEL` 坏值后回源；
- JSON 只反序列化到调用方传入的明确 `Class<V>`，不从 payload 中读取类型信息；
- `Optional<T>` 不作为 codec 的 value type。以下三个 region 必须在各自业务模块定义并使用独立的 JSON envelope record；缓存门面负责在 envelope 与业务 `Optional` 之间转换：

  | region | Redis value type | `present=false` 的含义 | 约束 |
  |---|---|---|---|
  | `ai-runtime-settings` | `AiRuntimeSettingsCacheEntry(boolean present, AiRuntimeSettings value)` | 设置记录不存在 | `present=true` 时 `value` 非空；`present=false` 时 `value` 必须为 `null` |
  | `auth-beta-access-settings` | `BetaAccessSettingsCacheEntry(boolean present, Boolean value)` | 白名单设置记录不存在 | `present=true` 时 `value` 非空；`present=false` 时 `value` 必须为 `null` |
  | `auth-access-snapshot` | `AuthAccessSnapshotCacheEntry(boolean present, AuthAccessSnapshot value)` | 用户不存在 | `present=true` 时 `value` 非空；`present=false` 时 `value` 必须为 `null` |

  这些 record 在紧凑 JSON 中使用稳定的 `present`、`value` 字段；构造器必须校验上述组合，避免把坏值误解为负缓存。它们仅是 Redis value 契约，不向 controller 或跨模块 API 暴露；
- `booleanAsZeroOrOne()` 严格只接受单字节 ASCII `1` 或 `0`，其他值视为坏缓存值。

每次 `SET PX` 前，region 比较 `valueCodec.encode(value).length` 与 `CacheProperties.Redis.maxValueBytes`；超限不写 Redis、记录 oversize 指标，仍向调用者返回 loader 或 `put` 的正常值。JSON 与标量 codec 都不得返回 null 或空 byte[]。

### 4.3 Lettuce 连接与命令执行

`RedisConnectionManager` 是唯一持有 Lettuce 对象的 bean，负责创建、获取和关闭：

```text
RedisClient
  -> StatefulRedisConnection<byte[], byte[]>
  -> RedisCommands<byte[], byte[]>（同步单命令）
```

- `RedisClient` 使用 `RedisURI` 构造，配置 host、port、database、username/password、TLS 和 connect timeout；端口默认只能是 `6379`，不指向 Streams 实例；
- `ClientOptions` 开启 auto reconnect、设为 `DisconnectedBehavior.REJECT_COMMANDS`，不在断线期间堆积请求；
- 连接创建采用惰性初始化：自动配置阶段只创建 manager，不执行 `connect()` 或 Redis ping。第一次命令获取连接，连接失败立即按 Redis 故障处理，因此 Redis 不可用不阻塞应用启动；
- manager 使用一个共享、非阻塞 Lettuce connection；region 只调用同步 `GET`、`SET PX`、`DEL` 三种单 key 命令，不使用 pipeline、事务、Lua、阻塞命令或连接池；
- 每个命令受 `commandTimeout` 限制。命令、编码或连接异常在 region 内分类为 timeout、connection、codec 或 command failure，记录指标后执行既定旁路语义；
- Spring bean 的 destroy method 调用 manager `close()`，先关闭 connection，再关闭 client；关闭最多等待 `shutdownTimeout`，不输出 URI、用户名或密码。

`LettuceRedisTtlCacheRegion` 的操作语义固定为：

| API | Redis 正常路径 | Redis/codec 故障 |
|---|---|---|
| `getIfPresent` | `GET` → codec decode → hit/miss | `Optional.empty()` |
| `get` | `GET` 命中则返回；miss 时调用 loader，成功后 `SET PX` | 直接调用 loader；loader 成功后的 `SET` 失败不影响返回 |
| `put` | codec encode → `SET key value PX ttl` | 不抛 Redis 故障；记录后返回 |
| `invalidate` | `DEL key` | 不抛 Redis 故障；记录后返回 |

loader 自身抛出的异常按现有 `CacheRegion` 契约原样向上传播，不能吞掉或缓存失败值。`GET` miss 之间不提供跨节点 single-flight；并发 miss 允许多个节点同时回源并 `SET`。

### 4.4 失效与故障旁路

Redis-only region 的正常写路径如下：

1. 业务事务更新 PostgreSQL；
2. 事务提交后通过 `CacheInvalidationExecutor` 执行 Redis `DEL`；
3. Redis 删除失败不阻塞已经提交的业务，后续由 TTL 自动过期；
4. miss 或 Redis 故障时直接调用现有 PostgreSQL loader。

本次接受简单删除的最终一致性：事务更新前已启动的 loader 可能在 `DEL` 后把旧值重新写入 Redis；该旧值最长存活至该 region 的 TTL 到期。首期不实现 version 校验、generation fencing、分布式锁或刷新协调。

Redis 只支持按明确业务 key 的精确 `DEL`，不提供 `KEYS`、`SCAN`、按前缀扫描、全量清库或通用 region 清理 API。数据库恢复后也不主动遍历 Redis key；已有值自然按 TTL 过期，后续业务写入仍按 key 删除。

故障规则：

- `GET`、`SET`、`DEL` 超时或连接失败：记录低基数指标，按 cache-aside 旁路数据库；
- JSON 反序列化失败或 schema 不匹配：删除坏值并回源；
- value 超过 `max-value-bytes`：拒绝写缓存，不影响业务响应；
- Redis DOWN 不应把 PostgreSQL 正常的应用 readiness 置为 DOWN。

### 4.5 配置

现有 Shared provider 配置保持原样，生产默认仍为：

```yaml
algo-mentor:
  cache:
    shared-provider: ${CACHE_SHARED_PROVIDER:postgres-coherent-caffeine}
```

新增独立 Redis 配置，不再使用 `shared-provider=redis`：

```yaml
algo-mentor:
  cache:
    redis:
      enabled: ${CACHE_REDIS_ENABLED:true}
      host: ${CACHE_REDIS_HOST:localhost}
      port: ${CACHE_REDIS_PORT:6379}
      database: ${CACHE_REDIS_DATABASE:0}
      username: ${CACHE_REDIS_USERNAME:}
      password: ${CACHE_REDIS_PASSWORD:}
      ssl-enabled: ${CACHE_REDIS_SSL_ENABLED:false}
      command-timeout: ${CACHE_REDIS_COMMAND_TIMEOUT:100ms}
      connect-timeout: ${CACHE_REDIS_CONNECT_TIMEOUT:1s}
      shutdown-timeout: ${CACHE_REDIS_SHUTDOWN_TIMEOUT:2s}
      max-value-bytes: ${CACHE_REDIS_MAX_VALUE_BYTES:65536}
```

`RedisClient` 采用懒连接、自动重连和断线拒绝排队命令；密码和 TLS 凭据只从环境变量或外部配置注入。Redis 客户端依赖、连接关闭和 Micrometer 指标统一封装在 `backend/cache`。

`CacheProperties` 新增嵌套 `Redis` 属性，并在 `CacheConfigurationKeys` 新增以下稳定常量：

```java
public static final String REDIS_ENABLED = PREFIX + ".redis.enabled";
public static final String REDIS_HOST = PREFIX + ".redis.host";
public static final String REDIS_PORT = PREFIX + ".redis.port";
public static final String REDIS_DATABASE = PREFIX + ".redis.database";
public static final String REDIS_USERNAME = PREFIX + ".redis.username";
public static final String REDIS_PASSWORD = PREFIX + ".redis.password";
public static final String REDIS_SSL_ENABLED = PREFIX + ".redis.ssl-enabled";
public static final String REDIS_COMMAND_TIMEOUT = PREFIX + ".redis.command-timeout";
public static final String REDIS_CONNECT_TIMEOUT = PREFIX + ".redis.connect-timeout";
public static final String REDIS_SHUTDOWN_TIMEOUT = PREFIX + ".redis.shutdown-timeout";
public static final String REDIS_MAX_VALUE_BYTES = PREFIX + ".redis.max-value-bytes";
```

`CacheProperties.Redis` 的默认值和启动校验如下：

| 属性 | 默认值 | 校验 |
|---|---:|---|
| `enabled` | `true` | 布尔值 |
| `host` | `localhost` | 非空、非空白；不记录到日志 |
| `port` | `6379` | `1..65535`；不允许默认指向 `6380` |
| `database` | `0` | `0..15` |
| `username` / `password` | 空 | 仅外部配置注入；禁止日志输出 |
| `ssl-enabled` | `false` | 布尔值 |
| `command-timeout` | `100ms` | 正数且不超过 `5s` |
| `connect-timeout` | `1s` | 正数且不超过 `10s` |
| `shutdown-timeout` | `2s` | 正数且不超过 `10s` |
| `max-value-bytes` | `65536` | `1..65536` |

`CacheAutoConfiguration` 无条件提供一个 `RedisCacheRegionFactory`：缓存总开关或 Redis 开关关闭时返回 `BypassRedisCacheRegionFactory`；否则装配下列依赖链。这与 `SharedCacheRegionFactory` 平行，不使用 `@ConditionalOnProperty(shared-provider=...)`，也不改变 `postgres-coherent-caffeine` 自动配置。

```text
CacheProperties.Redis
  -> ObjectMapper（应用 bean；不存在时专用安全 mapper）
  -> JacksonRedisValueCodecFactory
  -> RedisConnectionManager（lazy）
  -> LettuceRedisCacheRegionFactory
```

`backend/cache/pom.xml` 新增运行时依赖 `io.lettuce:lettuce-core`、`com.fasterxml.jackson.core:jackson-databind`、`com.fasterxml.jackson.datatype:jackson-datatype-jsr310`；测试依赖新增 `org.testcontainers:testcontainers` 和 `org.testcontainers:junit-jupiter`。Lettuce 版本使用 Spring Boot 3.5.15 dependency management 的 `6.6.0.RELEASE`，不在业务模块重复声明 Redis 或 Jackson client 依赖。

若必须在后续独立升级 Lettuce，才在 `backend/pom.xml` 新增唯一的 `lettuce.version` property，并通过 dependency management 覆盖；本次保持 Spring Boot 的受管版本，避免无实际需求的版本分叉。

### 4.6 指标、日志和测试

保留现有 `CacheMetrics` 的 request/load/invalidation 指标，并新增 `RedisCacheMetrics` 专用低基数指标：

```text
algo_mentor_cache_redis_commands_total{cache,operation,result}
algo_mentor_cache_redis_command_duration_seconds{cache,operation}
algo_mentor_cache_redis_failures_total{cache,operation,reason}
algo_mentor_cache_redis_oversize_values_total{cache}
```

`operation` 只能为 `get`、`set`、`del`，`result` 只能为 `success`、`failure`，`reason` 只能为 `timeout`、`connection`、`codec`、`command`、`oversize`。region、operation、result 和 reason 只能使用有限枚举 tag；禁止 userId、邮箱、keyToken、payload、URI、用户名和密钥出现在日志或指标中。连接状态、重连次数、Redis 实例内存和淘汰数由基础设施监控采集，不在每个 region 重新注册。

测试至少覆盖：

- `GET`/`SET PX`/`DEL`、TTL、坏值、schema mismatch、超大值和负缓存；
- `RedisTtlCacheSpec`、key builder、JSON/scalar codec 以及 `CacheProperties.Redis` 的边界校验；
- Redis factory 对同名 region 的 specification/key codec/value codec 冲突拒绝，且不会注册 PostgreSQL Shared invalidation target；
- 自动配置在 Redis disabled 或 cache disabled 时提供 bypass factory；正常启用时使用 lazy connection，启动期间不执行 Redis 连接；
- Redis 停止、重启、淘汰、超时和断线时直接回源 PostgreSQL；允许数据库回源量增加，不增加熔断、限流或分布式 single-flight；
- 并发 `get`/`put`/`invalidate` 不抛出业务异常；接受事务提交后旧 loader 回填，并验证其至多存活一个业务 TTL；
- Redis-only region 不注册 Shared invalidation target，Shared region 仍由原有 PostgreSQL coherence 测试覆盖；
- Testcontainers Redis 7.x 集成测试和业务模块不得直接依赖 Lettuce 的架构测试。

## 五、实施顺序

本次先交付 Redis-only 能力，再按以下 6 个明确 region 逐个接入：

1. `ai-runtime-settings`；
2. `auth-beta-access-settings`；
3. `auth-access-snapshot`；
4. `ai-user-policy`；
5. `identity-user-relations`；
6. `auth-beta-email-membership`。

每个 region 接入前必须固定 value codec、负缓存 envelope、TTL、最大 value 大小、提交后 `DEL` 入口和回源指标。三个负缓存 envelope 的类型和字段约束以 4.2 节的表格为准。项目尚未上线，本次不设计新旧版本滚动发布兼容；完成该 region 的 Redis 命中、miss、TTL、故障旁路和删除测试后直接切换业务注入点。

实施阶段：

1. 在 `backend/cache` 增加 Redis client、配置、key builder、value codec、`RedisTtlCacheRegion` 和 factory；
2. 以一个独立业务门面接入并完成命中、miss、TTL、故障旁路和失效测试；
3. 通过 Redis P95、超时率、命中率、回源量和内存淘汰观察是否扩展到其他候选；
4. 按上述 6 个 region 单独接入或回滚，`generic-policy-set` 的 `SharedTtlCacheRegion` 和 PostgreSQL poller 全程保持可用。

### 5.1 已确认的实现边界

- `generic-policy-set` 保持 `SharedTtlCacheRegion`，其余六个指定 region 切换为 `RedisTtlCacheRegion`；
- 失效只在事务提交后按业务 key 执行 Redis `DEL`；
- 接受旧 loader 在删除后回填旧值，由业务 TTL 限制陈旧窗口；
- Redis 故障、超时和淘汰直接回源 PostgreSQL，接受由此产生的数据库流量放大；
- 不使用 `KEYS`、`SCAN`、按前缀删除、全量清库或通用 region 清理；
- 不实现数据库恢复后的 Redis 遍历清理，也不考虑新旧版本滚动发布兼容；
- `AiRuntimeCache`、`BetaAccessCache`、`AuthAccessSnapshotCache`、`UserRelationCache` 改为 Redis factory；`PolicySetCache` 继续使用 Shared factory，自动配置和测试必须同时装配两套 factory。

## 六、回滚

当前配置只提供全局 `CACHE_REDIS_ENABLED`，不提供 region 级 Redis 开关。因此它有两种不同含义的回退方式：

- 将 `CACHE_REDIS_ENABLED=false`：所有 Redis-only region 使用 `BypassRedisCacheRegionFactory`，直接回源 PostgreSQL；这是禁用 Redis 的安全降级，不会恢复原来的 Shared Caffeine 或 PostgreSQL coherence 语义。
- 回退单个 region 或恢复原 Shared 语义：将该业务门面显式改回原来的 `SharedCacheRegionFactory`（或 Local factory）并部署；`generic-policy-set` 始终不受影响。若后续需要无需重新部署的 region 级开关，必须另行设计并实现，不能假定现有全局开关具备该能力。

上述回退均不修改 `CACHE_SHARED_PROVIDER`，不删除 PostgreSQL 失效事件表，也不改数据库事实数据或业务事务边界。

Redis 中的值在回滚后自然过期；不执行按前缀扫描或批量清理。PostgreSQL 继续作为权威来源。现有 Shared cache 的回滚仍沿用 `postgres-coherent-caffeine` 配置。

## 七、明确不纳入

- Redis Streams、持久化队列和可靠消息；
- 登录 Session、Agent run lock、Tool 权限等待、一次性 token、Learner memory scope；
- 将所有 Shared cache 全局迁移到 Redis；`generic-policy-set` 暂不纳入本次迁移；
- 未经压测和一致性评审的通用 L1 + L2 组合缓存。

最终目标是：

> 保留 `SharedTtlCacheRegion` 的 PostgreSQL 事务失效 + 本地 Caffeine 语义，同时新增可按业务选择的纯 Redis 远程缓存。两者并存，互不通过全局 provider 切换互相废弃。
