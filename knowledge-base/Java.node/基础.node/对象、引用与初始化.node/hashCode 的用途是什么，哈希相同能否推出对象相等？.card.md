---
slug: java-hashcode-contract-and-collisions
tags: [Java基础, hashCode, 对象契约]
order: 90
relations:
  related: [java-reference-equality-and-equals-contract]
---

`hashCode()` 返回一个 `int` 哈希值，哈希集合和映射用它缩小查找范围，再用 `equals()` 判断候选对象是否相等。**哈希相同不能推出对象相等**：不同对象可能得到同一个哈希值，这叫哈希碰撞。反过来，按照 Java 的契约，`equals()` 相等的对象必须有相同的哈希值。

## 哈希容器为什么还要比较 equals

以 `HashSet` 为例，加入或查找元素时，容器先利用哈希值定位候选位置。若候选元素的哈希值相同，还要比较 `equals()`；否则，一次碰撞就会让两个不相等的元素被误当成同一个元素。`hashCode()` 负责缩小范围，`equals()` 才决定对象是否相等。

下面两个字符串就是一次碰撞：

```java
String a = "Aa";
String b = "BB";
System.out.println(a.hashCode()); // 2112
System.out.println(b.hashCode()); // 2112
System.out.println(a.equals(b));  // false

java.util.Set<String> values = new java.util.HashSet<>();
values.add(a);
values.add(b);
System.out.println(values.size()); // 2
```

`"Aa"` 和 `"BB"` 的哈希值都是 2112，但内容不同，所以集合能同时保存它们。

## 参考

- [Java 17 Object.hashCode 契约](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode())。
- [Java 17 String.hashCode 算法](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#hashCode())。
