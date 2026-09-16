# Java 基础问题卡样稿

以下 8 张卡用于检验独立阅读和复习，每张都提供问题与核心回答。默认 Java 17。编号仅为本轮文档引用标识；前置、追问、对比关系集中维护在 [validation.md](validation.md)，不混进答案正文。

<a id="c01"></a>
## C01：两个变量指向同一个对象意味着什么？

**核心回答**：两个引用变量保存的引用值可以指向同一对象；通过任意一个引用修改这个对象的状态，其他引用可以观察到。把其中一个变量改为指向别处，不会改变另一个变量的指向。

引用赋值不等于对象复制。下面的 `first` 与 `second` 起初指向同一个 StringBuilder；`append` 改的是对象，而最后一行改的是变量。

```java
StringBuilder first = new StringBuilder("java");
StringBuilder second = first;
second.append("17");
System.out.println(first); // java17
second = new StringBuilder("other");
System.out.println(first); // java17
```

来源：G 基础中篇“对象实例与对象引用”；O 第 3 章；[JLS 4.3.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.3.1)。

<a id="c02"></a>
## C02：Java 为什么只有值传递，方法却可以修改对象？

**核心回答**：方法调用复制实参的值；引用类型复制的是引用值。调用者变量与形参于是可以指向同一对象，修改对象会被调用者观察到，但给形参重新赋值不会替换调用者变量。

```java
static void change(StringBuilder value) {
    value.append("17");
    value = new StringBuilder("other");
}

// 放在调用方法中：
StringBuilder text = new StringBuilder("java");
change(text);
System.out.println(text); // java17
```

这里没有自动进行深拷贝，也不应因为方法能改字段就称为“引用传递”。预测调用后的结果时，要分别追踪变量赋值和对象修改。

来源：G `why-there-only-value-passing-in-java.md`；B `basic-extra-meal/pass-by-value.md`；[JLS 15.12.4.5](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.5)。

<a id="c03"></a>
## C03：final 引用为什么不等于不可变对象？

**核心回答**：final 变量完成初始化后不能再次赋值；当它保存引用时，限制的是变量指向的对象不能被替换，对象自身是否可变由类型的设计决定。

```java
final StringBuilder title = new StringBuilder("java");
title.append("17"); // 合法，对象仍可变
// title = new StringBuilder("other"); // 取消注释后编译失败
```

不可变设计还要防止可变内部状态被外部修改，例如通过封装和防御性拷贝。`final` 字段也不等于整个对象图深度不可变。

来源：B `oo/final.md`、`basic-extra-meal/immutable.md`；[JLS 4.12.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4)。

<a id="c04"></a>
## C04：对对象引用使用 == 与 equals() 有何区别？

**核心回答**：引用上的 `==` 判断是否指向同一对象，或是否都为 null；`equals()` 的语义由类型实现决定。`Object.equals()` 默认判断身份，String 等类型重写它来判断各自定义的逻辑相等。

```java
String first = new String("java");
String second = new String("java");
System.out.println(first == second);      // false
System.out.println(first.equals(second)); // true
```

调用 null 引用上的 `equals` 会抛出 NullPointerException；需要允许 null 时可使用 `Objects.equals(a, b)`。本题限定对象引用；基本数值上的 `==` 涉及数值比较和类型转换，是另一张卡的范围。

**判断题**：任意 Java 类型的 equals 都会自动逐字段比较吗？**答案：否**，默认实现并不会，重写时也可以只选部分字段定义相等性。

来源：G 基础中篇“== 和 equals() 的区别”；B `string/equals.md`；[JLS 15.21.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21.3)、[Object API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)。

<a id="c05"></a>
## C05：equals() 应满足哪些约束？

**核心回答**：对于非 null 引用，equals 应满足自反性、对称性、传递性；在参与比较的信息未改变时，多次比较应保持一致；任何非 null 对象与 null 比较都应为 false。

一致性不意味着一个可变对象永远不能改变相等结果，而是比较依据不变时结果应稳定。继承也可能破坏契约：父类只比较标识，子类比较标识和附加字段，若两者允许彼此参与比较，可能出现 `a.equals(b)` 与 `b.equals(a)` 不一致。

设计值类型时需要明确是否允许跨类型相等；不能把某种 `instanceof` 或 `getClass()` 写法当成对所有继承体系都正确的模板。

来源：O 相等性附录“equals规范”；[Object.equals API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object))。

<a id="c06"></a>
## C06：为什么改变 equals() 的相等规则时，也要保证 hashCode() 一致？

**核心回答**：Object 契约要求 equals 相等的对象必须返回相同 hashCode。按逻辑相等重写 equals 时，通常需要同时重写 hashCode，依据一致的相等性字段计算；否则哈希容器可能无法正确查找或去重。

hashCode 只用于缩小哈希查找的候选范围，不是唯一标识。相同 hashCode 不保证 equals 相等；不同对象返回不同哈希值有利于分布，但不是正确性的必要条件。契约也不要求同一对象在不同次程序执行中保持同一个哈希值。

**单选题**：在实现遵守契约的前提下，下列哪个推论一定成立？

- A：hashCode 相同，则 equals 一定为 true。
- B：equals 为 true，则 hashCode 必须相同。
- C：equals 为 false，则 hashCode 必须不同。

**答案：B。** A/C 都忽略了哈希碰撞。即使所有对象都返回同一个 hashCode，也未必违反相等与哈希契约，但可能显著降低哈希容器性能。

来源：G 基础中篇“为什么重写 equals() 时必须重写 hashCode() 方法”；O 相等性附录；[Object.hashCode API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode())。

<a id="c07"></a>
## C07：用作 Map 键之后，为什么不应修改参与相等与哈希计算的字段？

**核心回答**：键进入 Map 后，如果修改影响其 equals 比较的状态，Map 接口不规定后续行为；在哈希容器中，改变哈希定位依据还可能使后续查找无法匹配已有条目。应让键的相等性依据在入表期间保持稳定。

以按题目标识定义 equals/hashCode 的键为例：入表时标识为 `two-sum`，入表后改为 `three-sum`，查找时使用的定位依据便可能与插入时不同。不能把这种情况下“一定查不到”作为所有 Map 的通用保证。

更合适的方式是使用稳定的不可变键；确需更换键时，在修改前移除原映射，再使用新键建立映射，并处理新键已存在的业务语义。修改 value 本身不等同于修改 key；与相等/哈希完全无关的键字段也不属于本题所指的修改。

**判断题**：把可变键变量声明为 final，就能避免这个问题。**答案：否**，final 不阻止键对象的字段被改变。

来源：O 相等性附录提供哈希键应用背景；本题的可变键边界以 [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) 为依据。

<a id="c08"></a>
## C08：String 的不可变性为什么有利于用作哈希键？

**核心回答**：String 创建后的字符序列不可修改，其 equals 与 hashCode 基于稳定的字符串内容，因此适合在哈希容器中保持键语义稳定。字符串拼接或替换不会就地改变原 String 的字符序列。

```java
String key = "java";
java.util.Map<String, Integer> counts = new java.util.HashMap<>();
counts.put(key, 1);
key = key + "17";
System.out.println(counts.get("java")); // 1
System.out.println(counts.get(key));    // null
```

此处只是让变量 `key` 指向另一个字符串，Map 中原键的内容没有变化。String 常量池与此结论无关：即使字符串不是驻留对象，只要内容相同，仍按 String 的 equals/hashCode 契约工作。

来源：G 基础中篇 String 部分；B `string/immutable.md`；[String API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)。
