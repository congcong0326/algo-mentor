---
slug: java-when-finally-does-not-execute
tags: [Java基础, finally, 进程终止]
order: 40
relations:
  related: [java-finally-return-throw-precedence]
---

**finally 不是进程终止时必然执行的保障。若在执行到 finally 之前 JVM 已被终止，或进程、机器已无法继续运行，finally 就可能没有执行机会。** 例如 System.exit 正常终止 JVM、Runtime.halt 强制终止 JVM、操作系统强制结束进程或机器掉电。

另一个边界是控制流始终没有离开 try 或 catch，例如无限循环或一直阻塞，此时 finally 尚无执行机会。普通未捕获异常即使最终导致当前线程结束，在线程退出前仍会按语言规则执行经过的 finally；没有 catch 住异常并不是跳过 finally 的一般原因。

## 原文的进程终止反例

以下代码只能作为独立进程示例理解；System.exit 会结束所在 JVM：

```java
public class ExitExample {
    public static void main(String[] args) {
        try {
            System.out.println("Try to do something");
            throw new RuntimeException("RuntimeException");
        } catch (Exception e) {
            System.out.println("Catch Exception -> " + e.getMessage());
            System.exit(1);
        } finally {
            System.out.println("Finally");
        }
    }
}
```

在退出请求成功执行的普通环境下输出：

```text
Try to do something
Catch Exception -> RuntimeException
```

不会因为存在 finally 就在 JVM 退出前自动打印 `Finally`。这里的前提是退出请求确实终止了 JVM，不能把“源码调用了 exit”与“进程已经退出”混为一谈。

## 未捕获异常的情况不同

```java
try {
    throw new IllegalStateException("失败");
} finally {
    System.out.println("执行清理");
}
```

在普通异常传播过程中，即使没有 catch，仍先执行 finally，再向外传播异常。return 也同样不会自动跳过 finally。

Runtime.halt、操作系统强制终止以及机器掉电等情况，已经超出普通异常传播能够确保清理的前提。finally 适合语言控制流内的收尾，不能提供进程或机器故障下的必达保证。

## 尚未进入与进入后中断是两回事

```java
static void neverLeaves() {
    try {
        while (true) {
            // 示意永不退出的计算，不要作为普通演示执行。
        }
    } finally {
        System.out.println("不会到达这里");
    }
}
```

这里 JVM 仍可运行，但 try 没有正常结束，也没有以 return、throw 等方式离开，finally 就还没有开始。对于会被唤醒或抛出异常的阻塞调用，只能说“阻塞期间尚未进入”，不能直接断言永远不执行。

另一种情况是 finally 已进入，却在中途抛异常、阻塞或遭遇进程终止，后面的清理语句无法继续。这不同于整个 finally 被跳过。多个资源需要可靠地依次尝试关闭时，应采用 try-with-resources 等有明确关闭与异常保留规则的结构，不能认为把 close 调用依次放进 finally 就都能执行到。

## 参考

- [JLS 17 §14.20.2：未捕获异常与 finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2)。
- [Java 17 System.exit](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#exit(int))。
- [Java 17 Runtime.halt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html#halt(int))。
