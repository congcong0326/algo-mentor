---
slug: java-optional-api-absence-modeling
tags: [Java基础, Optional, API设计]
order: 40
relations:
  prerequisites: [java-optional-absence-and-null-safety]
  related: [java-optional-map-flatmap-null-contract]
---

`Optional<T>` 最适合表示方法返回的“零个或一个结果”，且缺失是正常业务情况。返回多个结果时通常直接返回非 null 的集合，用空集合表示零个结果；只有“没有集合”和“集合存在但没有元素”确实是不同业务状态时，才考虑 `Optional<List<T>>`。

**Optional 只有有值和无值两种状态，不能携带缺失原因。** 若调用方需要区分未找到、未加载、无权限等结果，应使用明确的结果类型；执行故障通常用异常或既定错误模型表达。Optional 也不应机械地替代所有参数和字段，它是接口语义的选择，不是 Java 的非空类型系统。

## 单值查询与多值查询

下面是接口签名示意：

```java
Optional<Problem> findProblem(long id);
List<Problem> findProblemsByTag(String tag);
```

第一种查询允许正常地“没有这道题”；第二种查询即使没有匹配项，也可以成功返回空列表，让调用方统一遍历、计数和过滤。

若第二种返回 `Optional<List<Problem>>`，调用方就需要理解三个状态：

| 返回值 | 必须明确的语义 |
| --- | --- |
| `Optional.empty()` | 没有集合，具体含义必须由接口定义 |
| `Optional.of(List.of())` | 集合存在，但没有元素 |
| Optional 包含非空列表 | 集合存在，且包含元素 |

如果前两种都只是“没有匹配题目”，这层 Optional 增加了分支，却没有增加信息。若分别表示“尚未加载”和“加载完成但为空”，区分才有价值，也可以考虑命名更明确的状态类型。

## 为什么不默认把参数和字段都改成 Optional

Java 允许 Optional 出现在参数和字段中，但官方 API 将它的主要用途定位为返回值。作为参数时，调用方需要先包装值，而实现仍然可能收到 null 的 Optional；简单的可选配置通常可以通过重载、明确的默认值或配置对象表达。

作为字段时，需要维护“字段本身永不为 null”的约束，并检查对象的序列化契约。Java 17 的 `Optional` 没有实现 `Serializable`；可序列化对象包含非 transient 的 Optional 字段时，原生 Java 对象序列化会失败。其他序列化机制是否支持 Optional，取决于各自的适配，不能一概而论。

这些是设计取舍，并不是语法禁令。若团队契约明确、使用环境支持，并且有值或无值确实就是完整状态，参数或字段也可以使用 Optional。

## 两种状态不够时，显式建模

例如编辑题目标题时，可能需要区分“保持原值”“清空标题”“设置新标题”。仅用 `Optional<String>` 不足以清楚表达这三个意图，也不应额外约定 null 的 Optional 代表其中一种。

Java 17 可以用 sealed 接口与 record 明确表达这组操作：

```java
sealed interface TitleChange permits KeepTitle, ClearTitle, SetTitle {}

record KeepTitle() implements TitleChange {}
record ClearTitle() implements TitleChange {}
record SetTitle(String value) implements TitleChange {
    SetTitle {
        java.util.Objects.requireNonNull(value, "新标题不能为 null");
    }
}
```

此处演示的是 Java 类型设计，不涉及网络传输格式。三个类型直接表达三个操作；接收方仍需保证传入的 `TitleChange` 本身非 null。

选择类型时先问：调用方究竟要区分哪些状态？只有“有结果或没有结果”时，Optional 很合适；需要更多状态或失败原因时，就应让这些信息在契约中可见。

## 参考

- [Java 17 Optional API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)：主要作为方法返回类型的用途说明、容器状态及类声明。
