---
slug: java-jdk-jre-jvm-relationship
tags: [Java基础, JDK, JRE, JVM]
status: published
order: 10
---

JVM 是执行 Java 字节码的虚拟机；JRE 是运行 Java 程序所需的环境，包含 JVM、标准类库及其他运行支持；JDK 是开发工具包，在提供运行能力的基础上，还提供编译、调试、诊断等工具。

从职责上理解：**JVM 负责执行，JRE 提供运行环境，JDK 提供开发工具和运行环境。** 这是功能关系，不意味着现代 JDK 的安装目录一定按三层嵌套。

## 各自承担什么职责

本文以 Java 17 为背景，JRE 指运行环境这一概念，不限定为某个厂商单独发行的安装包。

| 名称 | 职责 | 典型内容 |
| --- | --- | --- |
| JVM：Java Virtual Machine | 加载、验证并执行字节码，管理运行时内存等 | 字节码执行、垃圾回收等机制 |
| JRE：Java Runtime Environment | 提供 Java 程序运行所需的基础环境 | JVM、标准类库、原生库及运行支持 |
| JDK：Java Development Kit | 提供开发和运行 Java 程序所需的工具与环境 | 运行环境，以及编译器 `javac`、归档工具 `jar`、文档工具 `javadoc`、诊断工具 `jcmd` 等 |

JVM 解决字节码如何执行的问题，标准类库提供程序可以调用的基础 API。例如，`String`、`System` 和文件操作 API 属于类库；执行 Java 程序时，需要虚拟机、类库和底层运行支持共同工作。

JRE 提供的是基础运行环境。应用使用的第三方依赖，例如 Spring，通常仍需由应用另外提供。

## 用一次编译和运行串起三者

将下面的代码保存为 `Hello.java`：

```java
public class Hello {
  public static void main(String[] args) {
    System.out.println("Hello");
  }
}
```

在文件所在目录执行：

```sh
javac Hello.java
java Hello
```

这个过程中：

1. `javac` 是 JDK 提供的编译器，将源文件编译为字节码文件 `Hello.class`。
2. `java` 是启动命令，启动 JVM，加载指定的类并调用其 `main` 方法。`java` 命令本身不等同于 JVM。
3. 执行程序时，JVM 配合运行环境提供的 `System`、`String` 等标准类及底层支持，完成输出。

因此，仅有字节码执行引擎并不足以构成完整的 Java 运行环境。这个例子没有引入第三方依赖，但仍然依赖 Java 标准类库。

## 容易混淆的地方

**JVM 不等于标准类库。** 虚拟机承担执行和运行时管理等职责，标准类库提供基础 API，两者配合工作。

**JDK 同时具备运行能力。** 安装完整 JDK 后，通常不需要再额外安装一套 JRE，就能完成上面的编译和运行。

**“JDK = JRE + 开发工具”是概念简写。** 它表达能力上的包含关系，不能据此推断 JDK 17 的安装目录中必须存在独立的 `jre/` 目录。运行环境的概念与安装包的组织方式需要分开理解。

## 参考资料

- [Java 虚拟机规范 Java SE 17：虚拟机结构](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html)：虚拟机的职责与运行时结构。
- [JDK 17 工具规范](https://docs.oracle.com/en/java/javase/17/docs/specs/man/index.html)：`java`、`javac` 及其他 JDK 工具的职责。
