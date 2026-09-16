# 知识库卡片浏览与复习管理

- 大纲使用嵌套连接线、目录图标和独立操作区表达层级；目录标题与箭头均可展开/收起，并暴露展开状态。窄屏操作区换行，长标题完整换行。
- 所有节点均按直属内容显示入口：`directArticleCount > 0` 显示「文章」，`directCardCount > 0` 显示「卡片」，两者都有则并列展示。子树卡片总数不用于判定当前节点的卡片入口，避免进入空列表。
- 文章入口进入 `/knowledge/nodes/{id}/articles` 独立阅读页，默认打开第一篇文章，多篇时提供文章目录切换，支持错误重试和返回所属大纲；不加载或展示卡片。
- 卡片入口进入独立分页列表，不加载或展示文章，复用复习中心的行布局、搜索与翻页样式；每页 20 张卡片，搜索问题与标签时重置页码。上述调整复用现有 API，无数据库迁移。
- 每行固定提供「查看」「加入复习」「移除复习」，根据当前加入状态禁用不适用的操作。查看进入 `/knowledge/cards/{slug}`，直接展开答案、解释及关联卡片。
- 正文共用 `KnowledgeCardContent`、`MarkdownView` 与复习中心的 `review-problem-content` 排版，统一行距、段间距、列表、表格和代码块。
- `GET /api/knowledge/outline-nodes/{id}/cards` 增加可选 `keyword`，查询总数与分页采用同一筛选条件。
- `PUT /api/knowledge/cards/{slug}/review-enrollment` 加入复习，`DELETE` 同路径移除，返回最新 `LearningState`。操作使用登录用户并与评级共用事务锁，重复操作幂等。
- V77 增加 `review_enrolled`（历史状态默认已加入），允许尚未评级的 `last_rating`、`last_reviewed_at` 为空。首次加入初始化 FSRS 并立即到期；移除只暂停入队，保留状态和评级流水；再次加入保留原调度。评级仍可自动加入，首次评级由实际评级时间判断。
- 验证覆盖分页搜索、详情跳转、完整 Markdown、加入/移除按钮状态、错误重试、真实 PostgreSQL 迁移与状态保留。回滚应用时保留兼容新增字段；新建但未评级的状态包含空评级，旧版应用不应继续处理这些状态。

## 验证结果

- 大纲与文章入口调整：`KnowledgeNavigationPages.test.tsx`、`navigation.test.ts`、`AppShell.test.tsx` 共 45 项通过，`App.test.tsx -t knowledge` 共 5 项通过；`make frontend-build` 通过。
- 使用生产构建和模拟 API 在 Chromium 检查桌面、390px 窄屏和深色模式，验证长标题、两种内容按钮、文章切换与页面无横向溢出。
- `make frontend-build`：通过 TypeScript 检查和生产构建。
- `npm --cache ./.npm --prefix frontend test -- src/knowledge/KnowledgeNavigationPages.test.tsx src/KnowledgePage.test.tsx src/services/knowledge.test.ts src/services/api.test.ts src/components/MarkdownView.test.tsx src/app/navigation.test.ts`：88 项通过。
- `npm --cache ./.npm --prefix frontend test -- src/App.test.tsx -t 'knowledge pagination'`：独立详情路由、分页参数保留和返回原页通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=KnowledgeControllerTest -Dit.test=KnowledgeImportIT -Dfailsafe.failIfNoSpecifiedTests=false verify`：4 项接口测试、6 项 PostgreSQL 集成测试通过，使用独立测试 schema 验证 V77。
