---
slug: java-how-annotations-take-effect
tags: [Java基础, 注解, 元数据]
order: 10
---

**注解是附着在程序元素上的元数据，声明或标注它本身不会自动执行业务逻辑。** 注解的效果来自读取它的代码：编译器可以识别特定注解或运行注解处理器；运行时程序可以读取保留下来的注解，再按约定执行相应操作。

因此要判断一个注解是否“生效”，应看谁读取它、在什么阶段读取，以及读取后做了什么。比如 `@Override` 由编译器检查重写关系；自己定义一个 `@Audit`，如果没有任何处理代码读取它，就不会自动产生审计行为。

## 注解从声明到产生效果

1. 用 `@interface` 声明注解类型，再将注解标在允许的程序元素上。注解可以携带值，供读取方使用。
2. 编译阶段，编译器处理它认识的标准注解；注解处理器也可以读取注解并生成源码等产物。仅声明一个自定义注解，不会让编译器猜测业务含义。
3. 运行阶段，程序或框架可以通过反射等方式读取具有 `RUNTIME` 保留策略的注解，并自行决定后续行为。没有运行时代码去读取和处理，注解就不会触发调用、校验或记录日志。

这三个阶段不要求全部发生。例如 `@Override` 的检查发生在编译期，不依赖运行时反射；运行时框架使用的注解则需要相应的运行时读取与处理逻辑。

## 一个没有处理器的自定义注解

```java
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface Audit {}

class OrderService {
    @Audit
    void submit() {
        // 这里不会因为标了 @Audit 就自动记录审计日志。
    }
}
```

`RUNTIME` 使 `@Audit` 可以被反射读取，但这只提供了信息。要产生审计行为，还需要有代码找到带 `@Audit` 的方法，并在合适的调用时机执行记录逻辑。标注注解和执行业务动作是两个步骤。

## 参考

- [JLS 17 §9.6：注解接口](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6)。
- [JLS 17 §9.7：注解](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7)。
- [Java 17 RetentionPolicy API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)。
