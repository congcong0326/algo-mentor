package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContext;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.junit.jupiter.api.Test;

class LearningPlanExtensionAgentDefinitionTest {

  @Test
  void enabledPersonalizationUsesSecondSystemMessageAndKeepsMetadataLowSensitivity() {
    LearningPlanExtensionAgentDefinition definition = definition();
    LearningPlanExtensionAgentInput input = input(null, enabledSnapshot());

    var prepared = definition.prepare(input, context());

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    assertThat(prepared.messages().get(2).text()).contains("增加图论补强阶段");
    assertThat(prepared.metadata())
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENABLED, true)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.ENTRY_COUNT, 1)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TOKEN_ESTIMATE, 8)
        .containsEntry(LearningPlanPersonalizationMetadataKeys.TRIMMED, false)
        .containsEntry(
            LearningPlanPersonalizationMetadataKeys.SOURCE_OUTCOMES,
            Map.of(
                "ACTIVE_CLAIMS", "SUCCESS",
                "ABILITY_TAGS", "EMPTY",
                "ACTIVE_PLAN", "EMPTY",
                "REVIEW_LOAD", "EMPTY"));
    assertThat(prepared.metadata().toString()).doesNotContain("sensitive-personalization-text", "731", "41");
  }

  @Test
  void disabledPersonalizationOmitsTheOptionalSystemMessage() {
    LearningPlanExtensionAgentDefinition definition = definition();

    var prepared = definition.prepare(input(null, LearningPlanPersonalizationSnapshot.disabled(Instant.EPOCH)), context());

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
  }

  @Test
  void extensionRevisionKeepsPreviousExtensionAsAssistantContextBeforeFinalInstruction() {
    LearningPlanExtensionAgentDefinition definition = definition();
    LearningPlanExtensionDraft previousExtension = new LearningPlanExtensionDraft(
        "previous extension", List.of(), Map.of());

    var prepared = definition.prepare(input(previousExtension, enabledSnapshot()), context());

    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.ASSISTANT,
            LlmMessage.Role.USER);
    assertThat(prepared.messages().get(2).text()).contains("previous extension");
    assertThat(prepared.messages().get(3).text()).contains("增加图论补强阶段");
  }

  private LearningPlanExtensionAgentDefinition definition() {
    return new LearningPlanExtensionAgentDefinition(new LearningPlanProposalPromptBuilder(new ObjectMapper()));
  }

  private LearningPlanExtensionAgentInput input(
      LearningPlanExtensionDraft previousExtension,
      LearningPlanPersonalizationSnapshot personalizationSnapshot
  ) {
    return new LearningPlanExtensionAgentInput(
        731L,
        41L,
        51L,
        "增加图论补强阶段",
        plan(),
        List.of(),
        previousExtension,
        "extension-1",
        personalizationSnapshot);
  }

  private LearningPlan plan() {
    LearningPlanDraftPlan snapshot = new LearningPlanDraftPlan(
        "plan",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "prepare for interviews",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        List.of(),
        Map.of(
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
    return new LearningPlan(
        41L,
        731L,
        LearningPlanStatus.ACTIVE,
        snapshot,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private LearningPlanPersonalizationSnapshot enabledSnapshot() {
    return new LearningPlanPersonalizationSnapshot(
        true,
        new LearningPlanPersonalizationContext(
            List.of("sensitive-personalization-text"), List.of(), List.of(), List.of(), null, null, Instant.EPOCH),
        "untrusted personalization reference",
        8,
        false,
        Map.of(
            LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.SUCCESS,
            LearningPlanPersonalizationSource.ABILITY_TAGS, LearningPlanPersonalizationSourceOutcome.EMPTY,
            LearningPlanPersonalizationSource.ACTIVE_PLAN, LearningPlanPersonalizationSourceOutcome.EMPTY,
            LearningPlanPersonalizationSource.REVIEW_LOAD, LearningPlanPersonalizationSourceOutcome.EMPTY));
  }

  private AgentInvocationContext context() {
    return new AgentInvocationContext(
        731L,
        AgentInvocationMode.USER_ENTRY,
        "extension-1",
        null,
        null,
        20,
        true);
  }
}
