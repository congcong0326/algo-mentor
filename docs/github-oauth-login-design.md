# GitHub OAuth 登录研发设计

## 目标

在现有 Google OIDC、邮箱密码和 Spring Session 认证链路上增加 GitHub OAuth2 登录，复用本地用户、角色、内测邮箱准入、会话策略和头像展示能力。

## 登录流程

```text
浏览器访问 /oauth2/authorization/github
  -> GitHub OAuth 授权页
  -> /login/oauth2/code/github
  -> Spring Security 使用 code 换取 access token
  -> GET https://api.github.com/user
  -> GET https://api.github.com/user/emails
  -> 同步本地用户与 auth_oauth_accounts
  -> 建立 Spring Session 并跳转登录成功页
```

Spring Security 负责授权码交换和基础用户资料请求。业务代码只在登录期间使用 access token 查询邮箱，不把 access token 写入业务表或日志。

## 用户字段映射

| 本地字段 | GitHub 字段 | 规则 |
| --- | --- | --- |
| `provider_subject` | `id` | 使用不可变数字 ID 的字符串形式，不使用可修改的 `login` |
| `email` | `/user/emails` 的 `primary && verified` | 用于内测白名单和已有账号合并；缺失时拒绝登录 |
| `display_name` | `name` / `login` | `name` 为空时回退到 `login` |
| `avatar_url` | `avatar_url` | 每次登录同步，前端继续读取 `CurrentUser.avatarUrl` |

首次登录优先按 `(provider, provider_subject)` 查找绑定；不存在绑定时，按规范化且已验证的邮箱复用已有用户，否则创建新用户并授予 `USER` 角色。

## 配置

本地 `.env` 和部署环境需要提供：

```dotenv
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
```

GitHub OAuth App 的 Authorization callback URL：

```text
http://localhost:8080/login/oauth2/code/github
```

生产环境改为实际 HTTPS 域名。开发与生产建议分别创建 OAuth App，避免共用 callback 配置和 secret。

申请 scope：

- `read:user`：读取用户基础资料和头像。
- `user:email`：读取已验证的主邮箱。

## 安全与失败语义

- 不记录 client secret、access token、完整 Authorization 头或邮箱列表响应。
- GitHub 邮箱接口使用 5 秒连接和读取超时。
- 邮箱查询失败、没有已验证主邮箱或返回未知 provider 时，统一进入现有 OAuth 登录失败跳转。
- 用户被禁用、删除或不满足内测邮箱白名单时，继续使用现有拒绝逻辑。
