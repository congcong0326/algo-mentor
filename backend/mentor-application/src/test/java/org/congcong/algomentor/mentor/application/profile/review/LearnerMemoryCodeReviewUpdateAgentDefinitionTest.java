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
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.junit.jupiter.api.Test;

class LearnerMemoryCodeReviewUpdateAgentDefinitionTest {

  @Test
  void preparesAFourStepBackgroundRequestWithOnlyScopedReviewTools() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryCodeReviewUpdateAgentDefinition definition = new LearnerMemoryCodeReviewUpdateAgentDefinition(
        new LearnerMemoryCodeReviewPromptBuilder(), registry);

    var prepared = definition.prepare(input("profile-batch-1", null), context(17L, "profile-batch-1"));

    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(4);
    assertThat(definition.allowedToolNames()).containsExactly(
        LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
        LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
        LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS);
    assertThat(prepared.executionOptions().structuredOutput().strategy())
        .isEqualTo(StructuredOutputStrategy.PROVIDER_NATIVE);
    assertThat(prepared.executionOptions().structuredOutput().schemaName())
        .isEqualTo(LearnerMemoryCodeReviewJsonSchema.SCHEMA_NAME);
    assertThat(prepared.metadata()).containsEntry(
        AgentRuntimeMetadataKeys.SCHEMA_VERSION, LearnerMemoryCodeReviewConsumerConstants.SCHEMA_VERSION)
        .containsKey(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF)
        .containsEntry(LearnerMemoryAgentToolContracts.METADATA_TOOL_CALL_COUNT, 0);
    assertThat(prepared.retryOfRunId()).isNull();

    prepared.runResource().release();
    assertThat(registry.reserveEvidence(
        (String) prepared.metadata().get(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF), 701L).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.SCOPE_UNAVAILABLE);
  }

  @Test
  void rejectsNonBackgroundInvocationAndPreservesRetrySource() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryCodeReviewUpdateAgentDefinition definition = new LearnerMemoryCodeReviewUpdateAgentDefinition(
        new LearnerMemoryCodeReviewPromptBuilder(), registry);

    assertThatThrownBy(() -> definition.prepare(input("profile-batch-1", null), new AgentInvocationContext(
        17L, AgentInvocationMode.USER_ENTRY, "profile-batch-1", null, null, 1, false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("background");
    var prepared = definition.prepare(input("profile-batch-2", 801L), context(17L, "profile-batch-2"));
    assertThat(prepared.retryOfRunId()).isEqualTo(801L);
    prepared.runResource().release();
  }

  private LearnerMemoryCodeReviewUpdateAgentInput input(String idempotencyKey, Long retryOfRunId) {
    LearnerMemoryCodeReviewFact fact = new LearnerMemoryCodeReviewFact(
        701L, "two-sum", 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
        BigDecimal.ONE, BigDecimal.ONE, false, List.of("boundary"), List.of("check edges"), List.of(), Instant.EPOCH);
    CodeReviewVerification verification = new CodeReviewVerification(701L, "two-sum", 1, List.of(), Instant.EPOCH);
    LearnerMemoryClaimScope scope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null);
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        17L,
        List.of(fact),
        List.of(verification),
        List.of(verification),
        List.of(),
        List.of(new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(scope, 0, 10)),
        new LearnerMemorySnapshotToken("0".repeat(64)),
        0,
        LearnerMemoryClaimContract.CapacityState.NORMAL,
        idempotencyKey,
        retryOfRunId);
  }

  private AgentInvocationContext context(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId, AgentInvocationMode.BACKGROUND, idempotencyKey, null, null, 1, false);
  }
}
