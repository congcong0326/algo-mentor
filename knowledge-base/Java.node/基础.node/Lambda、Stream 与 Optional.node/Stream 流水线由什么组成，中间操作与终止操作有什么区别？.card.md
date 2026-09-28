---
slug: java-stream-pipeline-intermediate-terminal
tags: [Java基础, Stream, 流水线]
order: 10
---

**一条完整的 Stream 流水线由数据源、零个或多个中间操作，以及一个终止操作组成。** 数据源提供元素，中间操作描述筛选、转换等处理规则，终止操作驱动求值，得到结果或产生副作用。

**中间操作返回新的流，并且是惰性的；终止操作结束流水线，通常在返回前完成所需处理。** 例如 `filter`、`map` 只构建处理阶段，`toList`、`count`、`forEach` 才发起计算。终止操作不一定遍历全部元素，也不一定返回值；`iterator()`、`spliterator()` 是特殊的终止操作，将遍历控制交给调用方。

Stream 本身不是存储元素的容器。`filter` 不会从源集合删除元素，`map` 也不会自动把源集合中的元素替换为映射结果。

## 用题目筛选串起流水线

下面是 Java 17 示例，省略 `java.util.List` 和 `java.util.stream.Stream` 的导入。`Problem` 是用于说明流程的简化题目模型：

```java
record Problem(String title, boolean solved) {}

static void demo() {
    List<Problem> problems = List.of(
        new Problem("两数之和", true),
        new Problem("有效的括号", false),
        new Problem("二分查找", false)
    );

    Stream<String> pipeline = problems.stream()
        .filter(problem -> !problem.solved())
        .map(Problem::title);

    // 此时只构建了流水线，还没有执行筛选和标题映射。
    List<String> titles = pipeline.toList();

    System.out.println(titles);          // [有效的括号, 二分查找]
    System.out.println(problems.size()); // 3：源集合没有被筛选操作修改
}
```

| 阶段 | 示例 | 作用 |
| --- | --- | --- |
| 数据源与流创建 | `problems.stream()` | 从题目集合创建 `Stream<Problem>`，集合是数据源 |
| 中间操作 | `filter(...)` | 描述筛选规则，返回 `Stream<Problem>` |
| 中间操作 | `map(Problem::title)` | 描述映射规则，返回 `Stream<String>` |
| 终止操作 | `toList()` | 执行所需处理，将结果汇集为 `List<String>` |

这里的 `Stream<String>` 表示流水线后续提供的元素类型，并不表示已经生成了一份标题集合。返回新流也不意味着复制出一份中间集合。

中间操作可以为零，例如 `problems.stream().count()` 也是完整流水线。

## 如何区分两类操作

| 对比点 | 中间操作 | 终止操作 |
| --- | --- | --- |
| 常见方法 | `filter`、`map`、`flatMap`、`sorted`、`distinct`、`limit` | `toList`、`collect`、`reduce`、`count`、`findFirst`、`anyMatch`、`forEach` |
| 返回内容 | 后续可继续组合的流，元素类型可能变化 | 集合、标量、Optional 等结果，也可能是 `void` |
| 求值时机 | 调用时构建阶段，不立即处理源元素 | 通常发起并完成本次求值；迭代器类终止操作由调用方驱动遍历 |
| 流水线位置 | 可继续衔接中间操作或终止操作 | 结束本次流水线，消费后不能继续复用该流 |

判断的重点是操作在流水线中的职责，不能把“返回流”作为判断任意 Stream 方法的唯一依据。例如 `parallel()`、`sequential()` 用于设置执行模式，不是筛选、映射这样的元素处理阶段。

## 三个容易混淆的边界

- **终止不等于处理全部元素。** `findFirst()` 等短路终止操作可能只需要部分元素；`count()` 在能够直接确定数量时也可能省略遍历。因此，不能依据流水线中出现了某个回调，就断言它一定执行。
- **流水线不自动修改源集合，不代表回调不能修改对象。** 示例只读取题目属性；如果回调主动修改可变对象，仍可能影响源集合持有的同一对象。
- **终止后仍能继续调用方法，不代表原流水线还在继续。** `stream.toList().stream()` 是从结果列表创建另一条流；原来的流已被消费。

## 参考

- [Java 17 Stream 包说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)：Stream 的特征，以及 “Stream operations and pipelines” 中对流水线、中间操作和终止操作的定义。
