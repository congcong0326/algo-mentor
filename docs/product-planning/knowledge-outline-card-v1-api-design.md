# 知识库用户 API

修订日期：2026-09-16。卡片长期标识统一为 slug，替代原 cardId 接口；大纲、文章保留当前快照内的数字 ID。

所有接口基础路径 `/api/knowledge`，要求登录，用户从 CurrentUserIdProvider 获取。响应沿用 ApiResponse。共享内容通过 [目录导入](knowledge-directory-import-v1-design.md) 维护，无管理员 CRUD 或上传接口。

## 路径

| 方法与相对路径 | 用途 |
| --- | --- |
| GET /topics | 技术主题、卡片和当前用户复习计数 |
| GET /outline-nodes/{id}/tree | 完整子树，NodeTree 包含 node、children |
| GET /outline-nodes/{id} | 节点、祖先 breadcrumbs、直接子节点 |
| GET /outline-nodes/{id}/cards | 当前节点卡片，page/pageSize |
| GET /outline-nodes/{id}/articles | 当前节点文章，page/pageSize |
| GET /articles/{id} | 文章全文 |
| GET /cards/{slug} | 卡片全文、标签、关系、祖先及当前用户状态 |
| GET /cards/{slug}/review-preview | 四档间隔预览，timezone 默认 UTC，不入队 |
| POST /cards/{slug}/review-attempts | 四档评价，首次成功才入队 |
| GET /review/summary | 当前用户有效复习卡总量、到期量、下次到期 |
| GET /review/cards | 复习列表，filter=ALL/DUE，page/pageSize |
| GET /review/next | 下一张到期卡片，无内容返回 null |

分页 page 从 1 开始，pageSize 默认 20，最大 100；响应包含 items/total/page/pageSize/asOf。节点 ROOT 不允许作为普通节点资源直接查询。列表与统计按用户隔离，只展示已发布且挂在有效树上的内容。

## 卡片详情

```json
{
  "slug": "java-pass-by-value",
  "outlineNodeId": 123,
  "question": "Java为什么只有值传递",
  "answerMarkdown": "核心回答",
  "explanationMarkdown": "## 原理解释\n\n任意 Markdown 详情",
  "tags": ["Java基础"],
  "relations": [{"type": "related", "slug": "hashmap-collision", "question": "HashMap如何处理哈希冲突"}],
  "breadcrumbs": [{"id": 122, "title": "Java", "slug": "Java"}],
  "learningState": {"enrolled": false, "isDue": false}
}
```

核心回答和详情分别渲染，详情标题不参与字段解释。关系按类型和源列表顺序返回，不返回指向草稿的关系。摘要包含 slug、outlineNodeId、question、sortOrder、tags、learningState，不包含答案。

文章摘要为 id/outlineNodeId/title，全文增加 bodyMarkdown/breadcrumbs；文章按 sort_order/title 显示，无四档评价入口。

## 评价

请求包含 clientAttemptId（UUID）、rating（AGAIN/HARD/GOOD/EASY）、timezone（IANA 时区）。响应为 id、cardSlug、clientAttemptId、rating、reviewedAt、dueAt、firstReview、duplicate。

首次评价事务创建用户状态；后续评价更新同一 `(user_id, card_slug)` 状态。流水保存调度前后快照。重复请求返回历史 dueAt，不返回后续评价改变后的当前到期时间；同请求 ID 被用于不同卡片或评级返回 409。

导入全量重建不会改变 slug 或用户状态关联；已移除卡片不再显示，但其历史可以继续用于幂等重放。

## 错误

- 400 KNOWLEDGE_INVALID_REQUEST：格式、slug、分页、评级、时区等非法。
- 404 KNOWLEDGE_NOT_FOUND：不存在、草稿、不可见或 ROOT。
- 409 KNOWLEDGE_ATTEMPT_CONFLICT：同用户同请求标识对应不同卡片或评级。
- 503 KNOWLEDGE_SERVICE_UNAVAILABLE：数据库或锁等待失败。

类型维护于后端 `knowledge/model/KnowledgeModels.java` 和前端 `types/knowledge.ts`；前端请求集中在 `services/knowledge.ts`。
