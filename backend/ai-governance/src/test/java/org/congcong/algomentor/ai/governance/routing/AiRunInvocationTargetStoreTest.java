package org.congcong.algomentor.ai.governance.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLlmRequestFactory;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AiRunInvocationTargetStoreTest {

  @Test
  void resolvesTheSameAdmittedTargetForEveryAgentStepAndRemovesItAtRunEnd() {
    AiRunInvocationTargetStore store = new AiRunInvocationTargetStore();
    LlmInvocationTarget target = target();
    store.bind("admission-run", target);
    AgentRequest request = new AgentRequest(
        "conversation-run",
        "request-1",
        List.of(LlmMessage.user("Explain binary search")),
        Map.of(AiGovernanceMetadataKeys.RUN_ID, "admission-run"));
    AgentLlmRequestFactory factory = new AgentLlmRequestFactory(
        LlmModelSelector.requiring(Set.of()),
        context -> LlmModelSelector.requiring(Set.of()),
        store);

    LlmCompletionRequest first = factory.build(request, 1, request.messages(), List.of(), null, request.metadata());
    LlmCompletionRequest second = factory.build(request, 2, request.messages(), List.of(), null, request.metadata());

    assertThat(first.invocationTarget()).isSameAs(target);
    assertThat(second.invocationTarget()).isSameAs(target);
    store.remove("admission-run");
    assertThat(store.resolve(request)).isEmpty();
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
}
