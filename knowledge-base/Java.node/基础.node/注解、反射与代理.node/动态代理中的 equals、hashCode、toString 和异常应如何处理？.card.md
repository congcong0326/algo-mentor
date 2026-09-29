---
slug: java-dynamic-proxy-object-methods-and-exceptions
tags: [Java基础, 动态代理, 对象契约, 异常]
order: 90
relations:
  prerequisites: [java-jdk-dynamic-proxy-invocation-handler]
  related: [java-jdk-interface-proxy-vs-subclass-proxy-limits]
---

JDK 动态代理不仅把接口方法交给 `InvocationHandler`，也会把代理对象上的 `equals`、`hashCode`、`toString` 交给它。处理器应为这三个方法确定一致的对象语义；直接转发给目标对象可能使 `proxy.equals(proxy)` 返回 `false`。若采用代理对象的身份语义，可用 `proxy == other` 判断相等，并以 `System.identityHashCode(proxy)` 计算哈希值。

异常也要分两层处理：`Method.invoke` 会把目标方法抛出的异常包在 `InvocationTargetException` 中，转发型处理器应取出原始原因；随后代理按接口方法的 `throws` 声明检查从处理器抛出的异常。未声明的受检异常会被包成 `UndeclaredThrowableException`，运行时异常和 `Error` 则可直接传播。

## 为什么不能盲目转发 Object 方法

代理类会将 `equals`、`hashCode`、`toString` 这三个可重写的 `Object` 方法交给处理器，传入的 `Method` 由 `Object` 声明。`getClass` 等 `final` 方法不会走这条路径。

假设目标对象沿用 `Object.equals` 的身份比较。若处理器把 `proxy.equals(proxy)` 转发成 `target.equals(proxy)`，结果是 `false`，违反自反性。只改 `equals` 而不相应处理 `hashCode`，还可能破坏相等对象必须有相同哈希值的约定；把代理放进 `HashSet` 或用作 `HashMap` 键时，问题就会显现。代理可以选择与目标对象共享业务相等性，但必须同时设计双方的比较与哈希规则，不能简单地把三个方法一律转发。

## 一个采用代理身份语义的处理器

```java
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ProxyBehaviorDemo {
    public interface ProblemService {
        String title(long id) throws IOException;
    }

    static class DefaultProblemService implements ProblemService {
        public String title(long id) throws IOException {
            if (id < 0) throw new IOException("题目编号无效");
            return "题目 " + id;
        }
    }

    public static void main(String[] args) throws IOException {
        ProblemService target = new DefaultProblemService();
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "ProblemServiceProxy@"
                            + Integer.toHexString(System.identityHashCode(proxy));
                    default -> throw new IllegalStateException(method.getName());
                };
            }
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("无法调用目标方法", e);
            }
        };

        ProblemService service = (ProblemService) Proxy.newProxyInstance(
                ProblemService.class.getClassLoader(),
                new Class<?>[] {ProblemService.class}, handler);

        System.out.println(service.equals(service)); // true
        System.out.println(service.hashCode() == System.identityHashCode(service)); // true
        System.out.println(service.title(42)); // 题目 42
        try {
            service.title(-1);
        } catch (IOException e) {
            System.out.println(e.getClass().getSimpleName()); // IOException
        }
    }
}
```

示例把 `IllegalAccessException` 转成运行时异常，因为它是处理器反射转发失败，不是 `ProblemService.title` 对调用方声明的业务异常。处理 `equals` 时用 `==`，不会再次调用代理的 `equals` 而递归进入处理器。

## 异常最终怎样到达调用方

- 目标方法抛出 `IOException`：`Method.invoke` 先包装为 `InvocationTargetException`；处理器取出原因。接口方法声明了 `IOException`，调用方最终收到原始的 `IOException`。
- 目标方法抛出运行时异常或 `Error`：反射调用同样先包装；取出原因后，代理允许其直接传播。
- 处理器抛出接口方法未声明的受检异常：代理会抛出 `UndeclaredThrowableException`，调用方可通过 `getUndeclaredThrowable()` 取得原异常。若接口未声明 `InvocationTargetException`，处理器把这个反射包装异常原样抛出也会出现这种结果。

处理器的返回值还必须符合接口方法的返回类型；例如返回 `null` 给基本类型返回值会导致拆箱失败。代理不仅改变了调用入口，也承担了保持接口契约的责任。

## 参考

- [Java 17 Proxy API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html)。
- [Java 17 InvocationHandler API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html)。
- [Java 17 Method.invoke API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))。
- [Java 17 UndeclaredThrowableException API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html)。
