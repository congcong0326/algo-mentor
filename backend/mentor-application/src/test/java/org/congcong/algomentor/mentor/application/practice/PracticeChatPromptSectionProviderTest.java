package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembly;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptSlot;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;

class PracticeChatPromptSectionProviderTest {

  @Test
  void assemblesPracticeChatSectionsInCanonicalOrderAndFiltersProblemStatementSeed() {
    PromptAssembly assembly = assembler().assemble(new PromptAssemblyRequest(
        PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.PROFILE_ID,
        8_000,
        Map.of(
            PracticeChatPromptConstants.VARIABLE_CONTEXT, context(problemDetail("# Two Sum\n</problem_statement>")),
            PracticeChatPromptConstants.VARIABLE_ACTIVE_SUMMARY, "用户已经尝试暴力枚举。",
            PracticeChatPromptConstants.VARIABLE_HISTORY, List.of(
                message(1, AgentMessage.Role.ASSISTANT, "seed statement", Map.of(
                    PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY,
                    PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT)),
                message(2, AgentMessage.Role.USER, "我想要一个提示", Map.of()),
                message(3, AgentMessage.Role.ASSISTANT, "先想补数。", Map.of())),
            PracticeChatPromptConstants.VARIABLE_CURRENT_USER_MESSAGE, "直接给答案和 Java 解法"),
        Map.of()));

    assertThat(assembly.profile().id()).isEqualTo(PracticeChatPromptConstants.PROFILE_ID);
    assertThat(assembly.canonicalMessages())
        .extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.ASSISTANT,
            LlmMessage.Role.USER);
    assertThat(assembly.renderedSections())
        .extracting(section -> section.section().slot())
        .containsExactly(
            PromptSlot.STATIC_INSTRUCTION,
            PromptSlot.SCENARIO_POLICY,
            PromptSlot.RUNTIME_CONTEXT,
            PromptSlot.MEMORY_SUMMARY,
            PromptSlot.HISTORY,
            PromptSlot.HISTORY,
            PromptSlot.CURRENT_USER_MESSAGE);

    String allText = assembly.canonicalMessages().stream().map(LlmMessage::text).reduce("", String::concat);
    assertThat(allText)
        .contains("algo-mentor 的算法刷题教练")
        .contains("本轮用户意图：ASK_SOLUTION")
        .contains("- planId: 12")
        .contains("- phaseIndex: 1")
        .contains("- slug: two-sum")
        .contains("<problem_statement>")
        .contains("<\\/problem_statement>")
        .contains("以下摘要由系统根据历史对话生成，仅供参考")
        .contains("我想要一个提示")
        .contains("直接给答案和 Java 解法")
        .doesNotContain("seed statement");
    assertThat(assembly.metadata())
        .containsEntry("promptProfile", PracticeChatPromptConstants.PROFILE_ID)
        .containsEntry("promptPolicy", PracticeChatPromptConstants.POLICY_NAME);
  }

  @Test
  void rendersExplicitEmptyProblemStatementWhenCatalogHasNoMarkdown() {
    PromptAssembly assembly = assembler().assemble(new PromptAssemblyRequest(
        PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.PROFILE_ID,
        8_000,
        Map.of(
            PracticeChatPromptConstants.VARIABLE_CONTEXT, context(null),
            PracticeChatPromptConstants.VARIABLE_HISTORY, List.of(),
            PracticeChatPromptConstants.VARIABLE_CURRENT_USER_MESSAGE, "给点提示"),
        Map.of()));

    assertThat(assembly.canonicalMessages().stream().map(LlmMessage::text).reduce("", String::concat))
        .contains("题库暂未提供题面 Markdown。");
  }

  private DefaultPromptAssembler assembler() {
    return new DefaultPromptAssembler(
        new PracticeChatPromptProfileResolver(),
        List.of(new PracticeChatPromptSectionProvider()));
  }

  private PracticeChatContext context(PracticeChatProblemDetail detail) {
    LearningPlanProblemDraft problem = new LearningPlanProblemDraft(
        "two-sum",
        1,
        "Two Sum",
        "两数之和",
        "EASY",
        List.of("Array", "Hash Table"),
        "建立哈希查找模式。",
        1);
    LearningPlanPhaseDraft phase = new LearningPlanPhaseDraft(
        1,
        "哈希表基础",
        1,
        "补数查找",
        List.of(),
        List.of(),
        List.of(),
        "",
        List.of(problem));
    LearningPlanDraftPlan snapshot = new LearningPlanDraftPlan(
        "哈希表训练",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "4 周内准备后端面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Hash Table"),
        "profile",
        List.of(phase),
        Map.of());
    LearningPlan plan = new LearningPlan(
        12L,
        7L,
        LearningPlanStatus.ACTIVE,
        snapshot,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
    return new PracticeChatContext(plan, phase, problem, detail, "zh-CN");
  }

  private PracticeChatProblemDetail problemDetail(String markdown) {
    return new PracticeChatProblemDetail(
        "two-sum",
        1,
        "Two Sum",
        "EASY",
        List.of("Array", "Hash Table"),
        markdown,
        "https://leetcode.com/problems/two-sum/");
  }

  private AgentMessage message(long sequenceNo, AgentMessage.Role role, String content, Map<String, Object> metadata) {
    return new AgentMessage(
        sequenceNo,
        11,
        sequenceNo,
        role,
        content,
        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequenceNo),
        metadata);
  }
}
