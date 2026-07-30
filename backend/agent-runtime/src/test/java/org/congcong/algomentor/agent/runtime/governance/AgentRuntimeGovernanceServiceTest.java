package org.congcong.algomentor.agent.runtime.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.runtime.governance.AgentRuntimeGovernanceService;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceService;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.ai.governance.routing.ResolvedAiModelSnapshot;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.junit.jupiter.api.Test;

class AgentRuntimeGovernanceServiceTest {

  @Test
  void childUsesItsOwnCatalogScenarioAndReleasesInvocationTargetOnCancellation() {
    AiRunInvocationTargetStore targets = new AiRunInvocationTargetStore();
    AgentRuntimeGovernanceService service = new AgentRuntimeGovernanceService(governanceService(targets));
    AgentKey<String> key = new AgentKey<>("practice-chat", String.class);

    AgentRuntimeGovernanceLease lease = service.begin(
        key,
        new AgentInvocationContext(7L, AgentInvocationMode.CHILD, "idem-child", "parent-uuid", 2, 64, false),
        run(key.value(), AgentInvocationMode.CHILD, 41L, 2));

    assertThat(lease.scenario().businessScenario()).isEqualTo(AiBusinessScenario.PRACTICE_CHAT);
    assertThat(lease.scenario().purpose()).isEqualTo(AiPurpose.LEARNING_CHAT);
    assertThat(lease.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.AGENT_KEY, "practice-chat")
        .containsEntry(AgentRuntimeMetadataKeys.INVOCATION_MODE, "CHILD")
        .containsEntry(AgentRuntimeMetadataKeys.PARENT_RUN_ID, 41L)
        .containsEntry("aiCallKind", "AGENT_STEP")
        .containsEntry("aiScenarioCode", "practice-chat");
    assertThat(targets.find("run-3")).contains(lease.invocationTarget());

    lease.cancel(null, null, null);

    assertThat(targets.find("run-3")).isEmpty();
  }

  @Test
  void rejectsChildAndBackgroundParentShapesBeforeOpeningAGovernanceLease() {
    AgentRuntimeGovernanceService service = new AgentRuntimeGovernanceService(governanceService(new AiRunInvocationTargetStore()));
    AgentKey<String> key = new AgentKey<>("practice-chat", String.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.begin(
            key,
            new AgentInvocationContext(7L, AgentInvocationMode.CHILD, "idem-child", null, null, 0, false),
            run(key.value(), AgentInvocationMode.CHILD, 41L, 2)))
        .withMessage("Child agent runs require a parent run and step");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.begin(
            key,
            new AgentInvocationContext(7L, AgentInvocationMode.BACKGROUND, "idem-background", "parent", 1, 0, false),
            run(key.value(), AgentInvocationMode.BACKGROUND, 41L, 1)))
        .withMessage("Background agent runs must not have a parent run");
  }

  private static PreparedAgentRun run(
      String agentKey,
      AgentInvocationMode mode,
      Long parentRunId,
      Integer parentStepIndex
  ) {
    return new PreparedAgentRun(
        1L,
        2L,
        3L,
        "run-3",
        "request-3",
        "system",
        null,
        Map.of("prepared", true),
        agentKey,
        mode,
        parentRunId,
        parentStepIndex,
        null,
        4);
  }

  private static AiRunGovernanceService governanceService(AiRunInvocationTargetStore targets) {
    AiGovernanceProperties properties = new AiGovernanceProperties();
    AiPurposePolicyResolver resolver = new AiPurposePolicyResolver(properties);
    return new AiRunGovernanceService(
        null,
        null,
        resolver,
        enabledRuntimePolicy(),
        routeResolver(),
        targets);
  }

  private static AiRuntimePolicyService enabledRuntimePolicy() {
    return new AiRuntimePolicyService(null, null, null) {
      @Override
      public EffectiveAiRuntimePolicy resolve(AiPurposePolicy staticPolicy, long userId) {
        return new EffectiveAiRuntimePolicy(true, null, true, null, 10, null, 10, null, null);
      }
    };
  }

  private static AiModelRouteResolver routeResolver() {
    return (scenario, userId) -> new ResolvedAiModelSnapshot(
        scenario,
        17L,
        2L,
        PolicyMatchSource.GROUP,
        9L,
        101L,
        "gpt-test",
        11L,
        "openai",
        Instant.parse("2026-07-27T00:00:00Z"),
        new LlmProviderClient() {
          @Override
          public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public java.util.concurrent.Flow.Publisher<LlmStreamEvent> stream(
              LlmModelId upstreamModelId,
              LlmCompletionRequest request) {
            throw new UnsupportedOperationException();
          }
        },
        Set.of(LlmCapability.CHAT_COMPLETION));
  }
}
