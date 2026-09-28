---
slug: java-optional-map-flatmap-null-contract
tags: [Java基础, Optional, 函数式编程]
order: 30
relations:
  prerequisites: [java-optional-absence-and-null-safety]
  related: [java-optional-fallback-evaluation]
---

**映射函数返回普通值时用 `map`，已经返回 Optional 时用 `flatMap`。** `map` 会把映射结果包装成 Optional；`flatMap` 直接使用返回的 Optional，避免多套一层。

源 Optional 有值时，如果映射函数返回 `null`，`map` 会得到空 Optional，`flatMap` 则抛出 `NullPointerException`。这是因为 `flatMap` 要求函数返回一个 Optional，没有结果应返回 `Optional.empty()`。源 Optional 为空时，两者都直接返回空 Optional，不执行映射函数。

## 为什么一个会嵌套，一个不会

可以从映射函数的返回类型看出区别：

| 映射函数返回 | 使用 map 后 | 使用 flatMap 后 |
| --- | --- | --- |
| 普通值 `U` | `Optional<U>` | 不适用，函数返回类型不符合要求 |
| `Optional<U>` | `Optional<Optional<U>>` | `Optional<U>` |

下面用 `Optional::of` 作为返回 Optional 的映射函数，观察两种方法的结果。代码省略 `java.util.Optional` 的导入：

```java
Optional<String> text = Optional.of("Java");

Optional<Integer> length = text.map(String::length); // Optional[4]
Optional<Optional<String>> nested = text.map(Optional::of);
Optional<String> flat = text.flatMap(Optional::of);

System.out.println(nested); // Optional[Optional[Java]]
System.out.println(flat);   // Optional[Java]
```

`map` 把函数返回的整个对象作为结果值。即使这个对象已经是 Optional，它也会再包装一层；`flatMap` 则直接把函数返回的 Optional 作为结果。在实际代码中，如果查询方法已经返回 `Optional<User>`，通过 `flatMap` 衔接后就能继续处理 User，而不必先解开两层 Optional。

## 返回 null 时，区别来自哪里

源 Optional 有值时，两者的关键处理可以分别理解为：

- `map`：执行函数，再用 `Optional.ofNullable(结果)` 包装。结果为 `null` 就得到空 Optional。
- `flatMap`：执行函数，检查返回的 Optional 是否为 `null`。非 `null` 就直接返回，否则抛出 `NullPointerException`。

```java
Optional<String> source = Optional.of("标题");

Optional<String> absent = source.<String>map(value -> null);
System.out.println(absent.isEmpty()); // true

source.<String>flatMap(value -> null); // NullPointerException
```

对 `flatMap` 来说，`Optional.empty()` 是合法的“没有值”，而 `null` 表示连约定的 Optional 都没有返回。因此，表示缺失应该这样写：

```java
Optional<String> absent = source.flatMap(value -> Optional.empty());
```

## 函数本身为 null 或抛出异常时怎么办

源 Optional 为空时，两者都会跳过函数执行。不过，传入的映射函数本身必须非 `null`：`map(null)` 和 `flatMap(null)` 都会抛出 `NullPointerException`，即使源 Optional 为空也一样。实现会先检查函数参数，再判断源是否有值。

源有值、需要执行函数时，如果函数内部抛出异常，两者都会向外传播，不会把异常转换成空 Optional。`map` 对返回值 `null` 的处理，只发生在函数正常返回之后。

## 参考

- [Java 17 Optional API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)：`map` 与 `flatMap` 的返回值及异常契约。
