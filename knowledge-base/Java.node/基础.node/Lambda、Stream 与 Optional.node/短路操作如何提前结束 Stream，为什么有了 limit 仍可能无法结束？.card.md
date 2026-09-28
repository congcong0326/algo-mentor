---
slug: java-stream-short-circuit-and-termination
tags: [Java基础, Stream, 短路操作]
order: 30
relations:
  prerequisites: [java-stream-lazy-evaluation-and-fusion]
---

**短路操作允许流水线在不处理全部输入的情况下得到结果，但不保证任意流水线都能结束。** `limit(n)` 是短路中间操作，限制向下游提供的元素数量；`findFirst()`、`anyMatch()` 等是短路终止操作，在结果已经确定时可以停止继续请求元素。

能否结束取决于整条流水线：上游能否提供所需元素、是否需要等待全部输入，以及终止结果是否能在有限步骤内确定。`limit` 限制的是它所在位置的输出数量，不是数据源的读取次数，也不是执行超时。

## 两类短路操作

| 操作 | 类型 | 可以提前结束的条件 |
| --- | --- | --- |
| `limit(n)` | 中间操作 | 已向下游提供至多 `n` 个元素；若上游先结束，实际数量可以更少 |
| `findFirst()` | 终止操作 | 找到第一个元素；没有元素时，需要确认上游已经结束才能返回空 Optional |
| `anyMatch(predicate)` | 终止操作 | 找到一个满足谓词的元素，即可返回 `true` |
| `allMatch(predicate)` | 终止操作 | 找到一个不满足谓词的元素，即可返回 `false` |
| `noneMatch(predicate)` | 终止操作 | 找到一个满足谓词的元素，即可返回 `false` |

`limit` 本身仍然是惰性的，需要终止操作驱动流水线。匹配类操作也不保证提前结束：例如有限流中的 `anyMatch` 始终不匹配，就需要等上游结束才能返回 `false`；对于永不结束的上游，它可能一直等待。

## 为什么 filter 后的 limit 可能一直等不到结果

下面使用 Java 17 的 `Stream`。标注无法正常结束的示例只用于分析，不应直接运行：

```java
// 无限产生 1，filter 永远无法向 limit 提供一个元素。
Stream.generate(() -> 1)
    .filter(n -> n > 1)
    .limit(3)
    .toList(); // 无法正常结束
```

`limit(3)` 不会在读取三个源元素后停止，而是等待至多三个通过过滤的元素。这里既得不到合格元素，也等不到上游结束，因此不能把结果判定为空列表。

调整操作顺序后可以结束，但语义也改变了：

```java
List<Integer> result = Stream.generate(() -> 1)
    .limit(3)
    .filter(n -> n > 1)
    .toList(); // []
```

前者表示“取前三个满足条件的元素”，后者表示“只检查前三个源元素，再保留符合条件的元素”。两者不能作为等价优化随意互换。

## 为什么 sorted 后的 limit 不能挽救无限输入

```java
Stream.generate(() -> 1)
    .sorted()
    .limit(3)
    .toList(); // 无法正常结束，并可能因不断缓冲而耗尽内存
```

这个输入没有已排序的特征声明，`sorted()` 需要接收全部上游元素后才能输出排序结果。即使实际生成值都是 1，排序阶段也不会据此推断未来输入，因而下游的 `limit` 始终得不到元素。

先限定有限输入再排序则可以结束：

```java
List<Integer> result = Stream.generate(() -> 1)
    .limit(3)
    .sorted()
    .toList(); // [1, 1, 1]
```

一般而言，`sorted().limit(n)` 表示“全局排序后取前 n 个”，`limit(n).sorted()` 表示“先取前 n 个，再只对这些元素排序”，业务含义不同。

## 判断能否结束的顺序

先看数据源是否有限，再看沿途操作能否持续向下游提供元素，最后看终止条件能否在有限步骤内确定。对无限输入而言，存在短路操作只是可能结束的必要条件，不是充分条件。

这些推理不等于承诺精确的回调次数，尤其不能据此推断并行流只会计算恰好需要的元素。短路也不是超时或中断机制，不能保证打断已经阻塞的回调。

## 参考

- [Java 17 Stream 包说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)：短路的定义、有状态操作，以及无限流终止的必要条件。
- [Java 17 Stream API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)：`limit`、`findFirst` 与匹配类终止操作的契约。
