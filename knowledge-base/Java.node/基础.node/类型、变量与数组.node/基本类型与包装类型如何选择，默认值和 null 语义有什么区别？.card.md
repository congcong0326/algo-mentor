---
slug: java-primitives-wrappers-defaults-and-null
tags: [Java基础, 基本类型, 包装类]
order: 10
---

**一定有值、需要直接运算时优先使用基本类型；需要表达缺失，或作为泛型类型实参时使用包装类型。** `int` 等基本类型保存相应的值，不能取 `null`；`Integer` 等包装类型是引用类型，可以引用表示数值的对象，也可以为 `null`。

默认值取决于变量种类：字段和数组元素有默认值，数值基本类型为零、`boolean` 为 `false`、`char` 为 `'\u0000'`，引用类型为 `null`。**局部变量无论基本类型还是包装类型，都必须在使用前满足确定赋值要求**，不能直接读取“默认值”。

## 八种对应关系

| 基本类型 | 包装类型 | 字段或数组元素的基本类型默认值 |
| --- | --- | --- |
| `byte`、`short`、`int`、`long` | `Byte`、`Short`、`Integer`、`Long` | 数值 0 |
| `float`、`double` | `Float`、`Double` | 正零 `0.0f`、`0.0d` |
| `char` | `Character` | `'\u0000'`，不是字符 `'0'` |
| `boolean` | `Boolean` | `false` |

所有这些包装类型的字段默认都是 `null`。`void` 不属于这八种基本值类型的使用场景，不能声明一个 `void` 变量。

```java
class Progress {
    int solved;          // 0：已解题数量
    Integer target;      // null：尚未设置目标
    boolean completed;   // false
}

// 以下片段放在方法中：
Progress progress = new Progress();
System.out.println(progress.solved); // 0
System.out.println(progress.target); // null

int count;
Integer limit;
// System.out.println(count); // 编译错误：尚未赋值
// System.out.println(limit); // 同样编译错误
```

`new Integer[2]` 只创建数组，两个元素都为 `null`，不会自动创建两个表示零的 Integer。`final` 字段还必须满足其赋值规则，不能因为字段有默认初始化就省略应有的赋值。

## 用 null 表达什么，应先由业务决定

学习计划的“已解题数”通常应始终存在，`int solved = 0` 比可空计数更合适。“每日目标”若允许尚未配置，`Integer target` 可区分“未设置”和“明确设置为 0”。但进入计算前，应决定缺失意味着采用默认值、拒绝请求还是跳过计算，不能直接拆箱。

```java
Integer requestedTarget = null;
int effectiveTarget = requestedTarget == null ? 10 : requestedTarget;
```

这个默认值只适用于业务认可的规则，不能对所有可空数字机械补零。边界 DTO 用包装类型，也不意味着内部计算变量必须一直使用包装类型。

## 选择时还有两条约束

- 泛型实参必须是引用类型：可以写 `List<Integer>`，不能写 `List<int>`；大量数值计算可使用 `int[]` 等，减少不必要的装箱。
- 包装类对象不可变，改变 `Integer` 变量的数值实际上是重新赋值；包装类的 `==` 还涉及引用身份，不能替代通用数值比较。

不要用“基本类型都在栈、包装类型都在堆”决定选型。字段归属、局部变量和优化实现是另一层问题，此处首先要明确值是否允许缺失以及 API 的类型约束。

## 参考

- [JLS 17 §4.12.5：变量的初始值](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.5)。
- [JLS 17 §4.2：基本类型与值](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2)。
