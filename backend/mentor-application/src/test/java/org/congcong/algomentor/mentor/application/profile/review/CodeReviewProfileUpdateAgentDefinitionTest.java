package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.junit.jupiter.api.Test;

class CodeReviewProfileUpdateAgentDefinitionTest {

  @Test
  void preparesAStrictSingleStepBackgroundRequestWithoutTools() {
    CodeReviewProfileUpdateAgentDefinition definition = new CodeReviewProfileUpdateAgentDefinition(
        new CodeReviewProfilePromptBuilder());

    var prepared = definition.prepare(input("profile-batch-1", null), context(17L, "profile-batch-1"));

    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(1);
    assertThat(definition.allowedToolNames()).isEmpty();
    assertThat(prepared.executionOptions().structuredOutput().strategy())
        .isEqualTo(StructuredOutputStrategy.PROVIDER_NATIVE);
    assertThat(prepared.executionOptions().structuredOutput().schemaName())
        .isEqualTo(CodeReviewProfileJsonSchema.SCHEMA_NAME);
    assertThat(prepared.metadata()).containsEntry(
        AgentRuntimeMetadataKeys.SCHEMA_VERSION, CodeReviewProfileConsumerConstants.SCHEMA_VERSION);
    assertThat(prepared.retryOfRunId()).isNull();
  }

  @Test
  void rejectsNonBackgroundInvocationAndPreservesRetrySource() {
    CodeReviewProfileUpdateAgentDefinition definition = new CodeReviewProfileUpdateAgentDefinition(
        new CodeReviewProfilePromptBuilder());

    assertThatThrownBy(() -> definition.prepare(input("profile-batch-1", null), new AgentInvocationContext(
        17L, AgentInvocationMode.USER_ENTRY, "profile-batch-1", null, null, 1, false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("background");
    assertThat(definition.prepare(input("profile-batch-2", 801L), context(17L, "profile-batch-2"))
        .retryOfRunId()).isEqualTo(801L);
  }

  private CodeReviewProfileUpdateAgentInput input(String idempotencyKey, Long retryOfRunId) {
    LearnerProfileIdentity identity = LearnerProfileIdentity.dimension(
        17L, LearnerProfileEntryKind.GENERAL_OBSERVATION, LearnerProfileDimension.PROBLEM_SOLVING_APPROACH);
    return new CodeReviewProfileUpdateAgentInput(
        17L,
        List.of(new CodeReviewProfileFact(
            701L, "two-sum", 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.ONE, BigDecimal.ONE, false, List.of("boundary"), List.of("check edges"), List.of(), Instant.EPOCH)),
        List.of(new CodeReviewProfilePromptBuilder.Candidate(LearnerProfileSnapshot.from(identity, null), "")),
        idempotencyKey,
        retryOfRunId);
  }

  private AgentInvocationContext context(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId, AgentInvocationMode.BACKGROUND, idempotencyKey, null, null, 1, false);
  }
}
