# AMR-10：画像文档投影、引用与后端 API

> 波次：D
>
> 状态：DONE
>
> 直接依赖：AMR-02、AMR-03
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

实现确定性的 `LearnerProfileDocumentProjector`、受限文档 AST、citation map、主画像 API 和 statement evidence cursor API，破坏性替换旧分类数组响应。

完成后 `/api/me/learner-profile` 只投影当前认证用户的 ACTIVE claim；相同输入生成字节级稳定的文档顺序、引用编号和 `documentRevision`，证据按需分页且不存在 N+1 查询。

## 2. 必须读取

- `CURRENT.md`、`AMR-02`、`AMR-03`、`AMR-04` 与 `AMR-05` 的完成备注；不要重读这些任务正文。
- `CONTRACTS.md` 第 2、4、5、10、12 节。
- 新 claim/evidence query 端口、section catalog、snapshot/hash 类型。
- `LearnerProfileController.java`、`ApiContractConstants.java`。
- 旧 `LearnerProfileViewService.java`、`LearnerProfileResponse.java` 和 profile model，只用于识别待替换边界。
- 旧 `LearnerProfileMapper.findCurrentForDisplay` 及 XML 片段，只用于确认删除范围。
- `PracticeCodeReviewMapper` 中 Review 定位字段查询和受信用户范围模式。
- `AgentTurnMessageLookupRepository` 或新 message evidence 摘要查询端口。
- `LearnerProfileControllerTest`、`LearnerProfileViewIT` 的认证和 PostgreSQL 测试模式。

## 3. 投影输入与稳定顺序

新增只读 `LearnerProfileProjectionSnapshot` 或等价模型，一次性包含：

- 有序 ACTIVE claim revision、tag 的双语展示名和最新更新时间。
- 每个 revision 的 Review/message evidence 总数、来源统计和最多 2 条预览。
- locale、`projectorVersion=v1` 和固定 section catalog。

读取必须批量完成，不能逐 claim 查询 evidence 或 Review。主题顺序复用 `LearnerMemorySectionCatalog`；主题内排序固定为 scope 顺序、用户自述优先、更新时间倒序、revision ID，不依赖 SQL 默认顺序。

空主题不渲染；无 ACTIVE claim 返回合法空文档，`blocks` 和 `citationMap` 为空，不生成推断正文。

## 4. 受限文档 AST

在领域投影层定义封闭类型：

- block：`HEADING`、`PARAGRAPH`。
- span：`TEXT`、`SUPPORTED_TEXT`。
- format：`MARKDOWN_DOCUMENT_V1`。

规则：

- 一个 claim revision 生成一个 `SUPPORTED_TEXT`，不能和其他 claim 改写合并。
- 同主题可把多个 supported span 放进稳定段落，span 之间只插入受信空格或标点 `TEXT`。
- `TEXT` 不得承载新的用户判断；标题和中性模板只能来自常量目录。
- citation 按文档首次出现顺序分配；每个 supported span 恰好引用一个 citation，citation 不孤悬也不跨 revision 复用。
- claim 只经过统一纯文本策略进入 AST；不得解析 Markdown、HTML、链接或模型 citation token，不得调用 LLM。
- projector 是纯函数，不持久化投影，也不允许从文档反向写 claim。

`documentRevision` 使用独立类型，按 `projectorVersion + locale + 有序 ACTIVE revision ID` 的规范化字节计算 SHA-256；禁止复用 claim snapshot token。

## 5. Citation 与证据 DTO

主响应固定包含：

- `format`、`projectorVersion`、`locale`、`documentRevision`、`title`、`blocks`、`citationMap`、`updatedAt`。
- citation 的 `displayNumber`、`statementRef`、claim revision/key、origin、受信 `sourceSummary`、`evidenceCount` 和最多 2 条 `previewEvidence`。

证据摘要使用封闭联合类型：

- `CODE_REVIEW`：只返回 review/session/plan/phase/problem/version、总分、是否通过、role 和时间等定位/摘要字段；其中 `problemSlug` 仅用于稳定定位和跳转，`problemTitle` 必须按请求语言返回题库本地化题名并用于展示。
- `USER_MESSAGE`：只返回 message role、时间和受限摘录；不返回完整消息，也不提供任意消息深链。

不得返回 raw/normalized/submitted code、完整 Review Markdown、context 全文、完整消息或模型 Prompt。来源摘要由确定性模板和计数生成，不调用模型。

## 6. Statement Ref、Cursor 与权限

- `statementRef` 与 evidence cursor 使用版本化、URL-safe、不透明且带完整性保护的 codec。
- ref 至少绑定 revision、当前用户和 codec/version；cursor 额外绑定 statement、最后排序键和页大小边界。
- 如项目没有可复用 codec，增加 profile 专用 HMAC 配置并通过环境变量注入；禁止硬编码密钥或复用可由前端控制的值。
- evidence endpoint 即使 ref/cursor 校验通过，也必须重查 revision 仍为当前用户 ACTIVE 状态。
- 伪造、跨用户、过期、已退役或 cursor 与 statement 不匹配统一返回不存在/无权限语义，不泄漏对象是否存在。
- evidence 稳定顺序为业务时间、evidence 类型、source ID；每页最大 20，返回 `items + nextCursor`，无重复、无遗漏。

## 7. Controller 与缓存契约

保留并破坏性升级：

```http
GET /api/me/learner-profile
GET /api/me/learner-profile/statements/{statementRef}/evidence?cursor=...&limit=20
```

- 路径、query key、format、block/span type 和 JSON 字段集中在常量/枚举中。
- 用户 ID 只来自 `CurrentUserIdProvider`，忽略或拒绝请求中的 `userId`。
- locale 使用项目现有 `Accept-Language` 解析规则并规范化为受支持值。
- 主响应设置由 `documentRevision` 派生的强 ETag；匹配 `If-None-Match` 时返回 `304` 且不重复序列化正文。
- API 包装方式与现有 `ApiResponse` 保持一致；`304`、认证失败、非法 limit 和不存在 ref 需要独立 controller 测试。

## 8. 实施步骤

1. 定义 projection snapshot、AST、citation/evidence model、常量和纯文本策略。
2. 实现批量 projection query adapter，补齐 Review/message 定位摘要且保持用户范围。
3. 实现 projector、稳定编号、document revision 和 source summary 模板。
4. 实现 statement ref/cursor codec 与 evidence 分页服务。
5. 破坏性替换旧 response/service/controller，接入 ETag 和 locale。
6. 增加纯函数单测、controller 测试、Mapper 测试和 PostgreSQL IT。

## 9. 重点测试

- 相同 locale/version/ACTIVE 集合在输入乱序后仍生成完全相同的 AST、citation map 和 revision。
- RETIRED/SUPPRESSED/REJECTED/SUPERSEDED 不出现；空主题与空文档语义正确。
- 每个 supported span/citation 一一对应，`TEXT` 只能来自白名单模板。
- Markdown、HTML、脚本、URL 样式和伪 citation 输入只作为纯文本，不生成可执行节点或链接。
- preview 最多 2 条，evidence 总数准确；完整分页 20 条边界稳定。
- 跨用户 ref、跨 statement cursor、退役后的 ref、其他用户 Review/message 全部不可读。
- 查询数量不随 claim 数线性增长；500 至 1000 ACTIVE claim 投影不出现 evidence N+1。
- ETag 命中返回 304；revision、locale 或 projector version 变化会改变 ETag。

## 10. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='*LearnerProfileDocumentProjector*Test,*LearnerProfile*PlainText*Test,*LearnerProfile*RefCodec*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='*LearnerProfile*Controller*Test,*LearnerProfile*ViewService*Test' \
  -Dit.test='*LearnerProfile*DocumentIT,*LearnerProfile*EvidenceIT' verify

git diff --check
```

## 11. 非目标与停止条件

- 不实现前端、不实现用户反馈、不导出自由 Markdown。
- 不把 projection AST、citation 编号或 document revision 写回数据库。
- 若 projector 调用 LLM、主 API 返回完整 evidence、存在 N+1、ref 可跨用户复用或纯文本进入 Markdown/HTML parser，不得开始 `AMR-11`。

## 12. 上下文交接

记录 projector/AST 类型、稳定排序键、statement ref/cursor codec、ETag 格式、API DTO 文件和测试结果。不要复制画像正文、citationMap 样例或 evidence 列表。

## 13. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增受限 `LearnerProfileDocument` AST、确定性 projector 和批量 projection repository；ACTIVE claim 按主题、scope、更新时间和 revision 稳定投影。
- citation 带签名 statement ref，evidence cursor 绑定用户、revision、页大小与微秒级排序键；主 API 支持强 ETag 和安全分页证据。
- `/api/me/learner-profile` 破坏性替换旧分类数组；删除旧 display mapper、view service、DTO 和对应测试，保留旧存储写路径至 AMR-14。

验证：

- mentor-application projector/plain-text/ref codec 定向测试：PASS（6 tests）。
- mentor-api controller/mapper XML 及 `LearnerProfileDocumentIT`：PASS（1 PostgreSQL IT）。
- `git diff --check`：PASS。

偏离计划：

- cursor 改为秒和纳秒编码，避免 PostgreSQL 微秒时间精度下的分页重复；tag assessment 使用受信双语标签前缀。

遗留事项：

- 无。

下一任务：`AMR-11`
