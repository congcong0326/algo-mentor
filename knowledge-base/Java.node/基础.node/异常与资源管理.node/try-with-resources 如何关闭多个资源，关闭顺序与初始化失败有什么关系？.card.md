---
slug: java-try-with-resources-order-and-initialization-failure
tags: [Java基础, try-with-resources, 资源关闭]
order: 50
relations:
  related: [java-finally-return-throw-precedence, java-try-with-resources-primary-and-suppressed]
---

`try-with-resources` 按声明顺序从左到右初始化资源。全部初始化成功后才执行 `try` 主体；离开时按相反顺序关闭资源，例如先打开 A、再打开 B，就先关闭 B、再关闭 A。主体执行 `return` 或抛异常，也要先完成关闭。

如果初始化 B 时失败，后面的资源不再初始化，主体也不执行；此前成功初始化的 A 仍会被关闭。**失败的初始化没有把 B 交给该语句管理，因此语法不会调用 `B.close()`。** 关闭完成后，才进入同一语句的 `catch` 或 `finally`。

## 关闭顺序取决于成功初始化的资源

可以把资源声明看作逐层进入的作用域：先获得 A，再获得 B；退出时先清理内层的 B，再清理外层的 A。无论主体正常结束、返回还是抛异常，都会按这个逆序尝试关闭。即使 B 的 `close()` 抛异常，也会继续尝试关闭 A；异常如何保留由另一张卡片说明。

| 执行过程 | 主体是否执行 | 自动关闭顺序 |
| --- | --- | --- |
| A、B、C 都初始化成功 | 执行 | C → B → A |
| B 初始化失败 | 不执行，C 也不初始化 | A |
| A 初始化失败 | 不执行，B、C 也不初始化 | 没有资源可关闭 |

## 初始化失败时先关闭，再进入 catch

```java
static final class Resource implements AutoCloseable {
    private final String name;

    Resource(String name) { this.name = name; }

    @Override
    public void close() { System.out.println("关闭 " + name); }
}

static Resource open(String name, boolean fail) {
    System.out.println("初始化 " + name);
    if (fail) throw new IllegalStateException("初始化失败 " + name);
    return new Resource(name);
}

static void demo() {
    try (Resource a = open("A", false);
         Resource b = open("B", true);
         Resource c = open("C", false)) {
        System.out.println("执行主体");
    } catch (IllegalStateException e) {
        System.out.println("捕获 " + e.getMessage());
    }
}
```

调用 `demo()` 后，输出依次是 `初始化 A`、`初始化 B`、`关闭 A`、`捕获 初始化失败 B`。没有初始化 C，也没有执行主体或调用 B 的 `close()`。如果 B 的初始化过程已经取得其他内部资源，`open` 或构造逻辑需要自行清理；外层语句只负责它已经成功接管的资源。

资源声明的类型必须实现 `AutoCloseable`；声明得到的引用若为 `null`，关闭时会跳过它。初始化或主体抛异常且关闭也失败时，具体的异常保留规则见关联卡片。

## 参考

- [JLS 17 §14.20.3：try-with-resources 语句](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3)。
- [JLS 17 §14.20.3.1：多资源初始化与关闭规则](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.1)。
