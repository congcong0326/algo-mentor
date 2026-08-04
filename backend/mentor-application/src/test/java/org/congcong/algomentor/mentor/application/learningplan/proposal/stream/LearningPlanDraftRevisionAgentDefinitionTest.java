package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContext;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionAgentDefinitionTest {

  @Test
  void insertsEnabledPersonalizationBeforeBriefPlanContextAndLeavesInstructionLast() {
    LearningPlanDraftRevisionAgentDefinition definition = new LearningPlanDraftRevisionAgentDefinition(
        new LearningPlanProposalPromptBuilder(new ObjectMapper()), new ObjectMapper());
    LearningPlanPersonalizationSnapshot snapshot = new LearningPlanPersonalizationSnapshot(
        true,
        new LearningPlanPersonalizationContext(
            List.of("claim-text-must-not-enter-metadata"), List.of(), List.of(), List.of(), null, null, Instant.EPOCH),
        "untrusted personalization reference",
        8,
        false,
        Map.of(
            LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.SUCCESS,
            LearningPlanPersonalizationSource.ABILITY_TAGS, LearningPlanPersonalizationSourceOutcome.EMPTY,
            LearningPlanPersonalizationSource.ACTIVE_PLAN, LearningPlanPersonalizationSourceOutcome.EMPTY,
            LearningPlanPersonalizationSource.REVIEW_LOAD, LearningPlanPersonalizationSourceOutcome.EMPTY));
    LearningPlanDraftRevisionAgentInput input = new LearningPlanDraftRevisionAgentInput(
        7L, 11L, "缩短到一周", brief(), currentPlan(), "revision-1", snapshot);

    var prepared = definition.prepare(input, context());

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.SYSTEM, LlmMessage.Role.ASSISTANT, LlmMessage.Role.USER);
    assertThat(prepared.messages().get(0).text()).contains("负责修订算法学习计划草案");
    assertThat(prepared.messages().get(2).text()).contains("当前目标", "当前计划");
    assertThat(prepared.messages().get(3).text()).contains("缩短到一周");
    assertThat(prepared.metadata())
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENABLED, true)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENTRY_COUNT, 1)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TOKEN_ESTIMATE, 8)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TRIMMED, false);
    assertThat(prepared.metadata().toString()).doesNotContain("claim-text-must-not-enter-metadata");
    assertThat(prepared.metadata().values()).doesNotContain(7L, 11L);
  }

  @Test
  void omitsPersonalizationSystemMessageWhenTheRunIsDisabled() {
    LearningPlanDraftRevisionAgentDefinition definition = new LearningPlanDraftRevisionAgentDefinition(
        new LearningPlanProposalPromptBuilder(new ObjectMapper()), new ObjectMapper());
    LearningPlanDraftRevisionAgentInput input = new LearningPlanDraftRevisionAgentInput(
        7L,
        11L,
        "缩短到一周",
        brief(),
        currentPlan(),
        "revision-1",
        LearningPlanPersonalizationSnapshot.disabled(Instant.EPOCH));

    var prepared = definition.prepare(input, context());

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.ASSISTANT, LlmMessage.Role.USER);
  }

  @Test
  void requiresWorkloadReductionsToPreserveThePeriodPhaseStructure() {
    LearningPlanDraftRevisionAgentDefinition definition = new LearningPlanDraftRevisionAgentDefinition(
        new LearningPlanProposalPromptBuilder(new ObjectMapper()), new ObjectMapper());
    LearningPlanDraftRevisionAgentInput input = new LearningPlanDraftRevisionAgentInput(
        7L,
        11L,
        "题目有点多了，把当前工作量减少一半，但是保留核心题目",
        brief(),
        currentPlan(),
        "revision-workload-reduction",
        LearningPlanPersonalizationSnapshot.disabled(Instant.EPOCH));

    var prepared = definition.prepare(input, context("revision-workload-reduction"));

    assertThat(prepared.messages().get(0).text())
        .contains("阶段数按 resolvedBrief 的 durationWeeks 规划：1 周 1 阶段，2 周 2 阶段，3-6 周 3 阶段，7 周及以上 4 阶段")
        .contains("当前模板阶段数不符合该规则时，应重组为目标阶段数")
        .contains("当前草案已经符合第 4 条阶段数时")
        .contains("一般、普通、合理工作量")
        .contains("sort=COMPANY_FREQUENCY_DESC")
        .contains("不得使用字符串 \"null\"");
  }

  private LearningPlanBrief brief() {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "当前目标",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        true,
        List.of("Array"),
        null,
        true,
        LearningPlanContentLocale.EN_US);
  }

  private LearningPlanDraftPlan currentPlan() {
    return new LearningPlanDraftPlan(
        "当前计划",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "当前目标",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        true,
        List.of("Array"),
        null,
        List.of(),
        Map.of());
  }

  private AgentInvocationContext context() {
    return context("revision-1");
  }

  private AgentInvocationContext context(String idempotencyKey) {
    return new AgentInvocationContext(
        7L,
        AgentInvocationMode.USER_ENTRY,
        idempotencyKey,
        null,
        null,
        20,
        true);
  }
}
