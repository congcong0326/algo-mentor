---
name: card-writing
description: 在 algo-mentor 的 knowledge-base 中新建或编辑复习卡片，维护 slug、核心回答、详细 Markdown 和卡片关系。
---

# 编写知识卡片

先读取 [维护手册](../../README.md)，按其格式编辑正式 `.node` 目录中的 `.card.md` 文件。

一张卡片围绕一个可独立评价的问题。标题来自文件名，身份来自维护者指定的 slug；移动、纠错和改名保留 slug，复制成新问题使用新 slug。

先给能直接回答问题的核心回答，再用二级标题展开解释。详细部分按知识需要自由组织，不补无意义的空章节。关联引用使用已存在卡片的 slug，前置关系不能成环。

修改后执行根目录 `make knowledge-validate`。只有当前任务包含导入时才执行 `make knowledge-import`；不要把本技能目录或参考资料目录转换成大纲。
