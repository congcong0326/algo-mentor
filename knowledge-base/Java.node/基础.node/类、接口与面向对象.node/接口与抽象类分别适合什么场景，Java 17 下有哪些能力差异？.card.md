---
slug: java-interface-versus-abstract-class
tags: [Java基础, 接口, 抽象类]
order: 50
relations:
  related: [java-polymorphic-dispatch-and-static-binding, java-overloading-versus-overriding]
---

**接口适合定义不同类型都能实现的能力契约，抽象类适合为一组有共同基础的类型共享实例状态、构造约束和实现。** 类可以实现多个接口，却只能直接继承一个类；采用抽象类会占用这条类继承关系。

Java 17 的接口不只有抽象方法：它还可以有 default、static 和 private 方法，但没有实例字段和构造器。抽象类可以有实例字段、构造器、抽象方法和具体方法，且可以使用更丰富的访问控制。

## 按 Java 17 比较能力

| 维度 | 接口 | 抽象类 |
| --- | --- | --- |
| 状态 | 字段隐式为 `public static final`；不能声明每个实现对象各自的字段 | 可以有实例字段与静态字段，包括可变状态 |
| 构造 | 没有构造器；不能直接 new 接口 | 可以有构造器，由子类构造链调用；不能直接 new 抽象类 |
| 方法 | public 抽象方法、public default 方法、public 或 private static 方法、private 实例方法 | 抽象或具体方法，允许 public、protected、包访问、private 等相应合法组合 |
| 继承 | 接口可继承多个接口，类可实现多个接口 | 类只能直接继承一个类，抽象类也可实现多个接口 |
| 复用方式 | default 实现可复用行为，private 方法用于接口内部辅助 | 可共享行为、状态及初始化规则 |

default 和 static 接口方法从 Java 8 开始支持，private 接口方法从 Java 9 开始支持。private 方法不被实现类继承或重写；接口的 static 方法通过接口名调用，也不被实现类继承。

## 能力契约与共享骨架可以一起使用

```java
interface Scorer {
    int score(int solved);

    default boolean passed(int solved) {
        return score(solved) >= 60;
    }
}

abstract class WeightedScorer implements Scorer {
    protected final int pointsPerProblem;

    protected WeightedScorer(int pointsPerProblem) {
        if (pointsPerProblem <= 0) {
            throw new IllegalArgumentException("每题分值必须为正");
        }
        this.pointsPerProblem = pointsPerProblem;
    }
}

class LinearScorer extends WeightedScorer {
    LinearScorer() { super(10); }

    @Override
    public int score(int solved) {
        return Math.multiplyExact(solved, pointsPerProblem);
    }
}
```

调用方依赖 Scorer；需要共享分值状态和构造校验的实现可以继承 WeightedScorer。其他实现仍可直接实现 Scorer，无须进入这个类继承体系。生产代码还应按业务约束验证 solved 的范围。

## default 方法不是多重状态继承

当两个互不继承的接口提供同签名 default 方法，实现类通常必须显式重写以消解冲突；更具体的子接口定义和类层次中的方法声明也会影响选择，不能靠接口声明顺序决定。

接口字段虽然是 final 引用，若指向可变容器，容器内容仍可能被修改。它既不是实例状态，也不天然是不可变常量。需要多个可组合行为但没有稳定“是一种”关系时，优先考虑接口配合组合，不必为了复用几行代码引入抽象父类。

## 参考

- [JLS 17 §9.3：接口字段](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.3)。
- [JLS 17 §9.4：接口方法](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4)。
- [JLS 17 §8.1.1.1：抽象类](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.1.1)。
