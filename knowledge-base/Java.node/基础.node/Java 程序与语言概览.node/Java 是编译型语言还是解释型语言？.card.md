---
slug: java-compiled-or-interpreted-language
tags: [Java基础, 编译, 解释执行, JIT]
status: published
order: 20
relations:
  prerequisites: [java-source-compilation-and-execution]
  contrasts: [java-source-compilation-and-execution]
---

Java 不能简单归类为“纯编译型”或“纯解释型”语言。通常的 Java 程序先由 `javac` 编译为与平台无关的字节码，再由 JVM 在运行时执行这些字节码；以 HotSpot 为例，代码可以先由解释器执行，运行到足够频繁后再由 JIT 编译器编译为本地机器码。

因此，更准确的面试回答是：**Java 采用“源码编译为字节码，JVM 运行时解释执行并对热点代码进行即时编译”的混合执行模式。** 这里的“编译”至少有两层含义：`javac` 的源码编译，以及运行期间 JIT 的字节码到机器码编译。

## 三种代码形态

| 形态 | 产生时机 | 面向对象 | 作用 |
| --- | --- | --- | --- |
| Java 源码 | 开发者编写 | 开发者和编译器 | 表达程序逻辑 |
| JVM 字节码 | `javac` 等编译器编译源码后 | JVM 指令集 | 作为 class 文件中的可移植执行内容 |
| 本地机器码 | JVM 解释执行或 JIT 编译后 | 当前处理器和操作系统 | 由硬件直接执行 |

`javac` 一般不会直接把 Java 源码编译成当前机器的最终机器码，而是生成 class 文件。class 文件可以在满足版本和运行环境要求的不同平台 JVM 上运行，这也是 Java 跨平台能力的基础。

## 解释器和 JIT 如何配合

程序启动后，JVM 不必等待整个应用都编译成本地机器码才能执行。常见的 HotSpot 流程可以这样理解：

1. JVM 加载并验证需要使用的类。
2. 方法调用时，解释器可以直接读取字节码并执行，程序较快进入可运行状态。
3. JVM 收集运行信息，识别被频繁调用的方法或循环等热点代码。
4. JIT 编译器将热点字节码编译为当前平台的机器码，并可能结合运行信息做内联等优化。
5. 后续执行热点路径时可以复用编译结果；运行假设发生变化时，JVM 也可能撤销优化并重新选择执行方式。

这解释了两个常见现象：程序刚启动时吞吐量可能还没有达到稳定水平；长时间运行后，热点代码的性能可能明显提升。具体阈值、编译层级和优化策略取决于 JVM 实现及其配置，不能把某个 HotSpot 参数当成 Java 语言本身的保证。

## 为什么不只使用 JIT

如果 JVM 启动时就把所有方法都编译为机器码，会增加启动时间、占用更多内存，并编译许多实际上不会执行的代码。解释器让程序能够较快开始运行，JIT 则把编译成本集中在有收益的热点路径上。

这是一种运行时权衡：

- **解释执行**通常启动更快，单次执行成本可能更高；
- **JIT 编译**需要编译开销，但热点代码重复执行时通常能获得更高吞吐量；
- **混合执行**让 JVM 可以根据实际运行情况动态选择。

## 与其他语言分类的关系

“编译型语言”和“解释型语言”是有用的简化说法，但它们描述的是执行实现的某一层，不能替代具体工具链和运行时分析。

- C/C++ 常见流程是直接编译、链接为面向目标平台的本地可执行文件；这不代表运行时绝不会有解释器或动态编译技术。
- JavaScript 现代引擎也可能解释执行并使用 JIT，因此“脚本语言 = 纯解释执行”同样过于简单。
- Java 的 `javac`、JVM 解释器和 JIT 分别处于不同阶段，把它们混为一个“Java 编译器”会遗漏关键边界。

## 面试回答示例

可以先给结论，再补充两层编译：

> Java 不是纯编译型或纯解释型。`javac` 先把源码编译成平台无关的 JVM 字节码，JVM 启动后可以解释执行字节码；在 HotSpot 等实现中，运行时会识别热点代码并通过 JIT 编译为本地机器码。因此 Java 兼顾了跨平台、较快启动和长期运行时的性能优化。`javac` 的编译和 JIT 的即时编译是两个不同阶段。

## 参考资料

- [Java 虚拟机规范 Java SE 17，第 2.13 节](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.13)：虚拟机实现技术不由规范限定。
- [Java 虚拟机规范 Java SE 17，第 6 章](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html)：JVM 指令集和字节码指令。
- [JDK 17 `java` 工具规范](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)：Java 启动器与运行方式。
- [OpenJDK HotSpot 编译器](https://openjdk.org/groups/hotspot/docs/HotSpotGlossary.html)：HotSpot 运行时编译相关术语。
