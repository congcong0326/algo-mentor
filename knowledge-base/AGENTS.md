# 知识库维护约定

本目录是共享知识库的正式内容源。先读 [README.md](README.md) 维护手册。

- 仅 `.node` 目录、`.card.md` 和 `.article.md` 文件进入知识库；其他目录整个子树不扫描。
- 一张卡片一个文件，文件名是问题，YAML front matter 的 slug 是长期身份。修改或移动保留 slug，新问题使用新 slug。
- 元数据后到第一个顶层二级标题之前是核心回答，之后全部是详细 Markdown，不限定章节。
- 卡片关系只能通过 slug 引用。导入前执行 `make knowledge-validate`。
- 用户要求修改内容时可直接编辑；执行导入按当前会话授权范围进行，不自动发布到外部环境。
