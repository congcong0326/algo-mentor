---
slug: java-try-with-resources-primary-and-suppressed
tags: [Java基础, suppressed, 异常保留]
order: 60
relations:
  related: [java-try-with-resources-order-and-initialization-failure, java-finally-return-throw-precedence]
---

**try-with-resources 在业务主体抛出异常后，会保留该异常作为向外传播的主异常，把关闭资源时抛出的异常加入它的 suppressed 列表。** 如果主体正常结束，则逆序关闭中第一个抛出的异常成为主异常，后续关闭失败加入它的 suppressed 列表。

资源初始化中途失败时，初始化异常是主异常，关闭此前已成功初始化资源时发生的异常被附加为 suppressed。suppressed 表示并列操作中被保留下来的其他失败，不等于 cause 所表达的异常因果链。

## 业务失败与两个关闭失败同时保留

```java
class BrokenResource implements AutoCloseable {
    private final String name;
    BrokenResource(String name) { this.name = name; }

    @Override
    public void close() throws java.io.IOException {
        throw new java.io.IOException("close-" + name);
    }
}
```

在方法中执行：

```java
try (BrokenResource a = new BrokenResource("A");
     BrokenResource b = new BrokenResource("B")) {
    throw new IllegalStateException("body");
} catch (Exception e) {
    System.out.println(e.getMessage());
    for (Throwable suppressed : e.getSuppressed()) {
        System.out.println(suppressed.getMessage());
    }
}
```

输出：

```text
body
close-B
close-A
```

先关闭 B，再关闭 A；两次失败都没有覆盖业务异常。示例使用标准异常，未禁用 suppression。

## 主异常如何确定

| 情况 | 主异常 | suppressed |
| --- | --- | --- |
| 主体抛异常，关闭也失败 | 主体异常 | 按关闭发生顺序记录关闭异常 |
| 主体正常，B 和 A 依次关闭失败 | B 的关闭异常 | A 的关闭异常 |
| B 初始化失败，A 关闭也失败 | B 的初始化异常 | A 的关闭异常 |
| 主体准备 return，但关闭失败 | 首个关闭异常，原 return 不再完成 | 后续关闭异常 |

“主体没有 throw”不等于整个语句成功，close 也是可能失败的操作。若外层又编写一个抛出新异常的 finally，仍可能覆盖已经整理好的主异常，try-with-resources 不会保护它免受后续控制流覆盖。

## suppressed 与 cause 是不同关系

把 IOException 包装成业务异常时，可用 `new IllegalStateException("读取失败", cause)` 表示原因。读操作和 close 各自失败，则不是 close 导致了读失败，应通过 suppressed 同时保留。

排查时不能只打印 `e.getMessage()`；完整堆栈输出通常会包含 cause 和 suppressed。示例逐个打印仅为展示顺序，生产日志应在合适边界保留异常对象，避免丢失这些信息。

Throwable 允许特殊子类通过构造器禁用 suppression，此时 getSuppressed 可以为空。普通 finally 也不会自动执行上述合并规则；这是 try-with-resources 的专门语义。

## 参考

- [JLS 17 §14.20.3.1：主异常与关闭异常规则](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.1)。
- [Java 17 Throwable.addSuppressed 与 getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#addSuppressed(java.lang.Throwable))。
