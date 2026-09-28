---
slug: java-string-immutability-design
tags: [Java基础, String, 不可变性]
order: 20
relations:
  related: [java-final-variable-method-class-and-mutability, java-reference-shallow-and-deep-copy]
---

`String` 的不可变性指：对象创建后，它表示的字符序列不会改变。这由一整套封装设计保证：内部存储不向外暴露可写引用，不提供修改原内容的方法，从外部可变数据构造字符串时隔离数据；同时，`String` 类声明为 `final`，禁止子类通过重写方法改变行为。

仅有 `final` 引用不够，因为它只限制变量不能重新指向另一个对象，并不禁止修改所引用对象的内容。例如，`final char[]` 的数组元素仍可修改。`String` 内部存储引用上的 `final` 也只是其中一环，必须配合对内部数据的保护，才能保证字符内容不变。

因此，即使变量没有声明为 `final`，它引用的 `String` 对象仍然不可变；变量可以改为引用另一个字符串，这与修改原对象是两回事。

## final 固定的是引用，不是对象内容

```java
final char[] letters = {'a', 'b'};
letters[0] = 'x';                // 合法：修改同一个数组的元素
// letters = new char[] {'c'};   // 编译错误：不能给 final 变量重新赋值
```

`letters` 保存的是数组引用。`final` 让这个引用值在初始化后不能改变，但数组仍然是可变对象，写入元素不需要给 `letters` 重新赋值。

同理，假设一个类只有 `private final char[] value`，也不能据此判断它不可变：类自己的方法仍可能改写元素；如果构造器直接保存调用方传入的数组，调用方也能通过原引用修改它。`private` 限制了字段访问，却不会消除指向同一个数组的其他引用。

## String 如何封住修改内容的入口

以 OpenJDK 17 为例，`String` 使用 `private final byte[] value` 保存内容，并用 `private final byte coder` 标识编码。这里的 `byte[]` 是该版本的实现细节；对使用者的契约是字符序列不可变，不是必须采用某一种数组存储。

围绕这份内部存储，各项设计分别承担不同职责：

| 设计 | 它解决的问题 |
| --- | --- |
| 存储字段为 `private final` | 外部不能直接访问字段，内部存储引用在构造后不能被重新赋值 |
| 实例方法不改写原有字符内容 | `replace`、`concat` 等操作通过返回结果表达变化，不把当前对象当作可变缓冲区 |
| 隔离外部可变输入 | 从调用方的 `char[]` 构造字符串时，复制字符内容，后续修改源数组不会影响字符串 |
| 不返回内部可写存储 | `toCharArray()` 返回新数组，调用方修改它不会影响原字符串 |
| 类声明为 `final` | 禁止继承，避免子类重写方法后表现出可变的字符串行为 |

其中，复制输入与输出是在阻止“绕过方法，直接通过数组引用修改内容”：

```java
char[] source = {'a', 'b'};
String text = new String(source);
source[0] = 'x';                 // 修改输入数组

char[] exported = text.toCharArray();
exported[1] = 'y';               // 修改返回的数组

System.out.println(text);       // 仍然是 ab
```

这两条修改路径都被数据隔离切断了。关键是不能让外部持有可写的内部数据引用，并非所有构造路径都必须复制：例如，从另一个不可变的 `String` 构造字符串时，OpenJDK 17 可以安全地共享底层存储。

类上的 `final` 同样不能独自保证不可变。如果一个 `final` 类提供了修改内容的方法，它仍然是可变类；`StringBuilder` 就是这样的例子。

## replace 和重新赋值为什么不算修改原对象

```java
String text = "cat";
String original = text;

text = text.replace('c', 'b');

System.out.println(text);       // bat
System.out.println(original);   // cat
```

`replace` 返回表示结果的字符串，赋值语句再让 `text` 指向这个结果。`original` 仍引用原来的对象，其内容保持为 `cat`。

如果把 `text` 声明为 `final`，调用 `replace` 仍然合法，只是不能再把返回值赋给 `text`。如果替换没有改变内容，`replace` 还可能直接返回原对象；不可变性要求原内容不变，并不要求每次调用都创建新对象。

## 参考资料

- [JLS 17 §4.12.4：final 变量](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4)：引用不能重新赋值不等于对象状态不能改变。
- [Java 17 String API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)：不可变契约与字符数组相关 API。
- [OpenJDK 17 String 实现](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/lang/String.java)：存储字段、构造器、`toCharArray()` 与 `replace()`。
