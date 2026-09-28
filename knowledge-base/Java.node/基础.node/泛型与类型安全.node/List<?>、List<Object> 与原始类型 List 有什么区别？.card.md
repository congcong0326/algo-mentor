---
slug: java-list-wildcard-object-and-raw-type
tags: [Java基础, 通配符, 原始类型]
order: 60
relations:
  related: [java-generic-invariance-list-subtyping, java-generics-versus-object-type-safety]
---

**`List<?>` 表示元素类型未知但固定的列表；`List<Object>` 明确允许把不同引用类型的值作为 Object 放入；原始类型 List 则放弃了相应泛型检查，主要用于兼容旧代码。** 三者不能因读取结果都可能是 Object 就视为等价。

从 `List<?>` 读取可当作 Object，但不能直接添加任意非 null 值，因为不知道原列表究竟要求什么类型；`List<Object>` 可以添加任意引用值；原始 List 的不安全写入通常只有编译警告，却可能污染其他参数化引用所看到的数据。

## 把读写能力并排比较

| 声明 | 可以接收 `List<String>` 吗 | `get` 可保证的类型 | 添加字符串或整数 |
| --- | --- | --- | --- |
| `List<?>` | 可以 | Object | 都不能直接添加非 null 值 |
| `List<Object>` | 不可以 | Object | 可以，整数发生装箱 |
| 原始 `List` | 可以 | Object | 通常允许但产生 unchecked 警告 |

这里讨论编译期类型允许的操作，实际对象还可能拒绝修改，例如不可修改列表会抛出 UnsupportedOperationException。

## ? 不是“元素类型随时可以变”

```java
java.util.List<String> names = new java.util.ArrayList<>();
names.add("数组");
java.util.List<?> unknown = names;
Object first = unknown.get(0); // 安全
// unknown.add("链表");       // 编译错误：并不知道捕获的实际类型
// unknown.add(1);            // 同样编译错误
unknown.add(null);            // 类型上允许，本例 ArrayList 也允许 null
```

虽然当前程序员看得出 unknown 来自字符串列表，但 `List<?>` 这个静态类型不再承诺元素就是 String。编译器根据声明保证安全，不根据当前值进行任意推测。

`List<Object>` 则是明确选定 Object 为元素类型：

```java
java.util.List<Object> mixed = new java.util.ArrayList<>();
mixed.add("数组");
mixed.add(1);
// mixed = names; // 编译错误：List<String> 不是 List<Object> 的子类型
```

## 原始类型绕开的是检查，不是风险

```java
java.util.List<String> typed = new java.util.ArrayList<>();
java.util.List raw = typed;
raw.add(42);                   // unchecked 调用警告
String value = typed.get(0);    // ClassCastException
```

错误在写入处就被引入，却可能很晚才在其他模块读取时暴露。新 API 通常不应使用原始 List；只需遍历而不关心元素具体类型时，`List<?>` 更能保留安全边界。

## 通配符列表并非不可修改集合

对 `List<?>` 仍可调用 `clear()`、`remove(int)`、`remove(Object)` 等不需要提供未知元素类型值的方法，具体是否成功取决于实现。泛型辅助方法还可以捕获那个未知类型，对已有元素做类型一致的重新排列。

因此“不能直接添加任意非 null 元素”不能缩写成“List<?> 完全只读”；泛型读写限制与集合是否可修改是两个问题。

## 参考

- [JLS 17 §4.5.1：无界通配符](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1)。
- [JLS 17 §4.8：原始类型与未经检查操作](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8)。
