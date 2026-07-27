package org.congcong.algomentor.ai.governance.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiGovernedCompletionServiceTest {

  @Test
  void userEntryUsesTheAdmissionBoundTargetWithoutResolvingTheRouteAgain() {
    AiRunAdmissionService admissionService = mock(AiRunAdmissionService.class);
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiPurposePolicyResolver policyResolver = mock(AiPurposePolicyResolver.class);
    org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService runtimePolicyService =
        mock(org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService.class);
    AiModelRouteResolver routeResolver = mock(AiModelRouteResolver.class);
    LlmGateway delegate = mock(LlmGateway.class);
    AiRunInvocationTargetStore targetStore = new AiRunInvocationTargetStore();
    AiPurposePolicy policy = policy();
    LlmInvocationTarget target = target();

    when(policyResolver.resolve(AiPurpose.PROBLEM_EXPLANATION)).thenReturn(policy);
    when(runtimePolicyService.resolve(eq(policy), eq(7L))).thenReturn(enabledPolicy());
    when(admissionService.admit(any(AiRunContext.class))).thenAnswer(invocation -> {
      AiRunContext context = invocation.getArgument(0);
      targetStore.bind(context.runId(), target);
      return new AiRunAdmission(
          1L,
          context.runId(),
          context.actor().userId(),
          context.purpose(),
          context.source(),
          AiRunStatus.ADMITTED,
          "ALL",
          null,
          policy,
          Map.of(AiGovernanceMetadataKeys.RUN_ID, context.runId()),
          Instant.parse("2026-07-27T00:00:00Z"));
    });
    when(delegate.complete(any(LlmCompletionRequest.class))).thenReturn(result());

    AiGovernedCompletionService service = new AiGovernedCompletionService(
        delegate,
        admissionService,
        lifecycleService,
        policyResolver,
        runtimePolicyService,
        routeResolver,
        targetStore);

    service.complete(
        LlmCompletionRequest.builder()
            .modelSelector(LlmModelSelector.requiring(Set.of()))
            .messages(List.of(LlmMessage.user("Explain binary search")))
            .build(),
        AiCompletionContext.userEntry(
            7L,
            "run-1",
            AiPurpose.PROBLEM_EXPLANATION,
            AiRunSource.PROBLEM_DETAIL,
            32));

    ArgumentCaptor<LlmCompletionRequest> request = ArgumentCaptor.forClass(LlmCompletionRequest.class);
    verify(delegate).complete(request.capture());
    assertThat(request.getValue().invocationTarget()).isSameAs(target);
    verifyNoInteractions(routeResolver);
  }

  private static AiPurposePolicy policy() {
    return new AiPurposePolicy(true, 10, 1, 1024, 512, 4, true, true, false, false, null, null, "v1");
  }

  private static EffectiveAiRuntimePolicy enabledPolicy() {
    return new EffectiveAiRuntimePolicy(true, null, true, null, 10, null, 10, null, null);
  }

  private static LlmInvocationTarget target() {
    return new LlmInvocationTarget(
        LlmProviderType.of("openai"),
        11L,
        101L,
        LlmModelId.of("gpt-test"),
        Instant.parse("2026-07-27T00:00:00Z"),
        Set.of(LlmCapability.CHAT_COMPLETION),
        new LlmProviderClient() {
          @Override
          public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
            throw new UnsupportedOperationException();
          }
        });
  }

  private static LlmCompletionResult result() {
    return new LlmCompletionResult(
        LlmMessage.assistant("Done"),
        List.of(),
        null,
        LlmFinishReason.STOP,
        LlmUsage.empty(),
        LlmProviderId.of("openai"),
        LlmModelId.of("gpt-test"),
        Map.of());
  }
}
