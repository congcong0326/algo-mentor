# Java 基础大纲维护说明

本大纲面向约五年开发经验的研发，范围为 Java 语言基础与常用标准 API。以 Java 17 为背景，重点关注语言语义、使用边界与常见误区。

## 本次范围

- 本次只建立章节与子主题目录，不新增问题卡或文章。
- 原有值传递卡片和对象与引用文章归入“对象、引用与初始化 → 引用、值传递与对象共享”，卡片 slug 保持不变。
- 集合实现、并发、JVM 专题和 Spring 不纳入本大纲；涉及的集合 API 和运行环境只提供基础语义所需背景。
- 枚举、record、sealed 等语言特性按主题归位，后续内容注明版本。
- 目录名称直接使用主题名称，不附加编号。空叶子使用 `.gitkeep` 保留目录；范围与来源统一维护在本文。

## 参考依据

沿用 2026-09-14 已记录的 GitHub 固定快照，并复核相关基础目录；这是多源综合大纲，不代表对全部正文完成审核。

- [JavaGuide 基础专题](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/README.md)：语言基础、高频问题、泛型、反射、代理、SPI 和序列化的覆盖检查。
- [二哥的 Java 进阶之路](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/README.md)：语法、面向对象、数组、字符串、异常、I/O 与常用 API 的主题拆分。
- [On Java 8 中文版目录](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/SUMMARY.md)：初始化、封装、多态、泛型和函数式编程的概念联系。
- [既有来源记录](../../../docs/java-fundamentals-pilot/sources.md)：查阅范围与事实校验记录；Java 17 特性、日期时间等后续写作仍需核对官方规范和 API。

## 大纲清单

完整的 14 章、72 个子主题维护在 [outline.json](outline.json)。清单只用于编辑和检查，系统仍从 `.node` 目录导入；标题不附加编号，展示按目录名排序。

从仓库根目录执行：

```sh
make knowledge-outline-preview KNOWLEDGE_OUTLINE=knowledge-base/Java.node/基础.node/outline.json
make knowledge-outline-apply KNOWLEDGE_OUTLINE=knowledge-base/Java.node/基础.node/outline.json
make knowledge-validate
```

已授权导入时执行 `make knowledge-import`。改名或移动前先预览差异，再迁移目录并保留已有卡片 slug；工具只补建目录，不自动迁移或删除内容。
