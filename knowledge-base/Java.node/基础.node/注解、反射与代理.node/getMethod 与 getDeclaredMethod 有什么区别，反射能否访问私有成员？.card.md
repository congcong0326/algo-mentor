---
slug: java-getmethod-versus-getdeclaredmethod-and-private-access
tags: [Java基础, 反射, 方法查找, 访问控制]
order: 50
relations:
  prerequisites: [java-reflection-and-class-object-acquisition]
---

**`getMethod` 查找 `public` 方法，包括本类声明和从父类、接口继承的方法；`getDeclaredMethod` 只查当前类自己声明的方法，但不限制方法的访问级别。** 前者适合查公开 API，后者适合检查当前类的完整声明；两者都按方法名和参数类型查找。

反射**能找到**私有方法，不等于调用方就**有权执行**它。JDK 8 没有模块系统，在权限允许时，`setAccessible(true)` 通常能放宽普通 Java 访问检查。Java 9 引入模块系统后，跨命名模块访问非公开成员还受到模块边界约束；在 Java 17 中，目标包仅 `exports` 而没有向调用方 `opens` 时，不能靠 `setAccessible(true)` 强行访问其私有成员。

## 查找范围与访问级别是两个维度

```java
class Parent {
    public void run() {}
}

class Child extends Parent {
    private void reset() {}
}
```

| 对 `Child.class` 的独立查询 | 结果 |
| --- | --- |
| `getMethod("run")` | 找到继承的 `public` 方法 |
| `getDeclaredMethod("run")` | 抛出 `NoSuchMethodException`：`Child` 没有声明它 |
| `getDeclaredMethod("reset")` | 找到 `Child` 声明的 `private` 方法 |
| `getMethod("reset")` | 抛出 `NoSuchMethodException`：不是 `public` 方法 |

如果要检查父类自己声明的非公开方法，应沿父类链逐级使用 `getDeclaredMethod`；`Child.class.getDeclaredMethod(...)` 不会自动向上查。`getMethods()` 和 `getDeclaredMethods()` 的集合版本遵循相同的范围区别。

## 为什么 JDK 8 常能访问，而 Java 17 可能失败

JDK 8 的反射访问主要面对 `private`、包访问权限等语言级限制；`setAccessible(true)` 可以请求跳过这类检查，因此访问自己项目中的私有成员通常会成功。JDK 9 开始有了 JPMS 命名模块：`exports` 允许其他模块使用包中的公开 API，`opens` 才允许对包中的非公开成员做深度反射。Java 17 默认强封装 JDK 内部包，也使以前依赖反射访问 JDK 私有实现的代码更容易直接失败。

```java
java.lang.reflect.Method reset = Child.class.getDeclaredMethod("reset");
if (reset.trySetAccessible()) {
    reset.invoke(new Child());
} else {
    // 当前调用方不能放宽对这个成员的反射访问检查。
}
```

一个具体的失败场景是：`model.module` 中的 `com.example.model` 包只声明了 `exports`，其中公开类 `User` 有私有字段 `secret`；依赖它的 `reader.module` 尝试访问该字段。

`model.module` 的 `module-info.java`：

```java
module model.module {
    exports com.example.model;
}
```

`reader.module` 中查询并尝试访问 `User` 的字段：

```java
java.lang.reflect.Field field = User.class.getDeclaredField("secret");
field.trySetAccessible(); // false：目标包没有向 reader.module 开放
field.setAccessible(true); // 抛出 InaccessibleObjectException
```

`getDeclaredField` 能找到成员，只说明元数据可查；访问 `secret` 还需要模块允许深度反射。要让示例中的调用成功，可在 `model.module` 中声明 `opens com.example.model to reader.module;`。调用方与目标类在同一个模块时，放宽访问检查通常可以成功。即使在 Java 17 中，只在传统类路径下使用自己编写的类也通常不会碰到上述命名模块边界。

在上述模块边界下，`trySetAccessible()` 返回 `false`，`setAccessible(true)` 抛出 `InaccessibleObjectException`。它们不会修改成员原本的 `private` 声明。JDK 8 也不能笼统说“必定成功”：如果启用了安全管理器且它拒绝相应权限，访问检查放宽同样可能以 `SecurityException` 失败；只是没有 JPMS 的 `opens` 限制。

例如 Jackson 绑定非公开字段或构造器时，可能需要放宽反射访问检查。如果数据类型处于未开放的命名模块中，绑定就可能失败；可以提供可访问的构造器与属性访问方法，或向 Jackson 开放相应包。Spring 对非公开注入点进行处理时也会遇到同类边界。

## 参考

- [Java 17 Class API：方法查找](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)。
- [Java 17 AccessibleObject API：反射访问检查](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)。
