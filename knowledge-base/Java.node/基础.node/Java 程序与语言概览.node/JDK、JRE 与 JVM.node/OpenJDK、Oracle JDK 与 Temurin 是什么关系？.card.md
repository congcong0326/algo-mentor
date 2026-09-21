---
slug: java-openjdk-oracle-jdk-temurin
tags: [Java基础, JDK, OpenJDK, HotSpot, OpenJ9]
status: published
order: 50
relations:
  prerequisites: [java-jvm-specification-and-implementations]
---

OpenJDK 是开源项目，提供构建 JDK 所需的源代码，包括 HotSpot JVM、Java 标准类库、`javac` 等开发工具。

Oracle JDK 和 Eclipse Temurin 是基于 OpenJDK 构建、测试并发布的完整 JDK 发行版，均搭载 HotSpot JVM。它们共享大量代码，但由不同组织负责发行与维护，具体版本、补丁、支持平台和许可安排可能不同。

**JDK 发行版是完整交付物，JVM 是其中一个组件。不同发行版可以采用同一种 JVM，也可以选择不同的 JVM 实现。**

## 从源代码到可以安装的 JDK

OpenJDK 提供的是开源代码基础。要得到可以下载安装的 JDK，还需要针对具体平台完成构建、测试和打包，并持续提供更新。

Oracle JDK 和 Temurin 都基于这些代码产生发行包：

```text
OpenJDK 的源代码
├── HotSpot JVM
├── Java 标准类库
├── javac 等开发工具
└── 其他组件
        │
        ├── Oracle 构建、测试、发行 → Oracle JDK
        │
        └── Eclipse Adoptium 构建、测试、发行 → Temurin
```

因此，“Oracle JDK 的 JVM”与“HotSpot”并不矛盾：前者说明它属于哪个发行包，后者说明这个虚拟机实现叫什么。

Temurin 也使用 HotSpot，不代表它直接拿 Oracle JDK 的安装包重新命名。两者共享 OpenJDK 的上游代码基础，各自形成发行包。

## OpenJ9 又处在哪个位置

Eclipse OpenJ9 是另一套 JVM 实现，源自 IBM J9，并不是基于 HotSpot 修改出来的。

OpenJ9 本身不等于完整 JDK。要构成可供开发和运行的 JDK，还需要标准类库、编译器和其他支持组件。

发行版构建者可以将 OpenJ9 与基于 OpenJDK 的其他组件集成：

| 完整 JDK 发行版 | JVM 实现 | 类库和开发工具的主要代码基础 |
| --- | --- | --- |
| Oracle JDK | HotSpot | OpenJDK |
| Eclipse Temurin | HotSpot | OpenJDK |
| 采用 OpenJ9 的 IBM Semeru | OpenJ9 | OpenJDK，配合所需适配 |

所以，**“基于 OpenJDK”不意味着必须使用 HotSpot**。它可以沿用 OpenJDK 的类库和工具，同时采用另一种虚拟机实现。

这里的集成由发行版构建者完成，需要适配、构建和测试。Oracle JDK 官方发行包没有切换到 OpenJ9 的选项；希望使用 OpenJ9 时，通常选择已经集成好它的发行版。

## 同样使用 HotSpot，发行版还有什么区别

不同发行版共享大量代码，但交付与维护方式仍可能不同：

| 方面 | 可能存在的区别 |
| --- | --- |
| 版本与补丁 | 具体更新版本、补丁回移、发布时间 |
| 平台支持 | 支持哪些操作系统、CPU 架构及系统版本 |
| 构建与测试 | 构建配置、测试流程、附带组件 |
| 维护服务 | 更新期限、问题响应、是否提供商业支持 |
| 许可条款 | 发行包的使用条件，以及支持服务如何收费 |

不能简单归纳为“Oracle JDK 收费，OpenJDK 免费”。开源代码的许可、具体发行包的使用条款和商业支持费用，是不同的问题；选择时应核对所用版本的实际条款。

## 对开发者有什么影响

使用标准 Java API、依赖兼容的应用，通常不需要仅因更换发行版而修改业务代码。不过，需要确认更换的具体内容：

- **Oracle JDK 换成 Temurin**：通常仍然使用 HotSpot，重点检查完整版本、启动参数、配置、证书及业务运行情况。
- **HotSpot 发行版换成 OpenJ9 发行版**：虚拟机实现也发生变化，还需要重新检查 GC 参数、诊断工具、监控方式和性能表现。

日常说“安装了 OpenJDK”往往不够准确。排障和维护时，最好记录**发行版、完整版本、JVM 实现和平台架构**，例如“Temurin 17 的某个更新版本，HotSpot，Linux x64”。

## 参考资料

- [OpenJDK 项目](https://openjdk.org/)：开源 Java 平台实现及其项目组织。
- [Eclipse Temurin](https://adoptium.net/temurin/)：基于 OpenJDK 的发行版、测试与支持平台说明。
- [Oracle JDK 常见问题](https://www.oracle.com/java/technologies/javase/jdk-faqs.html)：Oracle JDK 的发行与使用说明，具体许可需结合版本核对。
- [Eclipse OpenJ9 概览](https://eclipse.dev/openj9/docs/overview/)：独立虚拟机实现及与 OpenJDK 组件的集成关系。
- [IBM Semeru Runtimes](https://www.ibm.com/products/semeru-runtimes)：采用 OpenJ9 的完整 Java 运行环境发行产品。
