# AMR-12：精确 Review 深链与原句返回

> 波次：D
>
> 状态：DONE
>
> 直接依赖：AMR-11
>
> 建议首轮文件上限：10

## 1. 目标与完成标准

把画像 Review evidence 接到精确提交历史深链，使提交历史加载后自动选中、高亮并滚动到指定 Review；从该页面返回时恢复 `/me` 的原句锚点和滚动位置。

所有路径和 query 参数通过受控 helper 构造和归一化。`profileAnchor` 不是返回 URL，不能被利用为开放重定向或任意 DOM selector。

## 2. 必须读取

- `CURRENT.md` 和 `AMR-11` 完成备注。
- `CONTRACTS.md` 第 11、12 节。
- `frontend/src/app/navigation.ts` 及其测试。
- `frontend/src/App.tsx` 的 `normalizeAuthenticatedSearch`、`navigateToPath` 和 popstate 片段。
- `frontend/src/LearningPlans.tsx` 的 submissions route 接线。
- `PracticeSubmissionHistoryPage.tsx`、`ReviewVersionList.tsx` 及相关测试。
- `LearnerProfileEvidenceDrawer.tsx` 和 `LearnerProfileSupportedText.tsx` 的链接/anchor 片段。
- `MyPage.tsx` 或 `LearnerProfileSection.tsx` 的文档完成渲染时机。

## 3. 路由契约

扩展：

```text
/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/submissions
  ?review={reviewId}
  &from=learner-profile
  &profileAnchor={anchorId}
```

- 为 `learningPlanPracticeSubmissionsPath` 增加 typed options，而不是在组件中字符串拼接。
- 公共 query key、来源值和 anchor 前缀放入 navigation contract 常量。
- `review` 只接受正安全整数；`from` 只接受 `learner-profile`；`profileAnchor` 只接受 API 约定前缀和 ASCII 字符集，例如 `^memory-statement-[A-Za-z0-9_-]+$`。
- helper 对 slug 和 query 使用标准编码；非法 options 被忽略或显式拒绝，行为由测试固定。
- 增加 `learnerProfilePath({anchor})` 或等价 helper，只能生成 `/me?profileAnchor=...`，不接受任意路径。

## 4. 查询参数归一化

当前 `App.tsx::normalizeAuthenticatedSearch` 会清除这些参数，必须增加路径级白名单：

- submissions 路径保留合法 `review/from/profileAnchor`；继续兼容该路径已有的合法参数，未知参数丢弃。
- `/me` 只保留合法 `profileAnchor`，未知、超长或非法字符参数丢弃。
- `pack=today` 的现有学习计划语义不得吞掉 submissions 深链参数；按具体路由优先于宽泛 `/learning-plans/\d+` 规则处理。
- 首次加载、应用内导航和 `popstate` 使用相同 normalize 结果。

## 5. Review 自动定位

- `PracticeSubmissionHistoryPage` 接收规范化后的 requested review/origin/anchor，而不是自行读取未经校验的全局 URL。
- 历史加载完成后优先选择 requested review；仅无 requested review 时沿用 latest/first 默认逻辑。
- 指定 Review 不在当前 session/用户历史中时显示稳定“该提交不可用”状态，不加载其他 ID，也不泄漏其是否属于其他用户。
- `ReviewVersionList` 为目标项提供 ref、选中高亮和短暂来源高亮；列表完成布局后调用 `scrollIntoView({block: 'nearest'})`。
- 自动选择后加载现有受保护 Review detail；用户手动切换后不被后续 effect 重置回深链目标。

## 6. 返回画像与锚点恢复

- `from=learner-profile` 且 anchor 合法时，返回按钮文案和行为改为返回画像；否则保持现有返回 Practice Chat。
- 返回路径只由 `learnerProfilePath` 构造，不能使用 `document.referrer` 或 query 中的任意 URL。
- `/me` 文档加载并渲染完成后，按精确 DOM id 查找 anchor，滚动到视口中部或最近位置，设置可访问焦点并短暂高亮。
- 不把 anchor 直接拼成 CSS selector；使用 `document.getElementById` 并先通过 contract validator。
- 完成定位后使用 replace navigation 清除 `profileAnchor`，避免刷新和后续返回重复滚动；文档未包含该 anchor 时安静回到画像顶部并清除参数。
- 浏览器前进/后退、直接打开深链和页面刷新都保持一致行为。

## 7. 重点测试

- path helper 对 review/from/anchor 编码、非法值、未知 query 和 slug 特殊字符处理正确。
- `normalizeAuthenticatedSearch` 在首次加载、navigate 和 popstate 中保留合法参数并剔除恶意参数。
- 深链 Review 在历史到达后自动选中、detail 加载、高亮和滚动；手动选择不被覆盖。
- Review 不存在、属于其他 session 或 detail 返回 404 时不回退加载目标 ID之外的数据。
- 从画像进入时返回 `/me` 原句；普通入口仍返回 Practice Chat。
- `/me` 等文档渲染后再定位，清除 query，焦点可见；缺失 anchor 不报错。
- `profileAnchor=https://...`、CSS selector、超长值、编码绕过和开放重定向样例全部拒绝。
- 移动端窄布局下高亮项和返回按钮不遮挡 Review 内容。

## 8. 验证命令

```bash
npm --cache ./.npm --prefix frontend test -- \
  src/app/navigation.test.ts \
  src/learning-plans/PracticeSubmissionHistoryPage.test.tsx \
  src/learner-profile/LearnerProfileEvidenceDrawer.test.tsx \
  src/MyPage.test.tsx \
  src/App.test.tsx

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 9. 非目标与停止条件

- 不新增后端 Review detail 字段，不允许后端返回拼接完成的前端 URL。
- 不实现消息深链或任意“返回来源页”参数。
- 若 query 会被 App 归一化清除、Review 未经 session/user 保护即可加载、anchor 可作为任意 selector/URL 或返回后无法稳定恢复原句，不得进入波次 E。

## 10. 上下文交接

记录 path helper options、query validator、Review 自动选择状态、返回路径和 anchor 清理时机。不要携带完整 URL 测试矩阵或 Review 内容。

## 11. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- `navigation.ts` 集中维护 submissions deep link 的 review/from/profileAnchor 契约、严格 query 校验与 `/me` return helper。
- App 在首次加载、应用内导航和 popstate 对 submissions、`/me` 分别白名单归一化；`pack=today` 与深链参数可共存。
- Review 历史仅从当前受保护 history 选择目标、加载 detail、滚动并短暂高亮；不存在目标显示稳定不可用状态，手动选择不会被重置。
- 依据链接传递当前句子 anchor；返回 `/me` 后文档渲染完成才用 DOM id 定位、聚焦和高亮，并 replace 清除 query。

验证：

- 指定 Vitest：PASS（5 files，110 tests）。
- `npm --cache ./.npm --prefix frontend run build`：PASS；仅有既有 bundle size warning。
- `git diff --check`：PASS。

偏离计划：

- 新增 `PracticeSubmissionHistoryPage.test.tsx`，将 deep link 的受保护选择、不可用状态和手动选择覆盖独立于 App 大型测试。

遗留事项：

- 无。

下一任务：`AMR-13`
