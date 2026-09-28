---
slug: java-finally-return-throw-precedence
tags: [Java基础, finally, 控制流]
order: 30
relations:
  related: [java-pass-by-value, java-try-with-resources-primary-and-suppressed]
---

先执行 `try`；只有 `try` 抛出的异常匹配 `catch` 时，才转入对应的 `catch`。无论随后准备返回还是抛出异常，都要先执行 `finally`。**`finally` 正常结束，就保留此前的 return 或 throw；`finally` 自己执行 return 或 throw，就以它的结果为准，原本的返回或异常会被覆盖。**

`return` 的表达式在进入 `finally` 前已经求值，因此 `finally` 给局部变量重新赋值，不会改变已确定的返回值；若返回的是对象引用，修改该对象的状态仍可能影响调用方看到的内容。

## 先确定 try 或 catch 准备怎样结束

`try` 正常执行到 `return`，就准备返回；`try` 抛异常且匹配 `catch`，则以该 `catch` 后续的 return 或 throw 为准；没有匹配的 `catch`，异常就准备向外传播。之后才检查 `finally` 是否改变这个结果。

| try 或 catch 已准备的结果 | finally 的结束方式 | 方法最终结果 |
| --- | --- | --- |
| `return a` | 正常结束 | 返回 `a` |
| `throw e` | 正常结束 | 抛出 `e` |
| `return a` 或 `throw e` | `return b` | 返回 `b` |
| `return a` 或 `throw e` | `throw f` | 抛出 `f` |

`finally` 中的 return 可能吞掉原异常；`finally` 抛出新异常也会覆盖原异常。普通 `finally` 不会自动把原异常放进新异常的 `cause` 或 `suppressed`。因此通常避免在 `finally` 中写 return；清理操作可能抛异常时，要明确如何保留原异常。

## 返回值何时确定

```java
static int value() {
    int n = 1;
    try {
        return n;
    } finally {
        n = 2;
    }
}
// value() 返回 1。
```

执行 `return n` 时先求得 `1`，再运行 `finally`；重新给 `n` 赋值不会重新计算返回表达式。如果返回的是引用，规则相同：引用值已确定，但 `finally` 仍能通过这个引用修改所指对象。若返回表达式在求值时抛异常，就没有待返回值，该异常先按 `catch` 规则处理。

## finally 改写结果的例子

```java
static int replacedReturn() {
    try {
        throw new IllegalStateException("业务失败");
    } catch (IllegalStateException e) {
        return 1;
    } finally {
        return 2;
    }
}
// 返回 2：catch 准备返回 1，但 finally 的 return 覆盖了它。

static void replacedException() {
    try {
        throw new IllegalStateException("业务失败");
    } finally {
        throw new IllegalArgumentException("清理失败");
    }
}
// 向外抛出 IllegalArgumentException，原异常不会自动保留。
```

同一个 `try` 的 `catch` 只处理 `try` 中抛出的匹配异常，不会再捕获该语句自己的 `finally` 抛出的异常；后者需要外层处理。若业务执行和资源关闭都可能失败，`try-with-resources` 有专门的主异常与 suppressed 异常规则。

## 参考

- [JLS 17 §14.20.2：try-finally 的完成规则](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2)。
- [JLS 17 §14.17：return 表达式的求值](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.17)。
