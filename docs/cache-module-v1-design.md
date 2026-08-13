# 第一版缓存模块研发设计

更新时间：2026-07-22

状态：基础缓存模块已实现，Shared TTL 分布式一致性修订待实施

## 一、背景

当前项目已经出现多类适合缓存的高频读取：

- AI 动态全局策略和按用户覆盖策略；
- 认证请求中的用户状态、角色和内测准入设置；
- 固定字典、题目公共元数据和学习计划模板快照；
- 用户偏好、学习画像等按用户读取、允许短时间陈旧的业务快照。

这些数据以 PostgreSQL 为权威来源。基础缓存模块已经提供三类类型化缓存区域、Caffeine 实现、Spring 自动配置、事务提交后本地失效和 Micrometer 指标，但原设计将 `SharedTtlCacheRegion` 的第一版实现限定为单 JVM Caffeine，只把真正的共享语义留给未来 Redis。

该限制会让 TTL 同时承担两个互相冲突的职责：

1. 控制缓存失效机制异常时的最大陈旧时间；
2. 充当多节点之间唯一的数据同步手段。

如果为了管理员配置及时生效而把 TTL 设置为几秒，会造成热点配置频繁回源；如果为了提高命中率把 TTL 设置为几分钟，多节点又会在管理员修改后长时间读取旧值。

本次修订明确拆开这两个职责：

- `LocalCacheRegion` 保持节点本地语义，不增加数据库协调能力；
- `SharedTtlCacheRegion` 表达集群内共享失效语义；
- 第一版共享 provider 使用“PostgreSQL 失效事件 + 各节点 Caffeine”；
- value 继续存放在 JVM 内存，PostgreSQL 只保存轻量失效事件；
- TTL 只作为共享失效链路持续异常时的最终陈旧上限；
- 用户量、节点数和回源放大达到实际门槛后，再把共享 provider 替换为 Redis。

缓存仍然只保存权威数据的可重建副本。登录 Session、Agent 运行锁、Tool 权限等待状态等具有独立状态或协调语义的组件不纳入本模块。

## 二、已定决策

第一版按以下决策实施：

1. 保留单个 Maven 模块 `backend/cache`，artifactId 为 `cache`，不新增 `cache-core`、`cache-coherence-postgres` 或空的 `cache-redis` 模块。
2. Java 包根路径为 `org.congcong.algomentor.cache.*`，PostgreSQL 协调实现通过模块内子包隔离。
3. `LocalBoundedCacheRegion` 和 `LocalTtlCacheRegion` 始终表示节点本地缓存，不承诺跨节点失效或刷新。
4. `SharedTtlCacheRegion` 表示逻辑上的集群共享缓存；共享的是 key 失效和可见性契约，不要求 value 必须存放在共享介质。
5. 第一版提供两个共享 provider：
   - `caffeine`：单节点开发、测试或明确接受节点间差异的环境；
   - `postgres-coherent-caffeine`：value 存在各节点 Caffeine，通过 PostgreSQL 失效事件实现跨节点精确失效。
6. 第一版不引入 Redis、Guava Cache、多级缓存或分布式锁。
7. PostgreSQL 或其他业务 repository 始终是权威来源；缓存不可成为唯一事实来源。
8. 读取采用 cache-aside；写路径更新权威数据，并在同一数据库事务内写入共享失效事件。
9. 当前节点在事务提交后立即失效，其他节点通过增量 poller 消费事件后失效。
10. 共享缓存默认只失效，不由基础设施主动重新加载 value；下一次业务访问负责懒加载。
11. 共享缓存 key 必须通过稳定 `SharedCacheKeyCodec` 转换为无隐私泄露的 `keyToken`。
12. 共享 Caffeine provider 必须使用 generation fencing，禁止失效前启动的旧 loader 在失效后重新回填为可见值。
13. TTL 继续为必填正数，其含义调整为共享事件发布或消费持续失败时允许的最大陈旧时间。
14. 无 TTL 缓存只允许存放进程生命周期内不可变、使用版本化 key，或具有可靠全量重载边界的数据。
15. Spring Session JDBC 保持独立，未来是否迁移到 Redis 单独评估，不复用通用缓存 region。
16. Agent run lock、Tool permission coordinator、验证码、一次性 token 等需要原子占有、等待、消费或续期的状态继续使用专用接口。

## 三、目标与非目标

### 3.1 目标

- 为后端各模块提供统一、类型安全、可测试的缓存技术底座。
- 从 Java 类型上明确区分节点本地缓存和集群共享缓存。
- 在不引入 Redis 的情况下，为中小规模多节点部署提供秒级跨节点精确失效。
- 通过 Caffeine 的原子单 key 加载降低单 JVM 内并发回源。
- 统一缓存命名、容量、TTL、key 编码、失效、异常和指标语义。
- 保证业务写入、共享失效事件和事务提交具有明确的原子性边界。
- 保持业务模块对缓存 key、value、TTL、失效范围和失败语义的所有权。
- 为未来替换 Redis provider 保留稳定的业务缓存门面、namespace、schema version 和 key token。
- 第一批支撑 AI 动态策略和认证读取链路的缓存化。

### 3.2 非目标

- 不引入 Redis、Memcached 或其他远程 value store。
- 不实现本地 L1 + Redis L2 多级缓存。
- 不实现分布式锁、分布式 single-flight 或线性一致性缓存协议。
- 不保证强一致读取、跨节点 read-your-write 或失效瞬间所有在途请求同时切换。
- 不缓存登录 Session、密码凭据、Authorization、API key 或一次性 token。
- 不提供通用 key 扫描、模糊删除、按前缀删除或按用户扫描能力。
- 不实现后台 refresh、refresh-ahead、stale-while-revalidate 或通用缓存预热平台。
- 不通过 value 序列化后计算 hash 并逐 key 轮询数据库。
- 不要求所有 repository 查询都经过缓存。
- 不为了提高命中率缓存低复用、大对象或一致性要求过高的数据。

## 四、设计原则

### 4.1 缓存是副本，不是状态存储

缓存中的值必须能够从权威来源重新加载。缓存丢失、进程重启、容量淘汰或共享事件表清理不能造成业务状态丢失。

以下能力不属于普通缓存：

| 能力 | 原因 | 继续使用的边界 |
|---|---|---|
| HTTP 登录 Session | Session 自身是登录状态，需要 touch、过期和按 principal 吊销 | Spring Session JDBC，未来单独评估 Redis |
| Agent run lock | 需要原子占有、owner token、续期和安全释放 | `AgentRunLockManager` |
| Tool 权限等待 | 需要等待、决策、超时、取消和一次性完成 | `AgentToolPermissionCoordinator` |
| 验证码和一次性 token | 需要原子消费和不可重复使用 | 独立 Expiring State Store，后续单独设计 |

### 4.2 Local 与 Shared 按集群可见性区分

缓存类型不再按“当前 value 是否位于 JVM”判断，而按业务要求的可见性判断：

- 允许节点之间存在一个完整 TTL 的差异，选择 `LocalTtlCacheRegion`；
- 数据在进程生命周期不可变，选择 `LocalBoundedCacheRegion`；
- 管理员或用户修改后要求其他节点及时失效，选择 `SharedTtlCacheRegion`。

动态全局设置虽然 key 数量少，也应使用 `SharedTtlCacheRegion`，因为它们需要跨节点及时生效。用户粒度只影响 key 和容量，不自动意味着必须使用 Redis。

### 4.3 Shared 表达逻辑共享，不限定物理介质

`SharedTtlCacheRegion` 的契约是：

- 同一 namespace 和 schema version 在各节点使用相同 key token；
- 一个节点成功提交失效事件后，其他健康节点在约定传播时间内失效对应 key；
- provider 异常时，TTL 限制旧值继续存活的最长时间；
- provider 可以是 PostgreSQL 协调的本地 Caffeine，也可以在未来替换为 Redis。

第一版共享的是失效控制信息，不在 PostgreSQL 中存放缓存 value。

### 4.4 TTL 是故障兜底，不是跨节点广播

共享缓存需要分别定义两个时间：

| 参数 | 含义 |
|---|---|
| `pollInterval` | 正常情况下其他节点发现失效事件的时间粒度 |
| `ttl` | 事件发布或消费持续异常时，旧值允许保留的最大时间 |

业务接入时应先确定正常传播 SLA 和降级陈旧上限，再决定 TTL。不能仅依据配置修改频率或命中率设置 TTL。

### 4.5 业务模块拥有缓存语义

`backend/cache` 不定义 `AiUserPolicyCache`、`AuthIdentityCache` 等业务对象。业务模块负责：

- 定义强类型 key 和不可变 value；
- 定义缓存名称、namespace、schema version 和配置 key；
- 提供稳定、无隐私泄露的 `SharedCacheKeyCodec`；
- 决定 TTL、最大容量和是否允许负缓存；
- 定义 `invalidateUser(...)` 等精确失效方法；
- 决定加载失败时是失败关闭、业务降级还是直接抛错；
- 保证所有影响缓存的写入入口触发共享失效。

通用缓存模块只负责容量、过期、并发加载、事件发布和消费、generation fencing、基础失效和指标。

### 4.6 显式调用优先于缓存注解

第一版不以 Spring `@Cacheable`、`@CacheEvict` 为主要接入方式，原因包括：

- `cacheNames` 容易退化为跨模块字符串契约；
- AOP 隐藏 loader、异常、负缓存和 key token 行为；
- 共享事件必须与数据库事务建立明确关系；
- 难以在类型上区分本地缓存和共享缓存；
- 测试时不容易确认某次读取、事件发布和失效路径。

缓存调用保留在 service 或专用缓存门面中，使读取和失效路径可以直接阅读和单元测试。

## 五、总体架构

### 5.1 单 Maven 模块

继续使用已有模块：

```text
backend/cache
├── pom.xml
├── src/main/java/org/congcong/algomentor/cache
│   ├── api/                    通用 region 接口和类型标识
│   ├── spec/                   名称、容量、TTL、namespace 和 key codec
│   ├── factory/                本地与共享 region 工厂
│   ├── registry/               region 定义和本地失效目标注册
│   ├── caffeine/               Caffeine 基础实现
│   ├── coherence/              共享事件、cursor、generation 和协调 SPI
│   ├── coherence/postgres/     PostgreSQL event store、poller 和 provider
│   ├── invalidation/           本地事务提交后失效执行器
│   ├── metrics/                缓存与 coherence 指标
│   └── config/                 Spring Boot 自动配置和公共常量
├── src/main/resources
│   ├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── db/migration/cache/
└── src/test/java/org/congcong/algomentor/cache
```

当前阶段不新增 `cache-coherence-postgres` Maven 模块。只有出现以下情况才重新评估拆分：

- Redis provider 已实际落地并引入较重依赖；
- 存在不使用 PostgreSQL 的独立应用需要复用 cache core；
- 多个 provider 的自动配置开始冲突；
- 缓存基础设施需要独立发布或具有独立维护边界。

### 5.2 Maven 依赖

`backend/cache/pom.xml` 保留已有依赖，并增加 optional JDBC 编译依赖：

```text
com.github.ben-manes.caffeine:caffeine
org.springframework:spring-context
org.springframework:spring-tx
org.springframework:spring-jdbc                  optional
org.springframework.boot:spring-boot-autoconfigure
io.micrometer:micrometer-core
org.slf4j:slf4j-api
```

`spring-jdbc` 设置为 optional，使只使用本地缓存的下游模块不会被迫传递 JDBC。`mentor-api` 已提供 `spring-boot-starter-jdbc`、Flyway 和 PostgreSQL driver，运行时不需要新增独立基础设施依赖。

模块不依赖 `common`、MyBatis、Jackson 或任何业务模块。PostgreSQL event store 使用 Spring JDBC，避免为一张基础设施表引入 MyBatis mapper。

### 5.3 依赖方向

```text
backend/cache
      ↑
      ├── auth
      ├── ai-governance
      ├── mentor-application
      └── 其他需要缓存的业务模块

mentor-api
  提供 DataSource、Flyway、运行配置和最终应用装配
```

禁止 `cache` 依赖 `auth`、`identity`、`ai-governance`、`mentor-application` 或 `mentor-api`。

## 六、缓存类型

### 6.1 LocalBoundedCacheRegion

本地有界、无 TTL 缓存：

```java
public interface LocalBoundedCacheRegion<K, V> extends LocalCacheRegion<K, V> {
}
```

适用条件：

- 数据在当前进程生命周期内不可变；或
- key 包含数据版本，旧版本自然不再被读取；或
- 存在可靠、可测试的全量重载边界。

必须配置 `maximumSize` 或后续明确的 `maximumWeight`。禁止用它缓存管理员可在线修改的配置、用户状态、权限、额度和止损开关。

第一版采用 Caffeine `maximumSize`，不设置 `expireAfterWrite` 或 `expireAfterAccess`，不参与 PostgreSQL coherence。

### 6.2 LocalTtlCacheRegion

本地有界、带 TTL 缓存：

```java
public interface LocalTtlCacheRegion<K, V> extends LocalCacheRegion<K, V> {
}
```

适用于：

- 节点本地计算结果；
- 允许不同节点在完整 TTL 内读取不同值的公共快照；
- 即使业务数据发生变化，也不要求主动广播到其他节点的数据。

第一版统一使用 `expireAfterWrite`，不使用 `expireAfterAccess`，避免热点 key 因持续访问而永久不刷新。

### 6.3 SharedTtlCacheRegion

集群共享 TTL 缓存：

```java
public interface SharedTtlCacheRegion<K, V> extends CacheRegion<K, V> {
}
```

适用于：

- 管理员修改后需要其他节点及时生效的全局配置；
- 用户状态、角色、策略、偏好等按用户读取的数据；
- 多节点部署下需要精确 key 失效，但允许最终一致的数据；
- value 可以从 PostgreSQL 重新加载，且允许 TTL 作为故障兜底的数据。

第一版生产 provider 为 `postgres-coherent-caffeine`：

- 每个节点使用独立 Caffeine 保存 value；
- PostgreSQL 只保存失效事件；
- 当前节点事务提交后立即失效；
- 其他节点通过 poller 消费事件并精确失效；
- 缓存 miss 时每个节点仍独立回源，不承诺跨节点 single-flight。

开发和单元测试可以选择 `caffeine` provider。该 provider 只提供单 JVM 语义，应用启动日志必须明确输出警告。

### 6.4 三类缓存对比

| 类型 | 第一版 value 介质 | TTL | 跨节点失效 | 典型数据 |
|---|---|---:|---|---|
| `LocalBoundedCacheRegion` | Caffeine | 无 | 无 | 发布期不可变字典、版本化模板 |
| `LocalTtlCacheRegion` | Caffeine | 必须 | 无 | 节点本地快照、允许节点差异的数据 |
| `SharedTtlCacheRegion` | Caffeine | 必须 | PostgreSQL 事件 | AI 设置、认证快照、用户策略和偏好 |

## 七、核心接口与规格

### 7.1 CacheRegion

保留已有接口：

```java
public interface CacheRegion<K, V> {

  Optional<V> getIfPresent(K key);

  V get(K key, Function<? super K, ? extends V> loader);

  void put(K key, V value);

  void invalidate(K key);
}

public interface LocalCacheRegion<K, V> extends CacheRegion<K, V> {
  void invalidateAll();
}
```

接口约束：

- key、value 和 loader 不得为 `null`；
- loader 返回 `null` 视为编程错误，不写入缓存；
- loader 异常原样向上传播，不缓存异常或失败占位；
- `put` 主要用于明确预热和测试，业务写路径默认只做失效；
- `invalidateAll()` 只存在于 `LocalCacheRegion` 的公开接口；
- `SharedTtlCacheRegion` 不提供业务侧整区扫描和清理能力；
- `SharedTtlCacheRegion.invalidate(key)` 只表示当前 provider 的直接失效原语，供协调器、provider 内部和测试使用；
- 正式业务写路径不得直接调用 shared region 的 `invalidate(key)`，必须通过 `SharedCacheInvalidationCoordinator` 建立业务事务、共享事件和本地提交后失效的完整边界；
- 通用接口不暴露 Caffeine `Cache`、JDBC 或 provider 实现类。

需要缓存“不存在”结果时，业务模块应使用明确领域哨兵值，或把 `Optional<T>` 作为 value。负缓存必须使用 TTL region。

### 7.2 SharedCacheKeyCodec

共享缓存必须正式定义 key token 编码：

```java
public interface SharedCacheKeyCodec<K> {
  String encode(K key);
}
```

约束：

- 同一个业务 key 在所有节点编码结果必须一致；
- 同一个 namespace 内不同业务 key 不得编码为同一 token；
- token 必须稳定、长度受控，不包含空白和控制字符；
- token 不得直接暴露邮箱、手机号、token 等隐私内容；
- token 不要求可逆；
- schema 发生不兼容变化时升级 `schemaVersion`，不要悄悄改变编码结果。

schema version 变更期间不默认兼容滚动部署。新旧版本节点需要同时在线时，必须选择以下一种发布方式：

- 写路径在过渡窗口内同时发布新旧 schema version 的失效事件；或
- 先停止旧版本流量并排空旧节点，再启用只发布新 schema version 的版本。

禁止新节点只发布新版本事件、同时让仍持有旧缓存的节点继续长期服务。

示例：

```text
ai-runtime-settings   -> singleton
ai-user-policy        -> 42
auth-access-snapshot  -> 42
beta-email-membership -> sha256(normalizedEmail)
```

未来 Redis key 继续遵循：

```text
algo-mentor:{namespace}:v{schemaVersion}:{keyToken}
```

### 7.3 工厂接口

本地缓存工厂保持不变，共享工厂增加 key codec：

```java
public interface LocalCacheRegionFactory {

  <K, V> LocalBoundedCacheRegion<K, V> createBounded(
      LocalBoundedCacheSpec spec);

  <K, V> LocalTtlCacheRegion<K, V> createTtl(
      LocalTtlCacheSpec spec);
}

public interface SharedCacheRegionFactory {

  <K, V> SharedTtlCacheRegion<K, V> createTtl(
      SharedTtlCacheSpec spec,
      SharedCacheKeyCodec<K> keyCodec);
}
```

业务模块通过工厂创建具名 bean，不在业务代码中直接调用 `Caffeine.newBuilder()` 或 JDBC。

### 7.4 缓存规格

```java
public record CacheRegionName(String value) {
}

public record LocalBoundedCacheSpec(
    CacheRegionName name,
    long maximumSize
) {
}

public record LocalTtlCacheSpec(
    CacheRegionName name,
    long maximumSize,
    Duration ttl
) {
}

public record SharedTtlCacheSpec(
    CacheRegionName name,
    String namespace,
    int schemaVersion,
    long maximumSize,
    Duration ttl
) {
}
```

构造时统一校验：

- cache name 和 namespace 非空，只允许稳定的小写 kebab-case；
- `maximumSize > 0`；
- TTL region 的 `ttl` 必须为正数；
- `schemaVersion >= 1`；
- 同一应用内 cache name 不得重复注册为不同类型或不同规格；
- 同一 namespace 和 schema version 只能绑定一个 cache name 和 key codec 语义。

`CacheRegionRegistry` 继续负责定义冲突检查。共享 provider 另建内部失效目标注册表，用于按 cache name 和 key token 定位当前 JVM 中的 Caffeine entry；该注册表不暴露给业务代码。

## 八、Provider 设计

### 8.1 Local Caffeine

两类 local region 继续沿用当前实现：

```text
LocalBoundedCacheSpec
  -> Caffeine.maximumSize(maximumSize)

LocalTtlCacheSpec
  -> Caffeine.maximumSize(maximumSize)
  -> expireAfterWrite(ttl)
```

metrics 开启时使用 `recordStats()`。第一版不启用 weak/soft reference、async cache、refreshAfterWrite 或自定义 scheduler。

### 8.2 单节点 Shared Caffeine

`shared-provider=caffeine` 时：

- value 存在当前 JVM Caffeine；
- key 仍通过 `SharedCacheKeyCodec` 转换为 token，提前固定未来 provider 契约；
- invalidate 只影响当前节点；
- 只允许开发、测试或明确的单节点部署；
- 启动日志输出 `provider=caffeine scope=single-jvm`。

### 8.3 PostgreSQL Coherent Caffeine

`shared-provider=postgres-coherent-caffeine` 时：

```text
业务 key
  -> SharedCacheKeyCodec
  -> keyToken
  -> 当前节点 Caffeine<keyToken, VersionedValue<V>>

共享失效
  -> PostgreSQL cache_invalidation_event
  -> 每节点增量 poller
  -> 本地精确 invalidate(keyToken)
```

该 provider 不把 value 写入 PostgreSQL，不需要 value serializer，也不执行数据库 value hash 对比。

## 九、PostgreSQL 失效事件

### 9.1 表结构

迁移脚本放在：

```text
backend/cache/src/main/resources/db/migration/cache/
```

建议表结构：

```sql
CREATE TABLE cache_invalidation_event (
  id BIGSERIAL PRIMARY KEY,
  cache_name VARCHAR(100) NOT NULL,
  namespace VARCHAR(100) NOT NULL,
  schema_version INTEGER NOT NULL,
  key_token VARCHAR(200) NULL,
  event_type VARCHAR(24) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_cache_invalidation_event_type
    CHECK (event_type IN ('KEY_INVALIDATE', 'REGION_INVALIDATE')),
  CONSTRAINT ck_cache_invalidation_event_key
    CHECK (
      (event_type = 'KEY_INVALIDATE' AND key_token IS NOT NULL)
      OR (event_type = 'REGION_INVALIDATE' AND key_token IS NULL)
    )
);

CREATE INDEX idx_cache_invalidation_event_created_at
  ON cache_invalidation_event(created_at);
```

`id` 同时作为全局事件顺序和 poller cursor。事件不存 value、业务对象、用户 ID tag 或异常信息。

### 9.2 事件发布的事务边界

共享缓存写路径必须满足：

```text
业务事务
  -> 更新权威业务表
  -> 插入 cache_invalidation_event
  -> 注册当前节点 afterCommit 本地失效
  -> commit
```

事件插入必须加入当前业务事务，不能等业务提交后再插入：

- 业务事务回滚时，失效事件一起回滚；
- 事件插入失败时，业务事务失败，避免数据库新值已经提交但其他节点永远收不到失效；
- 事务提交后，本节点立即失效；
- 其他节点随后通过 poller 消费同一事件。

共享失效协调器建议定义为：

```java
public interface SharedCacheInvalidationCoordinator {

  <K, V> void invalidate(
      SharedTtlCacheRegion<K, V> region,
      K key);
}
```

业务专用缓存门面隐藏 region 和 coordinator，业务 service 只调用 `invalidateUser(userId)` 等领域方法。

不存在活动事务时，协调器在成功插入自动提交事件后立即失效当前节点。正式业务写路径应尽量建立明确的 Spring 事务；无事务模式主要用于已经完成权威写入的兼容路径和测试。

### 9.3 Poller

每个应用节点运行一个共享 poller，而不是每个 region 启动独立任务。poller 每次批量读取：

```sql
SELECT id, cache_name, namespace, schema_version, key_token, event_type, created_at
FROM cache_invalidation_event
WHERE id > :cursor
ORDER BY id ASC
LIMIT :batchSize;
```

处理规则：

- `KEY_INVALIDATE`：按 cache name 和 key token 精确失效；
- `REGION_INVALIDATE`：仅供批量结构切换、事件缺口恢复和基础设施内部使用；
- 未注册的 cache name 仍推进 cursor，但记录受控 warn 和指标；
- schema version 不匹配时不操作当前 region，并记录版本不匹配指标；
- 只有一批事件全部处理成功后才推进本地 cursor；
- poller 失败不清空缓存，等待下一轮重试，TTL 继续兜底。

默认参数建议：

```text
pollInterval = 1s
batchSize = 500
jitterRatio = 0.2
eventRetention = 24h
cleanupInterval = 1h
```

每个节点加入少量轮询抖动，避免所有节点在同一毫秒查询 PostgreSQL。

### 9.4 节点启动与 cursor

节点启动时本地共享缓存为空，因此不需要持久化每个节点的 cursor：

1. poller 读取当前最大 event id 作为启动高水位；
2. 本地共享缓存仍为空；
3. 高水位之后提交的事件由正常轮询消费；
4. 节点进程重启后缓存和 cursor 一起丢失，不存在旧缓存跨重启遗留。

如果应用可能在高水位初始化完成前接收流量，应由自动配置保证共享 provider 和 poller 初始化先于 readiness 状态。

### 9.5 事件清理与缺口恢复

事件表需要定期删除超过 retention 的记录。retention 必须显著大于：

- 共享缓存中最大的 TTL；
- 节点预期的最长短暂不可用时间；
- 正常发布、滚动重启和故障恢复窗口。

如果一个仍在运行的节点 cursor 已落后于当前最小保留 id，说明它可能错过事件。该节点必须：

1. 清空当前 JVM 内所有 Shared Caffeine region；
2. 把 cursor 跳到当前最大 event id；
3. 记录 gap recovery 指标和告警日志。

不允许直接跳过缺口并继续使用已有缓存。

## 十、Caffeine 并发与 Generation Fencing

### 10.1 单 key 并发加载

`CacheRegion.get(key, loader)` 继续使用 Caffeine 原子加载能力。单 JVM 内同一个 key 同时 miss 时，只允许一个 loader 执行，其余调用等待同一次结果。

该保证只适用于当前 JVM。PostgreSQL coherent provider 不提供跨节点 single-flight；多个节点在同一 key 失效后最多各自回源一次。

### 10.2 旧值回填竞态

仅调用 Caffeine `invalidate(key)` 无法消除以下竞态：

```text
T1: cache miss，开始读取旧数据库值
T2: 业务事务提交新值和失效事件
T3: 节点消费事件并 invalidate(key)
T1: 把旧值重新写回缓存
```

如果 TTL 已设置为几分钟，旧值可能在失效后继续存在几分钟。因此 Shared Caffeine provider 必须为每个 key token 维护本地 generation：

- loader 启动时记录 generation；
- 失效事件到达时先递增 generation，再清除 entry；
- 缓存内部保存 `VersionedValue<V>`，包含加载时 generation；
- loader 返回后发现 generation 已变化时，不允许把结果作为有效 entry 返回；
- 旧 generation entry 即使因并发时序重新进入 Caffeine，也必须在下一次读取时被识别并移除。

generation 只存在当前 JVM，不写入 PostgreSQL。PostgreSQL event id 负责跨节点顺序，本地 generation 负责阻止在途旧 loader 回填。

### 10.3 淘汰与过期

- 容量淘汰和 TTL 过期属于正常缓存行为，不记录 error 日志；
- eviction 指标按受控 cause 统计；
- 业务代码不得依赖 entry 一定保存到 TTL 到期；
- `maximumSize` 是近似容量约束，不作为精确业务计数；
- generation 元数据必须随 entry 淘汰或通过有界机制清理，不能形成无上限旁路 Map。

## 十一、旁路缓存生命周期

### 11.1 Local 读取

```text
业务 Service
  -> 本地业务缓存门面.get(key, loader)
       -> LocalCacheRegion.get(key, loader)
            -> hit: 返回缓存值
            -> miss: 当前 JVM 单 key 原子回源
```

Local region 不发布或消费 PostgreSQL 事件。

### 11.2 Shared 读取

```text
业务 Service
  -> 共享业务缓存门面.get(key, loader)
       -> key codec 生成 keyToken
       -> 检查当前 generation 下的 Caffeine entry
            -> hit: 返回缓存值
            -> miss: 当前 JVM 单 key 原子回源
            -> generation 已变化: 丢弃并重试
```

### 11.3 Local 写后失效

Local region 继续使用已有 `CacheInvalidationExecutor`：

```java
public interface CacheInvalidationExecutor {
  void afterCommit(Runnable invalidation);
}
```

事务提交后执行本地 `invalidate(key)` 或 `invalidateAll()`；事务回滚不执行。失效异常记录指标和日志，不反向改变已经提交的业务事务。

### 11.4 Shared 写后失效

Shared region 使用 `SharedCacheInvalidationCoordinator`：

```text
业务写入
  -> 同事务 append 共享失效事件
  -> afterCommit 当前节点精确失效
  -> 其他节点 poll 后精确失效
```

业务写方法不得在提交前通过同一个缓存重新读取刚写入的数据。需要返回更新结果时，应使用写入参数、mapper 返回值或绕过缓存查询权威来源。

### 11.5 删除、批量更新与 schema 变化

- 单 key 更新或删除：发布一个 `KEY_INVALIDATE`；
- 小范围已知 key 批量更新：逐 key 发布事件，可在一次 SQL batch 中插入；
- 全局单例设置：失效固定 `singleton` token；
- 无法可靠计算受影响 key 的共享缓存：优先升级 namespace/schema version；
- 仅在明确的全量导入或事件缺口恢复场景使用 `REGION_INVALIDATE`；
- 业务代码不得遍历缓存 key 推导受影响范围；
- Shared 公共接口仍不提供通用 `invalidateAll()`。

## 十二、一致性与故障模型

### 12.1 正常一致性

`postgres-coherent-caffeine` 提供最终一致性：

- 当前写入节点在事务提交后立即失效；
- 其他健康节点在一个或少量 poll interval 内失效；
- 默认目标为 1 至 2 秒跨节点传播；
- 下一次读取从权威数据库加载最新值；
- 不承诺失效前已经开始的业务请求中途切换值。

### 12.2 降级一致性

发生以下故障时，TTL 是最终陈旧上限：

- 事件 poller 暂时无法访问 PostgreSQL；
- 本地失效执行失败；
- 节点长时间暂停；
- 事件消费线程异常退出但应用仍继续服务。

业务 TTL 必须根据该降级场景可以接受的最大陈旧时间设置。共享机制存在不等于可以使用无限 TTL。

### 12.3 事件发布失败

事件插入和业务写入位于同一个 PostgreSQL 事务时，事件发布失败应导致业务事务回滚。这是 Shared region 相比 Local region 更强的写入约束。

如果某条兼容写路径无法加入事务，必须明确接受：

- 业务写已成功但事件插入失败时，只能依赖 TTL；
- 该路径需要错误指标和告警；
- 不能作为管理员配置、权限和用户状态的长期正式写入方式。

### 12.4 绕过应用直接修改数据库

直接 SQL 修改业务表不会自动生成失效事件，除非对应表另建数据库 trigger。第一版不为所有业务表生成通用 trigger。

运维规则：

- 正常修改必须经过业务 service 或管理 API；
- 紧急 SQL 必须同步插入对应失效事件；
- 对极少数关键单例配置，可以后续单独增加 trigger，但不把 trigger 作为通用缓存接口的一部分。

### 12.5 强一致业务边界

以下逻辑不能依赖 Shared TTL 缓存作为最终判断：

- AI 每日额度原子扣减；
- 账户余额或付费权益扣减；
- 数据库行锁和版本切换；
- 一次性 token 消费；
- 分布式锁、owner token 和 lease；
- 权限变更事务内部的最终写入校验。

缓存只减少重复读取，不代替权威事务判断。

## 十三、业务接入方式

### 13.1 业务缓存门面

业务模块不应把通用 region、key codec 或 coordinator 暴露给 controller。建议在所属模块建立专用门面：

```java
public interface AiRuntimePolicyCache {

  AiRuntimeSettings getSettings(Supplier<AiRuntimeSettings> loader);

  AiUserPolicy getUserPolicy(long userId, LongFunction<AiUserPolicy> loader);

  void invalidateSettings();

  void invalidateUserPolicy(long userId);
}
```

实现类内部组合两个 `SharedTtlCacheRegion`，并通过共享失效协调器发布事件。未来替换 Redis provider 时，业务 service 和门面接口保持不变。

### 13.2 AI 治理缓存

当前 `AiRuntimePolicyService` 每次解析策略都会查询全局设置和当前用户覆盖策略。两者都需要管理员修改后跨节点及时生效，因此均使用 Shared TTL：

| 数据 | 类型 | key token | TTL 含义 | 失效入口 |
|---|---|---|---|---|
| 全局 AI 设置 | `SharedTtlCacheRegion` | `singleton` | coherence 故障时止损开关最大陈旧时间 | 管理员更新全局设置 |
| 用户 AI 覆盖策略 | `SharedTtlCacheRegion` | userId | coherence 故障时用户策略最大陈旧时间 | 管理员 upsert 或删除用户覆盖 |

`AiRuntimeSettings` 缓存应保存数据库设置快照或缺失状态，不应缓存依赖具体 `AiPurposePolicy` 计算后的 fallback 结果，避免不同 purpose 的静态默认额度互相污染。

全局 `aiEnabled` 是运营止损开关。正常跨节点生效时间由 poll interval 保证，TTL 只定义 coherence 失效后的降级上限。

### 13.3 认证缓存

当前已认证 API 请求会读取用户状态、角色、内测开关和邮箱准入结果。建议建立最小认证快照：

```java
public record AuthAccessSnapshot(
    long userId,
    String email,
    AuthUserStatus status,
    List<AuthRole> roles
) {
}
```

建议接入：

| 数据 | 类型 | key token | 失效入口 |
|---|---|---|---|
| 用户访问快照 | `SharedTtlCacheRegion` | userId | 禁用、恢复、删除、角色或身份邮箱变化 |
| 内测准入设置 | `SharedTtlCacheRegion` | `singleton` | 管理员更新开关 |
| 邮箱准入结果 | `SharedTtlCacheRegion` | 规范化邮箱摘要 | 白名单增加或删除 |

不建议缓存最终 `ALLOWED/DENIED` 组合判断。认证过滤器应从用户快照、全局设置和邮箱准入结果组合计算，避免一个最终决策同时依赖多个失效入口。

认证缓存不得保存密码 hash、OAuth token、SecurityContext、完整 Session 或不必要的用户隐私字段。loader 失败时保持现有失败关闭语义。

### 13.4 适合 Local 的数据

以下数据在当前 seed 发布流程中随导入进程完成后退出，服务进程内可视为不可变快照，可以继续使用 Local region：

- 题库筛选项和固定标签字典；
- 规范化题目标签关系；
- 学习计划模板列表和模板详情；
- 代码内置、版本化的 prompt 或固定映射。

如果未来增加在线导入或管理员实时编辑，这些缓存需要重新评估为 Shared TTL，不能只因为 key 数量少继续使用 Local。

### 13.5 不适合第一批缓存的数据

- AI 每日用量扣减和 admission 原子判断；
- learner profile 的 `findCurrentForUpdate` 和版本切换写路径；
- practice session 创建、状态流转和 code review 完成门禁；
- Agent run、message、trace 和持久化队列状态；
- 低频管理员列表查询；
- 包含完整聊天、完整代码或大 Markdown 的对象；
- 已作为持久化业务派生物存在的 ReviewCard 数据。

## 十四、配置设计

### 14.1 模块公共配置

模块公共配置前缀保持：

```text
algo-mentor.cache
```

建议配置：

```yaml
algo-mentor:
  cache:
    enabled: true
    metrics-enabled: true
    shared-provider: postgres-coherent-caffeine
    coherence:
      enabled: true
      poll-interval: 1s
      batch-size: 500
      jitter-ratio: 0.2
      event-retention: 24h
      cleanup-interval: 1h
```

provider 约束：

- `caffeine`：单节点开发和测试；
- `postgres-coherent-caffeine`：需要 `DataSource` 和 Spring JDBC；
- 选择 PostgreSQL provider 但缺少 DataSource 时应用启动失败，不静默降级；
- 未来真正实现 Redis 后再增加 `redis`，不预建空 provider。

### 14.2 enabled 与 coherence 的关系

`enabled=false` 表示当前节点读取使用 bypass region，每次执行 loader。它不应默认禁止共享事件发布，因为该节点仍可能承载管理员写请求，而其他节点仍在使用缓存。

因此：

- cache read bypass 和 coherence event publication 使用不同开关；
- 仅在整个部署明确关闭共享缓存时，才关闭 coherence；
- 集群中 provider、namespace 和 schema version 配置必须一致；
- 配置不一致应通过启动检查或指标尽早暴露。

### 14.3 业务缓存配置

TTL 和容量继续由数据所属模块管理，例如：

```yaml
algo-mentor:
  ai:
    governance:
      cache:
        runtime-settings:
          ttl: 2m
          maximum-size: 2
        user-policy:
          ttl: 5m
          maximum-size: 10000
```

这里的 TTL 表示 coherence 持续异常时的最大陈旧上限，不代表管理员修改需要等待两分钟或五分钟。正常修改由失效事件在约 1 至 2 秒内传播。

具体业务 TTL 仍需在业务缓存接入阶段逐项确认，示例值不是所有环境的最终强制值。

## 十五、Spring 自动配置

### 15.1 基础自动配置

已有 `CacheAutoConfiguration` 继续提供：

- `CacheMetrics`；
- `CacheRegionRegistry`；
- `LocalCacheRegionFactory`；
- `CacheInvalidationExecutor`；
- disabled/bypass factories。

### 15.2 Shared provider 自动配置

按 `algo-mentor.cache.shared-provider` 显式装配一个 `SharedCacheRegionFactory`：

```text
caffeine
  -> 单 JVM Caffeine SharedCacheRegionFactory

postgres-coherent-caffeine
  -> PostgreSQL event store
  -> Shared invalidation coordinator
  -> Shared event poller
  -> generation-aware Caffeine SharedCacheRegionFactory
```

PostgreSQL provider 自动配置增加：

```java
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
    name = "algo-mentor.cache.shared-provider",
    havingValue = "postgres-coherent-caffeine")
```

业务 region bean 仍由使用方模块配置类创建。自动配置不扫描业务配置 Map，也不自动创建未知名称的缓存。

所有可替换 bean 使用 `@ConditionalOnMissingBean`。同一应用只能存在一个活动 `SharedCacheRegionFactory`，provider 冲突时启动失败。

### 15.3 Flyway

`mentor-api` 已使用 `classpath:db/migration` 递归扫描依赖模块资源。cache 模块的失效事件迁移脚本沿用全项目唯一 Flyway V 版本号，不新增独立 Flyway 实例。

## 十六、可观测性

### 16.1 现有缓存指标

保留：

```text
algo_mentor_cache_requests_total{cache,result=hit|miss}
algo_mentor_cache_loads_total{cache,result=success|failure}
algo_mentor_cache_load_duration_seconds{cache}
algo_mentor_cache_invalidations_total{cache,type=key|all,result=success|failure}
algo_mentor_cache_evictions_total{cache,cause}
algo_mentor_cache_estimated_size{cache}
```

### 16.2 Coherence 指标

新增低基数指标：

```text
algo_mentor_cache_coherence_events_published_total{cache,result}
algo_mentor_cache_coherence_polls_total{result}
algo_mentor_cache_coherence_events_consumed_total{cache,type,result}
algo_mentor_cache_coherence_poll_lag_seconds
algo_mentor_cache_coherence_last_success_age_seconds
algo_mentor_cache_coherence_gap_recoveries_total
algo_mentor_cache_coherence_generation_retries_total{cache}
algo_mentor_cache_coherence_cleanup_total{result}
```

约束：

- `cache` tag 只能使用注册时的固定 cache name；
- 不以 key、key token、userId、email、namespace 或异常消息作为 tag；
- loader 业务异常类型不作为高基数 tag；
- cursor 可以作为日志诊断字段，但不建议作为 Prometheus 高变化 tag。

### 16.3 日志

- 禁止输出缓存 value；
- 默认不输出 key token；确需诊断时只输出不可逆摘要；
- 正常 TTL 过期、容量淘汰和事件消费不逐条打印日志；
- poller 连续失败、事件缺口恢复、未知 cache name、schema 不匹配需要受控日志；
- 应用启动时明确记录 shared provider、poll interval、batch size 和 scope；
- `provider=caffeine` 必须记录单节点语义警告。

### 16.4 评估指标

缓存接入后至少观察：

- hit ratio、loader QPS 和 loader p95/p99；
- PostgreSQL 对应业务查询次数和连接池等待；
- 失效事件发布失败数；
- poll lag、last success age 和事件积压；
- 管理员配置提交到其他节点生效的实际延迟；
- generation retry 数量；
- 每次共享 key 失效引起的跨节点回源放大。

命中率低且回源成本不高的 region 应删除，而不是持续增大容量或 TTL。

## 十七、安全与数据约束

- 缓存中不得保存 API key、访问令牌、完整 Authorization、密码 hash 或临时密码。
- 不缓存完整 Agent trace、用户完整代码和完整聊天内容。
- 用户缓存 value 使用最小不可变快照，不直接缓存可变 MyBatis row 对象。
- value 使用 record 或不可变集合，放入缓存后不得原地修改。
- key token 不得直接包含邮箱、手机号、OAuth subject 或其他敏感标识。
- 失效事件表不保存 value、原始敏感 key 或业务审计内容。
- namespace、schema version 和 cache name 属于稳定基础设施契约。
- 缓存命中不能绕过权限最终检查、额度原子扣减或数据库行锁。
- 未来 Redis provider 避免 Java 原生序列化，统一使用明确 schema 的 Jackson value。

## 十八、测试设计

### 18.1 Local cache 单元测试

保留并完善：

- `LocalBoundedCacheRegion` 容量淘汰；
- TTL region 使用可控 ticker 验证 `expireAfterWrite`；
- 同 key 并发 miss 只执行一次 loader；
- loader 异常不写入缓存；
- null key、value 和 loader result 被拒绝；
- `invalidate(key)` 和 `invalidateAll()`；
- disabled/bypass 模式每次执行 loader；
- 本地 afterCommit、rollback 和无事务失效语义。

### 18.2 Shared Caffeine 单元测试

- key codec 在 get、put 和 invalidate 中一致使用；
- key token 不泄露原始敏感 key；
- 单节点 Caffeine provider 只影响当前实例；
- generation 变化后旧 loader 结果不可见；
- 失效与 loader 返回的不同交错顺序均不会留下旧值；
- generation 元数据可以随缓存生命周期清理。

### 18.3 PostgreSQL coherence 集成测试

使用 Testcontainers PostgreSQL 模拟至少两个节点：

- 节点 A 写入事件后节点 B 精确失效同一 key；
- 失效一个 key 不清除同 region 其他 key；
- 业务事务 rollback 时不产生可见事件；
- 事件插入失败导致业务事务失败；
- 当前节点只在 commit 后立即失效；
- poller 按 id 顺序和 batch 分页消费；
- poller 暂时失败后可以从原 cursor 恢复；
- 节点启动从高水位开始且本地缓存为空；
- retention 造成 cursor gap 时清空全部 Shared region；
- 未注册 cache name 和 schema 不匹配不会错误失效其他 region；
- cleanup 不删除 retention 窗口内事件。

### 18.4 业务模块测试

AI governance：

- 多次解析同一用户策略只回源一次；
- 更新全局设置后当前节点立即失效；
- 其他节点在传播 SLA 内失效 singleton；
- 更新和删除用户覆盖后按 userId 精确失效；
- 数据库设置缺失时不会缓存 purpose 相关的错误 fallback；
- loader 失败保持原有治理失败语义。

认证：

- 活跃用户重复请求复用访问快照；
- 用户禁用、恢复、删除和角色变化后精确失效；
- 白名单设置和成员变化后跨节点失效；
- repository 故障时不错误放行；
- Session 吊销逻辑不依赖通用缓存。

### 18.5 架构约束测试

- 业务模块不得 import Caffeine 或 JDBC cache 实现；
- `cache` 模块不得依赖业务模块；
- controller 不直接依赖通用 `CacheRegion`；
- Session、run lock 和 permission coordinator 不改为 `CacheRegion` 实现；
- Shared region 必须配置 namespace、schema version 和 key codec；
- 生产多节点配置不得使用 `shared-provider=caffeine`。

## 十九、实施拆分

### 阶段 C1：基础缓存模块，已完成

- 建立 `backend/cache` Maven 模块；
- 定义三类 region、spec 和 factory；
- 实现 Caffeine region、bypass 模式、自动配置和基础指标；
- 完成容量、TTL、single-flight 和异常测试。

### 阶段 C2：本地事务失效，已完成

- 实现 `CacheInvalidationExecutor`；
- 覆盖 commit、rollback 和无事务测试；
- 固化本地写后失效规范。

### 阶段 C3：Shared 契约修订

- 增加 `SharedCacheKeyCodec`；
- 调整 `SharedCacheRegionFactory`；
- 增加 shared provider 枚举和显式装配；
- 固化 namespace、schema version 和 key token 校验；
- 保留单节点 Caffeine provider 用于测试。

### 阶段 C4：PostgreSQL coherence

- 新增失效事件 Flyway 迁移；
- 实现 JDBC event store 和 shared invalidation coordinator；
- 实现单 poller、cursor、batch、jitter、retention 和 cleanup；
- 实现 gap recovery 和 coherence 指标；
- 完成两节点 Testcontainers 集成测试。

### 阶段 C5：Generation fencing

- 将 Shared Caffeine 内部 value 包装为 generation-aware entry；
- 处理 invalidate 与在途 loader 的全部交错；
- 保证 generation 元数据有界；
- 增加并发压力测试。

### 阶段 C6：首批业务接入

- AI runtime settings 和 user policy 接入 Shared TTL；
- 认证 access snapshot、beta settings 和 membership 接入 Shared TTL；
- 管理员写路径建立事务并发布事件；
- 验证当前节点立即失效和跨节点传播延迟；
- 根据指标再确定用户偏好、画像和公共目录的后续接入顺序。

## 二十、验收标准

修订后的第一版缓存模块完成时应满足：

1. 继续只使用一个 `backend/cache` Maven 模块。
2. 业务模块不直接依赖 Caffeine、JdbcTemplate 或 PostgreSQL event store。
3. Local region 保持节点本地语义，不隐式依赖数据库。
4. Shared region 必须具备稳定 namespace、schema version 和 key codec。
5. `postgres-coherent-caffeine` 可以在两个应用节点间精确失效指定 key。
6. 共享事件与业务写入在同一事务提交或回滚。
7. 当前节点只在事务提交后失效，其他节点在目标传播时间内失效。
8. generation fencing 能阻止在途旧 loader 回填为可见值。
9. poller 故障时 TTL 仍能限制最大陈旧时间。
10. cursor gap 会触发全 Shared region 本地清理，而不是静默跳过。
11. 指标不包含用户、key token、email 等高基数或隐私 tag。
12. Session、锁、权限等待和一次性状态未被纳入通用缓存。
13. 生产多节点配置不会使用单 JVM `caffeine` shared provider。
14. 文档和启动日志清楚区分正常传播 SLA 与 TTL 降级上限。

## 二十一、风险与后续演进

### 21.1 主要风险

- 失效入口遗漏会使数据只能等待 TTL；
- poller 长时间失败会扩大配置和权限数据的陈旧窗口；
- event retention 过短会导致慢节点频繁全量清理；
- key codec 冲突会错误地让不同业务 key 共用缓存和失效事件；
- generation fencing 实现不严谨会保留旧值回填竞态；
- 节点数增加后，同一 key 失效可能触发每节点各一次数据库回源；
- 事件表写入量、poller 查询量和 cleanup 可能逐步增加 PostgreSQL 压力；
- cache disabled 与 coherence disabled 配置不当可能让写节点停止发布事件；
- 业务直接 SQL 更新但未补失效事件会造成 TTL 内陈旧。

### 21.2 Redis 引入门槛

多节点本身不再是立即引入 Redis 的充分条件。出现以下一个或多个信号时再评估 Redis：

- 共享失效事件写入和轮询对 PostgreSQL 形成可观测压力；
- 节点数量增加后，失效后的 N 节点独立回源明显放大数据库负载；
- 单节点缓存内存体量过大，希望共享 value 降低总内存；
- 需要跨节点 single-flight、原子计数或其他 Redis 原生能力；
- 需要比数据库轮询更低的传播延迟；
- 需要独立扩缩缓存层和数据库层。

未来 Redis provider 优先替换 `SharedCacheRegionFactory` 和 shared invalidation coordinator，保持业务缓存门面、namespace、schema version、key codec 和 `CacheRegion` 读取调用不变。

本地常驻和本地 TTL 缓存不因为引入 Redis 自动迁移。是否远程化继续由一致性、命中率、数据规模和延迟共同决定。

## 二十二、最终结论

第一版继续使用单个 `backend/cache` Maven 模块。`LocalBoundedCacheRegion` 和 `LocalTtlCacheRegion` 保持纯节点本地语义；`SharedTtlCacheRegion` 承担集群共享失效契约。

在当前规模下，Shared TTL 采用 PostgreSQL 失效事件协调各节点 Caffeine：业务数据与事件同事务提交，当前节点提交后立即失效，其他节点通过增量 poller 精确失效，generation fencing 阻止在途旧 loader 回填，TTL 只负责共享链路异常时的最终兜底。

该设计能够在不提前引入 Redis、不增加多个 Maven 模块的情况下支持中小规模分布式部署。未来达到明确容量或吞吐门槛后，再通过 provider 替换引入 Redis，而不重写业务缓存门面。

## 二十三、部署边界

Redis provider 真正落地后，本地开发直接安装运行 Redis；测试环境直接使用安装在测试机上的 Redis；生产环境 Redis 由外部基础设施提供，不纳入应用容器或生产 Compose。应用只通过环境变量或外部配置连接 Redis；缓存实例和 Redis Streams 队列实例按用途独立部署，不能共用淘汰域。完整部署约束见 `docs/deployment-topology-and-infrastructure-boundary.md`。
