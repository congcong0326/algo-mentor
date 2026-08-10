package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContext;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionToolContracts;
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
        7L, 11L, 101L, "缩短到一周", brief(), currentPlan(), "revision-1", snapshot);

    var prepared = definition.prepare(input, context());

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.SYSTEM, LlmMessage.Role.ASSISTANT, LlmMessage.Role.USER);
    assertThat(prepared.messages().get(0).text()).contains("负责修订算法学习计划草案");
    assertThat(prepared.messages().get(2).text())
        .contains("当前目标", "当前计划", "projectionMode")
        .doesNotContain("phaseIndex", "durationWeeks\":1", "sortOrder", "frontendId", "titleCn", "tags");
    assertThat(prepared.messages().get(3).text()).contains("缩短到一周");
    assertThat(prepared.metadata())
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENABLED, true)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENTRY_COUNT, 1)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TOKEN_ESTIMATE, 8)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TRIMMED, false);
    assertThat(prepared.metadata().toString()).doesNotContain("claim-text-must-not-enter-metadata");
    assertThat(prepared.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.USER_ID, 7L)
        .containsEntry(LearningPlanRevisionToolContracts.METADATA_REVISION_ID, 101L)
        .containsEntry(
            LearningPlanRevisionToolContracts.METADATA_SCENARIO,
            LearningPlanRevisionToolContracts.SCENARIO);
    assertThat(definition.allowedToolNames()).containsExactly(
        LearningPlanRevisionToolContracts.QUERY_TOOL_NAME,
        LearningPlanRevisionToolContracts.COMPILE_TOOL_NAME);
  }

  @Test
  void omitsPersonalizationSystemMessageWhenTheRunIsDisabled() {
    LearningPlanDraftRevisionAgentDefinition definition = new LearningPlanDraftRevisionAgentDefinition(
        new LearningPlanProposalPromptBuilder(new ObjectMapper()), new ObjectMapper());
    LearningPlanDraftRevisionAgentInput input = new LearningPlanDraftRevisionAgentInput(
        7L,
        11L,
        101L,
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
  void instructsTheModelToQueryOnlyWhenNeededAndCompileAReference() {
    LearningPlanDraftRevisionAgentDefinition definition = new LearningPlanDraftRevisionAgentDefinition(
        new LearningPlanProposalPromptBuilder(new ObjectMapper()), new ObjectMapper());
    LearningPlanDraftRevisionAgentInput input = new LearningPlanDraftRevisionAgentInput(
        7L,
        11L,
        101L,
        "题目有点多了，把当前工作量减少一半，但是保留核心题目",
        brief(),
        currentPlan(),
        "revision-workload-reduction",
        LearningPlanPersonalizationSnapshot.disabled(Instant.EPOCH));

    var prepared = definition.prepare(input, context("revision-workload-reduction"));

    assertThat(prepared.messages().get(0).text())
        .contains("query_learning_plan_revision", "compile_learning_plan_revision")
        .contains("不要输出 Markdown、完整 Brief、完整计划或额外字段");
    assertThat(prepared.messages().get(prepared.messages().size() - 1).text())
        .contains("projectionMode=SUMMARY_WITH_TOOLS")
        .contains("最终只输出 status=COMPILED")
        .contains("题目有点多了");
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
