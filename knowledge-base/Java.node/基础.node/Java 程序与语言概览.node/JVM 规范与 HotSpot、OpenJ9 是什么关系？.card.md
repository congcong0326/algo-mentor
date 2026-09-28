---
slug: java-jvm-specification-and-implementations
tags: [Java基础, JVM, HotSpot, OpenJ9]
status: published
order: 40
relations:
  prerequisites: [java-jdk-jre-jvm-relationship]
---

JVM 规范定义了 Java 虚拟机必须遵守的规则，例如 class 文件格式、字节码指令的语义、运行时数据区，以及类的加载、链接和初始化规则。

HotSpot 和 Eclipse OpenJ9 是 JVM 的不同实现。它们遵循相应版本的规范，但可以采用不同的内部结构、垃圾回收算法、编译优化和诊断机制。**规范规定必须满足的语义与约束，实现选择如何完成这些要求。**

## 区分规范、实现和实例

“JVM”在不同语境中可能指抽象规范、实现软件，也可能指已经启动的虚拟机实例。以 Java 17 为背景，可以分成三个层次：

| 层次 | 含义 | 例子 |
| --- | --- | --- |
| JVM 规范 | 描述虚拟机必须遵守的规则 | 《Java 虚拟机规范 Java SE 17》 |
| JVM 实现 | 将这些规则实现为可执行的软件 | HotSpot、Eclipse OpenJ9 |
| JVM 实例 | 某个实现启动后形成的一次运行 | 启动应用后，承载它运行的虚拟机实例 |

例如，机器上安装了一套使用 HotSpot 的 JDK。分别执行两次 `java -jar app.jar`，通常会启动两个独立进程，各自拥有一个 JVM 实例，也有各自的堆和运行状态。

HotSpot 和 OpenJ9 是虚拟机实现，不是完整 JDK 的同义词。完整 JDK 还需要标准类库、开发工具及其他组件。

## 哪些由规范规定，哪些由实现选择

| 问题 | 规范与实现的边界 |
| --- | --- |
| 某条字节码指令做什么 | 指令语义由规范规定 |
| 什么样的 class 文件有效 | 文件格式、约束和验证规则由规范规定 |
| 类在什么时候进行初始化 | 初始化的触发条件和过程由规范规定 |
| 对象在内存中如何布局 | 具体布局由实现决定 |
| 使用哪种垃圾回收算法 | 具体算法由实现决定 |
| 是否采用解释器、JIT，如何优化 | 具体执行策略由实现决定，但必须保持规范要求的语义 |
| 有哪些诊断命令、GC 参数 | 通常与具体实现及版本有关 |

规范描述的运行时数据区也不等于某个实现的物理内存布局。例如，规范定义了方法区，但不能因此认为每种 JVM 都必须采用 HotSpot 的 Metaspace 实现。

同样，JVM 规范并不要求每种实现都采用“先解释、再 JIT”的执行方式。即使讨论 HotSpot，具体执行行为也与版本、配置和代码运行情况有关。

因此，阅读原理文章时，要区分“规范要求如此”和“某个版本的 HotSpot 这样实现”。HotSpot 的对象头布局、垃圾回收器或编译优化策略，都不能直接推广为所有 JVM 的统一机制。

## 同一份程序能在不同实现上运行吗

对于使用标准 Java API、字节码版本受支持、依赖齐全的程序，通常可以在不同的兼容实现上运行。完整的可移植性还依赖 Java 类库及其他运行环境组件，不能仅凭虚拟机名称判断。

遵循相同规范，不意味着所有可观察现象都完全一致：

- 启动速度、内存占用、吞吐量和暂停时间可能不同。
- 垃圾回收日志、诊断工具和启动参数可能不同。
- 依赖内部 API、特定对象布局或特定原生库的程序，可能需要适配。
- 对于规范未承诺的行为，不能依赖某个实现恰好表现出的结果。

例如，从 HotSpot 切换到 OpenJ9，业务代码可能无需修改，但启动脚本中的 GC 参数、监控和诊断配置仍需重新检查。不能将 HotSpot 的整套调优参数直接照搬到另一种实现上。

## 如何确认当前使用的实现

可以先查看命令输出：

```sh
java -version
```

输出通常包含虚拟机名称和版本。它反映的是当前命令实际启动的 Java 环境，不一定与 IDE 或其他已经运行的应用使用的环境相同。

在应用内部，可以读取当前虚拟机的系统属性：

```java
System.out.println(System.getProperty("java.vm.name"));
System.out.println(System.getProperty("java.vm.vendor"));
System.out.println(System.getProperty("java.vm.version"));
```

这三个属性分别表示虚拟机实现的名称、厂商和版本，适合用于环境确认与排障。普通业务逻辑应尽量依赖标准 API 和明确的能力契约，避免根据厂商名称字符串选择行为。

## 参考资料

- [Java 虚拟机规范 Java SE 17：引言](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-1.html)：虚拟机的抽象定义，以及内部优化、机器码转换等实现自由度。
- [Java 虚拟机规范 Java SE 17：虚拟机结构](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html)：运行时数据区、自动内存管理和对象表示的规范边界。
- [OpenJDK HotSpot 项目](https://openjdk.org/groups/hotspot/)：HotSpot 虚拟机实现及相关组件。
- [Eclipse OpenJ9 文档](https://eclipse.dev/openj9/docs/overview/)：OpenJ9 虚拟机及其与 OpenJDK 类库的组合关系。
- [Java 17：System.getProperties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#getProperties())：虚拟机名称、厂商和版本等标准系统属性。
