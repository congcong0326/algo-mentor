---
slug: java-equals-and-hashcode-must-agree
tags: [Java基础, equals, hashCode, 值对象]
order: 100
relations:
  related: [java-reference-equality-and-equals-contract, java-hashcode-contract-and-collisions, java-final-variable-method-class-and-mutability]
---

重写 `equals()` 后，必须保证新的相等规则与 `hashCode()` 一致：**两个对象只要 `equals()` 为 `true`，哈希值就必须相同。** 如果只按字段重写 `equals()`，却沿用 `Object.hashCode()`，两个字段相同的实例可能得到不同哈希值，使 `HashSet` 去重或 `HashMap` 按等价键查找失败。

“必须同时重写”说的是要满足这项契约，并非编译器要求两个方法必须一起出现。实现值对象时，通常让 `hashCode()` 使用决定 `equals()` 结果的那些字段。

## 为什么只改 equals 会影响哈希容器

假设两个 `ProblemKey` 实例的 `id` 都是 `"42"`，重写后的 `equals()` 因此认为它们相等。把第一个实例加入 `HashSet`，再用第二个实例调用 `contains()`：容器会先依据哈希值查找候选元素，然后才比较 `equals()`。若两者沿用的 `Object.hashCode()` 恰好不同，查找就无法把已有元素认作匹配，返回 `false`。

这里的关键是**可能**失败，而不是每次都失败；两个不同实例也可能碰巧有相同哈希值。不能用一次运行的结果证明实现符合契约。

## 怎样让两个方法保持一致

如果 `equals()` 只比较 `id`，`hashCode()` 也可以直接返回 `id.hashCode()`。这样，相同 `id` 必然得到相同哈希值；不同 `id` 仍允许发生哈希碰撞，容器会继续用 `equals()` 区分。若相等规则改成忽略大小写，哈希计算也要采用一致的大小写规则。

## 参考

- [Java 17 Object.equals 与 hashCode 的联合契约](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object))。
