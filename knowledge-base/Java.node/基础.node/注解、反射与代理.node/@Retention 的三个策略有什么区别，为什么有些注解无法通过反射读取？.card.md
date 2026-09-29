---
slug: java-annotation-retention-and-reflection
tags: [Java基础, 注解, Retention, 反射]
order: 20
relations:
  prerequisites: [java-how-annotations-take-effect]
---

**`@Retention` 决定一处注解使用要保留到哪个阶段。** `SOURCE` 只供源码和编译阶段使用，不写入相应类文件；`CLASS` 会写入类文件，但运行时反射不保证可见；`RUNTIME` 会保留到运行时，可通过对应的反射 API 读取。未声明 `@Retention` 时，默认是 `CLASS`。

因此，通过反射读不到某个注解，首先检查它的保留策略是否为 `RUNTIME`，再检查是否从正确的类、方法或字段等元素上读取。`CLASS` 注解即使存在于类文件里，也不能据此认为 `getAnnotation` 能读到它。

## 三种策略保留的是什么

| 策略 | 注解使用的位置会保留到哪里 | 常见第三方库的使用场景 |
| --- | --- | --- |
| `SOURCE` | 编译器处理源码时可见，不保留在相应类文件中 | **Lombok `@Getter`** 标在字段上，编译期间由 Lombok 处理并生成 getter；编译后的字段不需要保留 `@Getter`。 |
| `CLASS` | 写入相应类文件，运行时反射不保证可见；这是默认策略 | **JetBrains `@NotNull`** 标在方法返回值或参数上，IntelliJ IDEA 可据此分析可能的空值错误；保留在类文件中，使编译后的库也能携带这一约定。 |
| `RUNTIME` | 写入类文件并保留到运行时，可由反射读取 | **JUnit Jupiter `@Test`** 标在测试方法上，测试引擎在运行时发现这些方法并执行测试。 |

这里说的“保留”，指某处**使用了这个注解**的信息是否留下；不是说注解类型本身是否会编译成 `.class`。例如 `@Getter` 的使用痕迹可从编译后的类中消失，Lombok 生成的 getter 方法却会保留下来。

`@Retention` 控制的是保留阶段；如果要限制注解能标在类、方法还是字段上，应使用 `@Target`。

## 为什么三个例子的结果不同

对带 `@Getter` 的字段使用反射，不能靠读取 `@Getter` 还原 Lombok 的源码处理过程；可以看到的是生成后的 getter 方法。JetBrains `@NotNull` 可以供 IDE 等工具分析，但不能靠运行时 `getAnnotation` 取得，也不会仅凭注解本身自动做空值校验。IntelliJ IDEA 构建时可以按配置额外插入空值断言，那是构建工具生成的检查代码。JUnit Jupiter 的 `@Test` 具有 `RUNTIME` 保留策略，测试引擎才能在运行时读取它并识别测试方法。

`RUNTIME` 也不意味着可以在任意位置调用 `getAnnotation`：标在方法上的注解要从对应的 `Method` 读取。局部变量声明上的注解是更特殊的边界，即使声明了 `RUNTIME`，其使用也不会保留在类文件中，不能靠运行时反射找回。

## 参考

- [JLS 17 §9.6.4.2：@Retention](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.2)。
- [Java 17 RetentionPolicy API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)。
- [Lombok `@Getter` 声明](https://github.com/projectlombok/lombok/blob/e50d44b071c955d85f7b94a7cf06142c7ff2622b/src/core/lombok/Getter.java)与[功能说明](https://projectlombok.org/features/GetterSetter)。
- [JetBrains `@NotNull` 声明](https://github.com/JetBrains/java-annotations/blob/b5c263d764f18f491d09d46ca88f192e951f0b3d/src/jvmMain/java/org/jetbrains/annotations/NotNull.java)与[IntelliJ IDEA 注解说明](https://www.jetbrains.com/help/idea/annotating-source-code.html)。
- [JUnit Jupiter `@Test` 声明](https://github.com/junit-team/junit5/blob/6c8ca116941611df2d4e2e8319ab8cb3e520afe7/junit-jupiter-api/src/main/java/org/junit/jupiter/api/Test.java)。
