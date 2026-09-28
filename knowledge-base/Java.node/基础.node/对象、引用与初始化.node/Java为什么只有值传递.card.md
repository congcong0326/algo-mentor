---
slug: java-pass-by-value
tags: [Java基础, 对象与引用]
order: 10
---

Java 只有值传递，因为调用方法时，形参是新建的独立变量，接收的是实参值的副本。基本类型复制的是数值等基本类型值，引用类型复制的是引用值，不会复制对象本身。

因此，方法内外的两个引用可以指向同一个对象，通过形参修改这个对象的状态，调用方也能看到变化。但给形参重新赋值，只会改变形参自己的值，不能让调用方的变量改为指向另一个对象。**共享对象，不等于共享变量。**

从设计取舍看，这让参数传递规则保持统一，也限制了方法通过形参改写调用方变量的能力；共享和修改对象又不需要复制整个对象。C++ 的引用参数、C# 的 `ref` 参数则提供了额外能力：形参可以成为调用方变量的别名，直接修改那个变量。

## 值传递与引用传递的区别在哪里

区别在于形参是否成为调用方变量的别名：值传递创建独立变量并复制值；引用传递则让形参直接代表调用方的变量，对形参赋值就能改写那个变量。Java 采用前一种规则，引用类型参数也不例外。

以基本类型为例，调用 `change(number)` 时，复制的是 `number` 当时的值 `1`：

```java
static void change(int value) {
    value = 2;
}

// 调用处
int number = 1;
change(number);
System.out.println(number); // 1
```

`number` 和 `value` 是两个变量。`value = 2` 只给形参赋值，不会改写 `number`。

## 为什么能修改对象，却不能替换调用方的引用

数组也是对象，适合用来同时观察这两种操作：

```java
static void change(int[] values) {
    values[0] = 2;
    values = new int[] {3};
    values[0] = 4;
}

// 调用处
int[] numbers = {1};
change(numbers);
System.out.println(numbers[0]); // 2
```

这次调用没有复制数组，而是复制了指向数组的引用值。按执行顺序看：

| 操作 | 形参与调用方变量的关系 | 结果 |
| --- | --- | --- |
| 进入方法 | `values` 和 `numbers` 指向同一个数组 | 原数组内容为 `{1}` |
| `values[0] = 2` | 通过 `values` 修改共享数组的元素 | `numbers[0]` 也读到 `2` |
| `values = new int[] {3}` | 只有 `values` 改为指向新数组 | `numbers` 仍指向原数组 |
| `values[0] = 4` | 修改的是新数组 | 原数组仍为 `{2}` |

关键是赋值目标不同：`values[0] = ...` 写入数组元素，`values = ...` 写入形参变量。普通对象的字段修改与形参重新赋值，也遵循同样的规则。

## Java 为什么不提供引用传递

这是语言设计的选择。规范规定了传参规则；从现有语义看，统一采用值传递有三个直接好处：

- **规则统一。** 基本类型和引用类型都遵循“用实参值初始化独立形参”的规则，无需再区分普通参数与可改写调用方变量的引用参数。
- **变量的修改范围更容易判断。** 把局部变量 `x` 传给方法后，方法不能仅通过给形参赋值来改写 `x`。如果需要用新结果替换 `x`，通常由调用方写出 `x = method(x)`，替换动作是显式的。
- **共享对象已经足够高效。** 传对象时只复制引用值，不复制整个对象。方法仍能访问和修改共享对象，因此无需为了避免对象复制，再引入一种引用传递机制。

代价是 Java 不能直接提供交换两个调用方变量的 `swap(a, b)`，也没有 C# `out` 那样的输出参数。通常用返回值表达结果；多个结果可以封装成对象或 record。引用传递在这类场景有用，只是会增加变量别名和修改来源，调用者需要理解更多规则。

这个限制也不代表方法没有副作用：共享对象的字段、数组元素仍然可以被修改。值传递限制的是**通过形参赋值改写调用方变量**，并不保证对象不可变。

## 哪些语言支持引用传递，与 Java 有什么不同

应当比较具体的参数声明，而不是把一门语言笼统地归为“值传递语言”或“引用传递语言”。C++ 和 C# 都支持不止一种方式：

| 语言与参数声明 | 形参代表什么 | 给形参赋值能否改写调用方变量 |
| --- | --- | --- |
| Java：`void change(int value)` | 独立的基本类型变量 | 不能 |
| Java：`void change(Person value)` | 独立的引用变量，保存引用值的副本 | 不能，但可以修改共享对象的状态 |
| C++：`void change(int value)` | 独立的值参数 | 不能 |
| C++：`void change(int& value)` | 调用方变量的别名 | 能 |
| C#：`void Change(int value)` | 独立的值参数 | 不能 |
| C#：`void Change(ref int value)` | 调用方变量的别名 | 能，调用时也要写 `ref` |

### C++ 的引用参数可以直接交换外部变量

Java 中，下面的方法只交换两个形参的值：

```java
static void swap(int a, int b) {
    int temp = a;
    a = b;
    b = temp;
}

// 调用处
int x = 1, y = 2;
swap(x, y); // x 仍为 1，y 仍为 2
```

C++ 把参数声明为 `int&`，同样的赋值操作就能交换外部变量：

```cpp
void swap(int& a, int& b) {
    int temp = a;
    a = b;
    b = temp;
}

// 调用处
int x = 1, y = 2;
swap(x, y); // x 为 2，y 为 1
```

这里 `a` 是 `x` 的别名，`b` 是 `y` 的别名，`a = b` 实际上就是把 `y` 的值写入 `x`。如果去掉参数类型中的 `&`，就变成值传递，无法交换外部变量。

Java 即使把参数改成 `Person`，也只能交换两个局部引用的副本。传入的是引用类型，并不会自动获得 C++ 引用参数的语义。

### C# 可以显式选择引用传递

```csharp
static void Change(ref int value) {
    value = 2;
}

// 调用处
int number = 1;
Change(ref number); // number 变为 2
```

C# 的普通参数默认按值传递；对于类实例，默认复制的也是对象引用。使用 `ref Person` 时，形参才成为调用方引用变量的别名，方法内给形参赋一个新对象，就能让调用方变量也改为指向新对象。C# 还提供 `out` 输出参数；`ref` 要求调用前变量已赋值，`out` 则要求方法在正常返回前完成赋值。

## 规范依据

- [JLS 17 §8.4.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1)：实参表达式的值用于初始化新创建的形参变量。
- [C++ 标准草案：References](https://eel.is/c++draft/dcl.ref)：引用参数及通过引用修改调用方变量的示例。
- [Microsoft C# 文档：Method parameters and modifiers](https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/keywords/method-parameters)：默认值传递，以及 `ref`、`out` 等参数修饰符。
