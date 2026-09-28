---
slug: java-optional-fallback-evaluation
tags: [Java基础, Optional, 惰性求值]
order: 20
relations:
  prerequisites: [java-optional-absence-and-null-safety]
---

`orElse(defaultValue)` 接收一个已经求值的普通参数，**即使 Optional 有值，实参表达式也会先执行**；`orElseGet(supplier)` 只在 Optional 为空时调用 Supplier 计算默认值。差别不只是性能：默认值计算中的副作用和异常也可能改变程序行为。

已有常量或已计算好的默认值可以用 `orElse`；需要查询、创建对象等按需计算时用 `orElseGet`；缺失违反业务要求时用 `orElseThrow`。若备用查询也返回 Optional，并希望继续链式处理，则用 Java 9 起提供的 `or`。

## 有值时，备用逻辑是否执行

下面示例的两次调用都会返回“已有标题”，但只打印一次“计算默认标题”：

```java
static String defaultTitle() {
    System.out.println("计算默认标题");
    return "未命名";
}

static void demo() {
    Optional<String> title = Optional.of("已有标题");
    String first = title.orElse(defaultTitle());
    String second = title.orElseGet(() -> defaultTitle());
}
```

第一行先调用 `defaultTitle()`，再把返回值传给 `orElse`；第二行传入 Lambda，只有缺失时才执行 Lambda 的方法体。若 `defaultTitle()` 抛出异常，第一行即使有标题也会失败。

因此，`findProblem(id).orElse(createProblem())` 可能在题目已经存在时仍创建题目。如果创建行为确实是缺失时的业务策略，应改为 `orElseGet(() -> createProblem())`。

## 惰性的是回调执行，不是整个参数表达式

```java
title.orElseGet(buildSupplier());
```

这里 `buildSupplier()` 仍会立即执行，只有返回的 Supplier 的 `get()` 调用被延后。需要延后的工作应放进回调内部；`orElseGet` 不会改变 Java 对方法实参的求值规则。

## 按缺失语义选择出口

| 缺失时的要求 | 写法示意 | 返回类型 |
| --- | --- | --- |
| 使用固定默认标题 | `title.orElse("未命名")` | `String` |
| 按需生成默认标题 | `title.orElseGet(() -> defaultTitle())` | `String` |
| 题目必须存在 | `problem.orElseThrow(() -> new IllegalArgumentException("题目不存在"))` | 题目对象 |
| 继续尝试另一个可缺失来源 | `cached.or(() -> findProblem(id))` | `Optional<Problem>` |

`or` 只在当前 Optional 为空时调用备用 Supplier，且备用结果必须是非 null 的 Optional；查不到应返回 `Optional.empty()`。

`orElse(null)` 和返回 null 的 `orElseGet` 回调都是允许的，但会让最终结果重新变成可空引用。应只在明确需要适配可空接口的边界使用，不能因此假定解包后的结果一定非 null。

## 参考

- [Java 17 Optional API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)：`orElse`、`orElseGet`、`orElseThrow` 与 `or` 的契约。
