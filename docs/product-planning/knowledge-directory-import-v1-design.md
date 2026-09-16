# 知识目录全量导入实施设计

日期：2026-09-16。本文替代早期显式节点 ID、来源映射、hash/upsert、prune、单独元数据文件及固定答案章节的草案。

维护者的正式契约见 [知识库维护手册](../../knowledge-base/README.md)。

## 内容契约

只遍历 `.node` 目录；`.card.md` 与 `.article.md` 是正式内容。普通目录的整个子树和其他文件忽略。目录名与文件名产生标题，不另存编辑用节点键。

卡片是一个 Markdown 文件：YAML 元数据（slug 必填，tags/status/order/relations 可选）→ 核心回答 → 第一个顶层二级标题开始的自由详细解释。使用 CommonMark 解析块边界，SnakeYAML 安全解析元数据；未知字段和重复键失败。关系目标用 slug，校验目标存在、自引用、重复及前置循环。

## 代码边界

- `knowledge/model`：文件后缀、元数据、关系枚举、不可变源快照及用户 API DTO。
- `knowledge/importer/KnowledgeDirectoryReader`：完整扫描与校验，先于数据库写入。
- `knowledge/importer/KnowledgeContentImporter`：JdbcTemplate + TransactionTemplate，全量内容替换。
- `knowledge/importer/KnowledgeImportCli`：一次性 CLI，由应用 main 在启动 Spring 前分派，不启动 HTTP 或 worker。
- `knowledge/repository`：知识库查询、按 slug 关联的用户状态与评价流水。
- `knowledge/service`：可见性、用户隔离、FSRS、评价幂等及 DTO 组装。
- `frontend/src/services/knowledge.ts` 与 `types/knowledge.ts`：共享前端契约；页面使用现有 MarkdownView 渲染。

## 数据库与替换事务

新增 V76 迁移，开发期一次性重建已有知识库五张表并新增关系表；用户已明确无需保留旧数据。不修改 V74/V75 的历史校验和，不涉及其他业务表。

`knowledge_card.slug` 唯一；`knowledge_card_user_state.card_slug` 是不设置内容外键的逻辑引用，唯一键为 `(user_id, card_slug)`。流水继续外键引用用户状态。关系表以源 slug、关系类型、目标 slug 唯一，保存关系顺序；关系只引用当前内容，可以随内容一起删除。

导入顺序：完整解析 → 事务级导入锁 → 删除关系、卡片、文章 → 从叶向根删除大纲 → 建 ROOT 和目录节点 → 插入卡片、文章、关系 → 提交。用户状态与流水不在日常删除范围。失败回滚整个替换事务，无半棵树或临时空库。

接口读操作使用一致性快照；评价事务取得共享导入锁，并按用户串行化首次建状态和幂等请求。内容重建不会使同 slug 的评价引用失效。

相同内容重复导入保证业务结果一致，技术 ID 和导入时间可以变化。不使用 hash 或历史快照阻止重新导入，Git 回滚可直接恢复内容。源文件移除的卡片保留用户历史，同 slug 恢复后重新加入可见关联。

## API 和页面

所有卡片路径、响应与关系使用 slug；大纲和文章仍使用当前快照内的数字 ID。卡片答案分为 `answerMarkdown` 和 `explanationMarkdown`，详情不再拆来源、示例等字段。文章按确定性 sort_order 排序，发布时间仅为导入时间。

知识库提供节点树、节点卡片与文章列表、标签搜索、详情渲染、关系跳转与评价；复习中心按 slug 读取和评价。草稿不出现在用户 API 或关系目标列表中。

## 执行与验证

`make knowledge-validate` 和 `make knowledge-import` 是统一入口。迁移与数据库连接配置见维护手册。

单元测试覆盖后缀扫描、Markdown 块边界、YAML、slug、关系与空输入；PostgreSQL 隔离 schema 集成测试覆盖重复导入、改名移动、删除恢复、失败回滚、用户隔离、评价幂等和并发；前端测试覆盖大纲到卡片、详情、文章和关系跳转。

第一版不建设管理员编辑后台、多来源同步、资源上传或内容版本表。
