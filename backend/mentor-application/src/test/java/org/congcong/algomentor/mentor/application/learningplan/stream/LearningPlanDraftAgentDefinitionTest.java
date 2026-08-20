package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPaceStatus;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanAbilityTagSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanActiveProgressSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContext;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.junit.jupiter.api.Test;

class LearningPlanDraftAgentDefinitionTest {

  @Test
  void preparesManagedPromptSchemaAndMinimalProblemTools() {
    LearningPlanDraftAgentDefinition definition = definition();
    LearningPlanDraftAgentInput input = new LearningPlanDraftAgentInput(7L, brief(), "draft-1");

    var prepared = definition.prepare(input, context(7L, "draft-1"));

    assertThat(definition.key().value()).isEqualTo(AiBusinessScenario.LEARNING_PLAN_DRAFT.code());
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(LearningPlanDraftAgentDefinition.MAX_STEPS);
    assertThat(definition.allowedToolNames()).containsExactly(
        LearningPlanAgentToolNames.LIST_PROBLEM_FILTERS,
        LearningPlanAgentToolNames.SEARCH_PROBLEMS,
        ReadToolResultTool.NAME);
    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    assertThat(prepared.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TITLE, LearningPlanStreamConstants.DRAFT_AGENT_TITLE)
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US")
        .containsKey(SystemPromptMetadataKeys.TYPE_CODE);
    assertThat(prepared.executionOptions().structuredOutput().schemaName())
        .isEqualTo(LearningPlanStreamConstants.INITIAL_GENERATION_SCHEMA_NAME);
  }

  @Test
  void rejectsInvocationIdentityMismatch() {
    LearningPlanDraftAgentDefinition definition = definition();
    LearningPlanDraftAgentInput input = new LearningPlanDraftAgentInput(7L, brief(), "draft-1");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(8L, "draft-1")))
        .withMessage("Learning plan draft input user does not match the invocation user");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(7L, "other")))
        .withMessage("Learning plan draft input idempotency key does not match the invocation");
  }

  @Test
  void keepsPersonalizationMetadataLowSensitivityAndPlacesBriefLast() throws Exception {
    LearningPlanDraftAgentDefinition definition = definition();
    LearningPlanPersonalizationSnapshot snapshot = new LearningPlanPersonalizationSnapshot(
        true,
        new LearningPlanPersonalizationContext(
            List.of("claim-text-must-not-leak"),
            List.of(),
            List.of(new LearningPlanAbilityTagSummary(
                "tag-value-must-not-leak", "tag-label-must-not-leak", 2,
                new BigDecimal("10"), new BigDecimal("12"))),
            List.of(),
            new LearningPlanActiveProgressSummary(
                "plan-objective-must-not-leak", 1, 4, 25D,
                LearningPlanPaceStatus.ON_TRACK, 1, 5, 9),
            null,
            Instant.EPOCH),
        "untrusted personalization reference",
        8,
        false,
        Map.of(
            LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.ERROR,
            LearningPlanPersonalizationSource.ABILITY_TAGS, LearningPlanPersonalizationSourceOutcome.SUCCESS,
            LearningPlanPersonalizationSource.ACTIVE_PLAN, LearningPlanPersonalizationSourceOutcome.SUCCESS,
            LearningPlanPersonalizationSource.REVIEW_LOAD, LearningPlanPersonalizationSourceOutcome.EMPTY));
    LearningPlanDraftAgentInput input = new LearningPlanDraftAgentInput(731L, brief(), "draft-1", snapshot);

    var prepared = definition.prepare(input, context(731L, "draft-1"));

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    var briefInput = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
        prepared.messages().get(2).text());
    assertThat(briefInput.path("targetProblemCount").asInt()).isEqualTo(15);
    assertThat(briefInput.has("durationWeeks")).isFalse();
    assertThat(briefInput.has("weeklyHours")).isFalse();
    assertThat(prepared.metadata())
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENABLED, true)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENTRY_COUNT, 3)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TOKEN_ESTIMATE, 8)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TRIMMED, false)
        .containsEntry(
            LearningPlanPersonalizationMetadataKeys.SOURCE_OUTCOMES,
            Map.of(
                "ACTIVE_CLAIMS", "ERROR",
                "ABILITY_TAGS", "SUCCESS",
                "ACTIVE_PLAN", "SUCCESS",
                "REVIEW_LOAD", "EMPTY"));
    assertThat(prepared.metadata().toString()).doesNotContain(
        "plan-objective-must-not-leak",
        "claim-text-must-not-leak",
        "tag-label-must-not-leak",
        "tag-value-must-not-leak",
        "731",
        "exception-text-must-not-leak");
  }

  private LearningPlanDraftAgentDefinition definition() {
    return new LearningPlanDraftAgentDefinition(new LearningPlanDraftPromptBuilder(
        new LearningPlanLoadService(), ManagedSystemPrompts.defaultResolver()));
  }

  private LearningPlanBrief brief() {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        15,
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

  private AgentInvocationContext context(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId,
        AgentInvocationMode.USER_ENTRY,
        idempotencyKey,
        null,
        null,
        24,
        true);
  }
}
