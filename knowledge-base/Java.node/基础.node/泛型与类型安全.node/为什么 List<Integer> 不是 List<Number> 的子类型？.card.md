---
slug: java-generic-invariance-list-subtyping
tags: [Java基础, 泛型不变性, 子类型]
order: 50
relations:
  related: [java-generics-versus-object-type-safety]
---

**Integer 是 Number 的子类型，不代表 `List<Integer>` 是 `List<Number>` 的子类型。** 后者的契约允许写入任意 Number，例如 Double；如果把整数列表当成 Number 列表，就能通过另一引用塞入 Double，破坏原列表的类型承诺。因此 Java 的这类泛型参数化类型默认不变。

需要接收多种 Number 子类型列表时，可以根据操作方向使用 `List<? extends Number>` 等通配符类型，但相应读写能力也会受到限制。

## 反证：假设这种赋值被允许

```java
java.util.List<Integer> integers = new java.util.ArrayList<>();
integers.add(1);

// 假设下面赋值合法，后续会破坏类型安全：
// java.util.List<Number> numbers = integers;
// numbers.add(3.14);               // 对 List<Number> 来说完全合法
// Integer value = integers.get(1); // 实际却是 Double
```

编译器直接拒绝第一条赋值，避免把错误留到读取时。即使当前方法“只是打算读”，`List<Number>` 类型本身仍允许写入 Number，所以不能靠使用意图放松它的契约。

## 用上界通配符表达读取需求

```java
static double sum(java.util.List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

此方法能接收 `List<Integer>` 或 `List<Double>`。它承诺只按 Number 的能力读取，不会写入某个任意 Number。这里的求和仅展示类型关系，double 不适用于所有要求精确结果的数值业务。

`List<Integer>` 可以作为 `List<? extends Number>` 使用；这不是把原来的列表转换或复制成 `List<Number>`，只是用较受限制的类型来观察同一个对象。

## 不变性没有取消容器本身的继承

`ArrayList<Integer>` 仍然是 `List<Integer>` 的子类型，因为它实现了对应接口；不能成立的是把元素类型的继承关系直接搬到 List 的类型参数上。

数组则采用协变规则：`Integer[]` 可以赋给 `Number[]`，但通过后者存入 Double 时会抛出 ArrayStoreException。数组依赖运行时元素类型检查，泛型的不变性在编译期阻止了类似不安全赋值，不应把两套规则混用。

## 参考

- [JLS 17 §4.10.2：类和接口类型的子类型关系](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2)。
- [JLS 17 §4.5.1：通配符类型实参](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1)。
