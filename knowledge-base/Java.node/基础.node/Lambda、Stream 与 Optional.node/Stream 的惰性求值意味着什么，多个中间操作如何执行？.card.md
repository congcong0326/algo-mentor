---
slug: java-stream-lazy-evaluation-and-fusion
tags: [Java基础, Stream, 惰性求值]
order: 20
relations:
  prerequisites: [java-stream-pipeline-intermediate-terminal]
---

**Stream 的惰性求值是指：调用中间操作时只构建处理阶段，不立即遍历源元素或执行对应的筛选、映射；终止操作通常驱动实际计算。** 声明了 `filter`、`map`，不代表它们的回调已经执行。

**多个中间操作不必逐步生成完整集合。** 对顺序流中的 `filter`、`map` 等无状态操作，可以把处理融合到一次遍历中：一个元素先经过筛选，通过后立即进入映射，再处理下一个元素。被筛掉的元素不进入下游。

惰性不等于不需要缓冲。有状态操作可能依赖其他元素；例如对尚未有序的输入进行 `sorted`，需要先接收全部上游元素才能输出排序结果，但这项工作仍是在求值时发生，而不是调用 `sorted()` 时发生。

## 声明流水线与执行流水线是两件事

下面以题目标题为例，使用 Java 17，省略 `java.util.List` 和 `java.util.stream.Stream` 的导入：

```java
List<String> titles = List.of("  两数之和  ", "   ", " 二分查找 ");

Stream<String> pipeline = titles.stream()
    .map(String::trim)
    .filter(title -> !title.isEmpty())
    .map(title -> "待练：" + title);

// 此时尚未对源元素执行 trim、判空或前缀拼接。
List<String> result = pipeline.toList();

System.out.println(result); // [待练：两数之和, 待练：二分查找]
```

对于这里的顺序流水线，可以按下面的元素流向理解处理过程：

| 源元素 | `trim` | 判空过滤 | 添加前缀 |
| --- | --- | --- | --- |
| `"  两数之和  "` | `"两数之和"` | 通过 | `"待练：两数之和"` |
| `"   "` | `""` | 丢弃 | 不执行 |
| `" 二分查找 "` | `"二分查找"` | 通过 | `"待练：二分查找"` |

可以逐行理解这张表，而不是先对全部元素完成第一列处理，再为第二列创建一个完整集合。把各阶段衔接到一次遍历中称为操作融合，它避免了每个中间步骤都保存一份完整结果。

这里的 `toList()` 仍需要保存最终结果；减少中间集合不意味着整条流水线没有内存开销。

## 无状态操作与有状态操作的区别

这里的“状态”指操作是否需要记住或等待其他元素的信息，不是指 Lambda 能否捕获外部变量。

| 操作 | 是否需要跨元素信息 | 求值时的特点 |
| --- | --- | --- |
| `filter`、`map` | 不需要，操作本身可独立处理当前元素 | 可以与相邻阶段融合，逐元素向下游传递 |
| `distinct` | 需要判断元素是否已经出现 | 顺序流中通常记录已见元素，首次出现即可向下游传递 |
| `sorted` | 排序通常需要全部上游元素 | 对尚未有序的输入先缓冲和排序，再向下游传递 |

例如：

```java
List<Integer> result = Stream.of(3, 1, 2)
    .filter(n -> n > 1)
    .sorted()
    .map(n -> n * 10)
    .toList();

System.out.println(result); // [20, 30]
```

求值时，过滤后的 `3`、`2` 先进入排序阶段。排序阶段收齐上游元素后，按 `2`、`3` 的顺序交给下游映射。因此，不能把所有流水线都理解成“第一个元素一路走到终点后，才处理第二个元素”。也不能反过来认为所有有状态操作都必须等待全部输入，`distinct` 就不必一概如此。

## 惰性的边界

**延后的是流元素的处理，不是整条 Java 表达式的求值。** 调用中间操作时，方法参数仍按 Java 的正常规则计算：`stream.filter(createPredicate())` 中，`createPredicate()` 会立即执行以构造谓词，谓词对各元素的判断才延后。参数校验也可能立即发生，`filter(null)` 不需要等到终止操作才报错。

操作融合是理解执行方式的模型，不是回调次数或副作用顺序的通用保证。实现可以省略不影响结果的阶段，短路也可能减少处理量；并行流则不能套用这里逐元素串行执行的示意。业务正确性不应依赖在 `map`、`filter` 中计数、发消息等副作用。

## 参考

- [Java 17 Stream 包说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)：惰性求值、操作融合，以及无状态和有状态中间操作的执行特点。
- [Java 17 Stream API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)：流水线优化与行为参数的非 null 要求。
