---
slug: java-string-builder-buffer-selection
tags: [Java基础, 字符串, 类型选择]
order: 10
relations:
  related: [java-string-immutability-design, java-string-concatenation-and-loop-builders]
---

保存和传递文本通常用 `String`；在循环中或分多步构建文本，且构建对象只由当前线程使用时，优先用 `StringBuilder`；确实需要多个线程共同操作同一个可变字符序列时，可以考虑 `StringBuffer`。

原因是 `String` 的内容不可变，适合表示稳定的文本值；后两者都能修改自身内容，适合逐步构建文本。`StringBuilder` 不提供同步保证，`StringBuffer` 则通过同步保护同一实例上的操作。选择取决于对象的使用方式，不能仅凭“程序是多线程的”就选 `StringBuffer`。

少量、固定的拼接直接用 `+`，通常更清楚。构建完成后一般转成 `String` 再返回或保存。即使用了 `StringBuffer`，需要整体完成的一组操作仍须额外同步。

## 先区分文本值与构建过程

| 比较维度 | `String` | `StringBuilder` | `StringBuffer` |
| --- | --- | --- | --- |
| 内容能否改变 | 创建后不可变 | 可修改自身内容 | 可修改自身内容 |
| 如何得到不同内容 | 得到另一个字符串值 | 调用 `append`、`insert`、`delete` 等方法 | 提供类似的修改方法 |
| 多线程使用 | 同一个字符串的内容可共享读取 | 并发访问同一可变实例需要调用方保护 | 自带同步，保护同一实例上的操作 |
| 典型用途 | 字段、参数、返回值等文本数据 | 当前线程内逐步构建文本 | 需要同步访问的共享可变文本，或兼容已有接口 |

例如，执行 `text = text + "!"` 时，改变的是变量 `text` 指向的字符串，原来的 `String` 对象没有被修改。执行 `builder.append("!")` 时，改变的是这个构建对象本身，持有同一对象引用的代码会面对修改后的内容。

不可变也不代表围绕变量的操作都具有原子性：多个线程共同执行某个共享变量的 `text += "!"`，仍涉及读取旧值、拼接和赋值，不能依靠 `String` 的不可变性避免更新丢失。

## 分步构建为什么优先用 StringBuilder

`StringBuilder` 和 `StringBuffer` 都维护可扩容的内部缓冲区。追加内容时通常可以复用现有缓冲区，容量不足时会自动扩容。因此，循环中不断追加片段时，可以避免每一步都把已有结果重新拼成一个完整的 `String`。

```java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String result = builder.toString();
```

这里的 `builder` 只在当前线程中使用，不需要同步保护，默认选择 `StringBuilder`。如果能合理估计最终长度，可以指定初始容量以减少扩容；没必要为了很短的文本精确计算容量。`toString()` 得到的字符串不受后续 `builder` 修改的影响，适合作为最终结果交给其他代码。

对于 `"name=" + name + ", age=" + age` 这种片段数量固定的简单表达式，直接使用 `+` 即可。Java 编译器可以优化字符串拼接，不应把所有 `+` 都机械地改成 `StringBuilder`，也不应给三者排一个脱离场景的速度顺序。

## StringBuffer 的同步能保证到哪一步

首先看共享的是不是**同一个构建对象**。即使服务会并发处理很多请求，只要每次调用都新建自己的 `StringBuilder`，并且不把它交给其他线程并发使用，就无需换成 `StringBuffer`。

确实共享同一个 `StringBuffer` 时，它可以保护单次操作，但不会把连续调用自动合并成一个不可分割的动作：

```java
buffer.append("[").append(name).append("]");
```

这是三次独立调用。另一个线程可能在两次调用之间追加内容，所以每个线程想写入的 `[name]` 不一定在最终结果中保持连续。链式写法并没有改变同步边界。

若整段写入必须连续，可以把这一组操作放在同一个同步块中：

```java
synchronized (buffer) {
    buffer.append("[").append(name).append("]");
}
```

需要整体完成的访问应遵守同一套锁约定。另一种做法是各线程先在本地构建完整的 `String`，再一次性追加到共享的 `StringBuffer`；这样缩短了共享对象参与构建的过程，但各线程追加的先后顺序仍需由业务决定。

## 参考资料

- [Java 17 String API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)：不可变语义与字符串拼接说明。
- [Java 17 StringBuilder API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)：可变字符序列、容量与同步约定。
- [Java 17 StringBuffer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuffer.html)：同一实例上的同步保证。
