# 知识库维护约定

本目录是共享知识库的正式内容源。先读 [README.md](README.md) 维护手册。

- 仅 `.node` 目录、`.card.md` 和 `.article.md` 文件进入知识库；其他目录整个子树不扫描。
- 一张卡片一个文件，文件名是问题，YAML front matter 的 slug 是长期身份。修改或移动保留 slug，新问题使用新 slug。
- 元数据后到第一个顶层二级标题之前是核心回答，之后全部是详细 Markdown，不限定章节。
- 卡片关系只能通过 slug 引用。导入前执行 `make knowledge-validate`。
- 用户要求修改内容时可直接编辑；执行导入按当前会话授权范围进行，不自动发布到外部环境。
- 大纲标题默认不加编号；范围和 GitHub 固定版本来源集中写在主题级 README。修改前先读已有来源记录，避免重新扩大用户确定的主题范围。
- 新建或调整大纲先维护主题目录的 `outline.json`，再执行 `make knowledge-outline-preview KNOWLEDGE_OUTLINE=清单路径`；确认与本次任务一致后用 `knowledge-outline-apply` 补建目录。已有会话授权足够时直接继续，不为例行生成再次询问。
- 标题不可包含 `/`、`\` 等路径分隔符；工具遇到非法名称会拒绝生成。空叶子使用 `.gitkeep`，不要批量生成相同 README。
- 改名、移动应先预览，再显式迁移目录并保留卡片 slug；生成器不会自动删除、移动或覆盖内容。`make knowledge-validate` 和 `make knowledge-import` 均会先核对清单与完整路径。
- 工具自动复用已核验的构建；只有需要强制更新外部依赖或构建环境时执行 `make knowledge-cli-rebuild`，不要手动跳过检查。
