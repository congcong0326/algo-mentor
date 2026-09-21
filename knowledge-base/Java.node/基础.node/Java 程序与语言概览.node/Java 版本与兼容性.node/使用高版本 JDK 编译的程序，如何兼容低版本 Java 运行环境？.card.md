---
slug: java-build-for-older-runtime
tags: [Java基础, Java版本, 兼容性, 编译]
status: published
order: 40
relations:
  prerequisites: [java-source-binary-behavior-compatibility]
  related: [java-old-binaries-on-new-jdk]
---

可以使用较新的 JDK 构建面向旧版本 Java 的程序，但需要明确目标版本，并让**源码语法、生成的字节码、使用的平台 API 和运行时依赖**都满足目标环境的要求。

例如，使用 JDK 17 构建面向 Java 11 的程序，应优先使用 `javac --release 11`，或在构建工具中配置对应的 `release`。它会按 Java 11 的语言规则编译，生成面向 Java 11 的 class 文件，并限制编译时可用的平台 API。

**`--release` 不会自动改写新语法、转换第三方依赖，也不能代替在目标 Java 环境中的运行验证。** 编译器 JDK 可以较新，但最终交付物及其实际执行路径必须适配最低支持的运行版本。

## 先区分三个版本

| 版本 | JDK 17 构建、Java 11 部署的例子 |
| --- | --- |
| 编译工具版本 | 真正执行 `javac` 的是 JDK 17 |
| 编译目标版本 | 通过 `--release 11` 约束语言、字节码及平台 API |
| 实际运行版本 | 部署时真正执行程序的是 Java 11 |

安装 JDK 17 不意味着只能生成 Java 17 的 class 文件；但如果不指定目标版本，JDK 17 的 `javac` 默认会生成面向 Java 17 的 class 文件，Java 11 无法直接加载。

编译器只支持一定范围的历史目标版本，具体以该 JDK 的 `javac --help` 为准，不能假设任意新 JDK 都能面向任意旧版本编译。

## 用 JDK 17 构建面向 Java 11 的程序

保存为 `Hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println(" Java 11 ".strip());
    }
}
```

`String.strip()` 是 Java 11 已有的 API，可以在 JDK 17 环境中执行：

```sh
javac --release 11 -d out Hello.java
javap -verbose out/Hello.class
```

生成的 class 文件应显示 `major version: 55`，对应 Java 11 的 class 文件版本。随后应切换到实际的 Java 11 运行环境，确认 `java -version` 后执行：

```sh
java -cp out Hello
```

预期输出 `Java 11`。检查 class 版本只能确认其中一项条件，不能据此判断整个应用的所有依赖与行为都兼容。

如果使用 Maven，在支持 `release` 配置的 Maven Compiler Plugin 下，常见配置为：

```xml
<properties>
    <maven.compiler.release>11</maven.compiler.release>
</properties>
```

这项配置用于设置编译目标，不会选择或安装 JDK。实际编译器由 Maven 运行环境、Toolchains 或插件配置决定；多模块项目还需要确认子模块没有覆盖目标版本。

测试也需要单独确认所用 Java 版本。在 JDK 17 上执行 Maven，即使配置了 `release=11`，测试进程仍可能运行在 Java 17 上，不能把测试通过直接当作 Java 11 运行验证。

## 新语法和新 API 不会自动降级

`--release 11` 的作用是约束编译，不是把 Java 17 程序自动翻译成 Java 11 程序。

例如，下面的 record 声明无法以 Java 11 为目标编译：

```java
public record User(long id) {}
```

需要改用 Java 11 支持的类声明，并根据需求显式实现相应行为，或者提高最低运行版本。

同样，`Stream.toList()` 在 Java 16 才引入，使用它的代码在 `--release 11` 下会编译失败：

```java
java.util.stream.Stream.of("a", "b").toList();
```

应选择目标版本已有的 API，并核对替代方案的可变性、空值处理等契约是否符合业务需求。仅仅找到一个能编译的相似调用，不能证明行为一致。

仅设置 `-target`，或只组合使用 `-source` 与 `-target`，不足以限制高版本 JDK 中新引入的平台 API；跨版本构建应优先使用 `--release`。这些参数的具体差别可单独展开。

## 第三方依赖不会跟着一起降级

假设业务代码使用 `--release 11` 编译，但引用的某个依赖只支持 Java 17：

- JDK 17 的编译器可能正常读取该依赖，并成功编译业务代码。
- 业务 class 文件虽然面向 Java 11，依赖 JAR 中的 class 文件仍然可能面向 Java 17。
- 在 Java 11 上加载这些依赖类时，仍会出现 `UnsupportedClassVersionError`。

即使依赖的 class 文件版本足够低，也还要检查它是否在运行时调用了更高版本才存在的 API。`--release` 不会重新编译依赖 JAR，也不会全面审计依赖的方法体。

因此，需要核对直接依赖、传递依赖以及最终打包进制品的组件；字节码增强或代码生成工具产生的最终 class 文件也必须适配目标环境。对于多版本 JAR，应检查目标运行时实际选中的类，不能仅凭 JAR 内存在高版本 class 文件就判定不兼容。

## 可交付的兼容性需要怎样验证

面向 Java 11 交付时，至少应形成以下闭环：

1. **统一编译目标**：各业务模块使用正确的 `release`，检查构建配置是否被覆盖。
2. **检查完整制品**：依赖支持 Java 11，打包、生成或增强后的字节码也满足要求。
3. **在 Java 11 上执行测试**：验证单元测试、集成测试与关键业务路径，覆盖反射、动态加载等仅靠编译无法检查的情况。
4. **使用实际部署方式验证**：检查启动脚本、运行参数、容器镜像及外部连接，确认部署时没有误用更高版本的运行环境。

若产品声明支持多个 Java 版本，应在最低支持版本及承诺支持的其他版本上安排适当验证；只在较新版本上测试，无法证明较旧版本可用。

## 与当前项目的关系

algo-mentor 当前将 Java 17 作为后端基线，根 POM 的 `maven.compiler.release` 也设置为 `17`。本卡片的 Java 11 示例用于说明跨版本构建方法，并不表示将该属性改成 `11` 就能让现有项目运行在 Java 11 上。

实际降低项目基线，需要同时检查源码特性、框架与依赖的最低 Java 要求，以及完整构建和运行链路。

## 参考资料

- [JDK 17 javac：`--release` 与跨版本编译](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)：语言规则、目标 class 文件及平台 API 范围。
- [Maven Compiler Plugin：Setting the `--release` of the Java Compiler](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)：Maven 中的编译目标配置。
- [Java 11 String API](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/String.html#strip())：`strip()` 的版本与语义。
- [Java 17 Stream API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList())：`toList()` 自 Java 16 引入及其返回值契约。
