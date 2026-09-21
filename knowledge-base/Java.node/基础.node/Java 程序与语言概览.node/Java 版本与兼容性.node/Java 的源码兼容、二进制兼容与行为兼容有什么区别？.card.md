---
slug: java-source-binary-behavior-compatibility
tags: [Java基础, Java版本, 兼容性]
status: published
order: 20
relations:
  related: [java-lts-and-non-lts]
---

判断 Java 版本或类库升级是否兼容，需要区分三个问题：

- **源码兼容**：原有调用方源码不修改，在新的编译环境或依赖版本下能否重新编译通过。
- **二进制兼容**：原有调用方的 `.class` 文件不重新编译，替换相关类库后能否继续链接，不因这次变更产生链接错误。
- **行为兼容**：在约定的输入和使用条件下，升级后的可观察行为是否仍满足原有契约，包括返回值、异常和副作用等。

**重新编译通过、旧二进制能够链接、业务行为符合预期，是三个不同层面的判断，不能用其中一个代替其余两个。** 讨论兼容性时，还要明确方向：通常是旧调用方使用新平台或新类库，而不是新程序运行在旧平台上。

## 三种兼容性分别检查什么

| 类型 | 保留什么，替换什么 | 主要检查 |
| --- | --- | --- |
| 源码兼容 | 保留调用方源码，更换编译环境或依赖后重新编译 | 语法、类型检查、可访问性、重载解析等是否仍然成立 |
| 二进制兼容 | 保留已编译的调用方，替换它依赖的类或接口 | 既有符号引用能否继续解析，是否出现 `NoSuchMethodError` 等链接错误 |
| 行为兼容 | 在相同业务条件下运行升级前后的程序 | 返回值、异常、状态变化及其他约定行为是否符合原有契约 |

源码兼容需要说明编译器版本、语言级别和依赖版本。二进制兼容也有适用范围：JLS 第 13 章主要讨论类和接口演进对既有二进制的影响，它不代表任何版本的 JVM 都能加载这些 class 文件。

下面固定使用 Java 17，只替换一个公共类库，便于单独观察三种兼容性。

## 二进制兼容，不一定源码兼容

假设旧版类库只有一个方法：

```java
public class Formatter {
    public static String format(String value) {
        return "text:" + value;
    }
}
```

调用方写的是：

```java
Formatter.format(null);
```

新版保留原方法，新增重载：

```java
public static String format(Integer value) {
    return "number:" + value;
}
```

对于这个调用方：

- **不重新编译**：旧 class 文件已经记录了对 `format(String)` 的调用，新版仍有这个方法，因此能够继续链接并调用原方法。
- **重新编译**：`null` 同时适用于 `String` 和 `Integer`，两者又不存在子类型关系，编译器无法选出更具体的方法，报告调用歧义。

新增重载没有破坏这里的二进制兼容，却破坏了这段调用代码的源码兼容。重载选择发生在编译期，运行时不会因为多了一个重载而重新选择方法。

## 源码兼容，不一定二进制兼容

假设旧版类库提供：

```java
public class MessageApi {
    public static String message() {
        return "ok";
    }
}
```

调用方只将结果接收为 `Object`：

```java
Object value = MessageApi.message();
```

新版将方法返回类型从 `String` 改成 `Object`，方法体仍然返回 `"ok"`：

```java
public static Object message() {
    return "ok";
}
```

对于这段调用代码：

- **重新编译**：仍然能通过，`Object` 变量可以接收新的返回类型。
- **不重新编译**：旧 class 文件引用的是返回 `String` 的方法。JVM 方法描述符包含返回类型，新版已经没有原描述符对应的方法；在此示例中，执行到该调用时会抛出 `NoSuchMethodError`。

这里仅说明这段调用方源码仍然兼容，不能推导出所有调用方都源码兼容。例如，直接将返回值赋给 `String` 变量的代码就需要修改。

Java 源码不能仅靠返回类型区分重载，但 JVM 方法描述符包含返回类型。这两个规则作用于不同层面。

## 源码和二进制都兼容，也不保证行为兼容

假设某 API 的既有契约明确规定：找不到用户时返回 `null`。

```java
public static String findName(long userId) {
    return null;
}
```

新版保留方法名称、参数、返回类型和可访问性，却将“找不到用户”的处理改为抛出 `IllegalArgumentException`。

旧调用方可以继续编译，既有 class 文件也能够链接，但原先通过 `null` 分支处理缺失用户的逻辑不会再执行，异常还可能向外传播。这就破坏了行为兼容。

行为兼容不是要求所有内部实现都不变，也不是要求每次运行的耗时或随机结果完全一致。判断依据是 API 与业务的既有契约；依赖未承诺的实现细节所造成的变化，需要与违反明确契约区分开。

## 升级时如何分别验证

这三个例子可以概括为：

| 类库变更 | 示例调用方重新编译 | 示例调用方旧 class 链接 | 原有行为 |
| --- | --- | --- | --- |
| 保留 `format(String)`，新增 `format(Integer)` | `format(null)` 编译失败 | 成功，仍调用 `format(String)` | 旧二进制的这次调用不变 |
| `message()` 返回类型从 `String` 改为 `Object` | 接收为 `Object` 时成功 | 失败，找不到原描述符的方法 | 旧二进制无法完成调用 |
| 找不到用户时从返回 `null` 改为抛异常 | 成功 | 成功 | 违反原有缺失值处理契约 |

实际升级 JDK 或依赖时，可以分别做三类验证：

- 使用目标编译环境和依赖重新构建，检查源码兼容问题。
- 如果需要支持“不重新编译调用方”的升级方式，用旧调用方制品搭配新依赖运行，检查相关链接路径；仅重新构建全部模块可能掩盖这类问题。
- 围绕返回值、异常、边界输入和副作用执行契约与业务回归测试，检查行为变化。

程序启动成功不代表所有链接路径都已经验证，单次业务调用成功也不能证明所有行为都兼容。验证范围应覆盖实际使用的 API 和关键业务场景。

## 参考资料

- [JLS 17，第 13.2 节：What Binary Compatibility Is and Is Not](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.2)：二进制兼容的定义与边界。
- [JLS 17，第 13.4.23 节：Method and Constructor Overloading](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.4.23)：新增重载对旧二进制与重新编译的不同影响。
- [JLS 17，第 13.4.15 节：Method Result Type](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.4.15)：修改方法返回类型对二进制兼容性的影响。
- [JVMS 17，第 4.3.3 节：Method Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.3)：方法描述符包含参数类型与返回类型。
