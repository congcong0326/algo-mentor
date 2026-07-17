# 长期记忆专职框架对比与 Java 项目接入选型

更新时间：2026-07-15

> 关联阅读：[[记忆系统的定位与主流框架设计对比]] 解释记忆系统与聊天上下文的边界；[[记忆系统的四段生命周期与业界通用设计法]] 解释抽取、整合、检索、遗忘四段生命周期。本文进一步回答：**专职长期记忆框架有哪些，它们是什么语言实现的，是否开箱即用，以及 Java 项目应该怎样接入。**

## 核心结论

- LangChain、LangGraph、Spring AI 首先是 **LLM 应用与 Agent 编排框架**。它们能承载长期记忆，但长期记忆的形成、更新和治理不是其唯一核心能力。
- Mem0、Zep / Graphiti 更接近 **专职长期记忆引擎**；Letta 是内置记忆能力的 **持久化 Agent Runtime**；LangMem 是依附于 LangGraph 的 **长期记忆开发库**。它们不属于完全相同的产品类别。
- 这些项目的核心实现都不是 Java。Mem0、Graphiti、Letta、LangMem 主要采用 Python；Zep 当前更适合按托管 API 理解。Java 项目通常通过 HTTP 接入独立记忆服务，而不是把它们作为普通 Maven 依赖嵌入进程。
- “开箱即用”需要分层理解：托管 API 可以很快完成 Demo，自部署服务需要配置模型和存储，Python 库还需要自行封装服务边界。**Demo 开箱不等于生产开箱。**
- 对 algo-mentor，学习进度、题目结果、能力评分等权威事实仍应保存在 PostgreSQL 领域模型中。长期记忆框架更适合处理用户偏好、历史经历、非结构化观察和个性化召回，不能替代业务数据库。

---

## 一、先把不同层次的项目分开

长期记忆领域经常出现“拿 LangGraph 和 Mem0 比”“拿 Spring AI 和 Letta 比”的讨论，但它们负责的系统层次并不相同。

| 系统层次 | 代表项目 | 主要职责 |
|---|---|---|
| Agent / LLM 编排框架 | LangChain、LangGraph、Spring AI、AutoGen、CrewAI | 调用模型和工具，组织工作流与运行时上下文 |
| 长期记忆引擎 | Mem0、Zep / Graphiti | 从交互中形成、更新、检索长期记忆 |
| 长期记忆开发库 | LangMem | 为现有 Agent 补充抽取、反思和记忆管理能力 |
| 持久化 Agent Runtime | Letta | 管理完整 Agent、运行状态、上下文和多层记忆 |
| 知识与记忆引擎 | Cognee 等 | 把多来源数据组织成图和向量知识结构 |
| 底层存储 | PostgreSQL、pgvector、Qdrant、Neo4j | 保存和索引数据，不负责判断应该记住什么 |

这几个层次可以组合，而不是只能选择一个。例如：

```text
Spring AI                 应用和模型编排
    ↓
LongTermMemoryClient      项目内统一记忆接口
    ↓
Mem0 / Zep                记忆形成与检索
    ↓
向量库 / 图数据库          持久化与索引
```

因此，评价框架的长期记忆能力时，不能只看“能否把数据存下来”，而要看它覆盖了多少记忆生命周期。

---

## 二、什么才算比较完整的长期记忆支持

一个长期记忆系统至少涉及六类能力。

### 2.1 记忆形成

从原始对话、工具结果和业务事件中判断哪些信息值得长期保存，并转换成稳定表示。

例如：

```text
原始表达：我这次用 Python 做题，但工作里主要还是 Java。

不合理记忆：用户偏好 Python。
合理记忆：用户工作中主要使用 Java，本次练习临时使用 Python。
```

### 2.2 记忆整合

将新记忆与旧记忆比较，完成去重、合并、更新、失效或删除，而不是只追加内容。

### 2.3 记忆检索

根据当前用户、任务、时间和语义相关性召回少量记忆，而不是把所有历史都放进上下文。

### 2.4 时间与演化

区分“过去曾经成立”和“现在仍然成立”。长期记忆不是静态画像，用户偏好、能力和关系都会变化。

### 2.5 溯源与治理

能够解释记忆来自哪次交互，支持用户查看、修改、删除，并处理租户隔离、隐私和数据保留期限。

### 2.6 上下文组装

将召回结果压缩成适合模型消费的内容，并控制 token 预算、顺序和可信度。

如果一个组件只提供 `conversationId -> List<Message>`，它主要解决短期会话上下文；如果只提供向量 `add/search`，它主要解决存储和检索。只有进一步覆盖形成、整合和演化，才更接近专职长期记忆系统。

---

## 三、四个核心项目总览

| 项目 | 主要实现语言 | 核心定位 | 交付形态 | 独立服务 | Java 接入 | 开箱程度 |
|---|---|---|---|---|---|---|
| Mem0 | Python，提供 TypeScript/JavaScript 客户端 | 原子事实型长期记忆层 | 开源库、托管 API | 可以 | REST 最自然 | 托管高，自建中 |
| Zep | 以托管记忆服务形态使用 | 面向应用的长期记忆服务 | 云 API、SDK | 是 | REST | 较高 |
| Graphiti | Python | 时序知识图谱引擎 | 开源库 | 需要自行封装 | Python 服务 + REST/gRPC | 中低 |
| Letta | Python | 持久化 Agent Runtime | Server、云服务、SDK | 是 | REST | 作为完整 Agent 较高 |
| LangMem | Python | LangGraph 长期记忆开发库 | Python 包 | 否 | 需放在 Python/LangGraph 服务内 | 单独使用较低 |

表里的“开箱程度”不是对能力高低的评价，而是说明要完成一次可工作的集成需要多少工程工作。

---

## 四、Mem0：原子事实型记忆层

### 4.1 定位与语言

Mem0 的核心开源实现以 Python 为主，同时提供面向其他语言的客户端和托管 Memory API。它既可以作为 Python 库嵌入应用，也可以作为外部记忆服务使用。

对 Java 项目而言，最合理的方式通常不是尝试把 Python 库嵌入 JVM，而是通过 REST 调用托管服务或自建的 Mem0 服务。

### 4.2 核心原理

Mem0 与“对话写入向量库”的关键区别，是它会对新旧记忆做更新决策：

```text
新交互
  ↓
LLM 提取候选事实
  ↓
向量检索相关旧记忆
  ↓
LLM 比较新旧事实
  ↓
ADD / UPDATE / DELETE / NONE
  ↓
更新记忆与向量索引
```

例如旧记忆是：

```text
用户主要使用 Python。
```

新交互是：

```text
我已经转到 Java 项目，目前主要写 Java。
```

Mem0 风格的整合会倾向于执行 `UPDATE`，而不是让两条互相冲突的事实永久并存。

### 4.3 适合什么场景

- 用户偏好与个性化设置。
- 相对稳定的用户事实。
- 助手需要跨会话“认识用户”的场景。
- 希望以简单 `add/search/get/delete` 接口接入记忆的应用。

### 4.4 优势与限制

优势：

- 抽取、去重和更新流程相对完整。
- API 形态容易理解。
- 向量存储和模型提供商通常可配置。
- 托管 API 适合快速验证。

限制：

- 原子化事实可能丢失事件上下文和证据链。
- 记忆写入质量依赖 LLM 判断。
- 每轮自动抽取和整合会增加成本与延迟。
- 复杂时间关系和多跳关系不是其最自然的表示方式。

### 4.5 是否开箱即用

- **托管 API**：接近开箱即用，适合 Demo 和快速验证。
- **开源 Python 库**：属于“组件开箱”，仍需配置 LLM、Embedding、向量存储、鉴权和服务接口。
- **生产系统**：还要补充异步写入、失败重试、审计、删除、指标和质量评估。

---

## 五、Zep / Graphiti：面向时间变化的长期记忆

### 5.1 Zep 和 Graphiti 不是同一个交付物

二者关系可以简单理解为：

- **Zep**：面向应用交付的长期记忆服务，主要通过 API 和 SDK 使用。
- **Graphiti**：公开的时序知识图谱框架，是开发者可以自行部署和扩展的 Python 项目。

选择 Zep 时，更应关注 API、数据策略和托管服务能力，而不是尝试依赖其服务端实现语言。选择 Graphiti 时，Python 运行时、图数据库和模型配置会成为实际工程依赖。

### 5.2 Graphiti 的核心原理

Graphiti 通常以 episode 作为输入。episode 可以是一段对话、一条业务事件或一段结构化数据：

```text
用户完成二分查找练习，但在右边界处理上出错。
```

系统再从中提取实体、关系和事实：

```text
用户 ──练习──▶ 二分查找
用户 ──出现错误──▶ 右边界处理
事件时间 ──▶ 2026-07-15
```

它强调时序关系：旧事实可以保留为历史事实，同时标记何时不再有效，而不必直接覆盖掉全部历史。

检索通常结合：

- 语义向量检索。
- 关键词检索。
- 图关系遍历。
- 时间和 metadata 过滤。

### 5.3 适合什么场景

- 事实和关系会频繁变化。
- 需要回答“什么时候发生”“为什么变化”。
- 需要跨实体、多跳关系检索。
- 需要从记忆追溯到原始事件。

### 5.4 优势与限制

优势：

- 能表达事件、实体、关系和时间。
- 比扁平向量记忆更适合处理变化与矛盾。
- 图检索能够支持关系型、多跳问题。
- 原始 episode 与派生事实可以保留关联。

限制：

- 建图、实体消歧和关系抽取的成本较高。
- 图数据库增加了部署和运维复杂度。
- 对简单用户偏好而言可能明显过重。
- 领域 schema 和实体质量需要持续治理。

### 5.5 是否开箱即用

- **Zep 托管服务**：较接近开箱即用。
- **Graphiti 开源库**：不是完整的 Java 可调用服务，需要配置 Python、LLM、Embedding 和受支持的图数据库，并自行封装 API。

---

## 六、Letta：记忆内置于持久化 Agent Runtime

### 6.1 定位与语言

Letta 的主要实现语言是 Python。它源自 MemGPT 的上下文分层思想，但当前更适合被理解成完整的持久化 Agent 平台，而不是一个单独的 Memory SDK。

它通常同时管理：

- Agent 身份与配置。
- 模型调用和工具。
- 当前上下文。
- 可编辑的 Memory Blocks。
- 历史交互与归档记忆。
- Agent 的持久状态。

### 6.2 核心原理

Letta 将有限的模型上下文看成需要管理的资源。重要信息保留在核心 Memory Block 中，大量历史进入可检索的归档层；Agent 可以通过工具读取或修改自己的记忆。

```text
核心 Memory Blocks       重要、常驻、可编辑
        ↓
当前对话与运行状态        当前任务所需
        ↓
归档记忆                 大容量、按需检索
```

这种模式不只是“应用帮 Agent 存记忆”，还允许 Agent 主动参与记忆管理。

### 6.3 适合什么场景

- 长时间运行的个人 Agent。
- Agent 身份和状态需要持续存在。
- 希望 Agent 主动整理和修改自身记忆。
- 愿意把模型调用、工具和状态交给统一 Runtime 管理。

### 6.4 优势与限制

优势：

- Agent 和记忆的整体体验较完整。
- 上下文分层和持久化是核心能力。
- Server/API 形态便于跨语言调用。
- 适合直接构建长期运行 Agent。

限制：

- 它会与 Spring AI、LangGraph 等 Agent 编排层发生职责重叠。
- 只想增加一个轻量记忆模块时，引入整个 Runtime 可能过重。
- Agent 自主修改记忆带来确定性、审计和权限问题。

### 6.5 是否开箱即用

如果目标是“启动一个自带持久记忆的 Agent”，Letta 的开箱程度较高；如果目标是“给现有 Spring AI 应用外挂一个记忆组件”，它不一定是最小成本方案。

---

## 七、LangMem：LangGraph 体系内的长期记忆开发库

### 7.1 定位与语言

LangMem 主要是 Python 库，与 LangGraph 的 State、Store 和 Agent Runtime 配合使用。它不是一个现成的 Memory Server。

它关注的能力包括：

- 从交互中提取和更新记忆。
- 管理用户 Profile 或记忆集合。
- 在前台链路或后台任务中形成记忆。
- 通过反思生成情景或程序性记忆。
- 优化 Agent 的 Prompt 和行为规则。

### 7.2 与 LangGraph 的关系

可以把二者关系理解成：

```text
LangGraph Store       提供跨 thread 的存储与检索基础设施
LangMem               提供如何提取、组织和更新记忆的开发组件
业务代码               决定 schema、触发时机和治理策略
```

LangMem 让 LangGraph 的长期 Store 更容易用，但不会把业务决策全部自动化。

### 7.3 适合什么场景

- 项目本身已经使用 Python 和 LangGraph。
- 希望继续使用 LangGraph Store。
- 需要自定义语义、情景和程序性记忆。
- 希望把记忆形成放在后台反思任务中。

### 7.4 是否开箱即用

它属于开发者库，而不是独立工具：

- 没有可以直接供 Spring Boot 调用的标准独立服务。
- 需要编写 LangGraph Agent、Store 和记忆 schema。
- Java 项目若要使用，通常需要先建设独立的 Python/LangGraph 服务。

因此，对于纯 Java 项目，LangMem 的接入成本通常高于 Mem0 或 Zep API。

---

## 八、四种方案的关键机制对比

| 维度 | Mem0 | Zep / Graphiti | Letta | LangMem |
|---|---|---|---|---|
| 主要记忆单元 | 原子事实 | episode、实体、关系、时序事实 | Memory Block、交互、归档 | Profile、记忆集合、情景、规则 |
| 写入主体 | 自动提取管线 | episode 摄取与图谱抽取 | Agent 和 Runtime | 应用节点或后台任务 |
| 冲突处理 | ADD/UPDATE/DELETE/NONE | 新关系建立、旧关系失效 | 修改 Block 或追加归档 | 按 schema 和提取策略合并 |
| 时间表达 | metadata 为主 | 核心能力 | 状态和历史记录 | 由业务 schema 决定 |
| 主要检索 | 向量，可扩展图 | 向量 + 关键词 + 图 + 时间 | 核心上下文 + 归档搜索 | LangGraph Store 检索 |
| 是否管理完整 Agent | 否 | 否 | 是 | 否 |
| 与现有框架耦合 | 较低 | 较低 | 高，会接管 Runtime | 高度依赖 LangGraph |
| 最自然的 Java 接法 | REST | REST | REST | Python 服务封装 |

没有一种方案在所有维度上最好：

- Mem0 追求简单、通用的用户事实记忆。
- Graphiti 追求时间、关系和溯源能力。
- Letta 追求长期存在的完整 Agent。
- LangMem 追求在 LangGraph 体系内灵活构建记忆策略。

---

## 九、“开箱即用”需要分成三个等级

### 9.1 API 开箱

注册服务、配置密钥后即可调用 `add/search`。Mem0 托管服务和 Zep 更接近这一层。

### 9.2 服务开箱

项目提供可部署 Server 或容器，但需要自己配置模型、数据库、网络、鉴权和持久卷。Letta Server、Mem0 自建服务属于这一类。

### 9.3 组件开箱

可以安装 Python 包并调用 API，但需要自行设计进程、接口和运行流程。Graphiti、LangMem 更接近这一类。

即使达到 API 开箱，生产环境仍需补齐：

- 用户、Agent、租户的数据隔离。
- LLM 和 Embedding 成本控制。
- 同步写还是异步写。
- 写入失败、重试和幂等。
- 记忆来源和审计记录。
- 用户查看、纠正、删除记忆。
- 敏感信息过滤和数据保留期限。
- 召回质量、错误记忆率和业务效果评估。

因此，更准确的说法是：这些框架可以让“记忆算法和接口”开箱，但不能让“生产级记忆治理”开箱。

---

## 十、Java / Spring AI 的三种接入方式

### 10.1 方式一：调用托管记忆 API

```text
Spring AI 请求前
    ↓
LongTermMemoryClient.search(userId, query)
    ↓
把相关记忆注入 Prompt
    ↓
调用 ChatModel
    ↓
异步 LongTermMemoryClient.add(userId, interaction)
```

优点是验证快，缺点是产生外部依赖，并需要评估隐私、费用和供应商锁定。Mem0、Zep 最适合这种接法。

### 10.2 方式二：部署 Python 记忆服务

将 Mem0、Graphiti 或 LangMem 封装成独立服务：

```text
Spring Boot
    ↓ HTTP/gRPC
Python Memory Service
    ↓
PostgreSQL / Vector Store / Graph Store
```

优点是可以自托管并隔离技术栈；缺点是需要维护 Python 服务、接口契约、部署和监控。

### 10.3 方式三：在 Java 中实现 Mem0 风格管线

Mem0 的核心思想并不要求必须使用 Mem0：

```text
Spring AI 结构化输出提取候选事实
    ↓
pgvector 查找相似旧记忆
    ↓
规则优先、LLM 兜底判定新增/更新/删除/忽略
    ↓
PostgreSQL 保存事实、来源和有效期
```

这种方案开发量更大，但数据模型、成本、隐私和行为都更可控，也最容易与 algo-mentor 的领域模型结合。

无论选择哪种接法，项目内部都应保留统一边界：

```text
LongTermMemoryService
├── ingest(userId, event)
├── search(userId, query, limit)
├── correct(userId, memoryId, value)
└── delete(userId, memoryId)
```

这样上层 Advisor 和业务 Service 不需要知道底层是 Mem0、Zep 还是 PostgreSQL + pgvector。

---

## 十一、对 algo-mentor 的具体判断

### 11.1 不应交给通用记忆框架的数据

下面这些属于权威业务事实，应由 PostgreSQL 领域模型管理：

- 题目是否完成。
- 提交结果和代码。
- 学习计划及进度。
- 知识点掌握度评分。
- 错题复习状态。
- 用户权限和配置。

这些数据需要事务、一致性、精确查询和可解释更新，不能依赖 LLM 从聊天中猜测，也不应只存在向量库中。

### 11.2 适合长期记忆层的数据

- 用户偏好的讲解方式。
- 用户主动表达的学习目标。
- 多次交互中形成的非结构化观察。
- 某次解题失败的原因摘要。
- 对后续讲解有帮助的历史经验。

### 11.3 当前阶段建议

```text
短期会话：Spring AI ChatMemory
权威事实：PostgreSQL 业务表
长期画像：结构化 Profile / Memory 表
历史经历：事件表 + pgvector
记忆管线：应用显式写入为主，LLM 异步提取为辅
```

选型判断：

- 要快速验证外部长期记忆效果：优先试 Mem0 或 Zep API。
- 要自托管简单用户事实记忆：评估 Mem0。
- 确实需要复杂时间关系和多跳检索：再评估 Graphiti。
- 要构建由统一平台管理的长期自主 Agent：评估 Letta。
- 已经决定建设 Python LangGraph Agent：再使用 LangMem。
- 要保持 Java 单栈、强调业务可控：Spring AI + PostgreSQL + pgvector 自建更自然。

当前不建议仅为了长期记忆就直接引入 Letta 或 LangMem，因为它们会带来新的 Agent Runtime 或 Python/LangGraph 技术栈。可以先吸收 Mem0 的更新语义和 Graphiti 的时序失效思想，再根据实际数据规模与查询需求决定是否引入外部系统。

---

## 十二、面试表达

可以这样概括：

> LangChain、LangGraph 和 Spring AI 主要是 Agent 编排框架，它们提供状态、Store、ChatMemory 或 VectorStore 等基础设施，但不会完整解决长期记忆形成与治理。专职长期记忆方案中，Mem0 以原子事实和 ADD/UPDATE/DELETE/NONE 更新管线为核心；Zep/Graphiti 用时序知识图谱处理关系变化与溯源；Letta 把分层记忆内置进持久化 Agent Runtime；LangMem 则是 LangGraph 体系内的长期记忆开发库。Java 项目通常通过 REST 接入独立服务，或者用 Spring AI、PostgreSQL 和 pgvector 实现同类管线。选择时应先区分权威业务事实与非结构化记忆，再看是否需要自动抽取、时间关系、图检索或完整 Agent Runtime。

---

## 十三、其他值得关注的方向

长期记忆领域仍在快速发展，其他项目和研究方向还包括：

- **Cognee**：更偏向把多来源信息转成知识图与向量表示。
- **Supermemory**：偏托管式记忆与检索 API。
- **MemoryOS / MemGPT 类架构**：关注有限上下文下的分层分页和上下文管理。
- **A-Mem**：关注原子记忆之间的链接、演化和取代关系。
- **MemMachine**：强调保留原始证据、降低逐轮抽取成本。
- **CrewAI / AutoGen / LlamaIndex Memory**：属于 Agent 或数据框架内的记忆组件，不完全等同于独立长期记忆引擎。

比较这些方案时，应先判断它究竟是论文算法、开发库、可部署服务、托管 API，还是完整 Agent 平台。名称都叫 Memory，不代表交付能力和系统边界相同。

---

## 参考资料

- Mem0 Documentation — https://docs.mem0.ai/
- Mem0 GitHub — https://github.com/mem0ai/mem0
- Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory — https://arxiv.org/abs/2504.19413
- Zep Documentation — https://help.getzep.com/
- Graphiti GitHub — https://github.com/getzep/graphiti
- Zep: A Temporal Knowledge Graph Architecture for Agent Memory — https://arxiv.org/abs/2501.13956
- Letta Documentation — https://docs.letta.com/
- Letta GitHub — https://github.com/letta-ai/letta
- LangMem Documentation — https://langchain-ai.github.io/langmem/
- LangMem GitHub — https://github.com/langchain-ai/langmem
- LangGraph Memory — https://docs.langchain.com/oss/python/concepts/memory
