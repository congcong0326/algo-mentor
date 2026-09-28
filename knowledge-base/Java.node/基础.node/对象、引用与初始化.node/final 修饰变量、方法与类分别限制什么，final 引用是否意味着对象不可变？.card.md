---
slug: java-final-variable-method-class-and-mutability
tags: [Java基础, final, 不可变性]
order: 60
relations:
  related: [java-static-versus-instance-members]
---

**final 修饰变量，表示赋值后不能再次赋值；修饰实例方法，表示子类不能重写；修饰类，表示不能再定义它的子类。** 这三种限制分别针对变量的值、方法的实现和类的继承关系。

final 引用不意味着对象不可变。引用变量保存的是“指向哪个对象”的引用值，final 只禁止替换这个值，不能阻止修改同一个对象的字段、数组元素或集合内容。对象是否不可变，要看它是否允许自己的状态在创建后发生变化，不能只看有没有 final。

## final 修饰不同位置，具体禁止什么

| 修饰位置 | 例子 | 禁止的操作 | 仍然允许的操作 |
| --- | --- | --- | --- |
| 基本类型变量 | `final int limit = 3;` | 再写 `limit = 5` 或 `limit++` | 读取它、参与计算 |
| 引用变量 | `final int[] scores = {60};` | 再写 `scores = new int[] {90}` 或 `scores = null` | 修改 `scores[0]` |
| 实例方法 | `final void solve()` | 子类重写这个方法 | 在可访问的前提下继承、调用它，也可定义参数不同的同名重载方法 |
| 类 | `final class Practice` | 声明 `class SpecialPractice extends Practice` | 定义构造器、创建对象，通过方法修改对象状态 |

final 方法不会因为“实现不能被子类替换”，就变成只读方法。例如，`final void solve()` 内部仍然可以执行 `solved++`。同样，final 类只是禁止继承，不会自动给它的字段加上 final。

对于静态方法，对应的限制是不能被子类的同签名静态方法隐藏。抽象方法要求子类提供实现，因此不能同时声明为 final。

## 为什么 final 引用仍能修改对象

看一个数组就能区分“给变量赋值”和“修改对象”：

```java
final int[] scores = {60};

scores[0] = 90;             // 合法：修改原数组里的元素
// scores = new int[] {90}; // 编译错误：给 scores 重新赋值
// scores = null;           // 编译错误：同样是重新赋值

int[] other = scores;
other[0] = 100;
System.out.println(scores[0]); // 100
```

`scores[0] = 90` 的赋值目标是数组元素，`scores = ...` 的赋值目标才是引用变量。final 修饰的是 `scores`，所以只禁止后者。

`other` 和 `scores` 指向同一个数组，通过 `other` 修改元素，`scores` 也会读到新值。这说明 final 既不会冻结对象，也不会让这个引用独占对象。

集合也是同样的规则：`final List<String> tags = new ArrayList<>()` 仍然可以执行 `tags.add("数组")`。方法参数声明为 final，也只是禁止在方法内给参数重新赋值，不能禁止通过它修改调用方传入的对象。

## final 变量一定要在声明时赋值吗

不一定。没有在声明处赋值的 final 变量叫“空白 final”。例如，实例字段可以由构造器赋值：

```java
class Target {
    private final int count;

    Target(int count) {
        this.count = count;
    }
}
```

编译器要能确定：每个正常完成的构造过程都为 `count` 赋值，而且不会重复赋值。不能把它留到对象创建后的普通 setter 中再赋值。静态的空白 final 字段则可以在静态初始化块中赋值；局部 final 变量可以先声明、后赋值，但读取前必须已确定赋值。

`new Target(3)` 和 `new Target(5)` 可以各自保存不同的值。final 限制的是每个变量后续能否重新赋值，static 才决定字段是否属于类并被实例共享。

## 怎样才能让对象的状态真正不可变

只把字段设为 `private final` 还不够。如果构造器把外部传入的可变列表直接保存下来，即使没有 setter，外部仍能通过原来的列表引用修改内容；如果 getter 又直接返回这个可变列表，还会增加一个修改入口。

下面用只包含字符串的学习计划说明如何关闭这些入口：

```java
import java.util.List;

final class StudyPlan {
    private final List<String> topics;

    StudyPlan(List<String> topics) {
        this.topics = List.copyOf(topics);
    }

    List<String> topics() {
        return topics;
    }
}
```

这里几个设计分别解决不同问题：

- `final class` 禁止子类通过继承改变这个类型的行为；`private final` 字段封装引用，并禁止后续替换。
- `List.copyOf` 得到不可修改的列表快照。调用方之后增删原列表，不会改变计划内容；通过 `topics()` 返回的列表增删或替换元素，会抛出 `UnsupportedOperationException`。
- 列表元素是不可变的 `String`，调用方也无法修改某个元素自身的内容。`List.copyOf` 不接受 null 列表或 null 元素。

如果元素改成可变的 `Task` 对象，`List.copyOf` 不会深拷贝这些对象，外部仍可能修改某个任务的状态。此时还需要使用不可变的元素类型，或在输入、输出边界复制可变对象，才能保证整个计划的可观察状态不变。

## 参考

- [JLS 17 §4.12.4：final 变量与常量变量](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4)。
- [JLS 17 §8.4.3.3：final 方法](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.3.3)。
- [JLS 17 §8.1.1.2：final 类](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.1.2)。
- [Java 17 List.copyOf：不可修改的列表快照](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))。
