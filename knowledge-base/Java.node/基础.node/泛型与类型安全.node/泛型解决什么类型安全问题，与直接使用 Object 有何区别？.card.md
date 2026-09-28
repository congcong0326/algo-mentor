---
slug: java-generics-versus-object-type-safety
tags: [Java基础, 泛型, 类型安全]
order: 10
---

**泛型把某类数据的类型约束写入 API，让编译器在调用处检查输入和输出之间的类型关系，并减少调用方的显式强制转换。** 直接用 Object 可以接收各种引用值，却不能表达“放进去的值和取出来的值属于同一个指定类型”。

Java 的泛型检查主要发生在编译期；运行时类型擦除不意味着这些检查没有价值。它也不是万能的运行时容器校验：原始类型、未经检查的转换等仍可能绕过约束，直到取值时才暴露错误。

## Object 容器将风险推迟到读取时

```java
class ObjectBox {
    private Object value;
    void set(Object value) { this.value = value; }
    Object get() { return value; }
}

// 方法内：
ObjectBox box = new ObjectBox();
box.set(42);                       // 编译器允许
String title = (String) box.get();  // 运行时 ClassCastException
```

Object 本身是安全的类型；问题是这个 API 没有表达“此容器只放字符串”的业务约束，调用方又假定它一定返回 String。

## 类型参数把约束前移

```java
class Box<T> {
    private T value;
    void set(T value) { this.value = value; }
    T get() { return value; }
}

// 方法内：
Box<String> titleBox = new Box<>();
titleBox.set("两数之和");
String title = titleBox.get(); // 不需要手写强制转换
// titleBox.set(42);           // 编译错误：Integer 不能作为 String
```

T 将 set 参数、字段和 get 返回值关联起来。同一个实现也可以用于 `Box<Integer>`，而不需要复制一套整数版类。这比单纯“省略强转”更重要。

## 仍需理解运行时边界

```java
Box<String> typed = new Box<>();
Box raw = typed;       // 原始类型，示例用于说明风险
raw.set(42);          // 未经检查调用警告，破坏原有假设
String value = typed.get(); // 编译器插入的转换在运行时失败
```

泛型约束不会在每个实例上变成“只允许 String”的自动运行时检查器。应尽量避免原始类型和未经检查转换，不能靠忽略警告来证明安全。

泛型通常也不保证非 null：上面的 `Box<String>` 可以保存 null。类型参数不能直接用基本类型，`Box<int>` 不合法，要用 Integer 等引用类型。

## 用于项目契约时的收益

例如 `Page<ProblemSummary>` 能明确一页记录的元素类型，调用方获得记录时不再把 Object 逐个猜测为题目摘要。若 API 本来就允许混合类型，`List<Object>` 也可以是合理设计，但它表达的契约与 `List<ProblemSummary>` 不同。

## 参考

- [JLS 17 §4.5：参数化类型](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5)。
- [JLS 17 §4.8：原始类型](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8)。
