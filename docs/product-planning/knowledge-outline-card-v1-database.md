# 知识库数据库说明

修订日期：2026-09-16。

## 迁移

- V74：初始知识大纲、卡片、文章、用户状态及复习流水表。
- V75：旧测试内容。
- V76：开发期重建上述知识表，新增稳定卡片 slug、标签、文章顺序和关系表，用户状态改用 card_slug。

本次用户明确授权清理知识库旧开发数据。V76 不修改账号、算法题、学习计划等其他业务数据；V74/V75 文件保持原样。

## 当前模型

| 表 | 身份与关系 | 日常导入行为 |
| --- | --- | --- |
| knowledge_outline_node | 临时数字 ID、parent_id 单树；同父 slug 唯一 | 全量重建 |
| knowledge_card | 数字 ID 可变，slug 全局唯一；归属一个节点 | 全量重建 |
| knowledge_article | 数字 ID、归属节点、sort_order | 全量重建 |
| knowledge_card_relation | source_slug、relation_type、target_slug 唯一，sort_order | 全量重建 |
| knowledge_card_user_state | 用户 ID + card_slug 唯一，无指向内容表的物理外键 | 保留 |
| knowledge_card_review_attempt | 外键 user_state_id；用户 ID + client_attempt_id 唯一 | 保留 |

用户状态和流水只在首次/后续评价事务更新。删除内容保留历史，当前读取按 slug 与已发布内容关联。用户账号删除仍级联清理其个人状态与流水。

正文存储：核心回答为 answer_markdown，全部详情为 explanation_markdown；example_markdown/source_markdown 为旧兼容列，本导入器置空。tags_json 保存标签数组，文章 body_markdown 保存完整正文。

大纲和文章标题最多 200 字符，卡片问题最多 500 字符，slug 最长 160 字符，核心回答最多 20000 字符。published 文章的数据库 published_at 为本次导入时间，不作为稳定内容版本或排序依据。

完整事务顺序和运行命令见 [导入设计](knowledge-directory-import-v1-design.md) 与 [维护手册](../../knowledge-base/README.md)。
