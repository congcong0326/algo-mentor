package org.congcong.algomentor.mentor.application.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateAgentDefinitionTest {

  @Test
  void preparesAStrictSingleStepChildRequestWithoutTools() {
    DeclaredProfileUpdateAgentDefinition definition = new DeclaredProfileUpdateAgentDefinition(
        new DeclaredProfileUpdatePromptBuilder());
    DeclaredProfileUpdateAgentInput input = input("profile-child-1", null);

    var prepared = definition.prepare(input, context(17L, "profile-child-1"));

    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(1);
    assertThat(definition.allowedToolNames()).isEmpty();
    assertThat(prepared.executionOptions().structuredOutput().strategy())
        .isEqualTo(StructuredOutputStrategy.PROVIDER_NATIVE);
    assertThat(prepared.executionOptions().structuredOutput().schemaName())
        .isEqualTo(DeclaredProfileUpdateJsonSchema.SCHEMA_NAME);
    assertThat(prepared.metadata()).containsEntry(
        AgentRuntimeMetadataKeys.SCHEMA_VERSION, LearnerDeclaredProfileToolContracts.SCHEMA_VERSION);
    assertThat(prepared.retryOfRunId()).isNull();
  }

  @Test
  void rejectsMismatchedTrustedIdentityAndPreservesRetrySource() {
    DeclaredProfileUpdateAgentDefinition definition = new DeclaredProfileUpdateAgentDefinition(
        new DeclaredProfileUpdatePromptBuilder());

    assertThatThrownBy(() -> definition.prepare(input("profile-child-1", null), context(18L, "profile-child-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user");
    assertThat(definition.prepare(input("profile-child-2", 801L), context(17L, "profile-child-2"))
        .retryOfRunId()).isEqualTo(801L);
  }

  private DeclaredProfileUpdateAgentInput input(String idempotencyKey, Long retryOfRunId) {
    return new DeclaredProfileUpdateAgentInput(
        17L,
        List.of(new DeclaredProfileUpdateAgentInput.Candidate(
            LearnerMemoryClaimDimension.GOALS_AND_INTENTS,
            "Prepare interview",
            DeclaredProfileUpdateIntent.DECLARE,
            List.of(new DeclaredProfileUpdateAgentInput.ActiveClaim(42L, "Backend role")))),
        idempotencyKey,
        retryOfRunId);
  }

  private AgentInvocationContext context(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId,
        AgentInvocationMode.CHILD,
        idempotencyKey,
        "401",
        3,
        20,
        false);
  }
}
