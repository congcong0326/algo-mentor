---
slug: java-bytecode-and-machine-code
tags: [Java基础, 字节码, 机器码, JVM]
status: published
order: 30
relations:
  prerequisites: [java-compiled-or-interpreted-language]
  related: [java-source-compilation-and-execution]
---

Java 字节码和机器码都是可执行指令，但面向的执行主体不同：**字节码面向 JVM 定义的虚拟指令集，由 JVM 负责解释或编译执行；机器码面向具体 CPU 和操作系统，通常可以直接由处理器执行。** `javac` 通常生成字节码，JVM 运行时再根据需要生成当前平台的机器码。

字节码提供了 Java 的主要跨平台边界：同一个 class 文件可以交给不同平台上的兼容 JVM 执行。机器码则通常与目标架构相关，例如 x86-64 和 ARM64 的指令编码不能直接互换。

## 两者的主要区别

| 对比项 | Java 字节码 | 机器码 |
| --- | --- | --- |
| 面向对象 | JVM 虚拟机 | 具体 CPU 架构 |
| 常见生成者 | `javac`、其他 JVM 语言编译器 | C/C++ 编译器，或 JVM 的 JIT 编译器 |
| 典型载体 | `.class` 文件、JAR 中的类 | 可执行文件、共享库，或进程内的 JIT 代码缓存 |
| 可移植性 | 在兼容 JVM 间通常可移植 | 通常依赖 CPU、操作系统和 ABI |
| 执行方式 | JVM 解释执行或编译成本地代码 | 处理器直接执行，仍受操作系统装载和权限机制影响 |
| 生成时机 | 通常在构建阶段 | 可以在构建阶段生成，也可以在运行阶段生成 |

“字节码更抽象”并不代表它只是文本或脚本。class 文件是有严格格式的二进制文件，包含版本号、常量池、字段、方法和属性等结构；其中的方法体包含 JVM 指令。JVM 会在使用类前验证这些结构是否符合规范。

## 一段代码经过了什么转换

例如：

```java
public class Add {
  public static int sum(int left, int right) {
    return left + right;
  }
}
```

编译后可以使用下面的命令查看方法字节码：

```sh
javac Add.java
javap -c Add
```

在 Java 17 下，`sum` 方法通常会出现类似下面的 JVM 指令：

```text
0: iload_0
1: iload_1
2: iadd
3: ireturn
```

这里的 `iload_0`、`iadd` 和 `ireturn` 是 JVM 指令，不是 x86-64 的 `mov`、`add` 或 ARM64 的对应指令。JVM 可以先解释这些指令；如果 `sum` 成为热点方法，JIT 可能生成适合当前 CPU 的机器码。生成的具体机器指令属于 JVM 实现和运行平台细节，不能从 Java 源码直接推断出唯一结果。

## 跨平台能力的边界

字节码的可移植性依赖运行环境满足多个条件：

1. 目标平台存在支持该 class 文件版本的 JVM。
2. 使用到的标准类库和第三方依赖可用，且版本契约兼容。
3. 程序没有把平台相关行为当成统一语义，例如本地方法、文件路径、字符集、时区或操作系统命令。
4. 运行时权限、环境变量和外部服务满足应用要求。

因此，“一次编译，到处运行”更准确的理解是：**一次生成的字节码可以在多个兼容运行环境中执行，但 JVM、类库、依赖和操作系统交互仍可能带来差异。** 这也解释了为什么 class 文件可以跨平台，而包含 JNI 本地库的应用往往还需要按平台分别提供原生二进制文件。

## 常见误区

**字节码不是机器码的另一种文件后缀。** `.class` 描述的是 JVM 指令和类元数据；机器码可能只存在于进程内存中，也可能存在于本地可执行文件或共享库中。

**JIT 生成机器码不等于 `javac` 生成机器码。** `javac` 通常在构建时生成 class 文件，JIT 是 JVM 运行时根据实际执行情况进行的优化步骤。

**字节码跨平台不等于程序完全与平台无关。** 只要程序调用本地库、依赖操作系统能力或受到运行环境差异影响，仍需进行平台适配和验证。

**不同 JVM 生成的机器码可以不同。** JVM 规范约束可观察行为和字节码语义，允许实现使用不同的解释器、编译器和优化策略。

## 面试回答示例

> Java 字节码是面向 JVM 的中间指令，通常由 `javac` 写入 class 文件；机器码是面向具体 CPU 的指令，可以由处理器直接执行。JVM 能解释字节码，也能在运行时通过 JIT 将热点字节码编译为当前平台的机器码，所以同一个 class 文件可以在不同平台的兼容 JVM 上运行。跨平台只覆盖字节码和 JVM 这一层，依赖、类库、本地库和操作系统交互仍可能带来平台差异。

## 参考资料

- [Java 虚拟机规范 Java SE 17，第 4 章](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html)：class 文件格式。
- [Java 虚拟机规范 Java SE 17，第 6 章](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html)：JVM 指令集。
- [Java 虚拟机规范 Java SE 17，第 2.13 节](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.13)：虚拟机实现与执行技术的边界。
- [JDK 17 `javap` 工具规范](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html)：查看 class 文件和字节码信息。
