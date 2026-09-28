# JavaGuide Java 基础问题盘点与大纲评估

盘点日期：2026-09-23。依据工作区当前内容，以及[Java 基础主题 README](../Java.node/基础.node/README.md)指定的固定快照；本地 JavaGuide HEAD 已核对一致，资料仓库无工作区改动。本报告是选题与归属评估，不是已完成制卡或导入的清单。

## 结论

建议保留当前 **11 章、7 个已有内容子目录**。JavaGuide 的合适选题均能落到现有章节，没有证据支持恢复此前 14 章、72 个子主题的细目录，也无需照搬“上中下篇”“关键字”“语法糖”的来源目录。当前主要缺口是内容覆盖，其次是跨章节归属需要明确。

本次整理出 **85 个去重后的候选复习问题：6 个复用已有卡片，79 个建议新建**。这是按当前 Java 17、约五年经验研发的定位重新组织后的编辑方案，不是 JavaGuide 原站的题目总数。候选问题包含从正文提炼的追问；标题中新增的边界条件仍须在制卡时核对规范。

## 统计口径

- 资料范围：`docs/java/basis/` 中 15 个 Markdown 文件，即 1 个导航 README、3 篇面试题、11 篇专题文章。未扩展扫描集合、并发、JVM、独立 IO 或新版本特性专题。
- 原始问题：只数上、中、下篇代码块外的三级标题。其下的四级解释、README 高频问题和专题重复问题不再累加。
- 候选卡：按可独立评价“会不会”的问题去重、合并、拆分后计数。专题正文中的可用问题也纳入，不能用原始 84 题减去某个数直接推算。
- 已有覆盖：逐一对照知识库文件、slug 和相关内容；不把只有空目录视为已覆盖，也不把本报告中的候选视为已发布。
- 查阅深度：完整提取三篇问题标题，检查 11 篇专题结构，抽读与候选粒度及范围有关的正文。本轮不表示对所有原文答案完成 Java 17 事实审核。

| 面试题文章 | 原始问题数 | 分布 |
| --- | ---: | --- |
| 上篇 | 29 | 概念 8、语法 6、基本类型 7、变量 3、方法 5 |
| 中篇 | 21 | 面向对象 8、Object 5、String 8 |
| 下篇 | 34 | 异常 9、泛型 3、反射 3、代理 4、注解 2、SPI 3、序列化 4、IO 4、语法糖 2 |
| 合计 | **84** | 不包含专题文章的重复提问 |

## 现状与候选卡分布

Java 基础当前有 **27 张卡片、1 篇文章**；Java 集合下另有 HashMap 示例卡，不计入这 27 张。11 章中有 8 章尚无卡片。表中的“本轮复用”仅表示与本次候选重合，不能用它衡量已有其他卡片的价值。

| 现有章节 | 当前卡片 | 候选问题 | 本轮复用 | 建议新建 | 首批 P0 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Java 程序与语言概览 | 18 | 6 | 5 | 1 | 0 |
| 类型、变量与数组 | 0 | 10 | 0 | 10 | 4 |
| 类、接口与面向对象 | 0 | 9 | 0 | 9 | 3 |
| 对象、引用与初始化 | 1 | 12 | 1 | 11 | 9 |
| 字符串与文本处理 | 0 | 7 | 0 | 7 | 6 |
| 异常与资源管理 | 0 | 7 | 0 | 7 | 6 |
| 泛型与类型安全 | 0 | 9 | 0 | 9 | 5 |
| Lambda、Stream 与 Optional | 8 | 2 | 0 | 2 | 0 |
| 常用值类型 | 0 | 7 | 0 | 7 | 3 |
| 注解、反射与代理 | 0 | 10 | 0 | 10 | 0 |
| 基础 IO 与序列化 | 0 | 6 | 0 | 6 | 0 |
| 合计 | 27 | 85 | 6 | 79 | 36 |

79 张候选全部完成且不移除已有卡片时，基础主题将有 **106 张卡片**。这是容量预估，不是本次新增数量。已有运行环境深入卡、Stream 和 Optional 卡大多不在本轮 JavaGuide 基础题目中，仍应保留。

## 候选卡片清单

表中 ID 仅用于本报告核对，不是卡片 slug，也不用于目录编号。P0 为首批，P1 为第二批；“已有”保留原 slug。新增卡片的问法已按复习用途改写，实际制卡时应继续控制单卡边界。来源数字对应后面的 84 题逐项映射。

### Java 程序与语言概览

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| G01 | JDK、JRE 与 JVM 分别负责什么，它们是什么关系？ | 复用 [java-jdk-jre-jvm-relationship](<../Java.node/基础.node/Java 程序与语言概览.node/JDK、JRE 与 JVM.node/JDK、JRE 与 JVM 分别负责什么，它们是什么关系？.card.md>) | [1-03](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L43) |
| G02 | Java 字节码与机器码有什么区别？ | 复用 [java-bytecode-and-machine-code](<../Java.node/基础.node/Java 程序与语言概览.node/编译、字节码与运行.node/Java 字节码与机器码有什么区别？.card.md>) | [1-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L86) |
| G03 | Java 为什么能跨平台，跨平台能力有哪些边界？ | 复用 [java-cross-platform-capability-and-boundaries](<../Java.node/基础.node/Java 程序与语言概览.node/编译、字节码与运行.node/Java 为什么能跨平台，跨平台能力有哪些边界？.card.md>) | [1-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L86) |
| G04 | Java 是编译型语言还是解释型语言？ | 复用 [java-compiled-or-interpreted-language](<../Java.node/基础.node/Java 程序与语言概览.node/编译、字节码与运行.node/Java 是编译型语言还是解释型语言？.card.md>) | [1-05](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L113) |
| G05 | OpenJDK、Oracle JDK 与 Temurin 是什么关系？ | 复用 [java-openjdk-oracle-jdk-temurin](<../Java.node/基础.node/Java 程序与语言概览.node/JDK、JRE 与 JVM.node/OpenJDK、Oracle JDK 与 Temurin 是什么关系？.card.md>) | [1-07](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L177) |
| G06 | ClassNotFoundException 与 NoClassDefFoundError 有什么区别，如何定位？ | 新建 P1 | [3-02](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L26) |

### 类型、变量与数组

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| T01 | 基本类型与包装类型如何选择，默认值和 null 语义有什么区别？ | 新建 P0 | [1-15](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L567)、[1-16](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L635) |
| T02 | 包装类缓存为什么会影响 ==，值比较应该如何写？ | 新建 P0 | [1-17](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L663) |
| T03 | 自动装箱和拆箱何时发生，哪些表达式会触发空指针异常？ | 新建 P0 | [1-18](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L754)、[语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| T04 | 浮点数为什么不能精确表示某些十进制小数？ | 新建 P0 | [1-19](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L849)、[金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |
| T05 | 成员变量与局部变量在作用域、生命周期和初始化要求上有什么区别？ | 新建 P1 | [1-22](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L920) |
| T06 | 前缀与后缀自增在表达式求值中有什么区别？ | 新建 P1 | [1-12](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L305) |
| T07 | 左移、算术右移与逻辑右移有什么区别，移位距离有哪些边界？ | 新建 P1 | [1-13](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L351) |
| T08 | 增强 for 遍历数组和 Iterable 的机制有什么区别，给循环变量赋值会修改原元素吗？ | 新建 P1 | [语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| T09 | char 与 String 的表示和运算语义有什么区别？ | 新建 P1 | [1-24](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L993) |
| T10 | Java 整数运算溢出时会怎样，何时应使用 Math 的精确运算方法？ | 新建 P1 | [金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |

### 类、接口与面向对象

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| C01 | 封装解决什么问题，访问控制如何保护对象约束？ | 新建 P1 | [2-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L157) |
| C02 | 继承建立什么类型关系，哪些成员可以被继承或访问？ | 新建 P1 | [2-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L157) |
| C03 | 多态调用如何选择实际方法，哪些调用不参与实例方法的动态分派？ | 新建 P0 | [2-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L157)、[1-27](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1095) |
| C04 | 重载与重写有什么区别，方法选择发生在编译期还是运行期？ | 新建 P0 | [1-28](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1128) |
| C05 | 接口与抽象类分别适合什么场景，Java 17 下有哪些能力差异？ | 新建 P0 | [2-07](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L244) |
| C06 | 可变参数与数组有什么关系，与重载一起使用时有哪些歧义？ | 新建 P1 | [1-29](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1210)、[语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| C07 | 非静态内部类与静态嵌套类有什么区别，是否需要外围实例？ | 新建 P1 | [关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md)、[语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| C08 | 枚举与普通常量有什么区别，为什么更适合表达有限取值？ | 新建 P1 | [语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| C09 | assert 与参数校验有什么区别，为什么不能依赖断言处理业务输入？ | 新建 P1 | [语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |

### 对象、引用与初始化

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| O01 | Java 为什么只有值传递，对象参数为什么仍能修改字段？ | 复用 [java-pass-by-value](<../Java.node/基础.node/对象、引用与初始化.node/引用、值传递与对象共享.node/Java为什么只有值传递.card.md>) | [值传递](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/why-there-only-value-passing-in-java.md)、[2-02](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L98) |
| O02 | 没有显式构造器时编译器会做什么，哪些情况下仍无法实例化？ | 新建 P0 | [2-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L139) |
| O03 | 构造器能否继承或重写，this 与 super 如何形成构造链？ | 新建 P0 | [2-05](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L147)、[关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md) |
| O04 | 静态字段与实例字段分别属于谁，static 方法为什么不能直接访问实例成员？ | 新建 P0 | [1-23](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L969)、[1-26](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1073)、[1-27](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1095)、[关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md) |
| O05 | 静态初始化、实例初始化与构造器按什么顺序执行？ | 新建 P0 | [关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md) |
| O06 | final 修饰变量、方法与类分别限制什么，final 引用是否意味着对象不可变？ | 新建 P0 | [关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md) |
| O07 | final 变量什么时候才是编译期常量？ | 新建 P1 | [关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md)、[2-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L868) |
| O08 | ==、Object.equals 与重写后的 equals 分别比较什么？ | 新建 P0 | [2-03](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L105)、[2-10](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L462)、[2-17](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L743) |
| O09 | hashCode 的用途是什么，哈希相同能否推出对象相等？ | 新建 P0 | [2-11](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L529)、[2-12](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L548) |
| O10 | 为什么重写 equals 必须同时重写 hashCode？ | 新建 P0 | [2-13](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L585) |
| O11 | 引用拷贝、浅拷贝与深拷贝有什么区别？ | 新建 P0 | [2-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L308) |
| O12 | Object.clone 与 Cloneable 如何配合，为什么通常还需要处理嵌套可变对象？ | 新建 P1 | [2-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L308)、[2-09](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L411) |

### 字符串与文本处理

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| S01 | String、StringBuilder 与 StringBuffer 如何选择？ | 新建 P0 | [2-14](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L602) |
| S02 | String 的不可变性由什么保证，为什么仅有 final 引用不够？ | 新建 P0 | [2-15](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L647) |
| S03 | 字符串拼接何时使用 +，循环拼接为什么要考虑 StringBuilder？ | 新建 P0 | [2-16](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L694) |
| S04 | 字符串常量池解决什么问题，哪些字符串会共享引用？ | 新建 P0 | [2-18](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L747) |
| S05 | new String("abc") 的对象数量为什么依赖前提条件？ | 新建 P1 | [2-19](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L762) |
| S06 | String.intern 返回什么，原引用是否会因此改变？ | 新建 P0 | [2-20](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L837) |
| S07 | 字符串常量表达式与运行时拼接有什么区别？ | 新建 P0 | [2-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L868) |

### 异常与资源管理

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| E01 | Throwable、Exception 与 Error 有什么关系，捕获能力与处理策略有什么区别？ | 新建 P0 | [3-01](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L19) |
| E02 | 受检异常与非受检异常有什么区别，API 设计时如何选择？ | 新建 P0 | [3-03](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L31)、[3-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L57) |
| E03 | try、catch、finally 中的 return 或 throw 如何决定最终结果？ | 新建 P0 | [3-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L72) |
| E04 | finally 在什么条件下无法执行？ | 新建 P0 | [3-07](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L125) |
| E05 | try-with-resources 如何关闭多个资源，关闭顺序与初始化失败有什么关系？ | 新建 P0 | [3-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L157)、[语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| E06 | 业务执行和资源关闭同时抛异常时，主异常与 suppressed 异常如何保留？ | 新建 P0 | [3-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L157)、[语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| E07 | 异常包装与传播时如何保留 cause、上下文和堆栈，避免重复记录日志？ | 新建 P1 | [3-05](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L65)、[3-09](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L214) |

### 泛型与类型安全

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| F01 | 泛型解决什么类型安全问题，与直接使用 Object 有何区别？ | 新建 P0 | [3-10](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L224)、[泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F02 | 泛型类、泛型接口与泛型方法如何使用，静态泛型方法的类型参数属于谁？ | 新建 P1 | [3-11](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L236)、[3-12](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L319)、[泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F03 | 类型擦除会擦除什么，为什么某些泛型重载无法编译？ | 新建 P0 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F04 | 编译器为什么生成桥接方法，它如何维持泛型重写的多态语义？ | 新建 P1 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F05 | 为什么 List<Integer> 不是 List<Number> 的子类型？ | 新建 P0 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F06 | List<?>、List<Object> 与原始类型 List 有什么区别？ | 新建 P0 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F07 | extends 与 super 通配符分别允许怎样的读写，如何应用 PECS？ | 新建 P0 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F08 | 类型参数 T 与通配符 ? 如何选择，何时需要表达参数之间的类型关系？ | 新建 P1 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| F09 | 为什么不能直接 new T 或创建某些泛型数组，这些限制与运行时类型信息有什么关系？ | 新建 P1 | [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |

### Lambda、Stream 与 Optional

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| L01 | Lambda 如何由目标函数式接口确定类型？ | 新建 P1 | [语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |
| L02 | Lambda 是否等价于匿名内部类，源码简写与运行时实现应如何区分？ | 新建 P1 | [语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) |

### 常用值类型

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| V01 | 超过 long 范围的整数如何用 BigInteger 表示和运算？ | 新建 P1 | [1-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L902) |
| V02 | BigDecimal 的字符串构造、double 构造与 valueOf 有什么区别？ | 新建 P0 | [1-20](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L880)、[BigDecimal](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md)、[金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |
| V03 | BigDecimal 的 equals 与 compareTo 为什么可能给出不同的相等结论？ | 新建 P0 | [BigDecimal](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md)、[金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |
| V04 | BigDecimal 的 scale 与 precision 有什么区别，setScale 会改变什么？ | 新建 P1 | [BigDecimal](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md)、[金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |
| V05 | BigDecimal.divide 为什么可能抛异常，如何选择舍入规则？ | 新建 P0 | [BigDecimal](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md)、[金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |
| V06 | BigDecimal 运算是否修改原对象，为什么必须使用返回值？ | 新建 P1 | [BigDecimal](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md)、[金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |
| V07 | 金额何时用 long 存最小单位、何时用 BigDecimal，转换时如何处理单位、舍入与溢出？ | 新建 P1 | [金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) |

### 注解、反射与代理

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| R01 | 反射解决什么运行时问题，代价与适用边界是什么？ | 新建 P1 | [3-13](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L330)、[3-14](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L338)、[3-15](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L353)、[反射](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/reflection.md) |
| R02 | 获取 Class 的几种方式有什么区别，是否触发类初始化？ | 新建 P1 | [反射](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/reflection.md) |
| R03 | 反射如何查找成员、创建对象和调用方法，访问控制与模块边界有什么限制？ | 新建 P1 | [反射](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/reflection.md)、[3-14](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L338) |
| R04 | 注解为什么不会自动执行逻辑，谁负责解释注解？ | 新建 P1 | [3-20](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L444) |
| R05 | 注解的保留策略如何影响编译期处理和运行时读取？ | 新建 P1 | [3-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L464) |
| R06 | 静态代理与动态代理有什么区别，代理解决什么调用扩展问题？ | 新建 P1 | [3-17](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L414)、[代理](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/proxy.md) |
| R07 | JDK 动态代理如何通过接口和 InvocationHandler 分发调用？ | 新建 P1 | [3-16](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L396)、[代理](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/proxy.md) |
| R08 | JDK 动态代理与基于子类的 CGLIB 代理有哪些能力限制？ | 新建 P1 | [3-18](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L427)、[代理](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/proxy.md) |
| R09 | SPI 与 API 有什么区别，服务提供者由谁实现、由谁发现？ | 新建 P1 | [3-22](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L475)、[3-23](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L485)、[SPI](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/spi.md) |
| R10 | ServiceLoader 如何发现和加载提供者，SPI 的扩展收益和限制是什么？ | 新建 P1 | [3-24](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L500)、[SPI](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/spi.md) |

### 基础 IO 与序列化

| ID | 建议问题 | 处理 | 来源 |
| --- | --- | --- | --- |
| I01 | 字节流与字符流如何选择，字符集转换发生在哪里？ | 新建 P1 | [3-29](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L588)、[3-30](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L597) |
| I02 | 缓冲流等包装流如何体现装饰器模式？ | 新建 P1 | [3-31](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L606) |
| I03 | 序列化与反序列化解决什么问题，Serializable 声明了什么能力？ | 新建 P1 | [3-25](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L511)、[序列化](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/serialization.md) |
| I04 | Java 默认序列化如何处理 transient 与 static 字段？ | 新建 P1 | [3-26](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L554)、[序列化](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/serialization.md) |
| I05 | serialVersionUID 有什么作用，显式声明后是否就能保证任意版本兼容？ | 新建 P1 | [序列化](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/serialization.md) |
| I06 | 选择序列化格式应考虑哪些因素，Java 原生序列化有哪些局限？ | 新建 P1 | [3-27](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L566)、[3-28](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L572)、[序列化](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/serialization.md) |

## 专题文章如何去重吸收

这 11 篇文章不是 11 张卡片，也不是可以与 84 道标题题直接相加的另一组题数。其重叠内容已经归入上面的 85 个候选。

| 专题 | 处理方式 |
| --- | --- |
| [关键字](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-keyword-summary.md) | O03—O07、C07；与 static 原题合并 |
| [值传递](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/why-there-only-value-passing-in-java.md) | 复用 O01，保留既有 slug |
| [泛型与通配符](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) | F01—F09；补齐擦除、桥方法、不变性和 PECS |
| [反射](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/reflection.md) | R01—R03；机制概念合并，Class 获取和访问边界单列 |
| [代理](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/proxy.md) | R06—R08；仅保留语言机制与调用边界 |
| [序列化](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/serialization.md) | I03—I06；serialVersionUID 来自正文而非三级问题标题 |
| [SPI](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/spi.md) | R09—R10；不照搬历史 ServiceLoader 源码 |
| [BigDecimal](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md) | V02—V06；构造、相等、位数、舍入、不可变性 |
| [金额类型](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/money-long-vs-bigdecimal.md) | T04、T10、V02—V07；数据库字段细节不纳入基础卡 |
| [语法糖](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/syntactic-sugar.md) | T03、T08、C06—C09、E05—E06、F03、L01—L02；按语言主题分配 |
| [Unsafe](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/unsafe.md) | 暂不纳入：内存、CAS、屏障和线程调度超出当前范围 |

## 原始 84 题逐项去向

保留原文问题标题便于复核；“并入”表示作为其他卡片或导读的内容，不增加独立卡片数。

### 上篇

| 原题 | 原始标题 | 建议去向 |
| --- | --- | --- |
| [1-01](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L15) | Java 语言有哪些特点？ | 并入导读：跨平台见 G03，其他特点随具体卡片解释 |
| [1-02](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L34) | Java SE vs Java EE | 暂缓：平台生态概览，放文章背景即可 |
| [1-03](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L43) | JVM vs JDK vs JRE | 复用 G01 |
| [1-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L86) | 什么是字节码？采用字节码的好处是什么？ | 复用 G02、G03 |
| [1-05](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L113) | 为什么说 Java 语言“编译与解释并存”？ | 复用 G04 |
| [1-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L134) | AOT 有什么优点？为什么不全部使用 AOT 呢？ | 暂缓：AOT 与运行时优化留待 JVM 专题 |
| [1-07](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L177) | Oracle JDK vs OpenJDK | 复用 G05 |
| [1-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L220) | Java 和 C++ 的区别？ | 暂缓：跨语言宽泛比较不单独制卡 |
| [1-09](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L234) | 注释有哪几种形式？ | 不单独制卡：作为写作和代码示例规范 |
| [1-10](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L271) | 标识符和关键字的区别是什么？ | 不单独制卡：词法入门查阅项 |
| [1-11](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L277) | Java 语言关键字有哪些？ | 不单独制卡：按语义归入 O06、O04 等卡片 |
| [1-12](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L305) | 自增自减运算符 | 改写 T06 |
| [1-13](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L351) | 移位运算符 | 改写 T07 |
| [1-14](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L473) | continue、break 和 return 的区别是什么？ | 不单独制卡：控制流基础可放示例；finally 见 E03 |
| [1-15](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L567) | Java 中的几种基本数据类型了解么？ | 合并改写 T01 |
| [1-16](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L635) | 基本类型和包装类型的区别？ | 合并改写 T01 |
| [1-17](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L663) | 包装类型的缓存机制了解么？ | 改写 T02 |
| [1-18](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L754) | 自动装箱与拆箱了解吗？原理是什么？ | 改写 T03 |
| [1-19](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L849) | 为什么浮点数运算的时候会有精度丢失的风险？ | 改写 T04 |
| [1-20](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L880) | 如何解决浮点数运算的精度丢失问题？ | 改写 V02，并与 V05、V07 关联 |
| [1-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L902) | 超过 long 整型的数据应该如何表示？ | 改写 V01 |
| [1-22](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L920) | 成员变量与局部变量的区别？ | 改写 T05 |
| [1-23](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L969) | 静态变量有什么作用？ | 合并改写 O04 |
| [1-24](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L993) | 字符型常量和字符串常量的区别？ | 改写 T09 |
| [1-25](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1026) | 什么是方法的返回值？方法有哪几种类型？ | 不单独制卡：返回值定义过于基础 |
| [1-26](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1073) | 静态方法为什么不能调用非静态成员？ | 合并改写 O04 |
| [1-27](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1095) | 静态方法和实例方法有何不同？ | 分流 O04、C03 |
| [1-28](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1128) | 重载和重写有什么区别？ | 改写 C04 |
| [1-29](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-01.md#L1210) | 什么是可变长参数？ | 改写 C06 |

### 中篇

| 原题 | 原始标题 | 建议去向 |
| --- | --- | --- |
| [2-01](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L17) | 面向对象和面向过程的区别 | 并入导读：具体能力由 C01—C03 承载 |
| [2-02](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L98) | 创建一个对象用什么运算符？对象实例与对象引用有何不同？ | 并入已有 O01；new 语法不另建卡 |
| [2-03](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L105) | 对象的相等和引用相等的区别 | 合并改写 O08 |
| [2-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L139) | 如果一个类没有声明构造方法，该程序能正确执行吗？ | 改写 O02 |
| [2-05](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L147) | 构造方法有哪些特点？是否可被 override? | 改写 O03 |
| [2-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L157) | 面向对象三大特征 | 拆分 C01、C02、C03 |
| [2-07](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L244) | 接口和抽象类有什么共同点和区别？ | 改写 C05 |
| [2-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L308) | 深拷贝和浅拷贝区别了解吗？什么是引用拷贝？ | 拆分 O11、O12 |
| [2-09](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L411) | Object 类的常见方法有哪些？ | 按契约拆分：本轮取 O12；相等性见 O08—O10，线程协作方法留并发专题 |
| [2-10](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L462) | == 和 equals() 的区别 | 合并改写 O08 |
| [2-11](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L529) | hashCode() 有什么用？ | 合并改写 O09 |
| [2-12](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L548) | 为什么要有 hashCode？ | 合并改写 O09 |
| [2-13](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L585) | 为什么重写 equals() 时必须重写 hashCode() 方法？ | 改写 O10 |
| [2-14](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L602) | String、StringBuffer、StringBuilder 的区别？ | 改写 S01 |
| [2-15](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L647) | String 为什么是不可变的？ | 改写 S02 |
| [2-16](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L694) | 字符串拼接用“+” 还是 StringBuilder? | 改写 S03 |
| [2-17](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L743) | String#equals() 和 Object#equals() 有何区别？ | 合并改写 O08，String 作为反例 |
| [2-18](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L747) | 字符串常量池的作用了解吗？ | 改写 S04 |
| [2-19](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L762) | String s1 = new String("abc");这句话创建了几个字符串对象？ | 改写 S05，必须说明计数前提 |
| [2-20](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L837) | String#intern 方法有什么作用？ | 改写 S06 |
| [2-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md#L868) | String 类型的变量和常量做“+”运算时发生了什么？ | 改写 S07，并关联 O07 |

### 下篇

| 原题 | 原始标题 | 建议去向 |
| --- | --- | --- |
| [3-01](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L19) | Exception 和 Error 有什么区别？ | 改写 E01 |
| [3-02](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L26) | ClassNotFoundException 和 NoClassDefFoundError 的区别 | 改写 G06；基础仅保留异常辨析与排查入口 |
| [3-03](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L31) | Checked Exception 和 Unchecked Exception 有什么区别？ | 合并改写 E02 |
| [3-04](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L57) | 你更倾向于使用 Checked Exception 还是 Unchecked Exception？ | 合并改写 E02，去除无条件的个人偏好结论 |
| [3-05](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L65) | Throwable 类常用方法有哪些？ | 并入 E07；不另建 API 罗列卡 |
| [3-06](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L72) | try-catch-finally 如何使用？ | 改写 E03 |
| [3-07](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L125) | finally 中的代码一定会执行吗？ | 改写 E04 |
| [3-08](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L157) | 如何使用 `try-with-resources` 代替 `try-catch-finally`？ | 拆分 E05、E06 |
| [3-09](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L214) | 异常使用有哪些需要注意的地方？ | 改写 E07 |
| [3-10](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L224) | 什么是泛型？有什么作用？ | 改写 F01 |
| [3-11](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L236) | 泛型的使用方式有哪几种？ | 改写 F02 |
| [3-12](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L319) | 项目中哪里用到了泛型？ | 并入 F02 的项目示例，不重复建卡 |
| [3-13](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L330) | 什么是反射？ | 合并改写 R01 |
| [3-14](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L338) | 反射有什么优缺点？ | 分流 R01、R03 |
| [3-15](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L353) | 反射的应用场景？ | 并入 R01 示例 |
| [3-16](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L396) | 如何实现动态代理？ | 改写 R07 |
| [3-17](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L414) | 静态代理和动态代理有什么区别？ | 改写 R06 |
| [3-18](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L427) | JDK 动态代理和 CGLIB 动态代理有什么区别？ | 改写 R08，仅讨论机制与限制 |
| [3-19](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L432) | 介绍一下动态代理在框架中的实际应用场景 | 作为 R06、R07 场景；Spring 事务等细节归 Spring 专题 |
| [3-20](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L444) | 何谓注解？ | 改写 R04 |
| [3-21](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L464) | 注解的解析方法有哪几种？ | 改写 R05 |
| [3-22](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L475) | 何谓 SPI? | 合并改写 R09 |
| [3-23](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L485) | SPI 和 API 有什么区别？ | 合并改写 R09 |
| [3-24](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L500) | SPI 的优缺点？ | 改写 R10 |
| [3-25](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L511) | 什么是序列化？什么是反序列化？ | 改写 I03 |
| [3-26](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L554) | 如果有些字段不想进行序列化怎么办？ | 改写 I04 |
| [3-27](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L566) | 常见序列化协议有哪些？ | 合并改写 I06，不逐个协议建卡 |
| [3-28](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L572) | 为什么不推荐使用 JDK 自带的序列化？ | 合并改写 I06 |
| [3-29](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L588) | Java IO 流了解吗？ | 合并改写 I01 |
| [3-30](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L597) | I/O 流为什么要分为字节流和字符流呢？ | 合并改写 I01 |
| [3-31](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L606) | Java IO 中的设计模式有哪些？ | 改写 I02，仅讨论包装流的使用与职责 |
| [3-32](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L610) | BIO、NIO 和 AIO 的区别？ | 暂缓：BIO、NIO、AIO 的网络模型归 IO 或网络专题 |
| [3-33](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L616) | 什么是语法糖？ | 作为概念说明，分配到具体机制卡 |
| [3-34](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-03.md#L631) | Java 中有哪些常见的语法糖？ | 分流 T03、T08、C06—C09、E05—E06、F03、L01—L02；不另建语法糖目录 |

## 大纲是否需要重构

### 保留骨架，先明确归属

当前 11 章已足以承载本轮所有候选。建议先沿用目录，在后续维护主题 README 时补充下面的归属规则；本报告不修改正式 outline.json，也不迁移卡片。

| 容易混放的内容 | 建议唯一主归属 | 与其他章节的关系 |
| --- | --- | --- |
| 构造器、this、super、初始化顺序 | 对象、引用与初始化 | 类与继承章节只做前置说明 |
| static、final 的对象语义 | 对象、引用与初始化 | static 方法的分派边界在多态卡中解释；静态嵌套类归面向对象 |
| ==、equals、hashCode、clone | 对象、引用与初始化 | String 与 BigDecimal 只讲各自特例，通过 slug 关联通用契约 |
| 浮点表示误差 | 类型、变量与数组 | BigDecimal 构造、精度和金额应用归常用值类型 |
| 受检异常、finally、自动关闭、suppressed | 异常与资源管理 | IO 卡只引用资源关闭机制，不重复建相同问题 |
| 类缺失与初始化失败的异常辨析 | Java 程序与语言概览下的包、import 与类路径 | 解释排查入口，深入加载过程留 JVM 专题 |
| 类型擦除、桥方法、PECS | 泛型与类型安全 | 不放到 JVM 或反射章节重复讲 |
| 注解、反射、代理、SPI | 注解、反射与代理 | 在主题 README 的主要内容中明确写出 SPI；当前无需为它单建章 |
| “关键字”“语法糖” | 按实际语言语义分配到已有章 | 两者是来源文章的组织方式，不必变成知识库目录 |

### 何时才需要拆分目录

这些是后续内容增长时的判断标准，不是本次预建大纲：

- 本轮“对象、引用与初始化”将有 12 张相关卡，外加未来不可变性等内容；只有浏览已明显不便时，再考虑“初始化与类成员”“相等性与对象契约”“复制与不可变性”等分组。已有引用子目录继续保留。
- “注解、反射与代理”本轮只有 10 张候选，四种机制之间有联系；先用卡片标签和关系区分。若后续各组独立扩展到多张追问，再判断是否拆分。
- “类型、变量与数组”本轮只有 10 张候选，没有理由恢复每个运算符或 API 一个空子目录。
- 目录负责导航。卡片展示顺序用 order，学习依赖与追问关系用 prerequisites 和 followups 表达；这些关系不等于复习队列的调度规则。outline.json 数组顺序不会改变页面按目录名称排序的行为。

### JavaGuide 基础目录不能覆盖的内容

以下属于当前主题既定范围的补齐方向，但**未计入本轮 85 个来源候选**。不要因来源暂缺就删掉现有章节：

| 方向 | 需要额外补充的典型问题 |
| --- | --- |
| 数组与 API 边界 | 数组协变与运行时检查、数组复制、Arrays.asList 的视图与修改边界 |
| 对象契约 | equals 对称性与继承、可变对象作键、防御性拷贝、不变性与 final 的边界 |
| Lambda 与 Stream | effectively final、方法引用、map 与 flatMap、reduce 与 collect、副作用；保留现有 Stream/Optional 卡 |
| 现代语言特性 | record、sealed、switch 表达式与文本块；按 Java 17 支持范围落到所属章节 |
| 文本处理 | UTF-16 代码单元与码点、字符集编解码、正则的使用边界 |
| 常用值类型 | Instant、LocalDateTime、ZonedDateTime 的语义、时区与格式化 |
| IO API | Path、Files、缓冲、flush、大文件逐步读取；与网络 IO 模型分开组织 |

## 建议实施顺序

首批选择表中 **36 张 P0**：包装与拆箱、重载重写、多态与接口、初始化与 final、相等与哈希、字符串、异常与资源、泛型主干、BigDecimal。这些章当前多数为空，优先填充能最快改善知识库的面试覆盖。

第二批补 **43 张 P1**：操作符与数组遍历、嵌套类与枚举、完整泛型边界、反射、代理、SPI、序列化和更多值类型。新卡默认直接放所属章节；与 7 个保留子目录主题一致的卡继续进入对应子目录。

已有 6 张重合卡直接复用，不因更换来源重新创建 slug。候选之间的关系只有在对应卡片实际存在时才写入元数据，报告 ID 不进入 relations。

选题可以引入，答案仍须按 Java 17 自主组织并注明具体来源。例如 String 拼接不能统一按 Java 8 字节码讲解，ServiceLoader 的历史实现不能当作 Java 17 全貌，BigDecimal 的 scale 与 precision 必须区分，序列化格式也不能采用“JSON 一般不会选择”这种不带场景的判断。上述是后续制卡审核点，本轮没有生成答案卡片。

本次仅新增本盘点报告。未修改大纲、移动内容、创建正式卡片或执行导入；未运行 knowledge-validate。已用只读统计核对原始标题数、候选编号、来源引用、已有 slug 及章节计数。
