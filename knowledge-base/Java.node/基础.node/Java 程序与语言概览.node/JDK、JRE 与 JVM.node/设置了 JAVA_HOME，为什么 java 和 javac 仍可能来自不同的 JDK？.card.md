---
slug: java-home-path-and-toolchain
tags: [Java基础, JDK, JAVA_HOME, PATH, Maven]
status: published
order: 60
relations:
  prerequisites: [java-jdk-jre-jvm-relationship]
---

`JAVA_HOME` 是许多 Java 工具约定使用的 JDK 路径，但它不会自动决定终端执行哪个 `java` 或 `javac`。直接输入命令时，Shell 通常根据 `PATH` 查找可执行文件；别名、函数和版本管理工具也可能影响结果。

IDE、Maven 及编译工具链还可能独立选择 JDK。因此，确认环境时需要检查**实际执行文件的路径、工具报告的运行环境，以及项目的工具链配置**，不能只看 `JAVA_HOME`。

## JAVA_HOME 和 PATH 分别做什么

| 配置 | 作用 | 不保证什么 |
| --- | --- | --- |
| `JAVA_HOME` | 告诉遵循这一约定的工具，JDK 位于哪里 | 不会自动修改终端的命令查找路径 |
| `PATH` | Shell 查找外部命令时，依次搜索其中的目录 | 不保证 `java`、`javac` 来自同一目录 |
| IDE／工具链配置 | 为特定运行、构建或编译任务选择 JDK | 不一定与当前终端环境一致 |

例如，在 Linux／Bash 环境中：

```sh
export JAVA_HOME=/opt/jdk-17
export PATH=/opt/jdk-21/bin:/usr/bin:/bin
```

此时直接执行 `java`、`javac`，通常仍会使用 `/opt/jdk-21/bin` 中的程序。路径仅为示例，实际配置应使用本机的安装位置。

如果希望终端优先使用 `JAVA_HOME` 指向的 JDK，可以设置：

```sh
export PATH="$JAVA_HOME/bin:$PATH"
```

这里的前提是没有别名、函数等额外覆盖。`JAVA_HOME` 应指向 JDK 根目录，例如 `/opt/jdk-17`，而不是它的 `bin/` 目录或 `java` 文件。

## 为什么 java 和 javac 也可能不一致

Shell 会分别查找这两个命令，并不会将它们作为“一套 JDK”绑定选择。例如：

```text
PATH 中靠前的目录：只有 java
PATH 中靠后的目录：有另一个 JDK 的 java 和 javac
```

那么 `java` 可能来自前一个目录，`javac` 则来自后一个目录。系统的符号链接、alternatives 配置或版本管理工具，也可能分别指向不同安装位置。

这种组合不一定立即报错，但可能造成编译和运行版本不一致。例如，高版本编译器默认生成的字节码，低版本运行环境可能无法加载。

## 如何确认终端实际使用了什么

以 Linux／Bash 为例：

```sh
echo "$JAVA_HOME"

type -a java
type -a javac

java -version
javac -version
```

`type -a` 可以帮助发现同名命令、别名和函数。对于 `command -v` 返回可执行文件路径的普通外部命令，还可以查看符号链接最终指向哪里：

```sh
readlink -f "$(command -v java)"
readlink -f "$(command -v javac)"
```

如果入口是版本管理工具的包装脚本，解析符号链接只能找到该脚本，仍需结合管理工具配置确认它实际启动的 JDK。`readlink -f` 的用法以常见 Linux 工具为准，其他系统应使用对应的路径检查方式。

也可以绕过 `PATH`，直接检查指定 JDK：

```sh
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
```

仅比较版本号还不够：两套不同发行版或不同安装位置的 JDK，可能报告相同的版本号。

## 为什么终端正确，Maven 或 IDE 仍然不同

这里需要分别看三个选择：

| 环节 | 可能使用的 JDK |
| --- | --- |
| 运行 Maven 本身 | Maven 启动脚本或 IDE 为其选择的 JDK |
| 编译项目源码 | 默认编译器，或 Maven Toolchains、插件指定的另一套 JDK |
| 启动应用、运行测试 | IDE 运行配置、测试插件或工具链选择的 JDK |

命令行可以先执行：

```sh
mvn -version
```

它报告的是**运行 Maven 本身的 Java 环境**。如果项目配置了 Maven Toolchains，编译器仍可能来自另一套 JDK。

IDE 也可能分别配置项目 SDK、Maven Runner、测试和应用运行时。修改终端的 `JAVA_HOME`，不意味着这些配置会同步改变；已经启动的 IDE 也不会自动获得后来修改的终端环境变量。

另外，在 Maven 的 `properties` 中配置：

```xml
<maven.compiler.release>17</maven.compiler.release>
```

是在设置编译的目标版本和对应平台 API 范围，**不是选择或安装 JDK 17**。例如，支持该目标的 JDK 21 编译器也可以使用 `--release 17`。这项配置也不负责切换运行 Maven 或启动应用所使用的 JDK。

## 排查时按什么顺序看

1. 确认问题发生在终端、IDE、构建还是应用运行阶段。
2. 检查该阶段实际使用的可执行文件和 Java 版本。
3. 检查工具链、插件或运行配置是否另选了 JDK。
4. 修改配置后，在对应环境中重新验证。

环境变量表达配置意图，实际执行路径和工具输出才说明当前真正使用了什么。

## 参考资料

- [GNU Bash：命令查找与执行](https://www.gnu.org/software/bash/manual/html_node/Command-Search-and-Execution.html)：函数、内建命令、`PATH` 和命令路径缓存的查找规则。
- [Maven 安装说明](https://maven.apache.org/install.html)：Java 环境配置和 `mvn -v` 检查方式。
- [Maven Toolchains 指南](https://maven.apache.org/guides/mini/guide-using-toolchains.html)：将构建工具链与运行 Maven 的 JDK 分离。
- [Maven Compiler Plugin：设置 --release](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)：目标 Java 版本及平台 API 范围配置。
- [JDK 17：javac 工具说明](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)：`--release` 的语义及支持范围。
