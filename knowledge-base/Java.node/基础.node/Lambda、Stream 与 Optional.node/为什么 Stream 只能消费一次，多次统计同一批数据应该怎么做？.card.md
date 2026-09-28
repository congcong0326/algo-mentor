---
slug: java-stream-single-use-and-repeated-computation
tags: [Java基础, Stream, 单次消费]
order: 40
relations:
  prerequisites: [java-stream-pipeline-intermediate-terminal]
  related: [java-stream-short-circuit-and-termination]
---

**Stream 是一次性的处理流水线，不是可以反复遍历的数据容器。** 终止操作执行后，即使没有遍历全部元素，该流水线也已被消费；也不能从同一个流对象分叉出两条独立流水线。实现检测到复用时可以抛出 `IllegalStateException`，但不能依赖异常来判断某种复用是否合法。

多次统计同一批数据时，应从可重复访问的数据源分别创建新流；如果上游获取或计算昂贵，可以先把结果保存为集合，再为每次计算创建新流。`Supplier<Stream<T>>` 只有在每次调用都创建新流时才能用于重复计算，返回同一个流对象并不能解决问题。

## 短路也会消费整条流水线的使用资格

下面使用 Java 17，省略 `java.util.List` 和 `java.util.stream.Stream` 的导入：

```java
List<String> titles = List.of("两数之和", "二分查找", "有效的括号");
Stream<String> stream = titles.stream();

System.out.println(stream.findFirst().orElseThrow()); // 两数之和
stream.count(); // IllegalStateException：不能再次消费这个流
```

`findFirst()` 只需要部分数据，并不意味着可以用后续终止操作接着读取剩余元素。Stream 没有“重置到开头”的契约，切换 `sequential()` 或 `parallel()` 也不会创建一个可重新消费的数据源。

这并不是说源集合只能读一次。受约束的是流的生命周期，集合仍然可以重新创建流。

## 中间操作也不能从同一流对象分叉

```java
Stream<String> source = titles.stream();
Stream<String> filtered = source.filter(title -> title.contains("查找"));

// source 已衔接到 filtered，不能再次从 source 构建另一条分支。
Stream<Integer> lengths = source.map(String::length); // IllegalStateException
```

虽然此时没有执行终止操作，`source` 也已经被用于衔接一个下游阶段。正确的链式调用是在中间操作返回的流上继续操作，例如 `source.filter(...).map(...)`。

Java 17 的常规流实现会在上述错误用法处报错；API 允许实现检测复用后抛出异常，但并不保证所有复用都能被检测出来。没有抛异常不代表可以复用。

## 从同一集合创建新流

```java
long count = titles.stream()
    .filter(title -> title.contains("查找"))
    .count();

int totalLength = titles.stream()
    .filter(title -> title.contains("查找"))
    .mapToInt(String::length)
    .sum();
```

两次 `stream()` 创建两个独立的流，可以分别消费，但筛选也会执行两遍。如果集合或其中可变对象在两次计算之间发生变化，两次看到的数据可能不同；“创建新流”不等于“固定数据快照”。

## 复用处理规则，或者保存处理结果

重复使用相同规则时，可以用工厂创建新流水线：

```java
// 省略 java.util.function.Supplier 的导入。
Supplier<Stream<String>> factory = () -> titles.stream()
    .filter(title -> title.contains("查找"));

long count = factory.get().count();
int totalLength = factory.get().mapToInt(String::length).sum();
```

下面的写法则仍然复用了同一个流：

```java
Stream<String> once = titles.stream();
Supplier<Stream<String>> wrongFactory = () -> once;

wrongFactory.get().count();
wrongFactory.get().count(); // IllegalStateException
```

如果希望筛选只执行一次，可以先保存结果：

```java
List<String> selected = titles.stream()
    .filter(title -> title.contains("查找"))
    .toList();

long count = selected.size();
int totalLength = selected.stream().mapToInt(String::length).sum();
```

保存结果需要内存，只适合能够有限完成、规模可接受的数据；也不会自动深拷贝其中的可变对象。如果多个统计可以在一次遍历中完成，也可以使用合适的归约或收集操作，不必为了每个指标重新读取数据。

对于文件、网络等来源，还需确认能否重新打开、重新读取以及如何关闭资源。把创建逻辑放进 Supplier，不会自动赋予数据源可重复读取的能力，也不会保证多次读取内容一致。

## 参考

- [Java 17 Stream API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)：单次操作约束、禁止分叉与重复遍历，以及复用检测的边界。
- [Java 17 Stream 包说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)：终止操作后的消费状态，以及重新遍历时需要创建新流。
