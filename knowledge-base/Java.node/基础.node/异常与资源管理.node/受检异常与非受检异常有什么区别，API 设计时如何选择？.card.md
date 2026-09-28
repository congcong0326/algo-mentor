---
slug: java-checked-unchecked-exceptions-api-design
tags: [Java基础, 受检异常, API设计]
order: 20
relations:
  related: [java-throwable-exception-error-hierarchy, java-overloading-versus-overriding]
---

**受检异常要求调用方在编译期捕获或在 throws 中声明；RuntimeException、Error 及其子类是非受检异常，不受这项强制检查。** 这里的“检查”指编译器检查异常处理责任，不表示异常在编译期发生，也不表示非受检异常不能被捕获。

API 选择应看失败是否属于调用方应显式面对的契约、是否存在可行的处理动作，以及强制传播是否有价值。可预期且需要调用方作出决定的失败可以使用受检异常；违反参数或对象状态前提的编程问题通常适合非受检异常。不能只凭“能否恢复”一句话机械分类。

## 同一个外部失败，调用方有两种合法责任分配

```java
static String readQuestion(java.nio.file.Path file)
        throws java.io.IOException {
    return java.nio.file.Files.readString(file);
}
```

这个 API 明确将读取失败交给上层。上层可以继续声明 IOException，也可以在有能力提示用户或选择其他文件的位置捕获它。如果既不捕获也不声明，代码不能编译。

参数校验则通常采用非受检异常：

```java
static int pages(int total, int pageSize) {
    if (total < 0 || pageSize <= 0) {
        throw new IllegalArgumentException("数量不能为负，分页大小必须为正");
    }
    return (int) (((long) total + pageSize - 1) / pageSize);
}
```

调用方无需为了每次调用而写 catch，但仍应理解并遵守前置条件。非受检不等于可以不记录、不处理或不测试。

## 如何决定是否放进 throws 契约

| 设计问题 | 倾向 |
| --- | --- |
| 调用方有明确备选动作，遗漏处理会造成明显问题 | 受检异常可以提醒必须作出处理或传播决定 |
| 参数非法、状态调用顺序错误、内部不变量失败 | 通常使用具体的非受检异常 |
| 中间层无法处理，强制一路声明只会产生空 catch 或无意义包装 | 重新考虑责任边界，或选用合适的非受检业务异常 |
| “没有找到题目”是普通查询结果 | 可以用 Optional 或显式结果类型，无须默认当成异常 |

不要通过继承 Error 来绕开受检约束。业务异常的基类选择应基于调用契约，而不是为了少写一段代码。

## 传播仍需保留原始原因

将受检异常转换为领域内非受检异常时，应保留 cause，例如 `new IllegalStateException("读取题目配置失败", cause)`，避免只复制 message 而丢失原始异常链。

接口方法若声明受检异常，重写实现可以缩小或省略它，却不能任意增加更宽的受检异常。这保证使用父接口的调用方不必面对声明之外的新受检责任。

## 参考

- [JLS 17 §11.1.1：受检与非受检分类](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.1.1)。
- [JLS 17 §11.2：编译期异常检查](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2)。
