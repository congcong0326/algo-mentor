---
slug: java-optional-absence-and-null-safety
tags: [Java基础, Optional, 空值处理]
order: 10
---

`Optional<T>` 用类型显式表达“结果可能不存在”，主要用于方法返回值，提醒调用方处理缺失情况。它要么包含一个非 null 的值，要么为空；**空 Optional 是一个对象，不是 null，Optional 变量本身也不应为 null。**

`Optional.empty()` 表示无值；`Optional.of(value)` 要求 value 非 null，否则抛出 `NullPointerException`；`Optional.ofNullable(value)` 接受 null，并将其转为空 Optional。

**Optional 不会让 Java 自动具备空安全。** Optional 引用本身为 null、包装前的表达式解引用 null、回调内部访问空属性，仍可能抛出空指针异常。此外，空 Optional 调用 `get()` 抛出的是 `NoSuchElementException`，不是空指针异常。

## 它表达的是“可能没有结果”

以按 ID 查询题目为例，下面两个签名向调用者传递的信息不同：

```java
Problem findProblem(long id);            // 仅凭类型无法判断：查不到时返回 null 还是抛异常？
Optional<Problem> findProblem(long id);  // 明确提示：查询可能没有结果
```

两者是不同 API 设计的示意，不能仅凭返回类型在同一个类中重载。

调用方可以明确选择缺失时的处理方式，例如在必须拿到题目的业务中：

```java
Problem problem = findProblem(id)
    .orElseThrow(() -> new IllegalArgumentException("题目不存在"));
```

Optional 让缺失情况在接口上可见，但 Java 编译器不会强制调用方正确处理它，也不会禁止方法错误地返回 null。这仍然需要实现方遵守契约。

## 三种创建方式的契约

| 写法 | 含义 | 传入 null 时的行为 |
| --- | --- | --- |
| `Optional.empty()` | 明确表示没有值 | 无参数 |
| `Optional.of(value)` | 要求当前值非 null | 抛出 `NullPointerException` |
| `Optional.ofNullable(value)` | 将可能为 null 的值转为 Optional | 返回空 Optional |

```java
Optional<String> missing = Optional.empty();
Optional<String> title = Optional.of("两数之和");
Optional<String> nullableTitle = Optional.ofNullable(null);

System.out.println(missing.isEmpty());       // true
System.out.println(title.isPresent());      // true
System.out.println(nullableTitle.isEmpty()); // true
```

空字符串、空集合仍然是非 null 的值。例如 `Optional.of("").isPresent()` 为 true，Optional 不会自行判断业务上的“空”。

## 为什么仍然会出现空指针异常

下面各段是独立的错误示例。

**Optional 引用本身为 null。**

```java
Optional<String> title = null;
title.orElse("未命名"); // NullPointerException
```

返回 Optional 的方法在没有结果时应返回 `Optional.empty()`，否则调用方还得先判断 Optional 是否为 null，破坏了接口原本的约定。

**包装之前就发生了空指针。**

```java
String title = null;
Optional<String> result = Optional.ofNullable(title.trim());
// 先执行 title.trim()，此时已抛出 NullPointerException，尚未调用 ofNullable
```

`ofNullable` 只能处理传给它的值，不能保护实参表达式的求值。这里可以改成：

```java
Optional<String> result = Optional.ofNullable(title).map(String::trim);
```

**被包装的对象非 null，不代表它的所有属性非 null。**

```java
record Problem(String title) {}

Optional<Problem> problem = Optional.of(new Problem(null));
problem.map(p -> p.title().trim()); // 回调内部对 null 调用 trim，仍抛出 NPE
```

在这个例子中，可以逐步映射，让可空的属性先成为 Optional 的处理结果：

```java
Optional<String> title = problem.map(Problem::title).map(String::trim);
// 第一次 map 的结果为 null 时得到空 Optional，后续映射不执行
```

Optional 不会捕获并吞掉回调内部抛出的异常。

## 空 Optional、空指针与执行失败不要混为一谈

```java
Optional.empty().get(); // NoSuchElementException：容器存在，但没有值可取
```

没有值时盲目调用 `get()`，只是把对缺失结果的处理推迟成了运行时异常。应根据业务选择默认值、条件执行或明确抛出异常。

同样，**“没有查到题目”和“数据库查询失败”是不同结果**：前者可以正常返回 `Optional.empty()`；后者通常应传播或转换异常。不要捕获所有异常再返回空 Optional，否则调用方无法区分数据不存在和系统故障。

## 参考

- [Java 17 Optional API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)：用途说明及 `empty`、`of`、`ofNullable`、`get`、`map` 的契约。
