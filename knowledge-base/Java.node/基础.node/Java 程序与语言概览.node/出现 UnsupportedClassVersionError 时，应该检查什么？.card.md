---
slug: java-unsupported-class-version-error
tags: [Java基础, Java版本, 兼容性, 问题排查]
status: published
order: 60
relations:
  prerequisites: [java-build-for-older-runtime]
  related: [java-javac-release-source-target, java-old-binaries-on-new-jdk]
---

`UnsupportedClassVersionError` 表示 JVM 在读取 class 文件时，发现其主版本号或次版本号不受当前运行环境支持。最常见的原因是：**某个类面向较高版本 Java 编译，但实际加载它的 JVM 版本较低**。

排查时先从完整异常中确定出错的类、它的 class 文件版本，以及当前 JVM 支持的版本，再确认实际启动应用的 Java 路径，并追踪这个类来自业务制品、第三方依赖还是构建插件。

修复通常需要升级实际运行环境，或者将相关代码和依赖调整为兼容较低版本的制品。仅修改本机 `JAVA_HOME`、只降低业务模块的编译目标，或直接修改 class 文件头，都不能保证解决问题。若异常提到预览特性，还需要检查对应版本及 `--enable-preview` 设置。

## 先读懂异常中的两个版本

例如，Java 17 加载面向 Java 21 的普通 class 文件时，异常信息通常包含：

```text
java.lang.UnsupportedClassVersionError: com/example/Task
has been compiled by a more recent version of the Java Runtime
(class file version 65.0), this version of the Java Runtime
only recognizes class file versions up to 61.0
```

这里可以读出三件事：

- 出错的类是 `com.example.Task`，需要定位实际加载的是哪个文件或 JAR 中的这个类。
- `65.0` 是该 class 文件的主、次版本号，普通 Java 21 class 文件对应主版本 `65`。
- `61.0` 是当前 JVM 支持到的 class 版本，说明这里的运行环境是 Java 17。

常用对应关系如下：

| Java 版本 | class 主版本号 |
| --- | --- |
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

class 版本说明制品面向的运行版本，不能据此确定实际使用了哪个版本的编译器。例如，JDK 17 使用 `--release 11` 生成的 class 主版本号也是 `55`。

## 确认真正加载这个类的 JVM

在交互式终端中执行 `java -version` 是起点，但应用可能由另一个进程、脚本或容器启动。应在实际运行环境中核对：

```sh
command -v java
java -version
javac -version
```

对于 Maven 构建，还可以检查：

```sh
mvn -version
```

这些命令分别帮助确认命令解析路径、运行工具版本、编译工具版本，以及 Maven 自身所用的 Java 环境；它们不保证所有子进程都使用同一个 JDK。

常见偏差包括：

- shell 中的 `java` 来自 `PATH`，不一定来自 `JAVA_HOME`。
- IDE 的项目编译 JDK、运行配置 JDK 和 Maven runner JDK 不一致。
- 服务脚本写死了另一个 Java 可执行文件路径。
- 容器镜像中的 Java 版本与宿主机不同。
- Maven 插件、测试进程或工具链启动了独立 JVM。

如果报错发生在构建插件加载阶段，需要检查运行插件的 JVM；如果发生在应用部署后，需要检查部署进程。两者不能通过修改同一个编译目标参数一概解决。

## 定位不兼容的类来自哪里

对异常指出的 class 文件，可以查看版本：

```sh
javap -verbose path/to/Task.class
```

关注输出中的 `major version` 和 `minor version`。如果类位于普通 JAR 中，可以指定该 JAR 和完整类名：

```sh
javap -verbose -classpath path/to/library.jar com.example.Task
```

这里应检查实际部署的制品，避免只查看源码目录旁边的旧构建结果。同名类可能存在于多个依赖包中，必要时结合类加载日志和 class path 排查实际来源。包含嵌套依赖的可执行 JAR，需要先定位内部对应的依赖 JAR，不能把外层路径简单当作普通 class path 使用。

| 类的来源 | 重点检查 |
| --- | --- |
| 业务代码 | 模块实际生效的 `release` 配置，是否混入旧构建产物或更高目标版本的模块 |
| 第三方依赖 | 该依赖及传递依赖要求的最低 Java 版本，打包时实际选中的版本 |
| 构建或测试插件 | 插件要求的最低 Java 版本，以及真正运行插件的 JVM |
| 生成或增强后的代码 | 最终 class 文件的版本，是否被生成工具或后处理步骤改变 |

多版本 JAR 允许为不同 Java 版本放置不同实现，因此 JAR 内存在高版本 class 文件本身不一定是问题。应检查目标运行时按实际加载方式选中的类。

## 根据约束选择修复方式

如果应用和依赖已经要求 Java 21，而部署环境可以升级，应调整真正启动应用的运行环境，再验证启动参数、依赖和业务行为。

如果必须保留 Java 17，则需要提供面向 Java 17 的完整制品：

- 业务模块使用合适的 `--release 17` 配置，并调整不受支持的语法或 API。
- 第三方库和插件选择支持相应运行环境的版本，或在条件允许时从源码进行适配构建。
- 重新检查最终打包结果，在实际 Java 17 环境中执行验证。

只把业务模块设为 `release=17`，不会把依赖 JAR 中的 Java 21 class 文件转换成 Java 17。直接将文件头中的 `65` 改成 `61` 也不是兼容性转换：文件内部仍可能使用目标 JVM 不支持的结构、指令组合或平台 API。

## 主版本号匹配，也可能因预览特性失败

这项异常不只表示“主版本号太高”。使用预览特性的 class 文件有特殊的次版本号 `65535`，加载时还受预览功能启用状态和对应 Java 版本限制。

例如，使用 JDK 17 的预览特性编译出的相关 class 文件，即使在 Java 17 上运行，未启用预览功能也可能抛出 `UnsupportedClassVersionError`，异常会提示 preview features 未启用。

这时应根据异常确认：是否使用对应版本的 JVM，运行时是否设置 `--enable-preview`。不能认为任意更高版本的 JVM 加上该参数，就能直接运行旧版本的预览 class 文件。

## 与另外两类错误区分

| 错误 | 核心含义 |
| --- | --- |
| `UnsupportedClassVersionError` | JVM 不接受所加载 class 文件的版本或相应预览状态 |
| `NoSuchMethodError` | 已有二进制引用的方法，在实际加载的类中无法按要求解析 |
| `NoClassDefFoundError` | 类定义无法获得，或该类此前初始化失败等；需要结合异常链定位 |

例如，`-source 11 -target 11` 编译出的代码如果误用了新平台 API，在 Java 11 上更可能表现为相应的类或方法解析错误，不能只根据“升级后报错”就归因于 class 版本不匹配。

## 参考资料

- [Java 17 UnsupportedClassVersionError API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/UnsupportedClassVersionError.html)：主、次版本号不受支持时的异常定义。
- [JVMS 17，第 4.1 节：The ClassFile Structure](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.1)：class 版本字段、支持范围与预览特性约束。
- [JVMS 21，第 4.1 节](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.1)：Java 21 对应的 class 版本范围。
- [JDK 17 javap 工具说明](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html)：查看 class 文件信息的命令与参数。
