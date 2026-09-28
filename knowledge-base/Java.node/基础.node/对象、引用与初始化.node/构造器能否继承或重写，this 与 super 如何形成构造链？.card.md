---
slug: java-constructor-chaining-this-and-super
tags: [Java基础, 构造器, 继承]
order: 30
relations:
  related: [java-default-constructor-and-instantiation-limits]
---

**构造器不能继承，也不能重写，但同一个类可以定义参数不同的多个构造器，也就是重载。** 子类想提供哪些构造入口，需要自己声明；调用父类构造器不等于继承它。

`this(...)` 调用当前类的另一个构造器，`super(...)` 调用直接父类的构造器。所谓“构造链”，就是创建对象时，这些构造器依次调用形成的一串调用。例如，子类无参构造器先调用子类有参构造器，后者再调用父类构造器。被调用的构造器返回后，调用它的构造器才继续执行后面的代码。整个过程共同初始化同一个对象。

## this(...) 和 super(...) 分别调用谁

这里讨论的是带括号的构造调用：

- `this(...)`：在当前类中，根据参数选择另一个构造器，复用它的初始化代码。
- `super(...)`：在直接父类中，根据参数选择构造器，让父类初始化它定义的那部分状态。

它们与 `this.name`、`super.someMethod()` 不同：后两者分别用于访问字段、调用父类的方法实现。

下面只保留一个名字字段和三个构造器，观察它们如何配合：

```java
class Parent {
    final String name;

    Parent(String name) {
        this.name = name;
        System.out.println("父类构造器");
    }
}

class Child extends Parent {
    Child() {
        this("小明");
        System.out.println("子类无参构造器");
    }

    Child(String name) {
        super(name);
        System.out.println("子类有参构造器");
    }
}
```

`Child()` 提供默认名字“小明”，把后续初始化交给 `Child(String)`；`Child(String)` 再把名字交给 `Parent(String)`，由父类给 `name` 字段赋值。这样，不同构造入口可以复用同一套初始化逻辑。

## new Child() 时，调用与返回的顺序是什么

执行 `new Child()`，先沿着构造调用向下进入：

```text
Child()
  → this("小明")：进入 Child(String)
      → super(name)：进入 Parent(String)
          → 隐式 super()：进入 Object()
```

`Parent(String)` 没有显式写构造调用，编译器会为它隐式补上 `super()`。它的直接父类是 `Object`，所以这条链最终到达 `Object()`。

接着，被调用的构造器完成并返回，外层构造器继续执行剩余代码：

1. `Object()` 返回后，`Parent(String)` 给 `name` 赋值，打印“父类构造器”。
2. `Parent(String)` 返回后，`Child(String)` 打印“子类有参构造器”。
3. `Child(String)` 返回后，`Child()` 打印“子类无参构造器”。

所以输出是：

```text
父类构造器
子类有参构造器
子类无参构造器
```

最先进入的是 `Child()`，它的打印却最后执行，因为它必须等 `this("小明")` 调用完成后才能继续。

这里**只创建了一个 `Child` 对象**。`this(...)` 没有创建第二个子类对象，`super(...)` 也没有额外创建一个父类对象；父类构造器给这个 `Child` 对象中由父类定义的 `name` 字段赋值。

## 调用父类构造器，为什么不算继承或重写

继承构造器意味着子类自动获得父类的构造入口，但 Java 没有这条规则。上例能写 `new Child("小红")`，是因为 `Child` 自己声明了 `Child(String)`，而不是因为 `Parent` 有一个 `Parent(String)`。

重写则是子类为继承来的实例方法提供新实现，调用时可以根据对象的实际类型选择实现。构造器不属于可继承的成员，也不参与这种方法调用。`Child(String)` 与 `Parent(String)` 即使参数相同，也各自负责自己的初始化步骤；`super(name)` 明确调用父类构造器，没有替换它的实现。

## Java 17 中，构造链有哪些限制

- **显式构造调用必须是第一条语句。** 一个构造器开头只能选择 `this(...)` 或 `super(...)`，不能先打印再调用，也不能依次写两者。选择 `this(...)` 时，由被调用的同类构造器继续衔接父类构造器。
- **省略调用时，隐式补的是无参 `super()`。** 父类必须有可访问的无参构造器，否则编译失败，需要显式选择合适的父类构造器。`Object` 自身没有父类，是这条规则的终点。
- **同类构造器之间不能循环调用。** 例如 `A() { this(1); }` 与 `A(int x) { this(); }` 相互调用，会在编译期被拒绝。

## 参考

- [JLS 17 §8.8：构造器不继承、不重写](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8)。
- [JLS 17 §8.8.7：构造器体与隐式 super()](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.7)。
- [JLS 17 §8.8.7.1：显式构造器调用](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.7.1)。
