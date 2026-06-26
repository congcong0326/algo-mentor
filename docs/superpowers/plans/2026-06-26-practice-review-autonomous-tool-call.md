# Practice Review Autonomous Tool Call Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make practice chat prompt and Review tool metadata strongly guide the main model to autonomously call `submit_practice_code_review` for complete solution submissions, while preserving permission-based human confirmation and no forced tool calling.

**Architecture:** Keep the existing Agent loop and phase-one permission runtime unchanged. Update only the practice chat prompt contract, the Review tool description contract, related tests, and obsolete design docs that still point to forced tool calling or backend automatic Review capability.

**Tech Stack:** Java 17, Maven, JUnit 5, AssertJ, Spring MVC application modules, Markdown docs.

---

### Task 1: Strengthen Practice Chat Prompt Contract

**Files:**
- Modify: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProviderTest.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`

- [ ] **Step 1: Write failing prompt assertions**

Add assertions to the existing prompt test that render a practice chat prompt and verify these exact behavior contracts:

```java
assertThat(text)
    .contains("只要当前用户消息看起来像是在粘贴当前题目的完整 LeetCode 解法")
    .contains("即使用户没有明确要求正式 Review，也应调用 submit_practice_code_review")
    .contains("如果用户拒绝确认或确认超时，可以继续普通点评代码")
    .contains("不要给出正式分数")
    .contains("不要声称已生成 Review 记录")
    .doesNotContain("用户粘贴代码时，先定位关键问题和最小修改");
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeChatPromptSectionProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failure because the current prompt still uses weaker wording and does not include all autonomous Review tool rules.

- [ ] **Step 3: Update prompt text**

In `PracticeChatPromptSectionProvider`, rewrite `COACHING_POLICY` and `CODE_REVIEW_TOOL_BOUNDARY` so the final prompt states:

```text
当当前用户消息看起来像是在粘贴当前题目的完整 LeetCode 解法时，应优先调用 submit_practice_code_review。
即使用户没有明确要求正式 Review，也应调用 submit_practice_code_review，让用户通过确认弹窗决定是否生成正式记录。
如果不确定是否完整但确实像题解提交，偏积极触发；明显片段、伪代码、报错日志、局部 bug、语法问题、复杂度讨论和概念问题不要调用工具。
如果用户拒绝确认或确认超时，可以继续普通点评代码，但必须说明没有生成正式 Review 记录，不要给出正式分数，不要声称完成状态已更新。
```

Remove or rephrase the older generic rule:

```text
用户粘贴代码时，先定位关键问题和最小修改，再给必要修正版。
```

- [ ] **Step 4: Run prompt test to verify it passes**

Run the same Maven command from Step 2.

Expected: test passes.

### Task 2: Strengthen Review Tool Description Contract

**Files:**
- Modify: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolTest.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentTool.java`

- [ ] **Step 1: Write failing tool spec assertions**

Add or update a `spec` test to assert the tool description contains:

```java
assertThat(tool.spec().description())
    .contains("Record the current practice user message as a formal code submission")
    .contains("extract code, analyze, score, and save a review record")
    .contains("Use when the current user message looks like a complete solution submission")
    .contains("even if the user did not explicitly ask for a formal review")
    .contains("Do not pass user id, session id, problem slug, code, or message ids");
```

Also assert the schema still only exposes `userIntent` and `notes`.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeCodeReviewAgentToolTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failure because the current tool description does not yet express the formal submission record and sub-review flow strongly enough.

- [ ] **Step 3: Update tool description only**

Update the `LlmToolSpec` description in `PracticeCodeReviewAgentTool` to the tested wording. Do not add trusted business facts to arguments, and do not change execution behavior.

- [ ] **Step 4: Run tool test to verify it passes**

Run the same Maven command from Step 2.

Expected: test passes.

### Task 3: Clean Obsolete Direction In Docs

**Files:**
- Modify: `docs/agent-forced-tool-calling-design.md`
- Modify: `docs/practice-code-review-product-design.md`
- Modify: `docs/practice-code-review-technical-design.md`

- [ ] **Step 1: Update forced-tool design direction**

Replace the future “阶段二：显式入口 + Forced Tool Calling” direction with “阶段二：自主 Tool Call Prompt 强化”. State that forced tool calling is no longer the planned direction and that future Review triggering depends on main-model autonomous judgment plus phase-one permission confirmation.

- [ ] **Step 2: Update product design**

Replace claims that the backend “自动识别并生成代码 Review 记录” with wording that the model should autonomously call the formal Review tool when it judges the current message to be a complete solution submission, and the record is created only after user confirmation.

- [ ] **Step 3: Update technical design**

Mark the old `PracticeTurnCapability` / `CodeReviewTurnCapability` design as deprecated historical direction. State that the current implementation uses `submit_practice_code_review` Agent tool plus permission hook, and does not run post-chat backend capability detection.

- [ ] **Step 4: Search for misleading residue**

Run:

```bash
rg -n "阶段二.*Forced|forced tool calling.*阶段二|后端自动识别|自动识别并生成|CodeReviewTurnCapability|PracticeTurnCapability|practiceCapabilities" docs backend/mentor-application backend/mentor-api -g '!target' -g '!dist' -g '!build' -g '!node_modules'
```

Expected: remaining matches are either clearly marked historical/deprecated, test constants guarding old behavior, or non-misleading references.

### Task 4: Focused Verification

**Files:**
- No additional code files.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeChatPromptSectionProviderTest,PracticeCodeReviewAgentToolTest,PracticeCodeReviewFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all specified tests pass.

- [ ] **Step 2: Review diff**

Run:

```bash
git diff -- backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentTool.java backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProviderTest.java backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolTest.java docs/agent-forced-tool-calling-design.md docs/practice-code-review-product-design.md docs/practice-code-review-technical-design.md
```

Expected: no forced tool calling implementation, no backend automatic detection path, no permission runtime changes.
