# RAG 技术学习计划

更新时间：2026-06-29

## 学习目标

这份计划的目标不是把所有 RAG 变体一次性学完，而是先建立一条可执行路线：

- 能讲清楚 RAG 的核心流程和适用边界。
- 能独立搭建一个最小可用的文档问答 RAG。
- 能判断 RAG 效果差是检索问题、上下文问题、生成问题，还是数据质量问题。
- 能做基础优化：chunking、metadata、topK、hybrid search、rerank、query rewrite。
- 能设计一个适合 `algo-mentor` 的 Java/Spring RAG 模块雏形。

学习过程中优先关注“能解释、能实现、能评估”。暂时不要把精力放在 GraphRAG、Agentic RAG、训练 embedding 模型、向量索引底层算法等高级主题上。

## 总体路线

| 阶段 | 主题 | 建议周期 | 目标 |
|---|---|---:|---|
| 0 | 前置概念 | 1-2 天 | 理解 LLM、embedding、搜索、向量数据库的基本关系 |
| 1 | RAG 全貌 | 2-3 天 | 能画出 RAG pipeline，知道每个环节的职责 |
| 2 | 最小可用 RAG | 3-5 天 | 用少量文档跑通 indexing、retrieval、generation |
| 3 | 检索质量优化 | 5-7 天 | 能通过 chunking、metadata、hybrid search 提升召回质量 |
| 4 | 高级检索增强 | 5-7 天 | 掌握 rerank、query rewrite、multi-query 的使用场景 |
| 5 | RAG 评估 | 5-7 天 | 建立测试集，区分 retriever 和 generator 的问题 |
| 6 | Java/Spring 工程化 | 1-2 周 | 能设计并实现一个后端 RAG 服务雏形 |
| 7 | 扩展方向 | 长期 | 按项目需要了解 GraphRAG、Agentic RAG、多模态 RAG |

## 阶段 0：前置概念

### 要解决的问题

在正式学 RAG 前，先补齐几个底层直觉，否则后面容易把所有问题都归因于“大模型不行”。

需要理解：

- LLM 为什么不天然知道你的私有数据。
- embedding 为什么可以表示语义相似度。
- 关键词搜索和语义搜索分别擅长什么。
- 向量数据库在 RAG 中到底存什么。
- RAG 和 fine-tuning 的区别。

### 建议材料

- OpenAI Embeddings 文档：理解 embedding 用于 search、clustering 等场景。
  - https://developers.openai.com/api/docs/guides/embeddings
- Pinecone RAG 入门：先看 RAG 的核心组件和整体流程。
  - https://www.pinecone.io/learn/retrieval-augmented-generation/
- LlamaIndex RAG 介绍：理解“把你的数据接入 LLM”的基本动机。
  - https://developers.llamaindex.ai/python/framework/understanding/rag/

### 进入下一阶段的标准

能够不用术语堆砌，回答下面几个问题：

- RAG 为什么不是训练模型？
- embedding 存进向量库后，用户问题是怎么匹配到文档片段的？
- 什么问题用关键词搜索更合适，什么问题用向量搜索更合适？
- 为什么 RAG 仍然可能回答错误？

## 阶段 1：RAG 全貌

### 要解决的问题

这一阶段先建立完整地图，不急着优化。

需要掌握的主流程：

```text
文档收集
-> 文档解析和清洗
-> chunk 切分
-> embedding
-> 写入索引或向量库
-> 用户提问
-> query embedding / query rewrite
-> 检索 topK
-> rerank 或过滤
-> 拼接上下文
-> LLM 生成答案
-> 引用来源、记录日志、评估效果
```

需要重点区分两条链路：

- **Indexing 链路**：离线或异步处理文档，把知识变成可检索索引。
- **Query 链路**：在线处理用户问题，检索相关上下文并生成答案。

### 建议材料

- DeepLearning.AI RAG 课程页面：看课程大纲，重点关注 real-world RAG、keyword search、semantic search、hybrid search、chunking、query parsing。
  - https://www.deeplearning.ai/courses/retrieval-augmented-generation/
- LangChain RAG 教程：看一个端到端问答应用如何组织 loader、splitter、vector store、retriever、LLM。
  - https://docs.langchain.com/oss/python/langchain/rag
- LangChain4j RAG 文档：作为 Java 方向参考，注意它把 RAG 分成 indexing 和 retrieval 两个阶段。
  - https://docs.langchain4j.dev/tutorials/rag/

### 本阶段产出

写一页自己的 RAG 流程笔记，至少包含：

- 一张流程图或文字流程。
- 每个环节输入什么、输出什么。
- 哪些环节影响“是否检索到正确材料”。
- 哪些环节影响“模型是否基于材料正确回答”。

### 进入下一阶段的标准

能够解释：

- indexing 和 query 为什么要分开。
- chunking 为什么会影响答案质量。
- topK 太大和太小分别有什么问题。
- RAG 系统里哪些数据应该记录到日志里用于排查。

## 阶段 2：最小可用 RAG

### 要解决的问题

不要一开始做复杂系统。先用 5-10 篇 Markdown 或文本资料跑通最小闭环。

最小闭环包括：

- 加载本地文档。
- 按段落或标题切分 chunk。
- 生成 embedding。
- 存到一个简单向量库或内存索引。
- 用户提问后检索 topK。
- 把检索结果拼进 prompt。
- 要求模型基于上下文回答，并输出来源。

### 建议材料

优先选一条实现路线，不要同时学太多框架。

快速理解路线：

- LangChain RAG 教程。
  - https://docs.langchain.com/oss/python/langchain/rag
- LlamaIndex RAG 入门。
  - https://developers.llamaindex.ai/python/framework/understanding/rag/

Java 项目路线：

- Spring AI RAG 文档，重点看 `QuestionAnswerAdvisor` 如何从 vector store 取文档并追加到用户上下文。
  - https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- LangChain4j RAG 文档，重点看 indexing 和 retrieval 的组件拆分。
  - https://docs.langchain4j.dev/tutorials/rag/

### 推荐练习

用 `knowledge/` 目录里的已有 Markdown 文档做一个小型知识库，先回答这些问题：

- Prompt caching 和 KV cache 有什么区别？
- Few-shot 和 Chain-of-Thought 是同一类东西吗？
- 当前 `algo-mentor` 的提示词工程有哪些改进空间？

第一版可以先用脚本或 demo 项目实现，不必直接并入主后端。

### 进入下一阶段的标准

满足以下条件再继续优化：

- 至少导入 5 篇文档。
- 至少准备 10 个问题。
- 每个回答能看到命中的 chunk 或来源文档。
- 能手动判断：这次答错是没检索到材料，还是检索到了但模型没答好。

## 阶段 3：检索质量优化

### 要解决的问题

大多数 RAG 问题首先不是生成问题，而是检索问题。这个阶段重点提升“把正确材料找出来”的能力。

需要重点掌握：

- chunk size：过小会丢上下文，过大会引入噪声。
- chunk overlap：缓解边界信息被切断，但会增加冗余。
- 结构化切分：按 Markdown 标题、段落、代码块、表格切分。
- metadata：来源、章节、标签、用户、权限、更新时间。
- topK：召回数量和上下文噪声之间的权衡。
- hybrid search：关键词搜索 + 向量搜索。
- filter：按 metadata 过滤，而不是把所有文档混在一起搜。

### 建议材料

- DeepLearning.AI RAG 课程：重点关注搜索技术、chunking、query parsing。
  - https://www.deeplearning.ai/courses/retrieval-augmented-generation/
- Microsoft RAG 技术概览：了解 full-text search、vector search、chunking、hybrid search、query rewriting、reranking 的位置。
  - https://www.microsoft.com/en-us/microsoft-cloud/blog/2025/02/04/common-retrieval-augmented-generation-rag-techniques-explained/
- OpenAI Retrieval 文档：理解 vector store 和 semantic search。
  - https://developers.openai.com/api/docs/guides/retrieval
- pgvector 文档：如果走 PostgreSQL 路线，先了解 vector similarity search、HNSW、IVFFlat 的基本用法即可。
  - https://github.com/pgvector/pgvector

### 推荐练习

围绕同一批 10-20 个问题，做几组对比：

- chunk size：300、600、1000 tokens。
- topK：3、5、10。
- 检索方式：纯向量、关键词、hybrid。
- metadata filter：不过滤、按文档类型过滤、按主题过滤。

记录每次是否命中正确材料，不要只看最终回答是否顺眼。

### 进入下一阶段的标准

达到下面水平：

- 能解释一个问题为什么没有检索到正确 chunk。
- 能根据文档形态选择切分策略。
- 能说清楚 hybrid search 为什么常常比纯向量搜索稳。
- 有一个小型评估表，记录问题、期望来源、实际命中来源、是否回答正确。

## 阶段 4：高级检索增强

### 要解决的问题

当 naive RAG 已经能跑，但效果不稳定时，再引入高级检索增强。这个阶段不要追求所有技术都实现一遍，先掌握几个常用手段。

重点掌握：

- rerank：先召回较多候选，再用更强相关性模型重排。
- query rewrite：把用户口语化问题改写成更适合检索的查询。
- multi-query：从多个角度生成查询，提高召回。
- query decomposition：把复杂问题拆成多个子问题。
- context compression：从检索结果中压缩出真正相关片段。

### 建议材料

- DeepLearning.AI Advanced Retrieval for AI：重点看 query expansion、cross-encoder reranking、embedding adapter 等高级检索思路。
  - https://www.deeplearning.ai/courses/advanced-retrieval-for-ai
- Pinecone Rerankers：理解 two-stage retrieval 和 rerank 的价值。
  - https://www.pinecone.io/learn/series/rag/rerankers/
- Pinecone RAG 系列：了解 hybrid search、multi-query、reranking 等高级技巧。
  - https://www.pinecone.io/learn/series/rag/
- LlamaIndex Advanced RAG Cheat Sheet：作为高级模式索引，不要求一次性全部掌握。
  - https://www.llamaindex.ai/blog/a-cheat-sheet-and-some-recipes-for-building-advanced-rag-803a9d94c41b

### 推荐练习

在阶段 3 的小评估集上，只加入一个变量：

- baseline：topK=5，纯向量检索。
- 方案 A：topK=20 后 rerank 取前 5。
- 方案 B：用户问题先 query rewrite，再检索。
- 方案 C：multi-query 合并结果后 rerank。

比较命中率、答案质量、延迟和成本。

### 进入下一阶段的标准

能够回答：

- rerank 解决什么问题，不解决什么问题？
- query rewrite 什么时候会帮倒忙？
- multi-query 为什么可能提升召回，但也会增加噪声和成本？
- 如何用实验数据判断一个高级策略是否值得保留？

## 阶段 5：RAG 评估

### 要解决的问题

没有评估，RAG 优化很容易变成凭感觉调参数。这个阶段要建立基本评估闭环。

至少要评估两层：

- **检索层**：有没有找到正确材料。
- **生成层**：模型有没有忠实基于材料回答。

建议关注的指标：

- context precision：检索结果里有多少是真正有用的。
- context recall：需要的材料有没有被检索出来。
- faithfulness：回答是否忠实于上下文。
- answer relevancy / response relevancy：回答是否切题。

### 建议材料

- Ragas metrics：理解 RAG evaluation 可以按组件拆开评估。
  - https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/
- Ragas context precision：
  - https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/context_precision/
- Ragas context recall：
  - https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/context_recall/
- LangSmith RAG Evaluation：学习如何创建测试集、运行 RAG 应用、衡量效果。
  - https://docs.langchain.com/langsmith/evaluate-rag-tutorial

### 推荐练习

建立一个最小评估集：

| 字段 | 说明 |
|---|---|
| question | 用户问题 |
| expected_source | 应该命中的文档或章节 |
| expected_points | 答案必须覆盖的关键点 |
| retrieved_chunks | 实际命中的 chunk |
| retrieval_ok | 是否检索到正确材料 |
| answer_ok | 回答是否正确 |
| failure_type | retrieval / generation / data / prompt |

第一版手工评估就可以。等样例稳定后，再考虑接入 Ragas 或 LangSmith。

### 进入下一阶段的标准

达到下面水平：

- 有至少 30 条问题的评估集。
- 每次修改 chunking、embedding、topK、rerank 后，能跑同一批问题做对比。
- 能把失败归类为 retrieval、generation、data quality、prompt constraint、permission/filter 问题。

## 阶段 6：Java/Spring 工程化

### 要解决的问题

这一阶段把前面的概念落到 `algo-mentor` 后端可能采用的架构上。重点不是炫技，而是设计出可维护、可观测、可迭代的服务边界。

建议技术路线：

```text
Spring Boot / Spring AI 或 LangChain4j
+ PostgreSQL
+ pgvector
+ Markdown / 题解 / 学习笔记知识库
+ embedding provider
+ SSE 流式回答
+ 评估样例与日志
```

### 建议材料

- Spring AI RAG：
  - https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- Spring AI Vector Databases：
  - https://docs.spring.io/spring-ai/reference/api/vectordbs.html
- LangChain4j RAG：
  - https://docs.langchain4j.dev/tutorials/rag/
- Azure RAG Solution Design and Evaluation Guide：重点看生产级 RAG 为什么需要实验、评估、治理和设计权衡。
  - https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/rag/rag-solution-design-and-evaluation-guide
- pgvector：
  - https://github.com/pgvector/pgvector

### 推荐设计练习

先写一个设计草案，不急着实现：

```text
knowledge-ingestion
  文档导入、解析、chunking、embedding、索引更新

knowledge-retrieval
  query rewrite、vector search、keyword search、hybrid search、rerank

rag-answer
  prompt assembly、上下文拼接、LLM 调用、SSE 流式返回、引用来源

rag-evaluation
  测试集、命中结果、答案质量、回归对比
```

需要特别注意：

- embedding、LLM 模型名、base URL、API key、超时和重试必须走配置。
- 文档 metadata 要支持权限隔离。
- 日志不能记录 API key、完整 Authorization、用户隐私内容。
- 每次回答要记录命中文档、chunk id、score、topK、模型名、耗时、失败原因。
- RAG 生成不应该持有数据库事务等待模型返回。

### 进入下一阶段的标准

能够产出一版 `algo-mentor` RAG 设计文档，说明：

- 哪些数据进入知识库。
- 如何切分、索引、更新。
- 查询链路怎么走。
- 如何做权限隔离。
- 如何评估效果。
- 第一版不做哪些能力。

## 阶段 7：扩展方向

这些方向先了解概念，等项目真的需要再深入。

### GraphRAG

适合实体关系明显、需要跨文档关系推理的场景。比如人物、组织、事件、依赖关系。前期不用重点学。

### Agentic RAG

让模型自己决定查哪些源、拆哪些子问题、是否继续检索。适合复杂任务，但工程复杂度和不可控性更高。先把普通 RAG 做稳。

### 多模态 RAG

处理图片、截图、图表、PDF 版式、视频等内容。只有当知识来源大量不是纯文本时才需要重点投入。

### 微调 embedding 或 reranker

当通用 embedding 在特定领域效果不够，且你有足够标注数据时再考虑。个人学习阶段了解即可。

## 当前阶段不建议重点投入

以下内容容易消耗时间，但对第一版 RAG 能力帮助有限：

- 从零实现向量数据库。
- 深入推导 HNSW、IVFFlat、PQ 的数学细节。
- 自己训练 embedding 模型。
- 同时学习 LangChain、LlamaIndex、Spring AI、LangChain4j、Haystack 等多个框架。
- 一开始就做 GraphRAG 或 Agentic RAG。
- 没有评估集就反复调 prompt。
- 只看最终答案，不看检索命中的 chunk。

## 建议的第一轮学习节奏

如果每周能投入 6-8 小时，可以按下面节奏推进：

| 周次 | 重点 | 产出 |
|---|---|---|
| 第 1 周 | 阶段 0-1：概念和全貌 | 一页 RAG 流程笔记 |
| 第 2 周 | 阶段 2：最小可用 RAG | 一个能回答本地 Markdown 的 demo |
| 第 3 周 | 阶段 3：检索优化 | chunk/topK/hybrid 对比表 |
| 第 4 周 | 阶段 4：rerank/query rewrite | baseline 与增强方案对比 |
| 第 5 周 | 阶段 5：评估 | 30 条问题的评估集 |
| 第 6 周 | 阶段 6：Java/Spring 设计 | `algo-mentor` RAG 设计草案 |

如果时间有限，优先保证前 3 周完成。只要能跑通最小 RAG，并能判断检索问题，后续优化就有抓手。

## 后续可以补充的笔记主题

随着学习深入，可以在 `knowledge/` 目录继续拆分这些主题：

- `RAG核心流程与适用边界.md`
- `RAG中的Chunking策略.md`
- `Embedding与向量检索.md`
- `RAG中的HybridSearch与Rerank.md`
- `RAG评估指标与测试集设计.md`
- `algo-mentor的RAG模块设计草案.md`

## 参考资料

- DeepLearning.AI Retrieval Augmented Generation： https://www.deeplearning.ai/courses/retrieval-augmented-generation/
- DeepLearning.AI Advanced Retrieval for AI： https://www.deeplearning.ai/courses/advanced-retrieval-for-ai
- LangChain RAG Tutorial： https://docs.langchain.com/oss/python/langchain/rag
- LlamaIndex RAG Introduction： https://developers.llamaindex.ai/python/framework/understanding/rag/
- Pinecone RAG Guide： https://www.pinecone.io/learn/retrieval-augmented-generation/
- Pinecone RAG Series： https://www.pinecone.io/learn/series/rag/
- Pinecone Rerankers： https://www.pinecone.io/learn/series/rag/rerankers/
- OpenAI Embeddings： https://developers.openai.com/api/docs/guides/embeddings
- OpenAI Retrieval： https://developers.openai.com/api/docs/guides/retrieval
- Ragas Metrics： https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/
- LangSmith RAG Evaluation： https://docs.langchain.com/langsmith/evaluate-rag-tutorial
- Spring AI RAG： https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- Spring AI Vector Databases： https://docs.spring.io/spring-ai/reference/api/vectordbs.html
- LangChain4j RAG： https://docs.langchain4j.dev/tutorials/rag/
- pgvector： https://github.com/pgvector/pgvector
- Microsoft RAG Techniques： https://www.microsoft.com/en-us/microsoft-cloud/blog/2025/02/04/common-retrieval-augmented-generation-rag-techniques-explained/
- Azure RAG Solution Design and Evaluation Guide： https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/rag/rag-solution-design-and-evaluation-guide
