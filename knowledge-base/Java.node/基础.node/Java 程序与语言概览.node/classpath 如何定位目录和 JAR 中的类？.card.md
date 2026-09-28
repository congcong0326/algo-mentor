---
slug: java-classpath-locates-classes
tags: [Java基础, package, classpath, JAR, Spring Boot, Maven]
status: published
order: 10
relations:
  related: [java-jdk-jre-jvm-relationship]
---

classpath 是 Java 编译器或运行时查找类和资源的路径集合。日常 Maven 项目中，`mvn package` 先编译出 class 文件，再由 Spring Boot Maven Plugin 重新打包成可执行 JAR；执行 `java -jar app.jar` 时，看到的是一个文件，但它内部仍然需要一组运行时 classpath。

理解这件事要区分两种结构：普通 JAR 把类直接放在 JAR 根目录下，可以直接作为传统 classpath 条目；Spring Boot 可执行 JAR 把业务类和依赖放在 `BOOT-INF/` 下，由 Boot Launcher 负责组装和加载。`import` 只影响源码中的名称解析，不会改变这两种 classpath 结构。

## 普通目录和普通 JAR

类的全限定名为 `com.example.Main` 时，传统 classpath 会把它转换成：

```text
com/example/Main.class
```

如果编译输出目录是：

```text
app/classes/com/example/Main.class
```

那么 classpath 根目录应是 `app/classes`：

```sh
java -cp app/classes com.example.Main
```

不能把 `app/classes/com/example` 当作根目录，否则 JVM 会继续拼接 `com/example/Main.class`，最终找不到类。

普通 JAR 也遵循同一规则。它的内部结构通常是：

```text
com/example/Main.class
META-INF/MANIFEST.MF
```

因此可以直接运行其中的主类：

```sh
java -cp app.jar com.example.Main
```

依赖位于其他 JAR 时，需要一并加入运行期 classpath：

```sh
java -cp 'app.jar:lib/*' com.example.Main
```

Linux 和 macOS 使用 `:` 分隔路径，Windows 使用 `;`。Maven、Gradle 或 IDE 通常会替我们生成这串路径，但它仍然是应用启动时实际使用的 classpath。

## Maven 打包出的 Spring Boot 可执行 JAR

本项目的 `mentor-api` 使用 `spring-boot-maven-plugin`，打包后的 JAR 通常类似：

```text
app.jar
├── META-INF/MANIFEST.MF
├── org/springframework/boot/loader/...
├── BOOT-INF/
│   ├── classes/
│   │   └── org/congcong/algomentor/api/MentorApiApplication.class
│   └── lib/
│       ├── spring-boot-....jar
│       ├── spring-web-....jar
│       └── ...
```

业务 class 在 `BOOT-INF/classes`，第三方依赖在 `BOOT-INF/lib`。这和普通 JAR 的根目录结构不同：业务类并不位于 `app.jar` 根目录，而是嵌套在 `BOOT-INF/classes` 中。

执行：

```sh
java -jar app.jar
```

并不是标准 JVM 直接把整个 JAR 根目录当作普通 classpath 后寻找业务类。Spring Boot JAR 的 manifest 会指定 Boot 的 Launcher 作为入口，并记录实际应用的 `Start-Class`。Launcher 启动后会把 `BOOT-INF/classes` 和 `BOOT-INF/lib` 组织成应用使用的加载路径，再调用业务主类。因此，`BOOT-INF/` 是 Spring Boot 的打包约定和 Launcher 机制，不是普通 Java classpath 的通用规则。

## 解压后用 `-cp` 启动

把 Spring Boot JAR 解压后，可以绕过 Boot Launcher，按照普通 classpath 规则直接指定两个关键目录：

```sh
mkdir unpacked
cd unpacked
jar xf ../app.jar
java -cp 'BOOT-INF/classes:BOOT-INF/lib/*' \\
  org.congcong.algomentor.api.MentorApiApplication
```

这里的含义是：

- `BOOT-INF/classes` 是业务 classpath 根目录，里面的包路径从 `org/...` 开始；
- `BOOT-INF/lib/*` 把依赖 JAR 加入 classpath；
- 主类仍然使用全限定名；
- `BOOT-INF` 本身通常不是 classpath 根目录，因为 JVM 不会自动跳过这一层再寻找 `classes`。

这种方式能说明可执行 JAR 内部的真实布局，但生产启动通常仍使用 `java -jar`，因为 Boot Launcher 还处理了嵌套 JAR、启动属性和打包约定。不同 Spring Boot 版本的 Launcher 类名和 manifest 细节可能变化，不应手写固定的 Launcher 实现来替代标准启动方式。

## 如何查看最终制品

排查“本地能启动、部署不能启动”时，应查看最终 JAR，而不是只看 `target/classes`：

```sh
jar tf target/*.jar | head -50
unzip -p target/*.jar META-INF/MANIFEST.MF
```

重点确认：

- 是否存在 `BOOT-INF/classes` 和 `BOOT-INF/lib`；
- manifest 的 `Main-Class` 和 `Start-Class` 是什么；
- 依赖是否真的进入最终 JAR；
- 启动命令是 `java -jar`，还是脚本自行拼接了 `-cp`。

普通 Maven JAR、Spring Boot 可执行 JAR、解压目录三者的启动方式不能混用：

| 制品形态 | 典型类位置 | 常见启动方式 |
| --- | --- | --- |
| 编译输出目录 | `target/classes/com/example/Main.class` | `java -cp target/classes ...` |
| 普通 JAR | `com/example/Main.class` | `java -cp app.jar ...` 或依赖 manifest |
| Spring Boot 可执行 JAR | `BOOT-INF/classes/...`、`BOOT-INF/lib/*.jar` | `java -jar app.jar` |
| Spring Boot JAR 解压目录 | `BOOT-INF/classes`、`BOOT-INF/lib` | `java -cp 'BOOT-INF/classes:BOOT-INF/lib/*' ...` |

## 面试回答框架

可以结合实际项目这样回答：

1. Maven 编译结果通常位于 `target/classes`，它是一个 classpath 根目录。
2. 普通 JAR 把 `com/example/X.class` 放在 JAR 根下，可以直接作为 classpath 条目。
3. Spring Boot 可执行 JAR 把应用类放到 `BOOT-INF/classes`，依赖放到 `BOOT-INF/lib`，`java -jar` 由 Boot Launcher 组装这些路径。
4. 如果解压后自己用 `-cp` 启动，就要显式指定 `BOOT-INF/classes` 和 `BOOT-INF/lib/*`；只指定外层目录或 JAR 根目录通常找不到业务类。

## 参考资料

- [Spring Boot：Packaging Executable Archives](https://docs.spring.io/spring-boot/reference/packaging/executable-jar.html)：可执行 JAR 的目录结构、Launcher 和嵌套依赖。
- [Java 17 `java` 命令说明](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)：`-classpath`、`-jar` 与启动参数。
- [JAR 文件规范](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html)：普通 JAR 布局和 manifest。
