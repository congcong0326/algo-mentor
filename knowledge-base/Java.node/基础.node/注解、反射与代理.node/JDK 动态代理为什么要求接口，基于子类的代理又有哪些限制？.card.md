---
slug: java-jdk-interface-proxy-vs-subclass-proxy-limits
tags: [Java基础, 动态代理, JDK代理, 类代理]
order: 80
relations:
  prerequisites: [java-jdk-dynamic-proxy-invocation-handler]
  related: [java-static-versus-dynamic-proxy]
---

JDK 的 `Proxy` API 根据传入的**接口**生成代理类：代理类继承 `Proxy`，实现这些接口，并把接口方法调用交给 `InvocationHandler`。它不能同时继承某个目标类，因此代理对象可以当作接口使用，却不是目标实现类的实例。这是 JDK 这套代理 API 的设计边界，不是 Java 在运行时完全不能为类做代理。

基于子类的代理则继承目标类，通过覆盖可重写的实例方法拦截调用。它能暴露类本身的方法，但不能代理 `final` 类；`final`、`private` 方法无法被覆盖，`static` 方法也不通过实例方法的动态分派进入代理。选择时既要看调用方使用接口还是具体类，也要看需要增强的方法能否被子类覆盖。

## 两种代理的拦截位置

JDK 接口代理在**接口实现方法**处拦截：调用方拿到的是实现接口的另一个对象，处理器可以再调用目标对象。目标类自身是否可继承，不决定接口代理能否拦截。

类代理在**目标类方法的重写版本**处拦截：调用方可以按目标类类型使用代理，但只有能被子类重写的方法才能经过这层拦截。`private` 方法不能被子类重写；`static` 方法属于类，不靠实例的动态分派选择重写版本。若目标类是 `final`，连代理子类都无法创建。

下面的类型声明可以帮助区分两个边界：

```java
interface ProblemService {
    void submit();
}

class DefaultProblemService implements ProblemService {
    @Override
    public final void submit() { /* 提交题目 */ }

    public void refreshCache() { /* 刷新缓存 */ }
}
```

- JDK 代理可以实现 `ProblemService`，拦截经代理进入的 `submit()`，再决定是否转发给目标对象。即使目标对象的 `submit()` 是 `final`，接口代理仍能先收到这次调用，因为它没有覆盖目标类的方法。
- JDK 代理不是 `DefaultProblemService` 的实例，不能强转为该类，也没有 `refreshCache()` 这个接口外的方法。
- 基于 `DefaultProblemService` 创建的子类代理可以暴露 `refreshCache()`，但不能通过覆盖 `final` 的 `submit()` 来增强它。若把整个类声明为 `final`，子类代理也无法建立；接口代理仍可使用它实现的 `ProblemService`。

## 放到 Spring AOP 中怎样判断

Spring AOP 可以使用 JDK 接口代理，也可以使用基于类的代理。若组件只按 `ProblemService` 注入和调用，接口代理能覆盖接口入口；若调用方必须按 `DefaultProblemService` 类型使用代理，接口代理的类型就不满足要求。强制使用类代理也不能解决 `final` 方法无法重写的问题。

排查“注解写了却未增强”时，先确认调用方持有的是哪个代理类型、调用的方法是否经过代理对象，再检查类代理能否覆盖该方法。即使某种代理技术能拦截该方法，目标对象内部直接调用自身方法也不会再次经过外层代理；这是调用路径的问题，与接口代理或子类代理的选择不同。

## 参考

- [Java 17 Proxy API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html)。
- [Java 17 Java 语言规范 §8.1.1.2：final 类](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.1.2)。
- [Java 17 Java 语言规范 §8.4.3.3：final 方法](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.3.3)。
- [Spring Framework：代理机制](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)。
