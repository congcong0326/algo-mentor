---
slug: java-reflection-and-class-object-acquisition
tags: [Java基础, 反射, Class]
order: 40
relations:
  related: [java-annotation-retention-and-reflection]
---

**反射主要提供两类能力：内省和调用。** 内省是运行时检查类、构造器、方法、字段及注解，回答“这个类型有什么”；调用是按检查结果创建对象、调用方法或读写字段，完成“对它做什么”。框架借此处理编译时尚不确定的类型与成员，但反射操作仍受访问控制和模块边界约束。

`Class` 对象是进入反射 API 的入口，代表一个运行时类型，不是该类型的业务实例。类型已知时用 `类型.class`，已有对象时用 `对象.getClass()` 获取实际类型，只有类名字符串时可用 `Class.forName(类名)`。后者的单参数形式还会触发目标类初始化。

## 内省如何连接到调用

```java
import java.lang.reflect.Method;

public class ReflectDemo {
    public static class Greeter {
        public String greet(String name) { return "你好，" + name; }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> type = Greeter.class;
        Method method = type.getMethod("greet", String.class); // 内省：找到方法签名
        Object target = type.getDeclaredConstructor().newInstance(); // 调用构造器
        Object result = method.invoke(target, "小明"); // 调用找到的方法
        System.out.println(result);
    }
}
```

这里先根据 `Class` 查出 `Method`，再创建接收调用的对象，最后执行方法。`Method` 是方法的反射表示，单纯取得它不会执行 `greet`。真实框架通常会缓存已解析的类型和成员信息，避免每次处理请求都重新内省。

## Spring Boot 和 Jackson 如何使用这两类能力

**Spring Boot 依赖的 Spring 容器：**启动时读取组件、配置类和注入点的信息，确定需要哪些 Bean，以及使用哪个构造器、字段或方法。创建 Bean 时调用构造器或 `@Bean` 工厂方法，再完成依赖注入。类路径扫描可以先读取类文件元数据，不等于把所有扫描都交给 Java 反射；反射主要参与对已选类型的进一步检查与对象操作。

**Jackson：**把类型的字段、getter、setter、构造器以及 `@JsonProperty`、`@JsonCreator` 等注解解析成属性映射。序列化时按映射读取对象属性；反序列化时选择构造器或工厂方法创建对象，再设置属性。内省决定 JSON 字段与 Java 成员如何对应，后续的读写和调用完成数据转换。

## Class 对象怎样取得，何时初始化

| 方式 | 适用情况 | 关键边界 |
| --- | --- | --- |
| `Greeter.class` | 源码中已知类型 | 单独求值不会触发 `Greeter` 初始化。 |
| `object.getClass()` | 已持有对象，要知道其实际类型 | `object` 不能为 `null`；对象创建时其类通常已经初始化。 |
| `Class.forName("com.example.Greeter")` | 类名在运行时才确定 | 找不到类会抛出 `ClassNotFoundException`；单参数形式会初始化目标类。 |

初始化会执行静态字段初始化和静态初始化块。如果只需按名称取得类型、暂不初始化，可用 `Class.forName(name, false, loader)`。同名类由不同定义类加载器加载时，是不同的运行时类型，也对应不同的 `Class` 对象。

## 参考

- [Java 17 Class API：类字面量与 forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)。
- [JLS 17 §12.4.1：类初始化发生的时机](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1)。
- [JLS 17 §12.2：类加载与运行时类型身份](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2)。
