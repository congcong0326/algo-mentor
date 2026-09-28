---
slug: java-javac-release-source-target
tags: [Java基础, Java版本, 兼容性, javac]
status: published
order: 50
relations:
  prerequisites: [java-build-for-older-runtime]
  related: [java-source-binary-behavior-compatibility]
---

`-source` 指定编译时采用哪个 Java 版本的语言规则，`-target` 指定生成哪个版本的 class 文件；**两者都不会自动将可用的平台 API 限制为目标版本的 API**。

`--release` 同时约束语言规则、目标 class 文件版本，以及编译时可见的目标版本平台 API。因此，使用高版本 JDK 面向低版本 Java 编译时，应优先使用 `--release`，避免出现“class 版本正确，却引用了目标运行时没有的 API”的问题。

例如，在 JDK 17 上，`-source 11 -target 11` 仍可能允许调用 Java 16 才新增的 `Stream.toList()`；`--release 11` 会在编译期拒绝这个调用。`--release` 不能与 `-source`、`-target` 同时使用，也不会自动检查或转换第三方依赖。

## 三个参数分别控制什么

| 参数 | 控制内容 | 不提供的保证 |
| --- | --- | --- |
| `-source 11` | 按 Java 11 的语言规则解析和检查源码，例如拒绝 record 声明 | 不代表只允许调用 Java 11 的平台 API，也不能仅凭它确认最终 class 版本 |
| `-target 11` | 生成面向 Java 11 的 class 文件 | 不会将 Java 17 的语法或 API 自动改写成 Java 11 的实现 |
| `--release 11` | 同时选择 Java 11 的语言规则、class 文件版本和可用平台 API | 不保证第三方依赖、反射访问及实际运行行为全部兼容 |

`--source` 与 `-source` 是同一选项的两种写法，`--target` 与 `-target` 也是如此。

语言级别与目标 class 版本还需要满足编译器的组合约束：目标版本不能低于源码版本。在 JDK 17 中，仅指定 `-target 11`、保留默认的 Java 17 源码级别，会因组合不匹配而失败，不能用这种方式自动降级 Java 17 源码。

## 为什么 source 和 target 都正确，仍然可能误用新 API

保存为 `ApiDemo.java`：

```java
import java.util.List;
import java.util.stream.Stream;

public class ApiDemo {
    public static void main(String[] args) {
        List<String> values = Stream.of("a", "b").toList();
        System.out.println(values);
    }
}
```

这段代码没有使用 Java 11 不支持的语法，但 `Stream.toList()` 是 Java 16 才引入的方法。

在 JDK 17 中执行：

```sh
javac -source 11 -target 11 -d out-source-target ApiDemo.java
javap -verbose out-source-target/ApiDemo.class
```

编译可以通过，编译器通常会提示没有配套设置系统模块路径。生成的 class 文件显示 `major version: 55`，确实面向 Java 11；但方法解析使用了当前 JDK 17 的平台 API，因此 class 文件中仍然引用了 `Stream.toList()`。

在 Java 17 上运行这个制品会正常输出列表，不能暴露问题。在普通 Java 11 环境中执行到该调用时，由于 `Stream` 没有这个方法，会抛出 `NoSuchMethodError`。

这里不是 class 文件版本过高导致的 `UnsupportedClassVersionError`，而是**class 文件能被识别，但引用的方法不存在**。

改为：

```sh
javac --release 11 -d out-release ApiDemo.java
```

编译器会直接报告找不到 `toList()` 方法。问题因此可以在构建阶段暴露，不必等到部署到 Java 11 后才发现。

## release 为什么能限制平台 API

在 JDK 17 中，编译器可以借助 JDK 随附的历史平台 API 签名数据（`lib/ct.sym`），按所选历史版本提供编译期符号信息。

指定 `--release 11` 后，编译器看到的是 Java 11 对应的可用平台 API；它不会因为自己运行在 JDK 17 上，就允许源码调用 Java 17 中才有的方法。

这些签名数据供编译使用，不是完整的旧版 JDK，也不会随业务 class 文件一起成为运行时实现。最终仍然由部署环境中的 Java 运行时提供标准类库。

因此，`--release` 可以理解为同时设置语言、字节码与平台 API 三方面约束，但不能简单等同于 `-source` 和 `-target` 的缩写。

## 使用时还有哪些边界

- **支持的目标版本有限**：`--release` 从 JDK 9 开始提供；某个编译器支持哪些目标版本，应查看它的 `javac --help`。不能在 JDK 8 的 `javac` 上直接使用该选项。
- **不能混用参数**：直接调用 `javac` 时，`--release 11 -source 11` 或 `--release 11 -target 11` 都会报错，即使版本数字相同。
- **依赖不会自动转换**：class path 上的第三方 JAR 保持原样。JDK 17 可能读得懂只支持 Java 17 的依赖，使面向 Java 11 的业务源码编译成功，但最终依赖仍然无法在 Java 11 上运行。
- **动态行为不由它完整检查**：通过反射字符串查找某个新方法，可能绕过编译期 API 检查；实际执行仍取决于目标运行时。
- **测试 JVM 不会随之切换**：设置 `--release 11` 只影响编译，不会自动让测试或应用运行在 Java 11 上。

对于常规跨版本构建，优先使用 `--release`；只有需要专门控制语言级别、class 目标或平台类库来源时，才单独配置 `-source`、`-target` 等选项，并自行确保它们构成一致的编译环境。

## 参考资料

- [JDK 17 javac 工具说明](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)：`--release`、`--source`、`--target` 的定义、组合限制和跨版本编译说明。
- [JEP 247：Compile for Older Platform Versions](https://openjdk.org/jeps/247)：引入 `--release` 的动机，以及使用历史平台 API 签名数据的机制。
- [Java 17 Stream.toList()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList())：方法自 Java 16 引入。
