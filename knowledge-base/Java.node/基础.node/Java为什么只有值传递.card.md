---
slug: java-pass-by-value
tags: [Java基础, 对象与引用]
order: 10
relations:
  related: [hashmap-collision]
---

Java 传递的是实参值的副本。对于对象参数，复制的是引用值，所以方法内外可以访问同一个对象。

## 原理解释

修改对象属性和给形参重新赋值是两回事：前者操作共享对象，后者只改变方法内部的变量。

## 示例

```java
void rename(Person person) {
  person.name = "新的名字";
}
```

## 常见误区

**能修改对象，不等于使用引用传递。** 本卡用于演示核心回答、多个详情章节、代码和关联跳转。
