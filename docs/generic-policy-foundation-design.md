# 通用策略底座研发设计

更新时间：2026-07-23

状态：设计已收敛，待实施

## 0. 已定决策

1. 通用策略是跨业务复用的配置存储与查询底座，不理解策略内容中的业务字段，但会按照业务注册的类型描述统一完成 `JsonNode -> Java` 反序列化。
2. `typeCode` 是前端与业务使用方共同约定的稳定字符串契约，不由管理员创建，也不建设数据库类型表；业务后端必须为每个可用类型注册一个 `GenericPolicyType<T>` Spring Bean。
3. 底座持久化原始 JSON，业务 Bean 声明目标 Java 类型；业务 JSON 的字段结构、版本兼容、编辑表单和 Java DTO 仍由使用方负责。
4. 第一版只支持两类主体：用户 `USER` 和用户组 `GROUP`，以及全部用户 `allSubject=true`；不支持 OU、动态人群规则、标签、用户组嵌套或表达式条件。
5. 策略适用范围和内容分别使用 `JSONB` 保存，不为用户和用户组主体拆分关系表。
6. 策略内容可以使用任意合法 JSON 结构，但必须能够转换为对应 `GenericPolicyType<T>` 声明的 Java 类型；底座不实施 JSON Schema 或业务语义校验。
7. 同一策略类型最多保留 100 条未删除策略，禁用策略也计入上限；该限制属于底座约束，不从请求中的 `limitCount` 获取。
8. 同一类型的全部未删除策略共享一套全局优先级，数字越小优先级越高；用户、用户组和全部用户不产生隐含的范围权重。
9. 一个用户命中同类型多条策略时，只返回优先级最高的一条。例如全部用户策略优先级为 1、指定用户策略优先级为 3 时，命中全部用户策略。
10. 策略支持启用、禁用和逻辑删除；禁用保留原优先级，重新启用后回到原位置。
11. 新建策略默认追加到该类型末尾；删除后压缩优先级；批量排序在单个数据库事务内原子完成。
12. 运行时策略缓存使用一个共享 TTL region，以 `typeCode` 为 key，value 为该类型已启用、已完成主体编译和 Java 内容转换、按优先级排序的不可变快照。
13. 管理查询直接读取 PostgreSQL，不复用只包含已启用策略的运行时缓存。
14. 用户关系缓存由 `identity` 模块拥有，以 `userId` 为 key；通用策略模块只通过 `getRelations(userId)` 获取当前有效用户组，不感知用户组启停与删除细节。
15. 用户入组、出组和修改成员有效期时精确失效该用户；用户组启用、禁用和删除时查询受影响成员并逐用户发布共享失效事件。
16. 成员自然到期由 `getRelations(userId)` 按当前时间过滤，不等待固定缓存 TTL 到期。
17. 策略缓存和用户关系缓存均以 PostgreSQL 为权威来源，正常变更依靠共享缓存失效传播，TTL 只作为失效链路异常时的陈旧上限。
18. 成功查询但没有策略命中时返回 `Optional.empty()`；缓存、数据库或内容编译失败必须抛出明确异常，不能伪装成未命中。
19. 同一类型任意一条已启用策略编译失败时，整个 `typeCode` 的缓存加载失败，禁止跳过坏策略继续命中低优先级策略。
20. 缓存编译失败必须输出不含完整 JSON 的 `ERROR` 日志、异常堆栈和 Micrometer 失败指标，供日常巡检定位 `typeCode`、策略 ID、版本和目标 Java 类型。
21. 第一版不把现有 AI 动态策略强制迁移到通用策略底座；待底座稳定后，由 AI 治理单独评估接入和迁移。

## 1. 背景

项目已经具备用户、用户组、多对多成员关系、成员有效期和共享缓存基础设施。后续不同业务会反复出现类似配置需求：

- 为全部用户配置一个默认行为；
- 为指定用户或用户组配置特殊行为；
- 同一种业务策略允许存在多条配置；
- 多条配置同时命中时按照人工维护的优先级选择一条；
- 策略内容由业务自行定义，底座不应跟随每种业务结构频繁修改。

如果每个业务分别建设策略表、优先级算法、用户组匹配和缓存失效链路，会形成重复实现，也容易产生不同的命中语义。通用策略底座负责收敛以下公共能力：

```text
策略存储
  + 状态管理
  + 适用范围
  + 优先级排序
  + 用户关系匹配
  + 共享缓存
  = 单条有效策略查询
```

底座不拥有策略内容本身的业务语义。一个 `typeCode` 对应什么场景、`content` 包含哪些字段、字段如何演进，均由前端和业务使用方通过代码常量、类型定义和消费逻辑共同约定。

## 2. 目标与非目标

### 2.1 目标

- 提供单表、低复杂度的通用策略持久化模型。
- 支持全部用户、指定用户和指定用户组三种范围来源，并允许用户和用户组混合选择。
- 为同一类型维护稳定、唯一、可原子调整的全局优先级。
- 为指定用户和策略类型返回至多一条命中策略。
- 将策略范围编译为内存集合，使运行时查询主要依赖缓存而不是数据库关联查询。
- 通过业务注册的 Spring Bean 把 `typeCode` 与目标 Java 类型绑定，使运行时查询直接返回强类型对象。
- 在管理写入阶段预执行内容转换，避免正常写路径产生会污染运行时缓存的 JSON。
- 复用现有共享 TTL 缓存和事务内失效事件，支持多节点最终一致。
- 在 `identity` 内提供可复用的用户有效关系缓存，由用户组写路径负责精确失效。
- 明确未命中、加载失败、主体失效和并发更新的行为。
- 提供管理 API 和后端运行时查询服务，使不同业务可以复用同一底座。

### 2.2 非目标

- 不定义任何具体业务策略的 JSON 字段。
- 不在底座定义 JSON Schema、业务 DTO、业务枚举、业务默认值或业务语义校验；底座只调用业务注册的类型描述完成反序列化。
- 不建设可由管理员维护的策略类型目录。
- 不要求所有业务使用统一的策略内容编辑器；前端可以按 `typeCode` 提供专用表单。
- 不合并多条策略，也不支持继承、覆盖字段、权重计算或多策略叠加。
- 不支持用户标签、动态条件、时间段规则、设备条件、地区条件或表达式语言。
- 不支持用户组嵌套、父子组和 OU。
- 不在第一版建设策略发布审批、定时生效、灰度比例、历史版本回滚或草稿系统。
- 不将缓存作为权威数据源，也不追求强一致或跨节点线性一致。
- 不在第一版迁移现有 `ai_runtime_settings` 和 `ai_user_policy`。

## 3. 责任边界

### 3.1 通用策略底座负责

| 能力 | 底座职责 |
|---|---|
| 策略类型 | 收集业务 Spring Bean，按 `typeCode` 隔离、计数、排序和查询 |
| 策略内容 | 原样保存和返回 JSON，并按注册类型统一反序列化 |
| 适用范围 | 校验并解析 `allSubject`、`USER`、`GROUP` |
| 优先级 | 保证同类型未删除策略唯一、有序和原子重排 |
| 状态 | 支持 `ENABLED`、`DISABLED`、`DELETED` |
| 命中 | 过滤范围后按优先级返回第一条 |
| 缓存 | 编译主体集合和强类型内容、调用用户有效关系、处理策略共享失效 |
| 错误 | 区分未命中、非法基础字段、类型转换失败、并发冲突和加载失败 |

### 3.2 前端和业务使用方负责

| 能力 | 使用方职责 |
|---|---|
| `typeCode` | 注册唯一的 `GenericPolicyType<T>` Bean，并使用代码常量保持前后端一致 |
| JSON 结构 | 定义 TypeScript 类型、Java DTO、Jackson 注解和兼容策略 |
| 内容校验 | 在专用前端表单和业务写入入口校验字段 |
| 内容兼容 | 处理新增字段、默认值、旧数据和结构版本演进 |
| 默认行为 | 未命中时决定使用静态默认值、跳过功能或拒绝操作 |
| 加载失败 | 决定失败开放、失败关闭、重试或业务降级 |
| 业务安全 | 禁止把密钥、访问令牌、密码和不必要的隐私数据写入 `content` |

底座不得根据具体 `typeCode` 使用 `switch` 或条件分支解释 `content`，只能通过通用注册表调用业务提供的 `GenericPolicyType<T>`。业务模块也不得绕过底座自行实现另一套范围和优先级算法。

## 4. 总体架构

### 4.1 Maven 模块

新增独立模块：

```text
backend/policy
├── src/main/java/org/congcong/algomentor/policy
│   ├── model
│   ├── repository
│   ├── repository/mybatis
│   ├── service
│   ├── type
│   ├── cache
│   ├── controller/admin
│   ├── controller/runtime
│   ├── metrics
│   └── autoconfigure
├── src/main/resources
│   ├── mapper/policy
│   ├── db/migration/policy
│   └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── src/test/java/org/congcong/algomentor/policy
```

`identity` 模块增加用户关系缓存和查询门面，但不依赖 `policy`：

```text
backend/identity/src/main/java/org/congcong/algomentor/identity/group/relation
  UserRelationProvider
  UserRelations
  CachedUserRelations
  UserRelationCache
```

建议依赖方向：

```text
common       cache
   ^           ^
   |           |
identity ------+
   ^           ^
   |           |
policy --------+
   ^
   |
具体业务模块
```

`policy` 依赖 `identity` 暴露的用户关系查询门面；`identity` 只维护自己的用户和用户组事实及其缓存，不知道哪些策略正在消费这些关系。

### 4.2 运行时调用链

```text
业务使用方
  -> GenericPolicyQueryService.resolve(policyType, userId)
      -> 校验 policyType 是已注册 Bean
      -> PolicySetCache.get(policyType.typeCode())
      -> UserRelationProvider.getRelations(userId)
      -> 按 priority 遍历策略
      -> 第一条范围匹配即返回
```

一次解析只查询一个 `typeCode`。第一版每种类型最多 100 条策略，运行时采用内存顺序遍历，不需要复杂索引、规则引擎或数据库关联查询。

## 5. 公共契约

### 5.1 状态

```java
public enum GenericPolicyStatus {
  ENABLED,
  DISABLED,
  DELETED
}
```

- `ENABLED`：进入运行时缓存，可以参与命中。
- `DISABLED`：保留配置和优先级，不参与运行时命中。
- `DELETED`：逻辑删除，不出现在普通管理列表和运行时缓存中，不允许恢复。

### 5.2 主体类型

```java
public enum PolicySubjectType {
  USER,
  GROUP
}
```

主体类型、状态值、API 路径、JSON 字段名和缓存名称属于跨模块公共契约，应分别收敛到枚举或常量类。

### 5.3 适用范围 JSON

持久化和 API 使用统一结构：

```json
{
  "allSubject": false,
  "subjects": [
    {
      "type": "USER",
      "id": 42
    },
    {
      "type": "GROUP",
      "id": 10
    }
  ]
}
```

规则：

- `allSubject=true` 时 `subjects` 必须为空。
- `allSubject=false` 时 `subjects` 至少包含一项。
- 同一策略中允许同时选择用户和用户组。
- 相同 `type + id` 重复出现时在写入前去重，保持首次出现顺序。
- 用户和用户组 ID 必须为正整数。
- `USER` 主体必须存在且状态不是 `DELETED`。
- `GROUP` 主体必须存在且状态不是 `DELETED`；允许选择当前为 `DISABLED` 的组，该策略会在用户组恢复为 `ACTIVE` 后参与命中。
- 第一版不持久化主体的名称、编码、父级名称或远端删除标记。

管理详情如果需要展示名称，由 API 按主体 ID 批量查询 `identity` 并返回派生的展示摘要；展示摘要不写回 `subject_range`，不参与命中。

### 5.4 策略内容 JSON

`content` 是底座不透明数据，可以是对象、数组、字符串、数字、布尔值或 JSON `null`。例如：

```json
[
  {
    "windowSeconds": 60,
    "requestLimit": 100
  }
]
```

底座只保证：

- HTTP 请求体能够被 Jackson 解析为合法 `JsonNode`；
- 数据以 PostgreSQL `JSONB` 保存；
- `typeCode` 已注册对应的 `GenericPolicyType<T>`；
- 原始 JSON 能够通过该注册类型转换为目标 Java 对象；
- 单条内容不超过底座配置的字节上限；
- 写入、缓存和返回过程中不执行字符串拼接或二次 JSON 编码；
- 日志、指标和管理员审计不记录完整内容。

反序列化成功只表示 JSON 与 Java 结构兼容，不代表业务值合法。字段必填关系、数值范围、跨字段约束和业务默认值仍由前端和业务使用方负责。

### 5.5 领域对象

```java
public record GenericPolicy(
    long id,
    String typeCode,
    String name,
    String description,
    GenericPolicyStatus status,
    int priority,
    PolicySubjectRange subjectRange,
    JsonNode content,
    long version,
    long createdBy,
    Instant createdAt,
    long updatedBy,
    Instant updatedAt
) {}
```

持久化领域对象继续保存原始 `JsonNode`。运行时缓存使用泛型编译对象，同时保留原始内容和转换结果：

```java
public record CompiledPolicy<T>(
    long id,
    String typeCode,
    String name,
    int priority,
    boolean allSubject,
    Set<Long> userIds,
    Set<Long> groupIds,
    JsonNode rawContent,
    T content,
    long version
) {}
```

`CompiledPolicy<T>` 必须不可变，`userIds/groupIds` 使用不可变集合。缓存内的 `rawContent` 不得暴露给调用方修改；管理 HTTP API 返回数据库原始 JSON，Java 运行时查询返回 `T`。

## 6. 数据模型

### 6.1 单表设计

第一版使用一张表，不拆用户和用户组关系表：

```sql
CREATE TABLE generic_policy (
  id BIGSERIAL PRIMARY KEY,
  type_code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
  priority INTEGER NOT NULL,
  subject_range JSONB NOT NULL,
  content JSONB NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL REFERENCES auth_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT NOT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_by BIGINT NULL REFERENCES auth_users(id),
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT ck_generic_policy_type_code
    CHECK (type_code ~ '^[a-z][a-z0-9_.-]{0,63}$'),
  CONSTRAINT ck_generic_policy_name
    CHECK (length(btrim(name)) > 0),
  CONSTRAINT ck_generic_policy_status
    CHECK (status IN ('ENABLED', 'DISABLED', 'DELETED')),
  CONSTRAINT ck_generic_policy_priority
    CHECK (priority > 0),
  CONSTRAINT ck_generic_policy_subject_range
    CHECK (jsonb_typeof(subject_range) = 'object'),
  CONSTRAINT ck_generic_policy_version
    CHECK (version > 0),
  CONSTRAINT ck_generic_policy_deleted_fields
    CHECK (
      (status = 'DELETED' AND deleted_by IS NOT NULL AND deleted_at IS NOT NULL)
      OR (status != 'DELETED' AND deleted_by IS NULL AND deleted_at IS NULL)
    )
);

CREATE UNIQUE INDEX uk_generic_policy_type_priority_live
  ON generic_policy(type_code, priority)
  WHERE status != 'DELETED';

CREATE INDEX idx_generic_policy_runtime
  ON generic_policy(type_code, priority, id)
  WHERE status = 'ENABLED';

CREATE INDEX idx_generic_policy_admin_list
  ON generic_policy(type_code, status, priority, id)
  WHERE status != 'DELETED';
```

Flyway 版本号在实施前扫描全部模块后确定，迁移放在：

```text
backend/policy/src/main/resources/db/migration/policy
```

### 6.2 为什么不拆主体关系表

当前约束下，单表 JSONB 更符合实际规模：

- 每种类型不超过 100 条策略；
- 单条策略的主体数量通常较少；
- 运行时全量加载并编译到内存，不依赖 SQL 反向查询主体；
- 主体名称和状态由 `identity` 负责，策略只保存稳定 ID；
- 管理写入频率低，不需要针对主体关系做高频增删。

接受的代价：

- 数据库不能通过外键直接保证 JSON 内主体存在；
- 按主体反向搜索策略需要 JSONB 查询或内存扫描；
- 用户或用户组删除后，策略内可能保留失效 ID；
- 主体完整性主要由 Service 写入校验和管理详情提示保证。

出现以下任一情况时，再评估拆分关系表：

- 单条策略主体达到数千或更高数量；
- 需要高频查询“某用户或用户组被哪些策略引用”；
- 需要数据库级联删除或严格外键完整性；
- 需要独立审计每一次主体关联变更；
- JSONB 更新产生明显写放大或锁竞争。

### 6.3 基础限制

第一版底座约束建议集中在 `GenericPolicyConstraints`：

```text
MAX_POLICIES_PER_TYPE = 100
MAX_NAME_LENGTH = 120
MAX_DESCRIPTION_LENGTH = 500
MAX_TYPE_CODE_LENGTH = 64
MAX_SUBJECTS_PER_POLICY = 1000
MAX_CONTENT_BYTES = 262144
```

这些限制只保护底座资源使用，不表达业务内容语义。`MAX_CONTENT_BYTES` 按请求 JSON 的 UTF-8 序列化字节数计算，不能用 Java 字符数量代替。

## 7. 类型注册契约

### 7.1 Spring Bean 描述符

`typeCode` 不对应数据库类型表，也不由管理员创建，但每个可写入和可查询的类型必须由业务模块注册一个 Spring Bean：

```java
public final class GenericPolicyType<T> {

  private final String typeCode;
  private final Type contentType;

  public String typeCode() {
    return typeCode;
  }

  public JavaType javaType(ObjectMapper objectMapper) {
    return objectMapper.getTypeFactory().constructType(contentType);
  }

  public T deserialize(ObjectMapper objectMapper, JsonNode content) {
    return objectMapper.convertValue(content, javaType(objectMapper));
  }
}
```

底座应提供 `Class<T>` 和 `TypeReference<T>` 两种工厂方法，并统一保存其 `java.lang.reflect.Type`。`Class<T>` 适合普通根对象，`TypeReference<T>` 用于 `List<Rule>`、`Map<String, Rule>` 等泛型结构；`javaType(ObjectMapper)` 同时用于转换和错误日志中的目标类型描述。

普通对象注册示例：

```java
@Bean
GenericPolicyType<TunnelThrottlingPolicy> tunnelThrottlingPolicyType() {
  return GenericPolicyType.of(
      "tunnel_throttling_group",
      TunnelThrottlingPolicy.class);
}
```

泛型结构注册示例：

```java
@Bean
GenericPolicyType<List<TunnelThrottlingRule>> tunnelThrottlingPolicyType() {
  return GenericPolicyType.of(
      "tunnel_throttling_group",
      new TypeReference<List<TunnelThrottlingRule>>() {});
}
```

业务上优先使用稳定的根对象而不是直接使用数组，便于后续增加 `schemaVersion` 或公共字段：

```java
public record TunnelThrottlingPolicy(
    int schemaVersion,
    List<TunnelThrottlingRule> policies
) {}
```

### 7.2 注册中心

底座自动配置通过 `ObjectProvider<GenericPolicyType<?>>` 收集 Bean，构建只读映射：

```text
Map<typeCode, GenericPolicyType<?>>
```

注册规则：

- `typeCode` 执行 `trim + lowercase(Locale.ROOT)` 和基础格式校验；
- 相同 `typeCode` 注册多个 Bean 时应用启动失败；
- 管理创建、编辑和启用策略时，未注册的 `typeCode` 返回稳定错误；
- 缓存编译时找不到注册 Bean，按整个类型编译失败处理；
- 底座禁止启用 Jackson default typing，JSON 内容不能决定要实例化的 Java 类；
- 底座只调用描述符，不根据具体类型编写业务分支。

注册中心是运行时 Java 类型适配表，不是管理员可配置的业务类型目录。

### 7.3 前后端契约

前端仍需维护对应常量和 TypeScript 类型：

```typescript
export const POLICY_TYPE_CODES = {
  tunnelThrottling: 'tunnel_throttling_group',
} as const;

export interface TunnelThrottlingPolicy {
  schemaVersion: number;
  policies: TunnelThrottlingRule[];
}
```

管理前端不提供自由创建策略类型的入口。具体业务页面以固定 `typeCode` 调用通用策略 API，后端 Bean 是服务端认可该类型和声明 Java 目标类型的唯一入口。

### 7.4 兼容性责任

业务修改 Java DTO 时必须考虑数据库中已有 JSON 和滚动发布期间的新旧节点：

- 新 DTO 应优先兼容旧字段和缺省值；
- 删除或重命名字段前先完成数据迁移或兼容读取；
- 新前端写入的 JSON 必须能被滚动发布期间的旧节点读取；
- 不兼容升级会导致对应 `typeCode` 整体编译失败，而不是静默跳过坏策略。

## 8. 命中语义

### 8.1 候选条件

一条策略成为候选必须同时满足：

```text
policy.typeCode == requestedTypeCode
AND policy.status == ENABLED
AND (
  policy.allSubject
  OR policy.userIds contains userId
  OR policy.groupIds intersects user.activeGroupIds
)
```

### 8.2 优先级

候选策略按照以下顺序处理：

```text
priority ASC, id ASC
```

数据库保证同类型未删除策略的 `priority` 唯一，正常情况下不会使用 `id` 决胜；`id` 只作为防御性确定顺序。

范围精确度不参与排序。示例：

| 策略 | 范围 | priority | 用户是否命中范围 | 最终结果 |
|---|---|---:|---|---|
| A | 全部用户 | 1 | 是 | 命中 |
| B | 用户组 | 2 | 是 | 不再继续判断 |
| C | 指定用户 | 3 | 是 | 不再继续判断 |

### 8.3 未命中

成功加载策略和用户关系后没有候选项时：

```java
Optional<ResolvedPolicy<T>> result = Optional.empty();
```

底座不自动创建或要求“全部用户”的默认策略。具体业务自行决定未命中后的默认行为。

### 8.4 解析结果

```java
public record ResolvedPolicy<T>(
    long policyId,
    String typeCode,
    String name,
    int priority,
    T content,
    PolicyMatchSource matchSource,
    Long matchedSubjectId,
    long version
) {}
```

`matchSource` 只用于诊断和管理展示，可以是 `ALL`、`USER` 或 `GROUP`，不影响优先级。若同一条策略同时通过用户和用户组命中，诊断来源按 `USER -> GROUP -> ALL` 选择最具体的一项，但策略选择结果不变。

## 9. 策略缓存

### 9.1 缓存模型

通用策略模块定义一个业务缓存门面：

```java
SharedTtlCacheRegion<String, CompiledPolicySet> policySets;
```

```java
public record CompiledPolicySet(
    String typeCode,
    GenericPolicyType<?> policyType,
    List<CompiledPolicy<?>> sortedPolicies
) {}
```

缓存 key 为规范化后的 `typeCode`，value 只包含该类型当前 `ENABLED` 的策略，并在加载时完成：

- `subject_range` 解析；
- 用户 ID 和用户组 ID 去重；
- 通过注册的 `GenericPolicyType<T>` 把 `rawContent` 转换为 `T`；
- 不可变集合构造；
- `priority ASC, id ASC` 排序；
- 原始 JSON 和强类型内容只读快照构造。

运行时请求不重复解析 `subject_range` 或转换 `content`，也不访问策略数据库。

内部缓存为了容纳不同业务类型使用非泛型 `CompiledPolicySet` 包装，并把唯一一次受控的泛型转换集中在注册中心和查询服务内部。对外查询仍通过调用方传入的 `GenericPolicyType<T>` 返回 `ResolvedPolicy<T>`。

### 9.2 编译失败语义

同一 `typeCode` 的策略集合必须整体编译。只要任意一条已启用策略出现以下问题，整个 key 的 loader 失败：

- 找不到对应 `GenericPolicyType<?>`；
- `subject_range` 无法解析；
- `content` 无法反序列化为目标 Java 类型；
- 编译结果违反底座不可变或排序约束。

禁止跳过坏策略继续缓存剩余数据。否则损坏的高优先级策略可能被静默绕过，使用户命中低优先级策略。

编译失败时：

```text
不写入部分缓存值
-> 抛出 PolicyCompilationException
-> 查询层包装为 PolicyResolutionException
-> 输出 ERROR 日志和异常堆栈
-> 记录编译失败指标
```

日志至少包含：

```text
event=generic_policy_compile_failed
typeCode
policyId
policyVersion
targetJavaType
errorCode
exceptionClass
```

日志不得包含完整 `content`。为了避免坏数据被高频请求反复触发后造成日志洪泛，相同 `typeCode + policyId + policyVersion` 的重复错误可以限频，但首次错误和指标计数不得丢失。

### 9.3 单个 catalog 与按类型 key

第一版选择“一个缓存 region、按类型分 key”，而不是为每种业务类型创建不同的 cache bean：

```text
policy-set-cache[tunnel_throttling_group]
policy-set-cache[another_business_type]
```

该方案同时满足：

- 所有热点策略保留在 JVM 内存；
- 单个类型变更只失效一个 key；
- 业务类型数量增加时不需要增加 cache bean；
- 不同类型之间不存在一致性事务要求，混合缓存版本不影响单次解析。

如果部署阶段希望启动后立即全量预热，可以查询已启用策略的 distinct `type_code` 并逐个加载。单个类型预热失败不阻止管理 API 启动，但必须记录 `ERROR` 日志、失败指标，并让该类型的业务查询持续返回明确错误，直到数据或业务类型定义被修复并成功重新加载。

### 9.4 建议参数

```text
region name: generic-policy-set
namespace: generic-policy-set
schema version: 1
maximum size: 100
ttl: 60m
```

TTL 较长是因为策略配置频率低，正常生效依靠共享失效事件；实际值通过 `algo-mentor.policy.cache.*` 配置，并允许部署环境调整。

### 9.5 写后失效

以下事务成功提交后失效 `policySets[typeCode]`：

- 创建策略；
- 修改名称、说明、状态、适用范围或内容；
- 调整优先级；
- 删除策略。

使用现有 `SharedCacheInvalidationCoordinator` 在数据库事务内追加失效事件，当前节点提交后立即失效，其他节点通过 PostgreSQL poller 感知。

## 10. 用户关系缓存

### 10.1 模块归属

用户关系是身份事实，缓存必须放在 `identity`，不能放在 `policy`。否则用户组写路径需要反向依赖策略模块才能失效缓存。

对外门面：

```java
public interface UserRelationProvider {
  UserRelations getRelations(long userId);
}
```

```java
public record UserRelations(
    long userId,
    Set<Long> activeGroupIds
) {}
```

策略模块只看到当前有效的 `activeGroupIds`，不理解用户组状态和成员有效期。

### 10.2 内部缓存值

Identity 内部缓存不能只保存加载时计算好的 `Set<Long>`，否则成员自然到期后会继续生效到固定 TTL 结束。建议缓存：

```java
record CachedUserRelations(
    long userId,
    List<CachedGroupMembership> memberships
) {}

record CachedGroupMembership(
    long groupId,
    Instant joinedAt,
    Instant expiresAt
) {}
```

加载 SQL 只返回当前 `ACTIVE` 用户组中的未删除用户成员关系。`getRelations(userId)` 每次调用仍按当前 `Clock` 过滤：

```text
joinedAt <= now
AND (expiresAt == null OR expiresAt > now)
```

这样用户组状态被内聚在关系加载和失效路径中，成员自然到期则由读取时判断保证准确。

### 10.3 缓存模型

```java
SharedTtlCacheRegion<Long, CachedUserRelations> userRelations;
```

建议参数：

```text
region name: identity-user-relations
namespace: identity-user-relations
schema version: 1
maximum size: 1000
ttl: 30m
```

用户不存在或已删除时返回明确错误；是否允许 `DISABLED` 用户解析策略由调用业务的用户状态门禁决定，关系缓存不承担认证职责。

### 10.4 失效矩阵

| 身份变更 | 受影响用户 | 缓存动作 |
|---|---|---|
| 用户加入组 | 该用户 | 失效 `userRelations[userId]` |
| 用户退出组 | 该用户 | 失效 `userRelations[userId]` |
| 修改成员到期时间 | 该用户 | 失效 `userRelations[userId]` |
| 用户组启用 | 该组当前未过期成员 | 逐用户失效 |
| 用户组禁用 | 该组当前未过期成员 | 逐用户失效 |
| 用户组删除 | 删除关系前取得的当前未过期成员 | 逐用户失效 |
| 成员自然到期 | 无需主动事件 | `getRelations` 按当前时间过滤 |
| 用户组修改名称或说明 | 无 | 不影响命中 |

用户组启用、禁用和删除的事务顺序：

```text
锁定用户组
-> 查询该组当前未过期成员 userIds
-> 修改组状态或删除成员关系
-> 为每个 userId 追加共享缓存失效事件
-> 提交事务
```

用户组删除必须在删除成员关系前取得 userIds。任一失效事件写入失败应使身份写事务回滚，避免数据库状态已经变化但集群缓存无法及时收敛。

当前项目规模下逐用户失效足够直接。单组成员达到数千并造成明显事件放大时，再引入关系全局 revision、按组 generation 或批量失效协议。

## 11. 写入和排序语义

### 11.1 类型级并发控制

创建、删除和排序都会影响同一 `typeCode` 的条数或优先级，应在事务内取得类型级锁。仅使用 `SELECT ... FOR UPDATE` 无法锁住尚无策略的新类型，因此第一版建议使用 PostgreSQL transaction-scoped advisory lock，并由 repository 封装稳定 key 计算。

所有影响优先级的操作顺序：

```text
规范化 typeCode
-> 获取 typeCode 事务锁
-> 重新读取未删除策略
-> 校验条数、版本和排序请求
-> 执行写入
-> 追加共享缓存失效事件
-> 提交
```

### 11.2 创建

- 未删除策略达到 100 条时返回稳定冲突错误。
- 新策略的 `priority = 当前最大优先级 + 1`。
- 创建请求可以指定 `ENABLED` 或 `DISABLED`；未传状态时默认 `DISABLED`。
- `typeCode` 必须已经注册 `GenericPolicyType<T>` Spring Bean。
- 写入前校验主体范围、主体存在性和基础字段长度。
- `content` 先校验合法 JSON 和字节上限，再通过注册类型执行一次反序列化；转换失败时拒绝写入。
- 写入预转换只验证 Java 结构兼容性，不执行底座业务语义校验。

### 11.3 编辑

- 允许修改名称、说明、状态、适用范围和内容。
- 不允许修改已有策略的 `typeCode`；跨类型迁移应新建策略后删除旧策略。
- 普通编辑不改变优先级。
- 修改 `content` 时必须重新执行注册类型反序列化，失败则整个事务回滚。
- 请求携带 `version`，SQL 使用 `WHERE id = ? AND version = ?` 并在成功后 `version = version + 1`。
- 版本不一致返回 `409 POLICY_VERSION_CONFLICT`，防止管理员覆盖其他人的更新。

### 11.4 启用和禁用

- `ENABLED <-> DISABLED` 不改变优先级。
- 将历史禁用策略重新启用前，必须使用当前注册类型重新转换其 `content`，避免 Java DTO 已演进但旧 JSON 不兼容。
- 禁用后从下一次成功加载的运行时快照中移除。
- 重新启用后按原优先级参与命中。
- 重复提交相同状态按幂等成功处理，但仍应校验请求版本。

### 11.5 删除

- 删除采用不可恢复的逻辑删除，可以从 `ENABLED` 或 `DISABLED` 状态直接执行。
- 删除记录 `deleted_by/deleted_at` 并将状态更新为 `DELETED`。
- 删除后将该类型剩余策略重新编号为连续的 `1..N`。
- 重复删除返回幂等结果 `deleted=false`。
- 删除内容继续保留在数据库中用于低频排障，不进入普通列表和运行时缓存；后续如有数据保留要求，再设计清理周期。

### 11.6 重排

不建议前端逐条 PATCH priority。提供完整顺序接口：

```http
PUT /api/admin/policy-types/{typeCode}/order
```

```json
{
  "policyIds": [12, 7, 21],
  "versions": {
    "12": 3,
    "7": 5,
    "21": 2
  }
}
```

请求必须恰好包含该类型全部未删除策略，包括禁用策略。事务内先把原优先级整体移动到不会冲突的临时区间，再按请求顺序写入 `1..N`，避免部分唯一索引的逐行更新冲突。

任一 ID 不属于该类型、缺失、重复、已删除或版本冲突时整批回滚。

## 12. 管理 API

基础路径：

```text
/api/admin/policies
```

建议接口：

```text
GET    /api/admin/policies
POST   /api/admin/policies
GET    /api/admin/policies/{policyId}
PATCH  /api/admin/policies/{policyId}
DELETE /api/admin/policies/{policyId}
PUT    /api/admin/policy-types/{typeCode}/order
```

列表查询参数：

```text
typeCode    必填，业务页面传固定值
status      可选 ENABLED/DISABLED
keyword     按 name/description 模糊搜索
page
pageSize
```

创建请求示例：

```json
{
  "typeCode": "tunnel_throttling_group",
  "name": "默认限流策略",
  "description": "全部用户默认配置",
  "status": "ENABLED",
  "subjectRange": {
    "allSubject": true,
    "subjects": []
  },
  "content": [
    {
      "windowSeconds": 60,
      "requestLimit": 100
    }
  ]
}
```

响应中的 `content` 必须作为原始 JSON 节点输出，不能变成转义后的 JSON 字符串。

创建、编辑和启用接口在落库前调用已注册的 `GenericPolicyType<T>` 做结构转换校验，但响应仍返回数据库中的原始 JSON，不返回业务 Java 类名或底座内部编译对象。

策略管理属于高影响配置能力，建议新增 `policy:manage` 权限，不复用 `user:manage`。管理员审计记录策略 ID、`typeCode`、状态变化、主体数量和操作结果，不记录完整 `content` 或主体清单。

## 13. 运行时查询接口

### 13.1 Java 服务

主要消费方式为模块内服务：

```java
public interface GenericPolicyQueryService {
  <T> Optional<ResolvedPolicy<T>> resolve(
      GenericPolicyType<T> policyType,
      long userId);
}
```

调用方注入自己注册的强类型 Bean：

```java
Optional<ResolvedPolicy<TunnelThrottlingPolicy>> resolved =
    policyQueryService.resolve(
        tunnelThrottlingPolicyType,
        userId);

TunnelThrottlingPolicy content = resolved
    .map(ResolvedPolicy::content)
    .orElseGet(TunnelThrottlingPolicy::defaults);
```

不提供 `<T> resolve(String typeCode, long userId)` 形式的伪泛型接口，因为调用方可以为同一个字符串声明任意 `T`。查询必须携带已经注册的 `GenericPolicyType<T>`，由底座校验它与注册中心中的 Bean 一致后再返回泛型结果。

### 13.2 当前用户 HTTP 查询

需要由前端直接读取有效内容时，可以提供：

```text
GET /api/policies/{typeCode}/effective
```

该接口只允许查询当前认证用户，不接收前端声明的 `userId`。HTTP 接口继续返回原始 JSON，而不是暴露 Java DTO 类型。命中时返回：

```json
{
  "matched": true,
  "policyId": 12,
  "typeCode": "tunnel_throttling_group",
  "priority": 1,
  "content": []
}
```

未命中时返回成功响应：

```json
{
  "matched": false,
  "typeCode": "tunnel_throttling_group"
}
```

是否暴露该 HTTP 接口由具体业务接入阶段决定；底座 Java 服务是第一优先级能力。

## 14. 失败语义

### 14.1 未命中与加载失败必须分离

```text
成功加载 + 没有匹配策略 -> Optional.empty()
策略缓存加载失败        -> PolicyResolutionException
用户关系缓存加载失败    -> PolicyResolutionException
未注册 typeCode          -> PolicyTypeNotRegisteredException
content 编译失败         -> PolicyCompilationException -> PolicyResolutionException
```

底座不能在数据库异常、缓存 loader 异常、内容编译失败或主体关系加载失败时返回未命中，否则可能绕过限流、权限或其他重要业务策略。

### 14.2 建议错误码

```text
POLICY_NOT_FOUND
POLICY_INVALID_REQUEST
POLICY_INVALID_TYPE_CODE
POLICY_TYPE_NOT_REGISTERED
POLICY_INVALID_SUBJECT_RANGE
POLICY_SUBJECT_NOT_FOUND
POLICY_LIMIT_EXCEEDED
POLICY_CONTENT_TOO_LARGE
POLICY_CONTENT_COMPILE_FAILED
POLICY_VERSION_CONFLICT
POLICY_ORDER_CONFLICT
POLICY_RESOLUTION_FAILED
```

管理 API 按现有统一错误响应格式映射 `400/404/409/500`。运行时 Java API 通过强类型异常保留失败原因，不把底层 SQL 或缓存实现细节泄露给调用方。

## 15. 可观测性与审计

建议指标：

```text
generic.policy.resolve{typeCode,result=hit|miss|failure}
generic.policy.resolve.duration{typeCode}
generic.policy.compile{typeCode,result=success|failure}
generic.policy.cache.load{typeCode,result}
generic.policy.cache.invalidate{typeCode,result}
generic.policy.admin.write{operation,result}
identity.user.relations.cache.load{result}
identity.user.relations.cache.invalidate{reason,result}
identity.user.relations.group_fanout{operation}
```

`typeCode` 来自业务代码契约且数量有限，可以作为指标 tag；`policyId`、`userId`、`groupId` 和策略内容不得作为高基数 tag。

日志允许记录：

- `typeCode`；
- `policyId`；
- `policyVersion`；
- 注册的目标 Java 类型；
- 操作类型和错误码；
- 主体数量和失效用户数量；
- 缓存加载耗时。

日志禁止记录：

- 完整 `content`；
- 完整用户 ID 列表或用户组成员清单；
- API key、访问令牌、密码、Authorization 和其他敏感数据。

缓存编译失败必须使用 `ERROR` 级别记录异常堆栈，日志事件名固定为 `generic_policy_compile_failed`。写接口收到无法转换的用户输入时按可预期请求失败处理并记录管理员审计；不得为每次 `400` 打印包含完整堆栈的高噪声错误日志。

## 16. 安全约束

- 管理写接口要求管理员身份和 `policy:manage` 权限。
- 当前用户有效策略接口只能从认证上下文取得用户 ID。
- 底座不得提供普通用户按任意 `userId` 查询有效策略的接口。
- `content` 不得用于保存密钥、数据库密码、JWT、第三方 token 或需要专用加密存储的值。
- 管理审计不记录完整 JSON，避免不透明内容意外包含隐私数据时扩大泄露范围。
- 主体详情批量查询只返回管理页面需要的低敏字段。
- JSON 大小、主体数量和每类型条数必须在服务端校验，不能信任前端限制。
- Jackson 必须关闭不受信任的多态 default typing，只允许转换为业务 Bean 预先声明的目标类型。

## 17. 测试策略

### 17.1 领域与服务测试

至少覆盖：

- 全部用户策略命中；
- 指定用户命中；
- 任一用户组命中；
- 用户同时命中多条时只返回最小 priority；
- 全部用户策略优先级高于指定用户时仍命中全部用户策略；
- 禁用和删除策略不命中；
- 无策略时返回 `Optional.empty()`；
- 加载失败不会伪装成未命中；
- 普通对象和 `TypeReference<List<T>>` 都能注册并返回强类型结果；
- 重复 `typeCode` Bean 使应用启动失败；
- 未注册 `typeCode` 无法创建、编辑或启用策略；
- 写入阶段无法转换的 JSON 被拒绝且不会污染数据库；
- 一条已启用策略编译失败时整个类型加载失败，不会跳过后命中低优先级策略；
- 编译失败输出固定事件名、定位字段和异常堆栈，但不输出完整 JSON；
- `allSubject` 与 `subjects` 互斥校验；
- 用户和用户组混合范围；
- 重复主体去重；
- 业务 JSON 以对象、数组和标量形式原样往返；
- 内容超过大小限制被拒绝；
- 第 101 条未删除策略被拒绝。

### 17.2 优先级与并发测试

- 新建策略追加到末尾；
- 禁用和启用不改变优先级；
- 删除后优先级连续压缩；
- 完整重排原子成功；
- 重排缺 ID、重复 ID、跨类型 ID 和版本冲突时整批回滚；
- 并发创建不会产生重复 priority 或突破 100 条上限；
- 编辑版本冲突返回稳定错误。

### 17.3 用户关系缓存测试

- 入组、出组和修改到期时间精确失效用户 key；
- 用户组禁用后所有当前成员 key 被失效；
- 用户组启用后原未过期成员重新可见；
- 用户组删除前能够取得待失效用户 ID；
- 成员自然到期后即使缓存未过 TTL，`getRelations` 也不再返回该组；
- 失效事件写入失败时身份写事务回滚；
- 多节点 poller 消费事件后删除对应本地缓存值。

### 17.4 持久化与集成测试

- Flyway 全量升级和约束验证；
- JSONB 对象、数组、标量和 JSON null 往返；
- 部分唯一索引允许已删除记录保留旧 priority；
- 管理 CRUD、排序和运行时查询闭环；
- PostgreSQL coherent Caffeine 下策略修改的跨节点最终可见性；
- API 不返回转义 JSON 字符串；
- Java 查询接口返回 `ResolvedPolicy<T>`，不存在字符串 `typeCode` 驱动的伪泛型转换；
- 审计和日志不包含完整 `content`。

## 18. 实施顺序

1. 新增 `backend/policy` Maven 模块、基础枚举、约束常量和领域对象。
2. 实现 `GenericPolicyType<T>`、Bean 注册中心、重复类型启动校验和安全 Jackson 转换边界。
3. 新增单表 Flyway 迁移、MyBatis mapper、JSONB type handler 和 repository。
4. 实现类型级锁、CRUD、写入预转换、逻辑删除、数量限制和原子重排。
5. 在 `identity` 增加用户关系缓存、读取时有效期过滤和全部用户组写路径失效。
6. 实现策略编译器、整类失败语义、按 `typeCode` 的共享策略缓存和泛型查询服务。
7. 接入编译失败 `ERROR` 日志、管理员审计、Micrometer 指标、自动配置和统一错误处理。
8. 提供管理 API；根据首个业务需要决定是否开放当前用户 effective HTTP API。
9. 选择一个低风险业务类型注册首个 Bean，验证前端类型、Java DTO、兼容升级、缓存失效和默认降级。
10. 底座稳定后再评估现有 AI 动态策略是否迁移，避免第一次实现同时承担兼容迁移风险。

## 19. 验收标准

- 通用策略表只有一张，适用范围和内容均使用 JSONB。
- 每个可用 `typeCode` 由唯一 `GenericPolicyType<T>` Spring Bean 声明目标 Java 类型，重复注册启动失败。
- 底座代码中不存在按具体 `typeCode` 解释 `content` 的业务分支，所有转换统一通过注册中心完成。
- 同类型未删除策略最多 100 条，priority 唯一且可以原子重排。
- 用户命中多条时严格按照全局 priority 返回一条，不按范围精确度覆盖。
- 运行时策略查询命中缓存后不访问策略数据库。
- 用户关系缓存以 userId 为 key，成员到期不受固定 TTL 延迟。
- 入组、出组、组启停和组删除都能通过共享失效使关系缓存最终收敛。
- 未命中与加载失败具有不同返回语义。
- `content` 从写入到读取保持原始 JSON 结构，不被字符串化或业务转换。
- Java 运行时查询直接返回 `ResolvedPolicy<T>`，查询热路径不重复执行 Jackson 转换。
- 任意已启用策略编译失败会阻止整个类型进入缓存，并留下可巡检的 `ERROR` 日志和失败指标。
- 日志、指标和管理员审计不泄露完整策略内容或用户关系清单。

## 20. 第一版已知限制

- JSON 内主体没有数据库外键，删除后的主体 ID 可能继续保留在策略记录中。
- 底座只验证 JSON 能否转换为注册的 Java 类型，不验证数值范围、跨字段关系等业务语义。
- 业务 Java DTO 的不兼容升级可能使对应 `typeCode` 整体编译失败，使用方必须负责数据迁移和滚动发布兼容。
- 用户组启停采用逐用户失效，超大用户组会放大事务内失效事件数量。
- 策略只支持返回一条，不支持合并多个内容片段。
- 不支持定时生效、自动过期、发布审批和历史回滚。
- 多节点可见性依赖共享缓存 poller，允许秒级或 TTL 兜底范围内的短暂陈旧。

这些限制符合第一版小规模、低频配置、缓存主读路径的定位；达到文中列出的规模或治理门槛后再扩展，不提前增加关系表、规则引擎或发布系统。
