# Java 基础参考资料与快照

调研日期：2026-09-14。本轮延续[此前资料调研](../knowledge-source-research.md)，针对“知识大纲能否支撑层级介绍与独立问题卡”进行评估；按本次要求不做许可筛选。

## 推荐组合

| 代号 | 资料 | 适合承担的职责 | 使用边界与本轮查阅程度 |
| --- | --- | --- | --- |
| G | [JavaGuide](https://github.com/Snailclimb/JavaGuide) | 检查高频问题覆盖，补充泛型、反射、SPI 等专题 | 已读基础专题导航、基础上中下篇标题，并抽读对象/相等/String 正文；已有专题总览值得参考，但其面试权重不等于教学先后 |
| B | [二哥的 Java 进阶之路](https://github.com/itwanger/toBeBetterJavaer) | 中文教学骨架，按语法、对象、字符串、异常、I/O 组织的文章便于细化总览 | 已检查目录，抽读对象与字符串比较正文；教程和面试汇总并存，应优先取教程路径，精简对话和推广内容 |
| O | [On Java 8 中文版](https://github.com/OnJava8/OnJava8) | 参考概念递进，以及从动机、例子过渡到机制的长篇讲解 | 已读 SUMMARY，并抽读相等性附录；以 Java 8 为背景，目录中的历史 API 和具体实现须另行核对，不能覆盖 Java 17 新特性 |
| D | [Dev.java Learn](https://dev.java/learn/) | 官方教学目录，补齐现代语言特性、日期时间和标准 API 的范围 | 已读取 Learn 目录，非全站正文审阅；站点随当前 Java 演进，选材需筛出 Java 17 可用部分 |
| J | [JLS 17](https://docs.oracle.com/javase/specs/jls/se17/html/index.html) 与 [Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/index.html) | 裁定语言/API 契约、标注版本 | 抽查引用、final、方法调用、引用相等，以及 Object/String/Map 契约；适合事实校验，不直接用规范目录当初学者学习顺序 |

如果只新增一个中文参考，优先 B；如果希望明显改善总览的概念连贯性，再加 O。G 已经适合提供问题集，继续堆更多面试汇编的收益小于补一套教学资料。

[CS-Notes](https://github.com/CyC2018/CS-Notes) 可在第二轮用来做精简目录的遗漏检查；本轮仅确认仓库信息，未克隆或逐篇审阅。advanced-java 主要服务分布式与高并发，不承担本轮 Java 基础骨架。资料星标和最近推送时间不作为质量结论。

## GitHub 固定快照

下列 commit 均来自本地实际克隆的 HEAD，链接固定到对应提交；无需依赖之后的默认分支内容。

| 代号 | 本地目录 | 分支 | 固定 commit |
| --- | --- | --- | --- |
| G | `knowledge-base/references/JavaGuide/` | main | `d76264cb4e000416c4adca06770ce014bd309150` |
| B | `knowledge-base/references/toBeBetterJavaer/` | master | `d05a68f8fd8f8de9955b8aa958c555114e24d6eb` |
| O | `knowledge-base/references/OnJava8/` | master | `4e513bb43d49fcdc5e46211c4d0442a29351925b` |

浅克隆使用 `--depth 1 --filter=blob:none --no-checkout`，随后以 `git sparse-checkout set --no-cone '*.md' '*.java'` 和 `git checkout` 检出文本。可用 `rg --files knowledge-base/references/JavaGuide/docs/java/basis` 一类命令定位内容；本轮未安装外部项目依赖。

## 大纲资料入口

以下为本地确认存在的路径；链接已固定提交。除下节标注的抽读正文外，主要依据目录、标题和专题导航判断覆盖，不表示所有文章已经审核。

| 来源 | 用途 | 仓库内路径 |
| --- | --- | --- |
| G | 基础导航及上/中/下篇 | [docs/java/basis/README.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/README.md) |
| G | 对象、相等与 String | [docs/java/basis/java-basic-questions-02.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/java-basic-questions-02.md) |
| G | 值传递 | [docs/java/basis/why-there-only-value-passing-in-java.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/why-there-only-value-passing-in-java.md) |
| G | 泛型与通配符 | [docs/java/basis/generics-and-wildcards.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/generics-and-wildcards.md) |
| G | 十进制数值 | [docs/java/basis/bigdecimal.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/bigdecimal.md) |
| G | 反射、代理、SPI（从基础导航继续） | [docs/java/basis/reflection.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/basis/reflection.md) |
| G | I/O 专题 | [docs/java/io/README.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/io/README.md) |
| G | Java 17 特性 | [docs/java/new-features/java17.md](https://github.com/Snailclimb/JavaGuide/blob/d76264cb4e000416c4adca06770ce014bd309150/docs/java/new-features/java17.md) |
| B | 语言语法 | [docs/src/basic-grammar/basic-data-type.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/basic-grammar/basic-data-type.md) |
| B | 对象与类 | [docs/src/oo/object-class.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/oo/object-class.md) |
| B | final | [docs/src/oo/final.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/oo/final.md) |
| B | 值传递 | [docs/src/basic-extra-meal/pass-by-value.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/basic-extra-meal/pass-by-value.md) |
| B | 不可变对象 | [docs/src/basic-extra-meal/immutable.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/basic-extra-meal/immutable.md) |
| B | 字符串比较 | [docs/src/string/equals.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/string/equals.md) |
| B | 字符串不可变性 | [docs/src/string/immutable.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/string/immutable.md) |
| B | 数组 | [docs/src/array/array.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/array/array.md) |
| B | 异常总览 | [docs/src/exception/gailan.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/exception/gailan.md) |
| B | 自动关闭资源 | [docs/src/exception/try-with-resources.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/exception/try-with-resources.md) |
| B | 泛型 | [docs/src/collection/generic.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/collection/generic.md) |
| B | Stream | [docs/src/java8/stream.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/java8/stream.md) |
| B | 文件 API | [docs/src/nio/paths-files.md](https://github.com/itwanger/toBeBetterJavaer/blob/d05a68f8fd8f8de9955b8aa958c555114e24d6eb/docs/src/nio/paths-files.md) |
| O | 全书目录（正文实际在 docs/book） | [SUMMARY.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/SUMMARY.md) |
| O | 对象基础 | [docs/book/03-Objects-Everywhere.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/docs/book/03-Objects-Everywhere.md) |
| O | 对象传递与不可变性 | [docs/book/Appendix-Passing-and-Returning-Objects.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/docs/book/Appendix-Passing-and-Returning-Objects.md) |
| O | 相等性与哈希 | [docs/book/Appendix-Understanding-equals-and-hashCode.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/docs/book/Appendix-Understanding-equals-and-hashCode.md) |
| O | 泛型 | [docs/book/20-Generics.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/docs/book/20-Generics.md) |
| O | 异常 | [docs/book/15-Exceptions.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/docs/book/15-Exceptions.md) |
| O | 函数式编程 | [docs/book/13-Functional-Programming.md](https://github.com/OnJava8/OnJava8/blob/4e513bb43d49fcdc5e46211c4d0442a29351925b/docs/book/13-Functional-Programming.md) |

<a id="sample-sources"></a>
## 样例具体取材与纠偏

| 样例内容 | 查阅位置 | 整理时的判断 |
| --- | --- | --- |
| 对象/引用、值传递 | G 基础中篇与值传递专题；B 对象文章；O 第 3 章及对象传递附录为扩展入口 | 正文抽样以 G/B 为主；使用 JLS 4.3.1 与 15.12.4.5 核对，明确引用值复制与对象复制的区别 |
| equals 的默认语义 | G 基础中篇 Object 部分；B `string/equals.md` | B 先用“equals 比内容”作简化，后文才说明 Object 默认实现；独立卡必须把默认/重写的条件提前写清 |
| equals 的约束和哈希用途 | O 相等性附录“equals规范”“哈希和哈希码”；G Object 部分 | 使用 Object API 校验契约，不把“必须为哈希容器自行定义两个方法”推广到已正确继承实现的类 |
| final 与不可变性 | JLS 4.12.4；B final/immutable 文章作为进一步查阅入口 | final 约束变量赋值，不等于冻结对象图；卡片以官方条款作依据 |
| 可变键 | O 附录提供键查找场景；Map API 明确边界 | Java Map 接口使用“行为未规定”，因此不写成任何实现、任何修改下一定查不到 |
| 字符串 | G 基础中篇 String 部分；B 字符串相等文章 | B 正文仍有“Java 17 是最新 LTS”的时效性表述；只提取有版本依据的语义，不沿用相对时间 |
| String 作为稳定键 | String、Object API | 本轮自己串联不可变性与哈希契约，不把常量池当作 equals/hashCode 正确工作的必要条件 |

官方抽样快照保存在 `knowledge-base/references/official/`：`learn.html`、`jls4.html`、`jls15.html`、`object.html`、`string.html`、`map.html` 及对应纯文本，访问日期均为 2026-09-14。JLS 和 API 固定 SE 17；Dev.java 页面不代表固定 Java 版本。

## v0.2 介绍正文的来源映射

总览正文专注介绍，资料链接集中在这里用于编辑追溯。v0.2 沿用已有快照重新组织内容，没有新增外部调研或扩大已审阅资料范围。

| 介绍 | 内容依据与使用范围 |
| --- | --- |
| [Java 基础](overview.md) | G 基础导航、B 教程目录、O SUMMARY 与 D Learn 目录提供领域覆盖；类型、对象与常用机制按职责综合介绍。涉及尚未专项采样的主题只介绍用途，不增加具体版本边界结论 |
| [对象、引用与生命周期](object-overviews.md) | G 基础中篇、值传递专题与 B 对象文章提供讲解素材；对象引用和 final 的关键语义依据已抽查的 JLS 17 第 4、15 章。生命周期只介绍初始化、可达性和资源责任，不展开实现流程 |
| [对象相等与哈希契约](equality-overview.md) | G Object 部分与 O 相等性附录提供主题背景；默认相等语义、哈希一致性与可变键边界依据已抽查的 Object/Map API。题目标识为自行编写的教学例子 |

## 仍需补齐的覆盖

日期时间已经由官方 Learn 目录确认有专题，但本轮未完成正文采样；模块强封装、record、sealed、泛型边界和 I/O 也尚未逐卡核实。它们已进入大纲，后续写卡时仍需查版本文档。三个中文来源可能互相引用，多个来源表述一致不等于多份独立验证。

这批样稿验证的是编辑组织能力。资料完整性、全量事实正确性、AI 自动组织效果和实际学习收益，都不能由本次小样本直接推出。
