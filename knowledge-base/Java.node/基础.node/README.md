# Java 基础大纲维护说明

本大纲面向约五年开发经验研发的面试准备，范围为 Java 语言基础与常用标准 API。以 Java 17 为背景，重点关注语言语义、使用边界与常见误区，以章节组织知识、以卡片承载具体问题和追问。

## 本次范围

- 基础下统一为 10 个章节，卡片与文章直接放在所属章节内，不再保留细分子目录，不新增问题卡或文章。
- 原有 7 个子目录中的卡片与文章移至各自所属章节，卡片 slug 和正文保持不变；集合下的 HashMap 内容不变。
- 集合实现、并发、JVM 专题和 Spring 不纳入本大纲；涉及的集合 API 和运行环境只提供基础语义所需背景。
- 枚举、record、sealed 等语言特性按主题归位，后续内容注明版本。
- 目录名称直接使用主题名称，不附加编号。空叶子使用 `.gitkeep` 保留目录；范围与来源统一维护在本文。

## 章节与制卡归属

各章统一直接承载卡片与文章，新问题放在所属章节下，不按单个 API 或术语预建细分目录。以后若内容数量已影响查找，再整体评估分层方式并同步维护大纲。

| 章节 | 主要内容 |
| --- | --- |
| Java 程序与语言概览 | JDK、JRE 与 JVM，编译、字节码与运行，包、import 与类路径，Java 版本与兼容性 |
| 类型、变量与数组 | 基本类型、包装类与拆装箱、类型转换、变量与表达式、数组及集合转换边界 |
| 类、接口与面向对象 | 封装、继承与组合、多态、重载与重写、接口与抽象类、嵌套类及枚举等语言特性 |
| 对象、引用与初始化 | 引用、值传递与对象共享、初始化、static 与 final、equals 与 hashCode、对象表达、拷贝与不可变对象 |
| 字符串与文本处理 | String、常量池、字符串拼接、字符编码；正则与文本块按需补充 |
| 异常与资源管理 | 异常体系、传播与处理、finally、自动关闭资源与异常链 |
| 泛型与类型安全 | 类型参数、通配符与 PECS、类型擦除、桥接方法与堆污染 |
| Lambda、Stream 与 Optional | 函数式接口、Lambda、方法引用、变量捕获、Stream 流水线、惰性求值与单次消费、归约与收集、Optional 与缺失值表达 |
| 注解、反射与代理 | 注解生效方式、反射机制及边界、静态与动态代理；编译期注解处理和 SPI 按需补充 |
| 基础 IO 与序列化 | 字节流与字符流、缓冲与资源关闭、文件读写、序列化与兼容性 |

原“数组与基础数据操作”并入“类型、变量与数组”；原“Object 与对象契约”并入“对象、引用与初始化”；原“注解与反射”和“代理与 SPI”合并为“注解、反射与代理”。去掉空目录不代表删除这些知识点，也不要求逐项平均制卡。

优先补齐拆装箱、相等性与哈希契约、字符串、重载与重写、初始化与 final、异常和泛型等面试主干问题，再补充其他主题。具体问题围绕结论、原理、边界或反例、项目场景展开，避免将单个 API 或术语再次拆成目录。

## 参考依据

沿用 2026-09-14 已记录的 GitHub 固定快照，并复核相关基础目录；这是多源综合大纲，不代表对全部正文完成审核。

- [JavaGuide 基础专题](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/README.md)：语言基础、高频问题、泛型、反射、代理、SPI 和序列化的覆盖检查。
- [二哥的 Java 进阶之路](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/README.md)：语法、面向对象、数组、字符串、异常、I/O 与常用 API 的主题拆分。
- [On Java 8 中文版目录](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/SUMMARY.md)：初始化、封装、多态、泛型和函数式编程的概念联系。
- [历史调研记录](../../../docs/java-fundamentals-pilot/sources.md)：保存大纲建立时的查阅范围与事实校验记录，日常制卡无需读取；仅在调整主题范围、追溯来源或解决资料冲突时查阅相关段落。制卡以本文的 Java 17 版本和主题范围为准，按需核对当前问题相关的官方规范与 API。

## 大纲清单

完整的 10 个章节维护在 [outline.json](outline.json)，章节下不设子目录。清单只用于编辑和检查，系统仍从 `.node` 目录导入；标题不附加编号，展示按目录名排序，清单数组顺序不代表复习顺序。

从仓库根目录执行：

```sh
make knowledge-outline-preview KNOWLEDGE_OUTLINE=knowledge-base/Java.node/基础.node/outline.json
make knowledge-outline-apply KNOWLEDGE_OUTLINE=knowledge-base/Java.node/基础.node/outline.json
make knowledge-outline-check
```

已授权导入时执行 `make knowledge-import`，其中包含内容校验；用户明确要求单独校验时执行 `make knowledge-validate`。改名或移动前先预览差异，再迁移目录并保留已有卡片 slug；工具只补建目录，不自动迁移或删除内容。
