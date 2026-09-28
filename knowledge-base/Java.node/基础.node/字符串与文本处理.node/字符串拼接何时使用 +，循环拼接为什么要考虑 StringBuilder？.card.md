---
slug: java-string-concatenation-and-loop-builders
tags: [Java基础, 字符串拼接, StringBuilder]
order: 30
relations:
  related: [java-string-builder-buffer-selection, java-string-immutability-design]
---

片段数量固定、表达式简短时，直接用 `+`，例如 `"name=" + name + ", age=" + age`。这种写法清楚，编译器也可以整体优化一次拼接表达式，无需手动把每个 `+` 都改成 `StringBuilder`。

循环中不断把新片段追加到同一个结果时，优先用 `StringBuilder`。因为 `String` 不可变，`result += part` 每轮都要得到包含旧结果和新片段的字符串，越来越长的旧内容会被反复复制。`StringBuilder` 可以复用可扩容的缓冲区，逐步追加内容，最后再转成 `String`。

关键是**有没有跨轮次累积越来越长的结果**。如果循环每轮只是独立生成一条短文本，`+` 仍然适用；如果用 `StringBuilder`，应在循环外创建，并在最终结果需要时再调用 `toString()`。

## 固定表达式中的 + 为什么通常足够

```java
String label = "name=" + name + ", age=" + age;
```

这里的片段数量在编写代码时就确定了。语言规范允许实现把一次表达式中的转换和拼接合并，避免逐个 `+` 生成再丢弃中间字符串。因此，不能根据源码里有几个 `+`，就断定创建了几个中间对象。

具体如何优化属于编译器和运行时的实现，不能一概说 `+` 就是被改写为 `StringBuilder`。对于 `"hello" + " world"` 这样的常量表达式，拼接还可以在编译期完成，不需要每次执行时重新构建结果。

## 循环累积为什么会反复复制旧内容

```java
String result = "";
for (String part : parts) {
    result += part;
}
```

在这个例子中，每轮的 `result` 都依赖上一轮的完整结果。假设依次追加 `"a"`、`"b"`、`"c"`，得到的字符串依次是 `"a"`、`"ab"`、`"abc"`：构建 `"ab"` 时复制了旧的 `"a"`，构建 `"abc"` 时又复制了旧的 `"ab"`。

若有 `n` 个长度为 `k` 的非空片段，按每轮构建完整结果的通常执行方式，累计处理的字符量约为：

```text
k + 2k + 3k + … + nk = k × n × (n + 1) / 2
```

最终结果只有 `nk` 个字符，却因为反复复制前缀产生了额外工作。片段长度固定时，这部分成本随片段数呈平方增长。问题不只是产生临时对象，还包括不断复制同一批旧内容。

编译器能优化某一轮的拼接表达式，并不意味着它一定会把整个循环改成复用同一个缓冲区。这里的成本分析说明了常见实现下的问题，不是在规定所有 JVM 都必须进行相同次数的分配和复制。

## StringBuilder 如何减少这部分工作

```java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String result = builder.toString();
```

`builder` 在各轮之间保留已有内容，追加时通常只需把新片段写入缓冲区。容量不足时会扩容并搬移已有内容，但不再像循环 `+=` 那样每轮都复制完整前缀。对这种纯追加场景，考虑扩容的摊销成本后，总体工作量通常随最终文本长度线性增长。

`toString()` 用于取得不可变的最终结果，应放在循环结束后。如果每轮都调用它来获取完整字符串，仍会反复生成越来越长的结果，抵消复用缓冲区的收益。如果业务本来就需要每一轮的完整快照，这部分输出成本也不能仅靠换一种拼接写法消除。

能合理估计最终长度时，可以通过 `new StringBuilder(预计长度)` 预留容量，减少扩容；这属于进一步优化，首要的是让整个构建过程复用同一个 builder。

## 循环中每轮独立生成文本，仍可用 +

```java
for (String name : names) {
    String message = "Hello, " + name + "!";
    System.out.println(message);
}
```

每次生成的 `message` 都不包含上一轮结果，不存在越来越长的前缀被重复复制的问题。这里保留 `+` 即可，判断依据是数据如何累积，而不是代码是否写在循环里。

## 参考资料

- [JLS 17 §15.18.1：字符串拼接运算符](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18.1)：拼接语义、常量表达式及中间对象优化。
- [Java 17 String API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)：不可变性与拼接实现说明。
- [Java 17 StringBuilder API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)：缓冲区容量、追加与 `toString()`。
