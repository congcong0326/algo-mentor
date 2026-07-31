# AMR-11：单篇画像、句子引用与依据抽屉

> 波次：D
>
> 状态：DONE
>
> 直接依赖：AMR-10
>
> 建议首轮文件上限：12

## 1. 目标与完成标准

把 `/me` 的画像区域拆成独立组件，使用受限 AST 渲染一篇连续文档，移除分类 tab、claim 卡片和底层模型字段，并实现句子级引用预览与懒加载证据抽屉。

完成后用户能通过鼠标、键盘和移动端触摸查看每个判断的来源；页面展示全部文档内容，不复用 Practice Chat bootstrap/token 裁剪。

## 2. 必须读取

- `CURRENT.md` 和 `AMR-10` 完成备注，不重读后端实现文件。
- `CONTRACTS.md` 第 10 至 12 节。
- `frontend/src/MyPage.tsx` 中 learner memory 状态和渲染片段。
- `frontend/src/MyPage.test.tsx` 中画像测试。
- `frontend/src/types/api.ts` 的旧画像类型和 `frontend/src/services/api.ts` 的画像请求。
- `frontend/src/services/api.test.ts` 的请求测试模式。
- `frontend/src/styles.css` 中 `.learner-memory-*` 片段及相邻响应式/暗色规则。
- `frontend/src/i18n/locales.ts` 中 `myPage` 画像文案片段。
- 一个现有 drawer 和一个现有 popover/tooltip 组件，只读取可复用的焦点、遮罩和关闭模式。

## 3. 目录与组件边界

创建：

```text
frontend/src/learner-profile/
  LearnerProfileSection.tsx
  LearnerProfileDocumentRenderer.tsx
  LearnerProfileSupportedText.tsx
  LearnerProfileCitationPopover.tsx
  LearnerProfileEvidenceDrawer.tsx
  LearnerProfileEvidenceTimeline.tsx
```

- `MyPage.tsx` 只保留页面组合，把画像加载和交互交给 `LearnerProfileSection`。
- API 公共类型仍集中在 `types/api.ts`，请求仍集中在 `services/api.ts`。
- 文档 renderer 使用穷尽式 switch 处理 block/span；未知类型进入明确错误态，不能静默用自由 HTML fallback。
- 不使用 `MarkdownView`、`dangerouslySetInnerHTML` 或运行时 Markdown parser 渲染画像。

## 4. 文档展示规则

- 移除 declared/general/tag tab、分类计数、展开前 5 条、claim 卡片、dimension、kind、revision 和 evidence grade。
- 按 API block 顺序渲染标题、段落和句子，不在前端重新排序或重新拼接 claim。
- `TEXT` 和 `SUPPORTED_TEXT` 都只作为 React 文本节点；不自动链接 URL，不解析 HTML。
- 空文档、加载、错误、重试和文档级更新时间由 section 负责。
- 完整 `blocks` 必须可访问；前端可做自然段渐进渲染，但不得按 token、条数或 viewport 永久裁掉 claim。
- anchor ID 绑定 supported span 的稳定 DOM 容器，并设置适当 `scroll-margin`，供 `AMR-12` 恢复定位。

## 5. 引用与 Popover

- supported sentence 使用克制的强调/下划线，引用编号 `[n]` 始终可见，颜色不表达强弱或正确概率。
- 整个句子和编号形成一个可聚焦交互目标；`Enter`、`Space` 和点击打开 drawer。
- 桌面 hover 或 focus 显示 popover，只使用 citation 的 source summary 和最多 2 条 preview。
- 移动端首次轻触直接打开 drawer，不依赖 hover；不要要求双击。
- popover 使用稳定定位和层级，不遮挡触发句的引用编号；离开 hover/focus 后可关闭，打开 drawer 时必须关闭。
- 使用 `aria-describedby`/`aria-controls`、可读名称和明确的 expanded/dialog 状态；不能只靠颜色表达可交互性。

## 6. 依据抽屉与分页

- 抽屉首次打开某个 `statementRef` 时调用 evidence endpoint，不能把 preview 当成完整列表。
- 切换 statement 时取消旧请求并清空旧页，避免迟到响应污染当前 citation。
- 按 `nextCursor` 显式“加载更多”；防止重复点击、重复 cursor、重复 evidence item和并发页请求。
- Review evidence 显示题目、版本、分数、通过状态、自然化 role、时间和“查看本次提交”命令。
- message evidence 显示“来自你在题目聊天中的陈述/纠正”、时间和受限摘录；第一版不生成消息深链。
- 抽屉具备 loading/empty/error/retry/end 状态，关闭后焦点返回触发句；`Escape` 和关闭图标均可关闭。
- role 到用户文案使用固定映射：观察到、后续仍存在、后续已修正、再次出现、相反记录、你的陈述、你的纠正。

Review 精确链接先通过受控 helper 构造基础 submissions path；`review/from/profileAnchor` 的完整接线在 `AMR-12` 完成。

## 7. API 类型与请求

- 用 discriminated union 表达 block、span 和 evidence，禁止大而宽的全 optional interface。
- 增加 `getLearnerProfileStatementEvidence(statementRef, {cursor, limit}, signal)`；path segment 必须编码，limit 最大 20。
- 主请求继续支持 AbortSignal；如 `AMR-10` 暴露 ETag 缓存 helper，则只缓存完整成功文档，不把 304 当错误。
- API error 不把 response body、statement ref 或 evidence 摘录写入 console。

## 8. 重点测试

- 连续文档按 block/span 原序渲染，空主题不出现，旧 tab/card/计数/底层字段消失。
- `<script>`、Markdown link、图片和模型 citation 字样只显示为文本，不产生元素、链接或脚本执行。
- 引用编号始终可见；hover/focus 显示 preview；点击、Enter、Space 和触摸打开正确 drawer。
- 第一次打开才请求完整 evidence；分页追加无重复；切换 citation 会 abort/忽略旧响应。
- preview 两条但 evidenceCount 大于两条时仍显示加载后的完整列表。
- drawer 的焦点陷阱/返回、Escape、ARIA、错误重试和移动端窄宽度布局。
- 500 个 supported span 的渲染不丢项，且不一次预取 500 份完整 evidence。
- 中英文 source/role/空态文案完整，TypeScript 穷尽检查通过。

## 9. 验证命令

```bash
npm --cache ./.npm --prefix frontend test -- \
  src/MyPage.test.tsx \
  src/learner-profile/LearnerProfileSection.test.tsx \
  src/learner-profile/LearnerProfileDocumentRenderer.test.tsx \
  src/learner-profile/LearnerProfileEvidenceDrawer.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 10. 非目标与停止条件

- 不实现 claim 接受/拒绝/抑制，不实现消息深链，不修改 Review 页面自动选中。
- 不启动 Vite；用户自行通过 `make up` 查看页面。
- 若前端解析自由 Markdown/HTML、预取全部 evidence、隐藏引用编号、只支持 hover 或用户可见画像被 token/条数裁剪，不得开始 `AMR-12`。

## 11. 上下文交接

记录组件入口、API discriminated union、evidence loader 状态机、anchor 格式和已覆盖交互。不要复制 document fixture、证据摘录或截图内容。

## 12. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 新增 `learner-profile` 文档 renderer、supported sentence、citation popover、evidence drawer/timeline 和 section；`MyPage` 仅保留页面组合。
- 前端类型替换为 block/span/evidence discriminated union；evidence 请求编码 statement ref、限制 page size，并按需加载、取消旧请求、防重复 cursor 和项目。
- 移除旧分类 tab、claim card、Markdown 渲染、截断展开和底层字段；citation anchor 使用 `learner-profile-statement-{claimRevisionId}`。

验证：

- 指定 Vitest：PASS（5 files，43 tests）。
- `npm --cache ./.npm --prefix frontend run build`：PASS；仅有既有 bundle size warning。
- `git diff --check` 与旧画像符号扫描：PASS。

偏离计划：

- Review 链接先使用受控 `learningPlanPracticeSubmissionsPath` 构造基础 path；`review/from/profileAnchor` 完整深链留给 AMR-12。

遗留事项：

- 无。

下一任务：`AMR-12`
