# 登录入口设置设计

## 目标

将登录和注册入口的全局限制从部署环境配置迁移到数据库，管理员可以在管理后台修改，修改后对新的登录、注册和 OAuth 入口请求立即生效。

已经建立的认证会话不重新执行这些入口限制检查，也不会因为管理员修改开关而被注销。用户状态为 `ACTIVE`、`DISABLED` 或 `DELETED` 的既有会话校验仍保持原有行为。

## 设置范围

`auth_login_settings` 是单例表，固定 `id = 1`，包含以下字段：

- `account_registration_enabled`：是否允许创建新账号。
- `password_login_enabled`：是否允许邮箱密码登录。
- `password_registration_enabled`：是否允许邮箱密码注册。
- `google_login_enabled`：是否允许 Google OAuth 登录。
- `github_login_enabled`：是否允许 GitHub OAuth 登录。

OAuth client ID、client secret 等凭据仍然只从环境配置读取；数据库开关只负责运行时入口控制。

## 运行时读取

认证模块通过 `AuthLoginSettingsProvider` 每次读取当前设置。首次读取发现单例记录不存在时，使用现有 `AuthProperties` 的值初始化数据库，保证升级期间原有环境变量行为不变。初始化完成后，数据库记录成为运行时来源。

密码登录、密码注册、OAuth 授权跳转、OAuth 回调和公开能力接口都会读取当前快照，因此管理员保存后无需重启即可影响后续请求。当前用户响应只用于展示当前密码登录能力，不会用来撤销会话。

## 管理接口

- `GET /api/admin/auth-settings`
- `PATCH /api/admin/auth-settings`

接口要求 `auth-settings:manage` 权限。更新请求提交完整的五项布尔快照，避免部分更新形成管理员未明确选择的组合；更新成功后写入管理员 ID、更新时间和审计事件 `AUTH_LOGIN_SETTING_UPDATE`。

## 与内测白名单的关系

内测邮箱白名单继续由 `auth_beta_access_settings` 和既有管理接口维护，不复制到登录设置表。白名单变化只影响后续入口判断，移除白名单记录不会主动吊销已经建立的 Session。

## 数据库迁移

Flyway 迁移为 `V71__auth_login_settings.sql`。表不预置管理员信息，首次运行时由服务的幂等初始化逻辑插入单例记录；并发初始化由主键冲突保护。
