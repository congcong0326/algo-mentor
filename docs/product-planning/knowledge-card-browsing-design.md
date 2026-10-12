# 知识库文章、卡片浏览与复习管理

## 大纲内展开卡片

- 知识点的「卡片」按钮改为展开/收起直属卡片；每张卡片作为该知识点的子节点，展示问题、标签、复习状态及「查看」「加入复习」「移除复习」。目录子节点与直属卡片可以同时展示，互不影响展开状态。
- 首次展开才请求卡片，复用现有分页 API 并读完全部分页，避免超过单页的内容缺失；同一节点的卡片收起再展开复用已加载结果。加载失败在该节点下重试，复习操作失败保留原状态。
- URL 的 `open` 保存目录展开项，`cards` 保存卡片展开项；展开/收起仅替换当前历史记录，浏览器回退直接回到查看之前的大纲。
- 查看仍进入独立 Markdown 详情页。history.state 保存来源大纲路径和滚动位置，详情及关联卡片的「返回大纲」恢复原上下文；恢复时等待已展开的卡片加载完成，再还原滚动位置。直接访问详情、旧卡片列表入口仍使用原有返回列表逻辑。
- 文章仍独立阅读；本次复用现有接口，无配置或数据库迁移。回滚前端即可恢复原交互。

## 大纲内展开文章

- 「文章」按钮同样展开/收起直属文章子节点，每篇文章展示标题和「查看」按钮；文章与卡片展开状态相互独立，正文继续使用现有 Markdown 阅读页。
- 文章首次展开才加载，读取全部分页，收起再展开复用列表；加载失败原位重试，并忽略已取消读取的迟到响应。
- URL 的 `articles` 保存文章展开项；阅读页的 `article` 指定所查看的文章，避免多篇文章时误打开第一篇。旧阅读页 URL 仍默认阅读第一篇。
- 文章与卡片共用来源大纲路径和滚动快照；恢复大纲时等待已展开的两类内容加载完成。复用现有 API，无配置或数据库迁移。

## 既有分页列表与复习契约

- 大纲使用嵌套连接线、目录图标和独立操作区表达层级；目录标题与箭头均可展开/收起，并暴露展开状态。窄屏操作区换行，长标题完整换行。
- 所有节点均按直属内容显示入口：`directArticleCount > 0` 显示「文章」，`directCardCount > 0` 显示「卡片」，两者都有则并列展示。子树卡片总数不用于判定当前节点的卡片入口，避免进入空列表。
- 文章子节点的「查看」进入 `/knowledge/nodes/{id}/articles?article={articleId}` 阅读指定文章；旧链接默认打开第一篇文章，多篇时提供文章目录切换，支持错误重试和返回所属大纲；不加载或展示卡片。
- 旧卡片列表路径继续支持独立分页浏览，不加载或展示文章，复用复习中心的行布局、搜索与翻页样式；每页 20 张卡片，搜索问题与标签时重置页码。大纲的卡片入口采用上述原位展开交互。上述调整复用现有 API，无数据库迁移。
- 每行固定提供「查看」「加入复习」「移除复习」，根据当前加入状态禁用不适用的操作。查看进入 `/knowledge/cards/{slug}`，直接展开答案、解释及关联卡片。
- 正文共用 `KnowledgeCardContent`、`MarkdownView` 与复习中心的 `review-problem-content` 排版，统一行距、段间距、列表、表格和代码块。
- `GET /api/knowledge/outline-nodes/{id}/cards` 增加可选 `keyword`，查询总数与分页采用同一筛选条件。
- `PUT /api/knowledge/cards/{slug}/review-enrollment` 加入复习，`DELETE` 同路径移除，返回最新 `LearningState`。操作使用登录用户并与评级共用事务锁，重复操作幂等。
- V77 增加 `review_enrolled`（历史状态默认已加入），允许尚未评级的 `last_rating`、`last_reviewed_at` 为空。首次加入初始化 FSRS 并立即到期；移除只暂停入队，保留状态和评级流水；再次加入保留原调度。评级仍可自动加入，首次评级由实际评级时间判断。
- 验证覆盖分页搜索、详情跳转、完整 Markdown、加入/移除按钮状态、错误重试、真实 PostgreSQL 迁移与状态保留。回滚应用时保留兼容新增字段；新建但未评级的状态包含空评级，旧版应用不应继续处理这些状态。

## 验证结果

- 文章原位展开：上述相关前端测试共 68 项通过，路由联动测试 6 项通过，`make frontend-build` 通过；新增覆盖文章完整分页、按需加载与缓存、失败重试、迟到响应、指定文章和目录切换、文章与卡片同时展开及等待两类列表后恢复滚动。
- Chromium 使用生产构建与模拟 API 验证文章展开/收起、指定文章正文、目录切换与 URL 同步、返回大纲和浏览器后退；返回前后滚动位置均为 449px，文章及卡片均保持展开。桌面、390px 窄屏和深色模式无页面异常，窄屏无横向溢出；截图位于 `/tmp/algo-mentor-knowledge-articles/`。
- 大纲原位卡片交互：`npm --cache ./.npm --prefix frontend test -- src/knowledge/KnowledgeNavigationPages.test.tsx src/app/navigation.test.ts src/app/AppShell.test.tsx src/KnowledgePage.test.tsx src/services/knowledge.test.ts`，61 项通过，覆盖按需读取、完整分页、收起缓存、迟到响应、复习操作、错误重试、返回来源与等待卡片加载后恢复滚动；`make frontend-build` 通过。
- 路由联动：`npm --cache ./.npm --prefix frontend test -- src/App.test.tsx -t 'opens cards inside the outline|knowledge'`，6 项通过，覆盖新大纲读卡路径、详情返回、浏览器后退与旧分页列表兼容。
- Chromium 使用生产构建与模拟 API 验证原位展开、复习状态切换和浏览器后退；后退前后滚动位置均为 542px，390px 窄屏无横向溢出、无页面异常，并检查桌面和深色布局。截图位于 `/tmp/algo-mentor-knowledge-inline/`（本地临时验证产物）。
- 大纲与文章入口调整：`KnowledgeNavigationPages.test.tsx`、`navigation.test.ts`、`AppShell.test.tsx` 共 45 项通过，`App.test.tsx -t knowledge` 共 5 项通过；`make frontend-build` 通过。
- 使用生产构建和模拟 API 在 Chromium 检查桌面、390px 窄屏和深色模式，验证长标题、两种内容按钮、文章切换与页面无横向溢出。
- `make frontend-build`：通过 TypeScript 检查和生产构建。
- `npm --cache ./.npm --prefix frontend test -- src/knowledge/KnowledgeNavigationPages.test.tsx src/KnowledgePage.test.tsx src/services/knowledge.test.ts src/services/api.test.ts src/components/MarkdownView.test.tsx src/app/navigation.test.ts`：88 项通过。
- `npm --cache ./.npm --prefix frontend test -- src/App.test.tsx -t 'knowledge pagination'`：独立详情路由、分页参数保留和返回原页通过。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=KnowledgeControllerTest -Dit.test=KnowledgeImportIT -Dfailsafe.failIfNoSpecifiedTests=false verify`：4 项接口测试、6 项 PostgreSQL 集成测试通过，使用独立测试 schema 验证 V77。
