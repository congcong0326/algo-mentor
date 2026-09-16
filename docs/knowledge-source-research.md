# Java 与计算机八股文资料源候选调研

> 本文是 2026-09-14 的来源调研记录。当前正式内容维护已经切换到 [`knowledge-base/README.md`](../knowledge-base/README.md) 的目录导入契约；这里的“seed”、字段草案和批量资料计划不再是实现要求。新增内容请写入 `.node` 目录下的 `.card.md` / `.article.md`，具体格式、关系和许可证说明以维护手册为准。

调研日期：2026-09-14

## 目标与筛选口径

面向有约 5 年 Java 后端经验、准备中高级面试的用户，优先选择能覆盖以下面试域、且便于拆成知识卡片和复习计划的资料：

- Java 核心、集合、并发、JVM 与性能诊断
- Spring 生态、数据库、Redis、消息队列和微服务
- 分布式、高可用、系统设计与故障排查
- 计算机网络、操作系统、数据结构与算法基础

评价维度为内容深度与面试相关性、目录结构化程度、维护状态、许可证可用性，以及与 algo-mentor 现有学习计划和复盘能力的适配度。GitHub 星标只作为影响力信号，不作为质量结论。

## 候选资料源

| 优先级 | 资料源 | 主要覆盖 | 结构与维护信号 | 许可证与接入建议 |
| --- | --- | --- | --- | --- |
| P0 | [Snailclimb/JavaGuide](https://github.com/Snailclimb/JavaGuide) | Java 基础、集合、并发、JVM、数据库、网络、操作系统、分布式、系统设计 | 约 158k stars；JavaGuide 的 `java/concurrent` 路径最近提交为 2026-09-05，目录按面试主题拆分 | Apache-2.0；可在保留版权与许可证声明的前提下整理衍生内容。适合作为第一主源 |
| P0 | [doocs/advanced-java](https://github.com/doocs/advanced-java) | 高并发、Redis、MQ、分库分表、分布式事务、RPC、微服务、高可用 | 约 79k stars；高并发路径最近提交为 2026-04-17；主题直接对应中高级后端面试 | CC BY-SA 4.0；需要署名，改编内容需同许可发布。首版建议做来源链接和自写卡片，避免直接复制正文 |
| P0 | [donnemartin/system-design-primer](https://github.com/donnemartin/system-design-primer) | 系统设计方法、容量估算、缓存、队列、分片、可靠性、面试题与 Anki 卡片 | 约 370k stars；README 最近提交为 2026-03-20；有中文 README 和系统化目录 | CC BY 4.0；可改编但必须署名。适合作为“5 年经验系统设计”计划主源 |
| P1 | [iluwatar/java-design-patterns](https://github.com/iluwatar/java-design-patterns) | Java 设计模式、并发与微服务模式的可运行示例 | 约 95k stars；2026-09-13 仍有提交；每个模式独立目录，代码可验证 | MIT（仓库 `LICENSE.md`）；保留版权声明即可复用示例。适合补充设计模式和源码阅读卡片 |
| P1 | [TheAlgorithms/Java](https://github.com/TheAlgorithms/Java) | 数据结构、算法、并发及 Java 实现示例 | 约 66k stars；MIT；按算法目录组织、代码可运行 | MIT；适合作为算法/实现细节校验源，不承担八股主线 |
| P1 | [krahets/hello-algo](https://github.com/krahets/hello-algo) | 图解数据结构与算法，多语言（含 Java）实现 | 约 130k stars；持续维护，中文内容友好 | CC BY-NC-SA 4.0；非商业限制明显。仅用于外链或非商业学习场景，不能默认打包进商业服务 |
| P2 | [CyC2018/CS-Notes](https://github.com/CyC2018/CS-Notes) | Java、网络、OS、数据库、系统设计和 LeetCode | 约 186k stars；`notes` 最近提交为 2021-04-18，内容基本稳定但更新较慢 | 未发现 LICENSE；默认按“保留所有权利”处理。只做外链/目录参考，不能复制或改编正文 |
| P2 | [jwasham/coding-interview-university](https://github.com/jwasham/coding-interview-university) | 长周期计算机基础与软件工程学习路线 | 约 360k stars；CC BY-SA 4.0；覆盖很广 | 适合用来补充 OS/网络/系统设计的学习顺序，不适合作为 Java 八股的直接内容源 |

官方规范和项目文档（JDK、Spring、Kafka、Redis、MyBatis）应作为事实校验源和外链，而不是整段搬运的面试资料源。版本相关结论必须记录文档版本或访问日期。

## 面向 5 年 Java 面试的首批组合

建议先做四条可独立发布、也可组合成总计划的路线：

1. **Java 核心与 JVM**：JavaGuide 的基础/集合/并发/JVM，配合 OpenJDK 或官方规范链接校验；重点放在内存模型、锁与线程池、GC、类加载和性能诊断。
2. **高并发与分布式**：advanced-java 的高并发、Redis、MQ、分布式系统和高可用；每张卡增加幂等、重试、限流、降级、消息可靠性等生产约束。
3. **系统设计面试**：system-design-primer 的方法、容量估算、缓存/队列/分片/一致性，再用项目自身的缓存、SSE、持久化队列案例做迁移练习。
4. **设计模式与工程实现**：java-design-patterns 的可运行示例，选择工厂、策略、模板、观察者、责任链、装饰器、代理、状态、适配器等高频模式，要求回答适用边界和取舍。

算法题库已有 LeetCode 接入，因此 TheAlgorithms/Java 和 hello-algo 先作为 Java 实现与基础概念的补充，不重复建设题单。

## 接入和授权边界

- 运行时内容只保存 algo-mentor 自己撰写的摘要、问题、答案要点和练习任务；保存来源仓库、文件路径、固定 commit、许可证和归因文本。
- Apache-2.0/MIT 资料可以在履行 NOTICE/版权要求后进行整理；CC BY-SA 改编内容需要保留署名并以相同许可发布，产品分发方式要先确认兼容性。
- CC BY-NC-SA 内容默认不进入商业部署；无 LICENSE 的仓库默认只提供外链和独立重写后的知识框架。
- 不复制第三方题面、完整文章、图片、题解代码或仓库构建产物；来源更新时通过固定 commit 和审计记录保证可复现。

下一步应先确定知识条目的最小字段（主题、问题、回答要点、追问、来源快照、许可证、难度和复习间隔），再用 P0 的三类资料各选 10 条制作小批量 seed，验证检索、AI 讲解和复习流程后再扩大导入。
