---
slug: java-source-compilation-and-execution
tags: [Java基础, 编译, 字节码, JVM]
status: published
order: 10
relations:
  prerequisites: [java-jdk-jre-jvm-relationship]
  related: [java-compile-time-and-runtime-dependencies]
---

Java 源码从编译到运行，可以概括为：**`javac` 将源码编译成 class 文件，`java` 启动 JVM，JVM 加载、链接并初始化入口类，然后调用 `main` 方法执行程序。** 程序运行过程中，还会按需加载和处理其他类。

class 文件包含字节码以及类、字段、方法等信息，通常不是 CPU 可以直接执行的机器码。在常见的 HotSpot JVM 中，方法可以由解释器执行，热点代码也可以由 JIT 编译器编译为机器码后执行。**源码编译与运行时的 JIT 编译是两个不同阶段；并不是所有代码都必须先经过 JIT 编译才能运行。**

## 各阶段分别做什么

本文以 Java 17、使用 `javac` 编译并通过 `java` 启动主类的常规方式为例。

| 阶段 | 主要工作 | 需要区分的边界 |
| --- | --- | --- |
| 源码编译 | `javac` 分析源码，进行语法、类型等检查，生成 class 文件 | 编译成功不能保证运行时依赖齐全，也不能保证程序逻辑正确 |
| 启动 | `java` 启动器创建 JVM，并指定要启动的主类 | `java` 是启动工具，不能把命令本身等同于 JVM |
| 加载 | JVM 通过类加载器取得类的二进制表示，建立运行时的类信息 | 不要求一次加载应用中的所有类 |
| 链接 | 验证类的合法性、为类的静态字段分配存储并设置默认值，以及解析符号引用 | 链接包括验证、准备和解析；解析可以延迟到使用相关引用时 |
| 初始化 | 按规则执行类的静态字段初始化代码和静态初始化块；必要时先初始化父类 | 加载一个类不等于已经执行其静态初始化代码 |
| 执行 | 调用入口类的 `main`，继续执行方法调用等程序逻辑 | HotSpot 通常结合解释执行与 JIT 编译执行；具体执行策略属于 JVM 实现 |

这里的流程描述的是启动主线。**入口类必须先完成初始化，才会调用它的 `main`；但其他类的加载、链接、初始化可以穿插在后续执行过程中。** 不能理解为“所有类都完成解析和初始化后，程序才开始执行”。

## 用一个小程序串起流程

将以下代码保存为 `Hello.java`：

```java
public class Hello {
  static int value = initialize();

  static {
    System.out.println("static block: " + value);
  }

  private static int initialize() {
    System.out.println("initialize field");
    return 42;
  }

  public static void main(String[] args) {
    System.out.println("main: " + value);
  }
}
```

在文件所在目录执行：

```sh
javac Hello.java
java -cp . Hello
```

输出为：

```text
initialize field
static block: 42
main: 42
```

这段代码可以解释三个阶段的区别：

- **编译时**：`javac` 生成 `Hello.class`，不会执行这里的 `initialize()`，也不会产生示例中的三行输出。
- **准备时**：静态字段 `value` 先获得 `int` 的默认值 `0`。源码中的 `initialize()` 调用不在这个阶段执行。
- **初始化和执行时**：先执行静态字段初始化代码，将 `value` 赋为 `42`，再执行静态初始化块，最后调用 `main`。

还可以查看编译产物：

```sh
javap -c -p Hello
```

输出中的 `static {};` 对应类初始化方法 `<clinit>`，可以看到调用 `initialize()` 和写入静态字段的字节码；`main` 则有自己的方法体。它们都已在编译时生成，但要到运行时才执行。

## 面试中容易混淆的地方

**`javac` 没有把整份程序编译成当前 CPU 的机器码。** 常规编译产物是 class 文件。HotSpot 的 JIT 在运行过程中针对热点代码生成机器码，不会要求每次启动都先把整个应用编译一遍。

**静态初始化发生在运行时。** `static` 不表示“编译时执行”。示例中的静态字段赋值和静态块在入口类初始化时执行，早于该类的 `main`。

**编译能找到依赖，不代表运行时也能找到。** `javac` 检查代码时使用的依赖与启动时实际提供的依赖可能不同，因此程序仍可能因缺失类或依赖版本不匹配而失败。

**字节码执行方式不能写成所有 JVM 都必须遵守的固定流程。** JVM 规范规定程序应呈现的行为，并不要求每个实现必须采用“先解释，再 JIT”的策略。面试中介绍这种执行方式时，应明确以 HotSpot 等常见实现为背景。

## 参考资料

- [JDK 17：javac 工具规范](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)：源码编译与 class 文件生成。
- [JDK 17：java 工具规范](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)：启动 JVM、加载指定类和调用入口方法。
- [Java 虚拟机规范 Java SE 17，第 5 章](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)：加载、链接、初始化，以及 5.2 节的虚拟机启动过程。
- [Java 语言规范 Java SE 17，第 12 章](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html)：程序执行与类初始化语义。
- [Java 虚拟机规范 Java SE 17，2.13 节](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.13)：虚拟机的具体实现不由规范强制规定。
