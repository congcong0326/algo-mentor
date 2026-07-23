# 用户组与管理员成员管理功能设计

> 前置依赖：先完成 `docs/admin-frontend-shell-refactoring-design.md`，再实现本文功能。
> 本文只设计用户组和用户-组成员关系，不设计访问策略、会员支付、订阅账单或用户标签。

## 0. 已定决策

1. 用户组是独立身份数据，不在 `auth_users` 增加单一 `group_id` 字段。
2. 用户和用户组采用多对多关系，同一用户可以同时属于多个组。
3. 用户组与 `AuthRole` 分离：角色继续表示 `USER/ADMIN` 系统身份，用户组表示业务分组。
4. 第一版不引入用户标签，不允许使用自由文本标签替代用户组。
5. 用户组支持管理员手动创建、编辑、停用，以及手动添加和移除成员。
6. 成员关系支持可选到期时间；有效性由查询时间判断，不依赖定时任务及时删除记录。
7. 第一版不硬删除用户组。停用用户组后，其成员关系保留，但不再视为有效组成员。
8. 第一版不保留完整成员变更历史，管理员操作通过现有低敏审计能力留痕。
9. 用户组管理复用现有 `user:manage` 权限，不新增更细管理员权限。

## 1. 背景

当前用户模型已经包含身份字段、状态和角色，但缺少可由管理员维护的业务分组。后续会员、内测、活动或其他策略能力需要引用稳定的用户集合，因此先建立用户组事实：

```text
AuthUser
  -> UserGroupMembership
      -> UserGroup
```

本阶段只回答以下问题：

- 系统中有哪些用户组；
- 一个用户当前属于哪些用户组；
- 一个用户组当前有哪些有效成员；
- 管理员如何手动添加和移除成员。

后续访问策略可以读取这些事实，但不属于本文范围。

## 2. 目标与非目标

### 2.1 目标

- 在 `identity` 模块建立用户组和成员关系的统一模型。
- 管理员可以分页查询、创建、编辑和停用用户组。
- 管理员可以查看组内有效成员，并按用户 ID、邮箱或昵称搜索。
- 管理员可以向组内批量添加用户，并设置可选到期时间。
- 管理员可以从用户组详情或用户详情中移除成员。
- 用户管理列表和用户详情可以展示当前有效用户组。
- 所有写操作具备明确的幂等、冲突和错误语义。

### 2.2 非目标

- 不实现访问策略、资源权限、模型白名单或额度限制。
- 不实现会员套餐、支付、退款、续费或账单。
- 不实现用户标签、动态人群规则或自动分群。
- 不实现用户组嵌套、父子组或组继承。
- 不实现组管理员、组内角色或用户自助加入。
- 不实现导入文件、邀请链接和审批流程。
- 不实现成员变更历史查询页面。
- 不将用户组写入 Spring Security authority 或 Session。

## 3. 模块归属

用户组属于身份本体，放在 `backend/identity`：

```text
backend/identity/src/main/java/org/congcong/algomentor/identity/group/model
backend/identity/src/main/java/org/congcong/algomentor/identity/group/repository
backend/identity/src/main/java/org/congcong/algomentor/identity/group/service
backend/identity/src/main/java/org/congcong/algomentor/identity/controller/group
backend/identity/src/main/resources/mapper/identity/group
backend/identity/src/main/resources/db/migration/identity
```

前端放在管理员目录：

```text
frontend/src/admin/groups/UserGroupManagementPage.tsx
frontend/src/admin/groups/UserGroupDetailPage.tsx
frontend/src/admin/groups/UserGroupMemberDialog.tsx
frontend/src/admin/groups/UserGroupMemberTable.tsx
```

不新增 Maven 模块。`auth` 继续负责认证和角色装载，不读取用户组表。

## 4. 数据模型

Flyway 版本号在实施前扫描全仓后确定，以下只固定表结构和语义。

### 4.1 `identity_user_group`

```sql
CREATE TABLE identity_user_group (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_identity_user_group_code UNIQUE (code),
  CONSTRAINT ck_identity_user_group_code
    CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
  CONSTRAINT ck_identity_user_group_name
    CHECK (length(btrim(name)) > 0),
  CONSTRAINT ck_identity_user_group_status
    CHECK (status IN ('ACTIVE', 'DISABLED'))
);
```

字段语义：

- `code`：跨模块引用的稳定标识，例如 `PRO`、`BETA_TESTER`；创建后不可修改。
- `name`：管理员可读名称，可以修改。
- `description`：可选管理说明，不作为权限或程序判断依据。
- `status`：`ACTIVE` 表示可以继续维护有效成员，`DISABLED` 表示整个组暂不生效。

### 4.2 `identity_user_group_membership`

```sql
CREATE TABLE identity_user_group_membership (
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  group_id BIGINT NOT NULL REFERENCES identity_user_group(id) ON DELETE RESTRICT,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, group_id),
  CONSTRAINT ck_identity_user_group_membership_expiry
    CHECK (expires_at IS NULL OR expires_at > joined_at)
);

CREATE INDEX idx_identity_user_group_membership_group
  ON identity_user_group_membership(group_id, user_id);

CREATE INDEX idx_identity_user_group_membership_user_expiry
  ON identity_user_group_membership(user_id, expires_at);
```

第一版每个用户与每个组最多保留一条当前关系。重复添加采用更新语义：

- 已存在且仍有效：更新 `expires_at`，不重复创建；
- 已存在但已过期：重置 `joined_at` 和 `expires_at`；
- 不存在：新增关系；
- 手动移除：删除当前关系。

### 4.3 有效成员定义

当前有效成员必须同时满足：

```text
group.status = ACTIVE
AND membership.joined_at <= now
AND (membership.expires_at IS NULL OR membership.expires_at > now)
AND user.status != DELETED
```

用户被禁用时成员关系不删除，管理员仍可在详情中看到该用户状态；后续业务是否允许禁用用户使用功能由各业务入口的用户状态校验决定。

## 5. 领域模型

### 5.1 枚举

```java
public enum UserGroupStatus {
  ACTIVE,
  DISABLED
}
```

### 5.2 核心模型

```text
UserGroup
  id
  code
  name
  description
  status
  createdAt
  updatedAt

UserGroupMembership
  userId
  groupId
  joinedAt
  expiresAt
  createdAt
  updatedAt
```

公共状态值、API 路径和错误码分别收敛到枚举或契约常量类，不在 Controller、Service 和前端页面中重复写字符串。

## 6. 管理员 API

基础路径：

```text
/api/admin/user-groups
```

### 6.1 用户组 API

```text
GET    /api/admin/user-groups
POST   /api/admin/user-groups
GET    /api/admin/user-groups/{groupId}
PATCH  /api/admin/user-groups/{groupId}
```

查询参数：

```text
page
pageSize
keyword     按 code/name 模糊查询
status      ACTIVE/DISABLED
```

创建请求：

```json
{
  "code": "PRO",
  "name": "专业会员",
  "description": "专业会员用户组"
}
```

更新请求只允许修改：

```json
{
  "name": "专业版会员",
  "description": "更新后的说明",
  "status": "ACTIVE"
}
```

`code` 创建后不可修改。第一版不提供删除接口。

### 6.2 成员 API

```text
GET     /api/admin/user-groups/{groupId}/members
POST    /api/admin/user-groups/{groupId}/members
DELETE  /api/admin/user-groups/{groupId}/members/{userId}
```

成员查询参数：

```text
page
pageSize
keyword     按用户 ID、邮箱或昵称查询
```

批量添加请求：

```json
{
  "userIds": [42, 43],
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

- `expiresAt` 可以为空，表示长期有效。
- 单次最多添加 `100` 个用户，限制定义在公共约束常量中。
- 批量操作返回每个用户的结果，避免部分失败时管理员无法判断实际状态。

返回结果状态：

```text
ADDED
UPDATED
USER_NOT_FOUND
USER_DELETED
INVALID_EXPIRY
```

删除不存在的成员关系按幂等成功处理，响应可以返回 `removed=false`。

### 6.3 用户详情扩展

现有管理员用户详情响应增加当前有效成员关系：

```json
{
  "groups": [
    {
      "id": 1,
      "code": "PRO",
      "name": "专业会员",
      "joinedAt": "2026-07-01T00:00:00Z",
      "expiresAt": "2026-08-01T00:00:00Z"
    }
  ]
}
```

用户列表响应增加轻量组摘要，只返回 `id/code/name`。Repository 应按当前页用户 ID 批量查询组关系，禁止逐用户执行 N+1 查询。

## 7. 服务语义

### 7.1 创建和编辑用户组

- `code` 统一 `trim + uppercase(Locale.ROOT)` 后校验。
- 相同 `code` 重复创建返回稳定冲突错误。
- 停用组不会删除成员关系。
- 停用组不允许新增成员；恢复为 `ACTIVE` 后原未过期关系重新生效。
- 修改名称和说明不影响成员关系。

### 7.2 添加成员

- 添加前验证用户存在且未软删除。
- 添加到停用组返回 `USER_GROUP_DISABLED`。
- `expiresAt` 必须晚于当前时间。
- 对同一请求中的重复 `userId` 先去重。
- 整批写入在一个事务中完成，单个用户的业务结果明确返回。
- 管理员手动添加动作记录低敏审计，只记录 groupId、用户数量和结果，不记录邮箱等隐私字段。

### 7.3 移除成员

- 从用户详情或用户组详情发起的移除调用同一 Service。
- 删除不存在的关系视为幂等成功。
- 移除只删除成员关系，不修改用户状态、角色或业务数据。
- 审计记录 groupId、userId、操作者和结果。

## 8. 管理员前端

### 8.1 导航与路由

在管理员 `AdminShell` 的“用户与访问”业务域局部导航中新增：

```text
用户组管理    /admin/user-groups
```

用户组列表和详情共用 `user:manage` 权限。后端接口继续由管理员安全规则保护。

### 8.2 用户组列表

列表字段：

```text
名称
编码
状态
有效成员数
创建时间
更新时间
操作
```

页面操作：

- 按名称或编码搜索；
- 按状态筛选；
- 创建用户组；
- 打开用户组详情；
- 编辑名称、说明和状态。

创建和编辑使用对话框。`code` 创建后显示为只读文本，不再提供输入控件。

### 8.3 用户组详情

详情使用独立页面，而不是窄抽屉：

```text
/admin/user-groups/{groupId}
```

页面包含：

- 用户组名称、编码、状态和说明；
- 有效成员数量；
- 组内用户搜索；
- 成员表格；
- 添加成员；
- 移除成员。

成员表格字段：

```text
用户
邮箱
账号状态
加入时间
到期时间
操作
```

点击用户名称打开现有管理员用户详情抽屉，避免重复建设用户信息页面。

### 8.4 添加成员

点击“添加用户”打开可搜索对话框：

- 按用户 ID、邮箱或昵称搜索；
- 使用复选框选择一个或多个用户；
- 已在组内的用户显示当前到期时间，不允许重复勾选；
- 提供可选到期时间输入；
- 提交后展示新增、更新和失败数量；
- 成功后刷新成员列表和有效成员数。

### 8.5 移除成员

- 每行使用移除图标按钮并提供 tooltip。
- 点击后显示确认对话框，明确用户和目标用户组。
- 成功后保持当前搜索和分页位置。
- 批量移除不作为第一版必需能力。

### 8.6 用户管理页面扩展

现有用户列表增加“用户组”列：

- 最多直接展示两个组名；
- 超过两个显示“+N”，悬停或聚焦时展示完整列表；
- 没有有效用户组时显示统一空值。

现有用户详情抽屉增加“所属用户组”区块：

```text
专业会员    PRO           2026-08-01 到期    移除
内测用户    BETA_TESTER   长期有效            移除

[添加到用户组]
```

从用户详情添加时选择目标组和可选到期时间，最终仍调用统一成员 API。

## 9. 错误码

建议在 `identity` 模块增加稳定错误码：

```text
USER_GROUP_NOT_FOUND
USER_GROUP_CODE_CONFLICT
USER_GROUP_DISABLED
USER_GROUP_INVALID_CODE
USER_GROUP_INVALID_EXPIRY
USER_GROUP_MEMBER_NOT_FOUND
USER_GROUP_BATCH_LIMIT_EXCEEDED
```

错误消息继续通过项目统一错误响应和国际化机制返回。

## 10. 测试范围

### 10.1 后端

- Flyway 迁移包含表、约束、外键和索引。
- `code` 规范化、唯一冲突和不可修改。
- 用户组停用和恢复后的成员有效性。
- 永久成员、未到期成员和已到期成员查询。
- 重复添加的新增、更新和重新激活语义。
- 批量请求去重、数量上限和部分业务结果。
- 删除不存在关系的幂等语义。
- 已删除用户不能被添加，禁用用户可以保留现有关系。
- 用户列表批量加载组摘要，不产生 N+1 查询。
- 非管理员访问所有用户组接口返回 `403`。

### 10.2 前端

- 具有 `user:manage` 权限时显示用户组导航。
- 用户组列表支持搜索、状态筛选和分页。
- 创建和编辑对话框正确处理校验和冲突错误。
- 用户组详情支持成员搜索和分页。
- 添加成员支持多选、到期时间和结果汇总。
- 移除成员需要确认，成功后保持当前列表状态。
- 用户列表正确展示两个组和“+N”。
- 用户详情可以添加和移除用户组。
- 移动端成员表格和对话框不存在文字或按钮重叠。

## 11. 实施顺序

1. 完成管理员 `AdminShell` 改造并验证现有管理员页面无回归。
2. 新增用户组迁移、领域模型和 MyBatis Repository。
3. 新增用户组与成员管理 Service、错误码和管理员 API。
4. 新增用户组列表和详情页面，并启用管理员导航项。
5. 扩展用户列表和用户详情中的用户组展示与操作。
6. 补充后端、前端和管理员权限测试。

## 12. 验收标准

- 管理员可以创建、编辑和停用用户组。
- 管理员可以查看组内当前有效成员。
- 管理员可以批量添加用户并设置可选到期时间。
- 管理员可以从用户组详情和用户详情移除成员。
- 用户列表和用户详情展示一致的当前有效用户组。
- 用户组不影响现有角色、认证、用户状态和普通用户界面。
- 已到期成员无需定时删除，也不会出现在当前有效成员结果中。
- 本期没有引入访问策略、会员支付、订阅或用户标签实现。
