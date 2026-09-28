---
slug: java-reference-equality-and-equals-contract
tags: [Java基础, equals, 相等性]
order: 80
relations:
  related: [java-wrapper-caching-and-value-equality, java-polymorphic-dispatch-and-static-binding]
---

**`==` 比较基本类型时，按对应的值比较规则判断；比较两个引用时，判断它们是否指向同一个对象，或是否都为 null。`Object.equals` 的默认实现也判断是不是同一个对象。重写后的 equals 则按该类型定义的规则，判断两个对象是否“逻辑相等”。**

例如，两个不同的 String 对象可以包含相同字符：用 `==` 比较为 false，用 `String.equals` 比较为 true，因为 String 重写了 equals。自定义类如果没有重写，就算所有字段值都一样，两个不同对象的 equals 仍然返回 false。不能简单记成“`==` 比地址，equals 比内容”，必须先看类型和实际使用的 equals 实现。

## 三种比较各自在判断什么

| 比较方式 | 判断依据 | 能否由类自定义 |
| --- | --- | --- |
| 基本类型的 `==` | 数值或布尔值的相等规则，例如 `3 == 3L` 为 true | 不能 |
| 引用之间的 `==` | 是否为同一个对象，或是否都为 null | 不能 |
| 继承自 Object 的默认 equals | 是否为同一个对象，效果相当于 `this == obj` | 可以通过重写替换 |
| 重写后的 equals | 类型定义的相等规则，例如比较分数值、字符串字符序列 | 由实现决定，但必须遵守 equals 契约 |

对于引用，比较的是对象身份，无需把引用理解成可以直接观察的内存地址。基本类型也要按语言规则判断，例如浮点数的 `NaN == NaN` 为 false，不能套用对象 equals 的自反性要求。

## 没有重写 equals：字段相同也不等于对象相同

先定义一个只有分数字段、没有重写 equals 的类：

```java
final class Score {
    private final int value;

    Score(int value) {
        this.value = value;
    }
}
```

创建两个分数值相同的对象，再让第三个变量指向第一个对象：

```java
Score a = new Score(90);
Score b = new Score(90);
Score same = a;

System.out.println(a == b);      // false：两次 new 创建了不同对象
System.out.println(a == same);   // true：两个变量指向同一个对象
System.out.println(a.equals(b)); // false：继承的 Object.equals 不比较 value
System.out.println(a.equals(same)); // true
```

`a` 和 `b` 都保存 `90`，只是字段内容相同；`a` 和 `same` 才是指向同一个对象。默认 equals 只判断后一种关系，不会自动遍历字段进行比较。

## 重写 equals：明确哪些对象应当算作相等

如果希望“分数值相同的 Score 就相等”，可以在上面的 Score 类中加入：

```java
@Override
public boolean equals(Object obj) {
    return obj instanceof Score other && value == other.value;
}

@Override
public int hashCode() {
    return Integer.hashCode(value);
}
```

这段 Java 17 代码先检查对方是不是 Score，再比较两个对象的 `value`。`obj` 为 null 时，`instanceof` 为 false，因此也能正确处理 null。Score 是 final 类，无需处理子类增加字段后该如何比较的问题。

补上这两个方法后，同样创建 `a`、`b`，比较结果变成：

```java
System.out.println(a == b);      // false：仍然是两个对象
System.out.println(a.equals(b)); // true：分数值相同，符合我们定义的相等规则
```

重写 equals 没有把两个对象合并，也没有改变 `==`。它只是给这个类型增加了一套逻辑相等规则。`hashCode` 使用同一个分数字段，是为了保证 equals 相等的对象具有相同的哈希值。

## 变量声明为 Object，会改回默认 equals 吗

不会。继续使用已经重写 equals 的 Score：

```java
Object x = new Score(90);
Object y = new Score(90);

System.out.println(x == y);      // false
System.out.println(x.equals(y)); // true：实际调用 Score.equals
```

`equals(Object)` 是实例方法，运行时会根据接收对象 `x` 的实际类型选择重写实现。这里 `x` 指向 Score，所以调用 Score.equals；把引用声明为 Object，或者强制转换为 Object，都不会取消重写。

因此，“Object.equals 的默认实现”指的是 Object 类提供的那段实现，不是指“变量类型写成 Object 时调用的所有 equals”。

重写时参数必须是 `Object`。如果写成 `equals(Score other)`，只是增加了一个重载方法，原来的 `equals(Object)` 仍然存在。加上 `@Override`，编译器就能帮助检查是否真的完成了重写。

## 自定义相等规则需要遵守哪些约定

对于非 null 的对象引用，equals 应满足：

| 约定 | 具体含义 |
| --- | --- |
| 自反性 | `x.equals(x)` 为 true |
| 对称性 | `x.equals(y)` 与 `y.equals(x)` 结果相同 |
| 传递性 | x 等于 y、y 等于 z，那么 x 也等于 z |
| 一致性 | 比较依据的状态不变，多次比较结果应一致 |
| null 边界 | `x.equals(null)` 为 false |

此外，equals 相等的对象必须有相同的 hashCode；哈希值相同则不一定 equals 相等。相等规则不是随意挑几个字段比较，还要让不同调用方向、不同对象组合得到一致的结果。

## null 和数组应该怎么比较

| 场景 | 结果或选择 |
| --- | --- |
| `x` 是非 null 对象，执行 `x.equals(null)` | 符合契约的实现返回 false |
| `x` 是 null，执行 `x.equals(y)` | 抛出 NullPointerException，方法根本没有被调用 |
| 引用可能为 null | 使用 `Objects.equals(x, y)`；先判断是否为同一引用（包括都为 null），否则在 x 非 null 时调用 `x.equals(y)`，x 为 null 时返回 false |
| 两个内容相同的独立数组 | 数组继承 Object.equals，直接 equals 仍然为 false；比较内容用 `Arrays.equals`，嵌套数组按需用 `Arrays.deepEquals` |

`Objects.equals` 只提供 null 安全的比较入口，不会替类型定义新的相等规则，也不会自动把数组比较变成内容比较。

## 参考

- [Java 17 Object.equals 契约](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object))。
- [JLS 17 §15.21：相等运算符](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21)。
