---
slug: java-target-and-inherited-annotation-boundaries
tags: [Java基础, 注解, Target, Inherited]
order: 30
relations:
  prerequisites: [java-annotation-retention-and-reflection]
---

**`@Target` 限制一种注解可以标在哪里；`@Inherited` 决定查询子类的类注解时，是否沿父类链查找这种注解。** 例如 `@Target(METHOD)` 只允许标注方法，标在类上会编译失败；一个带 `@Inherited` 的类注解若只标在父类上，仍可通过子类的 `Class.getAnnotation` 查到。

`@Inherited` 只对**类上的注解**和**类继承链**起作用：它不会让实现类继承接口注解，也不会让重写方法继承父类方法上的注解。要在运行时反射读取，还需要注解具有 `RUNTIME` 保留策略。

## @Target 约束标注位置

`@Target` 的值来自 `ElementType`，可以指定一个或多个位置，例如 `TYPE` 对应类或接口声明，`METHOD` 对应方法声明，`FIELD` 对应字段声明。它在编译期检查注解的使用位置，不决定注解保留多久。

常见例子是 JUnit Jupiter 的 `@Test`：它允许标在方法和注解类型上，不能直接标在普通类上。Lombok 的 `@Getter` 则可标在字段或类上，分别为单个字段或类中适用的字段生成 getter。

## @Inherited 如何沿父类链查找

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface Marker {}

@Marker
interface Contract {}

@Marker
class Parent {
    @Marker void work() {}
}

class Child extends Parent {
    @Override void work() {}
}

class FromInterface implements Contract {}
```

`Child.class.getAnnotation(Marker.class)` 能查到父类上的 `@Marker`，但 `Child.class.getDeclaredAnnotation(Marker.class)` 返回 `null`：注解没有被复制到子类声明上。`FromInterface.class.getAnnotation(Marker.class)` 也返回 `null`，因为 `@Inherited` 不沿接口关系查找。

`Child.class.getDeclaredMethod("work").getAnnotation(Marker.class)` 返回 `null`。虽然 `Marker` 带有 `@Inherited`，但重写方法不会因此继承父类方法上的注解；需要这类语义的框架必须自行查找父方法。若子类直接标了同类型的类注解，查询时会优先得到子类自己的标注。

JUnit Jupiter 的 `@TestInstance` 是真实例子：它以 `TYPE` 为标注位置，并带有 `@Inherited`。在测试基类上指定实例生命周期后，子类可继承这个配置。JUnit 也支持从测试接口取得配置，但那是框架自己的查找规则，不能归因于 Java 的 `@Inherited`。

## 参考

- [JLS 17 §9.6.4.1：@Target](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.1)。
- [JLS 17 §9.6.4.3：@Inherited](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.3)。
- [Java 17 Inherited API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html)。
- [JUnit Jupiter `@Test` 声明](https://github.com/junit-team/junit5/blob/6c8ca116941611df2d4e2e8319ab8cb3e520afe7/junit-jupiter-api/src/main/java/org/junit/jupiter/api/Test.java)与[Lombok `@Getter` 声明](https://github.com/projectlombok/lombok/blob/e50d44b071c955d85f7b94a7cf06142c7ff2622b/src/core/lombok/Getter.java)。
- [JUnit Jupiter `@TestInstance` 声明与继承说明](https://github.com/junit-team/junit5/blob/6c8ca116941611df2d4e2e8319ab8cb3e520afe7/junit-jupiter-api/src/main/java/org/junit/jupiter/api/TestInstance.java)。
