# 公开题目页静态发布说明

前端构建会从 `data/seed/problems.jsonl` 和 `data/problem-insight-seed/problem_reasons.json` 生成公开题目页，输出到 `frontend/dist`：

- `/problems/index.html`
- `/problems/{slug}/index.html`
- `/en/problems/{slug}/index.html`（仅 `BILINGUAL` 题目）
- `/sitemap.xml`、`/robots.txt`、`/seo-manifest.json`

发布时必须执行 `make package`。该命令会先构建前端和 SEO 页面，再通过 `sync-frontend` 复制到 `backend/mentor-api/src/main/resources/static`，最后打包 API JAR。题目 seed 或学习提示变化后需要重新发布，运行中的 Spring Boot 不会从数据库生成 SEO 页面。

预发布环境可通过 `SEO_SITE_URL` 或 `VITE_SITE_URL` 指定 sitemap/canonical 域名；默认值为 `https://leetmentor.com`。

Cloudflare 的 `robots.txt` 规则必须配置为回源并透传源站响应，不要使用托管 robots 内容覆盖 `/robots.txt`。部署后分别用普通请求和 `Googlebot` User-Agent 检查 `/problems/01-matrix`、`/sitemap.xml`、`/robots.txt`，确认首屏 HTML、XML 和自定义 robots 内容来自当前 JAR。
