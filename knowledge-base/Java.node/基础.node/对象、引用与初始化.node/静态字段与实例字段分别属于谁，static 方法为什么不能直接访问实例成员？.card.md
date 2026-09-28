---
slug: java-static-versus-instance-members
tags: [Java基础, static, 对象状态]
order: 40
---

**静态字段属于类，同一个运行时类只有一份；实例字段属于对象，每个对象都有自己的一份。** 例如，所有练习对象共用的目标题数可以放在静态字段中，而每次练习已经完成的题数应放在实例字段中。

实例方法通过某个对象调用，这个对象就是方法里的 `this`。static 方法不绑定某个对象，没有 `this`，所以直接写实例字段名或调用实例方法时，无法确定要操作哪个对象。它可以通过明确的对象引用访问实例成员，例如 `practice.solved`；关键是有没有指定对象，而不是代码是否写在 static 方法里。

## 字段“属于类”与“属于对象”，实际有什么区别

用一个简化的练习类表示共享目标和各自的进度：

```java
class Practice {
    static int target = 3; // 所有练习对象共用的目标题数
    int solved = 0;        // 当前练习对象已完成的题数

    void solve() {
        this.solved++;
    }
}
```

创建两个对象，只给其中一个增加进度：

```java
Practice a = new Practice();
Practice b = new Practice();
a.solve();

System.out.println(a.solved);         // 1
System.out.println(b.solved);         // 0
System.out.println(Practice.target);  // 3

Practice.target = 5;
```

最后一行执行后，字段的状态是：

| 字段 | 状态归属 | 当前值 |
| --- | --- | --- |
| `Practice.target` | 类维护的一份共享状态 | `5` |
| `a.solved` | 对象 `a` 自己的状态 | `1` |
| `b.solved` | 对象 `b` 自己的状态 | `0` |

创建 `b` 不会再复制一份 `target`；修改共享目标后，两个对象后续读取的都是同一个静态字段。修改 `a.solved` 则不会改变 `b.solved`，因为它们是两个不同的字段变量。

即使还没有创建任何 `Practice` 对象，也可以使用 `Practice.target`。静态字段不依附于某个实例。

## 实例方法为什么能直接写 solved

调用 `a.solve()` 时，`solve()` 中的 `this` 就是 `a`；调用 `b.solve()` 时，`this` 就是 `b`。

所以上例中的 `this.solved++`，会随着调用对象不同，修改不同对象的字段。如果方法里没有同名局部变量或参数遮蔽字段，也可以简写成 `solved++`。虽然没有写出 `this`，操作的仍是当前对象的字段。

实例方法也可以读取 `Practice.target`：访问静态字段只需要确定所属类，不需要另外寻找一个对象。

## static 方法缺少的是当前对象，不是访问实例成员的能力

假设在 `Practice` 中加入下面的方法：

```java
static int solvedCount() {
    return solved; // 编译错误：这里没有 this，无法确定读取哪个对象的进度
}
```

调用 `Practice.solvedCount()` 没有指定 `a` 或 `b`。即使它们都已经创建，方法也不会自动选一个对象来读取。把代码改成 `return this.solved;` 同样无效，因为 static 方法没有 `this`。

如果确实需要查询某个对象，就把这个对象明确传进来：

```java
static int solvedCount(Practice practice) {
    return practice.solved;
}
```

这时 `Practice.solvedCount(a)` 返回 `1`，`Practice.solvedCount(b)` 返回 `0`。同理，在满足访问权限且引用非空时，也可以在 static 方法中写 `practice.solve()`，调用指定对象的实例方法。

因此，“静态方法执行得早，实例还没创建”不能解释这个限制。**即使对象已经存在，static 方法也没有隐含的当前对象；一旦明确指定对象，就可以访问它的实例成员。**

## 根据状态归属选择 static

如果每个对象都应该保存自己的值，就用实例字段。把上例的 `solved` 改成 static，会让一次练习的进度变化影响其他练习，失去各自独立记录的含义。示例中的 `target` 刻意设成统一目标；如果实际需求允许每次练习设置不同目标，它也应改为实例字段。

方法如果主要操作当前对象的状态，通常写成实例方法，例如 `a.solve()`。如果计算只依赖显式传入的参数，不需要某个对象作为当前实例，可以考虑 static 方法。不能仅为了消除“无法访问实例字段”的编译错误，就把字段一起改成 static，因为这会改变状态的归属。

日常代码用 `类名.静态成员` 表达归属。虽然 Java 允许通过对象表达式访问静态成员，但这不会让静态方法获得 `this`，也不会把静态字段变成该对象独有的字段。

这里的“一份”限定于同一个运行时类：同名类由不同的定义类加载器加载时，可以各自拥有静态字段，不能将其理解为跨进程、跨类加载器的全局唯一状态。

## 参考

- [JLS 17 §8.3.1.1：静态字段](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3.1.1)。
- [JLS 17 §8.4.3.2：静态方法](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.3.2)。
