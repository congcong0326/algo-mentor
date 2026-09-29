---
slug: java-protected-access-across-packages
tags: [Java基础, protected, 继承, 访问控制]
order: 25
relations:
  related: [java-encapsulation-and-access-control, java-inheritance-versus-composition, java-polymorphic-dispatch-and-static-binding]
---

**跨包访问父类的 `protected` 实例成员，必须发生在子类 `S` 的类体内；如果通过对象表达式访问，该表达式的编译期类型还必须是 `S` 或 `S` 的子类。** 因此，即使某个父类类型的引用实际指向 `S` 对象，也不能用这个父类类型的引用访问该成员。Java 在编译期按引用的类型检查访问权限，不会因为对象的运行期类型改变规则。

这条限制让跨包子类访问自己负责实现的对象，而不能借继承关系查看任意父类对象的内部状态。同包访问走另一条规则：声明成员的包内代码也可以访问 `protected` 成员，不要求是子类。

## 同一个对象，换成父类类型就无法访问

以下两个类分别位于不同的包；示例只用于说明访问规则，业务代码通常应保持字段私有，并通过有明确约束的方法提供扩展点。

```java
// domain/Progress.java
package domain;

public class Progress {
    protected int solved;
}
```

```java
// practice/PracticeProgress.java
package practice;

import domain.Progress;

public class PracticeProgress extends Progress {
    public int difference(PracticeProgress peer, Progress other) {
        int mine = this.solved;       // 合法：访问本子类对象
        int peers = peer.solved;      // 合法：peer 的编译期类型是 PracticeProgress

        Progress widened = peer;
        // int viaParent = widened.solved; // 编译错误：限定类型是 Progress
        // int viaOther = other.solved;    // 编译错误：限定类型是 Progress

        return mine - peers;
    }
}
```

`peer` 和 `widened` 指向同一个对象，但两处表达式的编译期类型不同。发生访问的代码位于 `PracticeProgress` 内，还只是满足了第一层条件；`widened.solved` 的限定类型是父类 `Progress`，不满足第二层条件。对 `protected` 实例方法，通过接收者调用时也适用这条限定类型规则。

## 为什么有两层限制

设成员声明在 `C` 中，访问代码位于不同包的子类 `S` 中。JLS 17 的规则可拆成两步：

1. 代码必须位于 `S` 的类体内，`S` 是 `C` 的子类。其他包里的普通代码不能仅凭一个 `C` 对象访问该成员。
2. 对带接收者的实例字段或实例方法访问，接收者的**限定类型**必须是 `S` 或其子类。`C` 类型的变量不合格；即使它在运行期碰巧指向 `S` 对象，结论也不变。

显式转换为 `S` 可以让表达式具有合格的编译期类型，但前提是对象确实属于 `S`；否则转换会在运行期抛出 `ClassCastException`。不应为了绕过访问边界而随意向下转型。若外部调用方需要读取进度，应由父类或业务类型提供合适的 `public` 操作，而不是依赖 `protected` 字段。

**不要把这条实例成员的限定类型规则套到所有 `protected` 声明上。** `protected` 静态成员和构造器另有访问细则；同包访问也不受上述跨包子类条件约束。

## 参考

- [JLS 17 §6.6.1：成员可访问性与同包规则](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6.1)。
- [JLS 17 §6.6.2.1：跨包访问 `protected` 成员](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6.2.1)：包含限定类型规则及跨包示例。
- [JLS 17 §6.6.2.2：`protected` 构造器](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6.2.2)：说明构造器与实例成员的访问规则不同。
