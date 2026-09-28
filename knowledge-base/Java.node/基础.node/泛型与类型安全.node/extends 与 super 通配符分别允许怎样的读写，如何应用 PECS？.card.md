---
slug: java-wildcards-extends-super-and-pecs
tags: [Java基础, 通配符, PECS]
order: 70
relations:
  related: [java-generic-invariance-list-subtyping, java-list-wildcard-object-and-raw-type]
---

`List<? extends T>` 的实际元素类型是 T 或其某个子类型，但具体是哪一种不知道。取出的值能当 T 用；不能直接放入任意非 `null` 的 T，因为列表可能要求更具体的类型。`List<? super T>` 的实际元素类型是 T 或其某个父类型。可以放入 T 及其子类型；取出时只保证是 `Object`，因为列表里可能原本就有其他值。

PECS 用来选方法参数的类型：方法**从集合取出 T**，用 `? extends T`（Producer Extends）；方法**向集合放入 T**，用 `? super T`（Consumer Super）。这里的“生产”和“消费”是相对该方法而言的。

## 未知的实际类型决定了读写限制

以 `Number` 为例，对同一个通配符声明，调用方可能传入不同的实际列表：

| 方法看到的类型 | 实际列表可能是 | `get` 至少保证 | 可以直接 `add` 什么 |
| --- | --- | --- | --- |
| `List<? extends Number>` | `List<Number>`、`List<Integer>`、`List<Double>` | `Number` | 不能添加任意非 `null` 的 Number |
| `List<? super Number>` | `List<Number>`、`List<Object>` | `Object` | `Number` 及其子类型 |

上界不能随意写入：`List<? extends Number>` 可能指向 `List<Double>`，此时写入 Integer 就会破坏元素类型。下界可以写入 Number：无论实际是 `List<Number>` 还是 `List<Object>`，都能接收它；但 `List<Object>` 可能早已有字符串，读取时不能保证得到 Number。

```java
List<Integer> integers = List.of(1, 2);
List<? extends Number> source = integers;
Number first = source.get(0); // 可以按 Number 读取
// source.add(3);             // 编译错误：声明不保证元素类型是 Integer

List<Object> objects = new ArrayList<>();
objects.add("已有字符串");
List<? super Number> target = objects;
target.add(3);                // Integer 是 Number 的子类型，可以写入
Object old = target.get(0);   // 只能保证 Object；这里是字符串
// Number n = target.get(0);  // 编译错误
```

代码中的 `List` 和 `ArrayList` 来自 `java.util`。

## 用 PECS 描述一次追加

```java
static void appendNumbers(List<? extends Number> source,
                          List<? super Number> target) {
    for (Number value : source) {
        target.add(value);
    }
}

static void demo() {
    List<Integer> integers = List.of(1, 2);
    List<Object> objects = new ArrayList<>();
    appendNumbers(integers, objects);
}
```

`source` 提供 Number，实际可由整数列表传入；`target` 接收 Number，实际可由 Object 列表传入。把 `Number` 换成方法类型参数 `T`，就得到通用形式 `List<? extends T>` → `List<? super T>`。如果方法既要按某个类型读取，又要向同一个列表写入该类型，通常直接用 `List<T>` 更清楚。

这些规则说的是**编译期允许传入什么类型的值**，不保证列表在运行时可修改。例如 `List.of(...)` 不接受 `add`；而 `extends` 视图也不等于完全只读，仍可调用不需要传入元素的 `clear()`。`null` 是 `add` 的特殊例外，但具体列表也可能拒绝它。

## 参考

- [JLS 17 §4.5.1：上界和下界通配符](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1)。
