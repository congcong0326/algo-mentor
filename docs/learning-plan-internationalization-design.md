# 学习计划内容国际化设计

## 1. 目标与边界

本次改造只支持 `zh-CN` 和 `en-US`，不建立通用翻译表。模板源文件中的现有正文仍是中文，英文正文由固定版本的 Argos Translate 离线批量生成并作为版本化数据提交。应用运行、普通 seed 生成和 seed 导入均不依赖 Argos、翻译模型或网络。

国际化拆成三个互不替代的维度：

- **UI locale**：控制按钮、状态、通知、筛选器和有限枚举文案，由前端 `I18nProvider` 管理并通过 `Accept-Language` 发送给 API。
- **计划 `contentLocale`**：控制学习计划正文、AI 推荐理由和后续修订/扩展的输出语言，在新建草稿时固化，确认计划后继续保留。
- **题库展示语言**：题目始终保留英文 `title` 和中文 `titleCn`。普通题库页面按 UI locale 展示；计划生成和 AI 题库工具按计划 `contentLocale` 请求，但不得覆盖双语标题或把本地化标签写入计划。

## 2. 模板数据

`learning_plan_template` 的现有 `title`、`summary`、`goal`、`target_audience`、`prerequisites`、`recommended_for`、`not_recommended_for`、`expected_outcome` 继续表示中文，新增对应 `_en` 字段。

`learning_plan_template_phase` 的现有 `title`、`focus`、`objectives`、`acceptance_criteria`、`review_advice` 继续表示中文，新增对应 `_en` 字段。

模板增加 `english_content_ready`。只有模板八个英文正文和每个阶段五个英文正文均完整、非空时才为 `true`。英文列迁移初期允许 `NULL`，但数据库约束禁止空字符串。

模板源目录新增 `translations/en-US.json`。该文件只包含 `templateId`、八个模板正文和按 `phaseIndex` 对齐的五个阶段正文，不包含枚举、数字、标签 key、slug、URL、来源信息、题目引用或题目顺序。聚合 seed 合并中文源和英文文件，题目匹配、缺失统计和校验和语义保持不变。

## 3. Locale 选择与回退

模板列表、详情和模板草稿创建读取 `Accept-Language`：

- 首选语言可解析为 `en-US` 且 `english_content_ready=true` 时，整个模板返回英文包。
- 英文内容未就绪时，整个模板回退中文；禁止逐字段 `COALESCE`，避免中英混排。
- `zh-CN`、未知语言、缺失请求头统一使用中文。
- 模板响应返回实际 `contentLocale`，并设置 `Vary: Accept-Language`。
- 模板缓存 key 包含 locale；中英文列表和详情不能共享缓存值。

前端切换 UI locale 后重新请求模板列表，并在新结果中按 `templateId` 保留当前选择。模板创建请求结构在中英文下保持一致，后端以本次模板实际返回的 `contentLocale` 创建草稿。

## 4. 学习计划语言生命周期

新增类型安全的 `LearningPlanContentLocale`，值固定为 `ZH_CN`/`EN_US`，JSON 值固定为 `zh-CN`/`en-US`。

- AI 新建：从创建请求的 UI locale 固化 `contentLocale`，prompt 使用 `outputLocale`，不再写死中文输出。
- 模板新建：使用模板本次实际返回的语言；英文未就绪时固化为 `zh-CN`。
- AI 修订与计划扩展：只继承原草稿或正式计划的 `contentLocale`，不读取当前浏览器语言。
- 确认计划：把草稿 `contentLocale` 写入正式计划 metadata。
- 用户输入、计划标题/摘要/目标/阶段正文、验收条件、复盘建议和推荐理由不会因 UI locale 切换而翻译。
- 计划标签只保存 `ProblemTag.value`，展示 label 由前端按 UI locale 解析。

旧草稿和旧正式计划 JSON 缺少 `contentLocale` 时按 `zh-CN` 反序列化。已有正式计划不自动翻译，也不做后台回填英文正文。

## 5. 有限状态文案

状态、通知、今日题包分组和负载建议优先返回稳定 `code` 与 `parameters`，由前端根据 UI locale 格式化。自由文本、用户输入、AI 正文和推荐理由不进入这类 UI 翻译机制。

## 6. 翻译生产链路

翻译工具位于 `tools/learning_plan_template_seed/translate_template_sources.py`，固定使用 lock 文件声明的 Argos Translate 和 `zh -> en` 模型。工具强制 CPU、单 inter/intra thread、固定 beam size 和批大小，且把模型安装到进程级临时隔离目录；不会读取或选择机器上其他模型。

术语修正只允许修改版本化术语表，整句或精确字段修正只允许修改版本化覆盖表。生成后的 `en-US.json` 禁止手工编辑。`--translate` 生成，`--check` 做离线完整性/格式/中文残留检查，`--verify-determinism` 用相同模型连续生成两次并与已提交产物逐字节比较。

任一模型哈希、依赖版本、字段结构、必填值、阶段对齐或中文残留校验失败时整批终止，不写部分结果，也不调用业务 AI 服务。

## 7. 改造前基线

2026-08-02 在干净工作区记录：

- 模板：35；阶段：178；problem refs：1738；本地匹配：1699；本地缺失：39。
- 移除全部可翻译字段后，对模板源和 problem refs 做排序规范化 JSON 的 SHA256：`a0518063bd4c04f5da49a9ae5d801f4a9e9fa54208203c635f6e1652353cffcd`。
- Python seed 测试：13 项通过。
- 后端模板 controller/import/cache 相关测试：11 项通过。
- 前端学习计划创建页测试：5 项通过。

## 8. 数据库发布与回退

Flyway 迁移只新增可空英文列、完整性标记和非空字符串约束，不改写中文正文和已有计划。发布顺序为迁移、导入包含英文包的新 seed、应用发布。

应用回退时可继续使用中文列；数据回退通过重新导入上一版 seed 清空/恢复英文包并把 `english_content_ready` 置为 `false`。Flyway 迁移保持向前，不删除新增列。正式计划的 `contentLocale` 是内容契约，不能通过切换 UI locale 或回滚前端来改写。
